package com.multitasks.store.ui

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.multitasks.store.model.*
import com.multitasks.store.viewmodel.HistoryViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ImageViewer(initialId: Long,vm: HistoryViewModel,onBack: ()->Unit){
    val records by vm.records.collectAsStateWithLifecycle()
    val pager=rememberPagerState(initialPage=records.indexOfFirst{it.id==initialId}.coerceAtLeast(0),pageCount={records.size})
    var menu by remember{mutableStateOf(false)}
    var detail by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf(false)}
    var barcodeOnly by remember{mutableStateOf(false)}
    var brightness by remember{mutableFloatStateOf(.8f)}
    val context=LocalContext.current
    val record=records.getOrNull(pager.currentPage)
    DisposableEffect(barcodeOnly){
        val window=(context as Activity).window;val original=window.attributes.screenBrightness
        if(barcodeOnly)window.attributes=window.attributes.apply{screenBrightness=brightness}
        onDispose{window.attributes=window.attributes.apply{screenBrightness=original}}
    }
    if(records.isEmpty()){Column{AppHeader("ดูภาพ",onBack);EmptyState("ไม่มีรายการในมุมมองนี้","รายการอาจอยู่ในถังขยะแล้ว")};return}
    Column(Modifier.fillMaxSize()){
        AppHeader(record?.type?.name?:"ดูภาพ",onBack,actions={
            if(record?.type==WorkType.QA)IconButton(onClick={barcodeOnly=!barcodeOnly}){Icon(if(barcodeOnly)Icons.Rounded.Photo else Icons.Rounded.QrCode2,if(barcodeOnly)"ดูภาพสินค้า" else "เปิดบาร์โค้ด")}
            Box{
                IconButton(onClick={menu=true}){Icon(Icons.Rounded.MoreVert,"เมนูรูปภาพ")}
                DropdownMenu(menu,{menu=false}){
                    DropdownMenuItem(text={Text("รายละเอียด")},onClick={menu=false;detail=true})
                    DropdownMenuItem(text={Text("แก้ไขข้อมูล")},onClick={menu=false;editing=true})
                    DropdownMenuItem(text={Text("แชร์ภาพ")},onClick={
                        menu=false
                        record?.let{r->
                            val path=if(barcodeOnly&&r.type==WorkType.QA)r.barcodeImage else r.photo
                            if(path.isBlank()||!File(path).exists())vm.message.value="รายการนี้ไม่มีไฟล์ภาพ"
                            else runCatching{
                                val uri=FileProvider.getUriForFile(context,context.packageName+".files",File(path))
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type=if(path.endsWith("png"))"image/png" else "image/jpeg";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"แชร์ภาพ"))
                            }.onFailure{vm.message.value="แชร์ภาพไม่สำเร็จ"}
                        }
                    })
                    DropdownMenuItem(text={Text(if(record?.deleted==true)"กู้คืนรายการ" else "ย้ายไปถังขยะ")},onClick={menu=false;record?.let{if(it.deleted)vm.restore(setOf(it.id))else vm.delete(setOf(it.id))}})
                }
            }
        })
        HorizontalPager(pager,Modifier.weight(1f).testTag("image-pager"),key={records[it].id}){index->
            val item=records[index]
            Column(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                ZoomablePhoto(if(barcodeOnly&&item.type==WorkType.QA)item.barcodeImage else item.photo,Modifier.weight(1f).fillMaxWidth())
                if(item.type==WorkType.QA&&!barcodeOnly){
                    ProductThumbnail(item.barcodeImage,Modifier.fillMaxWidth().height(95.dp).background(Color.White).clickable{barcodeOnly=true},"แตะเปิดบาร์โค้ด",true)
                }
                if(item.type==WorkType.QA)Text(item.barcode,style=MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("${item.quantity} ชิ้น",style=MaterialTheme.typography.headlineSmall,color=Pink);Text("${dateLabel(item.date)}\n${timeLabel(item.timestamp)}",style=MaterialTheme.typography.bodyMedium,color=Muted)}
                if(item.note.isNotBlank())Text(item.note,color=Muted)
                if(item.legacyExpiry)Text("วันที่ด้านบนคือวันหมดอายุจากรุ่นเดิม",style=MaterialTheme.typography.labelSmall,color=Muted)
            }
        }
        if(barcodeOnly&&record?.type==WorkType.QA)Column(Modifier.padding(horizontal=24.dp)){
            Text("ความสว่างสำหรับสแกน EOB",style=MaterialTheme.typography.labelMedium,color=Muted)
            Slider(brightness,{brightness=it;val w=(context as Activity).window;w.attributes=w.attributes.apply{screenBrightness=it}},valueRange=.1f..1f)
        }
        Text("${pager.currentPage+1} / ${records.size}  ·  ปัดซ้าย–ขวาเพื่อเปลี่ยนภาพ",Modifier.align(Alignment.CenterHorizontally).padding(20.dp),style=MaterialTheme.typography.labelMedium,color=Muted)
    }
    if(detail&&record!=null)ModalBottomSheet(onDismissRequest={detail=false}){
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            Text("รายละเอียด ${record.type.name}",style=MaterialTheme.typography.headlineSmall)
            if(record.barcode.isNotBlank())Text("บาร์โค้ด: ${record.barcode}\nชนิด: ${record.barcodeFormat}")
            Text("จำนวน ${record.quantity} ชิ้น\nวันที่ ${dateLabel(record.date)} (ค.ศ.)\nบันทึก ${timeLabel(record.timestamp)}")
            Text(if(record.done)"สถานะ: ทำรายการแล้ว" else "สถานะ: รอดำเนินการ")
            LargeActionButton(if(record.done)"ตั้งเป็นรอดำเนินการ" else "ทำรายการแล้ว",Icons.Rounded.Check){vm.edit(record.copy(done=!record.done));detail=false}
            OutlinedButton(onClick={detail=false;editing=true},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("แก้ไขข้อมูล")}
        }
    }
    if(editing&&record!=null)EditRecordDialog(record,{editing=false}){vm.edit(it);editing=false}
}

