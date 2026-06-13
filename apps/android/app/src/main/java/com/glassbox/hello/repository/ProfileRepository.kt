package com.glassbox.hello.repository

import android.content.Context
import com.glassbox.hello.debug.AppLog as Log
import androidx.room.withTransaction
import com.glassbox.hello.database.AppDatabase
import com.glassbox.hello.database.entities.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

/**
 * Repository focused on browser profile validation, persistence, and switching.
 */
class ProfileRepository(
    private val database: AppDatabase
) {
    private val profileDao = database.profileDao()

    /**
     * Observes the currently active profile.
     */
    fun getActiveProfile(): Flow<ProfileEntity?> {
        return profileDao.getActiveProfile()
    }

    /**
     * Observes all profiles ordered by most recently updated.
     */
    fun getAllProfiles(): Flow<List<ProfileEntity>> {
        return profileDao.getAllProfiles()
    }

    /**
     * Returns one profile by id, or null when missing.
     */
    suspend fun getProfile(profileId: Int): ProfileEntity? {
        return execute("get profile") {
            profileDao.getProfileById(profileId)
        }
    }

    /**
     * Returns all profiles as a one-shot snapshot.
     */
    suspend fun getProfilesSnapshot(): List<ProfileEntity> {
        return execute("get profile snapshot") {
            profileDao.getAllProfilesSnapshot()
        }
    }

    /**
     * Creates a validated profile from raw fields.
     */
    suspend fun createProfile(
        name: String,
        type: String = ProfileEntity.TYPE_CUSTOM,
        email: String? = null,
        accessToken: String? = null,
        refreshToken: String? = null,
        tokenExpiry: Long? = null,
        userAgent: String? = null,
        isActive: Boolean = false,
        isSyncEnabled: Boolean = true
    ): Long {
        return createProfile(
            ProfileEntity.create(
                name = name,
                type = type,
                email = email,
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenExpiry = tokenExpiry,
                userAgent = userAgent,
                isActive = isActive,
                isSyncEnabled = isSyncEnabled
            ),
            activate = isActive
        )
    }

    /**
     * Creates a validated profile and optionally makes it active.
     */
    suspend fun createProfile(profile: ProfileEntity, activate: Boolean = profile.isActive): Long {
        return execute("create profile") {
            database.withTransaction {
                val shouldActivate = activate || profileDao.countProfiles() == 0
                if (shouldActivate) {
                    profileDao.deactivateAllProfiles()
                }
                profileDao.insert(profile.copy(isActive = shouldActivate).requireValid())
            }
        }
    }

    /**
     * Updates a validated profile.
     */
    suspend fun updateProfile(profile: ProfileEntity): Int {
        require(profile.id > 0) { "Profile id must be positive." }
        return execute("update profile") {
            database.withTransaction {
                if (profile.isActive) {
                    profileDao.deactivateAllProfiles(profile.updatedAt)
                }
                profileDao.update(profile.requireValid())
            }
        }
    }

    /**
     * Atomically switches the active profile.
     */
    suspend fun switchProfile(profileId: Int): ProfileEntity {
        require(profileId > 0) { "Profile id must be positive." }
        return execute("switch profile") {
            database.withTransaction {
                val profile = profileDao.getProfileById(profileId)
                    ?: throw NoSuchElementException("Profile $profileId does not exist.")
                val changed = profileDao.activateProfile(profileId)
                check(changed) { "Profile $profileId could not be activated." }
                profile.withActiveState(active = true)
            }
        }
    }

    /**
     * Deletes a profile and activates the most recent remaining profile when needed.
     */
    suspend fun deleteProfile(profileId: Int): Boolean {
        require(profileId > 0) { "Profile id must be positive." }
        return execute("delete profile") {
            database.withTransaction {
                val profile = profileDao.getProfileById(profileId) ?: return@withTransaction false
                val wasActive = profile.isActive
                profileDao.delete(profile)
                if (wasActive) {
                    profileDao.getAllProfilesSnapshot().firstOrNull()?.let { nextProfile ->
                        profileDao.activateProfile(nextProfile.id)
                    }
                }
                true
            }
        }
    }

    /**
     * Updates OAuth tokens for a profile and returns the updated profile.
     */
    suspend fun updateOAuthTokens(
        profileId: Int,
        accessToken: String?,
        refreshToken: String?,
        tokenExpiry: Long?
    ): ProfileEntity {
        require(profileId > 0) { "Profile id must be positive." }
        return execute("update OAuth tokens") {
            database.withTransaction {
                val profile = profileDao.getProfileById(profileId)
                    ?: throw NoSuchElementException("Profile $profileId does not exist.")
                val updated = profile.withOAuthTokens(accessToken, refreshToken, tokenExpiry)
                profileDao.update(updated)
                updated
            }
        }
    }

    /**
     * Enables or disables browser profile sync.
     */
    suspend fun setSyncEnabled(profileId: Int, enabled: Boolean): ProfileEntity {
        require(profileId > 0) { "Profile id must be positive." }
        return execute("set profile sync") {
            database.withTransaction {
                val profile = profileDao.getProfileById(profileId)
                    ?: throw NoSuchElementException("Profile $profileId does not exist.")
                val updated = profile.withSyncEnabled(enabled)
                profileDao.update(updated)
                updated
            }
        }
    }

    /**
     * Marks a profile sync as completed at the current time.
     */
    suspend fun markSynced(profileId: Int): ProfileEntity {
        require(profileId > 0) { "Profile id must be positive." }
        return execute("mark profile synced") {
            database.withTransaction {
                val profile = profileDao.getProfileById(profileId)
                    ?: throw NoSuchElementException("Profile $profileId does not exist.")
                val updated = profile.markSynced()
                profileDao.update(updated)
                updated
            }
        }
    }

    private suspend fun <T> execute(operation: String, block: suspend () -> T): T {
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Invalid input during $operation.", error)
            throw error
        } catch (error: BrowserRepositoryException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Repository failure during $operation.", error)
            throw BrowserRepositoryException(operation, error)
        }
    }

    companion object {
        private const val TAG: String = "ProfileRepository"

        /**
         * Creates a repository from an Android context without retaining an Activity reference.
         */
        fun create(context: Context): ProfileRepository {
            return ProfileRepository(AppDatabase.getInstance(context.applicationContext))
        }
    }
}
