# Android Browser Implementation - PRODUCTION CODE GENERATION

You have these reference documents in the project:
1. Android_Browser_Complete_Implementation.md
2. Advanced_Architecture_And_API_Patterns.md
3. UI_UX_Implementation_Guide.md
4. QUICK_START_GUIDE.md

## TASK: Generate production-ready Android Kotlin code for the GlassBox Browser

### REQUIREMENTS - NON-NEGOTIABLE
- NO pseudo-code or comments explaining what should be done
- ONLY complete, copy-paste ready, production-grade code
- All imports must be explicit (no wildcards)
- All error handling must be comprehensive (try-catch with proper logging)
- All code must follow Kotlin best practices and Google Android style guide
- Memory management and leak prevention required
- No deprecated APIs - use AndroidX equivalents only
- Proper null safety with Kotlin's null-coalescing operators

### PHASE 1: FOUNDATION (Database Layer)

Generate the following complete files:

1. **app/src/main/java/com/glassbox/hello/database/entities/ProfileEntity.kt**
   - From: Android_Browser_Complete_Implementation.md → Module 2.1
   - Requirements: Complete with all annotations and properties
   - Add: validation methods

2. **app/src/main/java/com/glassbox/hello/database/entities/HistoryEntity.kt**
   - From: Android_Browser_Complete_Implementation.md → Module 2.1
   - Add: timestamp validation, URL normalization

3. **app/src/main/java/com/glassbox/hello/database/entities/DownloadEntity.kt**
   - From: Android_Browser_Complete_Implementation.md → Module 2.1
   - Add: progress calculation methods, status validation

4. **app/src/main/java/com/glassbox/hello/database/entities/CacheEntity.kt**
   - From: Android_Browser_Complete_Implementation.md → Module 2.1
   - Add: expiry checking methods, size validation

5. **app/src/main/java/com/glassbox/hello/database/entities/CookieEntity.kt**
   - From: Android_Browser_Complete_Implementation.md → Module 2.1
   - Add: domain parsing, cookie flag handling

6. **app/src/main/java/com/glassbox/hello/database/dao/ProfileDao.kt**
   - From: Android_Browser_Complete_Implementation.md → Module 2.2
   - Complete with all query methods
   - Add error handling for concurrent updates

7. **app/src/main/java/com/glassbox/hello/database/dao/HistoryDao.kt**
   - From: Android_Browser_Complete_Implementation.md → Module 2.2
   - Add pagination support
   - Add bulk operations for performance

8. **app/src/main/java/com/glassbox/hello/database/dao/DownloadDao.kt**
   - From: Android_Browser_Complete_Implementation.md → Module 2.2
   - Complete with status queries
   - Add progress update batch operations

9. **app/src/main/java/com/glassbox/hello/database/dao/CookieDao.kt**
   - From: Android_Browser_Complete_Implementation.md → Module 2.2
   - Complete with domain-based queries

10. **app/src/main/java/com/glassbox/hello/database/AppDatabase.kt**
    - From: Android_Browser_Complete_Implementation.md → Module 2.3
    - Include all entities
    - Add migration strategies
    - Singleton pattern with thread safety

### PHASE 2: REPOSITORY LAYER

Generate the following complete files:

11. **app/src/main/java/com/glassbox/hello/repository/BrowserRepository.kt**
    - From: Android_Browser_Complete_Implementation.md → Module 3.1
    - Complete profile, history, cookie, cache management
    - Add: transaction handling for atomic operations
    - Add: proper exception propagation

12. **app/src/main/java/com/glassbox/hello/repository/HistoryRepository.kt**
    - Extracted from BrowserRepository, focused on history
    - Add: Full-text search implementation
    - Add: Pagination support
    - Add: Bookmark management

13. **app/src/main/java/com/glassbox/hello/repository/ProfileRepository.kt**
    - Extracted from BrowserRepository, focused on profiles
    - Add: Profile validation
    - Add: Atomic profile switching (deactivate old, activate new)

14. **app/src/main/java/com/glassbox/hello/repository/DownloadRepository.kt**
    - Focused on download management
    - Add: Download queue management
    - Add: Status state machine

### PHASE 3: VIEWMODEL & BUSINESS LOGIC

Generate the following complete files:

15. **app/src/main/java/com/glassbox/hello/viewmodel/BrowserViewModel.kt**
    - From: Android_Browser_Complete_Implementation.md → Module 3.2
    - Extend with all methods shown
    - Add: StateFlow for reactive UI updates
    - Add: Error handling with proper UX feedback
    - Add: Coroutine scope management

16. **app/src/main/java/com/glassbox/hello/viewmodel/ProfileViewModel.kt**
    - New: Profile management ViewModel
    - Methods: switchProfile(), createProfile(), deleteProfile(), getProfiles()
    - Add: Real-time profile list with Flow
    - Add: Active profile tracking

