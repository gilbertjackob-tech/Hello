package com.glassbox.hello.familydrive

interface DrivePcApi {
    suspend fun fetchDriveItems(limit: Int = 60, before: Long? = null): Result<DriveItemsResponse>
    suspend fun uploadDriveFile(fileName: String, mimeType: String, bytes: ByteArray, uploaderId: String): Result<DriveUploadResponse>
    suspend fun deleteDriveItem(itemId: String): Result<Unit>
}
