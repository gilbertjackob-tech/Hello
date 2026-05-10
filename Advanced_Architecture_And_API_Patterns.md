# Advanced Browser Architecture & API Patterns
## Performance, Security, and Scalability for Chrome-Level Implementation

---

## 🏛️ Advanced MVVM Architecture with Clean Code

### Layered Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                   PRESENTATION LAYER                     │
│  (Activities, Fragments, ViewModels, LiveData/Flow)      │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────v──────────────────────────────────┐
│                   USE CASE LAYER                         │
│  (Business Logic, Interactors, State Management)         │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────v──────────────────────────────────┐
│                   REPOSITORY LAYER                       │
│  (Data Abstraction, Cache Strategy, API Integration)     │
└──────────────────────┬──────────────────────────────────┘
                       │
         ┌─────────────┴──────────────┐
         │                            │
┌────────v──────────┐      ┌──────────v────────┐
│   LOCAL DATA      │      │   REMOTE DATA     │
│  - Room Database  │      │   - REST API      │
│  - SharedPrefs    │      │   - WebSocket     │
│  - Files/Cache    │      │   - Sync Service  │
└───────────────────┘      └───────────────────┘
```

### UseCase Pattern Implementation

```kotlin
// Base UseCase
abstract class UseCase<in Params, out Result> {
    abstract suspend operator fun invoke(params: Params): Result
}

// Specific UseCases
class GetProfileUseCase @Inject constructor(
    private val repository: BrowserRepository
) : UseCase<Unit, Flow<ProfileEntity>>() {
    override suspend fun invoke(params: Unit): Flow<ProfileEntity> {
        return repository.getActiveProfile()
    }
}

class SearchHistoryUseCase @Inject constructor(
    private val repository: BrowserRepository
) : UseCase<SearchHistoryParams, Flow<List<HistoryEntity>>>() {
    data class SearchHistoryParams(
        val query: String,
        val profileId: Int,
        val limit: Int = 50
    )
    
    override suspend fun invoke(params: SearchHistoryParams): Flow<List<HistoryEntity>> {
        if (params.query.isBlank()) {
            return flowOf(emptyList())
        }
        return repository.searchHistory(params.query, params.profileId)
    }
}

// ViewModel using UseCases
class BrowserViewModel @Inject constructor(
    private val getProfileUseCase: GetProfileUseCase,
    private val searchHistoryUseCase: SearchHistoryUseCase,
    private val downloadFileUseCase: DownloadFileUseCase
) : ViewModel() {
    
    private val _state = MutableStateFlow<BrowserState>(BrowserState.Loading)
    val state: StateFlow<BrowserState> = _state.asStateFlow()
    
    init {
        loadProfile()
    }
    
    private fun loadProfile() {
        viewModelScope.launch {
            getProfileUseCase(Unit)
                .catch { e ->
                    _state.value = BrowserState.Error(e.message ?: "Unknown error")
                }
                .collect { profile ->
                    _state.value = BrowserState.Success(profile)
                }
        }
    }
}

