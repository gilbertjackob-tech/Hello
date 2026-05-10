package com.glassbox.hello.browser

data class BrowserProfileRecord(
    val id: String,
    val name: String,
    val email: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class BrowserTabRecord(
    val id: String,
    val profileId: String,
    val url: String = "about:blank",
    val title: String = "New tab",
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
    val sessionStorage: Map<String, String> = emptyMap()
)

data class BrowserPersistedState(
    val profiles: List<BrowserProfileRecord> = listOf(
        BrowserProfileRecord(id = "default", name = "Default")
    ),
    val activeProfileId: String = "default",
    val tabsByProfile: Map<String, List<BrowserTabRecord>> = mapOf(
        "default" to listOf(
            BrowserTabRecord(id = "tab-default", profileId = "default")
        )
    ),
    val selectedTabByProfile: Map<String, String?> = mapOf("default" to "tab-default"),
    val historyByProfile: Map<String, List<BrowserHistoryRecord>> = emptyMap(),
    val downloadsByProfile: Map<String, List<BrowserDownloadRecord>> = emptyMap(),
    val passwordsByProfile: Map<String, List<BrowserPasswordRecord>> = emptyMap(),
    val storageByProfile: Map<String, Map<String, BrowserStoredOriginData>> = emptyMap()
)

data class BrowserUiState(
    val profiles: List<BrowserProfileRecord> = emptyList(),
    val activeProfileId: String = "default",
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
    val statusMessage: String? = null,
    val requestResult: String? = null,
    val errorMessage: String? = null
) {
    val activeProfile: BrowserProfileRecord? get() = profiles.firstOrNull { it.id == activeProfileId }
    val activeTabs: List<BrowserTabRecord> get() = tabsByProfile[activeProfileId].orEmpty()
    val activeTabId: String? get() = selectedTabByProfile[activeProfileId]
    val activeTab: BrowserTabRecord? get() = activeTabs.firstOrNull { it.id == activeTabId }
    val history: List<BrowserHistoryRecord> get() = historyByProfile[activeProfileId].orEmpty()
    val downloads: List<BrowserDownloadRecord> get() = downloadsByProfile[activeProfileId].orEmpty()
    val passwords: List<BrowserPasswordRecord> get() = passwordsByProfile[activeProfileId].orEmpty()
    val storedOrigins: Map<String, BrowserStoredOriginData> get() = storageByProfile[activeProfileId].orEmpty()
}
