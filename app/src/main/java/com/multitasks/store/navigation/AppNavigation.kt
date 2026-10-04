package com.multitasks.store.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.multitasks.store.model.*
import com.multitasks.store.ui.*
import com.multitasks.store.viewmodel.*

@Composable fun AppNavigation(){
    val nav=rememberNavController()
    val capture: CaptureViewModel=viewModel()
    val history: HistoryViewModel=viewModel()
    val backStack by nav.currentBackStackEntryAsState()
    val route=backStack?.destination?.route?:"home"
    val snack=remember{SnackbarHostState()}
    val message by history.message.collectAsStateWithLifecycle()
    LaunchedEffect(message){if(message.isNotBlank()){snack.showSnackbar(message);history.message.value=""}}
    fun go(destination: String){nav.navigate(destination){launchSingleTop=true}}
    fun home(){nav.navigate("home"){popUpTo("home"){inclusive=true};launchSingleTop=true}}
    fun start(type: WorkType){capture.begin(type);go("capture/${type.name}")}
    fun back(){if(!nav.popBackStack())home()}
    Scaffold(containerColor=CanvasPink,contentWindowInsets=WindowInsets.safeDrawing,snackbarHost={SnackbarHost(snack)},bottomBar={
        if(!route.startsWith("capture")&&!route.startsWith("viewer"))NavigationBar(containerColor=MaterialTheme.colorScheme.surface){
            NavigationBarItem(selected=route=="home",onClick={home()},icon={Icon(Icons.Rounded.Home,null)},label={Text("หน้าหลัก")})
            NavigationBarItem(selected=route=="quick",onClick={go("quick")},icon={Icon(Icons.Rounded.PhotoCamera,null)},label={Text("กล้อง")})
            NavigationBarItem(selected=route=="more"||route=="help",onClick={go("more")},icon={Icon(Icons.Rounded.MoreHoriz,null)},label={Text("เพิ่มเติม")})
        }
    }){padding->
        NavHost(nav,"home",Modifier.padding(padding).consumeWindowInsets(padding)){
            composable("home"){MainPage({start(WorkType.QA)},{start(WorkType.STOCK)},{go("create")},{history.filter.value=HistoryFilter();go("history")},{go("more")})}
            composable("quick"){QuickCapture(::back,::start)}
            composable("capture/{type}"){entry->CaptureScreen(WorkType.valueOf(entry.arguments!!.getString("type")!!),capture,::back)}
            composable("create"){CreateWork(history,::back){session->capture.begin(session.type,session);go("capture/${session.type.name}")}}
            composable("history"){HistoryScreen(history,::back){go("viewer/$it")}}
            composable("viewer/{id}"){entry->ImageViewer(entry.arguments!!.getString("id")!!.toLong(),history,::back)}
            composable("more"){MoreScreen(capture,history,::back,{trash->history.filter.value=HistoryFilter(trash=trash);go("history")},{go("help")})}
            composable("help"){HelpScreen(::back)}
        }
    }
}
