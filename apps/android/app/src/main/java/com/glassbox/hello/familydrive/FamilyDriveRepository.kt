package com.glassbox.hello.familydrive

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.glassbox.hello.network.HelloApi
import com.glassbox.hello.network.HelloApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FamilyDriveRepository(
    private val api: HelloApi = HelloApiClient()
) {
    suspend fun fetchItems(limit: Int = 60, before: Long? = null): Result<DriveItemsResponse> {
        return api.fetchDriveItems(limit = limit, before = before)
    }

    suspend fun uploadUris(
        context: Context,
        uris: List<Uri>,
        uploaderId: String,
        onProgress: (uploaded: Int, total: Int) -> Unit
    ): Result<List<DriveItem>> = withContext(Dispatchers.IO) {
        try {
            val uploadedItems = mutableListOf<DriveItem>()
            val appContext = context.applicationContext
            uris.forEachIndexed { index, uri ->
                val picked = readPickedDriveFile(appContext, uri)
                    ?: throw IllegalArgumentException("Could not read selected media")
                val response = api.uploadDriveFile(
                    fileName = picked.name,
                    mimeType = picked.mimeType,
                    bytes = picked.bytes,
                    uploaderId = uploaderId
                ).getOrThrow()
                uploadedItems += response.items
                onProgress(index + 1, uris.size)
            }
            Result.success(uploadedItems)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun readPickedDriveFile(context: Context, uri: Uri): PickedDriveFile? {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        if (!mimeType.startsWith("image/") && !mimeType.startsWith("video/")) return null
        val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: "family-drive-media"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return PickedDriveFile(name = name, mimeType = mimeType, bytes = bytes)
    }

    private data class PickedDriveFile(
        val name: String,
        val mimeType: String,
        val bytes: ByteArray
    )
}
