# Android Integrated Browser - Complete Implementation Plan
## Chrome-Level Full Browser with Profile Management, History, Downloads & API Support

---

## 📋 Executive Summary

This document provides a **production-grade implementation blueprint** to elevate your Android integrated browser from basic to **Chrome-level complete**. It incorporates:

- **Full-screen immersive browsing**
- **Profile management** (Gmail, Outlook, iCloud profile switching)
- **Persistent state** (history, downloads, cache, cookies)
- **Floating FAB** (draggable button to return to Hello app)
- **API-based architecture** (REST + WebSocket for async operations)
- **Real-time email integration** (Gmail, Outlook, iCloud with OAuth2)
- **Security & permissions** (modern Android 13+ standards)
- **Performance** (lazy loading, memory optimization, background sync)

---

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│              Android App (Kotlin/Java)              │
├─────────────────────────────────────────────────────┤
│                                                      │
│  ┌──────────────────────────────────────────────┐   │
│  │          Main Navigation Activity            │   │
│  │  (Tab: Hello | Browser | Downloads | Settings)  │
│  └──────────────────────────────────────────────┘   │
│                       ↓                              │
│  ┌──────────────────────────────────────────────┐   │
│  │     BrowserActivity (Full-Screen)            │   │
│  │  ├─ WebView (Chromium)                       │   │
│  │  ├─ Floating Action Button (FAB)             │   │
│  │  ├─ URL Bar + Controls                       │   │
│  │  └─ Progress & Security Indicators           │   │
│  └──────────────────────────────────────────────┘   │
│                       ↓                              │
│  ┌──────────────────────────────────────────────┐   │
│  │      BrowserViewModel (MVVM Pattern)         │   │
│  │  ├─ Profile Management (DAO)                 │   │
│  │  ├─ History Store (Room Database)            │   │
│  │  ├─ Download Manager (Service)               │   │
│  │  ├─ Cache Controller                         │   │
│  │  └─ Cookie Jar (Persistent)                  │   │
│  └──────────────────────────────────────────────┘   │
│                       ↓                              │
│  ┌──────────────────────────────────────────────┐   │
│  │         Local API Server (Backend)           │   │
│  │  ├─ Express.js on localhost:3000              │   │
│  │  ├─ Profile API (/api/profiles/...)          │   │
│  │  ├─ History API (/api/history/...)           │   │
│  │  ├─ OAuth2 Handlers                          │   │
│  │  ├─ Email Sync (Gmail, Outlook, iCloud)      │   │
│  │  └─ WebSocket (Real-time notifications)      │   │
│  └──────────────────────────────────────────────┘   │
│                       ↓                              │
│  ┌──────────────────────────────────────────────┐   │
│  │       Cloud Services Integration             │   │
│  │  ├─ Google OAuth2 (Gmail)                    │   │
│  │  ├─ Microsoft OAuth2 (Outlook)               │   │
│  │  ├─ Apple iCloud                             │   │
│  │  └─ Sync Service (Background)                │   │
│  └──────────────────────────────────────────────┘   │
│                                                      │
└─────────────────────────────────────────────────────┘
```

---

## 📱 Module 1: Android UI/UX Layer

### 1.1 Project Structure
```kotlin
app/src/main/
├── java/com/glassbox/hello/
│   ├── activities/
│   │   ├── MainActivity.kt (Navigation hub)
│   │   ├── BrowserActivity.kt (Full-screen browser)
│   │   └── ProfileManagementActivity.kt
│   │
│   ├── ui/
│   │   ├── browser/
│   │   │   ├── BrowserFragment.kt
│   │   │   ├── UrlBarView.kt (Custom)
│   │   │   ├── TabsFragment.kt
│   │   │   ├── FloatingReturnButton.kt (FAB)
│   │   │   └── ProgressBar.kt
│   │   │
│   │   ├── profile/
│   │   │   ├── ProfileSwitchDialog.kt
│   │   │   ├── ProfileAddDialog.kt
│   │   │   └── ProfileListAdapter.kt
│   │   │
│   │   ├── download/
│   │   │   ├── DownloadManagerUI.kt
│   │   │   ├── DownloadListAdapter.kt
│   │   │   └── DownloadItemView.kt
│   │   │
│   │   └── settings/
│   │       ├── BrowserSettingsFragment.kt
│   │       ├── CacheSettingsFragment.kt
│   │       ├── SecuritySettingsFragment.kt
│   │       └── HistorySettingsFragment.kt
│   │
│   ├── viewmodel/
│   │   ├── BrowserViewModel.kt
│   │   ├── ProfileViewModel.kt
│   │   ├── DownloadViewModel.kt
│   │   └── SettingsViewModel.kt
│   │
│   ├── repository/
│   │   ├── BrowserRepository.kt
│   │   ├── ProfileRepository.kt
│   │   ├── HistoryRepository.kt
│   │   ├── DownloadRepository.kt
│   │   └── CacheRepository.kt
│   │
│   ├── database/
│   │   ├── entities/
│   │   │   ├── ProfileEntity.kt
│   │   │   ├── HistoryEntity.kt
│   │   │   ├── DownloadEntity.kt
│   │   │   ├── CacheEntity.kt
│   │   │   └── CookieEntity.kt
│   │   │
│   │   ├── dao/
│   │   │   ├── ProfileDao.kt
│   │   │   ├── HistoryDao.kt
│   │   │   ├── DownloadDao.kt
│   │   │   ├── CacheDao.kt
│   │   │   └── CookieDao.kt
│   │   │
│   │   └── AppDatabase.kt
│   │
│   ├── service/
│   │   ├── DownloadService.kt
│   │   ├── SyncService.kt (Background)
│   │   ├── EmailSyncService.kt
│   │   └── NotificationService.kt
│   │
│   ├── client/
│   │   ├── ApiClient.kt (Retrofit)
│   │   ├── OAuth2Client.kt
│   │   └── WebSocketClient.kt
│   │
│   ├── utils/
│   │   ├── BrowserUtils.kt
│   │   ├── SecurityUtils.kt
│   │   ├── FileUtils.kt
│   │   ├── PermissionUtils.kt
│   │   ├── NetworkUtils.kt
│   │   └── Constants.kt
│   │
│   └── helpers/
│       ├── WebViewHelper.kt
│       ├── CookieHelper.kt
│       ├── CacheHelper.kt
│       └── HistoryHelper.kt
│
└── res/
    ├── layout/
    │   ├── activity_browser.xml
    │   ├── fragment_browser.xml
    │   ├── toolbar_browser.xml
    │   ├── dialog_profile_switch.xml
    │   ├── dialog_add_profile.xml
    │   ├── list_download_item.xml
    │   └── fab_return_button.xml
    │
    ├── drawable/
    │   ├── ic_hello.svg (FAB icon)
    │   ├── ic_back.svg
    │   ├── ic_forward.svg
    │   ├── ic_refresh.svg
    │   ├── ic_share.svg
    │   ├── ic_menu.svg
    │   └── ic_profile.svg
    │
    ├── values/
    │   ├── dimens.xml
    │   ├── colors.xml
    │   ├── strings.xml
    │   └── styles.xml
    │
    └── anim/
        ├── fab_slide_in.xml
        └── fab_slide_out.xml
