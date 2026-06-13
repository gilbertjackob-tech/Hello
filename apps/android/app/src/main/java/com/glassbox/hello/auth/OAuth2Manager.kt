package com.glassbox.hello.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.glassbox.hello.debug.AppLog as Log
import com.glassbox.hello.client.ApiClient
import com.glassbox.hello.client.OAuthRequest
import com.glassbox.hello.database.entities.ProfileEntity
import com.glassbox.hello.security.SecureDataStore
import com.glassbox.hello.utils.Constants
import com.google.gson.Gson
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.cancellation.CancellationException

/**
 * OAuth2 authorization manager for Gmail, Outlook, and iCloud browser profiles.
 */
class OAuth2Manager(
    private val secureDataStore: SecureDataStore,
    private val apiClient: ApiClient,
    private val config: OAuth2Config = OAuth2Config()
) {
    private val gson = Gson()
    private val random = SecureRandom()

    /**
     * Starts the Gmail OAuth flow.
     */
    fun authorizeGmail(activity: Activity): Result<OAuthLaunchResult> {
        return startAuthorization(activity, OAuthProvider.Gmail)
    }

    /**
     * Starts the Outlook OAuth flow.
     */
    fun authorizeOutlook(activity: Activity): Result<OAuthLaunchResult> {
        return startAuthorization(activity, OAuthProvider.Outlook)
    }

    /**
     * Starts the iCloud OAuth flow.
     */
    fun authorizeICloud(activity: Activity): Result<OAuthLaunchResult> {
        return startAuthorization(activity, OAuthProvider.ICloud)
    }

    /**
     * Handles a glassbox://oauth callback and returns a local profile.
     */
    suspend fun handleOAuthCallback(callbackUri: Uri): Result<ProfileEntity> {
        return try {
            val provider = callbackUri.pathSegments.firstOrNull()
                ?: return Result.failure(IllegalArgumentException("Missing OAuth provider."))
            val code = callbackUri.getQueryParameter("code")
                ?: return Result.failure(IllegalArgumentException("Missing authorization code."))
            val state = callbackUri.getQueryParameter("state")
                ?: return Result.failure(IllegalArgumentException("Missing OAuth state."))
            val error = callbackUri.getQueryParameter("error")
            if (!error.isNullOrBlank()) {
                return Result.failure(IllegalStateException("OAuth provider returned error: $error."))
            }

            val session = loadSession(state)
                ?: return Result.failure(SecurityException("OAuth state is invalid or expired."))
            if (session.provider != provider) {
                deleteSession(state)
                return Result.failure(SecurityException("OAuth provider mismatch."))
            }

            val response = apiClient.api.authorizeOAuth(
                OAuthRequest(
                    provider = provider,
                    code = code,
                    redirectUri = session.redirectUri,
                    codeVerifier = session.codeVerifier,
                    state = state
                )
            )
            deleteSession(state)

            val now = System.currentTimeMillis()
            val profile = ProfileEntity.create(
                name = response.email.substringBefore("@").ifBlank { provider },
                type = provider,
                email = response.email,
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                tokenExpiry = response.expiresIn?.let { seconds -> now + seconds * 1000L },
                isActive = true,
                isSyncEnabled = true,
                now = now
            )
            Result.success(profile)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "OAuth callback handling failed.", error)
            Result.failure(error)
        }
    }

    private fun startAuthorization(activity: Activity, provider: OAuthProvider): Result<OAuthLaunchResult> {
        return try {
            val clientId = config.clientId(provider)
            require(clientId.isNotBlank()) { "OAuth client id for ${provider.id} is not configured." }

            val state = secureRandomString(STATE_BYTES)
            val verifier = secureRandomString(CODE_VERIFIER_BYTES)
            val challenge = codeChallenge(verifier)
            val redirectUri = config.redirectUri(provider)
            saveSession(
                OAuthSession(
                    provider = provider.id,
                    state = state,
                    codeVerifier = verifier,
                    redirectUri = redirectUri,
                    createdAt = System.currentTimeMillis()
                )
            )

            val authUri = buildAuthorizationUri(
                provider = provider,
                clientId = clientId,
                redirectUri = redirectUri,
                state = state,
                codeChallenge = challenge
            )
            activity.startActivity(Intent(Intent.ACTION_VIEW, authUri))
            Result.success(OAuthLaunchResult(provider = provider.id, state = state, authorizationUri = authUri))
        } catch (error: Exception) {
            Log.e(TAG, "OAuth launch failed for ${provider.id}.", error)
            Result.failure(error)
        }
    }

    private fun buildAuthorizationUri(
        provider: OAuthProvider,
        clientId: String,
        redirectUri: String,
        state: String,
        codeChallenge: String
    ): Uri {
        val builder = Uri.Builder()
            .scheme("https")
            .authority(provider.authority)
            .path(provider.path)
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", provider.scope)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")

        provider.extraParameters.forEach { (name, value) ->
            builder.appendQueryParameter(name, value)
        }

        return builder.build()
    }

    private fun saveSession(session: OAuthSession) {
        secureDataStore.saveString(sessionKey(session.state), gson.toJson(session))
    }

    private fun loadSession(state: String): OAuthSession? {
        val raw = secureDataStore.getString(sessionKey(state)) ?: return null
        return runCatching { gson.fromJson(raw, OAuthSession::class.java) }.getOrNull()
            ?.takeIf { session -> System.currentTimeMillis() - session.createdAt <= SESSION_TTL_MILLIS }
    }

    private fun deleteSession(state: String) {
        secureDataStore.deleteToken(sessionKey(state))
    }

    private fun sessionKey(state: String): String = "oauth_session_$state"

    private fun secureRandomString(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        private const val TAG: String = "OAuth2Manager"
        private const val STATE_BYTES: Int = 32
        private const val CODE_VERIFIER_BYTES: Int = 64
        private const val SESSION_TTL_MILLIS: Long = 10 * 60 * 1000L
    }
}

