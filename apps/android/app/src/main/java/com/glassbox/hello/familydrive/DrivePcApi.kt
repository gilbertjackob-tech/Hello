package com.glassbox.hello.familydrive

interface DrivePcApi {
    suspend fun fetchDriveItems(userId: String, limit: Int = 60, before: Long? = null, sync: Boolean = false, circleId: String? = null, eventId: String? = null): Result<DriveItemsResponse>
    suspend fun fetchDriveTrash(userId: String, limit: Int = 60, before: Long? = null, sync: Boolean = false, circleId: String? = null, eventId: String? = null): Result<DriveItemsResponse>
    suspend fun fetchDriveDeleteLimit(userId: String): Result<DriveDeleteLimit>
    suspend fun fetchDriveEvents(userId: String, circleId: String? = null): Result<List<DriveEvent>>
    suspend fun createDriveEvent(name: String, userId: String, circleId: String): Result<DriveEvent>
    suspend fun renameDriveEvent(eventId: String, userId: String, name: String): Result<DriveEvent>
    suspend fun deleteDriveEvent(eventId: String, userId: String): Result<Unit>
    suspend fun fetchDriveCircles(userId: String): Result<List<DriveCircle>>
    suspend fun createDriveCircle(id: String? = null, name: String, ownerUserId: String, members: List<DriveCircleMember>): Result<DriveCircle>
    suspend fun leaveDriveCircle(circleId: String, userId: String): Result<Unit>
    suspend fun deleteDriveCircle(circleId: String, userId: String): Result<Unit>
    suspend fun fetchDriveDeletePolls(userId: String, circleId: String): Result<List<DriveDeletePoll>>
    suspend fun createDriveDeletePoll(userId: String, targetType: String, targetId: String, circleId: String? = null): Result<DriveDeletePoll>
    suspend fun voteDriveDeletePoll(pollId: String, userId: String, vote: String): Result<DriveDeletePoll>
    suspend fun fetchDriveFavorites(userId: String): Result<List<String>>
    suspend fun setDriveFavorite(userId: String, itemId: String, favorite: Boolean): Result<Unit>
    suspend fun uploadDriveFile(fileName: String, mimeType: String, bytes: ByteArray, uploaderId: String, plan: DriveUploadPlan = DriveUploadPlan()): Result<DriveUploadResponse>
    suspend fun deleteDriveItem(itemId: String, userId: String, securityAnswer: String): Result<DriveDeleteResponse>
    suspend fun restoreDriveItem(itemId: String, userId: String): Result<DriveItem>
    suspend fun permanentlyDeleteDriveItem(itemId: String, userId: String): Result<Unit>
}
