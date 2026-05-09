package com.glassbox.hello.networkstatus

import android.content.Context
import android.content.Intent
import android.net.Uri

object TailscaleHelper {
    fun openTailscale(context: Context) {
        try {
            // Try to launch Tailscale app directly
            val intent = context.packageManager.getLaunchIntentForPackage("com.tailscale.ipn")
            if (intent != null) {
                context.startActivity(intent)
                return
            }
        } catch (e: Exception) {
            // Package not found or not launchable, continue to Play Store
        }

        try {
            // Try to open Play Store
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.tailscale.ipn"))
            context.startActivity(intent)
        } catch (e: Exception) {
            // Play Store not available, open web browser
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.tailscale.ipn"))
            context.startActivity(intent)
        }
    }
}
