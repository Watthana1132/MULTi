package com.multitasks.store.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.*
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.multitasks.store.ScanGate
import java.io.File
import java.util.concurrent.Executors

/** One CameraX binding per capture route, not per recomposition or scanned item. */
class CaptureCamera(private val context: Context) {
    private val executor=Executors.newSingleThreadExecutor()
    private val main=ContextCompat.getMainExecutor(context)
    private val scanner=BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(
        Barcode.FORMAT_EAN_13,Barcode.FORMAT_EAN_8,Barcode.FORMAT_UPC_A,Barcode.FORMAT_UPC_E,
        Barcode.FORMAT_CODE_128,Barcode.FORMAT_CODE_39,Barcode.FORMAT_ITF).build())
    private val gate=ScanGate()
    private var provider: ProcessCameraProvider?=null
    private var capture: ImageCapture?=null
    private var camera: Camera?=null
    private var closed=false
    private var generation=-1
    private var previous=""
    private var matches=0
    private var failureReported=false
    @Volatile var scanEnabled=false
    var onScan: (String,String,Bitmap)->Unit={_,_,b->b.recycle()}
    var onError: (String)->Unit={}
    var onReady: ()->Unit={}
    fun reset(version: Int,blocked: String) {
        if(version!=generation){generation=version;gate.clear();if(blocked.isNotBlank())gate.saved(blocked);matches=0;previous=""}
    }
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    fun bind(owner: LifecycleOwner, view: PreviewView) {
        val future=ProcessCameraProvider.getInstance(context)
        future.addListener({
            if(closed)return@addListener
            try {
                val p=future.get();provider=p
                val preview=Preview.Builder().build().also{it.setSurfaceProvider(view.surfaceProvider)}
                val photo=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();capture=photo
                val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor){image ->
                    val media=image.image
                    if(closed||!scanEnabled||media==null){image.close();return@setAnalyzer}
                    val requestGeneration=generation
                    scanner.process(InputImage.fromMediaImage(media,image.imageInfo.rotationDegrees))
                        .addOnSuccessListener(main){codes ->
                            if(closed||!scanEnabled||generation!=requestGeneration)return@addOnSuccessListener
                            val code=codes.firstOrNull{!it.rawValue.isNullOrBlank()}
                            val raw=code?.rawValue.orEmpty()
                            if(raw.isBlank()){gate.accept(null);previous="";matches=0;return@addOnSuccessListener}
                            matches=if(previous==raw)matches+1 else 1;previous=raw
                            if(matches<2 || !gate.accept(raw))return@addOnSuccessListener
                            scanEnabled=false
                            // Copy this exact analyzed frame before ImageProxy closes.
                            try {
                                val source=image.toBitmap()
                                val rotation=image.imageInfo.rotationDegrees.toFloat()
                                val rotated=if(rotation==0f)source else Bitmap.createBitmap(source,0,0,source.width,source.height,Matrix().apply{postRotate(rotation)},true)
                                if(rotated!==source)source.recycle()
                                onScan(raw,format(code!!.format),rotated)
                            }catch(e: Exception){gate.clear();onError("อ่านภาพไม่ได้ กรุณากดสแกนใหม่")}
                        }.addOnFailureListener(main){if(!closed&&!failureReported){failureReported=true;onError("อ่านบาร์โค้ดไม่ได้ ลองขยับกล้องหรือสแกนใหม่")}}
                        .addOnCompleteListener{image.close()}
                }
                p.unbindAll();camera=p.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,photo,analysis)
                onReady()
            } catch(e: Exception){onError("เปิดกล้องไม่ได้ ตรวจสิทธิ์กล้องแล้วลองอีกครั้ง")}
        },main)
    }
    fun photo(file: File,onSaved: (String)->Unit) {
        val current=capture
        if(current==null){onError("กล้องยังไม่พร้อม กรุณาลองอีกครั้ง");return}
        current.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),main,object: ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults){onSaved(file.absolutePath)}
            override fun onError(exception: ImageCaptureException){file.delete();onError("ถ่ายรูปไม่สำเร็จ กรุณาลองอีกครั้ง")}
        })
    }
    fun torch(enabled: Boolean): Boolean {
        val current=camera?:return false
        if(!current.cameraInfo.hasFlashUnit())return false
        current.cameraControl.enableTorch(enabled);return true
    }
    fun close(){closed=true;scanEnabled=false;provider?.unbindAll();scanner.close();executor.shutdown()}
    private fun format(value: Int)=when(value){
        Barcode.FORMAT_EAN_13->"EAN_13";Barcode.FORMAT_EAN_8->"EAN_8";Barcode.FORMAT_UPC_A->"UPC_A";Barcode.FORMAT_UPC_E->"UPC_E"
        Barcode.FORMAT_CODE_39->"CODE_39";Barcode.FORMAT_ITF->"ITF";else->"CODE_128"
    }
}
