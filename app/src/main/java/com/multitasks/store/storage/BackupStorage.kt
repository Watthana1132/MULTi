package com.multitasks.store.storage

import android.content.Context
import android.net.Uri
import com.multitasks.store.model.*
import com.multitasks.store.repository.WorkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.*

/** Portable backup with relative image names. Import adds a new copy; never overwrites live rows. */
class BackupStorage(private val context: Context,private val repository: WorkRepository,private val images: ImageStorage) {
    suspend fun export(uri: Uri)=withContext(Dispatchers.IO) {
        repository.refresh()
        val records=repository.records.value
        val files=(records.flatMap{listOf(it.photo,it.barcodeImage)}).filter{it.isNotBlank()}.distinct()
        val mapping=files.associateWith{"images/${UUID.randomUUID()}.${if(it.endsWith(".png"))"png" else "jpg"}"}
        val root=JSONObject().put("version",2)
        root.put("sessions",JSONArray().apply{repository.sessions.value.forEach{put(JSONObject().put("id",it.id).put("type",it.type.name).put("date",it.date).put("created",it.createdAt))}})
        root.put("records",JSONArray().apply{records.forEach{r->put(JSONObject().put("type",r.type.name).put("date",r.date).put("timestamp",r.timestamp).put("barcode",r.barcode).put("format",r.barcodeFormat).put("quantity",r.quantity).put("photo",mapping[r.photo].orEmpty()).put("image",mapping[r.barcodeImage].orEmpty()).put("session",r.sessionId).put("note",r.note).put("done",r.done).put("legacy",r.legacyExpiry).put("deleted",r.deleted))}})
        context.contentResolver.openOutputStream(uri)?.use{output->ZipOutputStream(output).use{zip->
            zip.putNextEntry(ZipEntry("manifest.json"));zip.write(root.toString().toByteArray(Charsets.UTF_8));zip.closeEntry()
            mapping.forEach{(path,name)->
                val f=File(path);require(f.exists()){ "ไม่พบรูปบางไฟล์ กรุณาตรวจข้อมูลก่อนสำรอง" }
                zip.putNextEntry(ZipEntry(name));f.inputStream().use{it.copyTo(zip)};zip.closeEntry()
            }
        }}?:error("เปิดไฟล์สำรองไม่ได้")
    }
    suspend fun restore(uri: Uri): Int=withContext(Dispatchers.IO) {
        val staged=mutableMapOf<String,File>()
        var manifest=""
        try {
            context.contentResolver.openInputStream(uri)?.use{input->ZipInputStream(input).use{zip->
                var total=0L;var entries=0
                while(true){val entry=zip.nextEntry?:break
                    require(++entries<=10001){"ไฟล์สำรองมีรายการมากเกินไป"}
                    require(entry.name=="manifest.json" || Regex("images/[A-Za-z0-9-]+\\.(png|jpg)").matches(entry.name)){"รูปแบบไฟล์สำรองไม่ถูกต้อง"}
                    require(!staged.containsKey(entry.name)){"ชื่อไฟล์ซ้ำในไฟล์สำรอง"}
                    val max=if(entry.name=="manifest.json")10_000_000L else 20_000_000L
                    var size=0L
                    if(entry.name=="manifest.json"){
                        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                        while(true){val n=zip.read(buffer);if(n<0)break;size+=n;total+=n;require(size<=max&&total<=500_000_000L);out.write(buffer,0,n)}
                        require(manifest.isEmpty());manifest=out.toString("UTF-8")
                    } else {
                        val file=images.file(if(entry.name.endsWith("png"))"png" else "jpg");staged[entry.name]=file
                        file.outputStream().use{out->val buffer=ByteArray(8192);while(true){val n=zip.read(buffer);if(n<0)break;size+=n;total+=n;require(size<=max&&total<=500_000_000L);out.write(buffer,0,n)}}
                    }
                    zip.closeEntry()
                }
            }}?:error("อ่านไฟล์ไม่ได้")
            val root=JSONObject(manifest);require(root.getInt("version")==2)
            val sessionMap=mutableMapOf<String,String>()
            val sessions=buildList {
                val data=root.getJSONArray("sessions")
                for(n in 0 until data.length()){val j=data.getJSONObject(n);val id=UUID.randomUUID().toString();sessionMap[j.getString("id")]=id
                    add(WorkSession(id,WorkType.valueOf(j.getString("type")),java.time.LocalDate.parse(j.getString("date")).toString(),j.getLong("created")))}
            }
            fun image(name: String): String=if(name.isBlank())"" else staged[name]?.absolutePath?:error("ไฟล์รูปไม่ครบ")
            val records=buildList {
                val data=root.getJSONArray("records");require(data.length()<=10000)
                for(n in 0 until data.length()){
                    val j=data.getJSONObject(n);val q=j.getInt("quantity");require(q in 1..9999)
                    val raw=j.getString("barcode");val format=j.getString("format")
                    require(raw.length<=80);com.google.zxing.BarcodeFormat.valueOf(format)
                    add(WorkRecord(type=WorkType.valueOf(j.getString("type")),date=java.time.LocalDate.parse(j.getString("date")).toString(),timestamp=j.getLong("timestamp"),barcode=raw,barcodeFormat=format,quantity=q,photo=image(j.getString("photo")),barcodeImage=image(j.getString("image")),sessionId=sessionMap[j.getString("session")]?:error("รอบงานไม่ครบ"),note=j.optString("note").take(200),done=j.optBoolean("done"),legacyExpiry=j.optBoolean("legacy"),deleted=j.optBoolean("deleted")))
                }
            }
            repository.importRows(sessions,records);records.size
        } catch(e: Exception){staged.values.forEach{it.delete()};throw e}
    }
}
