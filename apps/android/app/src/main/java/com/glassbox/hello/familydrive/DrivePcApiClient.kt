package com.glassbox.hello.familydrive

import com.glassbox.hello.core.AppConfig
import com.glassbox.hello.debug.HelloDebugLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DrivePcApiClient : DrivePcApi {
    class DriveApiException(
        val code: Int,
        val responseBody: String
    ) : Exception("PC Drive HTTP $code: $responseBody")

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .callTimeout(600, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val driveBaseUrl = AppConfig.DRIVE_API_BASE

    override suspend fun fetchDriveItems(userId: String, limit: Int, before: Long?, sync: Boolean, circleId: String?, eventId: String?): Result<DriveItemsResponse> = safePcCall {
        val url = buildString {
            append("$driveBaseUrl/drive/items?limit=$limit&userId=${encodePathValue(userId)}")
            if (before != null) append("&before=$before")
            if (sync) append("&sync=true")
            if (!circleId.isNullOrBlank()) append("&circleId=${encodePathValue(circleId)}")
            if (!eventId.isNullOrBlank()) append("&eventId=${encodePathValue(eventId)}")
        }
        HelloDebugLog.d("DriveApi", "fetchDriveItems userId=$userId limit=$limit before=$before sync=$sync circleId=$circleId eventId=$eventId")
        gson.fromJson(get(url), DriveItemsResponse::class.java) ?: DriveItemsResponse()
    }

    override suspend fun fetchDriveTrash(userId: String, limit: Int, before: Long?, sync: Boolean, circleId: String?, eventId: String?): Result<DriveItemsResponse> = safePcCall {
        val url = buildString {
            append("$driveBaseUrl/drive/trash?limit=$limit&userId=${encodePathValue(userId)}")
            if (before != null) append("&before=$before")
            if (sync) append("&sync=true")
            if (!circleId.isNullOrBlank()) append("&circleId=${encodePathValue(circleId)}")
            if (!eventId.isNullOrBlank()) append("&eventId=${encodePathValue(eventId)}")
        }
        HelloDebugLog.d("DriveApi", "fetchDriveTrash userId=$userId limit=$limit before=$before sync=$sync circleId=$circleId eventId=$eventId")
        gson.fromJson(get(url), DriveItemsResponse::class.java) ?: DriveItemsResponse()
    }

    override suspend fun fetchDriveDeleteLimit(userId: String): Result<DriveDeleteLimit> = safePcCall {
        val url = "$driveBaseUrl/drive/delete-limit?userId=${encodePathValue(userId)}"
        HelloDebugLog.d("DriveApi", "fetchDriveDeleteLimit userId=$userId")
        gson.fromJson(get(url), DriveDeleteLimit::class.java) ?: DriveDeleteLimit()
    }

    override suspend fun fetchDriveEvents(userId: String, circleId: String?): Result<List<DriveEvent>> = safePcCall {
        val url = buildString {
            append("$driveBaseUrl/drive/events?userId=${encodePathValue(userId)}")
            if (!circleId.isNullOrBlank()) append("&circleId=${encodePathValue(circleId)}")
        }
        HelloDebugLog.d("DriveApi", "fetchDriveEvents userId=$userId circleId=$circleId")
        parseDriveEvents(get(url))
    }

    override suspend fun createDriveEvent(name: String, userId: String, circleId: String): Result<DriveEvent> = safePcCall {
        HelloDebugLog.d("DriveApi", "createDriveEvent userId=$userId circleId=$circleId name=${HelloDebugLog.snippet(name)}")
        val body = gson.toJson(mapOf("name" to name, "userId" to userId, "circleId" to circleId))
            .toRequestBody("application/json".toMediaType())
        val response = request(Request.Builder().url("$driveBaseUrl/drive/circles/${encodePathValue(circleId)}/events").post(body).build())
        parseDriveEvent(response) ?: throw Exception("Create event response was empty")
    }

    override suspend fun renameDriveEvent(eventId: String, userId: String, name: String): Result<DriveEvent> = safePcCall {
        val body = gson.toJson(mapOf("userId" to userId, "name" to name)).toRequestBody("application/json".toMediaType())
        val response = request(Request.Builder().url("$driveBaseUrl/drive/events/${encodePathValue(eventId)}").patch(body).build())
        parseDriveEvent(response) ?: throw Exception("Rename event response was empty")
    }

    override suspend fun deleteDriveEvent(eventId: String, userId: String): Result<Unit> = safePcCall {
        request(Request.Builder().url("$driveBaseUrl/drive/events/${encodePathValue(eventId)}?userId=${encodePathValue(userId)}").delete().build())
        Unit
    }

    override suspend fun fetchDriveCircles(userId: String): Result<List<DriveCircle>> = safePcCall {
        HelloDebugLog.d("DriveApi", "fetchDriveCircles userId=$userId")
        parseDriveCircles(get("$driveBaseUrl/drive/circles?userId=${encodePathValue(userId)}"))
    }

    override suspend fun createDriveCircle(id: String?, name: String, ownerUserId: String, members: List<DriveCircleMember>): Result<DriveCircle> = safePcCall {
        HelloDebugLog.d("DriveApi", "createDriveCircle id=${id.orEmpty()} ownerUserId=$ownerUserId name=${HelloDebugLog.snippet(name)} members=${members.size}")
        val body = gson.toJson(
            mapOf(
                "id" to id,
                "name" to name,
                "userId" to ownerUserId,
                "members" to members.map { member ->
                    mapOf(
                        "userId" to member.userId,
                        "role" to member.role,
                        "name" to member.name,
                        "username" to member.username,
                        "avatar" to member.avatar
                    )
                }
            )
        ).toRequestBody("application/json".toMediaType())
        val response = request(Request.Builder().url("$driveBaseUrl/drive/circles").post(body).build())
        parseDriveCircle(response) ?: throw Exception("Create circle response was empty")
    }

    override suspend fun uploadDriveCircleAvatar(circleId: String, userId: String, fileName: String, mimeType: String, bytes: ByteArray): Result<DriveCircle> = safePcCall {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("userId", userId)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val response = request(Request.Builder().url("$driveBaseUrl/drive/circles/${encodePathValue(circleId)}/avatar").post(body).build())
        parseDriveCircle(response) ?: throw Exception("Circle profile picture response was empty")
    }

    override suspend fun leaveDriveCircle(circleId: String, userId: String): Result<Unit> = safePcCall {
        val body = gson.toJson(mapOf("userId" to userId)).toRequestBody("application/json".toMediaType())
        request(Request.Builder().url("$driveBaseUrl/drive/circles/${encodePathValue(circleId)}/leave").post(body).build())
        Unit
    }

    override suspend fun deleteDriveCircle(circleId: String, userId: String): Result<Unit> = safePcCall {
        request(Request.Builder().url("$driveBaseUrl/drive/circles/${encodePathValue(circleId)}?userId=${encodePathValue(userId)}").delete().build())
        Unit
    }

    override suspend fun fetchDriveDeletePolls(userId: String, circleId: String): Result<List<DriveDeletePoll>> = safePcCall {
        val raw = get("$driveBaseUrl/drive/delete-polls?userId=${encodePathValue(userId)}&circleId=${encodePathValue(circleId)}")
        parseDriveDeletePolls(raw)
    }

    override suspend fun createDriveDeletePoll(userId: String, targetType: String, targetId: String, circleId: String?): Result<DriveDeletePoll> = safePcCall {
        val body = gson.toJson(mapOf("userId" to userId, "targetType" to targetType, "targetId" to targetId, "circleId" to circleId))
            .toRequestBody("application/json".toMediaType())
        val raw = request(Request.Builder().url("$driveBaseUrl/drive/delete-polls").post(body).build())
        parseDriveDeletePoll(raw) ?: throw Exception("Create delete poll response was empty")
    }

    override suspend fun voteDriveDeletePoll(pollId: String, userId: String, vote: String): Result<DriveDeletePoll> = safePcCall {
        val body = gson.toJson(mapOf("userId" to userId, "vote" to vote)).toRequestBody("application/json".toMediaType())
        val raw = request(Request.Builder().url("$driveBaseUrl/drive/delete-polls/${encodePathValue(pollId)}/votes").post(body).build())
        parseDriveDeletePoll(raw) ?: throw Exception("Vote response was empty")
    }

    override suspend fun fetchDriveFavorites(userId: String): Result<List<String>> = safePcCall {
        val raw = get("$driveBaseUrl/drive/favorites?userId=${encodePathValue(userId)}")
        val root = parseMap(raw)
        parseList(root["itemIds"]).mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
    }

    override suspend fun setDriveFavorite(userId: String, itemId: String, favorite: Boolean): Result<Unit> = safePcCall {
        if (favorite) {
            val body = gson.toJson(mapOf("userId" to userId, "itemId" to itemId)).toRequestBody("application/json".toMediaType())
            request(Request.Builder().url("$driveBaseUrl/drive/favorites").post(body).build())
        } else {
            request(Request.Builder().url("$driveBaseUrl/drive/favorites/${encodePathValue(itemId)}?userId=${encodePathValue(userId)}").delete().build())
        }
        Unit
    }

    override suspend fun uploadDriveFile(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        uploaderId: String,
        plan: DriveUploadPlan
    ): Result<DriveUploadResponse> = safePcCall {
        HelloDebugLog.d(
            "DriveApi",
            "uploadDriveFile uploaderId=$uploaderId fileName=${HelloDebugLog.snippet(fileName)} mimeType=$mimeType bytes=${bytes.size} eventId=${plan.eventId} circles=${plan.circleIds.size} users=${plan.allowedUserIds.size}"
        )
        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("userId", uploaderId)
            .addFormDataPart("uploaderId", uploaderId)
            .addFormDataPart("eventName", plan.eventName)
            .addFormDataPart("batchId", plan.batchId)
            .addFormDataPart("files", fileName, bytes.toRequestBody(mimeType.toMediaType()))
        plan.eventId?.takeIf { it.isNotBlank() }?.let { bodyBuilder.addFormDataPart("eventId", it) }
        plan.circleIds.forEach { bodyBuilder.addFormDataPart("circleIds[]", it) }
        plan.allowedUserIds.forEach { bodyBuilder.addFormDataPart("allowedUserIds[]", it) }
        val body = bodyBuilder.build()
        val response = request(Request.Builder().url("$driveBaseUrl/drive/upload").post(body).build())
        gson.fromJson(response, DriveUploadResponse::class.java) ?: DriveUploadResponse()
    }

    override suspend fun deleteDriveItem(itemId: String, userId: String, securityAnswer: String): Result<DriveDeleteResponse> = safePcCall {
        HelloDebugLog.d("DriveApi", "deleteDriveItem itemId=$itemId userId=$userId")
        val body = gson.toJson(mapOf("userId" to userId, "securityAnswer" to securityAnswer)).toRequestBody("application/json".toMediaType())
        val response = request(Request.Builder().url("$driveBaseUrl/drive/items/${encodePathValue(itemId)}").delete(body).build())
        gson.fromJson(response, DriveDeleteResponse::class.java) ?: DriveDeleteResponse()
    }

    override suspend fun restoreDriveItem(itemId: String, userId: String): Result<DriveItem> = safePcCall {
        HelloDebugLog.d("DriveApi", "restoreDriveItem itemId=$itemId")
        val body = gson.toJson(mapOf("userId" to userId)).toRequestBody("application/json".toMediaType())
        val response = request(Request.Builder().url("$driveBaseUrl/drive/items/${encodePathValue(itemId)}/restore").post(body).build())
        (gson.fromJson(response, DriveItemActionResponse::class.java) ?: DriveItemActionResponse()).item
            ?: throw Exception("Restore response was empty")
    }

    override suspend fun permanentlyDeleteDriveItem(itemId: String, userId: String): Result<Unit> = safePcCall {
        HelloDebugLog.d("DriveApi", "permanentlyDeleteDriveItem itemId=$itemId")
        val body = gson.toJson(mapOf("userId" to userId)).toRequestBody("application/json".toMediaType())
        request(Request.Builder().url("$driveBaseUrl/drive/items/${encodePathValue(itemId)}/permanent").delete(body).build())
        Unit
    }

    private suspend fun get(url: String): String = request(Request.Builder().url(url).get().build())

    private suspend fun request(request: Request): String = withContext(Dispatchers.IO) {
        HelloDebugLog.d("DriveApi", "http ${request.method} url=${request.url}")
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                HelloDebugLog.w(
                    "DriveApi",
                    "http failure code=${response.code} url=${request.url} body=${HelloDebugLog.snippet(responseBody)}"
                )
                throw DriveApiException(response.code, responseBody ?: response.message)
            }
            HelloDebugLog.d(
                "DriveApi",
                "http success code=${response.code} url=${request.url} body=${HelloDebugLog.snippet(responseBody)}"
            )
            responseBody ?: throw Exception("Empty PC Drive response")
        }
    }

    private suspend inline fun <T> safePcCall(crossinline block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: Exception) {
            HelloDebugLog.w("DriveApi", "safePcCall failure error=${error.message}", error)
            Result.failure(normalizeDriveError(error))
        }

    private fun normalizeDriveError(error: Exception): Exception {
        if (error is DriveApiException) {
            val friendlyMessage = when (error.code) {
                502, 503, 504 -> "PC Drive is offline."
                else -> null
            }
            if (!friendlyMessage.isNullOrBlank()) {
                return Exception(friendlyMessage, error)
            }
        }
        if (error is IOException) {
            return Exception("PC Drive is offline.", error)
        }
        return error
    }

    companion object {
        fun isPcUnavailable(error: Throwable?): Boolean {
            var current = error
            while (current != null) {
                when (current) {
                    is IOException -> return true
                    is DriveApiException -> if (current.code in setOf(502, 503, 504)) return true
                }
                current = current.cause
            }
            return false
        }
    }

    private fun encodePathValue(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun parseDriveEvents(raw: String): List<DriveEvent> {
        val root = parseMap(raw)
        val events = root["events"]
        return parseList(events).mapNotNull { parseDriveEvent(it) }
    }

    private fun parseDriveEvent(raw: String): DriveEvent? = parseDriveEvent(parseMap(raw))

    private fun parseDriveEvent(value: Any?): DriveEvent? {
        val map = value as? Map<*, *> ?: return null
        val id = map.string("id")
        val name = map.string("name")
        if (id.isBlank() || name.isBlank()) return null
        return DriveEvent(
            id = id,
            name = name,
            createdByUserId = map.stringOrNull("createdByUserId"),
            coverItemId = map.stringOrNull("coverItemId"),
            itemCount = map.int("itemCount"),
            createdAt = map.long("createdAt"),
            updatedAt = map.long("updatedAt")
        )
    }

    private fun parseDriveCircles(raw: String): List<DriveCircle> {
        val root = parseMap(raw)
        val circles = root["circles"]
        return parseList(circles).mapNotNull { parseDriveCircle(it) }.distinctBy { it.id }
    }

    private fun parseDriveCircle(raw: String): DriveCircle? = parseDriveCircle(parseMap(raw))

    private fun parseDriveCircle(value: Any?): DriveCircle? {
        val map = value as? Map<*, *> ?: return null
        val id = map.string("id")
        val name = map.string("name")
        if (id.isBlank() || name.isBlank()) return null
        val members = parseList(map["members"]).mapNotNull { memberValue ->
            val member = memberValue as? Map<*, *> ?: return@mapNotNull null
            val userId = member.string("userId")
            if (userId.isBlank()) return@mapNotNull null
            DriveCircleMember(
                userId = userId,
                role = member.string("role").ifBlank { "Viewer" },
                name = member.stringOrNull("name"),
                username = member.stringOrNull("username"),
                avatar = member.stringOrNull("avatar")
            )
        }
        return DriveCircle(
            id = id,
            name = name,
            ownerUserId = map.stringOrNull("ownerUserId"),
            avatarUrl = map.stringOrNull("avatarUrl"),
            memberCount = map.int("memberCount").coerceAtLeast(members.size),
            members = members,
            createdAt = map.long("createdAt"),
            updatedAt = map.long("updatedAt")
        )
    }

    private fun parseDriveDeletePolls(raw: String): List<DriveDeletePoll> {
        val root = parseMap(raw)
        return parseList(root["polls"]).mapNotNull { parseDriveDeletePoll(it) }
    }

    private fun parseDriveDeletePoll(raw: String): DriveDeletePoll? = parseDriveDeletePoll(parseMap(raw))

    private fun parseDriveDeletePoll(value: Any?): DriveDeletePoll? {
        val map = value as? Map<*, *> ?: return null
        val id = map.string("id")
        val targetId = map.string("targetId")
        val circleId = map.string("circleId")
        if (id.isBlank() || targetId.isBlank() || circleId.isBlank()) return null
        val votes = parseList(map["votes"]).mapNotNull { voteValue ->
            val voteMap = voteValue as? Map<*, *> ?: return@mapNotNull null
            val pollId = voteMap.string("pollId")
            val userId = voteMap.string("userId")
            val vote = voteMap.string("vote")
            if (pollId.isBlank() || userId.isBlank() || vote.isBlank()) return@mapNotNull null
            DriveDeletePollVote(
                pollId = pollId,
                userId = userId,
                vote = vote,
                createdAt = voteMap.long("createdAt"),
                updatedAt = voteMap.long("updatedAt")
            )
        }
        return DriveDeletePoll(
            id = id,
            targetType = map.string("targetType"),
            targetId = targetId,
            circleId = circleId,
            startedByUserId = map.stringOrNull("startedByUserId"),
            endsAt = map.long("endsAt"),
            status = map.string("status").ifBlank { "open" },
            createdAt = map.long("createdAt"),
            resolvedAt = map["resolvedAt"]?.let { map.long("resolvedAt") }.takeIf { it != 0L },
            deleteVotes = map.int("deleteVotes"),
            keepVotes = map.int("keepVotes"),
            votes = votes
        )
    }

    private fun parseMap(raw: String): Map<*, *> {
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson<Map<String, Any?>>(raw, type).orEmpty()
    }

    private fun parseList(value: Any?): List<Any?> = value as? List<Any?> ?: emptyList()

    private fun Map<*, *>.string(key: String): String = this[key]?.toString()?.trim().orEmpty()

    private fun Map<*, *>.stringOrNull(key: String): String? = string(key).ifBlank { null }

    private fun Map<*, *>.int(key: String): Int = when (val value = this[key]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        else -> 0
    }

    private fun Map<*, *>.long(key: String): Long = when (val value = this[key]) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }
}