// State Management
sealed class BrowserState {
    object Loading : BrowserState()
    data class Success(val profile: ProfileEntity) : BrowserState()
    data class Error(val message: String) : BrowserState()
}
```

---

## 🔗 RESTful API Design with Best Practices

### API Routes Architecture

```typescript
// Base API Routes Structure
/api/v1/
├── /profiles
│   ├── GET / - List all profiles for user
│   ├── POST / - Create new profile
│   ├── GET /:id - Get specific profile
│   ├── PUT /:id - Update profile
│   ├── DELETE /:id - Delete profile
│   ├── POST /:id/activate - Set as active
│   ├── POST /:id/sync - Trigger manual sync
│   └── GET /:id/details - Full profile with stats
│
├── /history
│   ├── GET /?profileId=X&limit=50&offset=0 - Paginated history
│   ├── POST / - Add history entry
│   ├── DELETE /:id - Remove single entry
│   ├── DELETE /?profileId=X&before=timestamp - Bulk delete
│   ├── GET /search?q=term&profileId=X - Search
│   ├── POST /export?profileId=X - Export history
│   └── POST /:id/bookmark - Toggle bookmark
│
├── /downloads
│   ├── GET /?profileId=X&status=active - Get downloads
│   ├── POST / - Start new download
│   ├── GET /:id - Download details
│   ├── PUT /:id - Update (pause/resume)
│   ├── DELETE /:id - Cancel/remove
│   ├── GET /:id/progress - Real-time progress
│   └── GET / /stats - Download statistics
│
├── /cache
│   ├── GET /stats - Cache statistics
│   ├── DELETE / - Clear all cache
│   ├── PUT /settings - Configure cache
│   └── POST /optimize - Optimize cache
│
├── /cookies
│   ├── GET /?domain=example.com&profileId=X - List cookies
│   ├── POST / - Save cookie
│   ├── DELETE /:id - Remove cookie
│   └── DELETE /?profileId=X - Clear all cookies
│
├── /oauth
│   ├── POST /authorize - OAuth authorization
│   ├── POST /callback - OAuth callback handler
│   ├── POST /refresh - Refresh access token
│   ├── POST /revoke - Revoke token
│   └── GET /status/:provider - Provider status
│
├── /sync
│   ├── POST /request - Request data sync
│   ├── GET /status/:profileId - Sync status
│   ├── WS /ws - WebSocket connection
│   │   ├── subscribe-sync - Subscribe to sync events
│   │   ├── request-sync - Request immediate sync
│   │   └── sync-update - Broadcast sync updates
│   │
│   ├── /gmail
│   │   ├── POST /sync - Sync Gmail emails
│   │   ├── GET /folders - Gmail folders
│   │   └── POST /configure - Configure Gmail sync
│   │
│   ├── /outlook
│   │   ├── POST /sync - Sync Outlook emails
│   │   ├── GET /folders - Outlook folders
│   │   └── POST /configure - Configure Outlook sync
│   │
│   └── /icloud
│       ├── POST /sync - Sync iCloud data
│       ├── GET /folders - iCloud folders
│       └── POST /configure - Configure iCloud sync
│
└── /settings
    ├── GET /general - General browser settings
    ├── PUT /general - Update general settings
    ├── GET /privacy - Privacy settings
    ├── PUT /privacy - Update privacy settings
    ├── GET /advanced - Advanced settings
    └── PUT /advanced - Update advanced settings
```

### TypeScript API Response Models

```typescript
// Generic API Response Wrapper
interface ApiResponse<T> {
    success: boolean;
    data?: T;
    error?: ApiError;
    metadata?: ResponseMetadata;
    timestamp: number;
}

interface ApiError {
    code: string;
    message: string;
    details?: Record<string, any>;
}

interface ResponseMetadata {
    pagination?: {
        page: number;
        limit: number;
        total: number;
        totalPages: number;
    };
    caching?: {
        cached: boolean;
        expiresAt: number;
    };
    timing?: {
        processingTime: number;
        cacheHitTime?: number;
    };
}

// Profile Models
interface ProfileResponse {
    id: string;
    userId: string;
    name: string;
    type: 'gmail' | 'outlook' | 'icloud' | 'custom';
    email?: string;
    icon?: string;
    isActive: boolean;
    isSyncEnabled: boolean;
    syncInterval: number; // minutes
    lastSyncTime?: number;
    syncStatus: 'idle' | 'syncing' | 'error';
    stats: {
        historyCount: number;
        bookmarkCount: number;
        downloadCount: number;
        totalCacheSize: number;
    };
    createdAt: number;
    updatedAt: number;
}

// History Models
interface HistoryResponse {
    id: string;
    profileId: string;
    url: string;
    title?: string;
    faviconUrl?: string;
    description?: string;
    thumbnail?: string;
    timestamp: number;
    visitCount: number;
    lastVisited: number;
    isBookmarked: boolean;
    tags?: string[];
    category?: string;
}

// Download Models
interface DownloadResponse {
    id: string;
    profileId: string;
    url: string;
    fileName: string;
    filePath: string;
    fileSize: number;
    downloadedSize: number;
    progress: number; // 0-100
    status: 'pending' | 'downloading' | 'completed' | 'failed' | 'paused';
    mimeType?: string;
    startTime: number;
    endTime?: number;
    estimatedTimeRemaining?: number;
    downloadSpeed?: number; // bytes per second
    error?: string;
}

// Cache Models
interface CacheStatsResponse {
    totalSize: number;
    itemCount: number;
    oldestItem?: number;
    newestItem?: number;
    hitRate: number;
    byContentType: Record<string, number>;
}