@Composable private fun ZoomablePhoto(path: String,modifier: Modifier){
    var scale by remember(path){mutableFloatStateOf(1f)}
    var offset by remember(path){mutableStateOf(Offset.Zero)}
    val transform=rememberTransformableState{zoom,pan,_->
        scale=(scale*zoom).coerceIn(1f,4f)
        offset=if(scale==1f)Offset.Zero else Offset((offset.x+pan.x).coerceIn(-600f,600f),(offset.y+pan.y).coerceIn(-900f,900f))
    }
    Box(modifier.background(Color.White).clipToBounds().transformable(transform,canPan={scale>1f})){
        ProductThumbnail(path,Modifier.fillMaxSize().graphicsLayer{scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y},"รูปสินค้า • ใช้สองนิ้วเพื่อซูม",true)
        if(scale>1f)TextButton(onClick={scale=1f;offset=Offset.Zero},modifier=Modifier.align(Alignment.TopEnd)){Text("คืนขนาด")}
    }
}

@Composable private fun EditRecordDialog(record: WorkRecord,onDismiss: ()->Unit,onSave: (WorkRecord)->Unit){
    var qty by remember{mutableStateOf(record.quantity.toString())}
    var date by remember{mutableStateOf(record.date)}
    var note by remember{mutableStateOf(record.note)}
    val context=LocalContext.current
    AlertDialog(onDismissRequest=onDismiss,title={Text("แก้ไขข้อมูล")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(if(record.legacyExpiry)"วันหมดอายุ (รายการรุ่นเดิม)" else "วันที่บันทึก",color=Muted)
        OutlinedButton(onClick={chooseDate(context,date){date=it}},modifier=Modifier.fillMaxWidth()){Text(dateLabel(date))}
        OutlinedTextField(qty,{if(it.length<=4&&it.all(Char::isDigit))qty=it},label={Text("จำนวน")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
        if(record.type==WorkType.STOCK||record.note.isNotBlank())OutlinedTextField(note,{note=it.take(200)},label={Text("โน้ต")})
    }},confirmButton={TextButton(enabled=qty.toIntOrNull() in 1..9999,onClick={onSave(record.copy(quantity=qty.toInt(),date=date,note=note))}){Text("บันทึก")}},dismissButton={TextButton(onClick=onDismiss){Text("ยกเลิก")}})
}
