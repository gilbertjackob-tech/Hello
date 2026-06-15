package com.glassbox.hello.familydrive

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class FamilyDrivePendingEventStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun observeActive(): Flow<List<PendingDriveEvent>> =
        changes.onStart { emit(Unit) }.map { getActive() }

    suspend fun getActive(circleId: String? = null): List<PendingDriveEvent> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            readAllLocked()
                .filter { it.status != PendingDriveEventStatus.SYNCED }
                .filter { circleId.isNullOrBlank() || it.circleId == circleId }
                .sortedByDescending { it.updatedAt }
        }
    }

    suspend fun getRetryable(): List<PendingDriveEvent> = withContext(Dispatchers.IO) {
        val retryable = setOf(
            PendingDriveEventStatus.PENDING_LOCAL,
            PendingDriveEventStatus.SYNCING,
            PendingDriveEventStatus.FAILED_RETRYABLE
        )
        synchronized(lock) {
            readAllLocked().filter { it.status in retryable }.sortedBy { it.createdAt }
        }
    }

    suspend fun upsert(event: PendingDriveEvent) = mutate { existing ->
        val byId = existing.associateBy { it.id }.toMutableMap()
        byId[event.id] = event
        byId.values.sortedByDescending { it.updatedAt }
    }

    suspend fun updateStatus(id: String, status: PendingDriveEventStatus, lastError: String? = null) = mutate { existing ->
        existing.map { event ->
            if (event.id == id) event.copy(status = status, lastError = lastError) else event
        }
    }

    suspend fun markRetryable(id: String, lastError: String?) = mutate { existing ->
        existing.map { event ->
            if (event.id == id) {
                event.copy(
                    status = PendingDriveEventStatus.FAILED_RETRYABLE,
                    retryCount = event.retryCount + 1,
                    lastError = lastError
                )
            } else {
                event
            }
        }
    }

    suspend fun remapCircleIds(circleIdMap: Map<String, String>) = mutate { existing ->
        existing.map { event ->
            val nextCircleId = circleIdMap[event.circleId] ?: event.circleId
            if (nextCircleId == event.circleId) event else event.copy(circleId = nextCircleId)
        }
    }

    suspend fun delete(id: String) = mutate { existing -> existing.filterNot { it.id == id } }

    suspend fun deleteForCircle(circleId: String) = mutate { existing ->
        existing.filterNot { it.circleId == circleId }
    }

    private suspend fun mutate(block: (List<PendingDriveEvent>) -> List<PendingDriveEvent>) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            writeAllLocked(block(readAllLocked()))
        }
        changes.emit(Unit)
    }

    private fun readAllLocked(): List<PendingDriveEvent> {
        val json = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(json, Array<PendingDriveEvent>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun writeAllLocked(items: List<PendingDriveEvent>) {
        prefs.edit().putString(KEY_ITEMS, gson.toJson(items)).apply()
    }

    companion object {
        private const val PREFS_NAME = "family_drive_pending_events"
        private const val KEY_ITEMS = "items"
        private val lock = Any()
        private val changes = MutableSharedFlow<Unit>(replay = 1)

        fun getInstance(context: Context): FamilyDrivePendingEventStore =
            FamilyDrivePendingEventStore(context.applicationContext)
    }
}