17. **app/src/main/java/com/glassbox/hello/viewmodel/DownloadViewModel.kt**
    - New: Download management ViewModel
    - Methods: startDownload(), pauseDownload(), resumeDownload(), cancelDownload()
    - Add: Download progress tracking with Flow
    - Add: Batch operations support

18. **app/src/main/java/com/glassbox/hello/viewmodel/HistoryViewModel.kt**
    - New: History management ViewModel
    - Methods: getHistory(), searchHistory(), deleteHistory(), bookmarkUrl()
    - Add: Pagination support
    - Add: Search with debouncing

### PHASE 4: API CLIENT & NETWORKING

Generate the following complete files:

19. **app/src/main/java/com/glassbox/hello/client/ApiClient.kt**
    - From: Android_Browser_Complete_Implementation.md → Module 4.1
    - Complete Retrofit setup
    - Include: Logging interceptor, Auth interceptor, Retry logic
    - Add: Certificate pinning
    - Add: Request/response timeout configuration

20. **app/src/main/java/com/glassbox/hello/client/BrowserApi.kt**
    - From: Android_Browser_Complete_Implementation.md → Module 4.1
    - Complete interface with all endpoints
    - Add: Request/Response data classes
    - Add: Error handling models

21. **app/src/main/java/com/glassbox/hello/client/WebSocketClient.kt**
    - From: Android_Browser_Complete_Implementation.md → Module 4.2
    - Complete WebSocket implementation
    - Add: Auto-reconnection logic
    - Add: Message queuing for offline support
    - Add: Heartbeat/keepalive mechanism

### PHASE 5: AUTHENTICATION & SECURITY

Generate the following complete files:

22. **app/src/main/java/com/glassbox/hello/auth/OAuth2Manager.kt**
    - From: Android_Browser_Complete_Implementation.md → Module 5.1
    - Complete Gmail, Outlook, iCloud OAuth flows
    - Add: PKCE support for security
    - Add: State parameter validation

23. **app/src/main/java/com/glassbox/hello/auth/TokenManager.kt**
    - From: Android_Browser_Complete_Implementation.md → Advanced Module 7
    - Complete token refresh logic
    - Add: Token expiry buffer (5 minutes)
    - Add: Automatic token refresh on API call

24. **app/src/main/java/com/glassbox/hello/security/SecureDataStore.kt**
    - From: Advanced_Architecture_And_API_Patterns.md
    - Encrypted SharedPreferences using Tink
    - Methods: saveToken(), getToken(), deleteToken()
    - Add: Key rotation support

25. **app/src/main/java/com/glassbox/hello/security/SecurityUtils.kt**
    - From: Android_Browser_Complete_Implementation.md → Module 7.2
    - Complete: URL validation, SSL utilities
    - Add: Certificate pinning configuration
    - Add: Header validation utilities

### PHASE 6: SERVICES

Generate the following complete files:

26. **app/src/main/java/com/glassbox/hello/service/DownloadService.kt**
    - From: Android_Browser_Complete_Implementation.md → Module 6.1
    - Complete download with progress tracking
    - Add: Pause/resume support
    - Add: Background execution with foreground service
    - Add: Download notification system

27. **app/src/main/java/com/glassbox/hello/service/SyncService.kt**
    - New: Background sync service
    - Methods: syncProfiles(), syncHistory(), syncEmails()
    - Add: WorkManager integration
    - Add: Sync failure retry logic

28. **app/src/main/java/com/glassbox/hello/service/SyncWorker.kt**
    - From: Advanced_Architecture_And_API_Patterns.md
    - Background work with WorkManager
    - Add: Exponential backoff on failure
    - Add: Periodic sync scheduling

### PHASE 7: ACTIVITIES & UI

Generate the following complete files:

29. **app/src/main/java/com/glassbox/hello/activities/BrowserActivity.kt**
    - From: Android_Browser_Complete_Implementation.md → Module 1.3
    - Complete implementation with WebView setup
    - Add: Full-screen immersive mode
    - Add: WebViewClient & WebChromeClient implementations
    - Add: Proper lifecycle management

30. **app/src/main/java/com/glassbox/hello/ui/DraggableFloatingButton.kt**
    - From: UI_UX_Implementation_Guide.md → Draggable FAB
    - Complete touch handling and drag logic
    - Add: Snap-to-edge animation
    - Add: Position persistence to SharedPreferences

31. **app/src/main/java/com/glassbox/hello/ui/CustomWebViewClient.kt**
    - New: Custom WebViewClient implementation
    - Methods: shouldOverrideUrlLoading(), onPageStarted(), onPageFinished()
    - Add: History recording on page load
    - Add: Favicon extraction
    - Add: Error handling for failed loads

### PHASE 8: LAYOUTS (XML)

Generate the following complete files:

32. **res/layout/activity_browser.xml**
    - From: UI_UX_Implementation_Guide.md
    - Complete with WebView, toolbar, progress bar, FAB
    - Implement Material Design 3
    - All dimensions from dimens.xml

33. **res/layout/component_url_bar.xml**
    - From: UI_UX_Implementation_Guide.md
    - Material Design TextInputLayout with icon and clear button

34. **res/layout/bottom_sheet_browser_menu.xml**
    - From: UI_UX_Implementation_Guide.md
    - Grid layout with 4 columns, 2 rows
    - All 8 menu items with icons and labels

35. **res/layout/item_profile.xml**
    - From: UI_UX_Implementation_Guide.md
    - MaterialCardView with avatar, name, email, active indicator

36. **res/values/colors.xml**
    - From: UI_UX_Implementation_Guide.md → Colors section
    - Complete Material Design 3 color palette
    - All status colors (error, success, warning)

37. **res/values/themes.xml**
    - From: UI_UX_Implementation_Guide.md → Design System
    - Material3.Dark theme with all color attributes
    - Typography styles

38. **res/values/strings.xml**
    - From: UI_UX_Implementation_Guide.md → Accessibility
    - All user-facing strings (English)
    - Accessibility descriptions

39. **res/values/dimens.xml**
    - New: All dimension constants
    - Standard sizes for buttons, margins, padding
    - Typography sizes

### PHASE 9: UTILITIES

Generate the following complete files:

40. **app/src/main/java/com/glassbox/hello/utils/PermissionManager.kt**
    - From: Android_Browser_Complete_Implementation.md → Module 7.1
    - Complete permission request handling
    - Add: Runtime permission checking
    - Add: Permission denial handling

41. **app/src/main/java/com/glassbox/hello/utils/BrowserUtils.kt**
    - New: Utility functions for browser operations
    - Methods: normalizeUrl(), extractDomain(), isValidUrl()
    - Add: User agent management

42. **app/src/main/java/com/glassbox/hello/utils/FileUtils.kt**
    - New: File handling utilities
    - Methods: getDownloadDirectory(), getMimeType(), getFileSize()
    - Add: File size formatting

43. **app/src/main/java/com/glassbox/hello/utils/Constants.kt**
    - New: All constants in one place
    - API endpoints, timeout values, cache sizes, etc.

### PHASE 10: CONFIGURATION

Generate the following complete files:

44. **AndroidManifest.xml**
    - From: Android_Browser_Complete_Implementation.md → Module 11
    - All required permissions
    - All activities and services
    - OAuth callback intent-filter

45. **build.gradle.kts (app module)**
    - From: Android_Browser_Complete_Implementation.md → Module 12
    - All dependencies with exact versions
    - Build types (debug/release)
    - Signing configuration placeholder

### GENERATION RULES

1. **Code Style**
   - Follow Google Android Kotlin Style Guide
   - 4-space indentation
   - Proper naming: camelCase for variables/functions, PascalCase for classes
   - All public functions should have KDoc comments

2. **Error Handling**
   - Every API call wrapped in try-catch
   - Proper logging using Log.e, Log.w, Log.d
   - User-friendly error messages in UI
   - No silent failures

3. **Coroutines**
   - All network calls use suspend functions
   - Proper scope management (viewModelScope, lifecycleScope)
   - Cancellation on view destruction
   - Timeout handling

4. **Memory Management**
   - No memory leaks from listeners/callbacks
   - Proper WebView cleanup in onDestroy()
   - Database connection pooling
   - Image caching with size limits

5. **Testing-Ready**
   - Dependency injection with Hilt
   - Repository pattern for testability
   - No direct activity dependencies in ViewModels
   - Interface-based APIs

6. **Security**
   - Never log sensitive data (tokens, passwords)
   - Token encryption before storage
   - Input validation on all user data
   - HTTPS only for production
   - SQL injection prevention (use Room queries)

7. **Performance**
   - Database queries use proper indexes
   - Network calls cached when appropriate
   - Background work with WorkManager
   - Main thread never blocked

### OUTPUT INSTRUCTIONS

- Generate one complete file at a time
- Include ALL necessary imports
- Include complete class implementations (no TODOs)
- Include proper initialization (init blocks, companion objects)
- Format with proper indentation and spacing
- Include brief explanation of complex sections (NOT the implementation instructions)
- Ensure files are immediately usable (no missing dependencies or method stubs)

### START WITH

Generate PHASE 1 files first (1-10):
- Start with ProfileEntity.kt
- Then HistoryEntity.kt
- Continue through CookieEntity.kt
- Then all DAOs (6-9)
- Finally AppDatabase.kt

After I confirm Phase 1 is complete and tested, request Phase 2.

---

use local android to store cache and data. i mean they stores on their mobile those login and other profile cache and data
