package com.multitasks.store

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.multitasks.store.navigation.AppNavigation
import com.multitasks.store.ui.MultiTasksTheme

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MultiTasksTheme { AppNavigation() } }
    }
}
