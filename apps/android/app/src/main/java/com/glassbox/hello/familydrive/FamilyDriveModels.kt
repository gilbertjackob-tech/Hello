package com.glassbox.hello.familydrive

data class DriveItem(
    val id: String,
    val batchId: String? = null,
    val eventId: String? = null,
    val eventName: String? = null,
    val circleIds: List<String> = emptyList(),
    val favorite: Boolean = false,
    val url: String,
    val thumbnailUrl: String? = null,
    val originalName: String? = null,
    val mimeType: String? = null,
    val type: String = "image",
    val size: Long = 0L,
    val uploaderId: String? = null,
    val createdAt: Long = 0L,
    val monthKey: String? = null,
    val monthLabel: String? = null,
    val deletedAt: Long? = null,
    val deletedBy: String? = null
) {
    val isVideo: Boolean get() = type == "video" || mimeType?.startsWith("video/") == true
}

data class DriveUploadPlan(
    val eventId: String? = null,
    val eventName: String = "Daily Memories",
    val circleIds: List<String> = emptyList(),
    val allowedUserIds: List<String> = emptyList(),
    val audienceBreakdown: Map<String, Int> = emptyMap(),
    val batchId: String = "batch_${System.currentTimeMillis()}"
)

data class DriveEvent(
    val id: String,
    val name: String,
    val createdByUserId: String? = null,
    val coverItemId: String? = null,
    val itemCount: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class DriveEventsResponse(
    val events: List<DriveEvent> = emptyList()
)

data class DriveCircle(
    val id: String,
    val name: String,
    val ownerUserId: String? = null,
    val avatarUrl: String? = null,
    val memberCount: Int = 0,
    val members: List<DriveCircleMember> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val syncStatus: PendingDriveCircleStatus = PendingDriveCircleStatus.SYNCED,
    val syncError: String? = null,
    val serverCircleId: String? = null
)

data class DriveCircleMember(
    val userId: String,
    val role: String = "Viewer",
    val name: String? = null,
    val username: String? = null,
    val avatar: String? = null
)

data class DriveCirclesResponse(
    val circles: List<DriveCircle> = emptyList()
)

data class DriveContact(
    val id: String,
    val name: String,
    val username: String? = null,
    val avatar: String? = null,
    val sourceChatId: String? = null
)

data class DriveItemsResponse(
    val items: List<DriveItem> = emptyList(),
    val nextCursor: Long? = null,
    val hasMore: Boolean = false,
    val total: Int = 0
)

data class DriveUploadResponse(
    val items: List<DriveItem> = emptyList(),
    val count: Int = 0
)

data class DriveDeleteLimit(
    val limit: Int = 20,
    val used: Int = 0,
    val remaining: Int = 20,
    val deleteDay: String = ""
)

data class DriveDeleteResponse(
    val ok: Boolean = false,
    val item: DriveItem? = null,
    val deleteLimit: DriveDeleteLimit? = null
)

data class DriveDeletePollVote(
    val pollId: String,
    val userId: String,
    val vote: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class DriveDeletePoll(
    val id: String,
    val targetType: String,
    val targetId: String,
    val circleId: String,
    val startedByUserId: String? = null,
    val endsAt: Long = 0L,
    val status: String = "open",
    val createdAt: Long = 0L,
    val resolvedAt: Long? = null,
    val deleteVotes: Int = 0,
    val keepVotes: Int = 0,
    val votes: List<DriveDeletePollVote> = emptyList()
)

data class DriveDeletePollsResponse(
    val polls: List<DriveDeletePoll> = emptyList()
)

data class DriveItemActionResponse(
    val ok: Boolean = false,
    val item: DriveItem? = null
)

enum class PendingDriveStatus {
    PENDING_LOCAL,
    UPLOADING,
    SYNCED,
    FAILED_RETRYABLE
}

enum class PendingDriveCircleStatus {
    PENDING_LOCAL,
    SYNCING,
    SYNCED,
    FAILED_RETRYABLE
}

data class PendingDriveCircle(
    val id: String,
    val name: String,
    val ownerUserId: String,
    val members: List<DriveCircleMember> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val status: PendingDriveCircleStatus = PendingDriveCircleStatus.PENDING_LOCAL,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val serverCircleId: String? = null
) {
    fun asDriveCircle(): DriveCircle {
        return DriveCircle(
            id = id,
            name = name,
            ownerUserId = ownerUserId,
            memberCount = members.size,
            members = members,
            createdAt = createdAt,
            updatedAt = updatedAt,
            syncStatus = status,
            syncError = lastError,
            serverCircleId = serverCircleId
        )
    }
}

data class PendingDriveItem(
    val id: String,
    val localUri: String,
    val displayName: String,
    val mimeType: String,
    val mediaType: String,
    val size: Long,
    val createdAt: Long,
    val monthKey: String,
    val monthLabel: String,
    val status: PendingDriveStatus = PendingDriveStatus.PENDING_LOCAL,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val eventId: String? = null,
    val eventName: String? = null,
    val selectedCircleIds: List<String> = emptyList(),
    val selectedUserIds: List<String> = emptyList(),
    val batchId: String? = null
) {
    val isVideo: Boolean get() = mediaType == "video" || mimeType.startsWith("video/")
}

data class DriveUploadOutcome(
    val syncedItems: List<DriveItem> = emptyList(),
    val pendingItems: List<PendingDriveItem> = emptyList()
)
