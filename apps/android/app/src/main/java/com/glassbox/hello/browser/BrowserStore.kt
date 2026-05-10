package com.glassbox.hello.browser

import android.content.Context
import com.google.gson.Gson
import java.io.File

class BrowserStore(context: Context) {
    private val gson = Gson()
    private val stateFile = File(File(context.filesDir, "browser"), "browser_state.json")
    private val lock = Any()

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
        state.ensureDefaults()
    }

    fun save(state: BrowserPersistedState) = synchronized(lock) {
        saveLocked(state.ensureDefaults())
    }

    private fun saveLocked(state: BrowserPersistedState) {
        stateFile.bufferedWriter().use { writer ->
            gson.toJson(state.ensureDefaults(), writer)
        }
    }

    private fun BrowserPersistedState.ensureDefaults(): BrowserPersistedState {
        val profiles = if (profiles.isEmpty()) listOf(BrowserProfileRecord("default", "Default")) else profiles
        val activeProfileId = if (profiles.any { it.id == this.activeProfileId }) this.activeProfileId else profiles.first().id
        val tabsByProfile = tabsByProfile.toMutableMap()
        val selectedTabByProfile = selectedTabByProfile.toMutableMap()
        val historyByProfile = historyByProfile.toMutableMap()
        val downloadsByProfile = downloadsByProfile.toMutableMap()
        val passwordsByProfile = passwordsByProfile.toMutableMap()
        val storageByProfile = storageByProfile.toMutableMap()

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
}

fun BrowserPersistedState.profileTabs(profileId: String): List<BrowserTabRecord> = tabsByProfile[profileId].orEmpty()

fun BrowserPersistedState.selectedTab(profileId: String): String? = selectedTabByProfile[profileId]

fun BrowserPersistedState.storage(profileId: String): Map<String, BrowserStoredOriginData> = storageByProfile[profileId].orEmpty()
