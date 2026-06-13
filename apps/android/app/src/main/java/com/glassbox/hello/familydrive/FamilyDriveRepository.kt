package com.glassbox.hello.familydrive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.glassbox.hello.chat.CloudChatRepository
import com.glassbox.hello.chat.ChatModels
import com.glassbox.hello.debug.HelloDebugLog
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FamilyDriveRepository(
    private val api: DrivePcApi = DrivePcApiClient()
) {
    suspend fun fetchItems(
        userId: String,
        limit: Int = 60,
        before: Long? = null,
        sync: Boolean = false,
        circleId: String? = null,
        eventId: String? = null
    ): Result<DriveItemsResponse> {
        return api.fetchDriveItems(
            userId = userId,
            limit = limit,
            before = before,
            sync = sync,
            circleId = circleId,
            eventId = eventId
        )
    }

    suspend fun fetchTrash(
        userId: String,
        limit: Int = 60,
        before: Long? = null,
        sync: Boolean = false,
        circleId: String? = null,
        eventId: String? = null
    ): Result<DriveItemsResponse> {
        return api.fetchDriveTrash(
            userId = userId,
            limit = limit,
            before = before,
            sync = sync,
            circleId = circleId,
            eventId = eventId
        )
    }

    suspend fun fetchDeleteLimit(userId: String): Result<DriveDeleteLimit> {
        return api.fetchDriveDeleteLimit(userId)
    }

    suspend fun fetchEvents(userId: String, circleId: String? = null): Result<List<DriveEvent>> {
        return api.fetchDriveEvents(userId, circleId)
    }

    suspend fun createEvent(name: String, userId: String, circleId: String): Result<DriveEvent> {
        return api.createDriveEvent(name, userId, circleId).also { result ->
            result.getOrNull()?.let { event ->
                mirrorDriveMetadataToFirestore("events", event.id, event)
            }
        }
    }

    suspend fun renameEvent(eventId: String, userId: String, name: String): Result<DriveEvent> {
        return api.renameDriveEvent(eventId, userId, name).also { result ->
            result.getOrNull()?.let { event ->
                mirrorDriveMetadataToFirestore("events", event.id, event)
            }
        }
    }

    suspend fun deleteEvent(eventId: String, userId: String): Result<Unit> {
        return api.deleteDriveEvent(eventId, userId)
    }

    suspend fun fetchCircles(userId: String): Result<List<DriveCircle>> {
        return api.fetchDriveCircles(userId)
    }

    suspend fun fetchCirclesWithPending(context: Context, userId: String): Result<List<DriveCircle>> = withContext(Dispatchers.IO) {
        syncPendingCircles(context.applicationContext, userId)
        val pending = pendingCircleStore(context.applicationContext).getActive()
        val remote = fetchCircles(userId)
        remote.fold(
            onSuccess = { circles ->
                Result.success(mergeCircles(circles, pending))
            },
            onFailure = { error ->
                if (pending.isNotEmpty()) {
                    Result.success(mergeCircles(emptyList(), pending))
                } else {
                    Result.failure(error)
                }
            }
        )
    }

    suspend fun createCircle(
        context: Context,
        id: String? = null,
        name: String,
        ownerUserId: String,
        members: List<DriveCircleMember>
    ): Result<DriveCircle> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val remoteResult = api.createDriveCircle(id, name, ownerUserId, members)
        remoteResult.getOrNull()?.let { circle ->
            mirrorDriveMetadataToFirestore("circles", circle.id, circle)
            return@withContext Result.success(circle)
        }
        val error = remoteResult.exceptionOrNull() ?: Exception("Circle could not be created")
        if (!id.isNullOrBlank() || !DrivePcApiClient.isPcUnavailable(error)) {
            return@withContext Result.failure(error)
        }
        val pending = PendingDriveCircle(
            id = buildLocalCircleId(),
            name = name,
            ownerUserId = ownerUserId,
            members = members,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = PendingDriveCircleStatus.PENDING_LOCAL,
            lastError = null
        )
        pendingCircleStore(appContext).upsert(pending)
        Result.success(pending.asDriveCircle())
    }

    suspend fun syncPendingCircles(context: Context, ownerUserId: String): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val appContext = context.applicationContext
            val circleStore = pendingCircleStore(appContext)
            val uploadStore = pendingStore(appContext)
            val pendingCircles = circleStore.getRetryable()
            val remappedIds = mutableMapOf<String, String>()
            for (circle in pendingCircles) {
                circleStore.updateStatus(circle.id, PendingDriveCircleStatus.SYNCING)
                try {
                    val created = api.createDriveCircle(
                        id = null,
                        name = circle.name,
                        ownerUserId = ownerUserId,
                        members = circle.members
                    ).getOrThrow()
                    mirrorDriveMetadataToFirestore("circles", created.id, created)
                    remappedIds[circle.id] = created.id
                    uploadStore.remapCircleIds(mapOf(circle.id to created.id))
                    circleStore.delete(circle.id)
                } catch (error: Exception) {
                    HelloDebugLog.w("DriveRepo", "syncPendingCircles item_failed circleId=${circle.id} error=${error.message}", error)
                    circleStore.markRetryable(circle.id, error.message ?: "Sync failed")
                }
            }
            Result.success(remappedIds)
        } catch (error: Exception) {
            HelloDebugLog.w("DriveRepo", "syncPendingCircles failure error=${error.message}", error)
            Result.failure(error)
        }
    }

    fun observePendingUploads(context: Context): Flow<List<PendingDriveItem>> {
        return pendingStore(context).observeActive()
    }

    fun observePendingCircles(context: Context): Flow<List<DriveCircle>> {
        return pendingCircleStore(context.applicationContext)
            .observeActive()
            .map { pending -> pending.map { it.asDriveCircle() } }
    }

    suspend fun uploadCircleAvatar(circleId: String, userId: String, fileName: String, mimeType: String, bytes: ByteArray): Result<DriveCircle> {
        return api.uploadDriveCircleAvatar(circleId, userId, fileName, mimeType, bytes).also { result ->
            result.getOrNull()?.let { circle ->
                mirrorDriveMetadataToFirestore("circles", circle.id, circle)
            }
        }
    }

    suspend fun leaveCircle(circleId: String, userId: String): Result<Unit> {
        return api.leaveDriveCircle(circleId, userId)
    }

    suspend fun deleteCircle(circleId: String, userId: String): Result<Unit> {
        return api.deleteDriveCircle(circleId, userId)
    }

    suspend fun fetchDeletePolls(userId: String, circleId: String): Result<List<DriveDeletePoll>> {
        return api.fetchDriveDeletePolls(userId, circleId)
    }

    suspend fun createDeletePoll(userId: String, targetType: String, targetId: String, circleId: String? = null): Result<DriveDeletePoll> {
        return api.createDriveDeletePoll(userId, targetType, targetId, circleId)
    }

    suspend fun voteDeletePoll(pollId: String, userId: String, vote: String): Result<DriveDeletePoll> {
        return api.voteDriveDeletePoll(pollId, userId, vote)
    }

    suspend fun fetchFavorites(userId: String): Result<List<String>> {
        return api.fetchDriveFavorites(userId)
    }

    suspend fun setFavorite(userId: String, itemId: String, favorite: Boolean): Result<Unit> {
        return api.setDriveFavorite(userId, itemId, favorite)
    }

    suspend fun fetchChatContacts(context: Context, currentUserId: String): Result<List<DriveContact>> = withContext(Dispatchers.IO) {
        try {
            HelloDebugLog.d("DriveRepo", "fetchChatContacts currentUserId=$currentUserId")
            val cloudRepository = CloudChatRepository(context.applicationContext)
            val chats = cloudRepository.fetchChats(currentUserId)
                .getOrElse { cloudRepository.cachedChats(currentUserId) }
            val chatContacts = chats
                .flatMap { chat ->
                    chat.participants.orEmpty()
                        .filter { it.id != currentUserId }
                        .map { user ->
                            DriveContact(
                                id = user.id,
                                name = normalizedContactName(user),
                                username = user.username?.trim()?.ifBlank { null },
                                avatar = user.avatar,
                                sourceChatId = chat.id
                            )
                        }
                }
            val globalContacts = cloudRepository.fetchUsers()
                .getOrDefault(emptyList())
                .filter { user -> user.id != currentUserId }
                .map { user ->
                    DriveContact(
                        id = user.id,
                        name = normalizedContactName(user),
                        username = user.username?.trim()?.ifBlank { null },
                        avatar = user.avatar,
                        sourceChatId = null
                    )
                }
            val contacts = (chatContacts + globalContacts)
                .filter { it.id.isNotBlank() }
                .distinctBy { it.id }
                .sortedBy { it.name.trim().lowercase(Locale.US) }
            HelloDebugLog.d("DriveRepo", "fetchChatContacts success chats=${chats.size} chatContacts=${chatContacts.size} globalContacts=${globalContacts.size} mergedContacts=${contacts.size}")
            Result.success(contacts)
        } catch (error: Exception) {
            HelloDebugLog.w("DriveRepo", "fetchChatContacts failure error=${error.message}", error)
            Result.failure(error)
        }
    }

    suspend fun deleteItem(itemId: String, userId: String, securityAnswer: String): Result<DriveDeleteResponse> {
        return api.deleteDriveItem(itemId, userId, securityAnswer)
    }

    suspend fun restoreItem(itemId: String, userId: String): Result<DriveItem> {
        return api.restoreDriveItem(itemId, userId)
    }

    suspend fun permanentlyDeleteItem(itemId: String, userId: String): Result<Unit> {
        return api.permanentlyDeleteDriveItem(itemId, userId)
    }

    suspend fun removePendingUpload(context: Context, itemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            pendingStore(context.applicationContext).delete(itemId)
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun removePendingCircle(context: Context, circleId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            pendingCircleStore(context.applicationContext).delete(circleId)
            pendingStore(context.applicationContext).removeCircleId(circleId)
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
            HelloDebugLog.d("DriveRepo", "uploadUris uploaderId=$uploaderId count=${uris.size} eventId=${plan.eventId} circles=${plan.circleIds.size} users=${plan.allowedUserIds.size}")
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
                    HelloDebugLog.w("DriveRepo", "uploadUris falling_back_to_pending index=$index uri=$uri error=${error.message}", error)
                    val remaining = uris.drop(index)
                    pendingItems = savePendingUploads(appContext, remaining, plan).getOrThrow()
                    FamilyDriveUploadWorker.enqueue(appContext, uploaderId)
                    break
                }
            }
            val outcome = DriveUploadOutcome(syncedItems = uploadedItems, pendingItems = pendingItems)
            mirrorUploadOutcomeToFirestore(plan, outcome)
            HelloDebugLog.d("DriveRepo", "uploadUris success synced=${outcome.syncedItems.size} pending=${outcome.pendingItems.size}")
            Result.success(outcome)
        } catch (error: Exception) {
            HelloDebugLog.w("DriveRepo", "uploadUris failure error=${error.message}", error)
            Result.failure(error)
        }
    }

    suspend fun retryPendingUploads(context: Context, uploaderId: String, itemId: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            HelloDebugLog.d("DriveRepo", "retryPendingUploads uploaderId=$uploaderId itemId=$itemId")
            val appContext = context.applicationContext
            syncPendingCircles(appContext, uploaderId)
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
                    HelloDebugLog.w("DriveRepo", "retryPendingUploads item_failed itemId=${item.id} error=${error.message}", error)
                    store.markRetryable(
                        item.id,
                        error.message ?: "Upload retry failed"
                    )
                }
            }
            HelloDebugLog.d("DriveRepo", "retryPendingUploads success uploadedCount=$uploadedCount")
            Result.success(uploadedCount)
        } catch (error: Exception) {
            HelloDebugLog.w("DriveRepo", "retryPendingUploads failure error=${error.message}", error)
            Result.failure(error)
        }
    }

    private suspend fun savePendingUploads(context: Context, uris: List<Uri>, plan: DriveUploadPlan): Result<List<PendingDriveItem>> = withContext(Dispatchers.IO) {
        try {
            HelloDebugLog.d("DriveRepo", "savePendingUploads count=${uris.size} eventId=${plan.eventId}")
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
            HelloDebugLog.d("DriveRepo", "savePendingUploads success count=${items.size}")
            Result.success(items)
        } catch (error: Exception) {
            HelloDebugLog.w("DriveRepo", "savePendingUploads failure error=${error.message}", error)
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

    private fun pendingCircleStore(context: Context): FamilyDrivePendingCircleStore {
        return FamilyDrivePendingCircleStore.getInstance(context.applicationContext)
    }

    private fun mergeCircles(remote: List<DriveCircle>, pending: List<PendingDriveCircle>): List<DriveCircle> {
        val remoteIds = remote.map { it.id }.toSet()
        val pendingCircles = pending
            .filter { it.serverCircleId.isNullOrBlank() || it.serverCircleId !in remoteIds }
            .map { it.asDriveCircle() }
        return (pendingCircles + remote)
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAt }
    }

    private fun buildLocalCircleId(): String {
        return "local_circle_${System.currentTimeMillis()}"
    }

    private fun normalizedContactName(user: ChatModels.User): String {
        val cleanName = user.name?.trim().orEmpty()
        if (cleanName.isNotBlank()) return cleanName
        val cleanUsername = user.username?.trim().orEmpty()
        if (cleanUsername.isNotBlank()) return cleanUsername
        val cleanId = user.id?.trim().orEmpty()
        return cleanId.ifBlank { "Unknown contact" }
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
