package com.glassbox.hello.status

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class StoryTextTransform(
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val widthScale: Float = 1f
)

data class FirestoreStatusStory(
    val id: String,
    val userId: String,
    val userName: String,
    val userAvatar: String?,
    val kind: String,
    val text: String,
    val mediaUrl: String?,
    val mediaType: String?,
    val backgroundThemeId: String,
    val backgroundColor: String,
    val fontId: String,
    val textColor: String,
    val textTransform: StoryTextTransform,
    val durationMs: Long,
    val createdAt: Long,
    val expiresAt: Long,
    val viewers: Map<String, Long>
)

class StatusFirestoreRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun fetchActiveStories(now: Long = System.currentTimeMillis()): Result<List<FirestoreStatusStory>> = runCatching {
        firestore.collection(COLLECTION)
            .whereGreaterThan("expiresAt", now)
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                document.data?.let { map -> storyFromMap(document.id, map) }
            }
            .sortedBy { it.createdAt }
    }

    suspend fun createStory(story: FirestoreStatusStory): Result<FirestoreStatusStory> = runCatching {
        firestore.collection(COLLECTION)
            .document(story.id)
            .set(storyToMap(story))
            .await()
        story
    }

    suspend fun markViewed(statusId: String, userId: String, timestamp: Long = System.currentTimeMillis()): Result<Unit> = runCatching {
        firestore.collection(COLLECTION)
            .document(statusId)
            .update("viewers.$userId", timestamp)
            .await()
    }

    private fun storyFromMap(id: String, map: Map<String, Any>): FirestoreStatusStory {
        val transform = map["textTransform"] as? Map<*, *> ?: emptyMap<String, Any>()
        val viewers = (map["viewers"] as? Map<*, *>)
            .orEmpty()
            .mapNotNull { (key, value) ->
                val userId = key as? String ?: return@mapNotNull null
                userId to numberToLong(value)
            }
            .toMap()
        return FirestoreStatusStory(
            id = stringValue(map["id"], id),
            userId = stringValue(map["userId"], ""),
            userName = stringValue(map["userName"], "Hello user"),
            userAvatar = map["userAvatar"] as? String,
            kind = stringValue(map["kind"], "text"),
            text = stringValue(map["text"], ""),
            mediaUrl = map["mediaUrl"] as? String,
            mediaType = map["mediaType"] as? String,
            backgroundThemeId = stringValue(map["backgroundThemeId"], "pink"),
            backgroundColor = stringValue(map["backgroundColor"], "#f472b6"),
            fontId = stringValue(map["fontId"], "bold"),
            textColor = stringValue(map["textColor"], "#fff7fb"),
            textTransform = StoryTextTransform(
                x = numberToFloat(transform["x"]),
                y = numberToFloat(transform["y"]),
                scale = numberToFloat(transform["scale"], 1f),
                rotation = numberToFloat(transform["rotation"]),
                widthScale = numberToFloat(transform["widthScale"], 1f)
            ),
            durationMs = numberToLong(map["durationMs"], 5000L),
            createdAt = numberToLong(map["createdAt"]),
            expiresAt = numberToLong(map["expiresAt"]),
            viewers = viewers
        )
    }

    private fun storyToMap(story: FirestoreStatusStory): Map<String, Any?> {
        return mapOf(
            "id" to story.id,
            "userId" to story.userId,
            "userName" to story.userName,
            "userAvatar" to story.userAvatar,
            "kind" to story.kind,
            "text" to story.text,
            "mediaUrl" to story.mediaUrl,
            "mediaType" to story.mediaType,
            "backgroundThemeId" to story.backgroundThemeId,
            "backgroundColor" to story.backgroundColor,
            "fontId" to story.fontId,
            "textColor" to story.textColor,
            "textTransform" to mapOf(
                "x" to story.textTransform.x,
                "y" to story.textTransform.y,
                "scale" to story.textTransform.scale,
                "rotation" to story.textTransform.rotation,
                "widthScale" to story.textTransform.widthScale
            ),
            "durationMs" to story.durationMs,
            "createdAt" to story.createdAt,
            "expiresAt" to story.expiresAt,
            "viewers" to story.viewers
        )
    }

    private fun stringValue(value: Any?, fallback: String): String {
        return (value as? String)?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun numberToFloat(value: Any?, fallback: Float = 0f): Float {
        return when (value) {
            is Number -> value.toFloat()
            else -> fallback
        }
    }

    private fun numberToLong(value: Any?, fallback: Long = 0L): Long {
        return when (value) {
            is Number -> value.toLong()
            else -> fallback
        }
    }

    companion object {
        private const val COLLECTION = "statuses"
    }
}
