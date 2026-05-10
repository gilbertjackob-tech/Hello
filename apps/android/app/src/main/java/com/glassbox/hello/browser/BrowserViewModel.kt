package com.glassbox.hello.browser

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val store = BrowserStore(application.applicationContext)
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    init {
        _state.value = store.load().toUiState()
    }

    fun selectProfile(profileId: String) {
        updateState { current ->
            if (current.profiles.none { it.id == profileId }) return@updateState current
            current.copy(activeProfileId = profileId)
        }
    }

    fun createProfile(name: String, email: String? = null) {
        val cleanName = name.trim().ifBlank { "Profile" }
        val cleanEmail = email?.trim()?.takeIf { it.isNotBlank() }
        val id = slugify(cleanName).ifBlank { "profile-${UUID.randomUUID().toString().take(8)}" }
        updateState { current ->
            if (current.profiles.any { it.id == id }) {
                current.copy(statusMessage = "Profile already exists")
            } else {
                val profile = BrowserProfileRecord(id = id, name = cleanName, email = cleanEmail)
                val tabs = current.tabsByProfile[id].orEmpty().ifEmpty {
                    listOf(BrowserTabRecord(id = "tab-$id", profileId = id))
                }
                current.copy(
                    profiles = current.profiles + profile,
                    activeProfileId = id,
                    tabsByProfile = current.tabsByProfile + (id to tabs),
                    selectedTabByProfile = current.selectedTabByProfile + (id to tabs.firstOrNull()?.id),
                    historyByProfile = current.historyByProfile + (id to current.historyByProfile[id].orEmpty()),
                    downloadsByProfile = current.downloadsByProfile + (id to current.downloadsByProfile[id].orEmpty()),
                    passwordsByProfile = current.passwordsByProfile + (id to current.passwordsByProfile[id].orEmpty()),
                    storageByProfile = current.storageByProfile + (id to current.storageByProfile[id].orEmpty()),
                    statusMessage = "Created profile ${cleanName}"
                )
            }
        }
    }

    fun updateProfile(profileId: String, name: String, email: String?) {
        val cleanName = name.trim().ifBlank { "Profile" }
        val cleanEmail = email?.trim()?.takeIf { it.isNotBlank() }
        updateState { current ->
            current.copy(
                profiles = current.profiles.map { profile ->
                    if (profile.id == profileId) profile.copy(name = cleanName, email = cleanEmail) else profile
                }
            )
        }
    }

    fun createTab(profileId: String = _state.value.activeProfileId, url: String = DEFAULT_BROWSER_HOME_URL) {
        val target = normalizeBrowserUrl(url)
        updateState { current ->
            val tabs = current.tabsByProfile[profileId].orEmpty().toMutableList()
            val tab = BrowserTabRecord(
                id = "tab-${UUID.randomUUID().toString().take(8)}",
                profileId = profileId,
                url = target,
                title = if (target == DEFAULT_BROWSER_HOME_URL) "Google" else target
            )
            tabs += tab
            current.copy(
                tabsByProfile = current.tabsByProfile + (profileId to tabs),
                selectedTabByProfile = current.selectedTabByProfile + (profileId to tab.id),
                statusMessage = "Opened new tab"
            )
        }
    }

    fun closeTab(tabId: String) {
        updateState { current ->
            val tab = current.tabsByProfile.values.flatten().firstOrNull { it.id == tabId } ?: return@updateState current
            val tabs = current.tabsByProfile[tab.profileId].orEmpty().filterNot { it.id == tabId }
            val selected = current.selectedTabByProfile[tab.profileId].takeIf { it != tabId } ?: tabs.firstOrNull()?.id
            val nextTabs = if (tabs.isEmpty()) {
                listOf(BrowserTabRecord(id = "tab-${tab.profileId}", profileId = tab.profileId))
            } else tabs
            val nextSelected = selected ?: nextTabs.first().id
            current.copy(
                tabsByProfile = current.tabsByProfile + (tab.profileId to nextTabs),
                selectedTabByProfile = current.selectedTabByProfile + (tab.profileId to nextSelected),
                statusMessage = "Closed tab"
            )
        }
    }

    fun selectTab(tabId: String) {
        updateState { current ->
            val tab = current.tabsByProfile.values.flatten().firstOrNull { it.id == tabId } ?: return@updateState current
            current.copy(
                activeProfileId = tab.profileId,
                selectedTabByProfile = current.selectedTabByProfile + (tab.profileId to tabId)
            )
        }
    }

    fun updateTabState(tabId: String, update: (BrowserTabRecord) -> BrowserTabRecord) {
        updateState { current ->
            val tab = current.tabsByProfile.values.flatten().firstOrNull { it.id == tabId } ?: return@updateState current
            val tabs = current.tabsByProfile[tab.profileId].orEmpty().map { if (it.id == tabId) update(it) else it }
            current.copy(tabsByProfile = current.tabsByProfile + (tab.profileId to tabs))
        }
    }

    fun updateActiveTabFromWeb(profileId: String, tabId: String, url: String, title: String, loading: Boolean, canGoBack: Boolean, canGoForward: Boolean, progress: Int, error: String? = null) {
        updateState { current ->
            val tabs = current.tabsByProfile[profileId].orEmpty().map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        url = url.ifBlank { tab.url },
                        title = title.ifBlank { tab.title },
                        isLoading = loading,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        progress = progress,
                        lastError = error,
                        updatedAt = System.currentTimeMillis()
                    )
                } else tab
            }
            current.copy(tabsByProfile = current.tabsByProfile + (profileId to tabs))
        }
    }

    fun recordHistory(profileId: String, title: String, url: String) {
        val normalized = normalizeBrowserUrl(url)
        if (normalized == DEFAULT_BROWSER_HOME_URL && title.isBlank()) return
        updateState { current ->
            val history = current.historyByProfile[profileId].orEmpty().toMutableList()
            history.removeAll { it.url == normalized }
            history.add(
                0,
                BrowserHistoryRecord(
                    id = UUID.randomUUID().toString(),
                    profileId = profileId,
                    title = title.ifBlank { normalized },
                    url = normalized
                )
            )
            current.copy(historyByProfile = current.historyByProfile + (profileId to history.take(500)))
        }
    }

    fun clearHistory(profileId: String) {
        updateState { current -> current.copy(historyByProfile = current.historyByProfile + (profileId to emptyList())) }
    }

    fun addDownload(record: BrowserDownloadRecord) {
        updateState { current ->
            val downloads = current.downloadsByProfile[record.profileId].orEmpty().toMutableList()
            downloads.add(0, record)
            current.copy(downloadsByProfile = current.downloadsByProfile + (record.profileId to downloads.take(500)))
        }
    }

    fun addPassword(profileId: String, origin: String, username: String, password: String) {
        val cleanOrigin = origin.trim().ifBlank { return }
        val cleanUser = username.trim().ifBlank { return }
        updateState { current ->
            val passwords = current.passwordsByProfile[profileId].orEmpty().toMutableList()
            val existingIndex = passwords.indexOfFirst { it.origin == cleanOrigin && it.username == cleanUser }
            val record = BrowserPasswordRecord(
                id = passwords.getOrNull(existingIndex)?.id ?: UUID.randomUUID().toString(),
                profileId = profileId,
                origin = cleanOrigin,
                username = cleanUser,
                password = password,
                createdAt = passwords.getOrNull(existingIndex)?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            if (existingIndex >= 0) {
                passwords[existingIndex] = record
            } else {
                passwords.add(0, record)
            }
            current.copy(passwordsByProfile = current.passwordsByProfile + (profileId to passwords))
        }
    }

    fun updateStorage(profileId: String, origin: String, cookies: String?, localStorage: Map<String, String>, sessionStorage: Map<String, String>) {
        val cleanOrigin = origin.trim().ifBlank { return }
        updateState { current ->
            val profileStorage = current.storageByProfile[profileId].orEmpty().toMutableMap()
            profileStorage[cleanOrigin] = BrowserStoredOriginData(
                cookies = cookies,
                localStorage = localStorage,
                sessionStorage = sessionStorage
            )
            current.copy(storageByProfile = current.storageByProfile + (profileId to profileStorage))
        }
    }

    fun storedOrigin(profileId: String, origin: String): BrowserStoredOriginData? {
        return _state.value.storageByProfile[profileId].orEmpty()[origin]
    }

    fun setSummary(summary: BrowserPageSummary) {
        updateState { current -> current.copy(activePageSummary = summary, errorMessage = null) }
    }

    fun setDomSnapshot(nodes: List<BrowserDomNode>) {
        updateState { current -> current.copy(domSnapshot = nodes) }
    }

    fun setQueryResult(nodes: List<BrowserDomNode>) {
        updateState { current -> current.copy(queryResult = nodes) }
    }

    fun setActionTargets(nodes: List<BrowserActionTarget>) {
        updateState { current -> current.copy(actionTargets = nodes) }
    }

    fun setRequestResult(result: String) {
        updateState { current -> current.copy(requestResult = result, errorMessage = null) }
    }

    fun setStatusMessage(message: String?) {
        updateState { current -> current.copy(statusMessage = message) }
    }

    fun setError(message: String?) {
        updateState { current -> current.copy(errorMessage = message) }
    }

    fun resolveActiveOrigin(): String? {
        val tab = _state.value.activeTab ?: return null
        return extractOrigin(tab.url)
    }

    fun applyPageUrl(profileId: String, tabId: String, url: String) {
        updateTabState(tabId) { tab -> tab.copy(url = normalizeBrowserUrl(url), updatedAt = System.currentTimeMillis()) }
        val normalized = normalizeBrowserUrl(url)
        recordHistory(profileId, normalized, normalized)
    }

    fun selectProfileAndTab(profileId: String) {
        selectProfile(profileId)
        val tab = _state.value.tabsByProfile[profileId].orEmpty().firstOrNull()
        if (tab != null) {
            updateState { current -> current.copy(selectedTabByProfile = current.selectedTabByProfile + (profileId to tab.id)) }
        }
    }

    fun bindDetectedEmailToActiveProfile(email: String) {
        val normalizedEmail = email.trim().lowercase()
        if (!isEmailAddress(normalizedEmail)) return
        updateState { current ->
            val activeProfile = current.activeProfile ?: return@updateState current
            if (activeProfile.email.equals(normalizedEmail, ignoreCase = true)) return@updateState current
            if (!activeProfile.email.isNullOrBlank()) return@updateState current
            val displayName = deriveProfileLabelFromEmail(normalizedEmail)
            current.copy(
                profiles = current.profiles.map { profile ->
                    if (profile.id == activeProfile.id) {
                        profile.copy(name = displayName, email = normalizedEmail)
                    } else {
                        profile
                    }
                },
                statusMessage = "Profile saved for $normalizedEmail"
            )
        }
    }

    fun persist() {
        store.save(_state.value.toPersistedState())
    }

    private fun updateState(block: (BrowserUiState) -> BrowserUiState) {
        val next = block(_state.value)
        if (next != _state.value) {
            _state.value = next
            store.save(next.toPersistedState())
        }
    }

    private fun BrowserPersistedState.toUiState(): BrowserUiState {
        return BrowserUiState(
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

    private fun BrowserUiState.toPersistedState(): BrowserPersistedState {
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

    private fun extractOrigin(url: String): String? {
        return runCatching {
            val uri = Uri.parse(url)
            if (uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank()) return null
            "${uri.scheme}://${uri.host}"
        }.getOrNull()
    }

    private fun slugify(value: String): String {
        return value.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }
}
