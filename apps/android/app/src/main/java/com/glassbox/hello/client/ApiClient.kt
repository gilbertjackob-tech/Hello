package com.glassbox.hello.client

import android.content.Context
import android.content.SharedPreferences
import com.glassbox.hello.debug.AppLog as Log
import com.glassbox.hello.BuildConfig
import com.glassbox.hello.core.AppConfig
import com.google.gson.Gson
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Retrofit-backed API client for browser data and provider sync networking.
 */
class ApiClient(
    context: Context,
    config: ApiClientConfig = ApiClientConfig(),
    tokenStore: MutableAuthTokenStore = SharedPreferencesAuthTokenStore(context.applicationContext)
) {
    private val applicationContext = context.applicationContext
    private val gson = Gson()
    private val authTokenStore = tokenStore
    private val okHttpClient: OkHttpClient
    private val retrofit: Retrofit

    /**
     * Typed Retrofit API implementation.
     */
    val api: BrowserApi

    init {
        okHttpClient = buildOkHttpClient(applicationContext, config, authTokenStore)
        retrofit = Retrofit.Builder()
            .baseUrl(config.normalizedBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        api = retrofit.create(BrowserApi::class.java)
    }

    /**
     * Saves the bearer token used by future API and WebSocket calls.
     */
    fun saveBearerToken(token: String) {
        authTokenStore.saveBearerToken(token)
    }

    /**
     * Clears the bearer token used by future API and WebSocket calls.
     */
    fun clearBearerToken() {
        authTokenStore.clearBearerToken()
    }

    /**
     * Executes an API call and returns a value-based result.
     */
    suspend fun <T> execute(block: suspend BrowserApi.() -> T): ApiResult<T> {
        return try {
            ApiResult.Success(api.block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val apiError = normalizeError(error)
            Log.e(TAG, "API call failed: ${apiError.message}", error)
            ApiResult.Failure(apiError)
        }
    }

    /**
     * Releases OkHttp resources owned by this client.
     */
    fun close() {
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }

    private fun buildOkHttpClient(
        context: Context,
        config: ApiClientConfig,
        tokenProvider: AuthTokenProvider
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = if (BuildConfig.DEBUG && config.enableHttpLogging) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }

        return OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(config.writeTimeoutMillis, TimeUnit.MILLISECONDS)
            .callTimeout(config.callTimeoutMillis, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .certificatePinner(config.certificatePinning.toCertificatePinner())
            .addInterceptor(AuthInterceptor(context, tokenProvider))
            .addInterceptor(RetryInterceptor(config.retryPolicy))
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private fun normalizeError(error: Exception): ApiError {
        return when (error) {
            is HttpException -> parseHttpException(error)
            is IOException -> ApiError(
                message = error.message ?: "Network request failed.",
                isNetworkError = true,
                isRetriable = true
            )
            else -> ApiError(
                message = error.message ?: "API request failed.",
                isRetriable = false
            )
        }
    }

    private fun parseHttpException(error: HttpException): ApiError {
        val response = error.response()
        val body = try {
            response?.errorBody()?.string()
        } catch (readError: Exception) {
            Log.w(TAG, "Failed to read API error body.", readError)
            null
        }
        val parsed = parseErrorBody(body)
        val httpCode = error.code()
        return ApiError(
            message = parsed?.message
                ?: parsed?.error
                ?: response?.message()
                ?: "HTTP $httpCode",
            httpCode = httpCode,
            code = parsed?.code,
            details = parsed?.details,
            requestId = parsed?.requestId,
            isNetworkError = false,
            isRetriable = httpCode in RETRIABLE_HTTP_CODES
        )
    }

    private fun parseErrorBody(body: String?): ApiErrorResponse? {
        if (body.isNullOrBlank()) return null
        return try {
            gson.fromJson(body, ApiErrorResponse::class.java)
        } catch (parseError: Exception) {
            Log.w(TAG, "Failed to parse API error body.", parseError)
            null
        }
    }

    companion object {
        private const val TAG: String = "ApiClient"
        private val RETRIABLE_HTTP_CODES: Set<Int> = setOf(408, 429, 500, 502, 503, 504)
    }
}

/**
 * API client configuration for URLs, timeouts, retries, logging, and certificate pins.
 */
data class ApiClientConfig(
    val baseUrl: String = AppConfig.HELLO_API_BASE,
    val connectTimeoutMillis: Long = 15_000L,
    val readTimeoutMillis: Long = 30_000L,
    val writeTimeoutMillis: Long = 30_000L,
    val callTimeoutMillis: Long = 60_000L,
    val enableHttpLogging: Boolean = true,
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val certificatePinning: CertificatePinningConfig = CertificatePinningConfig()
) {
    /**
     * Returns a Retrofit-compatible base URL with one trailing slash.
     */
    fun normalizedBaseUrl(): String {
        val cleanBaseUrl = baseUrl.trim()
        require(cleanBaseUrl.startsWith("https://") || BuildConfig.DEBUG) {
            "Production API base URL must use HTTPS."
        }
        return cleanBaseUrl.trimEnd('/') + "/"
    }
}

/**
 * Retry settings for transient network and backend failures.
 */
data class RetryPolicy(
    val maxRetries: Int = 2,
    val initialDelayMillis: Long = 300L,
    val maxDelayMillis: Long = 3_000L,
    val retryUnsafeMethods: Boolean = false
) {
    /**
     * Returns the exponential backoff delay for a retry attempt.
     */
    fun delayForAttempt(attempt: Int): Long {
        val multiplier = 1L shl attempt.coerceAtMost(10)
        return (initialDelayMillis * multiplier).coerceAtMost(maxDelayMillis)
    }
}

/**
 * Certificate pinning configuration applied to OkHttp.
 */
data class CertificatePinningConfig(
    val pins: List<CertificatePin> = emptyList()
) {
    /**
     * Builds an OkHttp certificate pinner.
     */
    fun toCertificatePinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()
        pins.forEach { pin ->
            builder.add(pin.hostname, *pin.sha256Pins.toTypedArray())
        }
        return builder.build()
    }
}

/**
 * SHA-256 certificate pins for one hostname.
 */
data class CertificatePin(
    val hostname: String,
    val sha256Pins: List<String>
)

/**
 * Supplies the current bearer token.
 */
interface AuthTokenProvider {
    /**
     * Returns the current bearer token, or null when signed out.
     */
    fun currentToken(): String?
}

/**
 * Mutable token store used by API and WebSocket clients.
 */
interface MutableAuthTokenStore : AuthTokenProvider {
    /**
     * Saves a bearer token.
     */
    fun saveBearerToken(token: String)

    /**
     * Clears the bearer token.
     */
    fun clearBearerToken()
}

/**
 * SharedPreferences-backed token store until encrypted storage is introduced.
 */
class SharedPreferencesAuthTokenStore(
    context: Context
) : MutableAuthTokenStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the current bearer token, or null when signed out.
     */
    override fun currentToken(): String? {
        return preferences.getString(KEY_BEARER_TOKEN, null)?.takeIf { token -> token.isNotBlank() }
    }

    /**
     * Saves a bearer token.
     */
    override fun saveBearerToken(token: String) {
        require(token.isNotBlank()) { "Bearer token cannot be blank." }
        preferences.edit().putString(KEY_BEARER_TOKEN, token).apply()
    }

    /**
     * Clears the bearer token.
     */
    override fun clearBearerToken() {
        preferences.edit().remove(KEY_BEARER_TOKEN).apply()
    }

    companion object {
        private const val PREFERENCES_NAME: String = "glassbox_browser_auth"
        private const val KEY_BEARER_TOKEN: String = "bearer_token"
    }
}

private class AuthInterceptor(
    private val context: Context,
    private val tokenProvider: AuthTokenProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("Accept", "application/json")
            .header("User-Agent", "GlassBox-Hello-Android/${BuildConfig.VERSION_NAME}")
            .header("X-GlassBox-Package", context.packageName)

        val token = tokenProvider.currentToken()
        if (!token.isNullOrBlank() && original.header("Authorization").isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(builder.build())
    }
}

private class RetryInterceptor(
    private val retryPolicy: RetryPolicy
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var lastException: IOException? = null

        while (attempt <= retryPolicy.maxRetries) {
            try {
                val response = chain.proceed(chain.request())
                if (!shouldRetry(chain.request(), response, attempt)) {
                    return response
                }
                response.close()
                sleepBeforeRetry(response, attempt)
            } catch (error: IOException) {
                lastException = error
                if (!shouldRetry(chain.request(), null, attempt)) {
                    throw error
                }
                sleepBeforeRetry(null, attempt)
            }
            attempt += 1
        }

        throw lastException ?: IOException("Retry attempts exhausted.")
    }

    private fun shouldRetry(request: Request, response: Response?, attempt: Int): Boolean {
        if (attempt >= retryPolicy.maxRetries) return false
        if (!isRetryableMethod(request.method)) return false
        val responseCode = response?.code ?: return true
        return responseCode in RETRIABLE_HTTP_CODES
    }

    private fun isRetryableMethod(method: String): Boolean {
        return retryPolicy.retryUnsafeMethods || method in IDEMPOTENT_METHODS
    }

    private fun sleepBeforeRetry(response: Response?, attempt: Int) {
        val retryAfterMillis = response?.header("Retry-After")?.toLongOrNull()?.times(1_000L)
        val delayMillis = retryAfterMillis ?: retryPolicy.delayForAttempt(attempt)
        try {
            Thread.sleep(delayMillis)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Retry sleep interrupted.").apply {
                initCause(error)
            }
        }
    }

    companion object {
        private val IDEMPOTENT_METHODS: Set<String> = setOf("GET", "HEAD", "PUT", "DELETE", "OPTIONS")
        private val RETRIABLE_HTTP_CODES: Set<Int> = setOf(408, 429, 500, 502, 503, 504)
    }
}
