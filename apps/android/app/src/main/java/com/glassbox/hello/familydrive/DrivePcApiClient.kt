package com.glassbox.hello.familydrive

import com.glassbox.hello.core.AppConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DrivePcApiClient : DrivePcApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .callTimeout(600, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val driveBaseUrl = AppConfig.DRIVE_API_BASE

    override suspend fun fetchDriveItems(limit: Int, before: Long?): Result<DriveItemsResponse> = safePcCall {
        val url = buildString {
            append("$driveBaseUrl/drive/items?limit=$limit")
            if (before != null) append("&before=$before")
        }
        gson.fromJson(get(url), DriveItemsResponse::class.java)
    }

    override suspend fun uploadDriveFile(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        uploaderId: String
    ): Result<DriveUploadResponse> = safePcCall {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("uploaderId", uploaderId)
            .addFormDataPart("files", fileName, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val response = request(Request.Builder().url("$driveBaseUrl/drive/upload").post(body).build())
        gson.fromJson(response, DriveUploadResponse::class.java)
    }

    override suspend fun deleteDriveItem(itemId: String): Result<Unit> = safePcCall {
        request(Request.Builder().url("$driveBaseUrl/drive/items/${encodePathValue(itemId)}").delete().build())
        Unit
    }

    private suspend fun get(url: String): String = request(Request.Builder().url(url).get().build())

    private suspend fun request(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                throw Exception("PC Drive HTTP ${response.code}: ${responseBody ?: response.message}")
            }
            responseBody ?: throw Exception("Empty PC Drive response")
        }
    }

    private suspend inline fun <T> safePcCall(crossinline block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: Exception) {
            Result.failure(error)
        }

    private fun encodePathValue(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
