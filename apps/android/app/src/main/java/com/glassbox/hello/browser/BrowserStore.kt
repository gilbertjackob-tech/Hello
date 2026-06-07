package com.glassbox.hello.browser

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
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
                val raw = gson.fromJson(reader, JsonObject::class.java)
                raw?.toPersistedState()
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
            .map(::normalizeProfile)
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
            tabs.map(::normalizeTab).map { tab ->
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

    private fun normalizeProfile(profile: BrowserProfileRecord): BrowserProfileRecord {
        val id = rawString(profile.id).ifBlank { DEFAULT_BROWSER_PROFILE_ID }
        val email = rawString(profile.email).lowercase().takeIf { it.isNotBlank() }
        val displayName = rawString(profile.name).ifBlank {
            email?.substringBefore('@')?.takeIf { it.isNotBlank() } ?: "Sign in"
        }
        return profile.copy(
            id = id,
            name = displayName,
            email = email,
            avatarUrl = rawString(profile.avatarUrl).takeIf { it.isNotBlank() }
        )
    }

    private fun normalizeTab(tab: BrowserTabRecord): BrowserTabRecord {
        val profileId = rawString(tab.profileId).ifBlank { DEFAULT_BROWSER_PROFILE_ID }
        val tabId = rawString(tab.id).ifBlank { "tab-$profileId" }
        val url = rawString(tab.url).ifBlank { DEFAULT_BROWSER_HOME_URL }
        val title = rawString(tab.title).ifBlank { if (url == DEFAULT_BROWSER_HOME_URL) "Google" else url }
        return tab.copy(
            id = tabId,
            profileId = profileId,
            url = url,
            title = title,
            lastError = rawString(tab.lastError).takeIf { it.isNotBlank() }
        )
    }

    private fun rawString(value: String?): String = value?.trim().orEmpty()

    private fun JsonObject.toPersistedState(): BrowserPersistedState {
        val profiles = getAsJsonArray("profiles")
            ?.mapNotNull { it.asJsonObjectOrNull()?.toBrowserProfileRecord() }
            .orEmpty()
        val activeProfileId = get("activeProfileId").asTrimmedString()
            .ifBlank { profiles.firstOrNull()?.id ?: DEFAULT_BROWSER_PROFILE_ID }
        val tabsByProfile = getAsJsonObject("tabsByProfile")
            ?.entrySet()
            ?.associate { entry ->
                val profileId = entry.key.trim()
                val tabs = entry.value.asJsonArrayOrNull()
                    ?.mapNotNull { it.asJsonObjectOrNull()?.toBrowserTabRecord(profileId) }
                    .orEmpty()
                profileId to tabs
            }
            .orEmpty()
        val selectedTabByProfile = getAsJsonObject("selectedTabByProfile")
            ?.entrySet()
            ?.associate { entry -> entry.key.trim() to entry.value.asTrimmedString().ifBlank { null } }
            .orEmpty()
        val historyByProfile = getAsJsonObject("historyByProfile")
            ?.entrySet()
            ?.associate { entry ->
                entry.key.trim() to entry.value.asJsonArrayOrNull()
                    ?.mapNotNull { it.asJsonObjectOrNull()?.toBrowserHistoryRecord(entry.key) }
                    .orEmpty()
            }
            .orEmpty()
        val downloadsByProfile = getAsJsonObject("downloadsByProfile")
            ?.entrySet()
            ?.associate { entry ->
                entry.key.trim() to entry.value.asJsonArrayOrNull()
                    ?.mapNotNull { it.asJsonObjectOrNull()?.toBrowserDownloadRecord(entry.key) }
                    .orEmpty()
            }
            .orEmpty()
        val passwordsByProfile = getAsJsonObject("passwordsByProfile")
            ?.entrySet()
            ?.associate { entry ->
                entry.key.trim() to entry.value.asJsonArrayOrNull()
                    ?.mapNotNull { it.asJsonObjectOrNull()?.toBrowserPasswordRecord(entry.key) }
                    .orEmpty()
            }
            .orEmpty()
        val storageByProfile = getAsJsonObject("storageByProfile")
            ?.entrySet()
            ?.associate { entry ->
                entry.key.trim() to entry.value.asJsonObjectOrNull()
                    ?.entrySet()
                    ?.associate { originEntry ->
                        originEntry.key.trim() to (originEntry.value.asJsonObjectOrNull()?.toBrowserStoredOriginData()
                            ?: BrowserStoredOriginData())
                    }
                    .orEmpty()
            }
            .orEmpty()
        return BrowserPersistedState(
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

    private fun JsonObject.toBrowserProfileRecord(): BrowserProfileRecord? {
        val id = get("id").asTrimmedString()
        if (id.isBlank()) return null
        return BrowserProfileRecord(
            id = id,
            name = get("name").asTrimmedString(),
            email = get("email").asTrimmedString().ifBlank { null },
            avatarUrl = get("avatarUrl").asTrimmedString().ifBlank { null },
            authProvider = get("authProvider").asTrimmedString().ifBlank { BROWSER_PROVIDER_LOCAL },
            pendingSignIn = get("pendingSignIn").asBoolean(false),
            createdAt = get("createdAt").asLong(System.currentTimeMillis())
        )
    }

    private fun JsonObject.toBrowserTabRecord(profileIdFallback: String? = null): BrowserTabRecord? {
        val profileId = get("profileId").asTrimmedString().ifBlank { profileIdFallback.orEmpty() }
        if (profileId.isBlank()) return null
        val id = get("id").asTrimmedString().ifBlank { "tab-$profileId" }
        return BrowserTabRecord(
            id = id,
            profileId = profileId,
            url = get("url").asTrimmedString().ifBlank { DEFAULT_BROWSER_HOME_URL },
            title = get("title").asTrimmedString().ifBlank { "Google" },
            isLoading = get("isLoading").asBoolean(false),
            canGoBack = get("canGoBack").asBoolean(false),
            canGoForward = get("canGoForward").asBoolean(false),
            progress = get("progress").asInt(0),
            lastError = get("lastError").asTrimmedString().ifBlank { null },
            createdAt = get("createdAt").asLong(System.currentTimeMillis()),
            updatedAt = get("updatedAt").asLong(System.currentTimeMillis())
        )
    }

    private fun JsonObject.toBrowserHistoryRecord(profileIdFallback: String): BrowserHistoryRecord? {
        val id = get("id").asTrimmedString()
        val url = get("url").asTrimmedString()
        if (id.isBlank() || url.isBlank()) return null
        return BrowserHistoryRecord(
            id = id,
            profileId = get("profileId").asTrimmedString().ifBlank { profileIdFallback },
            title = get("title").asTrimmedString().ifBlank { url },
            url = url,
            visitedAt = get("visitedAt").asLong(System.currentTimeMillis())
        )
    }

    private fun JsonObject.toBrowserDownloadRecord(profileIdFallback: String): BrowserDownloadRecord? {
        val id = get("id").asTrimmedString()
        val url = get("url").asTrimmedString()
        if (id.isBlank() || url.isBlank()) return null
        return BrowserDownloadRecord(
            id = id,
            profileId = get("profileId").asTrimmedString().ifBlank { profileIdFallback },
            fileName = get("fileName").asTrimmedString().ifBlank { url.substringAfterLast('/') },
            url = url,
            mimeType = get("mimeType").asTrimmedString().ifBlank { null },
            destination = get("destination").asTrimmedString().ifBlank { null },
            sizeBytes = get("sizeBytes").asLongOrNull(),
            status = get("status").asTrimmedString().ifBlank { "queued" },
            createdAt = get("createdAt").asLong(System.currentTimeMillis())
        )
    }

    private fun JsonObject.toBrowserPasswordRecord(profileIdFallback: String): BrowserPasswordRecord? {
        val id = get("id").asTrimmedString()
        val origin = get("origin").asTrimmedString()
        if (id.isBlank() || origin.isBlank()) return null
        return BrowserPasswordRecord(
            id = id,
            profileId = get("profileId").asTrimmedString().ifBlank { profileIdFallback },
            origin = origin,
            username = get("username").asTrimmedString().ifBlank { "Account" },
            password = get("password").asTrimmedString(),
            createdAt = get("createdAt").asLong(System.currentTimeMillis()),
            updatedAt = get("updatedAt").asLong(System.currentTimeMillis())
        )
    }

    private fun JsonObject.toBrowserStoredOriginData(): BrowserStoredOriginData {
        return BrowserStoredOriginData(
            cookies = get("cookies").asTrimmedString().ifBlank { null },
            localStorage = getAsJsonObject("localStorage")
                ?.entrySet()
                ?.associate { it.key to it.value.asTrimmedString() }
                .orEmpty(),
            sessionStorage = getAsJsonObject("sessionStorage")
                ?.entrySet()
                ?.associate { it.key to it.value.asTrimmedString() }
                .orEmpty(),
            updatedAt = get("updatedAt").asLong(System.currentTimeMillis())
        )
    }

    private fun JsonElement?.asJsonObjectOrNull(): JsonObject? =
        this?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonElement?.asJsonArrayOrNull() =
        this?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonElement?.asTrimmedString(): String =
        when {
            this == null || isJsonNull -> ""
            isJsonPrimitive && asJsonPrimitive.isString -> asString.trim()
            isJsonPrimitive -> runCatching { asJsonPrimitive.toString().trim('"').trim() }.getOrDefault("")
            else -> ""
        }

    private fun JsonElement?.asBoolean(default: Boolean): Boolean =
        runCatching {
            when {
                this == null || isJsonNull -> default
                isJsonPrimitive && asJsonPrimitive.isBoolean -> asBoolean
                isJsonPrimitive && asJsonPrimitive.isString -> asString.equals("true", ignoreCase = true)
                isJsonPrimitive && asJsonPrimitive.isNumber -> asInt != 0
                else -> default
            }
        }.getOrDefault(default)

    private fun JsonElement?.asInt(default: Int): Int =
        runCatching {
            when {
                this == null || isJsonNull -> default
                isJsonPrimitive && asJsonPrimitive.isNumber -> asInt
                isJsonPrimitive && asJsonPrimitive.isString -> asString.toIntOrNull() ?: default
                else -> default
            }
        }.getOrDefault(default)

    private fun JsonElement?.asLong(default: Long): Long =
        asLongOrNull() ?: default

    private fun JsonElement?.asLongOrNull(): Long? =
        runCatching {
            when {
                this == null || isJsonNull -> null
                isJsonPrimitive && asJsonPrimitive.isNumber -> asLong
                isJsonPrimitive && asJsonPrimitive.isString -> asString.toLongOrNull()
                else -> null
            }
        }.getOrNull()

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