// Sync Models
interface SyncStatusResponse {
    profileId: string;
    provider: string;
    status: 'idle' | 'syncing' | 'pending' | 'error';
    lastSyncTime?: number;
    nextSyncTime?: number;
    itemsToSync: number;
    itemsSynced: number;
    error?: string;
}

interface SyncEventPayload {
    type: 'profile' | 'history' | 'download' | 'email';
    action: 'add' | 'update' | 'delete' | 'clear';
    data: any;
    timestamp: number;
    profileId: string;
}
```

---

## 🔐 Advanced Security Implementation

### Encryption & Token Management

```kotlin
// Encrypted Data Store with Tink
class SecureDataStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val dataStore = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun saveToken(key: String, token: String) {
        dataStore.edit().putString(key, token).apply()
    }
    
    fun getToken(key: String): String? {
        return dataStore.getString(key, null)
    }
    
    fun deleteToken(key: String) {
        dataStore.edit().remove(key).apply()
    }
}

// OAuth2 Token Management with Refresh
class TokenManager(
    private val secureDataStore: SecureDataStore,
    private val apiClient: ApiClient
) {
    companion object {
        const val TOKEN_EXPIRY_BUFFER = 5 * 60 * 1000 // 5 minutes
    }
    
    suspend fun ensureValidToken(provider: String): String? {
        val token = secureDataStore.getToken("${provider}_access_token") ?: return null
        val expiryTime = secureDataStore.getToken("${provider}_expiry_time")?.toLongOrNull() ?: 0
        
        return if (System.currentTimeMillis() + TOKEN_EXPIRY_BUFFER > expiryTime) {
            // Token expired, refresh it
            refreshToken(provider)
        } else {
            token
        }
    }
    
    private suspend fun refreshToken(provider: String): String? {
        return try {
            val refreshToken = secureDataStore.getToken("${provider}_refresh_token") ?: return null
            
            val response = apiClient.api.refreshToken(
                TokenRefreshRequest(
                    provider = provider,
                    refreshToken = refreshToken
                )
            )
            
            // Save new tokens
            secureDataStore.saveToken("${provider}_access_token", response.accessToken)
            secureDataStore.saveToken("${provider}_expiry_time", (System.currentTimeMillis() + response.expiresIn * 1000).toString())
            
            response.accessToken
        } catch (e: Exception) {
            Log.e("TokenManager", "Token refresh failed", e)
            null
        }
    }
}

// SSL/TLS Pinning
class CertificatePinning(private val context: Context) {
    
    fun getOkHttpClient(): OkHttpClient {
        val pins = listOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", // Primary
            "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="  // Backup
        )
        
        val certificatePinner = CertificatePinner.Builder()
            .add("api.example.com", *pins.toTypedArray())
            .build()
        
        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .build()
    }
}
```

### Content Security Policy (CSP) Headers

```typescript
// Express middleware for security headers
app.use((req, res, next) => {
    // Content Security Policy
    res.setHeader(
        'Content-Security-Policy',
        "default-src 'self'; " +
        "script-src 'self' 'unsafe-inline'; " +
        "style-src 'self' 'unsafe-inline'; " +
        "img-src 'self' data: https:; " +
        "font-src 'self' data:; " +
        "connect-src 'self' https://accounts.google.com https://login.microsoftonline.com https://appleid.apple.com; " +
        "frame-ancestors 'none';"
    );
    
    // Other security headers
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('X-XSS-Protection', '1; mode=block');
    res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
    res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
    
    next();
});
```

---

## ⚡ Performance Optimization Strategies

### 1. Intelligent Caching Strategy

```kotlin
// Multi-level Cache Strategy
class CacheStrategy {
    enum class CacheLevel {
        MEMORY,      // Fast, limited size
        DISK,        // Larger, slower
        NETWORK      // Slowest, freshest
    }
    
    sealed class CachePolicy {
        object CacheOnly : CachePolicy()
        object NetworkOnly : CachePolicy()
        data class CacheFirst(val maxAge: Long = 24 * 60 * 60 * 1000) : CachePolicy()
        data class NetworkFirst(val timeout: Long = 5000) : CachePolicy()
        data class CacheAndSync(val maxAge: Long = 1 * 60 * 60 * 1000) : CachePolicy()
    }
}