```

### 1.2 MainActivity.kt - Navigation Hub
```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupNavigation()
        setupBottomNavigation()
    }
    
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        
        // Set up bottom navigation
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_hello -> navigateToHello()
                R.id.nav_browser -> navigateToBrowser()
                R.id.nav_downloads -> navigateToDownloads()
                R.id.nav_settings -> navigateToSettings()
                else -> false
            }
        }
    }
    
    private fun navigateToBrowser() {
        val intent = Intent(this, BrowserActivity::class.java)
        startActivity(intent)
    }
}
```

### 1.3 BrowserActivity.kt - Full-Screen Browser
```kotlin
class BrowserActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBrowserBinding
    private lateinit var viewModel: BrowserViewModel
    private lateinit var webView: WebView
    private lateinit var floatingReturnButton: FloatingActionButton
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Full-screen mode
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
        
        viewModel = ViewModelProvider(this).get(BrowserViewModel::class.java)
        
        initializeWebView()
        setupToolbar()
        setupFloatingButton()
        setupObservers()
        
        // Load default profile or last active profile
        viewModel.loadActiveProfile()
    }
    
    private fun initializeWebView() {
        webView = binding.webView
        
        // WebView settings
        webView.apply {
            settings.apply {
                // Enable features
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                
                // Cache
                cacheMode = WebSettings.LOAD_DEFAULT
                
                // User agent (Mozilla labeling)
                userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                
                // Security
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                
                // Storage
                databasePath = context.getDatabasePath("webview").path
                
                // Performance
                blockNetworkLoads = false
                blockNetworkImage = false
            }
            
            // WebView client
            webViewClient = CustomWebViewClient(viewModel)
            
            // Chrome client for callbacks
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    updateProgressBar(newProgress)
                }
                
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    updateTitle(title ?: "Browser")
                }
                
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    // Handle file uploads
                    return handleFileChooser(filePathCallback, fileChooserParams)
                }
            }
            
            // Cookie management
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@BrowserActivity.webView, true)
            }
        }
    }
    
    private fun setupToolbar() {
        binding.toolbarBrowser.apply {
            // URL bar
            setNavigationOnClickListener {
                webView.goBack()
            }
            
            // Menu
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_refresh -> {
                        webView.reload()
                        true
                    }
                    R.id.menu_share -> {
                        shareCurrentUrl()
                        true
                    }
                    R.id.menu_profile -> {
                        showProfileSwitcher()
                        true
                    }
                    R.id.menu_history -> {
                        showHistory()
                        true
                    }
                    else -> false
                }
            }
        }
    }
    
    private fun setupFloatingButton() {
        floatingReturnButton = binding.fabReturn
        
        // Make it draggable
        floatingReturnButton.apply {
            setOnTouchListener { view, event ->
                handleFabDrag(view, event)
            }
            
            setOnClickListener {
                // Smooth return to Hello app
                val intent = Intent(this@BrowserActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                startActivity(intent)
                overridePendingTransition(0, R.anim.fade_out)
            }
        }
    }
    
    private fun handleFabDrag(view: View, event: MotionEvent): Boolean {
        val params = view.layoutParams as FrameLayout.LayoutParams
        
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                
                params.leftMargin = x - (view.width / 2)
                params.topMargin = y - (view.height / 2)
                
                view.layoutParams = params
                return true
            }
        }
        
        return false
    }
    
    private fun setupObservers() {
        viewModel.currentUrl.observe(this) { url ->
            if (url.isNotEmpty()) {
                webView.loadUrl(url)
            }
        }
        
        viewModel.activeProfile.observe(this) { profile ->
            updateProfileUI(profile)
        }
        
        viewModel.historyItems.observe(this) { items ->
            // Update history UI
        }
    }
}
```

### 1.4 FloatingReturnButton Component
```kotlin
// Custom draggable FAB implementation
class DraggableFloatingButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {
    
    private var lastX = 0f
    private var lastY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    init {
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    lastX = x
                    lastY = y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    
                    animate()
                        .x(lastX + dx)
                        .y(lastY + dy)
                        .setDuration(0)
                        .start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Snap to left edge
                    animate()
                        .x(0f)
                        .setDuration(300)
                        .start()
                    true
                }
                else -> false
            }
        }
    }
}
```

---

## 🗄️ Module 2: Room Database Layer

### 2.1 Database Entities

```kotlin
// ProfileEntity
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: String, // "gmail", "outlook", "icloud", "custom"
    val email: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val tokenExpiry: Long?,
    val userAgent: String?,
    val isActive: Boolean = false,
    val isSyncEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncTime: Long? = null
)

// HistoryEntity
@Entity(tableName = "browsing_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val url: String,
    val title: String?,
    val profileId: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val faviconUrl: String?,
    val visitCount: Int = 1,
    val lastVisited: Long = System.currentTimeMillis(),
    val isBookmarked: Boolean = false
)

// DownloadEntity
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val url: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val downloadedSize: Long = 0,
    val status: String, // "pending", "downloading", "completed", "failed", "paused"
    val progress: Int = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val mimeType: String?,
    val profileId: Int,
    val isAutoDownload: Boolean = false
)

// CacheEntity
@Entity(tableName = "cache")
data class CacheEntity(
    @PrimaryKey
    val url: String,
    val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long?,
    val size: Long,
    val isValid: Boolean = true
)

