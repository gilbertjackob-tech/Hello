package com.glassbox.hello.familydrive

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class FamilyDrivePendingStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun observeActive(): Flow<List<PendingDriveItem>> {
        return changes
            .onStart { emit(Unit) }
            .map { getActive() }
    }

    suspend fun getRetryable(itemId: String? = null): List<PendingDriveItem> = withContext(Dispatchers.IO) {
        val retryableStatuses = setOf(PendingDriveStatus.PENDING_LOCAL, PendingDriveStatus.FAILED_RETRYABLE)
        synchronized(lock) {
            val all = readAllLocked()
            if (itemId != null) {
                all.filter { it.id == itemId && it.status != PendingDriveStatus.SYNCED }
            } else {
                all.filter { it.status in retryableStatuses }.sortedBy { it.createdAt }
            }
        }
    }

    suspend fun upsertAll(items: List<PendingDriveItem>) = mutate { existing ->
        val byId = existing.associateBy { it.id }.toMutableMap()
        items.forEach { byId[it.id] = it }
        byId.values.sortedByDescending { it.createdAt }
    }

    suspend fun updateStatus(id: String, status: PendingDriveStatus, lastError: String?) = mutate { existing ->
        existing.map { item ->
            if (item.id == id) item.copy(status = status, lastError = lastError) else item
        }
    }

    suspend fun markRetryable(id: String, lastError: String?) = mutate { existing ->
        existing.map { item ->
            if (item.id == id) {
                item.copy(
                    status = PendingDriveStatus.FAILED_RETRYABLE,
                    retryCount = item.retryCount + 1,
                    lastError = lastError
                )
            } else {
                item
            }
        }
    }

    suspend fun delete(id: String) = mutate { existing ->
        existing.filterNot { it.id == id }
    }

    suspend fun remapCircleIds(circleIdMap: Map<String, String>) = mutate { existing ->
        if (circleIdMap.isEmpty()) {
            existing
        } else {
            existing.map { item ->
                val nextCircleIds = item.selectedCircleIds.map { circleId -> circleIdMap[circleId] ?: circleId }
                if (nextCircleIds == item.selectedCircleIds) item else item.copy(selectedCircleIds = nextCircleIds)
            }
        }
    }

    suspend fun removeCircleId(circleId: String) = mutate { existing ->
        existing.map { item ->
            val nextCircleIds = item.selectedCircleIds.filterNot { it == circleId }
            if (nextCircleIds == item.selectedCircleIds) item else item.copy(selectedCircleIds = nextCircleIds)
        }
    }

    private suspend fun getActive(): List<PendingDriveItem> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            readAllLocked()
                .filter { it.status != PendingDriveStatus.SYNCED }
                .sortedByDescending { it.createdAt }
        }
    }

    private suspend fun mutate(block: (List<PendingDriveItem>) -> List<PendingDriveItem>) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            writeAllLocked(block(readAllLocked()))
        }
        changes.emit(Unit)
    }

    private fun readAllLocked(): List<PendingDriveItem> {
        val json = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(json, Array<PendingDriveItem>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun writeAllLocked(items: List<PendingDriveItem>) {
        prefs.edit().putString(KEY_ITEMS, gson.toJson(items)).apply()
    }

    companion object {
        private const val PREFS_NAME = "family_drive_pending_uploads"
        private const val KEY_ITEMS = "items"
        private val lock = Any()
        private val changes = MutableSharedFlow<Unit>(replay = 1)

        fun getInstance(context: Context): FamilyDrivePendingStore = FamilyDrivePendingStore(context.applicationContext)
    }
}
