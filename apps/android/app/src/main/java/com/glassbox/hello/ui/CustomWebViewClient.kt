package com.glassbox.hello.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import com.glassbox.hello.debug.AppLog as Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.glassbox.hello.utils.BrowserUtils
import com.glassbox.hello.viewmodel.BrowserViewModel

/**
 * WebViewClient that records browser history, favicon URLs, and navigation errors.
 */
class CustomWebViewClient(
    private val context: Context,
    private val viewModel: BrowserViewModel,
    private val callbacks: BrowserPageCallbacks = BrowserPageCallbacks()
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val scheme = uri.scheme.orEmpty().lowercase()
        if (scheme in setOf("http", "https", "about", "file", "data")) {
            return false
        }

        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (error: Exception) {
            Log.w(TAG, "No activity can handle URL: $uri", error)
            callbacks.onError("No app can open this link.")
            true
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        val pageUrl = url.orEmpty()
        callbacks.onPageStarted(pageUrl)
        if (pageUrl.isNotBlank() && pageUrl != "about:blank") {
            viewModel.navigateToUrl(pageUrl)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        val pageUrl = url.orEmpty()
        val title = view?.title?.takeIf { value -> value.isNotBlank() }
        val faviconUrl = extractFaviconUrl(pageUrl)
        callbacks.onPageFinished(pageUrl, title, faviconUrl)
        if (pageUrl.isNotBlank() && pageUrl != "about:blank") {
            viewModel.finishNavigation(title = title, faviconUrl = faviconUrl)
        }
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) {
            val message = error?.description?.toString() ?: "Page load failed."
            viewModel.clearStatusMessage()
            callbacks.onError(message)
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        if (request?.isForMainFrame == true) {
            val statusCode = errorResponse?.statusCode ?: return
            val message = "HTTP $statusCode while loading ${request.url.host.orEmpty()}."
            callbacks.onError(message)
        }
    }

    private fun extractFaviconUrl(url: String): String? {
        val origin = BrowserUtils.extractOrigin(url) ?: return null
        return "$origin/favicon.ico"
    }

    companion object {
        private const val TAG: String = "CustomWebViewClient"
    }
}

/**
 * UI callbacks emitted by [CustomWebViewClient].
 */
data class BrowserPageCallbacks(
    val onPageStarted: (String) -> Unit = {},
    val onPageFinished: (url: String, title: String?, faviconUrl: String?) -> Unit = { _, _, _ -> },
    val onError: (String) -> Unit = {}
)