/**
 * OAuth provider configuration values.
 */
data class OAuth2Config(
    val gmailClientId: String = Constants.GMAIL_CLIENT_ID,
    val outlookClientId: String = Constants.OUTLOOK_CLIENT_ID,
    val iCloudClientId: String = Constants.ICLOUD_CLIENT_ID,
    val gmailRedirectUri: String = Constants.GMAIL_REDIRECT_URI,
    val outlookRedirectUri: String = Constants.OUTLOOK_REDIRECT_URI,
    val iCloudRedirectUri: String = Constants.ICLOUD_REDIRECT_URI
) {
    /**
     * Returns the configured client id for a provider.
     */
    fun clientId(provider: OAuthProvider): String {
        return when (provider) {
            OAuthProvider.Gmail -> gmailClientId
            OAuthProvider.Outlook -> outlookClientId
            OAuthProvider.ICloud -> iCloudClientId
        }
    }

    /**
     * Returns the configured redirect URI for a provider.
     */
    fun redirectUri(provider: OAuthProvider): String {
        return when (provider) {
            OAuthProvider.Gmail -> gmailRedirectUri
            OAuthProvider.Outlook -> outlookRedirectUri
            OAuthProvider.ICloud -> iCloudRedirectUri
        }
    }
}

/**
 * OAuth provider metadata.
 */
enum class OAuthProvider(
    val id: String,
    val authority: String,
    val path: String,
    val scope: String,
    val extraParameters: Map<String, String> = emptyMap()
) {
    Gmail(
        id = Constants.PROVIDER_GMAIL,
        authority = "accounts.google.com",
        path = "/o/oauth2/v2/auth",
        scope = "openid email profile https://www.googleapis.com/auth/gmail.readonly",
        extraParameters = mapOf("access_type" to "offline", "prompt" to "consent")
    ),
    Outlook(
        id = Constants.PROVIDER_OUTLOOK,
        authority = "login.microsoftonline.com",
        path = "/common/oauth2/v2.0/authorize",
        scope = "openid profile email Mail.Read offline_access",
        extraParameters = mapOf("response_mode" to "query")
    ),
    ICloud(
        id = Constants.PROVIDER_ICLOUD,
        authority = "appleid.apple.com",
        path = "/auth/authorize",
        scope = "email name",
        extraParameters = mapOf("response_mode" to "query")
    )
}

/**
 * Result returned after launching a provider authorization page.
 */
data class OAuthLaunchResult(
    val provider: String,
    val state: String,
    val authorizationUri: Uri
)

private data class OAuthSession(
    val provider: String,
    val state: String,
    val codeVerifier: String,
    val redirectUri: String,
    val createdAt: Long
)
