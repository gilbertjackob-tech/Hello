package com.glassbox.hello.familydrive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.glassbox.hello.chat.CloudChatRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FamilyDriveRepository(
    private val api: DrivePcApi = DrivePcApiClient()
) {
    suspend fun fetchItems(limit: Int = 60, before: Long? = null, sync: Boolean = false): Result<DriveItemsResponse> {
        return api.fetchDriveItems(limit = limit, before = before, sync = sync)
    }

    suspend fun fetchTrash(limit: Int = 60, before: Long? = null, sync: Boolean = false): Result<DriveItemsResponse> {
        return api.fetchDriveTrash(limit = limit, before = before, sync = sync)
    }

    suspend fun fetchDeleteLimit(userId: String): Result<DriveDeleteLimit> {
        return api.fetchDriveDeleteLimit(userId)
    }

    suspend fun fetchEvents(): Result<List<DriveEvent>> {
        return api.fetchDriveEvents()
    }

    suspend fun createEvent(name: String, userId: String): Result<DriveEvent> {
        return api.createDriveEvent(name, userId).also { result ->
            result.getOrNull()?.let { event ->
                mirrorDriveMetadataToFirestore("events", event.id, event)
            }
        }
    }

    suspend fun fetchCircles(): Result<List<DriveCircle>> {
        return api.fetchDriveCircles()
    }

    suspend fun createCircle(name: String, ownerUserId: String, members: List<DriveCircleMember>): Result<DriveCircle> {
        return api.createDriveCircle(name, ownerUserId, members).also { result ->
            result.getOrNull()?.let { circle ->
                mirrorDriveMetadataToFirestore("circles", circle.id, circle)
            }
        }
    }

    suspend fun fetchChatContacts(context: Context, currentUserId: String): Result<List<DriveContact>> = withContext(Dispatchers.IO) {
        try {
            val cloudRepository = CloudChatRepository(context.applicationContext)
            val chats = cloudRepository.fetchChats(currentUserId)
                .getOrElse { cloudRepository.cachedChats(currentUserId) }
            val contacts = chats
                .flatMap { chat ->
                    chat.participants.orEmpty()
                        .filter { it.id != currentUserId }
                        .map { user ->
                            DriveContact(
                                id = user.id,
                                name = user.name,
                                avatar = user.avatar,
                                sourceChatId = chat.id
                            )
                        }
                }
                .distinctBy { it.id }
                .sortedBy { it.name.lowercase(Locale.US) }
            Result.success(contacts)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun deleteItem(itemId: String, userId: String): Result<DriveDeleteResponse> {
        return api.deleteDriveItem(itemId, userId)
    }

    suspend fun restoreItem(itemId: String): Result<DriveItem> {
        return api.restoreDriveItem(itemId)
    }

    suspend fun permanentlyDeleteItem(itemId: String): Result<Unit> {
        return api.permanentlyDeleteDriveItem(itemId)
    }

    suspend fun removePendingUpload(context: Context, itemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            pendingStore(context.applicationContext).delete(itemId)
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun uploadUris(
        context: Context,
        uris: List<Uri>,
        uploaderId: String,
        plan: DriveUploadPlan = DriveUploadPlan(),
        onProgress: (uploaded: Int, total: Int) -> Unit
    ): Result<DriveUploadOutcome> = withContext(Dispatchers.IO) {
        try {
            val uploadedItems = mutableListOf<DriveItem>()
            val appContext = context.applicationContext
            var pendingItems = emptyList<PendingDriveItem>()
            for ((index, uri) in uris.withIndex()) {
                try {
                    val picked = readPickedDriveFile(appContext, uri)
                        ?: throw IllegalArgumentException("Could not read selected media")
                    val response = api.uploadDriveFile(
                        fileName = picked.name,
                        mimeType = picked.mimeType,
                        bytes = picked.bytes,
                        uploaderId = uploaderId,
                        plan = plan
                    ).getOrThrow()
                    uploadedItems += response.items
                    onProgress(index + 1, uris.size)
                } catch (error: Exception) {
                    val remaining = uris.drop(index)
                    pendingItems = savePendingUploads(appContext, remaining, plan).getOrThrow()
                    FamilyDriveUploadWorker.enqueue(appContext, uploaderId)
                    break
                }
            }
            val outcome = DriveUploadOutcome(syncedItems = uploadedItems, pendingItems = pendingItems)
            mirrorUploadOutcomeToFirestore(plan, outcome)
            Result.success(outcome)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun observePendingUploads(context: Context): Flow<List<PendingDriveItem>> {
        return pendingStore(context).observeActive()
    }

    suspend fun retryPendingUploads(context: Context, uploaderId: String, itemId: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val appContext = context.applicationContext
            val store = pendingStore(appContext)
            val items = store.getRetryable(itemId)
            var uploadedCount = 0
            for (item in items) {
                store.updateStatus(item.id, PendingDriveStatus.UPLOADING, null)
                try {
                    val picked = readPickedDriveFile(appContext, Uri.parse(item.localUri))
                        ?: throw IllegalArgumentException("Could not read selected media")
                    val retryPlan = DriveUploadPlan(
                        eventId = item.eventId,
                        eventName = item.eventName ?: "Daily Memories",
                        circleIds = item.selectedCircleIds,
                        allowedUserIds = item.selectedUserIds,
                        batchId = item.batchId ?: "batch_${item.createdAt}"
                    )
                    val response = api.uploadDriveFile(
                        fileName = picked.name,
                        mimeType = picked.mimeType,
                        bytes = picked.bytes,
                        uploaderId = uploaderId,
                        plan = retryPlan
                    ).getOrThrow()
                    mirrorUploadOutcomeToFirestore(retryPlan, DriveUploadOutcome(syncedItems = response.items))
                    store.updateStatus(item.id, PendingDriveStatus.SYNCED, null)
                    uploadedCount += 1
                } catch (error: Exception) {
                    store.markRetryable(
                        item.id,
                        error.message ?: "Upload retry failed"
                    )
                }
            }
            Result.success(uploadedCount)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun savePendingUploads(context: Context, uris: List<Uri>, plan: DriveUploadPlan): Result<List<PendingDriveItem>> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val items = uris.mapIndexedNotNull { index, uri ->
                readPickedDriveMetadata(context, uri)?.let { picked ->
                    persistReadPermission(context, uri)
                    val createdAt = now + index
                    PendingDriveItem(
                        id = "pending_${createdAt}_${uri.toString().hashCode()}",
                        localUri = uri.toString(),
                        displayName = picked.name,
                        mimeType = picked.mimeType,
                        mediaType = if (picked.mimeType.startsWith("video/")) "video" else "image",
                        size = picked.size,
                        createdAt = createdAt,
                        monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(createdAt)),
                        monthLabel = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(createdAt)),
                        status = PendingDriveStatus.PENDING_LOCAL,
                        eventId = plan.eventId,
                        eventName = plan.eventName,
                        selectedCircleIds = plan.circleIds,
                        selectedUserIds = plan.allowedUserIds,
                        batchId = plan.batchId
                    )
                }
            }
            if (items.isEmpty()) throw IllegalArgumentException("No selected photos or videos could be saved locally")
            pendingStore(context).upsertAll(items)
            Result.success(items)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun mirrorUploadOutcomeToFirestore(plan: DriveUploadPlan, outcome: DriveUploadOutcome) {
        if (outcome.syncedItems.isEmpty()) return
        mirrorDriveMetadataToFirestore(
            collection = "upload_batches",
            documentId = plan.batchId,
            value = mapOf(
                "batchId" to plan.batchId,
                "eventId" to plan.eventId,
                "eventName" to plan.eventName,
                "circleIds" to plan.circleIds,
                "allowedUserIds" to plan.allowedUserIds,
                "audienceBreakdown" to plan.audienceBreakdown,
                "itemIds" to outcome.syncedItems.map { it.id },
                "updatedAt" to System.currentTimeMillis()
            )
        )
    }

    private suspend fun mirrorDriveMetadataToFirestore(collection: String, documentId: String, value: Any) {
        runCatching {
            FirebaseFirestore.getInstance()
                .collection("family_drive")
                .document(collection)
                .collection("items")
                .document(documentId)
                .set(value)
                .await()
        }
    }

    private fun pendingStore(context: Context): FamilyDrivePendingStore {
        return FamilyDrivePendingStore.getInstance(context.applicationContext)
    }

    private fun readPickedDriveFile(context: Context, uri: Uri): PickedDriveFile? {
        val metadata = readPickedDriveMetadata(context, uri) ?: return null
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return PickedDriveFile(name = metadata.name, mimeType = metadata.mimeType, bytes = bytes)
    }

    private fun readPickedDriveMetadata(context: Context, uri: Uri): PickedDriveMetadata? {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        if (!mimeType.startsWith("image/") && !mimeType.startsWith("video/")) return null
        val cursorMetadata = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                name to size
            } else {
                null
            }
        }
        return PickedDriveMetadata(
            name = cursorMetadata?.first ?: "family-drive-media",
            mimeType = mimeType,
            size = cursorMetadata?.second ?: 0L
        )
    }

    private fun persistReadPermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private data class PickedDriveFile(
        val name: String,
        val mimeType: String,
        val bytes: ByteArray
    )

    private data class PickedDriveMetadata(
        val name: String,
        val mimeType: String,
        val size: Long
    )
}
