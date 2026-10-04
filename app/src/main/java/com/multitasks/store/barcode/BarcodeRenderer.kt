package com.multitasks.store.barcode

import android.graphics.*
import com.google.zxing.*

object BarcodeRenderer {
    fun render(raw: String, format: String): Bitmap {
        require(raw.isNotBlank() && raw.length <= 80)
        val matrix=MultiFormatWriter().encode(raw,BarcodeFormat.valueOf(format),1200,330,mapOf(EncodeHintType.MARGIN to 32))
        val bitmap=Bitmap.createBitmap(matrix.width,420,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
        val pixels=IntArray(matrix.width*matrix.height) { index -> if(matrix[index%matrix.width,index/matrix.width]) Color.BLACK else Color.WHITE }
        bitmap.setPixels(pixels,0,matrix.width,0,15,matrix.width,matrix.height)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=42f;textAlign=Paint.Align.CENTER;typeface=Typeface.MONOSPACE}
        canvas.drawText(raw,matrix.width/2f,397f,paint)
        return bitmap
    }
}
