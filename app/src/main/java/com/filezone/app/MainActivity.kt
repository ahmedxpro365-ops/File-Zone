package com.filezone.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filezone.app.ui.FileZoneApp
import com.filezone.app.ui.theme.FileZoneTheme
import com.filezone.app.viewmodel.FileZoneViewModel
import com.filezone.app.viewmodel.UIThemeMode

class MainActivity : ComponentActivity() {
    
    private val viewModel: FileZoneViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            
            FileZoneTheme(darkTheme = themeMode == UIThemeMode.Dark) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FileZoneApp(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAndSetStoragePermission()
    }
}
