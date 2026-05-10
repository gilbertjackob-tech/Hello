package com.glassbox.hello

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity

class SplashActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val launchMain = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        handler.postDelayed(launchMain, 900)
    }

    override fun onDestroy() {
        handler.removeCallbacks(launchMain)
        super.onDestroy()
    }
}
