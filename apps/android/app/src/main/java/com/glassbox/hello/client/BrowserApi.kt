package com.glassbox.hello.client

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API surface for browser profile, history, download, OAuth, and sync operations.
 */
interface BrowserApi {
    /**
     * Returns every browser profile known to the backend account.
     */
    @GET("profiles")
    suspend fun getProfiles(): List<ProfileResponse>

    /**
     * Creates a browser profile.
     */
    @POST("profiles")
    suspend fun createProfile(@Body profile: ProfileRequest): ProfileResponse

    /**
     * Updates a browser profile.
     */
    @PUT("profiles/{id}")
    suspend fun updateProfile(
        @Path("id") id: Int,
        @Body profile: ProfileRequest
    ): ProfileResponse

    /**
     * Deletes a browser profile.
     */
    @DELETE("profiles/{id}")
    suspend fun deleteProfile(@Path("id") id: Int)

    /**
     * Marks a browser profile as the active profile.
     */
    @POST("profiles/{id}/activate")
    suspend fun activateProfile(@Path("id") id: Int): ProfileResponse

    /**
     * Exchanges an OAuth authorization code for profile tokens.
     */
    @POST("oauth/authorize")
    suspend fun authorizeOAuth(@Body request: OAuthRequest): OAuthResponse

    /**
     * Refreshes an OAuth access token.
     */
    @POST("oauth/refresh")
    suspend fun refreshToken(@Body request: TokenRefreshRequest): TokenResponse

    /**
     * Returns browsing history for a profile.
     */
    @GET("history/{profileId}")
    suspend fun getHistory(
        @Path("profileId") profileId: Int,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): List<HistoryResponse>

    /**
     * Searches browsing history for a profile.
     */
    @GET("history/{profileId}/search")
    suspend fun searchHistory(
        @Path("profileId") profileId: Int,
        @Query("q") query: String,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): List<HistoryResponse>

    /**
     * Adds or updates one browsing history entry.
     */
    @POST("history")
    suspend fun addHistory(@Body history: HistoryRequest): HistoryResponse

    /**
     * Deletes one browsing history entry.
     */
    @DELETE("history/{id}")
    suspend fun deleteHistoryItem(@Path("id") id: Int)

    /**
     * Clears all browsing history for a profile.
     */
    @DELETE("history/profile/{profileId}")
    suspend fun clearHistory(@Path("profileId") profileId: Int): BatchMutationResponse

    /**
     * Returns persisted downloads for a profile.
     */
    @GET("downloads/{profileId}")
    suspend fun getDownloads(
        @Path("profileId") profileId: Int,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): List<DownloadResponse>

    /**
     * Starts or enqueues a download.
     */
    @POST("downloads")
    suspend fun startDownload(@Body request: DownloadRequest): DownloadResponse

    /**
     * Updates persisted download progress and status.
     */
    @PUT("downloads/{id}")
    suspend fun updateDownload(
        @Path("id") id: Int,
        @Body update: DownloadUpdateRequest
    ): DownloadResponse

    /**
     * Deletes one download entry.
     */
    @DELETE("downloads/{id}")
    suspend fun deleteDownload(@Path("id") id: Int)

    /**
     * Synchronizes Gmail browser data for a profile.
     */
    @POST("sync/gmail")
    suspend fun syncGmail(@Body request: SyncRequest): SyncResponse

    /**
     * Synchronizes Outlook browser data for a profile.
     */
    @POST("sync/outlook")
    suspend fun syncOutlook(@Body request: SyncRequest): SyncResponse

    /**
     * Synchronizes iCloud browser data for a profile.
     */
    @POST("sync/icloud")
    suspend fun syncICloud(@Body request: SyncRequest): SyncResponse

    /**
     * Checks backend availability.
     */
    @GET("health")
    suspend fun health(): ApiStatusResponse
}

/**
 * Request body used to create or update browser profiles.
 */
data class ProfileRequest(
    val name: String,
    val type: String,
    val email: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenExpiry: Long? = null,
    val userAgent: String? = null,
    val isActive: Boolean = false,
    val isSyncEnabled: Boolean = true
)

/**
 * Browser profile returned by the API.
 */
