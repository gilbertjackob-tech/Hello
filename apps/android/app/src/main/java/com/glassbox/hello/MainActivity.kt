package com.glassbox.hello

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import com.glassbox.hello.core.rememberHelloSettingsState
import com.glassbox.hello.notifications.HelloNotificationCenter
import com.glassbox.hello.ui.HelloApp
import com.glassbox.hello.ui.theme.HelloTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HelloNotificationCenter.handleLaunchIntent(intent)
        enableEdgeToEdge()
        setContent {
            val settings by rememberHelloSettingsState(this)
            HelloTheme(themeMode = settings.themeMode) { darkTheme ->
                HelloApp(darkTheme = darkTheme)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        HelloNotificationCenter.handleLaunchIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        HelloNotificationCenter.setAppForeground(true)
    }

    override fun onStop() {
        HelloNotificationCenter.setAppForeground(false)
        super.onStop()
    }
}