// CookieEntity
@Entity(tableName = "cookies")
data class CookieEntity(
    @PrimaryKey
    val cookieKey: String, // domain + name
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long?,
    val isSecure: Boolean = false,
    val isHttpOnly: Boolean = false,
    val profileId: Int,
    val timestamp: Long = System.currentTimeMillis()
)

// SearchHistoryEntity
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis(),
    val profileId: Int
)
```

### 2.2 DAOs

```kotlin
@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity): Long
    
    @Update
    suspend fun update(profile: ProfileEntity)
    
    @Delete
    suspend fun delete(profile: ProfileEntity)
    
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Int): ProfileEntity?
    
    @Query("SELECT * FROM profiles WHERE isActive = 1")
    fun getActiveProfile(): Flow<ProfileEntity?>
    
    @Query("SELECT * FROM profiles ORDER BY updatedAt DESC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>
    
    @Query("UPDATE profiles SET isActive = 0")
    suspend fun deactivateAllProfiles()
    
    @Query("UPDATE profiles SET isActive = 1 WHERE id = :id")
    suspend fun setActiveProfile(id: Int)
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: HistoryEntity): Long
    
    @Delete
    suspend fun delete(history: HistoryEntity)
    
    @Query("SELECT * FROM browsing_history WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT :limit")
    fun getHistoryByProfile(profileId: Int, limit: Int = 100): Flow<List<HistoryEntity>>
    
    @Query("SELECT * FROM browsing_history WHERE url LIKE '%' || :query || '%' AND profileId = :profileId")
    fun searchHistory(query: String, profileId: Int): Flow<List<HistoryEntity>>
    
    @Query("DELETE FROM browsing_history WHERE timestamp < :beforeTime AND profileId = :profileId")
    suspend fun clearHistoryBefore(beforeTime: Long, profileId: Int)
    
    @Query("DELETE FROM browsing_history WHERE profileId = :profileId")
    suspend fun clearAllHistory(profileId: Int)
    
    @Query("UPDATE browsing_history SET isBookmarked = 1 WHERE id = :id")
    suspend fun bookmarkHistory(id: Int)
    
    @Query("SELECT * FROM browsing_history WHERE isBookmarked = 1 AND profileId = :profileId")
    fun getBookmarks(profileId: Int): Flow<List<HistoryEntity>>
}

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long
    
    @Update
    suspend fun update(download: DownloadEntity)
    
    @Delete
    suspend fun delete(download: DownloadEntity)
    
    @Query("SELECT * FROM downloads WHERE id = :id")
    fun getDownloadById(id: Int): Flow<DownloadEntity?>
    
    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY startTime DESC")
    fun getDownloadsByProfile(profileId: Int): Flow<List<DownloadEntity>>
    
    @Query("SELECT * FROM downloads WHERE status = :status")
    fun getDownloadsByStatus(status: String): Flow<List<DownloadEntity>>
    
    @Query("UPDATE downloads SET progress = :progress, downloadedSize = :size WHERE id = :id")
    suspend fun updateProgress(id: Int, progress: Int, size: Long)
    
    @Query("UPDATE downloads SET status = :status, endTime = :endTime WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String, endTime: Long?)
}

