package com.cdcvouchers.extraction

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The single extraction implementation, used by exactly two call sites
 * (spec 02 §2.4):
 *  - extractForAdd: one-time hidden WebView at add time (Package 3)
 *  - extractFromVisibleWebView: the visible WebView the user already opened
 *    (Package 4) — never a second WebView.
 *
 * Every extraction is a fresh session: no persisted cookies/cache carried over.
 * Failures report clearly (PARSE_ERROR / NETWORK_ERROR / TIMEOUT) and never
 * throw; cancellation tears down the hidden WebView and orphans nothing.
 */
class ExtractionEngine(
    private val forceFallbackInjection: Boolean = false,
    private val hiddenWebViewFactory: ((Context) -> WebView)? = null,
) : VoucherExtractor {

    override suspend fun extractForAdd(context: Context, url: String): ExtractionResult =
        withContext(Dispatchers.Main) {
            val webView = hiddenWebViewFactory?.invoke(context) ?: createHiddenWebView(context)
            try {
                extract(webView, url)
            } finally {
                runCatching { webView.stopLoading() }
                runCatching { webView.destroy() }
            }
        }

    suspend fun extractFromVisibleWebView(webView: WebView, url: String): ExtractionResult =
        withContext(Dispatchers.Main) {
            try {
                extract(webView, url)
            } finally {
                (injectionPath as? InjectionPath.DocumentStart)?.scriptHandler?.remove()
                runCatching { webView.removeJavascriptInterface(BRIDGE_NAME) }
            }
        }

    private var injectionPath: InjectionPath = InjectionPath.HtmlRewrite

    private suspend fun extract(webView: WebView, url: String): ExtractionResult {
        configureFreshSession(webView)
        val deferred = CompletableDeferred<ExtractionResult>()
        val bridge = RedeemBridge(
            onDataCallback = { payloadJson ->
                val success = VoucherPayloadParser.parse(payloadJson)
                if (success == null) {
                    deferred.complete(ExtractionResult.Failure(ExtractionResult.FailureReason.PARSE_ERROR))
                } else {
                    deferred.complete(success)
                }
            },
            onErrorCallback = { kind ->
                val reason = if (kind == "network") {
                    ExtractionResult.FailureReason.NETWORK_ERROR
                } else {
                    ExtractionResult.FailureReason.PARSE_ERROR
                }
                deferred.complete(ExtractionResult.Failure(reason))
            },
        )
        webView.addJavascriptInterface(bridge, BRIDGE_NAME)
        injectionPath = installInjection(webView, forceFallbackInjection)
        webView.loadUrl(url)
        return try {
            withTimeout(EXTRACTION_TIMEOUT_MS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT)
        }
    }

    private fun createHiddenWebView(context: Context): WebView = WebView(context)

    private fun configureFreshSession(webView: WebView) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        runCatching { webView.clearCache(false) }
    }

    companion object {
        const val EXTRACTION_TIMEOUT_MS = 10_000L
    }
}
