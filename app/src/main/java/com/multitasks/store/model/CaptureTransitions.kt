package com.multitasks.store.model

object CaptureTransitions {
    fun rescan(s: CaptureState)=if(s.busy)s else s.copy(phase=CapturePhase.SCANNING,barcode="",photo="",barcodeImage="",message="",blockedBarcode="",scanGeneration=s.scanGeneration+1)
    fun saved(s: CaptureState,rememberQuantity: Boolean)=CaptureState(type=s.type,sessionId=s.sessionId,date=s.date,quantity=if(rememberQuantity)s.quantity else "1",savedCount=s.savedCount+1,scanGeneration=s.scanGeneration+1,blockedBarcode=s.barcode,message="✓ บันทึกแล้ว")
}
