package com.multitasks.store.storage

import android.content.Context
import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID

class ImageStorage(context: Context) {
    val directory=File(context.filesDir,"photos").apply{mkdirs()}
    fun file(extension: String="jpg")=File(directory,"${UUID.randomUUID()}.$extension")
    fun save(bitmap: Bitmap, png: Boolean=false): String {
        val f=file(if(png)"png" else "jpg")
        try { f.outputStream().use { check(bitmap.compress(if(png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,85,it)) };return f.absolutePath }
        catch(e: Exception){f.delete();throw e}
    }
    fun compressPhoto(path: String): String {
        val options=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(path,options)
        options.inJustDecodeBounds=false;options.inSampleSize=1
        while(options.outWidth/options.inSampleSize>1800 || options.outHeight/options.inSampleSize>1800)options.inSampleSize*=2
        val bitmap=BitmapFactory.decodeFile(path,options)?:error("อ่านรูปไม่ได้")
        val angle=when(ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)){6->90f;3->180f;8->270f;else->0f}
        val rotated=if(angle==0f)bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,Matrix().apply{postRotate(angle)},true)
        return try { save(rotated) } finally { if(rotated!==bitmap)rotated.recycle();bitmap.recycle();File(path).delete() }
    }
    fun delete(path: String) {
        if(path.isBlank()) return
        val f=File(path)
        if(f.canonicalFile.parentFile==directory.canonicalFile)f.delete()
    }
}