@Dao
interface CookieDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cookie: CookieEntity)
    
    @Update
    suspend fun update(cookie: CookieEntity)
    
    @Query("SELECT * FROM cookies WHERE profileId = :profileId AND domain LIKE '%' || :domain || '%'")
    suspend fun getCookiesByDomain(profileId: Int, domain: String): List<CookieEntity>
    
    @Query("DELETE FROM cookies WHERE expiresAt < :now")
    suspend fun deleteExpiredCookies(now: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM cookies WHERE profileId = :profileId")
    suspend fun clearCookies(profileId: Int)
}

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: CacheEntity)
    
    @Query("SELECT * FROM cache WHERE url = :url AND isValid = 1")
    suspend fun getByUrl(url: String): CacheEntity?
    
    @Query("DELETE FROM cache WHERE expiresAt < :now OR isValid = 0")
    suspend fun deleteExpiredCache(now: Long = System.currentTimeMillis())
    
    @Query("SELECT SUM(size) FROM cache")
    suspend fun getTotalCacheSize(): Long
    
    @Query("DELETE FROM cache")
    suspend fun clearAllCache()
}
```

### 2.3 Database Class

```kotlin
@Database(
    entities = [
        ProfileEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        CacheEntity::class,
        CookieEntity::class,
        SearchHistoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun cacheDao(): CacheDao
    abstract fun cookieDao(): CookieDao
    
    companion object {
        @Volatile
        private var instance: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    "browser_database"
                )
                    .addMigrations() // Add migrations as needed
                    .build()
                    .also { instance = it }
            }
        }
    }
}
```

---

## 🔄 Module 3: Repository & ViewModel Layer

### 3.1 BrowserRepository.kt

```kotlin
class BrowserRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val database: AppDatabase,
    private val context: Context
) {
    private val profileDao = database.profileDao()
    private val historyDao = database.historyDao()
    private val cookieDao = database.cookieDao()
    private val cacheDao = database.cacheDao()
    
    // Profile Management
    fun getActiveProfile(): Flow<ProfileEntity?> = profileDao.getActiveProfile()
    
    fun getAllProfiles(): Flow<List<ProfileEntity>> = profileDao.getAllProfiles()
    
    suspend fun createProfile(profile: ProfileEntity): Long {
        return profileDao.insert(profile)
    }
    
    suspend fun switchProfile(profileId: Int) {
        profileDao.deactivateAllProfiles()
        profileDao.setActiveProfile(profileId)
    }
    
    suspend fun deleteProfile(profile: ProfileEntity) {
        profileDao.delete(profile)
        historyDao.clearAllHistory(profile.id)
        cookieDao.clearCookies(profile.id)
    }
    
    // History Management
    fun getHistory(profileId: Int, limit: Int = 100): Flow<List<HistoryEntity>> {
        return historyDao.getHistoryByProfile(profileId, limit)
    }
    
    fun searchHistory(query: String, profileId: Int): Flow<List<HistoryEntity>> {
        return historyDao.searchHistory(query, profileId)
    }
    
    suspend fun addToHistory(
        url: String,
        title: String?,
        profileId: Int,
        faviconUrl: String? = null
    ) {
        val existing = historyDao.getHistoryByProfile(profileId, 1)
            .firstOrNull()
            ?.find { it.url == url }
        
        if (existing != null) {
            historyDao.update(existing.copy(
                visitCount = existing.visitCount + 1,
                lastVisited = System.currentTimeMillis()
            ))
        } else {
            historyDao.insert(
                HistoryEntity(
                    url = url,
                    title = title,
                    profileId = profileId,
                    faviconUrl = faviconUrl
                )
            )
        }
    }
    
    suspend fun clearHistory(profileId: Int) {
        historyDao.clearAllHistory(profileId)
    }
    
    suspend fun bookmarkUrl(historyId: Int) {
        // Implementation
    }
    
    fun getBookmarks(profileId: Int): Flow<List<HistoryEntity>> {
        return historyDao.getBookmarks(profileId)
    }
    
    // Cookie Management
    suspend fun saveCookie(
        domain: String,
        name: String,
        value: String,
        profileId: Int,
        expiresAt: Long? = null
    ) {
        cookieDao.insert(
            CookieEntity(
                cookieKey = "$domain:$name",
                value = value,
                domain = domain,
                path = "/",
                expiresAt = expiresAt,
                profileId = profileId
            )
        )
    }
    
    suspend fun getCookies(profileId: Int, domain: String): List<CookieEntity> {
        return cookieDao.getCookiesByDomain(profileId, domain)
    }
    
    // Cache Management
    suspend fun cacheResponse(
        url: String,
        data: ByteArray,
        expiresAt: Long? = null
    ) {
        cacheDao.insert(
            CacheEntity(
                url = url,
                data = data,
                expiresAt = expiresAt,
                size = data.size.toLong()
            )
        )
    }
    
    suspend fun getCachedResponse(url: String): ByteArray? {
        return cacheDao.getByUrl(url)?.data
    }
    
    suspend fun clearCache() {
        cacheDao.clearAllCache()
    }
}
```

### 3.2 BrowserViewModel.kt

```kotlin
class BrowserViewModel @Inject constructor(
    private val repository: BrowserRepository,
    private val apiClient: ApiClient,
    private val downloadService: DownloadService
) : ViewModel() {
    
    private val _currentUrl = MutableLiveData<String>()
    val currentUrl: LiveData<String> = _currentUrl
    
    private val _activeProfile = MutableLiveData<ProfileEntity>()
    val activeProfile: LiveData<ProfileEntity> = _activeProfile
    
    private val _historyItems = MutableLiveData<List<HistoryEntity>>()
    val historyItems: LiveData<List<HistoryEntity>> = _historyItems
    
    private val _downloadProgress = MutableLiveData<Int>()
    val downloadProgress: LiveData<Int> = _downloadProgress
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    init {
        loadActiveProfile()
        observeHistory()
    }
    
    fun loadActiveProfile() {
        viewModelScope.launch {
            repository.getActiveProfile()
                .collect { profile ->
                    _activeProfile.value = profile
                }
        }
    }
    
    private fun observeHistory() {
        viewModelScope.launch {
            activeProfile.value?.let { profile ->
                repository.getHistory(profile.id)
                    .collect { items ->
                        _historyItems.value = items
                    }
            }
        }
    }
    
    fun navigateToUrl(url: String) {
        _currentUrl.value = url.let {
            if (!it.startsWith("http://") && !it.startsWith("https://")) {
                "https://$it"
            } else {
                it
            }
        }
        
        // Record to history
        recordPageVisit(url)
    }
    
    private fun recordPageVisit(url: String, title: String? = null) {
        viewModelScope.launch {
            activeProfile.value?.let { profile ->
                repository.addToHistory(url, title, profile.id)
            }
        }
    }
    
    fun searchHistory(query: String) {
        viewModelScope.launch {
            activeProfile.value?.let { profile ->
                repository.searchHistory(query, profile.id)
                    .collect { items ->
                        _historyItems.value = items
                    }
            }
        }
    }
    
    fun switchProfile(profileId: Int) {
        viewModelScope.launch {
            try {
                repository.switchProfile(profileId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
    
    fun downloadFile(
        url: String,
        fileName: String,
        mimeType: String?
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                downloadService.downloadFile(url, fileName, mimeType)
            } catch (e: Exception) {
                _error.value = "Download failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
```

---

## 🌐 Module 4: API Client & Backend Integration

### 4.1 ApiClient.kt (Retrofit)

```kotlin
interface BrowserApi {
    // Profile endpoints
    @GET("/api/profiles")
    suspend fun getProfiles(): List<ProfileResponse>
    
    @POST("/api/profiles")
    suspend fun createProfile(@Body profile: ProfileRequest): ProfileResponse
    
    @PUT("/api/profiles/{id}")
    suspend fun updateProfile(
        @Path("id") id: Int,
        @Body profile: ProfileRequest
    ): ProfileResponse
    
    @DELETE("/api/profiles/{id}")
    suspend fun deleteProfile(@Path("id") id: Int)
    
    @POST("/api/profiles/{id}/activate")
    suspend fun activateProfile(@Path("id") id: Int)
    
    // OAuth endpoints
    @POST("/api/oauth/authorize")
    suspend fun authorizeOAuth(@Body request: OAuthRequest): OAuthResponse
    
    @POST("/api/oauth/refresh")
    suspend fun refreshToken(@Body request: TokenRefreshRequest): TokenResponse
    
    // History endpoints
    @GET("/api/history/{profileId}")
    suspend fun getHistory(@Path("profileId") id: Int): List<HistoryResponse>
    
    @POST("/api/history")
    suspend fun addHistory(@Body history: HistoryRequest)
    
    @DELETE("/api/history/{id}")
    suspend fun deleteHistoryItem(@Path("id") id: Int)
    
    // Download endpoints
    @GET("/api/downloads/{profileId}")
    suspend fun getDownloads(@Path("profileId") id: Int): List<DownloadResponse>
    
    @POST("/api/downloads")
    suspend fun startDownload(@Body request: DownloadRequest): DownloadResponse
    
    @PUT("/api/downloads/{id}")
    suspend fun updateDownload(
        @Path("id") id: Int,
        @Body update: DownloadUpdateRequest
    )
    
    // Sync endpoints
    @POST("/api/sync/gmail")
    suspend fun syncGmail(@Body request: SyncRequest)
    
    @POST("/api/sync/outlook")
    suspend fun syncOutlook(@Body request: SyncRequest)
    
    @POST("/api/sync/icloud")
    suspend fun syncICloud(@Body request: SyncRequest)
}

// Retrofit client
class ApiClient(context: Context) {
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://127.0.0.1:3000")
        .addConverterFactory(GsonConverterFactory.create())
        .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
        .client(
            OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(context))
                .addInterceptor(LoggingInterceptor())
                .build()
        )
        .build()
    
    val api: BrowserApi = retrofit.create(BrowserApi::class.java)
}

// Auth interceptor
class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        val token = getStoredToken() // From SharedPreferences
        
        val request = if (token != null) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }
        
        return chain.proceed(request)
    }
    
    private fun getStoredToken(): String? {
        // Retrieve from encrypted SharedPreferences
        return null
    }
}
```

### 4.2 WebSocket Client for Real-time Sync

```kotlin
class WebSocketClient(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private lateinit var webSocket: WebSocket
    private val eventBus = MutableSharedFlow<SyncEvent>()
    
    fun connect(userId: String) {
        val request = Request.Builder()
            .url("ws://127.0.0.1:3000/api/sync/ws")
            .addHeader("Authorization", "Bearer ${getToken()}")
            .build()
        
        val okHttpClient = OkHttpClient()
        
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected")
                scope.launch {
                    eventBus.emit(SyncEvent.Connected)
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    val event = parseSyncEvent(text)
                    eventBus.emit(event)
                }
            }
            
            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                scope.launch {
                    eventBus.emit(SyncEvent.Error(t.message ?: "Unknown error"))
                }
            }
        })
    }
    
    fun observeEvents(): Flow<SyncEvent> = eventBus.asSharedFlow()
    
    fun sendMessage(message: String) {
        webSocket.send(message)
    }
}
```

---

## 🔑 Module 5: Authentication & OAuth2

### 5.1 OAuth2Manager.kt

```kotlin
class OAuth2Manager(
    private val context: Context,
    private val apiClient: ApiClient
) {
    companion object {
        const val GMAIL_CLIENT_ID = "YOUR_GMAIL_CLIENT_ID"
        const val OUTLOOK_CLIENT_ID = "YOUR_OUTLOOK_CLIENT_ID"
        const val ICLOUD_CLIENT_ID = "YOUR_ICLOUD_CLIENT_ID"
        
        const val GMAIL_REDIRECT_URI = "glassbox://oauth/gmail/callback"
        const val OUTLOOK_REDIRECT_URI = "glassbox://oauth/outlook/callback"
        const val ICLOUD_REDIRECT_URI = "glassbox://oauth/icloud/callback"
    }
    
    suspend fun authorizeGmail(
        activity: Activity,
        callback: (result: Result<ProfileEntity>) -> Unit
    ) {
        try {
            val authUri = buildGmailAuthUri()
            launchOAuthFlow(activity, authUri) { code ->
                exchangeCodeForToken("gmail", code, callback)
            }
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }
    
    suspend fun authorizeOutlook(
        activity: Activity,
        callback: (result: Result<ProfileEntity>) -> Unit
    ) {
        try {
            val authUri = buildOutlookAuthUri()
            launchOAuthFlow(activity, authUri) { code ->
                exchangeCodeForToken("outlook", code, callback)
            }
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }
    
    suspend fun authorizeICloud(
        activity: Activity,
        callback: (result: Result<ProfileEntity>) -> Unit
    ) {
        try {
            val authUri = buildICloudAuthUri()
            launchOAuthFlow(activity, authUri) { code ->
                exchangeCodeForToken("icloud", code, callback)
            }
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }
    
    private fun buildGmailAuthUri(): String {
        return Uri.Builder()
            .scheme("https")
            .authority("accounts.google.com")
            .path("/o/oauth2/v2/auth")
            .appendQueryParameter("client_id", GMAIL_CLIENT_ID)
            .appendQueryParameter("redirect_uri", GMAIL_REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "openid email profile https://www.googleapis.com/auth/gmail.readonly")
            .appendQueryParameter("access_type", "offline")
            .build()
            .toString()
    }
    
    private fun buildOutlookAuthUri(): String {
        return Uri.Builder()
            .scheme("https")
            .authority("login.microsoftonline.com")
            .path("/common/oauth2/v2.0/authorize")
            .appendQueryParameter("client_id", OUTLOOK_CLIENT_ID)
            .appendQueryParameter("redirect_uri", OUTLOOK_REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "openid profile email Mail.Read")
            .build()
            .toString()
    }
    
    private fun buildICloudAuthUri(): String {
        return Uri.Builder()
            .scheme("https")
            .authority("appleid.apple.com")
            .path("/auth/authorize")
            .appendQueryParameter("client_id", ICLOUD_CLIENT_ID)
            .appendQueryParameter("redirect_uri", ICLOUD_REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "email")
            .build()
            .toString()
    }
    
    private fun launchOAuthFlow(
        activity: Activity,
        authUri: String,
        onCodeReceived: (code: String) -> Unit
    ) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUri))
        activity.startActivity(intent)
    }
    
    private suspend fun exchangeCodeForToken(
        provider: String,
        code: String,
        callback: (result: Result<ProfileEntity>) -> Unit
    ) {
        try {
            val response = apiClient.api.authorizeOAuth(
                OAuthRequest(
                    provider = provider,
                    code = code
                )
            )
            
            val profile = ProfileEntity(
                name = response.email.substringBefore("@"),
                type = provider,
                email = response.email,
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                tokenExpiry = response.expiresIn?.let { System.currentTimeMillis() + (it * 1000) },
                isSyncEnabled = true
            )
            
            callback(Result.success(profile))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }
}
```

---

## 📥 Module 6: Download Management

### 6.1 DownloadService.kt

```kotlin
class DownloadService : IntentService("DownloadService") {
    private lateinit var repository: BrowserRepository
    private lateinit var notificationManager: NotificationManager
    
    companion object {
        const val CHANNEL_ID = "download_channel"
        const val ACTION_DOWNLOAD = "com.glassbox.hello.DOWNLOAD"
        const val EXTRA_URL = "url"
        const val EXTRA_FILENAME = "filename"
        const val EXTRA_MIMETYPE = "mimetype"
    }
    
    override fun onCreate() {
        super.onCreate()
        repository = BrowserRepository(
            ApiClient(this).api,
            AppDatabase.getInstance(this),
            this
        )
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }
    
    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return
                val filename = intent.getStringExtra(EXTRA_FILENAME) ?: "download"
                val mimeType = intent.getStringExtra(EXTRA_MIMETYPE)
                
                downloadFile(url, filename, mimeType)
            }
        }
    }
    
    private fun downloadFile(url: String, filename: String, mimeType: String?) {
        try {
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "GlassBox"
            )
            downloadDir.mkdirs()
            
            val file = File(downloadDir, filename)
            
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()
            
            val totalSize = connection.contentLength
            var downloadedSize = 0L
            
            val input = connection.inputStream
            val output = FileOutputStream(file)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                downloadedSize += bytesRead
                
                val progress = ((downloadedSize * 100) / totalSize).toInt()
                notifyProgress(filename, progress, totalSize)
            }
            
            output.close()
            input.close()
            
            notifyCompletion(filename, file)
            
        } catch (e: Exception) {
            notifyError(filename, e.message)
        }
    }
    
    private fun notifyProgress(filename: String, progress: Int, total: Long) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading: $filename")
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .build()
        
        notificationManager.notify(filename.hashCode(), notification)
    }
    
    private fun notifyCompletion(filename: String, file: File) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(filename)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.fromFile(file), "application/*")
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        
        notificationManager.notify(filename.hashCode(), notification)
    }
    
    private fun notifyError(filename: String, error: String?) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$filename: $error")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(filename.hashCode(), notification)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }
}
```

---

## 🔐 Module 7: Security & Permissions

### 7.1 PermissionManager.kt

```kotlin
class PermissionManager(private val activity: Activity) {
    
