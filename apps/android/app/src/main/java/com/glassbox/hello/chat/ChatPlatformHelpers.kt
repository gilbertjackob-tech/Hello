package com.glassbox.hello.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

data class CameraCapture(val uri: Uri, val file: File)

fun createCameraCapture(context: Context): CameraCapture {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "hello-camera-${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return CameraCapture(uri, file)
}

@SuppressLint("MissingPermission")
fun getLastKnownHelloLocation(context: Context): Location? {
    val hasFine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return manager.getProviders(true)
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
}

class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    @Suppress("DEPRECATION")
    fun start(): File {
        stop(delete = true)
        val dir = File(context.cacheDir, "voice-notes").apply { mkdirs() }
        val file = File(dir, "hello-voice-${System.currentTimeMillis()}.m4a")
        val nextRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(96_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = nextRecorder
        outputFile = file
        return file
    }

    fun stop(delete: Boolean = false): File? {
        val file = outputFile
        runCatching { recorder?.stop() }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile = null
        if (delete) {
            file?.delete()
            return null
        }
        return file?.takeIf { it.exists() && it.length() > 0 }
    }
}
