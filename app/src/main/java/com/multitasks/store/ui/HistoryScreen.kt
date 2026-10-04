package com.multitasks.store.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.multitasks.store.model.*
import com.multitasks.store.viewmodel.*
import java.time.LocalDate

fun chooseDate(context: android.content.Context,current: String,onPick: (String)->Unit){
    val d=runCatching{LocalDate.parse(current)}.getOrDefault(LocalDate.now())
    DatePickerDialog(context,{_,y,m,day->onPick(LocalDate.of(y,m+1,day).toString())},d.year,d.monthValue-1,d.dayOfMonth).show()
}

@OptIn(ExperimentalMaterial3Api::class,ExperimentalFoundationApi::class)
@Composable fun HistoryScreen(vm: HistoryViewModel,onBack: ()->Unit,onOpen: (Long)->Unit){
    val records by vm.records.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var gallery by rememberSaveable{mutableStateOf(false)}
    var filtering by rememberSaveable{mutableStateOf(false)}
    var selecting by rememberSaveable{mutableStateOf(false)}
    var selected by remember{mutableStateOf(setOf<Long>())}
    val visibleIds=records.map{it.id}.toSet()
    val selection=selected.intersect(visibleIds)
    var folders by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize()){
        AppHeader(if(filter.trash)"ถังขยะ" else if(gallery)"แกลเลอรี" else "ประวัติ",onBack,actions={
            IconButton(onClick={gallery=!gallery}){Icon(if(gallery)Icons.Rounded.ViewList else Icons.Rounded.GridView,if(gallery)"ดูรายการ" else "ดูแกลเลอรี")}
            IconButton(onClick={selecting=!selecting;selected=emptySet()}){Icon(Icons.Rounded.Checklist,"เลือกหลายรายการ")}
        })
        Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf(null,WorkType.QA,WorkType.STOCK).forEach{type->FilterChip(selected=filter.type==type,onClick={vm.filter.value=filter.copy(type=type)},label={Text(type?.name?:"ทั้งหมด")},modifier=Modifier.heightIn(min=48.dp))}
            }
            OutlinedTextField(filter.query,{vm.filter.value=filter.copy(query=it)},Modifier.fillMaxWidth().testTag("history-search"),placeholder={Text("ค้นหาบาร์โค้ดหรือโน้ต")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true,shape=RoundedCornerShape(18.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                TextButton(onClick={filtering=!filtering}){Icon(Icons.Rounded.DateRange,null);Text(if(filter.from.isBlank()&&filter.to.isBlank())"กรองวันที่" else "กำลังกรองวันที่")}
                TextButton(onClick={folders=true}){Icon(Icons.Rounded.FolderOpen,null);Text(if(filter.session.isBlank())"รอบงาน" else "รอบงานที่เลือก")}
            }
            if(filtering){
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={chooseDate(context,filter.from){date->vm.filter.value=filter.copy(from=date,to=filter.to.takeIf{it.isBlank()||it>=date}?:date)}},modifier=Modifier.weight(1f)){Text(if(filter.from.isBlank())"ตั้งแต่วันที่" else dateLabel(filter.from))}
                    OutlinedButton(onClick={chooseDate(context,filter.to){date->vm.filter.value=filter.copy(to=date,from=filter.from.takeIf{it.isBlank()||it<=date}?:date)}},modifier=Modifier.weight(1f)){Text(if(filter.to.isBlank())"ถึงวันที่" else dateLabel(filter.to))}
                }
            }
            if(filter.from.isNotBlank()||filter.to.isNotBlank()||filter.session.isNotBlank())TextButton(onClick={vm.filter.value=filter.copy(from="",to="",session="")}){Text("ล้างตัวกรองวันที่และรอบงาน")}
            if(selecting)Row(verticalAlignment=Alignment.CenterVertically){Checkbox(selection.size==records.size&&records.isNotEmpty(),{selected=if(it)visibleIds else emptySet()});Text("เลือกทั้งหมด (${selection.size})");Spacer(Modifier.weight(1f));TextButton(enabled=selection.isNotEmpty(),onClick={if(filter.trash)vm.restore(selection)else vm.delete(selection);selected=emptySet();selecting=false}){Text(if(filter.trash)"กู้คืน" else "ย้ายไปถังขยะ",color=MaterialTheme.colorScheme.error)}}
        }
        if(records.isEmpty())EmptyState(if(filter.trash)"ถังขยะว่าง" else "ยังไม่มีรายการ",if(filter.session.isNotBlank())"รอบงานนี้ยังไม่มีรายการที่ตรงกับตัวกรอง" else "เริ่มบันทึกจากหน้าหลัก หรือเปลี่ยนตัวกรอง")
        else {
            val groups=records.groupBy{it.date}.toSortedMap(reverseOrder())
            fun click(record: WorkRecord){if(selecting)selected=if(record.id in selected)selected-record.id else selected+record.id else onOpen(record.id)}
            if(gallery)LazyVerticalGrid(GridCells.Adaptive(150.dp),Modifier.weight(1f).testTag("gallery"),contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                groups.forEach{(date,rows)->
                    item(key="date-$date",span={GridItemSpan(maxLineSpan)}){Text(dateLabel(date),color=Pink,style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(vertical=8.dp))}
                    items(rows,key={it.id}){record->
                        Card(onClick={click(record)},modifier=Modifier.testTag("record-${record.id}"),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
                            Box{ProductThumbnail(record.photo,Modifier.fillMaxWidth().aspectRatio(1f));Surface(Modifier.align(Alignment.BottomEnd).padding(8.dp),shape=RoundedCornerShape(12.dp),color=Pink){Text("+${record.quantity}",color=MaterialTheme.colorScheme.onPrimary,modifier=Modifier.padding(horizontal=10.dp,vertical=5.dp),fontWeight=FontWeight.Bold)};if(selecting)Checkbox(record.id in selection,{click(record)},Modifier.align(Alignment.TopStart))}
                            if(record.type==WorkType.QA)ProductThumbnail(record.barcodeImage,Modifier.fillMaxWidth().height(48.dp).background(androidx.compose.ui.graphics.Color.White),"บาร์โค้ดย่อ",true)
                            Text("${record.type.name} · ${timeLabel(record.timestamp)}",Modifier.padding(10.dp),style=MaterialTheme.typography.labelMedium,color=Muted)
                        }
                    }
                }
            } else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                groups.forEach{(date,rows)->item(key="date-$date"){Text(dateLabel(date),color=Pink,style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(vertical=6.dp))}
                    items(rows,key={it.id}){record->HistoryItem(record,selecting,record.id in selection){click(record)}}
                }
            }
        }
    }
    if(folders)ModalBottomSheet(onDismissRequest={folders=false}){
        Text("เลือกรอบงาน",Modifier.padding(20.dp),style=MaterialTheme.typography.titleLarge)
        TextButton(onClick={vm.filter.value=filter.copy(session="");folders=false},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("ทุกรอบงาน")}
        LazyColumn(Modifier.heightIn(max=400.dp),contentPadding=PaddingValues(20.dp)){
            items(sessions,key={it.id}){s->ListItem(headlineContent={Text("${s.type.name} · ${dateLabel(s.date)}")},supportingContent={Text("สร้าง ${dateLabel(java.time.Instant.ofEpochMilli(s.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString())} ${timeLabel(s.createdAt)}")},leadingContent={Icon(Icons.Rounded.Folder,null)},modifier=Modifier.clickable{vm.filter.value=filter.copy(session=s.id);folders=false})}
        }
    }
}

@Composable fun HistoryItem(record: WorkRecord,selecting: Boolean,checked: Boolean,onClick: ()->Unit){
    Card(onClick=onClick,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth().testTag("record-${record.id}")){
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
            if(selecting)Checkbox(checked,{onClick()})
            ProductThumbnail(record.photo,Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(if(record.type==WorkType.QA)record.barcode else record.note.ifBlank{"STOCK"},style=MaterialTheme.typography.titleSmall)
                Text("${record.quantity} ชิ้น · ${timeLabel(record.timestamp)}",color=Muted,style=MaterialTheme.typography.bodyMedium)
                if(record.legacyExpiry)Text("วันหมดอายุจากรุ่นเดิม",color=Muted,style=MaterialTheme.typography.labelSmall)
                if(record.done)Text("✓ ทำรายการแล้ว",color=Pink,style=MaterialTheme.typography.labelSmall)
            }
            if(!selecting)Icon(Icons.Rounded.ChevronRight,null,tint=Muted)
        }
    }
}