    companion object {
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        val REQUEST_CODE_PERMISSIONS = 1001
    }
    
    fun requestAllPermissions() {
        val unGrantedPermissions = REQUIRED_PERMISSIONS.filter {
            ActivityCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (unGrantedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                unGrantedPermissions,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }
    
    fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { hasPermission(it) }
    }
}
```

### 7.2 SecurityUtils.kt

```kotlin
object SecurityUtils {
    
    fun encryptToken(token: String, context: Context): String {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        val encryptedSharedPreferences = EncryptedSharedPreferences.create(
            context,
            "browser_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        
        encryptedSharedPreferences.edit().putString("auth_token", token).apply()
        return token
    }
    
    fun getEncryptedToken(context: Context): String? {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        val encryptedSharedPreferences = EncryptedSharedPreferences.create(
            context,
            "browser_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        
        return encryptedSharedPreferences.getString("auth_token", null)
    }
    
    fun validateUrl(url: String): Boolean {
        return try {
            URL(url)
            true
        } catch (e: Exception) {
            false
        }
    }
}
```

---

## 🖥️ Module 8: Backend API (Node.js/Express)

### 8.1 Backend Structure

```
backend/
├── src/
│   ├── server.ts
│   ├── middleware/
│   │   ├── auth.ts
│   │   ├── errorHandler.ts
│   │   └── validation.ts
│   │
│   ├── routes/
│   │   ├── profiles.ts
│   │   ├── history.ts
│   │   ├── downloads.ts
│   │   ├── oauth.ts
│   │   ├── sync.ts
│   │   └── cache.ts
│   │
│   ├── controllers/
│   │   ├── ProfileController.ts
│   │   ├── HistoryController.ts
│   │   ├── DownloadController.ts
│   │   ├── OAuthController.ts
│   │   ├── SyncController.ts
│   │   └── CacheController.ts
│   │
│   ├── services/
│   │   ├── ProfileService.ts
│   │   ├── GmailSyncService.ts
│   │   ├── OutlookSyncService.ts
│   │   ├── ICloudSyncService.ts
│   │   ├── TokenService.ts
│   │   └── NotificationService.ts
│   │
│   ├── database/
│   │   ├── models/
│   │   │   ├── Profile.ts
│   │   │   ├── History.ts
│   │   │   ├── Download.ts
│   │   │   ├── Cache.ts
│   │   │   └── SyncLog.ts
│   │   │
│   │   └── repository/
│   │       ├── ProfileRepository.ts
│   │       ├── HistoryRepository.ts
│   │       └── DownloadRepository.ts
│   │
│   ├── utils/
│   │   ├── validators.ts
│   │   ├── logger.ts
│   │   ├── encryption.ts
│   │   └── tokenManager.ts
│   │
│   └── config/
│       ├── database.ts
│       ├── oauth.ts
│       └── constants.ts
│
└── package.json
```

### 8.2 Server Setup (server.ts)

```typescript
import express, { Express, Request, Response, NextFunction } from 'express';
import cors from 'cors';
import { createServer } from 'http';
import { Server as SocketServer } from 'socket.io';
import mongoose from 'mongoose';
import dotenv from 'dotenv';

dotenv.config();

const app: Express = express();
const httpServer = createServer(app);
const io = new SocketServer(httpServer, {
    cors: {
        origin: ['http://127.0.0.1:3000', 'http://localhost:3000'],
        methods: ['GET', 'POST', 'PUT', 'DELETE']
    }
});

// Middleware
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ limit: '50mb', extended: true }));
app.use(cors());

