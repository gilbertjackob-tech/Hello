package com.glassbox.hello.database.dao

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.glassbox.hello.database.entities.ProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room access layer for browser profiles.
 */
@Dao
abstract class ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertProfile(profile: ProfileEntity): Long

    @Update
    protected abstract suspend fun updateProfile(profile: ProfileEntity): Int

    @Delete
    protected abstract suspend fun deleteProfile(profile: ProfileEntity): Int

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    protected abstract suspend fun getProfileByIdInternal(id: Int): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE isActive = 1 ORDER BY updatedAt DESC LIMIT 1")
    protected abstract fun getActiveProfileInternal(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles ORDER BY updatedAt DESC")
    protected abstract fun getAllProfilesInternal(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY updatedAt DESC")
    protected abstract suspend fun getAllProfilesSnapshotInternal(): List<ProfileEntity>

    @Query("UPDATE profiles SET isActive = 0, updatedAt = :updatedAt WHERE isActive = 1")
    protected abstract suspend fun deactivateAllProfilesInternal(updatedAt: Long): Int

    @Query("UPDATE profiles SET isActive = 1, updatedAt = :updatedAt WHERE id = :id")
    protected abstract suspend fun setActiveProfileInternal(id: Int, updatedAt: Long): Int

    @Query("SELECT COUNT(*) FROM profiles")
    protected abstract suspend fun countProfilesInternal(): Int

    @Query("SELECT * FROM profiles WHERE email = :email COLLATE NOCASE LIMIT 1")
    protected abstract suspend fun getProfileByEmailInternal(email: String): ProfileEntity?

    /**
     * Inserts a validated profile and returns its row id.
     */
    suspend fun insert(profile: ProfileEntity): Long {
        return execute("insert profile") {
            insertProfile(profile.requireValid())
        }
    }

    /**
     * Updates a validated profile and returns the number of affected rows.
     */
    suspend fun update(profile: ProfileEntity): Int {
        return execute("update profile") {
            updateProfile(profile.requireValid())
        }
    }

    /**
     * Deletes a profile and returns the number of affected rows.
     */
    suspend fun delete(profile: ProfileEntity): Int {
        return execute("delete profile") {
            deleteProfile(profile)
        }
    }

    /**
     * Returns the profile with [id], or null when no profile exists.
     */
    suspend fun getProfileById(id: Int): ProfileEntity? {
        require(id > 0) { "Profile id must be positive." }
        return execute("get profile by id") {
            getProfileByIdInternal(id)
        }
    }

    /**
     * Observes the active profile.
     */
    fun getActiveProfile(): Flow<ProfileEntity?> {
        return getActiveProfileInternal()
    }

    /**
     * Observes all profiles ordered by most recently updated.
     */
    fun getAllProfiles(): Flow<List<ProfileEntity>> {
        return getAllProfilesInternal()
    }

    /**
     * Returns all profiles ordered by most recently updated.
     */
    suspend fun getAllProfilesSnapshot(): List<ProfileEntity> {
        return execute("get profile snapshot") {
            getAllProfilesSnapshotInternal()
        }
    }

    /**
     * Deactivates every active profile and returns the number of changed rows.
     */
    suspend fun deactivateAllProfiles(now: Long = System.currentTimeMillis()): Int {
        return execute("deactivate profiles") {
            deactivateAllProfilesInternal(now)
        }
    }

    /**
     * Marks one profile active and returns the number of changed rows.
     */
    suspend fun setActiveProfile(id: Int, now: Long = System.currentTimeMillis()): Int {
        require(id > 0) { "Profile id must be positive." }
        return execute("set active profile") {
            setActiveProfileInternal(id, now)
        }
    }

    /**
     * Atomically switches the active profile.
     */
    @Transaction
    open suspend fun activateProfile(id: Int, now: Long = System.currentTimeMillis()): Boolean {
        require(id > 0) { "Profile id must be positive." }
        return execute("activate profile") {
            deactivateAllProfilesInternal(now)
            setActiveProfileInternal(id, now) > 0
        }
    }

    /**
     * Returns the number of stored profiles.
     */
    suspend fun countProfiles(): Int {
        return execute("count profiles") {
            countProfilesInternal()
        }
    }

    /**
     * Returns the profile matching [email], or null when none exists.
     */
    suspend fun getProfileByEmail(email: String): ProfileEntity? {
        val cleanEmail = email.trim()
        require(cleanEmail.isNotBlank()) { "Email must not be blank." }
        return execute("get profile by email") {
            getProfileByEmailInternal(cleanEmail)
        }
    }

    private suspend fun <T> execute(operation: String, block: suspend () -> T): T {
        return try {
            block()
        } catch (error: SQLiteException) {
            Log.e(TAG, "Database failure during $operation.", error)
            throw IllegalStateException("Failed to $operation.", error)
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Invalid data during $operation.", error)
            throw error
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unexpected failure during $operation.", error)
            throw error
        }
    }

    private companion object {
        private const val TAG: String = "ProfileDao"
    }
}
