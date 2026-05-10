package com.glassbox.hello.utils

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Runtime permission helper for browser, downloads, camera, microphone, and location workflows.
 */
class PermissionManager(private val activity: Activity) {
    /**
     * Requests every currently missing browser permission.
     */
    fun requestAllPermissions(requestCode: Int = REQUEST_CODE_PERMISSIONS) {
        val missing = requiredPermissions().filterNot { permission -> hasPermission(permission) }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), requestCode)
        }
    }

    /**
     * Requests selected permissions when any are missing.
     */
    fun requestPermissions(permissions: Array<String>, requestCode: Int = REQUEST_CODE_PERMISSIONS) {
        val missing = permissions.filterNot { permission -> hasPermission(permission) }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), requestCode)
        }
    }

    /**
     * Returns true when a permission is already granted.
     */
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns true when all required browser permissions are granted.
     */
    fun hasAllPermissions(): Boolean {
        return requiredPermissions().all { permission -> hasPermission(permission) }
    }

    /**
     * Returns true when the UI should explain why a permission is needed.
     */
    fun shouldShowRationale(permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    /**
     * Returns permissions that were denied from a request result.
     */
    fun deniedPermissions(permissions: Array<String>, grantResults: IntArray): List<String> {
        return permissions.zip(grantResults.toTypedArray())
            .filter { (_, result) -> result != PackageManager.PERMISSION_GRANTED }
            .map { (permission, _) -> permission }
    }

    companion object {
        const val REQUEST_CODE_PERMISSIONS: Int = 1001

        /**
         * Returns the permission set required for the current Android version.
         */
        fun requiredPermissions(): Array<String> {
            val permissions = mutableListOf(
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions += Manifest.permission.POST_NOTIFICATIONS
                permissions += Manifest.permission.READ_MEDIA_IMAGES
                permissions += Manifest.permission.READ_MEDIA_VIDEO
            } else {
                permissions += Manifest.permission.READ_EXTERNAL_STORAGE
            }
            return permissions.toTypedArray()
        }
    }
}