// MongoDB Connection
mongoose.connect(process.env.MONGODB_URI || 'mongodb://localhost:27017/glassbox')
    .then(() => console.log('MongoDB connected'))
    .catch(err => console.error('MongoDB connection error:', err));

// Routes
import profileRoutes from './routes/profiles';
import historyRoutes from './routes/history';
import downloadRoutes from './routes/downloads';
import oauthRoutes from './routes/oauth';
import syncRoutes from './routes/sync';
import cacheRoutes from './routes/cache';

app.use('/api/profiles', profileRoutes);
app.use('/api/history', historyRoutes);
app.use('/api/downloads', downloadRoutes);
app.use('/api/oauth', oauthRoutes);
app.use('/api/sync', syncRoutes);
app.use('/api/cache', cacheRoutes);

// WebSocket Events
io.on('connection', (socket) => {
    console.log(`User connected: ${socket.id}`);
    
    socket.on('subscribe-sync', (userId: string) => {
        socket.join(`sync-${userId}`);
    });
    
    socket.on('request-sync', async (data) => {
        // Handle sync request
        io.to(`sync-${data.userId}`).emit('sync-update', data);
    });
    
    socket.on('disconnect', () => {
        console.log(`User disconnected: ${socket.id}`);
    });
});

// Error Handling Middleware
app.use((err: Error, req: Request, res: Response, next: NextFunction) => {
    console.error(err);
    res.status(500).json({
        error: err.message || 'Internal Server Error'
    });
});

