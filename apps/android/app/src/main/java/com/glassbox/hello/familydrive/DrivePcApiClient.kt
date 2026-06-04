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

    override suspend fun fetchDriveItems(limit: Int, before: Long?, sync: Boolean): Result<DriveItemsResponse> = safePcCall {
        val url = buildString {
            append("$driveBaseUrl/drive/items?limit=$limit")
            if (before != null) append("&before=$before")
            if (sync) append("&sync=true")
        }
        gson.fromJson(get(url), DriveItemsResponse::class.java)
    }

    override suspend fun fetchDriveTrash(limit: Int, before: Long?, sync: Boolean): Result<DriveItemsResponse> = safePcCall {
        val url = buildString {
            append("$driveBaseUrl/drive/trash?limit=$limit")
            if (before != null) append("&before=$before")
            if (sync) append("&sync=true")
        }
        gson.fromJson(get(url), DriveItemsResponse::class.java)
    }

    override suspend fun fetchDriveDeleteLimit(userId: String): Result<DriveDeleteLimit> = safePcCall {
        val url = "$driveBaseUrl/drive/delete-limit?userId=${encodePathValue(userId)}"
        gson.fromJson(get(url), DriveDeleteLimit::class.java)
    }

    override suspend fun fetchDriveEvents(): Result<List<DriveEvent>> = safePcCall {
        gson.fromJson(get("$driveBaseUrl/drive/events"), DriveEventsResponse::class.java).events
    }

    override suspend fun createDriveEvent(name: String, userId: String): Result<DriveEvent> = safePcCall {
        val body = gson.toJson(mapOf("name" to name, "userId" to userId))
            .toRequestBody("application/json".toMediaType())
        val response = request(Request.Builder().url("$driveBaseUrl/drive/events").post(body).build())
        gson.fromJson(response, DriveEvent::class.java)
    }

    override suspend fun fetchDriveCircles(): Result<List<DriveCircle>> = safePcCall {
        gson.fromJson(get("$driveBaseUrl/drive/circles"), DriveCirclesResponse::class.java).circles
    }

    override suspend fun createDriveCircle(name: String, ownerUserId: String, members: List<DriveCircleMember>): Result<DriveCircle> = safePcCall {
        val body = gson.toJson(
            mapOf(
                "name" to name,
                "userId" to ownerUserId,
                "members" to members.map { member ->
                    mapOf(
                        "userId" to member.userId,
                        "role" to member.role,
                        "name" to member.name,
                        "avatar" to member.avatar
                    )
                }
            )
        ).toRequestBody("application/json".toMediaType())
        val response = request(Request.Builder().url("$driveBaseUrl/drive/circles").post(body).build())
        gson.fromJson(response, DriveCircle::class.java)
    }

    override suspend fun uploadDriveFile(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        uploaderId: String,
        plan: DriveUploadPlan
    ): Result<DriveUploadResponse> = safePcCall {
        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("uploaderId", uploaderId)
            .addFormDataPart("eventName", plan.eventName)
            .addFormDataPart("batchId", plan.batchId)
            .addFormDataPart("files", fileName, bytes.toRequestBody(mimeType.toMediaType()))
        plan.eventId?.takeIf { it.isNotBlank() }?.let { bodyBuilder.addFormDataPart("eventId", it) }
        plan.circleIds.forEach { bodyBuilder.addFormDataPart("circleIds[]", it) }
        plan.allowedUserIds.forEach { bodyBuilder.addFormDataPart("allowedUserIds[]", it) }
        val body = bodyBuilder.build()
        val response = request(Request.Builder().url("$driveBaseUrl/drive/upload").post(body).build())
        gson.fromJson(response, DriveUploadResponse::class.java)
    }

    override suspend fun deleteDriveItem(itemId: String, userId: String): Result<DriveDeleteResponse> = safePcCall {
        val url = "$driveBaseUrl/drive/items/${encodePathValue(itemId)}?userId=${encodePathValue(userId)}"
        val response = request(Request.Builder().url(url).delete().build())
        gson.fromJson(response, DriveDeleteResponse::class.java)
    }

    override suspend fun restoreDriveItem(itemId: String): Result<DriveItem> = safePcCall {
        val response = request(Request.Builder().url("$driveBaseUrl/drive/items/${encodePathValue(itemId)}/restore").post("".toRequestBody()).build())
        gson.fromJson(response, DriveItemActionResponse::class.java).item
            ?: throw Exception("Restore response was empty")
    }

    override suspend fun permanentlyDeleteDriveItem(itemId: String): Result<Unit> = safePcCall {
        request(Request.Builder().url("$driveBaseUrl/drive/items/${encodePathValue(itemId)}/permanent").delete().build())
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
