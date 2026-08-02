package com.cdcvouchers.extraction

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The single extraction implementation, used by exactly two call sites
 * (spec 02 §2.4):
 *  - extractForAdd: one-time hidden WebView at add time (Package 3) — fully
 *    fresh: cache and cookies wiped.
 *  - extractFromVisibleWebView: the visible WebView the user already opened
 *    (Package 4) — browser-like: HTTP cache and cookies persist across opens
 *    (spec 02 §2.4 revision 2026-08-02). Churning fresh sessions per open
 *    tripped the operator's rate limiting on api-cdc.redeem.gov.sg, leaving
 *    the makeup view blank for minutes; one persistent session behaves like a
 *    normal returning browser and avoids it. Balance payloads still come from
 *    live API responses, so extraction is unaffected.
 *
 * Failures report clearly (PARSE_ERROR / NETWORK_ERROR / TIMEOUT) and never
 * throw; cancellation tears down the hidden WebView and orphans nothing.
 *
 * All teardown (script handler removal + bridge removal) happens inside
 * `extract()`'s `finally` — owned per-call, never shared state. The engine is
 * an app-wide singleton used by both the add flow and the detail screen, and
 * two overlapping extractions must never touch each other's handlers.
 */
class ExtractionEngine(
    private val forceFallbackInjection: Boolean = false,
    private val hiddenWebViewFactory: ((Context) -> WebView)? = null,
    /**
     * API 24–25 have no WebView#getWebViewClient getter, so on those versions
     * a pre-existing client (e.g. an asset-loader client under test) cannot be
     * read back for chaining in the HtmlRewrite fallback. Production never
     * sets a client before injection, so this is null in production and used
     * only by tests that must keep their interception working.
     */
    private val fallbackInjectionDelegate: WebViewClient? = null,
) : VoucherExtractor {

    override suspend fun extractForAdd(context: Context, url: String): ExtractionResult =
        withContext(Dispatchers.Main) {
            val webView = hiddenWebViewFactory?.invoke(context) ?: createHiddenWebView(context)
            try {
                extract(webView, url, browserLike = false)
            } finally {
                runCatching { webView.stopLoading() }
                runCatching { webView.destroy() }
            }
        }

    suspend fun extractFromVisibleWebView(webView: WebView, url: String): ExtractionResult =
        withContext(Dispatchers.Main) {
            extract(webView, url, browserLike = true)
        }

    /**
     * Rotation rehydrate: a recreated visible WebView is re-driven without
     * re-extracting. Injection is (re)installed because the viewport-fix script
     * must run on every page load — without it, affected WebView builds render
     * the SPA blank after rotation (02 §2.4). The capture wrapper is inert
     * without a bridge installed (it checks window.RedeemBridge and no-ops).
     * The document-start handler is intentionally NOT removed: it must live for
     * the page lifetime. HtmlRewrite fallback gets a fresh client per WebView.
     */
    suspend fun rehydrateVisibleWebView(webView: WebView, url: String) {
        withContext(Dispatchers.Main) {
            configureSession(webView, browserLike = true)
            installInjection(webView, forceFallbackInjection, fallbackInjectionDelegate)
            webView.loadUrl(url)
        }
    }

    private suspend fun extract(webView: WebView, url: String, browserLike: Boolean): ExtractionResult {
        configureSession(webView, browserLike = browserLike)
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
        val path = installInjection(webView, forceFallbackInjection, fallbackInjectionDelegate)
        webView.loadUrl(url)
        return try {
            withTimeout(EXTRACTION_TIMEOUT_MS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT)
        } finally {
            (path as? InjectionPath.DocumentStart)?.let { p ->
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    p.scriptHandler.remove()
                }
            }
            runCatching { webView.removeJavascriptInterface(BRIDGE_NAME) }
        }
    }

    private fun createHiddenWebView(context: Context): WebView = WebView(context)

    private fun configureSession(webView: WebView, browserLike: Boolean) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Browser-like visible loads reuse the on-disk HTTP cache so repeat
            // opens skip re-downloading the content-hashed bundle (spec 02 §2.4
            // revision). Add-time loads stay LOAD_NO_CACHE.
            cacheMode = if (browserLike) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_NO_CACHE
        }
        if (browserLike) {
            // The visible WebView keeps one persistent session (cookies + cache)
            // like a normal browser. Creating a fresh anonymous session per open
            // tripped the operator's rate limiting and hung the makeup view.
            return
        }
        // Add-time validation fetch: fully fresh — no cookies, no cache carried
        // over, since this one-shot hidden load has no prior session anyway.
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        runCatching { webView.clearCache(false) }
    }

    companion object {
        const val EXTRACTION_TIMEOUT_MS = 10_000L
    }
}
