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
        val cleanEmail = email?.trim()?.lowercase()?.takeIf { isEmailAddress(it) }
        if (cleanEmail == null) {
            updateState { current -> current.copy(statusMessage = "Sign in with Google or Outlook to create a browser profile") }
            return
        }
        val id = "mail-${slugify(cleanEmail).ifBlank { UUID.randomUUID().toString().take(8) }}"
        updateState { current ->
            if (current.profiles.any { it.email.equals(cleanEmail, ignoreCase = true) }) {
                current.copy(statusMessage = "Profile already exists")
            } else {
                val profile = BrowserProfileRecord(
                    id = id,
                    name = cleanName,
                    email = cleanEmail,
                    authProvider = providerFromEmail(cleanEmail)
                )
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

    fun startGoogleSignInProfile(): String {
        return startConnectedSignInProfile(BROWSER_PROVIDER_GOOGLE, GOOGLE_SIGN_IN_URL)
    }

    fun startOutlookSignInProfile(): String {
        return startConnectedSignInProfile(BROWSER_PROVIDER_OUTLOOK, OUTLOOK_SIGN_IN_URL)
    }

    private fun startConnectedSignInProfile(provider: String, signInUrl: String): String {
        val id = "${provider}-${UUID.randomUUID().toString().take(8)}"
        val tab = BrowserTabRecord(
            id = "tab-$id",
            profileId = id,
            url = signInUrl,
            title = if (provider == BROWSER_PROVIDER_OUTLOOK) "Outlook sign-in" else "Google sign-in"
        )
        updateState { current ->
            current.copy(
                profiles = current.profiles + BrowserProfileRecord(
                    id = id,
                    name = if (provider == BROWSER_PROVIDER_OUTLOOK) "Outlook Account" else "Google Account",
                    authProvider = provider,
                    pendingSignIn = true
                ),
                activeProfileId = id,
                tabsByProfile = current.tabsByProfile + (id to listOf(tab)),
                selectedTabByProfile = current.selectedTabByProfile + (id to tab.id),
                historyByProfile = current.historyByProfile + (id to emptyList()),
                downloadsByProfile = current.downloadsByProfile + (id to emptyList()),
                passwordsByProfile = current.passwordsByProfile + (id to emptyList()),
                storageByProfile = current.storageByProfile + (id to emptyMap()),
                statusMessage = if (provider == BROWSER_PROVIDER_OUTLOOK) "Sign in with Outlook" else "Sign in with Google"
            )
        }
        return id
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

    fun clearCookies(profileId: String, range: BrowserClearRange) {
        val now = System.currentTimeMillis()
        val cutoff = range.durationMillis?.let { now - it }
        updateState { current ->
            val currentStorage = current.storageByProfile[profileId].orEmpty()
            val retainedStorage = if (cutoff == null) {
                emptyMap()
            } else {
                currentStorage.filterValues { stored ->
                    val updatedAt = stored.updatedAt.takeIf { it > 0L } ?: now
                    updatedAt < cutoff
                }
            }
            current.copy(
                storageByProfile = current.storageByProfile + (profileId to retainedStorage),
                statusMessage = "Cleared ${range.label.lowercase()} cookies and site data for ${profileLabel(current, profileId)}"
            )
        }
    }

    fun addDownload(record: BrowserDownloadRecord) {
        if (record.profileId.isBlank()) return
        updateState { current ->
            val downloads = current.downloadsByProfile[record.profileId].orEmpty().toMutableList()
            downloads.removeAll { download -> download.id == record.id || download.url == record.url && download.fileName == record.fileName }
            downloads.add(0, record.copy(createdAt = record.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis()))
            current.copy(downloadsByProfile = current.downloadsByProfile + (record.profileId to downloads.take(500)))
        }
    }

    fun clearDownloads(profileId: String) {
        updateState { current ->
            current.copy(
                downloadsByProfile = current.downloadsByProfile + (profileId to emptyList()),
                statusMessage = "Cleared downloads for ${profileLabel(current, profileId)}"
            )
        }
    }

    fun addPassword(profileId: String, origin: String, username: String, password: String) {
        val cleanOrigin = origin.trim().ifBlank { return }
        val cleanUser = username.trim().ifBlank { "Account" }
        val cleanPassword = password.ifBlank { return }
        updateState { current ->
            upsertPassword(current, profileId, cleanOrigin, cleanUser, cleanPassword)
        }
    }

    fun offerDetectedPassword(profileId: String, origin: String, username: String, password: String) {
        val cleanOrigin = origin.trim().ifBlank { return }
        val cleanUser = username.trim().ifBlank { "Account" }
        val cleanPassword = password.ifBlank { return }
        updateState { current ->
            val existing = current.passwordsByProfile[profileId].orEmpty()
                .firstOrNull { it.origin == cleanOrigin && it.username == cleanUser }
            if (existing?.password == cleanPassword) return@updateState current
            val pending = current.pendingPasswordPrompt
            if (
                pending?.profileId == profileId &&
                pending.origin == cleanOrigin &&
                pending.username == cleanUser &&
                pending.password == cleanPassword
            ) {
                return@updateState current
            }
            current.copy(
                pendingPasswordPrompt = BrowserPasswordPrompt(
                    profileId = profileId,
                    origin = cleanOrigin,
                    username = cleanUser,
                    password = cleanPassword
                )
            )
        }
    }

    fun confirmPendingPasswordSave() {
        updateState { current ->
            val prompt = current.pendingPasswordPrompt ?: return@updateState current
            upsertPassword(
                current = current,
                profileId = prompt.profileId,
                origin = prompt.origin,
                username = prompt.username,
                password = prompt.password
            ).copy(
                pendingPasswordPrompt = null,
                statusMessage = "Saved password for ${prompt.origin}"
            )
        }
    }

    fun dismissPendingPasswordSave() {
        updateState { current -> current.copy(pendingPasswordPrompt = null) }
    }

    fun deletePassword(profileId: String, passwordId: String) {
        updateState { current ->
            val updated = current.passwordsByProfile[profileId].orEmpty()
                .filterNot { record -> record.id == passwordId }
            current.copy(
                passwordsByProfile = current.passwordsByProfile + (profileId to updated),
                statusMessage = "Deleted saved password"
            )
        }
    }

    fun updateStorage(profileId: String, origin: String, cookies: String?, localStorage: Map<String, String>, sessionStorage: Map<String, String>) {
        val cleanOrigin = origin.trim().ifBlank { return }
        updateState { current ->
            val profileStorage = current.storageByProfile[profileId].orEmpty().toMutableMap()
            profileStorage[cleanOrigin] = BrowserStoredOriginData(
                cookies = cookies,
                localStorage = localStorage,
                sessionStorage = sessionStorage,
                updatedAt = System.currentTimeMillis()
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
        bindDetectedProfileToActiveProfile(email = email, avatarUrl = null)
    }

    fun bindDetectedProfileToActiveProfile(email: String, avatarUrl: String?) {
        val normalizedEmail = email.trim().lowercase()
        if (!isEmailAddress(normalizedEmail)) return
        val cleanAvatarUrl = avatarUrl?.trim()?.takeIf { isSupportedAvatarUrl(it) }
        updateState { current ->
            val activeProfile = current.activeProfile ?: return@updateState current
            if (!activeProfile.email.isNullOrBlank() && !activeProfile.email.equals(normalizedEmail, ignoreCase = true)) {
                return@updateState current
            }
            val displayName = deriveProfileLabelFromEmail(normalizedEmail)
            val connectedProfile = activeProfile.copy(
                name = if (activeProfile.name.isBlank() || activeProfile.pendingSignIn) displayName else activeProfile.name,
                email = normalizedEmail,
                avatarUrl = cleanAvatarUrl ?: activeProfile.avatarUrl,
                authProvider = providerFromEmail(normalizedEmail, activeProfile.authProvider),
                pendingSignIn = false
            )
            current.copy(
                profiles = current.profiles.mapNotNull { profile ->
                    if (profile.id == activeProfile.id) {
                        connectedProfile
                    } else if (profile.email.equals(normalizedEmail, ignoreCase = true)) {
                        null
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

    private fun upsertPassword(
        current: BrowserUiState,
        profileId: String,
        origin: String,
        username: String,
        password: String
    ): BrowserUiState {
        val passwords = current.passwordsByProfile[profileId].orEmpty().toMutableList()
        val existingIndex = passwords.indexOfFirst { it.origin == origin && it.username == username }
        val existing = passwords.getOrNull(existingIndex)
        val record = BrowserPasswordRecord(
            id = existing?.id ?: UUID.randomUUID().toString(),
            profileId = profileId,
            origin = origin,
            username = username,
            password = password,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        if (existingIndex >= 0) {
            passwords[existingIndex] = record
        } else {
            passwords.add(0, record)
        }
        return current.copy(passwordsByProfile = current.passwordsByProfile + (profileId to passwords))
    }

    private fun profileLabel(current: BrowserUiState, profileId: String): String {
        return current.profiles.firstOrNull { profile -> profile.id == profileId }?.name ?: "this profile"
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

    private fun providerFromEmail(email: String, fallback: String = BROWSER_PROVIDER_LOCAL): String {
        val domain = email.substringAfter('@', "").lowercase()
        return when {
            domain == "gmail.com" || domain == "googlemail.com" -> BROWSER_PROVIDER_GOOGLE
            domain == "outlook.com" || domain == "hotmail.com" || domain == "live.com" -> BROWSER_PROVIDER_OUTLOOK
            fallback != BROWSER_PROVIDER_LOCAL -> fallback
            else -> BROWSER_PROVIDER_LOCAL
        }
    }

    private fun isSupportedAvatarUrl(value: String): Boolean {
        return value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("data:image/", ignoreCase = true)
    }
}
