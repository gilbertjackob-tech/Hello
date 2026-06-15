package com.glassbox.hello.familydrive

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class FamilyDrivePendingCircleStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun observeActive(): Flow<List<PendingDriveCircle>> {
        return changes
            .onStart { emit(Unit) }
            .map { getActive() }
    }

    suspend fun getActive(): List<PendingDriveCircle> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            readAllLocked()
                .filter { it.status != PendingDriveCircleStatus.SYNCED }
                .sortedByDescending { it.updatedAt }
        }
    }

    suspend fun getRetryable(circleId: String? = null): List<PendingDriveCircle> = withContext(Dispatchers.IO) {
        val retryableStatuses = setOf(
            PendingDriveCircleStatus.PENDING_LOCAL,
            PendingDriveCircleStatus.SYNCING,
            PendingDriveCircleStatus.FAILED_RETRYABLE
        )
        synchronized(lock) {
            val all = readAllLocked()
            if (circleId != null) {
                all.filter { it.id == circleId && it.status != PendingDriveCircleStatus.SYNCED }
            } else {
                all.filter { it.status in retryableStatuses }.sortedBy { it.createdAt }
            }
        }
    }

    suspend fun upsert(circle: PendingDriveCircle) = mutate { existing ->
        val byId = existing.associateBy { it.id }.toMutableMap()
        byId[circle.id] = circle
        byId.values.sortedByDescending { it.updatedAt }
    }

    suspend fun updateStatus(id: String, status: PendingDriveCircleStatus, lastError: String? = null, serverCircleId: String? = null) = mutate { existing ->
        existing.map { circle ->
            if (circle.id == id) {
                circle.copy(
                    status = status,
                    lastError = lastError,
                    serverCircleId = serverCircleId ?: circle.serverCircleId
                )
            } else {
                circle
            }
        }
    }

    suspend fun markRetryable(id: String, lastError: String?) = mutate { existing ->
        existing.map { circle ->
            if (circle.id == id) {
                circle.copy(
                    status = PendingDriveCircleStatus.FAILED_RETRYABLE,
                    retryCount = circle.retryCount + 1,
                    lastError = lastError
                )
            } else {
                circle
            }
        }
    }

    suspend fun delete(id: String) = mutate { existing ->
        existing.filterNot { it.id == id }
    }

    private suspend fun mutate(block: (List<PendingDriveCircle>) -> List<PendingDriveCircle>) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            writeAllLocked(block(readAllLocked()))
        }
        changes.emit(Unit)
    }

    private fun readAllLocked(): List<PendingDriveCircle> {
        val json = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(json, Array<PendingDriveCircle>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun writeAllLocked(items: List<PendingDriveCircle>) {
        prefs.edit().putString(KEY_ITEMS, gson.toJson(items)).apply()
    }

    companion object {
        private const val PREFS_NAME = "family_drive_pending_circles"
        private const val KEY_ITEMS = "items"
        private val lock = Any()
        private val changes = MutableSharedFlow<Unit>(replay = 1)

        fun getInstance(context: Context): FamilyDrivePendingCircleStore {
            return FamilyDrivePendingCircleStore(context.applicationContext)
        }
    }
}
