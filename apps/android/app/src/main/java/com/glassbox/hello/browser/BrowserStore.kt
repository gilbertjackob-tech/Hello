package com.glassbox.hello.browser

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import java.io.File

class BrowserStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val gson = Gson()
    private val stateFile = File(File(applicationContext.filesDir, "browser"), "browser_state.json")
    private val lock = Any()
    private val passwordPreferences: SharedPreferences? by lazy { createPasswordPreferences() }

    init {
        stateFile.parentFile?.mkdirs()
    }

    fun load(): BrowserPersistedState = synchronized(lock) {
        if (!stateFile.exists()) {
            val state = BrowserPersistedState()
            saveLocked(state)
            return state
        }

        val loaded = runCatching {
            stateFile.bufferedReader().use { reader ->
                gson.fromJson(reader, BrowserPersistedState::class.java)
            }
        }.getOrElse { null }

        val state = loaded ?: BrowserPersistedState()
        state.ensureDefaults().withRestoredPasswords()
    }

    fun save(state: BrowserPersistedState) = synchronized(lock) {
        saveLocked(state.ensureDefaults())
    }

    private fun saveLocked(state: BrowserPersistedState) {
        persistPasswordSecrets(state)
        stateFile.bufferedWriter().use { writer ->
            gson.toJson(state.ensureDefaults().withoutPasswordSecrets(), writer)
        }
    }

    private fun BrowserPersistedState.ensureDefaults(): BrowserPersistedState {
        val profiles = profiles
            .filter { profile -> isEmailAddress(profile.email.orEmpty()) || (profile.pendingSignIn && profile.id == activeProfileId) }
            .ifEmpty { listOf(defaultSignInProfile()) }
            .map { profile ->
                if (profile.email.isNullOrBlank()) {
                    profile.copy(name = "Sign in", pendingSignIn = true)
                } else {
                    profile.copy(email = profile.email.orEmpty().trim().lowercase(), pendingSignIn = false)
                }
            }
            .distinctBy { profile -> profile.email?.lowercase() ?: profile.id }
        val profileIds = profiles.map { it.id }.toSet()
        val activeProfileId = if (profiles.any { it.id == this.activeProfileId }) this.activeProfileId else profiles.first().id
        val tabsByProfile = tabsByProfile
            .filterKeys { it in profileIds }
            .mapValues { (_, tabs) ->
            tabs.map { tab ->
                if (tab.url == "about:blank") {
                    tab.copy(url = DEFAULT_BROWSER_HOME_URL, title = if (tab.title == "New tab") "Google" else tab.title)
                } else {
                    tab
                }
            }
        }.toMutableMap()
        val selectedTabByProfile = selectedTabByProfile.filterKeys { it in profileIds }.toMutableMap()
        val historyByProfile = historyByProfile.filterKeys { it in profileIds }.toMutableMap()
        val downloadsByProfile = downloadsByProfile.filterKeys { it in profileIds }.toMutableMap()
        val passwordsByProfile = passwordsByProfile.filterKeys { it in profileIds }.toMutableMap()
        val storageByProfile = storageByProfile.filterKeys { it in profileIds }.toMutableMap()

        profiles.forEach { profile ->
            val tabs = tabsByProfile[profile.id].orEmpty().ifEmpty {
                listOf(
                    BrowserTabRecord(
                        id = "tab-${profile.id}",
                        profileId = profile.id
                    )
                )
            }
            tabsByProfile[profile.id] = tabs
            selectedTabByProfile[profile.id] = selectedTabByProfile[profile.id] ?: tabs.firstOrNull()?.id
            historyByProfile.putIfAbsent(profile.id, emptyList())
            downloadsByProfile.putIfAbsent(profile.id, emptyList())
            passwordsByProfile.putIfAbsent(profile.id, emptyList())
            storageByProfile.putIfAbsent(profile.id, emptyMap())
        }

        return copy(
            profiles = profiles,
            activeProfileId = activeProfileId,
            tabsByProfile = tabsByProfile,
            selectedTabByProfile = selectedTabByProfile,
            historyByProfile = historyByProfile,
            downloadsByProfile = downloadsByProfile,
            passwordsByProfile = passwordsByProfile,
            storageByProfile = storageByProfile
        )
    }

    private fun BrowserPersistedState.withRestoredPasswords(): BrowserPersistedState {
        val preferences = passwordPreferences ?: return this
        val restored = passwordsByProfile.mapValues { (_, passwords) ->
            passwords.map { record ->
                val securePassword = preferences.getString(passwordKey(record.id), null)
                when {
                    !securePassword.isNullOrBlank() -> record.copy(password = securePassword)
                    record.password.isNotBlank() -> {
                        preferences.edit().putString(passwordKey(record.id), record.password).apply()
                        record
                    }
                    else -> record
                }
            }
        }
        return copy(passwordsByProfile = restored)
    }

    private fun BrowserPersistedState.withoutPasswordSecrets(): BrowserPersistedState {
        return copy(
            passwordsByProfile = passwordsByProfile.mapValues { (_, passwords) ->
                passwords.map { record -> record.copy(password = "") }
            }
        )
    }

    private fun persistPasswordSecrets(state: BrowserPersistedState) {
        val preferences = passwordPreferences ?: return
        val passwordIds = state.passwordsByProfile.values.flatten().map { record -> record.id }.toSet()
        val editor = preferences.edit()
        state.passwordsByProfile.values.flatten().forEach { record ->
            if (record.password.isNotBlank()) {
                editor.putString(passwordKey(record.id), record.password)
            }
        }
        preferences.all.keys
            .filter { key -> key.startsWith(PASSWORD_KEY_PREFIX) && key.removePrefix(PASSWORD_KEY_PREFIX) !in passwordIds }
            .forEach { key -> editor.remove(key) }
        editor.apply()
    }

    private fun createPasswordPreferences(): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(applicationContext, MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                applicationContext,
                PASSWORD_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (error: Exception) {
            Log.e(TAG, "Encrypted password store is unavailable.", error)
            null
        }
    }

    private fun passwordKey(id: String): String = "$PASSWORD_KEY_PREFIX$id"

    private fun defaultSignInProfile(): BrowserProfileRecord {
        return BrowserProfileRecord(
            id = DEFAULT_BROWSER_PROFILE_ID,
            name = "Sign in",
            authProvider = BROWSER_PROVIDER_GOOGLE,
            pendingSignIn = true
        )
    }

    private companion object {
        private const val TAG: String = "BrowserStore"
        private const val PASSWORD_PREFS_NAME: String = "browser_passwords_secure"
        private const val MASTER_KEY_ALIAS: String = "browser_password_master_key"
        private const val PASSWORD_KEY_PREFIX: String = "password_"
    }
}

fun BrowserPersistedState.profileTabs(profileId: String): List<BrowserTabRecord> = tabsByProfile[profileId].orEmpty()

fun BrowserPersistedState.selectedTab(profileId: String): String? = selectedTabByProfile[profileId]

fun BrowserPersistedState.storage(profileId: String): Map<String, BrowserStoredOriginData> = storageByProfile[profileId].orEmpty()
