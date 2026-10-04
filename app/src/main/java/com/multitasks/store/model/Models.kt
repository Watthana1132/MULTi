package com.multitasks.store.model

import java.time.LocalDate

enum class WorkType(val db: String) { QA("qa"), STOCK("stock") }

data class WorkSession(val id: String, val type: WorkType, val date: String, val createdAt: Long)

data class WorkRecord(
    val id: Long = 0,
    val type: WorkType,
    val date: String = LocalDate.now().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val barcode: String = "",
    val barcodeFormat: String = "CODE_128",
    val quantity: Int = 1,
    val photo: String = "",
    val barcodeImage: String = "",
    val sessionId: String = "",
    val note: String = "",
    val done: Boolean = false,
    val legacyExpiry: Boolean = false,
    val deleted: Boolean = false,
)

enum class CapturePhase { SCANNING, PROCESSING, CONFIRMING, SAVING }

data class CaptureState(
    val type: WorkType = WorkType.QA,
    val phase: CapturePhase = CapturePhase.SCANNING,
    val sessionId: String = "",
    val date: String = LocalDate.now().toString(),
    val barcode: String = "",
    val barcodeFormat: String = "CODE_128",
    val photo: String = "",
    val barcodeImage: String = "",
    val quantity: String = "1",
    val note: String = "",
    val savedCount: Int = 0,
    val scanGeneration: Int = 0,
    val blockedBarcode: String = "",
    val message: String = "",
) {
    val busy get() = phase == CapturePhase.PROCESSING || phase == CapturePhase.SAVING
    val canSave get() = phase == CapturePhase.CONFIRMING && photo.isNotBlank() &&
        (type == WorkType.STOCK || barcode.isNotBlank()) && quantity.toIntOrNull() in 1..9999
}
