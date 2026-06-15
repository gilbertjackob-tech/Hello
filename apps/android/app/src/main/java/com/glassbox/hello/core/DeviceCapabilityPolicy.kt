package com.glassbox.hello.core

import android.app.ActivityManager
import android.content.Context

data class DeviceCapabilityProfile(
    val reduceDecorativeRendering: Boolean
)

object DeviceCapabilityPolicy {
    @Volatile
    private var cachedProfile: DeviceCapabilityProfile? = null

    fun profile(context: Context): DeviceCapabilityProfile {
        cachedProfile?.let { return it }
        return synchronized(this) {
            cachedProfile ?: buildProfile(context.applicationContext).also { cachedProfile = it }
        }
    }

    private fun buildProfile(context: Context): DeviceCapabilityProfile {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val totalMemoryGb = memoryInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return DeviceCapabilityProfile(
            reduceDecorativeRendering = activityManager?.isLowRamDevice == true || totalMemoryGb in 0.1..3.5
        )
    }
}
