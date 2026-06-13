package com.glassbox.hello.security

import android.content.Context
import android.content.SharedPreferences
import com.glassbox.hello.debug.AppLog as Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted SharedPreferences wrapper for OAuth tokens and security metadata.
 */
class SecureDataStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val metadata: SharedPreferences =
        applicationContext.getSharedPreferences(METADATA_PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    @Volatile
    private var generation: Int = metadata.getInt(KEY_GENERATION, 1)

    @Volatile
    private var securePreferences: SharedPreferences = createPreferences(generation)

    /**
     * Saves a token value.
     */
    fun saveToken(key: String, token: String) {
        requireKey(key)
        require(token.isNotBlank()) { "Token cannot be blank." }
        securePreferences.edit().putString(key, token).apply()
    }

    /**
     * Returns a token value.
     */
    fun getToken(key: String): String? {
        requireKey(key)
        return securePreferences.getString(key, null)?.takeIf { value -> value.isNotBlank() }
    }

    /**
     * Deletes one token value.
     */
    fun deleteToken(key: String) {
        requireKey(key)
        securePreferences.edit().remove(key).apply()
    }

    /**
     * Saves a string value.
     */
    fun saveString(key: String, value: String) {
        requireKey(key)
        securePreferences.edit().putString(key, value).apply()
    }

    /**
     * Returns a string value.
     */
    fun getString(key: String): String? {
        requireKey(key)
        return securePreferences.getString(key, null)
    }

    /**
     * Saves a long value.
     */
    fun saveLong(key: String, value: Long) {
        requireKey(key)
        securePreferences.edit().putLong(key, value).apply()
    }

    /**
     * Returns a long value.
     */
    fun getLong(key: String, defaultValue: Long = 0L): Long {
        requireKey(key)
        return securePreferences.getLong(key, defaultValue)
    }

    /**
     * Deletes every encrypted value.
     */
    fun clear() {
        securePreferences.edit().clear().apply()
    }

    /**
     * Rotates encrypted preference files by copying values into a new MasterKey generation.
     */
    fun rotateKeys() {
        synchronized(lock) {
            val oldPreferences = securePreferences
            val snapshot = oldPreferences.all.toMap()
            val nextGeneration = generation + 1
            val nextPreferences = createPreferences(nextGeneration)
            val editor = nextPreferences.edit()
            snapshot.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Long -> editor.putLong(key, value)
                    is Int -> editor.putInt(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> {
                        val stringSet = value.filterIsInstance<String>().toSet()
                        editor.putStringSet(key, stringSet)
                    }
                }
            }
            editor.apply()
            oldPreferences.edit().clear().apply()
            generation = nextGeneration
            securePreferences = nextPreferences
            metadata.edit().putInt(KEY_GENERATION, nextGeneration).apply()
            Log.i(TAG, "Secure data store keys rotated to generation $nextGeneration.")
        }
    }

    private fun createPreferences(preferenceGeneration: Int): SharedPreferences {
        val masterKey = MasterKey.Builder(applicationContext, "$MASTER_KEY_ALIAS$preferenceGeneration")
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            applicationContext,
            "$SECURE_PREFS$preferenceGeneration",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun requireKey(key: String) {
        require(key.isNotBlank()) { "Secure data key cannot be blank." }
    }

    companion object {
        private const val TAG: String = "SecureDataStore"
        private const val METADATA_PREFS: String = "glassbox_secure_metadata"
        private const val SECURE_PREFS: String = "glassbox_secure_v"
        private const val MASTER_KEY_ALIAS: String = "glassbox_master_key_v"
        private const val KEY_GENERATION: String = "generation"
    }
}
