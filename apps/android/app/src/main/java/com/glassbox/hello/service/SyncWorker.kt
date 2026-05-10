package com.glassbox.hello.service

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.glassbox.hello.client.ApiClient
import com.glassbox.hello.client.SyncRequest
import com.glassbox.hello.database.entities.ProfileEntity
import com.glassbox.hello.repository.BrowserRepository
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * WorkManager worker for background profile, history, and provider sync.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    private val repository = BrowserRepository.create(context.applicationContext)
    private val apiClient = ApiClient(context.applicationContext)

    override suspend fun doWork(): Result {
        return try {
            val requestedProfileId = inputData.getInt(KEY_PROFILE_ID, ALL_PROFILES)
            val forceFullSync = inputData.getBoolean(KEY_FORCE_FULL_SYNC, false)
            if (requestedProfileId == ALL_PROFILES) {
                repository.profiles.getProfilesSnapshot()
                    .filter { profile -> profile.isSyncEnabled }
                    .forEach { profile -> syncProfile(profile, forceFullSync) }
            } else {
                val profile = repository.profiles.getProfile(requestedProfileId)
                    ?: return Result.failure()
                if (profile.isSyncEnabled) {
                    syncProfile(profile, forceFullSync)
                }
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Background sync failed.", error)
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        } finally {
            apiClient.close()
        }
    }

    private suspend fun syncProfile(profile: ProfileEntity, forceFullSync: Boolean) {
        val request = SyncRequest(
            profileId = profile.id,
            provider = profile.type,
            since = if (forceFullSync) null else profile.lastSyncTime,
            forceFullSync = forceFullSync,
            limit = SYNC_LIMIT
        )
        when (profile.type) {
            ProfileEntity.TYPE_GMAIL -> apiClient.api.syncGmail(request)
            ProfileEntity.TYPE_OUTLOOK -> apiClient.api.syncOutlook(request)
            ProfileEntity.TYPE_ICLOUD -> apiClient.api.syncICloud(request)
            else -> return
        }
        repository.profiles.markSynced(profile.id)
    }

    companion object {
        private const val TAG: String = "SyncWorker"
        private const val UNIQUE_PERIODIC_PREFIX: String = "browser_sync_periodic_"
        private const val UNIQUE_IMMEDIATE_PREFIX: String = "browser_sync_immediate_"
        private const val ALL_PROFILES: Int = -1
        private const val MAX_RETRY_ATTEMPTS: Int = 3
        private const val SYNC_LIMIT: Int = 500

        const val KEY_PROFILE_ID: String = "profile_id"
        const val KEY_FORCE_FULL_SYNC: String = "force_full_sync"

        /**
         * Schedules periodic sync with exponential backoff.
         */
        fun schedulePeriodicSync(context: Context, profileId: Int = ALL_PROFILES) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_PROFILE_ID to profileId))
                .setConstraints(syncConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_PREFIX + profileId,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Requests immediate sync.
         */
        fun requestImmediateSync(
            context: Context,
            profileId: Int = ALL_PROFILES,
            forceFullSync: Boolean = false
        ) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(
                    workDataOf(
                        KEY_PROFILE_ID to profileId,
                        KEY_FORCE_FULL_SYNC to forceFullSync
                    )
                )
                .setConstraints(syncConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_PREFIX + profileId,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        private fun syncConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        }
    }
}