// Interceptor with Cache Strategy
class CacheInterceptor(
    private val cacheRepository: CacheRepository,
    private val cachePolicy: CacheStrategy.CachePolicy
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cacheKey = request.url.toString()
        
        return when (cachePolicy) {
            is CacheStrategy.CachePolicy.NetworkFirst -> {
                try {
                    val networkResponse = chain.proceed(request)
                    // Cache successful response
                    if (networkResponse.isSuccessful) {
                        runBlocking {
                            cacheRepository.cache(cacheKey, networkResponse.body?.bytes() ?: byteArrayOf())
                        }
                    }
                    networkResponse
                } catch (e: Exception) {
                    // Use cached response on failure
                    val cachedResponse = runBlocking {
                        cacheRepository.get(cacheKey)
                    }
                    cachedResponse?.let { Response.Builder()/*..*/ build() } ?: throw e
                }
            }
            
            is CacheStrategy.CachePolicy.CacheFirst -> {
                val cached = runBlocking { cacheRepository.get(cacheKey) }
                cached?.let { Response.Builder()/*..*/ build() } 
                    ?: chain.proceed(request).also { response ->
                        if (response.isSuccessful) {
                            runBlocking {
                                cacheRepository.cache(cacheKey, response.body?.bytes() ?: byteArrayOf())
                            }
                        }
                    }
            }
            
            else -> chain.proceed(request)
        }
    }
}
```

### 2. Memory Optimization

```kotlin
// WebView Memory Management
class BrowserMemoryManager(private val context: Context) {
    
    fun configureWebViewForMemory(webView: WebView) {
        webView.apply {
            settings.apply {
                // Disable unneeded features
                displayZoomControls = false
                builtInZoomControls = false
                
                // Optimize rendering
                layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN
                
                // Memory cache
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                
                // Reduce memory footprint
                blockNetworkImage = false // Or true to save memory
                useWideViewPort = true
                loadWithOverviewMode = true
            }
            
            // Clear old data periodically
            clearFormData()
            clearMatches()
        }
    }
    
