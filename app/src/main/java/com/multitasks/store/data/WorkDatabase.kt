package com.multitasks.store.data

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.multitasks.store.model.*

/** v2 is additive. Original IDs, photos, expiry dates and completion flags are retained. */
class WorkDatabase(context: Context, name: String = "multitasks.db") : SQLiteOpenHelper(context, name, null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL,
            code TEXT NOT NULL DEFAULT '', format TEXT NOT NULL DEFAULT 'CODE_128', note TEXT NOT NULL DEFAULT '',
            date TEXT NOT NULL, photo TEXT NOT NULL DEFAULT '', quantity INTEGER NOT NULL CHECK(quantity > 0),
            done INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL, barcode_image TEXT NOT NULL DEFAULT '',
            session_id TEXT NOT NULL DEFAULT '', legacy_expiry INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0)""")
        sessions(db)
    }
    private fun sessions(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sessions (id TEXT PRIMARY KEY, kind TEXT NOT NULL, date TEXT NOT NULL, created INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS items_date ON items(date, created)")
        db.execSQL("CREATE INDEX IF NOT EXISTS items_session ON items(session_id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 2) {
            db.execSQL("ALTER TABLE items ADD COLUMN barcode_image TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE items ADD COLUMN session_id TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE items ADD COLUMN legacy_expiry INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE items ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            sessions(db)
            db.execSQL("UPDATE items SET session_id='legacy-' || kind || '-' || date, legacy_expiry=CASE WHEN kind='qa' THEN 1 ELSE 0 END")
            db.execSQL("INSERT OR IGNORE INTO sessions SELECT session_id,kind,date,MIN(created) FROM items GROUP BY session_id,kind,date")
        }
    }
    fun records(): List<WorkRecord> = buildList {
        readableDatabase.query("items",null,null,null,null,null,"created DESC, id DESC").use { c ->
            fun str(name: String) = c.getString(c.getColumnIndexOrThrow(name))
            fun num(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
            while(c.moveToNext()) add(WorkRecord(num("id"),WorkType.entries.first { it.db == str("kind") },str("date"),num("created"),str("code"),str("format"),num("quantity").toInt(),str("photo"),str("barcode_image"),str("session_id"),str("note"),num("done")==1L,num("legacy_expiry")==1L,num("deleted")==1L))
        }
    }
    fun sessions(): List<WorkSession> = buildList {
        readableDatabase.rawQuery("SELECT id,kind,date,created FROM sessions ORDER BY created DESC",null).use { c ->
            while(c.moveToNext()) add(WorkSession(c.getString(0),WorkType.entries.first{it.db==c.getString(1)},c.getString(2),c.getLong(3)))
        }
    }
    fun insertSession(s: WorkSession) = writableDatabase.insertOrThrow("sessions",null,ContentValues().apply {
        put("id",s.id); put("kind",s.type.db); put("date",s.date); put("created",s.createdAt)
    })
    fun save(r: WorkRecord): Long {
        require(r.quantity in 1..9999)
        val v=ContentValues().apply {
            put("kind",r.type.db);put("date",r.date);put("created",r.timestamp);put("code",r.barcode);put("format",r.barcodeFormat)
            put("quantity",r.quantity);put("photo",r.photo);put("barcode_image",r.barcodeImage);put("session_id",r.sessionId)
            put("note",r.note);put("done",if(r.done)1 else 0);put("legacy_expiry",if(r.legacyExpiry)1 else 0);put("deleted",if(r.deleted)1 else 0)
        }
        return if(r.id==0L) writableDatabase.insertOrThrow("items",null,v) else {
            check(writableDatabase.update("items",v,"id=?",arrayOf(r.id.toString()))==1);r.id
        }
    }
    fun setDeleted(ids: Set<Long>, deleted: Boolean) {
        val db=writableDatabase;db.beginTransaction()
        try { ids.forEach { db.update("items",ContentValues().apply{put("deleted",if(deleted)1 else 0)},"id=?",arrayOf(it.toString())) }; db.setTransactionSuccessful() }
        finally { db.endTransaction() }
    }
    fun importRows(sessions: List<WorkSession>, records: List<WorkRecord>) {
        val db=writableDatabase;db.beginTransaction()
        try { sessions.forEach(::insertSession);records.forEach(::save);db.setTransactionSuccessful() } finally {db.endTransaction()}
    }
}