data class ProfileResponse(
    val id: Int,
    val name: String,
    val type: String,
    val email: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenExpiry: Long? = null,
    val userAgent: String? = null,
    val isActive: Boolean = false,
    val isSyncEnabled: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastSyncTime: Long? = null
)

/**
 * OAuth authorization-code exchange request.
 */
data class OAuthRequest(
    val provider: String,
    val code: String,
    val redirectUri: String? = null,
    val codeVerifier: String? = null,
    val state: String? = null
)

/**
 * OAuth authorization-code exchange response.
 */
data class OAuthResponse(
    val provider: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: Long? = null,
    val tokenType: String = "Bearer",
    val profile: ProfileResponse? = null
)

/**
 * OAuth refresh-token request.
 */
data class TokenRefreshRequest(
    val provider: String,
    val refreshToken: String,
    val profileId: Int? = null
)

/**
 * OAuth refresh-token response.
 */
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: Long? = null,
    val tokenType: String = "Bearer"
)

/**
 * Request body used to add a history entry.
 */
data class HistoryRequest(
    val profileId: Int,
    val url: String,
    val title: String? = null,
    val faviconUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val visitCount: Int = 1,
    val lastVisited: Long = timestamp,
    val isBookmarked: Boolean = false
)

/**
 * History entry returned by the API.
 */
data class HistoryResponse(
    val id: Int,
    val profileId: Int,
    val url: String,
    val title: String? = null,
    val faviconUrl: String? = null,
    val timestamp: Long = 0L,
    val visitCount: Int = 1,
    val lastVisited: Long = 0L,
    val isBookmarked: Boolean = false
)

/**
 * Request body used to start or enqueue a download.
 */
data class DownloadRequest(
    val profileId: Int,
    val url: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long = -1L,
    val mimeType: String? = null,
    val isAutoDownload: Boolean = false
)

/**
 * Request body used to update download state.
 */
data class DownloadUpdateRequest(
    val downloadedSize: Long? = null,
    val fileSize: Long? = null,
    val status: String? = null,
    val progress: Int? = null,
    val endTime: Long? = null,
    val mimeType: String? = null
)

/**
 * Download entry returned by the API.
 */
data class DownloadResponse(
    val id: Int,
    val profileId: Int,
    val url: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long = -1L,
    val downloadedSize: Long = 0L,
    val status: String,
    val progress: Int = 0,
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val mimeType: String? = null,
    val isAutoDownload: Boolean = false
)

/**
 * Request body used for provider sync.
 */
data class SyncRequest(
    val profileId: Int,
    val provider: String,
    val since: Long? = null,
    val forceFullSync: Boolean = false,
    val limit: Int? = null
)

/**
 * Provider sync response.
 */
data class SyncResponse(
    val profileId: Int,
    val provider: String,
    val status: String,
    val itemCount: Int = 0,
    val startedAt: Long = 0L,
    val completedAt: Long? = null,
    val nextSyncAt: Long? = null,
    val error: String? = null
)

/**
 * Generic mutation response for batch operations.
 */
data class BatchMutationResponse(
    val affectedRows: Int = 0,
    val status: String = "ok"
)

/**
 * Generic status response.
 */
data class ApiStatusResponse(
    val status: String,
    val message: String? = null,
    val timestamp: Long? = null
)

/**
 * Error response body returned by the backend.
 */
data class ApiErrorResponse(
    val error: String? = null,
    val message: String? = null,
    val code: String? = null,
    val details: String? = null,
    val requestId: String? = null,
    val timestamp: Long? = null
)

/**
 * Normalized client error exposed to repositories and view models.
 */
data class ApiError(
    val message: String,
    val httpCode: Int? = null,
    val code: String? = null,
    val details: String? = null,
    val requestId: String? = null,
    val isNetworkError: Boolean = false,
    val isRetriable: Boolean = false
)

/**
 * Result wrapper for API calls that need explicit error values instead of exceptions.
 */
sealed class ApiResult<out T> {
    /**
     * Successful API call result.
     */
    data class Success<T>(val value: T) : ApiResult<T>()

    /**
     * Failed API call result.
     */
    data class Failure(val error: ApiError) : ApiResult<Nothing>()
}
