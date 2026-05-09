package com.glassbox.hello

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import com.glassbox.hello.core.rememberHelloSettingsState
import com.glassbox.hello.ui.HelloApp
import com.glassbox.hello.ui.theme.HelloTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            rememberHelloSettingsState(this)
            HelloTheme(themeMode = "dark") {
                HelloApp(darkTheme = true)
            }
        }
    }
}
