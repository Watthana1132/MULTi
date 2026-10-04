package com.multitasks.store.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import com.multitasks.store.R
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val Pink=Color(0xFFD91F6F)
val SoftPink=Color(0xFFFCE7F1)
val CanvasPink=Color(0xFFFFF7FB)
val Ink=Color(0xFF17151A)
val Muted=Color(0xFF77727A)

@Composable fun MultiTasksTheme(content: @Composable ()->Unit) {
    MaterialTheme(colorScheme=lightColorScheme(primary=Pink,onPrimary=Color.White,primaryContainer=SoftPink,onPrimaryContainer=Pink,secondary=Pink,onSecondary=Color.White,secondaryContainer=SoftPink,onSecondaryContainer=Pink,tertiary=Pink,background=CanvasPink,surface=Color.White,onSurface=Ink,onSurfaceVariant=Muted,surfaceContainer=SoftPink,surfaceContainerLow=CanvasPink,surfaceContainerHigh=CanvasPink,surfaceTint=Pink,error=Color(0xFFBA1A1A)),shapes=Shapes(small=RoundedCornerShape(14.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(28.dp)),content=content)
}
fun dateLabel(value: String)=runCatching{LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM uuuu",Locale.forLanguageTag("th")))}.getOrDefault(value)
fun timeLabel(value: Long)=Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppHeader(title: String,back: (() -> Unit)?=null,actions: @Composable RowScope.()->Unit={}) {
    TopAppBar(title={Text(title,fontWeight=FontWeight.Bold)},navigationIcon={if(back!=null)IconButton(onClick=back){Icon(Icons.AutoMirrored.Rounded.ArrowBack,stringResource(R.string.back))}},actions=actions,colors=TopAppBarDefaults.topAppBarColors(containerColor=CanvasPink))
}
@Composable fun LargeActionButton(label: String,icon: ImageVector?=null,enabled: Boolean=true,tag: String="",onClick: ()->Unit) {
    Button(onClick,Modifier.fillMaxWidth().heightIn(min=58.dp).testTag(tag),enabled=enabled,shape=RoundedCornerShape(18.dp)){
        if(icon!=null){Icon(icon,null);Spacer(Modifier.width(10.dp))};Text(label,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    }
}
@Composable fun EmptyState(title: String,detail: String){Column(Modifier.fillMaxWidth().padding(vertical=36.dp,horizontal=20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Rounded.PhotoLibrary,null,Modifier.size(42.dp),tint=Pink);Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text(detail,color=Muted,style=MaterialTheme.typography.bodyMedium)}}
@Composable fun ProductThumbnail(path: String,modifier: Modifier=Modifier,description: String="รูปสินค้า",fit: Boolean=false){
    if(path.isBlank())Box(modifier.background(SoftPink),contentAlignment=Alignment.Center){Icon(Icons.Rounded.Inventory2,description,tint=Pink)}
    else {
        var loading by remember(path){mutableStateOf(true)}
        var failed by remember(path){mutableStateOf(false)}
        Box(modifier,contentAlignment=Alignment.Center){
            AsyncImage(model=ImageRequest.Builder(LocalContext.current).data(java.io.File(path)).crossfade(false).build(),contentDescription=description,modifier=Modifier.fillMaxSize(),contentScale=if(fit)ContentScale.Fit else ContentScale.Crop,onSuccess={loading=false},onError={loading=false;failed=true})
            if(loading||failed)Icon(if(failed)Icons.Rounded.BrokenImage else Icons.Rounded.Image,if(failed)"อ่านภาพไม่ได้" else "กำลังโหลดภาพ",tint=Muted)
        }
    }
}
@Composable fun QuantityStepper(value: String,enabled: Boolean=true,onChange: (String)->Unit,onStep: (Int)->Unit){
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(stringResource(R.string.quantity),style=MaterialTheme.typography.labelLarge,color=Muted)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
            RepeatButton(Icons.Rounded.Remove,"ลดจำนวน",enabled){onStep(-1)}
            OutlinedTextField(value,onChange,Modifier.weight(1f).testTag("quantity"),enabled=enabled,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus();keyboard?.hide()}),textStyle=MaterialTheme.typography.headlineSmall.copy(textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontWeight=FontWeight.Bold),shape=RoundedCornerShape(16.dp))
            RepeatButton(Icons.Rounded.Add,"เพิ่มจำนวน",enabled){onStep(1)}
        }
    }
}
@Composable private fun RepeatButton(icon: ImageVector,label: String,enabled: Boolean,step: ()->Unit){
    val action by rememberUpdatedState(step)
    FilledTonalIconButton(onClick=step,enabled=enabled,modifier=Modifier.size(64.dp).testTag(label).pointerInput(enabled){
        if(enabled)detectTapGestures(onPress={coroutineScope{val repeat=launch{delay(400);while(isActive){action();delay(90)}};val released=tryAwaitRelease();val repeating=repeat.isActive;repeat.cancel();if(released&&repeating)action()}})
    },shape=RoundedCornerShape(18.dp)){Icon(icon,label,Modifier.size(30.dp))}
}
