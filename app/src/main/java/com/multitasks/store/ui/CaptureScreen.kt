package com.multitasks.store.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import com.multitasks.store.MultiTasksApp
import com.multitasks.store.camera.CaptureCamera
import com.multitasks.store.model.*
import com.multitasks.store.viewmodel.CaptureViewModel
import kotlinx.coroutines.delay

@Composable fun CaptureScreen(type: WorkType,vm: CaptureViewModel,onBack: ()->Unit){
    val state by vm.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val owner=LocalLifecycleOwner.current
    var permission by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
    var requested by remember{mutableStateOf(false)}
    var ready by remember{mutableStateOf(false)}
    var flash by remember{mutableStateOf(false)}
    var retry by remember{mutableIntStateOf(0)}
    var noBarcode by remember{mutableStateOf(false)}
    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){permission=it;requested=true}
    val camera=remember(retry,permission){CaptureCamera(context)}
    val images=(context.applicationContext as MultiTasksApp).images
    val preview=remember(retry){PreviewView(context).apply{implementationMode=PreviewView.ImplementationMode.COMPATIBLE}}
    LaunchedEffect(type){vm.begin(type)}
    LaunchedEffect(Unit){if(!permission){requested=true;permissionLauncher.launch(Manifest.permission.CAMERA)}}
    DisposableEffect(owner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)permission=ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}}
    DisposableEffect(camera,permission){
        ready=false
        camera.onScan=vm::scanned;camera.onError={ready=false;vm.error(it)};camera.onReady={ready=true}
        if(permission)camera.bind(owner,preview)
        onDispose{camera.close()}
    }
    SideEffect{camera.reset(state.scanGeneration,state.blockedBarcode);camera.scanEnabled=type==WorkType.QA&&state.type==type&&state.phase==CapturePhase.SCANNING}
    LaunchedEffect(state.scanGeneration,state.phase){noBarcode=false;if(type==WorkType.QA&&state.phase==CapturePhase.SCANNING){delay(12000);noBarcode=true}}
    LaunchedEffect(state.message){if(state.message.startsWith("✓")){delay(1500);vm.clearMessage()}}
    BackHandler(enabled=state.busy){}
    Column(Modifier.fillMaxSize().imePadding()){
        AppHeader(type.name,back={if(!state.busy)onBack()},actions={Text(dateLabel(state.date),style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(end=16.dp),color=Muted)})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Box(Modifier.fillMaxWidth().height(if(state.phase==CapturePhase.CONFIRMING)172.dp else 288.dp).clip(RoundedCornerShape(24.dp)).background(Ink)){
                if(permission){AndroidView(factory={preview},modifier=Modifier.fillMaxSize())
                    if(type==WorkType.QA&&state.phase==CapturePhase.SCANNING)Box(Modifier.align(Alignment.Center).fillMaxWidth(.8f).height(115.dp).border(2.dp,Color.White.copy(alpha=.85f),RoundedCornerShape(16.dp)))
                    Surface(Modifier.align(Alignment.TopStart).padding(12.dp),shape=RoundedCornerShape(8.dp),color=Ink.copy(alpha=.6f)){Text("● LIVE",color=Color.White,modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.labelSmall)}
                    FilledTonalIconButton(onClick={if(camera.torch(!flash))flash=!flash else vm.error("กล้องนี้ไม่มีแฟลช")},enabled=ready&&!state.busy,modifier=Modifier.align(Alignment.TopEnd).padding(8.dp)){Icon(if(flash)Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,"แฟลช")}
                }else Column(Modifier.align(Alignment.Center).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    Text("ใช้กล้องเพื่อสแกนและถ่ายรูปสินค้า",color=Color.White)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick={
                        val activity=context as Activity
                        if(requested&&!activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))
                        else permissionLauncher.launch(Manifest.permission.CAMERA)
                    }){Text(if(requested)"เปิดสิทธิ์กล้อง" else "อนุญาตกล้อง")}
                }
            }
            if(state.message.isNotBlank())Text(state.message,color=if(state.message.startsWith("✓"))Color(0xFF237847)else MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("capture-message"))
            if(!ready&&permission&&!state.busy)TextButton(onClick={retry++;vm.clearMessage()}){Text("เชื่อมต่อกล้อง / ลองใหม่")}
            if(state.phase==CapturePhase.CONFIRMING){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    ProductThumbnail(state.photo,Modifier.size(if(type==WorkType.QA)96.dp else 150.dp).clip(RoundedCornerShape(16.dp)))
                    if(type==WorkType.QA)Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text("ตรวจสอบบาร์โค้ด",style=MaterialTheme.typography.labelMedium,color=Muted)
                        ProductThumbnail(state.barcodeImage,Modifier.fillMaxWidth().height(66.dp).background(Color.White),"ภาพบาร์โค้ด",fit=true)
                        Text(state.barcode,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.testTag("barcode-result"))
                    }else Column(Modifier.weight(1f)){Text("ถ่ายรูปแล้ว",style=MaterialTheme.typography.titleMedium);Text("เลือกจำนวน แล้วบันทึกได้เลย",color=Muted,style=MaterialTheme.typography.bodyMedium)}
                }
                if(type==WorkType.STOCK){
                    var showNote by remember{mutableStateOf(state.note.isNotBlank())}
                    if(showNote)OutlinedTextField(state.note,vm::note,Modifier.fillMaxWidth().testTag("stock-note"),label={Text("โน้ต (ไม่บังคับ)")},placeholder={Text("200g / ห่อใหญ่")},singleLine=true,shape=RoundedCornerShape(16.dp))
                    else TextButton(onClick={showNote=true}){Icon(Icons.Rounded.EditNote,null);Spacer(Modifier.width(6.dp));Text("เพิ่มโน้ต")}
                }
            } else if(state.busy)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp);Text(if(state.phase==CapturePhase.SAVING)"กำลังบันทึก…" else "กำลังเตรียมภาพ…")}
            else {
                Text(if(type==WorkType.QA){if(noBarcode)"ไม่พบบาร์โค้ด • ขยับสินค้าให้ชัดแล้วลองใหม่" else "เล็งบาร์โค้ดในกรอบ ระบบจะอ่านอัตโนมัติ"}else "จัดสินค้าให้อยู่ในกรอบ แล้วกดถ่ายรูป",color=Muted,style=MaterialTheme.typography.bodyMedium)
                if(noBarcode)OutlinedButton(onClick=vm::rescan,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("สแกนใหม่")}
            }
            Spacer(Modifier.height(8.dp))
        }
        Surface(color=Color.White,tonalElevation=1.dp,shadowElevation=6.dp){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            if(state.phase==CapturePhase.CONFIRMING||state.phase==CapturePhase.SAVING){
                QuantityStepper(state.quantity,!state.busy,vm::quantity,vm::step)
                LargeActionButton(if(state.phase==CapturePhase.SAVING)"กำลังบันทึก…" else "บันทึก",Icons.Rounded.Check,enabled=state.canSave&&state.sessionId.isNotBlank(),tag="save-record",onClick={focus.clearFocus();keyboard?.hide();vm.save()})
                OutlinedButton(onClick={focus.clearFocus();keyboard?.hide();vm.rescan()},enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp).testTag("rescan")){Icon(Icons.Rounded.Refresh,null);Spacer(Modifier.width(8.dp));Text(if(type==WorkType.QA)"สแกนใหม่" else "ถ่ายใหม่")}
            }else if(type==WorkType.STOCK){LargeActionButton("ถ่ายรูปสินค้า",Icons.Rounded.PhotoCamera,ready&&!state.busy,"take-photo"){
                vm.takingPhoto();camera.photo(images.file(),vm::photoTaken)
            }}else Text("กำลังสแกน…",style=MaterialTheme.typography.titleMedium,color=Pink,modifier=Modifier.align(Alignment.CenterHorizontally))
            Text("บันทึกแล้ว ${state.savedCount} รายการ  •  ${dateLabel(state.date)}",style=MaterialTheme.typography.labelSmall,color=Muted,modifier=Modifier.align(Alignment.CenterHorizontally))
        }}
    }
}
