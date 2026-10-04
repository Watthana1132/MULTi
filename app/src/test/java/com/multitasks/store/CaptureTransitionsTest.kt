package com.multitasks.store

import com.multitasks.store.model.*
import com.multitasks.store.viewmodel.*
import org.junit.Assert.*
import org.junit.Test

class CaptureTransitionsTest {
    private fun draft()=CaptureState(type=WorkType.QA,phase=CapturePhase.CONFIRMING,sessionId="round",date="2026-10-04",barcode="8851234567896",photo="photo.jpg",barcodeImage="code.png",quantity="24",savedCount=3)
    @Test fun rescanReplacesBothImagesButKeepsQuantityDateAndSession(){
        val s=CaptureTransitions.rescan(draft());assertEquals("24",s.quantity);assertEquals("round",s.sessionId);assertEquals("2026-10-04",s.date)
        assertEquals("",s.barcode);assertEquals("",s.photo);assertEquals("",s.barcodeImage);assertEquals(CapturePhase.SCANNING,s.phase)
    }
    @Test fun busyDraftCannotBeReset(){val s=draft().copy(phase=CapturePhase.SAVING);assertEquals(s,CaptureTransitions.rescan(s));assertFalse(s.canSave)}
    @Test fun savePreparesNextWithoutChangingSession(){val s=CaptureTransitions.saved(draft(),false);assertEquals(CapturePhase.SCANNING,s.phase);assertEquals("1",s.quantity);assertEquals(4,s.savedCount);assertEquals("round",s.sessionId);assertEquals("8851234567896",s.blockedBarcode);assertFalse(s.canSave)}
    @Test fun rememberQuantityIsOptIn(){assertEquals("24",CaptureTransitions.saved(draft(),true).quantity)}
    @Test fun validatesQuantityPhotoAndBarcode(){assertTrue(draft().canSave);listOf("","0","-1","10000","abc").forEach{assertFalse(draft().copy(quantity=it).canSave)};assertFalse(draft().copy(photo="").canSave);assertFalse(draft().copy(barcode="").canSave);assertTrue(draft().copy(type=WorkType.STOCK,barcode="").canSave)}
    @Test fun historyFiltersDateRangeTypeQueryAndTrash(){
        val rows=listOf(WorkRecord(id=1,type=WorkType.QA,date="2026-10-03",barcode="111"),WorkRecord(id=2,type=WorkType.STOCK,date="2026-10-04",note="200g"),WorkRecord(id=3,type=WorkType.QA,date="2026-10-04",deleted=true))
        assertEquals(listOf(2L),filterRecords(rows,HistoryFilter(from="2026-10-04",to="2026-10-04",query="200")).map{it.id})
        assertEquals(listOf(1L),filterRecords(rows,HistoryFilter(type=WorkType.QA)).map{it.id})
        assertEquals(listOf(3L),filterRecords(rows,HistoryFilter(trash=true)).map{it.id})
    }
}
