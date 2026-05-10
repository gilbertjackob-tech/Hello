package com.glassbox.hello.browser

data class BrowserProfileRecord(
    val id: String,
    val name: String,
    val email: String? = null,
    val avatarUrl: String? = null,
    val authProvider: String = BROWSER_PROVIDER_LOCAL,
    val pendingSignIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isConnectedAccount: Boolean get() = !email.isNullOrBlank()
}

data class BrowserTabRecord(
    val id: String,
    val profileId: String,
    val url: String = DEFAULT_BROWSER_HOME_URL,
    val title: String = "Google",
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val progress: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class BrowserHistoryRecord(
    val id: String,
    val profileId: String,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

data class BrowserDownloadRecord(
    val id: String,
    val profileId: String,
    val fileName: String,
    val url: String,
    val mimeType: String? = null,
    val destination: String? = null,
    val sizeBytes: Long? = null,
    val status: String = "queued",
    val createdAt: Long = System.currentTimeMillis()
)

data class BrowserPasswordRecord(
    val id: String,
    val profileId: String,
    val origin: String,
    val username: String,
    val password: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class BrowserPasswordPrompt(
    val profileId: String,
    val origin: String,
    val username: String,
    val password: String
)

enum class BrowserClearRange(val label: String, val durationMillis: Long?) {
    OneHour("1 hour", 60L * 60L * 1000L),
    TwentyFourHours("24 hours", 24L * 60L * 60L * 1000L),
    SevenDays("7 days", 7L * 24L * 60L * 60L * 1000L),
    AllTime("All time", null)
}

data class BrowserDomNode(
    val tag: String,
    val text: String? = null,
    val role: String? = null,
    val id: String? = null,
    val name: String? = null,
    val placeholder: String? = null,
    val selector: String? = null,
    val href: String? = null,
    val bounds: BrowserRect? = null
)

data class BrowserActionTarget(
    val selector: String,
    val label: String? = null,
    val role: String? = null,
    val text: String? = null,
    val tag: String? = null,
    val bounds: BrowserRect? = null
)

data class BrowserRect(
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0
)

data class BrowserPageSummary(
    val url: String = "",
    val title: String = "",
    val description: String? = null,
    val headings: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val forms: List<String> = emptyList(),
    val inputs: List<String> = emptyList(),
    val buttons: List<String> = emptyList(),
    val text: String? = null
)

data class BrowserStoredOriginData(
    val cookies: String? = null,
    val localStorage: Map<String, String> = emptyMap(),
    val sessionStorage: Map<String, String> = emptyMap(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class BrowserPersistedState(
    val profiles: List<BrowserProfileRecord> = listOf(
        BrowserProfileRecord(
            id = DEFAULT_BROWSER_PROFILE_ID,
            name = "Sign in",
            authProvider = BROWSER_PROVIDER_GOOGLE,
            pendingSignIn = true
        )
    ),
    val activeProfileId: String = DEFAULT_BROWSER_PROFILE_ID,
    val tabsByProfile: Map<String, List<BrowserTabRecord>> = mapOf(
        DEFAULT_BROWSER_PROFILE_ID to listOf(
            BrowserTabRecord(id = "tab-$DEFAULT_BROWSER_PROFILE_ID", profileId = DEFAULT_BROWSER_PROFILE_ID)
        )
    ),
    val selectedTabByProfile: Map<String, String?> = mapOf(DEFAULT_BROWSER_PROFILE_ID to "tab-$DEFAULT_BROWSER_PROFILE_ID"),
    val historyByProfile: Map<String, List<BrowserHistoryRecord>> = emptyMap(),
    val downloadsByProfile: Map<String, List<BrowserDownloadRecord>> = emptyMap(),
    val passwordsByProfile: Map<String, List<BrowserPasswordRecord>> = emptyMap(),
    val storageByProfile: Map<String, Map<String, BrowserStoredOriginData>> = emptyMap()
)

data class BrowserUiState(
    val profiles: List<BrowserProfileRecord> = emptyList(),
    val activeProfileId: String = DEFAULT_BROWSER_PROFILE_ID,
    val tabsByProfile: Map<String, List<BrowserTabRecord>> = emptyMap(),
    val selectedTabByProfile: Map<String, String?> = emptyMap(),
    val historyByProfile: Map<String, List<BrowserHistoryRecord>> = emptyMap(),
    val downloadsByProfile: Map<String, List<BrowserDownloadRecord>> = emptyMap(),
    val passwordsByProfile: Map<String, List<BrowserPasswordRecord>> = emptyMap(),
    val storageByProfile: Map<String, Map<String, BrowserStoredOriginData>> = emptyMap(),
    val activePageSummary: BrowserPageSummary? = null,
    val domSnapshot: List<BrowserDomNode> = emptyList(),
    val queryResult: List<BrowserDomNode> = emptyList(),
    val actionTargets: List<BrowserActionTarget> = emptyList(),
    val pendingPasswordPrompt: BrowserPasswordPrompt? = null,
    val statusMessage: String? = null,
    val requestResult: String? = null,
    val errorMessage: String? = null
) {
    val activeProfile: BrowserProfileRecord? get() = profiles.firstOrNull { it.id == activeProfileId }
    val connectedProfiles: List<BrowserProfileRecord> get() = profiles.filter { it.isConnectedAccount }
    val activeTabs: List<BrowserTabRecord> get() = tabsByProfile[activeProfileId].orEmpty()
    val activeTabId: String? get() = selectedTabByProfile[activeProfileId]
    val activeTab: BrowserTabRecord? get() = activeTabs.firstOrNull { it.id == activeTabId }
    val history: List<BrowserHistoryRecord> get() = historyByProfile[activeProfileId].orEmpty()
    val downloads: List<BrowserDownloadRecord> get() = downloadsByProfile[activeProfileId].orEmpty()
    val passwords: List<BrowserPasswordRecord> get() = passwordsByProfile[activeProfileId].orEmpty()
    val storedOrigins: Map<String, BrowserStoredOriginData> get() = storageByProfile[activeProfileId].orEmpty()
}
