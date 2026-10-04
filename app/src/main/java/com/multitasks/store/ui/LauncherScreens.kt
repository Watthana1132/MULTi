package com.multitasks.store.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.multitasks.store.R
import com.multitasks.store.model.*
import com.multitasks.store.viewmodel.*
import java.time.LocalDate

@Composable fun MainPage(onQa: ()->Unit,onStock: ()->Unit,onCreate: ()->Unit,onHistory: ()->Unit,onSettings: ()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp)){
        Row(Modifier.fillMaxWidth().padding(top=24.dp,bottom=20.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text(stringResource(R.string.app_name),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold);Spacer(Modifier.height(6.dp));Text(stringResource(R.string.author),color=Muted,style=MaterialTheme.typography.bodyMedium)}
            IconButton(onClick=onSettings){Icon(Icons.Rounded.Settings,stringResource(R.string.settings),tint=Pink)}
        }
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
            LauncherCard("QA","ตรวจสินค้า",Icons.Rounded.FactCheck,Modifier.weight(1f),true,onQa)
            LauncherCard("STOCK","บันทึกสินค้า",Icons.Rounded.Inventory2,Modifier.weight(1f),false,onStock)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
            LauncherCard("สร้างงาน","สร้างโฟลเดอร์งาน",Icons.Rounded.CreateNewFolder,Modifier.weight(1f),false,onCreate)
            LauncherCard("ประวัติ","ดูรายการย้อนหลัง",Icons.Rounded.History,Modifier.weight(1f),false,onHistory)
        }
        Spacer(Modifier.height(28.dp))
    }
}
@Composable private fun LauncherCard(title: String,subtitle: String,icon: ImageVector,modifier: Modifier,primary: Boolean,onClick: ()->Unit){
    Card(onClick=onClick,modifier=modifier.heightIn(min=196.dp).testTag("launch-$title"),shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=if(primary)Pink else SoftPink)){
        Column(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
            Icon(icon,null,Modifier.size(48.dp),tint=if(primary)Color.White else Pink)
            Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=if(primary)Color.White else Ink)
            Text(subtitle,style=MaterialTheme.typography.bodySmall,color=if(primary)Color.White.copy(alpha=.85f) else Muted)
        }
    }
}
@Composable fun QuickCapture(onBack: ()->Unit,onCapture: (WorkType)->Unit){Column{
    AppHeader("เลือกกล้อง",onBack)
    Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
        Text("เลือกงานแล้วเริ่มได้เลย",style=MaterialTheme.typography.headlineSmall)
        LargeActionButton("QA · สแกนบาร์โค้ด",Icons.Rounded.QrCodeScanner){onCapture(WorkType.QA)}
        LargeActionButton("STOCK · ถ่ายรูปสินค้า",Icons.Rounded.PhotoCamera){onCapture(WorkType.STOCK)}
    }
}}
@Composable fun CreateWork(vm: HistoryViewModel,onBack: ()->Unit,onStart: (WorkSession)->Unit){
    var type by remember{mutableStateOf(WorkType.QA)}
    var date by remember{mutableStateOf(LocalDate.now().toString())}
    val context=LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    Column{AppHeader("สร้างงาน",onBack);Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
        Text("สร้างรอบงานใหม่",style=MaterialTheme.typography.headlineSmall)
        Text("ใช้วันที่เป็นชื่อโฟลเดอร์ ไม่ต้องพิมพ์ชื่อ",color=Muted)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){WorkType.entries.forEach{FilterChip(type==it,{type=it},label={Text(it.name)},modifier=Modifier.heightIn(min=52.dp))}}
        OutlinedButton(onClick={chooseDate(context,date){date=it}},modifier=Modifier.fillMaxWidth().heightIn(min=60.dp)){Icon(Icons.Rounded.CalendarMonth,null);Spacer(Modifier.width(12.dp));Text(dateLabel(date)+" (ค.ศ.)")}
        Text("โฟลเดอร์: $date · ${type.name}",color=Muted)
        LargeActionButton(if(busy)"กำลังสร้าง…" else "สร้างโฟลเดอร์และเริ่มงาน",Icons.Rounded.CreateNewFolder,!busy){vm.create(type,date,onStart)}
    }}
}
@Composable fun MoreScreen(capture: CaptureViewModel,history: HistoryViewModel,onBack: ()->Unit,onHistory: (Boolean)->Unit,onHelp: ()->Unit){
    var rememberQty by remember{mutableStateOf(capture.rememberQuantity)}
    val busy by history.busy.collectAsStateWithLifecycle()
    var importing by remember{mutableStateOf(false)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){it?.let{uri->history.backup(uri,false)}}
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let{uri->history.backup(uri,true)}}
    Column{AppHeader("เพิ่มเติม",onBack);Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("ตั้งค่าการทำงาน",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        Card(colors=CardDefaults.cardColors(containerColor=Color.White)){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("จำจำนวนล่าสุด");Text("ค่าเริ่มต้นปิด: รายการถัดไปกลับเป็น 1",style=MaterialTheme.typography.bodySmall,color=Muted)};Switch(rememberQty,{rememberQty=it;capture.rememberQuantity=it})}}
        SettingsRow("จัดการรายการ",Icons.Rounded.Inventory2){onHistory(false)}
        SettingsRow("ถังขยะและกู้คืน",Icons.Rounded.DeleteOutline){onHistory(true)}
        SettingsRow("สำรองข้อมูลพร้อมรูป",Icons.Rounded.Backup,enabled=!busy){export.launch("MULTI-TASKS-${LocalDate.now()}.zip")}
        SettingsRow("นำเข้าข้อมูลสำรอง",Icons.Rounded.Restore,enabled=!busy){importing=true}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("ข้อมูลเก็บในเครื่อง การถอนแอปจะลบข้อมูล ควรสำรองไว้ก่อน",color=Muted,style=MaterialTheme.typography.bodySmall)
        SettingsRow("คู่มือและเกี่ยวกับแอป",Icons.Rounded.Info){onHelp()}
        Text("MULTI-TASKS 0.2.0 · App by Watthana",style=MaterialTheme.typography.labelSmall,color=Muted,modifier=Modifier.padding(top=12.dp))
    }}
    if(importing)AlertDialog(onDismissRequest={importing=false},title={Text("นำเข้าข้อมูลสำรอง")},text={Text("รายการในไฟล์จะถูกเพิ่มเป็นสำเนาใหม่ ข้อมูลปัจจุบันยังอยู่ หากนำเข้าไฟล์เดิมซ้ำจะมีรายการซ้ำ")},confirmButton={TextButton(onClick={importing=false;restore.launch(arrayOf("application/zip","application/octet-stream"))}){Text("เลือกไฟล์")}},dismissButton={TextButton(onClick={importing=false}){Text("ยกเลิก")}})
}
@Composable private fun SettingsRow(title: String,icon: ImageVector,enabled: Boolean=true,onClick: ()->Unit){
    OutlinedCard(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){Icon(icon,null,tint=Pink);Text(title,Modifier.weight(1f));Icon(Icons.Rounded.ChevronRight,null,tint=Muted)}
    }
}
@Composable fun HelpScreen(onBack: ()->Unit){Column{AppHeader("คู่มือและเกี่ยวกับแอป",onBack);Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
    Text("MULTI-TASKS",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
    Text("App by Watthana · 0.2.0",color=Pink)
    Text("QA\nเล็งบาร์โค้ด → ตรวจภาพและเลข → จำนวน → บันทึก\nอ่านผิดให้กดสแกนใหม่ได้ทันที กล้องยังอยู่หน้าเดิม")
    Text("STOCK\nถ่ายภาพ → จำนวน → บันทึก\nเพิ่มโน้ตได้ถ้าจำเป็น ไม่ต้องใส่ชื่อสินค้า")
    Text("วันที่และรอบงาน\nเริ่มที่วันนี้โดยอัตโนมัติ หากทำย้อนหลังให้สร้างงานพร้อมวันที่ที่ต้องการ แก้วันที่ของรายการที่บันทึกแล้วได้ในประวัติ")
    Text("รูปภาพและบาร์โค้ด\nประวัติ → เปลี่ยนเป็นแกลเลอรี → แตะภาพ\nปัดซ้าย–ขวาเปลี่ยนรายการ ใช้สองนิ้วเพื่อซูม QA กดไอคอนบาร์โค้ดเพื่อแสดงพื้นขาวให้ EOB สแกน")
    Text("การลบและสำรอง\nลบแล้วรายการจะอยู่ในถังขยะ กู้คืนได้จากเพิ่มเติม สำรองข้อมูลเป็น ZIP พร้อมรูปได้ผ่านตัวเลือกบันทึกไฟล์ของ Android")
    Text("ข้อมูลจากรุ่น 0.1\nวันหมดอายุเดิมยังอยู่และมีป้ายระบุ รายการเก่าที่ไม่มีรูปจะใช้ภาพแทนจนกว่าจะมีรายการใหม่")
    Text("แอปโปรเจกต์นักศึกษาอิสระ ไม่ใช่แอปทางการของร้าน ไม่มีการเชื่อมระบบ EOB โดยตรง",style=MaterialTheme.typography.bodySmall,color=Muted)
}}}