    fun cleanupMemory() {
        // Clear image cache
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            WebView.getCurrentWebView()?.evaluateJavascript(
                "javascript:(function() { " +
                "window.localStorage.clear(); " +
                "window.sessionStorage.clear(); " +
                "})()",
                null
            )
        }
    }
    
    fun monitorMemory(webView: WebView) {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val percentUsed = (usedMemory * 100) / maxMemory
        
        if (percentUsed > 80) {
            // Trigger cleanup
            cleanupMemory()
        }
    }
}
```

### 3. Lazy Loading & Pagination

```kotlin
// Paging Library 3
class HistoryPagingSource @Inject constructor(
    private val historyDao: HistoryDao,
    private val profileId: Int
) : PagingSource<Int, HistoryEntity>() {
    
    override suspend fun load(
        params: LoadParams<Int>
    ): LoadResult<Int, HistoryEntity> {
        return try {
            val page = params.key ?: 0
            val pageSize = params.loadSize
            
            val items = historyDao.getHistoryPaged(
                profileId = profileId,
                offset = page * pageSize,
                limit = pageSize
            )
            
            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (items.size < pageSize) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
    
    override fun getRefreshKey(state: PagingState<Int, HistoryEntity>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}

// ViewModel with Paging
class HistoryViewModel @Inject constructor(
    private val repository: BrowserRepository
) : ViewModel() {
    
    val historyPagingFlow: Flow<PagingData<HistoryEntity>> = 
        repository.getHistoryPaging(profileId = 1)
            .cachedIn(viewModelScope)
}
```

### 4. Network Optimization

```kotlin
// Connection Pool & Keep-Alive
val okHttpClient = OkHttpClient.Builder()
    .connectionPool(ConnectionPool(
        maxIdleConnections = 5,
        keepAliveDuration = 5,
        timeUnit = TimeUnit.MINUTES
    ))
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

// Retry Policy with Exponential Backoff
class RetryInterceptor(
    private val maxRetry: Int = 3,
    private val baseDelay: Long = 100 // milliseconds
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var response: Response? = null
        var exception: IOException? = null
        
        repeat(maxRetry) { attempt ->
            try {
                response = chain.proceed(request)
                return response!!
            } catch (e: IOException) {
                exception = e
                if (attempt < maxRetry - 1) {
                    val delay = baseDelay * (2.0.pow(attempt.toDouble())).toLong()
                    Thread.sleep(delay)
                }
            }
        }
        
        throw exception ?: IOException("Unknown error")
    }
}
```

---

## 🎯 Advanced Features Implementation

### 1. Real-time Email Sync with Notification

```typescript
// Gmail Real-time Push Notifications
export class GmailPushService {
    async setupPushNotifications(userId: string, refreshToken: string) {
        const auth = new google.auth.OAuth2(
            process.env.GMAIL_CLIENT_ID,
            process.env.GMAIL_CLIENT_SECRET,
            process.env.GMAIL_REDIRECT_URI
        );
        
        auth.setCredentials({ refresh_token: refreshToken });
        
        const gmail = google.gmail({ version: 'v1', auth });
        
        // Watch for changes
        const response = await gmail.users.watch({
            userId: 'me',
            requestBody: {
                topicName: `projects/${process.env.GCP_PROJECT_ID}/topics/gmail-${userId}`,
                labelIds: ['INBOX']
            }
        });
        
        return response.data;
    }
}

// Pub/Sub Message Handler
export async function handleGmailPush(message: any) {
    const decoded = Buffer.from(message.data, 'base64').toString();
    const { emailAddress } = JSON.parse(decoded);
    
    // Sync new emails
    const emails = await syncNewEmails(emailAddress);
    
    // Emit real-time update via WebSocket
    io.to(`sync-${emailAddress}`).emit('new-emails', {
        count: emails.length,
        preview: emails.slice(0, 3),
        timestamp: Date.now()
    });
}
```

### 2. Offline-First Architecture

```kotlin
// Offline-First Data Sync
class OfflineFirstRepository @Inject constructor(
    private val localDatabase: AppDatabase,
    private val apiClient: ApiClient,
    private val connectivityManager: ConnectivityManager,
    private val scope: CoroutineScope
) {
    
    fun getHistoryWithSync(profileId: Int): Flow<List<HistoryEntity>> = flow {
        // Always emit local data first
        val localData = localDatabase.historyDao()
            .getHistoryByProfile(profileId)
        
        emitAll(localData)
        
        // Try to sync if online
        if (isOnline()) {
            try {
                val remoteData = apiClient.api.getHistory(profileId)
                
                // Merge and update local
                remoteData.forEach { remote ->
                    localDatabase.historyDao().insert(
                        HistoryEntity(
                            url = remote.url,
                            title = remote.title,
                            profileId = profileId,
                            timestamp = remote.timestamp
                        )
                    )
                }
            } catch (e: Exception) {
                // Continue with local data
                Log.w("OfflineFirst", "Sync failed, using local data", e)
            }
        }
    }
    
    private fun isOnline(): Boolean {
        val networkCapabilities = connectivityManager.getNetworkCapabilities(
            connectivityManager.activeNetwork
        )
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
```

### 3. AI-Powered Features (Optional)

```kotlin
// Smart suggestions using ML
class SmartSuggestionsViewModel @Inject constructor(
    private val mlRepository: MLRepository
) : ViewModel() {
    
    val suggestions: Flow<List<SuggestionItem>> = flow {
        while (currentCoroutineContext().isActive) {
            val recent = getRecentHistory()
            val predictions = mlRepository.predictNextUrls(recent)
            emit(predictions)
            delay(5000) // Update every 5 seconds
        }
    }
}

// Autocomplete with History
class UrlAutocompleteViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {
    
    fun getSuggestions(query: String): Flow<List<String>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
        } else {
            historyRepository.searchHistory(query, currentProfileId)
                .map { items ->
                    items
                        .map { it.url }
                        .distinct()
                        .take(5)
                }
                .collect { emit(it) }
        }
    }
}
```

---

## 📊 Monitoring & Analytics

### Crash Reporting & Analytics

```kotlin
// Firebase Crashlytics Integration
class CrashReportingManager(context: Context) {
    private val crashlytics = FirebaseCrashlytics.getInstance()
    
    fun setupCrashReporting() {
        // Enable collection for production
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        
        // Custom exceptions
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            logException(exception)
        }
    }
    
    fun logException(exception: Throwable) {
        crashlytics.recordException(exception)
    }
    
    fun logCustomEvent(event: String, data: Map<String, String>) {
        crashlytics.setCustomKey("event", event)
        data.forEach { (key, value) ->
            crashlytics.setCustomKey(key, value)
        }
    }
}

// Analytics Events
class BrowserAnalytics(private val analytics: FirebaseAnalytics) {
    
    fun trackNavigation(url: String) {
        analytics.logEvent("browser_navigate") {
            param("url_domain", URL(url).host)
            param("timestamp", System.currentTimeMillis())
        }
    }
    
    fun trackDownload(fileName: String, size: Long) {
        analytics.logEvent("browser_download") {
            param("file_name", fileName)
            param("file_size_bytes", size)
            param("timestamp", System.currentTimeMillis())
        }
    }
    
    fun trackSyncEvent(provider: String, itemCount: Int) {
        analytics.logEvent("browser_sync") {
            param("provider", provider)
            param("item_count", itemCount.toLong())
            param("timestamp", System.currentTimeMillis())
        }
    }
}
```

---

## 🔬 Testing Strategy

### Unit Tests

```kotlin
class BrowserViewModelTest {
    
    private lateinit var viewModel: BrowserViewModel
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    @Before
    fun setup() {
        val repository = mockk<BrowserRepository>()
        viewModel = BrowserViewModel(repository)
    }
    
    @Test
    fun `navigating to valid URL loads page`() = runTest {
        val testUrl = "https://example.com"
        
        viewModel.navigateToUrl(testUrl)
        
        assertEquals(testUrl, viewModel.currentUrl.value)
    }
    
    @Test
    fun `invalid URL is corrected with https`() = runTest {
        viewModel.navigateToUrl("example.com")
        
        assertEquals("https://example.com", viewModel.currentUrl.value)
    }
}
```

### Integration Tests

```kotlin
class HistoryIntegrationTest {
    
    @get:Rule
    val databaseRule = DatabaseRule()
    
    private lateinit var database: AppDatabase
    private lateinit var historyDao: HistoryDao
    
    @Before
    fun setup() {
        database = databaseRule.database
        historyDao = database.historyDao()
    }
    
    @Test
    fun `add and retrieve history`() = runTest {
        val history = HistoryEntity(
            url = "https://example.com",
            title = "Example",
            profileId = 1
        )
        
        historyDao.insert(history)
        val retrieved = historyDao.getHistoryByProfile(1, 1).first()
        
        assertEquals(history.url, retrieved.first().url)
    }
}
```

---

## 📱 Gradle Configuration for Production

```kotlin
// build.gradle.kts - Optimized for production
android {
    // ... existing config ...
    
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            buildConfigField("String", "API_BASE_URL", "\"https://api.example.com\"")
            buildConfigField("String", "SOCKET_URL", "\"wss://api.example.com/socket\"")
            
            ndk {
                debugSymbolLevel = "full"
            }
        }
        
        debug {
            debuggable = true
            buildConfigField("String", "API_BASE_URL", "\"http://127.0.0.1:3000\"")
            buildConfigField("String", "SOCKET_URL", "\"ws://127.0.0.1:3000/socket\"")
        }
    }
    
    // Bundle configuration
    bundle {
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
        language {
            enableSplit = true
        }
    }
}

dependencies {
    // ... other dependencies ...
    
    // Profiler & Performance
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    
    // Crash Reporting
    implementation("com.google.firebase:firebase-crashlytics-ktx:18.6.1")
    
    // Analytics
    implementation("com.google.firebase:firebase-analytics-ktx:21.5.1")
    
    // WorkManager for background sync
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
```

---

## 🚀 Deployment Checklist

- [ ] Signed APK with release keystore
- [ ] ProGuard/R8 obfuscation enabled
- [ ] Crash reporting configured
- [ ] Analytics events tracked
- [ ] OAuth credentials secured in BuildConfig
- [ ] API endpoints using production URLs
- [ ] TLS 1.2+ enforced
- [ ] Hardware acceleration enabled
- [ ] Minify enabled with optimization
- [ ] Bundle splitting configured
- [ ] Play Store screenshots & description
- [ ] Privacy policy & terms of service
- [ ] Performance baseline established
- [ ] Security audit completed

---

This advanced architecture makes your Android browser **production-ready and Chrome-competitive**! 🎯
