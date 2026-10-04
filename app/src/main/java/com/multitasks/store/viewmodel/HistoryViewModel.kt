package com.multitasks.store.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.multitasks.store.MultiTasksApp
import com.multitasks.store.model.*
import com.multitasks.store.storage.BackupStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class HistoryFilter(val type: WorkType?=null,val query: String="",val from: String="",val to: String="",val session: String="",val trash: Boolean=false)

fun filterRecords(records: List<WorkRecord>,f: HistoryFilter)=records.filter {
    it.deleted==f.trash && (f.type==null||it.type==f.type) && (f.from.isBlank()||it.date>=f.from) &&
        (f.to.isBlank()||it.date<=f.to) && (f.session.isBlank()||it.sessionId==f.session) &&
        (f.query.isBlank()||it.barcode.contains(f.query,true)||it.note.contains(f.query,true))
}

class HistoryViewModel(app: Application): AndroidViewModel(app) {
    private val container=app as MultiTasksApp
    val repository=container.repository
    val filter=MutableStateFlow(HistoryFilter())
    val records=combine(repository.records,filter,::filterRecords).stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val sessions=repository.sessions
    val message=MutableStateFlow("")
    val busy=MutableStateFlow(false)
    init {run {repository.refresh()}}
    private fun run(action: suspend ()->Unit){viewModelScope.launch{try{action()}catch(e: Exception){message.value="ดำเนินการไม่สำเร็จ กรุณาลองอีกครั้ง"}}}
    fun delete(ids: Set<Long>){run{repository.setDeleted(ids,true);message.value="ย้ายไปถังขยะแล้ว กู้คืนได้ที่เพิ่มเติม"}}
    fun restore(ids: Set<Long>){run{repository.setDeleted(ids,false);message.value="กู้คืนรายการแล้ว"}}
    fun edit(record: WorkRecord){run{repository.save(record);message.value="บันทึกการแก้ไขแล้ว"}}
    fun create(type: WorkType,date: String,onCreated: (WorkSession)->Unit){if(busy.value)return;busy.value=true;run{try{onCreated(repository.createSession(type,date))}finally{busy.value=false}}}
    fun backup(uri: Uri,restore: Boolean){if(busy.value)return;busy.value=true;run{try{
        val backup=BackupStorage(getApplication(),repository,container.images)
        message.value=if(restore)"นำเข้า ${backup.restore(uri)} รายการแล้ว" else {backup.export(uri);"สำรองข้อมูลพร้อมรูปแล้ว"}
    }finally{busy.value=false}}}
}
