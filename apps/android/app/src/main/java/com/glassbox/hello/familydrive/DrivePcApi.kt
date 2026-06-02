package com.glassbox.hello.familydrive

interface DrivePcApi {
    suspend fun fetchDriveItems(limit: Int = 60, before: Long? = null, sync: Boolean = false): Result<DriveItemsResponse>
    suspend fun fetchDriveTrash(limit: Int = 60, before: Long? = null, sync: Boolean = false): Result<DriveItemsResponse>
    suspend fun fetchDriveDeleteLimit(userId: String): Result<DriveDeleteLimit>
    suspend fun uploadDriveFile(fileName: String, mimeType: String, bytes: ByteArray, uploaderId: String): Result<DriveUploadResponse>
    suspend fun deleteDriveItem(itemId: String, userId: String): Result<DriveDeleteResponse>
    suspend fun restoreDriveItem(itemId: String): Result<DriveItem>
    suspend fun permanentlyDeleteDriveItem(itemId: String): Result<Unit>
}