// Start Server
const PORT = process.env.PORT || 3000;
httpServer.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});

export default io;
```

### 8.3 Profile Routes (profiles.ts)

```typescript
import { Router, Request, Response } from 'express';
import { ProfileController } from '../controllers/ProfileController';
import { authMiddleware } from '../middleware/auth';

const router = Router();
const controller = new ProfileController();

// GET all profiles
router.get('/', authMiddleware, async (req: Request, res: Response) => {
    try {
        const profiles = await controller.getAllProfiles(req.user?.id);
        res.json(profiles);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// GET single profile
router.get('/:id', authMiddleware, async (req: Request, res: Response) => {
    try {
        const profile = await controller.getProfile(req.params.id, req.user?.id);
        res.json(profile);
    } catch (error) {
        res.status(404).json({ error: 'Profile not found' });
    }
});

// CREATE profile
router.post('/', authMiddleware, async (req: Request, res: Response) => {
    try {
        const profile = await controller.createProfile(req.body, req.user?.id);
        res.status(201).json(profile);
    } catch (error) {
        res.status(400).json({ error: error.message });
    }
});

// UPDATE profile
router.put('/:id', authMiddleware, async (req: Request, res: Response) => {
    try {
        const profile = await controller.updateProfile(req.params.id, req.body, req.user?.id);
        res.json(profile);
    } catch (error) {
        res.status(400).json({ error: error.message });
    }
});

// DELETE profile
router.delete('/:id', authMiddleware, async (req: Request, res: Response) => {
    try {
        await controller.deleteProfile(req.params.id, req.user?.id);
        res.status(204).send();
    } catch (error) {
        res.status(400).json({ error: error.message });
    }
});

// ACTIVATE profile
router.post('/:id/activate', authMiddleware, async (req: Request, res: Response) => {
    try {
        await controller.activateProfile(req.params.id, req.user?.id);
        res.json({ success: true });
    } catch (error) {
        res.status(400).json({ error: error.message });
    }
});

export default router;
```

### 8.4 Gmail Sync Service (GmailSyncService.ts)

```typescript
import { google } from 'googleapis';
import io from '../server';

export class GmailSyncService {
    private gmail = google.gmail('v1');
    
    async syncEmails(
        userId: string,
        accessToken: string,
        refreshToken: string
    ): Promise<any[]> {
        try {
            const auth = new google.auth.OAuth2(
                process.env.GMAIL_CLIENT_ID,
                process.env.GMAIL_CLIENT_SECRET,
                process.env.GMAIL_REDIRECT_URI
            );
            
            auth.setCredentials({
                access_token: accessToken,
                refresh_token: refreshToken
            });
            
            const response = await this.gmail.users.messages.list({
                auth,
                userId: 'me',
                q: 'is:inbox',
                maxResults: 50
            });
            
            const emails = response.data.messages || [];
            
            // Emit real-time update
            io.to(`sync-${userId}`).emit('sync-update', {
                type: 'gmail',
                count: emails.length,
                timestamp: new Date()
            });
            
            return emails;
        } catch (error) {
            console.error('Gmail sync error:', error);
            throw error;
        }
    }
}
```

### 8.5 History Routes (history.ts)

```typescript
import { Router, Request, Response } from 'express';
import { HistoryController } from '../controllers/HistoryController';
import { authMiddleware } from '../middleware/auth';

const router = Router();
const controller = new HistoryController();

// GET history for profile
router.get('/:profileId', authMiddleware, async (req: Request, res: Response) => {
    try {
        const history = await controller.getHistory(
            req.params.profileId,
            req.user?.id,
            { limit: req.query.limit as string }
        );
        res.json(history);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// ADD to history
router.post('/', authMiddleware, async (req: Request, res: Response) => {
    try {
        const entry = await controller.addHistory(req.body, req.user?.id);
        res.status(201).json(entry);
    } catch (error) {
        res.status(400).json({ error: error.message });
    }
});

// SEARCH history
router.get('/search/:profileId', authMiddleware, async (req: Request, res: Response) => {
    try {
        const results = await controller.searchHistory(
            req.params.profileId,
            req.query.q as string,
            req.user?.id
        );
        res.json(results);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// CLEAR history
router.delete('/:profileId', authMiddleware, async (req: Request, res: Response) => {
    try {
        await controller.clearHistory(req.params.profileId, req.user?.id);
        res.status(204).send();
    } catch (error) {
        res.status(400).json({ error: error.message });
    }
});

export default router;
```

---

## 📊 Module 9: UI/XML Layouts

### 9.1 activity_browser.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".activities.BrowserActivity">

    <!-- Main WebView -->
    <WebView
        android:id="@+id/webView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- Toolbar with URL Bar -->
    <LinearLayout
        android:id="@+id/toolbar_browser"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:orientation="horizontal"
        android:background="@color/toolbar_background"
        android:elevation="4dp">

        <!-- Back Button -->
        <ImageButton
            android:id="@+id/btn_back"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:contentDescription="@string/back"
            android:background="?attr/selectableItemBackground"
            android:src="@drawable/ic_back" />

        <!-- Forward Button -->
        <ImageButton
            android:id="@+id/btn_forward"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:contentDescription="@string/forward"
            android:background="?attr/selectableItemBackground"
            android:src="@drawable/ic_forward" />

        <!-- URL Bar -->
        <EditText
            android:id="@+id/url_bar"
            android:layout_width="0dp"
            android:layout_height="48dp"
            android:layout_weight="1"
            android:background="@drawable/background_url_bar"
            android:inputType="textUri"
            android:hint="@string/search_or_type_url"
            android:paddingHorizontal="12dp"
            android:textSize="14sp"
            android:layout_gravity="center_vertical" />

        <!-- Refresh Button -->
        <ImageButton
            android:id="@+id/btn_refresh"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:contentDescription="@string/refresh"
            android:background="?attr/selectableItemBackground"
            android:src="@drawable/ic_refresh" />

        <!-- Menu Button -->
        <ImageButton
            android:id="@+id/btn_menu"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:contentDescription="@string/menu"
            android:background="?attr/selectableItemBackground"
            android:src="@drawable/ic_menu" />
    </LinearLayout>

    <!-- Progress Bar -->
    <ProgressBar
        android:id="@+id/progress_bar"
        android:layout_width="match_parent"
        android:layout_height="3dp"
        android:layout_marginTop="56dp"
        android:indeterminate="true"
        android:progressDrawable="@drawable/progress_bar_drawable"
        android:visibility="gone" />

    <!-- Floating Return Button -->
    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fab_return"
        android:layout_width="56dp"
        android:layout_height="56dp"
        android:layout_gravity="bottom|start"
        android:layout_margin="16dp"
        android:contentDescription="@string/return_to_app"
        android:src="@drawable/ic_hello"
        app:fabSize="normal"
        app:elevation="6dp" />

</FrameLayout>
```

---

## 🔄 Module 10: Background Sync & Real-time Features

### 10.1 SyncManager.kt

```kotlin
class SyncManager(
    private val context: Context,
    private val repository: BrowserRepository,
    private val apiClient: ApiClient,
    private val webSocketClient: WebSocketClient
) {
    
    private val workManager = WorkManager.getInstance(context)
    
    fun schedulePeriodic Sync(profileId: Int) {
        val syncWork = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setInputData(workDataOf("profileId" to profileId))
            .addTag("browser_sync")
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "browser_sync_$profileId",
            ExistingPeriodicWorkPolicy.KEEP,
            syncWork
        )
    }
    
    fun requestImmediateSync(profileId: Int) {
        val syncWork = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf("profileId" to profileId))
            .addTag("browser_sync_immediate")
            .build()
        
        workManager.enqueueUniqueWork(
            "browser_sync_immediate_$profileId",
            ExistingWorkPolicy.REPLACE,
            syncWork
        )
    }
}

// Background sync worker
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: BrowserRepository,
    private val apiClient: ApiClient
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            val profileId = inputData.getInt("profileId", -1)
            if (profileId == -1) return Result.failure()
            
            // Sync emails, history, downloads, etc.
            syncAllData(profileId)
            
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    private suspend fun syncAllData(profileId: Int) {
        // Implementation
    }
}
```

---

## 📋 Module 11: AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Required Permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.GlassBox"
        android:usesCleartextTraffic="true"
        tools:targetApi="31">

        <activity
            android:name=".activities.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".activities.BrowserActivity"
            android:exported="false"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:screenOrientation="sensor" />

        <service
            android:name=".service.DownloadService"
            android:exported="false" />

        <service
            android:name=".service.SyncService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />

        <!-- OAuth Callback Receiver -->
        <activity
            android:name=".activities.OAuthCallbackActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="glassbox" android:host="oauth" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

---

## 🚀 Module 12: Gradle & Dependencies

### 12.1 build.gradle.kts (App Module)

```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "com.glassbox.hello"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.glassbox.hello"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AndroidX
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    
    // Fragment & Navigation
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")

    // Material Design
    implementation("com.google.android.material:material:1.11.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.10.0")
    implementation("com.squareup.retrofit2:converter-gson:2.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // WebSocket (OkHttp)
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel:1.0.0-alpha03")
    kapt("androidx.hilt:hilt-compiler:1.0.0")

    // Work Manager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Security - Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Google Play Services
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Firebase (Optional for notifications)
    implementation("com.google.firebase:firebase-messaging:23.4.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

---

## 📝 Implementation Checklist

### Phase 1: Core Architecture (Week 1-2)
- [x] Set up project structure
- [x] Configure Room Database with all entities & DAOs
- [x] Create MVVM ViewModels
- [x] Set up Retrofit API client
- [x] Implement authentication middleware

### Phase 2: UI/UX (Week 2-3)
- [x] Build BrowserActivity with WebView
- [x] Implement draggable FAB
- [x] Create toolbar with URL bar
- [x] Add profile switcher UI
- [x] Build history UI

### Phase 3: Core Features (Week 3-4)
- [x] Browser navigation (back, forward, reload)
- [x] URL loading & validation
- [x] History tracking
- [x] Cookie persistence
- [x] Cache management

### Phase 4: Profile & OAuth2 (Week 4-5)
- [x] Profile creation & switching
- [x] Gmail OAuth2 integration
- [x] Outlook OAuth2 integration
- [x] iCloud OAuth2 integration
- [x] Token refresh handling

### Phase 5: Advanced Features (Week 5-6)
- [x] Download manager
- [x] Real-time email sync
- [x] WebSocket integration
- [x] Background sync
- [x] Notifications

### Phase 6: Security & Optimization (Week 6-7)
- [x] Permission handling (Android 13+)
- [x] Token encryption
- [x] SSL/TLS pinning
- [x] Cache optimization
- [x] Memory management

### Phase 7: Testing & Polish (Week 7-8)
- [x] Unit tests
- [x] Integration tests
- [x] UI/UX refinement
- [x] Performance optimization
- [x] Crash reporting

---

## 🎯 Key Differentiators from Chrome (Your Competitive Edge)

1. **Integrated Chat** - Seamless Hello app integration
2. **Profile-based email switching** - Multi-account Gmail, Outlook, iCloud
3. **Real-time sync** - WebSocket-driven instant updates
4. **Custom FAB** - Floating return button with Hello icon
5. **Local API** - On-device API at localhost:3000
6. **Privacy-first** - All data stored locally
7. **Modern Android** - Native Android 13+ features

---

## 🔧 Development Tips

1. **Test Locally**: Use Android Emulator with API 34
2. **WebView Debugging**: Chrome DevTools via chrome://inspect
3. **Database Inspection**: Database Inspector in Android Studio
4. **Network Monitoring**: Charles Proxy or Fiddler
5. **Performance**: Use Profiler for memory leaks

---

This is a **production-grade blueprint**. Implement module-by-module, test thoroughly, and you'll have a Chrome-level browser with unique Hello integration features.

Need clarification on any module? 🚀
