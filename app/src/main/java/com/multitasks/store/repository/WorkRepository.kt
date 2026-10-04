package com.multitasks.store.repository

import android.content.Context
import com.multitasks.store.data.WorkDatabase
import com.multitasks.store.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class WorkRepository(context: Context) {
    val database=WorkDatabase(context)
    private val images=com.multitasks.store.storage.ImageStorage(context)
    private val mutex=Mutex()
    private val _records=MutableStateFlow<List<WorkRecord>>(emptyList())
    private val _sessions=MutableStateFlow<List<WorkSession>>(emptyList())
    val records=_records.asStateFlow()
    val sessions=_sessions.asStateFlow()
    suspend fun refresh()=withContext(Dispatchers.IO){mutex.withLock{
        database.records().filter{it.type==WorkType.QA && it.barcodeImage.isBlank()}.forEach { record ->
            runCatching {
                val bitmap=com.multitasks.store.barcode.BarcodeRenderer.render(record.barcode,record.barcodeFormat)
                val path=try{images.save(bitmap,true)}finally{bitmap.recycle()}
                try{database.save(record.copy(barcodeImage=path))}catch(e: Exception){images.delete(path);throw e}
            }
        }
        reload()
    }}
    private fun reload(){_records.value=database.records();_sessions.value=database.sessions()}
    suspend fun createSession(type: WorkType,date: String): WorkSession=withContext(Dispatchers.IO){mutex.withLock {
        WorkSession(UUID.randomUUID().toString(),type,date,System.currentTimeMillis()).also{database.insertSession(it);reload()}
    }}
    suspend fun save(record: WorkRecord)=withContext(Dispatchers.IO){mutex.withLock{database.save(record).also{reload()}}}
    suspend fun setDeleted(ids: Set<Long>,deleted: Boolean)=withContext(Dispatchers.IO){mutex.withLock{database.setDeleted(ids,deleted);reload()}}
    suspend fun importRows(sessions: List<WorkSession>,records: List<WorkRecord>)=withContext(Dispatchers.IO){mutex.withLock{database.importRows(sessions,records);reload()}}
}
