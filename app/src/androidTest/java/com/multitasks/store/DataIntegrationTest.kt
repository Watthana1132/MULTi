package com.multitasks.store

import android.content.*
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.multitasks.store.data.WorkDatabase
import com.multitasks.store.model.*
import com.multitasks.store.repository.WorkRepository
import com.multitasks.store.storage.*
import com.multitasks.store.barcode.BarcodeRenderer
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.*

@RunWith(AndroidJUnit4::class)
class DataIntegrationTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val roots=mutableListOf<File>()
    private fun isolated(): Context {
        val root=File(context.cacheDir,"test-${UUID.randomUUID()}").apply{mkdirs()};roots+=root
        return object: ContextWrapper(context){
            override fun getFilesDir()=File(root,"files").apply{mkdirs()}
            override fun getDatabasePath(name: String)=File(root,name)
            override fun openOrCreateDatabase(name: String,mode: Int,factory: SQLiteDatabase.CursorFactory?,handler: DatabaseErrorHandler?)=SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory)
            override fun openOrCreateDatabase(name: String,mode: Int,factory: SQLiteDatabase.CursorFactory?)=SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory)
        }
    }
    @After fun cleanup(){roots.forEach{it.deleteRecursively()}}
    @Test fun migratesVersionOneWithoutLosingExpiryQuantityOrId(){
        val c=isolated()
        c.openOrCreateDatabase("multitasks.db",0,null).use{db->
            db.execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, code TEXT NOT NULL DEFAULT '', format TEXT NOT NULL DEFAULT 'CODE_128', note TEXT NOT NULL DEFAULT '', date TEXT NOT NULL, photo TEXT NOT NULL DEFAULT '', quantity INTEGER NOT NULL CHECK(quantity>0), done INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)")
            db.execSQL("INSERT INTO items VALUES(7,'qa','8851234567896','CODE_128','old note','2026-10-03','',2,1,1000)")
            db.version=1
        }
        WorkDatabase(c).use{db->val row=db.records().single();assertEquals(7L,row.id);assertEquals(2,row.quantity);assertEquals("2026-10-03",row.date);assertTrue(row.legacyExpiry);assertTrue(row.done);assertEquals("old note",row.note);assertEquals(row.sessionId,db.sessions().single().id)}
    }
    @Test fun barcodeImageDecodesToOriginalRawValue(){
        val bitmap=BarcodeRenderer.render("8851234567896","CODE_128")
        val scanner=BarcodeScanning.getClient()
        try {val codes=Tasks.await(scanner.process(InputImage.fromBitmap(bitmap,0)),15,TimeUnit.SECONDS);assertEquals("8851234567896",codes.first().rawValue)}finally{scanner.close();bitmap.recycle()}
    }
    @Test fun backupRoundTripPreservesPicturesAndSoftDelete()=runBlocking {
        val c=isolated();val repo=WorkRepository(c);val images=ImageStorage(c)
        val session=repo.createSession(WorkType.STOCK,"2026-10-04")
        val bitmap=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.MAGENTA)}
        val photo=images.save(bitmap);bitmap.recycle()
        val id=repo.save(WorkRecord(type=WorkType.STOCK,date=session.date,quantity=24,photo=photo,note="200g",sessionId=session.id))
        repo.setDeleted(setOf(id),true);assertTrue(repo.records.value.single().deleted)
        repo.setDeleted(setOf(id),false);assertFalse(repo.records.value.single().deleted)
        val backup=File(c.filesDir,"backup.zip");BackupStorage(c,repo,images).export(Uri.fromFile(backup))
        val c2=isolated();val repo2=WorkRepository(c2)
        assertEquals(1,BackupStorage(c2,repo2,ImageStorage(c2)).restore(Uri.fromFile(backup)))
        val restored=repo2.records.value.single();assertEquals(24,restored.quantity);assertEquals("200g",restored.note);assertTrue(File(restored.photo).exists());assertNotEquals(photo,restored.photo)
        repo.database.close();repo2.database.close()
    }
    @Test fun rejectsBackupPathTraversalBeforeCreatingRecords()=runBlocking {
        val c=isolated();val repo=WorkRepository(c);val f=File(c.filesDir,"bad.zip")
        ZipOutputStream(f.outputStream()).use{it.putNextEntry(ZipEntry("../../escape.jpg"));it.write(byteArrayOf(1,2));it.closeEntry()}
        try{BackupStorage(c,repo,ImageStorage(c)).restore(Uri.fromFile(f));fail("Invalid archive accepted")}catch(expected: Exception){assertTrue(expected is IllegalArgumentException || expected is java.util.zip.ZipException)}
        assertTrue(repo.records.value.isEmpty());repo.database.close()
    }
}
