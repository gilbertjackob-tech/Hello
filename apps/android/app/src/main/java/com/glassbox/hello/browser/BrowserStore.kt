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

    private fun defaultSignInProfile(): BrowserProfileRecord {
        return BrowserProfileRecord(
            id = DEFAULT_BROWSER_PROFILE_ID,
            name = "Sign in",
            authProvider = BROWSER_PROVIDER_GOOGLE,
            pendingSignIn = true
        )
    }
}

fun BrowserPersistedState.profileTabs(profileId: String): List<BrowserTabRecord> = tabsByProfile[profileId].orEmpty()

fun BrowserPersistedState.selectedTab(profileId: String): String? = selectedTabByProfile[profileId]

fun BrowserPersistedState.storage(profileId: String): Map<String, BrowserStoredOriginData> = storageByProfile[profileId].orEmpty()
