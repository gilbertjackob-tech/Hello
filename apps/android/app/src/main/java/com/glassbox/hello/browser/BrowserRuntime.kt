package com.glassbox.hello.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID
import kotlin.coroutines.resume

private const val BROWSER_DATA_SUFFIX = "glassbox_browser"
private const val PROFILE_BRIDGE_NAME = "GlassBoxProfileBridge"
private var browserDataSuffixConfigured = false

class BrowserRuntime(
    private val context: Context,
    private val viewModel: BrowserViewModel,
    private val scope: CoroutineScope,
    private val onFileChooserRequest: (String, Boolean, (Array<Uri>?) -> Unit) -> Unit
) {
    private val http = OkHttpClient.Builder().build()
    private val sessions = linkedMapOf<String, BrowserTabSession>()
    private val cookieManager: CookieManager by lazy { CookieManager.getInstance() }

    init {
        ensureBrowserSuffix()
        cookieManager.setAcceptCookie(true)
    }

    fun ensureSession(tab: BrowserTabRecord): BrowserTabSession {
        return sessions.getOrPut(tab.id) { createSession(tab) }
    }

    fun currentSession(tabId: String?): BrowserTabSession? = tabId?.let { sessions[it] }

    fun destroyOtherProfiles(activeProfileId: String) {
        val toRemove = sessions.values.filter { it.profileId != activeProfileId }.map { it.tabId }
        toRemove.forEach { closeSession(it) }
    }

    fun closeSession(tabId: String) {
        sessions.remove(tabId)?.destroy()
    }

    fun closeAll() {
        sessions.values.forEach { it.destroy() }
        sessions.clear()
    }

    fun resetBrowserStorage() {
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    fun restoreProfileCookies(profileId: String) {
        viewModel.state.value.storageByProfile[profileId].orEmpty().forEach { (origin, stored) ->
            setCookies(origin, stored.cookies)
        }
    }

    fun syncProfile(profileId: String) {
        destroyOtherProfiles(profileId)
        resetBrowserStorage()
        restoreProfileCookies(profileId)
    }

    fun loadUrl(tabId: String, rawUrl: String) {
        val session = sessions[tabId] ?: return
        val normalized = normalizeBrowserUrl(rawUrl)
        session.webView.post { session.webView.loadUrl(normalized) }
    }

    fun goBack(tabId: String) {
        sessions[tabId]?.webView?.post {
            if (sessions[tabId]?.webView?.canGoBack() == true) {
                sessions[tabId]?.webView?.goBack()
            }
        }
    }

    fun goForward(tabId: String) {
        sessions[tabId]?.webView?.post {
            if (sessions[tabId]?.webView?.canGoForward() == true) {
                sessions[tabId]?.webView?.goForward()
            }
        }
    }

    fun reload(tabId: String) {
        sessions[tabId]?.webView?.post { sessions[tabId]?.webView?.reload() }
    }

    fun stop(tabId: String) {
        sessions[tabId]?.webView?.post { sessions[tabId]?.webView?.stopLoading() }
    }

    suspend fun query(tabId: String, selector: String): List<BrowserDomNode> {
        val session = sessions[tabId] ?: return emptyList()
        val script = """
            (function() {
              const nodes = Array.from(document.querySelectorAll(${JSONObject.quote(selector)})).slice(0, 200);
              return JSON.stringify(nodes.map((el) => {
                const rect = el.getBoundingClientRect();
                const text = (el.innerText || el.textContent || '').trim().slice(0, 240);
                return {
                  tag: el.tagName.toLowerCase(),
                  text,
                  role: el.getAttribute('role'),
                  id: el.id || null,
                  name: el.getAttribute('name'),
                  placeholder: el.getAttribute('placeholder'),
                  selector: el.id ? '#' + el.id : (el.getAttribute('name') ? el.tagName.toLowerCase() + '[name="' + el.getAttribute('name') + '"]' : el.tagName.toLowerCase()),
                  href: el.href || null,
                  bounds: { x: Math.round(rect.x), y: Math.round(rect.y), width: Math.round(rect.width), height: Math.round(rect.height) }
                };
              }));
            })();
        """.trimIndent()
        return runJsonArrayScript(session.webView, script).mapNotNull { it.toDomNode() }
    }

    suspend fun captureSummary(tabId: String): BrowserPageSummary {
        val session = sessions[tabId] ?: return BrowserPageSummary()
        val script = """
            (function() {
              const headings = Array.from(document.querySelectorAll('h1,h2,h3,h4,h5,h6')).slice(0, 20).map((el) => (el.innerText || el.textContent || '').trim()).filter(Boolean);
              const links = Array.from(document.querySelectorAll('a[href]')).slice(0, 40).map((el) => (el.innerText || el.getAttribute('aria-label') || el.href || '').trim()).filter(Boolean);
              const forms = Array.from(document.querySelectorAll('form')).slice(0, 20).map((el) => el.getAttribute('aria-label') || el.getAttribute('name') || el.getAttribute('id') || el.action || 'form').filter(Boolean);
              const inputs = Array.from(document.querySelectorAll('input,textarea,select')).slice(0, 40).map((el) => el.getAttribute('name') || el.getAttribute('placeholder') || el.getAttribute('aria-label') || el.id || el.type || el.tagName.toLowerCase()).filter(Boolean);
              const buttons = Array.from(document.querySelectorAll('button,input[type="button"],input[type="submit"],[role="button"]')).slice(0, 40).map((el) => el.innerText || el.getAttribute('aria-label') || el.value || el.id || el.tagName.toLowerCase()).filter(Boolean);
              const description = document.querySelector('meta[name="description"]')?.content || document.querySelector('meta[property="og:description"]')?.content || null;
              const text = (document.body ? document.body.innerText : '').trim().slice(0, 4000);
              return JSON.stringify({
                url: location.href,
                title: document.title || location.href,
                description,
                headings,
                links,
                forms,
                inputs,
                buttons,
                text
              });
            })();
        """.trimIndent()
        return runJsonObjectScript(session.webView, script).toPageSummary()
    }

    suspend fun captureDomSnapshot(tabId: String): List<BrowserDomNode> {
        val session = sessions[tabId] ?: return emptyList()
        val script = """
            (function() {
              const candidates = Array.from(document.querySelectorAll('a,button,input,textarea,select,[role],[aria-label],[placeholder]')).slice(0, 150);
              return JSON.stringify(candidates.map((el) => {
                const rect = el.getBoundingClientRect();
                return {
                  tag: el.tagName.toLowerCase(),
                  text: (el.innerText || el.textContent || '').trim().slice(0, 160),
                  role: el.getAttribute('role'),
                  id: el.id || null,
                  name: el.getAttribute('name'),
                  placeholder: el.getAttribute('placeholder'),
                  selector: el.id ? '#' + el.id : el.tagName.toLowerCase(),
                  href: el.href || null,
                  bounds: { x: Math.round(rect.x), y: Math.round(rect.y), width: Math.round(rect.width), height: Math.round(rect.height) }
                };
              }));
            })();
        """.trimIndent()
        return runJsonArrayScript(session.webView, script).mapNotNull { it.toDomNode() }
    }

    suspend fun captureActionTargets(tabId: String): List<BrowserActionTarget> {
        val session = sessions[tabId] ?: return emptyList()
        val script = """
            (function() {
              const nodes = Array.from(document.querySelectorAll('a[href],button,input,textarea,select,[role="button"],[onclick]')).slice(0, 200);
              return JSON.stringify(nodes.map((el) => {
                const rect = el.getBoundingClientRect();
                return {
                  selector: el.id ? '#' + el.id : el.tagName.toLowerCase(),
                  label: el.getAttribute('aria-label') || el.getAttribute('name') || el.getAttribute('placeholder') || el.innerText || el.value || '',
                  role: el.getAttribute('role'),
                  text: (el.innerText || el.textContent || '').trim().slice(0, 120),
                  tag: el.tagName.toLowerCase(),
                  bounds: { x: Math.round(rect.x), y: Math.round(rect.y), width: Math.round(rect.width), height: Math.round(rect.height) }
                };
              }));
            })();
        """.trimIndent()
        return runJsonArrayScript(session.webView, script).mapNotNull { it.toActionTarget() }
    }

    suspend fun captureStorage(tabId: String): BrowserStoredOriginData? {
        val session = sessions[tabId] ?: return null
        val url = session.webView.url.orEmpty()
        val origin = extractOrigin(url) ?: return null
        val script = """
            (function() {
              const read = (storage) => {
                const payload = {};
                for (let i = 0; i < storage.length; i++) {
                  const key = storage.key(i);
                  payload[key] = storage.getItem(key);
                }
                return payload;
              };
              return JSON.stringify({
                localStorage: read(window.localStorage),
                sessionStorage: read(window.sessionStorage)
              });
            })();
        """.trimIndent()
        val storageObject = runJsonObjectScript(session.webView, script)
        val cookies = cookieManager.getCookie(origin)
        return BrowserStoredOriginData(
            cookies = cookies,
            localStorage = storageObject.optJSONObject("localStorage")?.toStringMap().orEmpty(),
            sessionStorage = storageObject.optJSONObject("sessionStorage")?.toStringMap().orEmpty()
        )
    }

    suspend fun request(
        tabId: String,
        method: String,
        url: String,
        headersText: String,
        bodyText: String
    ): String {
        val session = sessions[tabId] ?: return "No active browser tab"
        val targetUrl = normalizeBrowserUrl(url)
        val builder = Request.Builder().url(targetUrl)
        val httpMethod = method.trim().uppercase().ifBlank { "GET" }
        val headerLines = headersText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains(":") }
            .toList()
        headerLines.forEach { line ->
            val index = line.indexOf(':')
            val name = line.substring(0, index).trim()
            val value = line.substring(index + 1).trim()
            if (name.isNotBlank()) builder.addHeader(name, value)
        }
        val cookieHeader = CookieManager.getInstance().getCookie(targetUrl)
        if (!cookieHeader.isNullOrBlank()) {
            builder.addHeader("Cookie", cookieHeader)
        }
        val userAgent = session.webView.settings.userAgentString
        if (!userAgent.isNullOrBlank()) {
            builder.header("User-Agent", userAgent)
        }
        val request = when (httpMethod) {
            "GET" -> builder.get().build()
            "HEAD" -> builder.head().build()
            else -> {
                val mediaType = when {
                    bodyText.trim().startsWith("{") -> "application/json; charset=utf-8"
                    bodyText.trim().startsWith("<") -> "text/html; charset=utf-8"
                    else -> "text/plain; charset=utf-8"
                }.toMediaType()
                builder.method(httpMethod, bodyText.toRequestBody(mediaType)).build()
            }
        }
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val body = response.peekBody(64 * 1024).string()
                buildString {
                    appendLine("Status: ${response.code} ${response.message}")
                    appendLine("URL: ${response.request.url}")
                    appendLine("Headers:")
                    response.headers.toMultimap().forEach { (name, values) ->
                        appendLine("$name: ${values.joinToString(", ")}")
                    }
                    appendLine("Body preview:")
                    append(body)
                }
            }
        }
    }

    suspend fun applyStoredOriginData(tabId: String, url: String) {
        val session = sessions[tabId] ?: return
        val origin = extractOrigin(url) ?: return
        val stored = viewModel.storedOrigin(session.profileId, origin) ?: return
        setCookies(origin, stored.cookies)
        val storage = JSONObject().apply {
            put("localStorage", JSONObject(stored.localStorage))
            put("sessionStorage", JSONObject(stored.sessionStorage))
        }
        val storageJson = storage.toString()
        val script = """
            (function(data) {
              try {
                const local = data.localStorage || {};
                const session = data.sessionStorage || {};
                Object.keys(local).forEach((key) => {
                  const value = local[key];
                  if (value !== null && value !== undefined) {
                    window.localStorage.setItem(key, String(value));
                  }
                });
                Object.keys(session).forEach((key) => {
                  const value = session[key];
                  if (value !== null && value !== undefined) {
                    window.sessionStorage.setItem(key, String(value));
                  }
                });
                return true;
              } catch (e) {
                return false;
              }
            })($storageJson)
        """.trimIndent()
        session.webView.evaluateJavascript(script, null)
    }

    fun captureAndStoreSession(tabId: String) {
        val session = sessions[tabId] ?: return
        scope.launch {
            val stored = captureStorage(tabId) ?: return@launch
            val origin = extractOrigin(session.webView.url.orEmpty()) ?: return@launch
            viewModel.updateStorage(session.profileId, origin, stored.cookies, stored.localStorage, stored.sessionStorage)
        }
    }

    fun createTab(profileId: String, url: String = DEFAULT_BROWSER_HOME_URL): BrowserTabSession {
        val tab = BrowserTabRecord(id = "tab-${UUID.randomUUID().toString().take(8)}", profileId = profileId, url = normalizeBrowserUrl(url))
        return ensureSession(tab)
    }

    private fun createSession(tab: BrowserTabRecord): BrowserTabSession {
        val webView = WebView(context)
        configureWebView(webView)
        webView.addJavascriptInterface(ProfileCaptureBridge(viewModel, tab.profileId), PROFILE_BRIDGE_NAME)
        val session = BrowserTabSession(tabId = tab.id, profileId = tab.profileId, webView = webView)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                val actualUrl = url.orEmpty()
                viewModel.updateTabState(tab.id) { current ->
                    current.copy(
                        url = actualUrl.ifBlank { current.url },
                        isLoading = true,
                        progress = 10,
                        lastError = null
                    )
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val actualUrl = url.orEmpty()
                val title = webView.title.orEmpty()
                viewModel.updateTabState(tab.id) { current ->
                    current.copy(
                        url = actualUrl.ifBlank { current.url },
                        title = title.ifBlank { actualUrl.ifBlank { current.title } },
                        isLoading = false,
                        progress = 100,
                        canGoBack = webView.canGoBack(),
                        canGoForward = webView.canGoForward(),
                        lastError = null
                    )
                }
                if (actualUrl.isNotBlank() && actualUrl != "about:blank") {
                    viewModel.recordHistory(tab.profileId, title.ifBlank { actualUrl }, actualUrl)
                    scope.launch { applyStoredOriginData(tab.id, actualUrl) }
                    captureAndStoreSession(tab.id)
                    injectProfileCaptureBridge(webView)
                    injectPasswordCaptureBridge(webView)
                }
            }

            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    viewModel.updateTabState(tab.id) { current ->
                        current.copy(isLoading = false, lastError = error?.description?.toString() ?: "Load failed")
                    }
                    viewModel.setError(error?.description?.toString() ?: "Page load failed")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                viewModel.updateTabState(tab.id) { current ->
                    current.copy(title = title.orEmpty().ifBlank { current.title })
                }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                viewModel.updateTabState(tab.id) { current ->
                    current.copy(
                        progress = newProgress,
                        isLoading = newProgress in 1..99,
                        canGoBack = webView.canGoBack(),
                        canGoForward = webView.canGoForward()
                    )
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                onFileChooserRequest(
                    fileChooserParams.acceptTypes.filter { it.isNotBlank() }.joinToString(","),
                    fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                ) { uris ->
                    filePathCallback.onReceiveValue(uris)
                }
                return true
            }
        }

        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            val fileName = guessFileName(url, contentDisposition, mimeType)
            viewModel.addDownload(
                BrowserDownloadRecord(
                    id = UUID.randomUUID().toString(),
                    profileId = tab.profileId,
                    fileName = fileName,
                    url = url,
                    mimeType = mimeType,
                    destination = fileName,
                    sizeBytes = if (contentLength > 0) contentLength else null,
                    status = "queued"
                )
            )
            enqueueDownload(url, userAgent, contentDisposition, mimeType, fileName)
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.loadUrl(tab.url)
        return session
    }

    private fun configureWebView(webView: WebView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                offscreenPreRaster = true
            }
        }
    }

    private fun enqueueDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String, fileName: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle(fileName)
                    setDescription("GlassBox browser download")
                    setMimeType(mimeType)
                    setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                    addRequestHeader("User-Agent", userAgent)
                    val cookie = CookieManager.getInstance().getCookie(url)
                    if (!cookie.isNullOrBlank()) {
                        addRequestHeader("Cookie", cookie)
                    }
                    if (!contentDisposition.isNullOrBlank()) {
                        addRequestHeader("Content-Disposition", contentDisposition)
                    }
                    setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                manager.enqueue(request)
            }
        }
    }

    private fun setCookies(origin: String, cookies: String?) {
        if (cookies.isNullOrBlank()) return
        cookies.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { cookie ->
                cookieManager.setCookie(origin, "$cookie; Path=/")
            }
        cookieManager.flush()
    }

    private suspend fun runJsonObjectScript(webView: WebView, script: String): JSONObject {
        val result = evaluateJavascriptAwait(webView, script).orEmpty()
        val cleaned = unwrapJavascriptString(result)
        return if (cleaned.isBlank()) JSONObject() else JSONObject(cleaned)
    }

    private suspend fun runJsonArrayScript(webView: WebView, script: String): List<JSONObject> {
        val result = evaluateJavascriptAwait(webView, script).orEmpty()
        val cleaned = unwrapJavascriptString(result)
        if (cleaned.isBlank()) return emptyList()
        val array = JSONArray(cleaned)
        return buildList {
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { add(it) }
            }
        }
    }

    private suspend fun evaluateJavascriptAwait(webView: WebView, script: String): String? {
        return suspendCancellableCoroutine { continuation ->
            webView.post {
                webView.evaluateJavascript(script) { value ->
                    if (continuation.isActive) continuation.resume(value)
                }
            }
        }
    }

    private fun unwrapJavascriptString(value: String): String {
        if (value == "null" || value == "undefined") return ""
        return runCatching {
            when (val parsed = JSONTokener(value).nextValue()) {
                is String -> parsed
                else -> parsed.toString()
            }
        }.getOrElse { value.trim('"') }
    }

    private fun JSONObject.toPageSummary(): BrowserPageSummary {
        return BrowserPageSummary(
            url = optString("url"),
            title = optString("title"),
            description = optString("description").takeIf { it.isNotBlank() },
            headings = optJSONArray("headings").toStringList(),
            links = optJSONArray("links").toStringList(),
            forms = optJSONArray("forms").toStringList(),
            inputs = optJSONArray("inputs").toStringList(),
            buttons = optJSONArray("buttons").toStringList(),
            text = optString("text").takeIf { it.isNotBlank() }
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val value = optString(i).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun JSONObject.toDomNode(): BrowserDomNode? {
        return BrowserDomNode(
            tag = optString("tag"),
            text = optString("text").takeIf { it.isNotBlank() },
            role = optString("role").takeIf { it.isNotBlank() },
            id = optString("id").takeIf { it.isNotBlank() },
            name = optString("name").takeIf { it.isNotBlank() },
            placeholder = optString("placeholder").takeIf { it.isNotBlank() },
            selector = optString("selector").takeIf { it.isNotBlank() },
            href = optString("href").takeIf { it.isNotBlank() },
            bounds = optJSONObject("bounds")?.toRect()
        )
    }

    private fun JSONObject.toActionTarget(): BrowserActionTarget? {
        return BrowserActionTarget(
            selector = optString("selector"),
            label = optString("label").takeIf { it.isNotBlank() },
            role = optString("role").takeIf { it.isNotBlank() },
            text = optString("text").takeIf { it.isNotBlank() },
            tag = optString("tag").takeIf { it.isNotBlank() },
            bounds = optJSONObject("bounds")?.toRect()
        )
    }

    private fun JSONObject.toRect(): BrowserRect {
        return BrowserRect(
            x = optInt("x"),
            y = optInt("y"),
            width = optInt("width"),
            height = optInt("height")
        )
    }

    private fun JSONObject.toStringMap(): Map<String, String> {
        return keys().asSequence().associateWith { key -> optString(key) }
    }

    private fun guessFileName(url: String, contentDisposition: String, mimeType: String): String {
        val fromDisposition = Regex("filename\\*=UTF-8''([^;]+)|filename=\"?([^\";]+)\"?")
            .find(contentDisposition)
            ?.groupValues
            ?.drop(1)
            ?.firstOrNull { it.isNotBlank() }
            ?.let(Uri::decode)
        val fromUrl = Uri.parse(url).lastPathSegment
        val extension = when {
            mimeType.contains("pdf") -> ".pdf"
            mimeType.startsWith("image/") -> ".${mimeType.substringAfter("/").lowercase()}"
            else -> ""
        }
        return (fromDisposition ?: fromUrl ?: "download-${UUID.randomUUID().toString().take(6)}$extension").replace('/', '_')
    }

    private fun ensureBrowserSuffix() {
        if (browserDataSuffixConfigured) return
        synchronized(BrowserRuntime::class.java) {
            if (browserDataSuffixConfigured) return
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    WebView.setDataDirectorySuffix(BROWSER_DATA_SUFFIX)
                }
            }
            browserDataSuffixConfigured = true
        }
    }

    private fun injectProfileCaptureBridge(webView: WebView) {
        val script = """
            (function() {
              if (window.__glassboxProfileBridgeReady) return;
              window.__glassboxProfileBridgeReady = true;
              const bridge = window.$PROFILE_BRIDGE_NAME;
              if (!bridge || typeof bridge.captureEmail !== 'function') return;
              const avatar = function() {
                const images = Array.prototype.slice.call(document.querySelectorAll('img'));
                const match = images.find(function(img) {
                  const src = String(img.currentSrc || img.src || '');
                  const alt = String(img.alt || img.getAttribute('aria-label') || '').toLowerCase();
                  return src.indexOf('googleusercontent.com') >= 0 ||
                    alt.indexOf('profile') >= 0 ||
                    alt.indexOf('account') >= 0;
                });
                return match ? String(match.currentSrc || match.src || '') : '';
              };
              const send = function(value) {
                if (typeof value !== 'string') return;
                const normalized = value.trim().toLowerCase();
                if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalized)) {
                  if (typeof bridge.captureProfile === 'function') {
                    bridge.captureProfile(normalized, avatar());
                  } else {
                    bridge.captureEmail(normalized);
                  }
                }
              };
              const track = function(root) {
                root.querySelectorAll('input').forEach(function(input) {
                  send(input.value || '');
                  input.addEventListener('input', function() { send(input.value || ''); }, true);
                  input.addEventListener('change', function() { send(input.value || ''); }, true);
                  input.addEventListener('blur', function() { send(input.value || ''); }, true);
                });
              };
              const scan = function(root) {
                root.querySelectorAll('[aria-label],[data-email],[title]').forEach(function(node) {
                  send(node.getAttribute('data-email') || '');
                  send(node.getAttribute('aria-label') || '');
                  send(node.getAttribute('title') || '');
                });
                const text = (document.body && document.body.innerText) ? document.body.innerText : '';
                const match = text.match(/[^\s@]+@[^\s@]+\.[^\s@]+/);
                if (match) send(match[0]);
              };
              track(document);
              scan(document);
              document.addEventListener('submit', function() { track(document); scan(document); }, true);
              setTimeout(function() { scan(document); }, 1000);
              new MutationObserver(function() { scan(document); }).observe(document.documentElement, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['aria-label', 'data-email', 'title', 'src']
              });
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun injectPasswordCaptureBridge(webView: WebView) {
        val script = """
            (function() {
              if (window.__glassboxPasswordBridgeReady) return;
              window.__glassboxPasswordBridgeReady = true;
              const bridge = window.$PROFILE_BRIDGE_NAME;
              if (!bridge || typeof bridge.capturePassword !== 'function') return;
              const visible = function(el) {
                if (!el) return false;
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
              };
              const clean = function(value) {
                return String(value || '').trim();
              };
              const usernameFor = function(passwordInput) {
                const form = passwordInput.form || passwordInput.closest('form') || document;
                const candidates = Array.prototype.slice.call(form.querySelectorAll('input')).filter(function(input) {
                  const type = String(input.type || 'text').toLowerCase();
                  return input !== passwordInput &&
                    visible(input) &&
                    ['email', 'text', 'tel', 'url'].indexOf(type) >= 0 &&
                    clean(input.value).length > 0;
                });
                const emailCandidate = candidates.find(function(input) {
                  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(clean(input.value).toLowerCase());
                });
                return clean((emailCandidate || candidates[candidates.length - 1] || {}).value);
              };
              const capture = function(passwordInput) {
                const password = clean(passwordInput && passwordInput.value);
                if (password.length < 4) return;
                const username = usernameFor(passwordInput);
                if (!username) return;
                bridge.capturePassword(String(location.origin || location.href), username, password);
              };
              const watch = function(root) {
                Array.prototype.slice.call(root.querySelectorAll('input[type="password"]')).forEach(function(input) {
                  if (input.__glassboxPasswordWatched) return;
                  input.__glassboxPasswordWatched = true;
                  input.addEventListener('change', function() { capture(input); }, true);
                  input.addEventListener('blur', function() { capture(input); }, true);
                  const form = input.form || input.closest('form');
                  if (form && !form.__glassboxPasswordWatched) {
                    form.__glassboxPasswordWatched = true;
                    form.addEventListener('submit', function() { capture(input); }, true);
                  }
                });
              };
              watch(document);
              new MutationObserver(function() { watch(document); }).observe(document.documentElement, {
                childList: true,
                subtree: true
              });
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun extractOrigin(url: String): String? {
        return runCatching {
            val uri = Uri.parse(url)
            val scheme = uri.scheme ?: return null
            val host = uri.host ?: return null
            "$scheme://$host"
        }.getOrNull()
    }
}

private class ProfileCaptureBridge(
    private val viewModel: BrowserViewModel,
    private val profileId: String
) {
    @JavascriptInterface
    fun captureEmail(email: String?) {
        val value = email?.trim().orEmpty()
        if (isEmailAddress(value)) {
            viewModel.bindDetectedEmailToActiveProfile(value)
        }
    }

    @JavascriptInterface
    fun captureProfile(email: String?, avatarUrl: String?) {
        val value = email?.trim().orEmpty()
        if (isEmailAddress(value)) {
            viewModel.bindDetectedProfileToActiveProfile(value, avatarUrl)
        }
    }

    @JavascriptInterface
    fun capturePassword(origin: String?, username: String?, password: String?) {
        viewModel.offerDetectedPassword(
            profileId = profileId,
            origin = origin?.trim().orEmpty(),
            username = username?.trim().orEmpty(),
            password = password.orEmpty()
        )
    }
}

class BrowserTabSession(
    val tabId: String,
    val profileId: String,
    val webView: WebView
) {
    fun destroy() {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.destroy()
    }
}
