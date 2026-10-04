package com.multitasks.store.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.multitasks.store.MultiTasksApp
import com.multitasks.store.barcode.BarcodeRenderer
import com.multitasks.store.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

class CaptureViewModel(app: Application): AndroidViewModel(app) {
    private val container=app as MultiTasksApp
    private val repository=container.repository
    private val images=container.images
    private val preferences=app.getSharedPreferences("capture_v2",0)
    private val _state=MutableStateFlow(CaptureState())
    val state=_state.asStateFlow()
    private var operation: Job?=null
    private var active: WorkType?=null
    var rememberQuantity: Boolean
        get()=preferences.getBoolean("remember_quantity",false)
        set(value){preferences.edit().putBoolean("remember_quantity",value).apply()}
    fun begin(type: WorkType,session: WorkSession?=null) {
        if(_state.value.busy)return
        if(active==type && session==null && (_state.value.sessionId.isNotBlank() || operation?.isActive==true))return
        persist();active=type
        val restored=if(session==null)restore(type) else CaptureState(type=type,sessionId=session.id,date=session.date)
        // Finish recovery/session setup before accepting a camera frame. This prevents
        // slow database initialization from overwriting a newly captured confirmation.
        _state.value=restored.copy(phase=CapturePhase.PROCESSING)
        operation=viewModelScope.launch {
            try {
                repository.refresh()
                var s=restored
                if(s.photo.isNotBlank() && repository.records.value.any{it.photo==s.photo}) {
                    s=s.copy(phase=CapturePhase.SCANNING,barcode="",photo="",barcodeImage="",quantity="1",note="")
                }
                val valid=repository.sessions.value.any{it.id==s.sessionId}
                // Resume a pending draft regardless of date. Start a fresh day after completed work.
                if(!valid || (session==null && s.phase==CapturePhase.SCANNING && s.date!=LocalDate.now().toString())) {
                    val created=repository.createSession(type,session?.date?:LocalDate.now().toString())
                    _state.value=s.copy(sessionId=created.id,date=created.date,savedCount=0)
                } else _state.value=s.copy(savedCount=repository.records.value.count{it.sessionId==s.sessionId&&!it.deleted})
                persist()
            }catch(e: Exception){error("เปิดรอบงานไม่สำเร็จ กรุณาลองอีกครั้ง")}
        }
    }
    fun quantity(value: String) { if(!_state.value.busy && value.length<=4 && value.all(Char::isDigit))update{it.copy(quantity=value)} }
    fun step(delta: Int)=quantity(((_state.value.quantity.toIntOrNull()?:1)+delta).coerceIn(1,9999).toString())
    fun note(value: String){if(!_state.value.busy)update{it.copy(note=value.take(200))}}
    fun error(message: String){update{it.copy(phase=if(it.photo.isNotBlank())CapturePhase.CONFIRMING else CapturePhase.SCANNING,message=message,scanGeneration=it.scanGeneration+1)}}
    fun clearMessage(){_state.value=_state.value.copy(message="")}
    fun scanned(raw: String,format: String,bitmap: Bitmap) {
        if(_state.value.phase!=CapturePhase.SCANNING){bitmap.recycle();return}
        update{it.copy(phase=CapturePhase.PROCESSING,message="")}
        operation=viewModelScope.launch {
            var photo="";var barcode=""
            try {
                withContext(Dispatchers.IO) {
                    photo=images.save(bitmap)
                    val generated=BarcodeRenderer.render(raw,format)
                    try {barcode=images.save(generated,true)}finally{generated.recycle()}
                }
                update{it.copy(phase=CapturePhase.CONFIRMING,barcode=raw,barcodeFormat=format,photo=photo,barcodeImage=barcode)}
            }catch(e: Exception){withContext(Dispatchers.IO){images.delete(photo);images.delete(barcode)};error("บาร์โค้ดไม่ถูกต้องหรือบันทึกรูปไม่ได้ กรุณาสแกนใหม่")}
            finally{bitmap.recycle()}
        }
    }
    fun takingPhoto(){if(!_state.value.busy)update{it.copy(phase=CapturePhase.PROCESSING,message="")}}
    fun photoTaken(path: String){
        operation=viewModelScope.launch {
            try {val compressed=withContext(Dispatchers.IO){images.compressPhoto(path)};update{it.copy(photo=compressed,phase=CapturePhase.CONFIRMING)}}
            catch(e: Exception){withContext(Dispatchers.IO){images.delete(path)};error("บันทึกรูปไม่ได้ กรุณาถ่ายใหม่")}
        }
    }
    fun rescan() {
        val old=_state.value;if(old.busy)return
        update{CaptureTransitions.rescan(it)}
        viewModelScope.launch(Dispatchers.IO){images.delete(old.photo);images.delete(old.barcodeImage)}
    }
    fun save() {
        val s=_state.value
        if(!s.canSave || s.sessionId.isBlank())return
        update{it.copy(phase=CapturePhase.SAVING)}
        operation=viewModelScope.launch {
            try {
                repository.save(WorkRecord(type=s.type,date=s.date,barcode=s.barcode,barcodeFormat=s.barcodeFormat,quantity=s.quantity.toInt(),photo=s.photo,barcodeImage=s.barcodeImage,sessionId=s.sessionId,note=s.note))
                update{CaptureTransitions.saved(s,rememberQuantity)}
            }catch(e: Exception){update{it.copy(phase=CapturePhase.CONFIRMING,message="บันทึกไม่สำเร็จ ข้อมูลยังอยู่ กรุณาลองอีกครั้ง")}}
        }
    }
    private fun update(transform: (CaptureState)->CaptureState){_state.value=transform(_state.value);persist()}
    private fun persist(){
        if(active==null)return
        val s=_state.value
        // During an async operation retain the last complete draft on disk.
        if(s.busy)return
        val j=JSONObject().put("session",s.sessionId).put("date",s.date).put("barcode",s.barcode).put("format",s.barcodeFormat).put("photo",s.photo).put("image",s.barcodeImage).put("quantity",s.quantity).put("note",s.note).put("count",s.savedCount)
        preferences.edit().putString(s.type.db,j.toString()).apply()
    }
    private fun restore(type: WorkType): CaptureState = try {
        val j=JSONObject(preferences.getString(type.db,"{}")!!)
        val photo=j.optString("photo").takeIf{it.isNotBlank()&&File(it).exists()}.orEmpty()
        CaptureState(type=type,sessionId=j.optString("session"),date=j.optString("date",LocalDate.now().toString()),barcode=j.optString("barcode"),barcodeFormat=j.optString("format","CODE_128"),photo=photo,barcodeImage=j.optString("image"),quantity=j.optString("quantity","1"),note=j.optString("note"),savedCount=j.optInt("count"),phase=if(photo.isBlank())CapturePhase.SCANNING else CapturePhase.CONFIRMING)
    }catch(e: Exception){CaptureState(type=type)}
}
