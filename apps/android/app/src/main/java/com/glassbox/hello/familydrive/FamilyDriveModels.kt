package com.glassbox.hello.familydrive

data class DriveItem(
    val id: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val originalName: String? = null,
    val mimeType: String? = null,
    val type: String = "image",
    val size: Long = 0L,
    val uploaderId: String? = null,
    val createdAt: Long = 0L,
    val monthKey: String? = null,
    val monthLabel: String? = null
) {
    val isVideo: Boolean get() = type == "video" || mimeType?.startsWith("video/") == true
}

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
