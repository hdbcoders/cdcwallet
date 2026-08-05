package com.cdcvouchers.extraction

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
 *  - extractForAdd: one-time hidden WebView at add time (Package 3) — a
 *    fresh instance that loads network-fresh (LOAD_NO_CACHE) but shares the
 *    persistent process session, since Android's cookie store is process-wide
 *    (wiping it would destroy the visible session; see 2026-08-02 revision).
 *  - extractFromVisibleWebView: the visible WebView the user already opened
 *    (Package 4) — browser-like: HTTP cache and cookies persist across opens
 *    (spec 02 §2.4 revision 2026-08-02). Churning fresh sessions per open
 *    tripped the operator's rate limiting on api-cdc.redeem.gov.sg, leaving
 *    the makeup view blank for minutes; one persistent session behaves like a
 *    normal returning browser and avoids it. Balance payloads still come from
 *    live API responses, so extraction is unaffected.
 *
 * Since revision 2026-08-03 the visible WebView is itself **long-lived**
 * (spec 02 §2.4/§2.7): one instance created warm at app start
 * ([warmUp]/[acquireVisibleWebView]), reused for every tap, never destroyed by
 * the UI — the screen only detaches it. Each tap is still a fresh document on
 * that instance (unconditional reload + fresh wrapper + fresh bridge), so data
 * is always fresh; only the engine/process/DNS machinery is reused, which is
 * what makes taps feel like a phone browser.
 *
 * Reuse requires **epoch gating**: a previous page's document can still
 * deliver or fail after the next tap has installed its bridge (the bridge
 * name is remapped, so a late callback would land in the new bridge). Two
 * layers close that window: `stopLoading()` first aborts the superseded load's
 * in-flight requests, and a per-load [WebViewClient] opens a [LoadGate] on the
 * new load's first `onPageStarted` — callbacks arriving while the gate is
 * closed (i.e. before the new page actually started) are dropped. Impossible
 * with fresh instances, mandatory with reuse.
 *
 * Failures report clearly (PARSE_ERROR / NETWORK_ERROR / TIMEOUT) and never
 * throw; cancellation tears down the hidden WebView and orphans nothing.
 *
 * All teardown (script handler removal + bridge removal + client restore)
 * happens inside `extract()`'s `finally` — owned per-call, never shared state.
 * The engine is an app-wide singleton used by both the add flow and the detail
 * screen, and two overlapping extractions must never touch each other's
 * handlers.
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

    /**
     * The base client of the long-lived visible WebView: the caller-supplied
     * delegate (tests) or a plain no-op client. The per-load epoch client
     * wraps whatever client the view has at extraction time, so interception
     * and epoch gating work together on every API level and injection path.
     */
    private val visibleClient: WebViewClient = fallbackInjectionDelegate ?: WebViewClient()

    /** The one long-lived visible WebView (spec 02 §2.4 revision 2026-08-03). */
    private var visibleWebView: WebView? = null

    /**
     * Warm-up: creates the long-lived visible WebView without loading anything
     * (no network traffic), so engine initialization, renderer, and DNS are
     * already warm by the time the user taps a voucher. Safe to call from
     * Application.onCreate; failures degrade to the normal first-tap creation.
     */
    fun warmUp(context: Context) {
        runCatching { acquireVisibleWebView(context) }
    }

    /**
     * Returns the single long-lived visible WebView, creating it once with the
     * browser-like session (persistent cache + cookies) and the base client.
     * Detaches it from any previous view hierarchy so the caller can attach
     * it. Never creates a second instance.
     */
    fun acquireVisibleWebView(context: Context): WebView {
        val existing = visibleWebView
        if (existing != null) {
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        return WebView(context).also { view ->
            visibleWebView = view
            configureSession(view, browserLike = true)
            view.webViewClient = visibleClient
        }
    }

    override suspend fun extractForAdd(context: Context, url: String): ExtractionResult =
        withContext(Dispatchers.Main) {
            val webView = hiddenWebViewFactory?.invoke(context) ?: createHiddenWebView(context)
            try {
                extract(webView, url, browserLike = false, gate = null)
            } finally {
                runCatching { webView.stopLoading() }
                runCatching { webView.destroy() }
            }
        }

    suspend fun extractFromVisibleWebView(webView: WebView, url: String): ExtractionResult =
        withContext(Dispatchers.Main) {
            val gate = LoadGate()
            // Per-load epoch client: wraps whatever client the view currently
            // has (the engine's base client, a previous HtmlRewritingClient, or
            // a test's asset-loader client) and opens the gate on the first
            // page start of THIS load. On API 24–25 the view's client cannot
            // be read back, so the engine-owned view uses its known base client
            // and any other view (tests) falls back to the explicit delegate.
            val previousClient = if (Build.VERSION.SDK_INT >= 26) {
                webView.webViewClient
            } else if (webView === visibleWebView) {
                visibleClient
            } else {
                fallbackInjectionDelegate
            }
            webView.webViewClient = EpochGateClient(previousClient, gate)
            try {
                extract(webView, url, browserLike = true, gate = gate)
            } finally {
                webView.webViewClient = previousClient ?: WebViewClient()
            }
        }

    private suspend fun extract(
        webView: WebView,
        url: String,
        browserLike: Boolean,
        gate: LoadGate?,
    ): ExtractionResult {
        configureSession(webView, browserLike = browserLike)
        val deferred = CompletableDeferred<ExtractionResult>()
        if (gate != null) {
            // Reuse hygiene: the superseded load (which may still be in flight —
            // loads survive screen exit) is aborted by this tap. Aborting it
            // here also closes the stale-page delivery window the epoch gate
            // guards (spec 02 §2.7 revision 2026-08-03).
            webView.stopLoading()
        }
        val bridge = RedeemBridge(
            onDataCallback = { payloadJson ->
                // Epoch gate: ignore payloads from a previous page's document.
                // They can only arrive before this load's navigation commits,
                // i.e. while the gate is still closed.
                if (gate != null && !gate.isOpen) return@RedeemBridge
                val success = VoucherPayloadParser.parse(payloadJson)
                if (success == null) {
                    deferred.complete(ExtractionResult.Failure(ExtractionResult.FailureReason.PARSE_ERROR))
                } else {
                    deferred.complete(success)
                }
            },
            onErrorCallback = { kind ->
                if (gate != null && !gate.isOpen) return@RedeemBridge
                val reason = if (kind == "network") {
                    ExtractionResult.FailureReason.NETWORK_ERROR
                } else {
                    ExtractionResult.FailureReason.PARSE_ERROR
                }
                deferred.complete(ExtractionResult.Failure(reason))
            },
        )
        webView.addJavascriptInterface(bridge, BRIDGE_NAME)
        val path = installInjection(
            webView,
            forceFallbackInjection,
            if (browserLike) visibleClient else fallbackInjectionDelegate,
        )
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
        // The visible WebView keeps one persistent session (cookies + cache)
        // like a normal browser. Creating a fresh anonymous session per open
        // tripped the operator's rate limiting and hung the makeup view.
        // Add-time shares that same persistent session: Android's CookieManager
        // is process-wide, so wiping it here for a "fresh" add-time load would
        // destroy the visible session on every add — recreating exactly the
        // churn this revision (2026-08-02) was written to prevent. The fresh
        // hidden instance still loads network-fresh for its own request via
        // LOAD_NO_CACHE; it just does not wipe the shared session.
    }

    /**
     * Per-load epoch gate (spec 02 §2.7 revision 2026-08-03): opens [gate] on
     * the first `onPageStarted` of the load it wraps, delegating everything
     * else to the previous client so existing interception (HtmlRewrite
     * fallback, test asset loaders) keeps working. Installed around each
     * visible load and restored in `extractFromVisibleWebView`'s `finally`.
     */
    private class EpochGateClient(
        private val delegate: WebViewClient?,
        private val gate: LoadGate,
    ) : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            gate.open()
            delegate?.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView, url: String) {
            delegate?.onPageFinished(view, url)
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = delegate?.shouldInterceptRequest(view, request)

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean = delegate?.shouldOverrideUrlLoading(view, request) ?: false

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: android.webkit.WebResourceError,
        ) {
            delegate?.onReceivedError(view, request, error)
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            delegate?.onReceivedHttpError(view, request, errorResponse)
        }
    }

    companion object {
        const val EXTRACTION_TIMEOUT_MS = 10_000L
    }
}
