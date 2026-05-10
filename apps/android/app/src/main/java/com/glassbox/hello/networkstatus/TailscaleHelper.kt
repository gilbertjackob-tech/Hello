package com.glassbox.hello.networkstatus

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object TailscaleHelper {
    private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
    private const val TAILSCALE_RECEIVER = "com.tailscale.ipn.IPNReceiver"
    private const val ACTION_CONNECT = "com.tailscale.ipn.CONNECT_VPN"
    private const val ACTION_DISCONNECT = "com.tailscale.ipn.DISCONNECT_VPN"

    fun connectVpn(context: Context): Boolean = sendVpnIntent(context, ACTION_CONNECT)

    fun disconnectVpn(context: Context): Boolean = sendVpnIntent(context, ACTION_DISCONNECT)

    fun openTailscaleSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_VPN_SETTINGS
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        openTailscale(context)
    }

    fun openTailscale(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)
            if (intent != null) {
                context.startActivity(intent)
                return
            }
        } catch (e: Exception) {
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$TAILSCALE_PACKAGE"))
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$TAILSCALE_PACKAGE"))
            context.startActivity(intent)
        }
    }

    private fun sendVpnIntent(context: Context, action: String): Boolean {
        val intent = Intent(action).apply {
            setClassName(TAILSCALE_PACKAGE, TAILSCALE_RECEIVER)
            `package` = TAILSCALE_PACKAGE
        }
        return runCatching {
            context.sendBroadcast(intent)
            true
        }.getOrElse {
            false
        }
    }
}
