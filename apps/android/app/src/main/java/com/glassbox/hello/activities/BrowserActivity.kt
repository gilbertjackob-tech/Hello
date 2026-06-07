package com.glassbox.hello.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.addCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.glassbox.hello.MainActivity
import com.glassbox.hello.browser.BrowserScreen
import com.glassbox.hello.core.rememberHelloSettingsState
import com.glassbox.hello.ui.theme.HelloTheme

class BrowserActivity : ComponentActivity() {
    private val launchState = mutableStateOf(BrowserLaunchRequest())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchState.value = BrowserLaunchRequest.from(intent)
        enableEdgeToEdge()
        configureImmersiveMode()
        onBackPressedDispatcher.addCallback(this) {
            returnToHello()
        }
        setContent {
            val settings by rememberHelloSettingsState(this)
            val launch by launchState
            HelloTheme(themeMode = settings.themeMode) {
                BrowserScreen(
                    launchUrl = launch.url,
                    launchProfileId = launch.profileId,
                    launchTabId = launch.tabId,
                    showReturnBubble = true,
                    onReturnToHello = ::returnToHello
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchState.value = BrowserLaunchRequest.from(intent)
    }

    private fun configureImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun returnToHello() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_PROFILE_ID = "extra_profile_id"
        private const val EXTRA_TAB_ID = "extra_tab_id"

        fun createIntent(
            context: Context,
            url: String? = null,
            profileId: String? = null,
            tabId: String? = null
        ): Intent {
            return Intent(context, BrowserActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (context !is ComponentActivity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                url?.let { putExtra(EXTRA_URL, it) }
                profileId?.let { putExtra(EXTRA_PROFILE_ID, it) }
                tabId?.let { putExtra(EXTRA_TAB_ID, it) }
            }
        }
    }

    private data class BrowserLaunchRequest(
        val url: String? = null,
        val profileId: String? = null,
        val tabId: String? = null
    ) {
        companion object {
            fun from(intent: Intent): BrowserLaunchRequest =
                BrowserLaunchRequest(
                    url = intent.getStringExtra(EXTRA_URL),
                    profileId = intent.getStringExtra(EXTRA_PROFILE_ID),
                    tabId = intent.getStringExtra(EXTRA_TAB_ID)
                )
        }
    }
}
