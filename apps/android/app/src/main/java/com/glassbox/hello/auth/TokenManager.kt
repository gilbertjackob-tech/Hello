package com.glassbox.hello.auth

import com.glassbox.hello.debug.AppLog as Log
import com.glassbox.hello.client.ApiClient
import com.glassbox.hello.security.SecureDataStore
import com.glassbox.hello.utils.Constants
import kotlin.coroutines.cancellation.CancellationException

/**
 * Manages OAuth token storage, expiry checks, and refresh.
 */
class TokenManager(
    private val secureDataStore: SecureDataStore,
    private val apiClient: ApiClient,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * Saves a provider token set.
     */
    fun saveTokenSet(
        provider: String,
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long?,
        profileId: Int? = null
    ) {
        val normalizedProvider = normalizeProvider(provider)
        val expiryTime = expiresInSeconds?.let { seconds -> clock() + seconds * 1000L }
        secureDataStore.saveToken(accessTokenKey(normalizedProvider, profileId), accessToken)
        if (!refreshToken.isNullOrBlank()) {
            secureDataStore.saveToken(refreshTokenKey(normalizedProvider, profileId), refreshToken)
        }
        if (expiryTime != null) {
            secureDataStore.saveLong(expiryKey(normalizedProvider, profileId), expiryTime)
        }
    }

    /**
     * Returns a valid access token, refreshing it when needed.
     */
    suspend fun getValidAccessToken(provider: String, profileId: Int? = null): String? {
        val normalizedProvider = normalizeProvider(provider)
        val accessToken = secureDataStore.getToken(accessTokenKey(normalizedProvider, profileId))
        if (!accessToken.isNullOrBlank() && !isTokenExpired(normalizedProvider, profileId)) {
            return accessToken
        }
        return refreshAccessToken(normalizedProvider, profileId)
    }

    /**
     * Refreshes an access token using the stored refresh token.
     */
    suspend fun refreshAccessToken(provider: String, profileId: Int? = null): String? {
        val normalizedProvider = normalizeProvider(provider)
        val refreshToken = secureDataStore.getToken(refreshTokenKey(normalizedProvider, profileId))
            ?: return null

        return try {
            val response = apiClient.api.refreshToken(
                com.glassbox.hello.client.TokenRefreshRequest(
                    provider = normalizedProvider,
                    refreshToken = refreshToken,
                    profileId = profileId
                )
            )
            saveTokenSet(
                provider = normalizedProvider,
                accessToken = response.accessToken,
                refreshToken = response.refreshToken ?: refreshToken,
                expiresInSeconds = response.expiresIn,
                profileId = profileId
            )
            response.accessToken
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Token refresh failed for provider $normalizedProvider.", error)
            null
        }
    }

    /**
     * Executes a block after installing a fresh bearer token into the API client.
     */
    suspend fun <T> withFreshToken(provider: String, profileId: Int? = null, block: suspend () -> T): T {
        val token = getValidAccessToken(provider, profileId)
            ?: throw IllegalStateException("No valid access token for $provider.")
        apiClient.saveBearerToken(token)
        return block()
    }

    /**
     * Returns true when the token is missing or inside the expiry buffer.
     */
    fun isTokenExpired(provider: String, profileId: Int? = null): Boolean {
        val normalizedProvider = normalizeProvider(provider)
        val expiryTime = secureDataStore.getLong(expiryKey(normalizedProvider, profileId), 0L)
        return expiryTime <= 0L || clock() + Constants.TOKEN_EXPIRY_BUFFER_MILLIS >= expiryTime
    }

    /**
     * Clears tokens for one provider/profile pair.
     */
    fun clearTokens(provider: String, profileId: Int? = null) {
        val normalizedProvider = normalizeProvider(provider)
        secureDataStore.deleteToken(accessTokenKey(normalizedProvider, profileId))
        secureDataStore.deleteToken(refreshTokenKey(normalizedProvider, profileId))
        secureDataStore.deleteToken(expiryKey(normalizedProvider, profileId))
    }

    private fun normalizeProvider(provider: String): String {
        val value = provider.trim().lowercase()
        require(value in SUPPORTED_PROVIDERS) { "Unsupported OAuth provider: $provider." }
        return value
    }

    private fun accessTokenKey(provider: String, profileId: Int?): String = key(provider, profileId, "access_token")

    private fun refreshTokenKey(provider: String, profileId: Int?): String = key(provider, profileId, "refresh_token")

    private fun expiryKey(provider: String, profileId: Int?): String = key(provider, profileId, "expiry_time")

    private fun key(provider: String, profileId: Int?, suffix: String): String {
        return if (profileId == null) {
            "${provider}_$suffix"
        } else {
            "${provider}_${profileId}_$suffix"
        }
    }

    companion object {
        private const val TAG: String = "TokenManager"
        private val SUPPORTED_PROVIDERS: Set<String> = setOf(
            Constants.PROVIDER_GMAIL,
            Constants.PROVIDER_OUTLOOK,
            Constants.PROVIDER_ICLOUD
        )
    }
}
