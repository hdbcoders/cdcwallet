package com.cdcwallet.extraction

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewFeature
import com.cdcwallet.data.token.VoucherToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The single extraction implementation, used by exactly two call sites
 * (spec 02 §2.4):
 *  - extractForAdd: one-time hidden WebView at add time (Package 3) - a
 *    fresh instance that loads network-fresh (LOAD_NO_CACHE) but shares the
 *    persistent process session, since Android's cookie store is process-wide
 *    (wiping it would destroy the visible session; see 2026-08-02 revision).
 *  - extractFromVisibleWebView: the visible WebView the user already opened
 *    (Package 4) - browser-like: HTTP cache and cookies persist across opens
 *    (spec 02 §2.4 revision 2026-08-02).
 *
 * Since revision 2026-08-03 the visible WebView is itself **long-lived**
 * (spec 02 §2.4/§2.7): one instance created warm at app start
 * ([warmUp]/[acquireVisibleWebView]), reused for every tap, never destroyed by
 * the UI - the screen only detaches it. Each tap is still a fresh document on
 * that instance (unconditional reload + fresh wrapper + fresh bridge).
 *
 * **Per-load isolation (refactor H1/H3).** Every load gets a unique bridge
 * name (monotonic generation) that is also the bridge-call nonce:
 *  - a previous page's late callback looks up its own name, which its load's
 *    `finally` already removed - it can never reach the next load's bridge,
 *    and one load's teardown can never remove another load's bridge;
 *  - an unrelated document cannot forge a payload: the nonce is only known to
 *    the injected wrapper of the same load;
 *  - the capture script additionally requires the exact API host and the
 *    expected voucher token, and activates only on the allowed page origin
 *    (checked inside the script - see InjectionScript), so a payload from a
 *    different voucher (or any other page/host) is not even captured (H3).
 * The coordinator additionally serializes visible extractions (latest-tap-wins
 * cancels and joins the previous load before the next one starts), so the
 * per-load client slot is never clobbered by a superseded load's `finally`.
 *
 * Failures report clearly (PARSE_ERROR / NETWORK_ERROR / TIMEOUT) and never
 * throw: the whole setup/load/await/teardown lifecycle sits inside one
 * try/finally (H6). Coroutine cancellation is re-thrown (the caller owns it)
 * after stopping the visible load; timeouts do NOT stop the visible load,
 * because the page the user is looking at must keep loading even when the
 * extraction timed out (spec 02 §2.7: never block the user's WebView - the
 * operator's throttling already taught the app to let slow pages finish).
 *
 * When no injection path exists at all (legacy WebView without
 * document-start scripts AND no delegate response to rewrite - H5), the page
 * still loads normally but extraction fails fast instead of burning the
 * timeout.
 */
class ExtractionEngine(
    private val forceFallbackInjection: Boolean = false,
    private val hiddenWebViewFactory: ((Context) -> WebView)? = null,
    /**
     * API 24–25 have no WebView#getWebViewClient getter, so on those versions
     * a pre-existing client (e.g. an asset-loader client under test) cannot be
     * read back for chaining. Production never sets a client before injection,
     * so this is null in production and used only by tests that must keep
     * their interception working.
     */
    private val fallbackInjectionDelegate: WebViewClient? = null,
    /**
     * Per-extraction timeout (spec 02 §2.7 fail-soft). Production keeps the
     * 10s default; tests that must observe a successful extraction pass a
     * larger budget so slow emulator load doesn't abort them.
     */
    private val extractionTimeoutMs: Long = EXTRACTION_TIMEOUT_MS,
    /**
     * The only page origin the capture wrapper may activate on (refactor H3).
     * The document-start injection itself uses the `*` rule set because exact
     * origin rule sets do not run on Google WebView 150 (verified empirically
     * on WebView 150.0.7871.181); the script enforces this origin itself and
     * bails out everywhere else. Production: the official voucher page only.
     * Test fixtures run on a different origin (WebViewAssetLoader) and pass it
     * through this seam - never through the production policy.
     */
    private val allowedPageOrigin: String = DEFAULT_ALLOWED_PAGE_ORIGIN,
    /**
     * The exact API host whose `/vouchers/groups/{token}` responses the
     * capture script may deliver (refactor H3). Production: the real API host.
     * Test fixtures serve synthetic responses from the asset-loader host and
     * pass it through this seam.
     */
    private val targetApiHost: String = DEFAULT_TARGET_API_HOST,
) : VoucherExtractor {

    /**
     * The base client of the long-lived visible WebView: the caller-supplied
     * delegate (tests) or a plain no-op client. The per-load extraction
     * client wraps whatever client the view has at extraction time, so
     * interception and error handling work together on every API level and
     * injection path.
     */
    private val visibleClient: WebViewClient = fallbackInjectionDelegate ?: WebViewClient()

    /** The one long-lived visible WebView (spec 02 §2.4 revision 2026-08-03). */
    private var visibleWebView: WebView? = null

    /**
     * Monotonic per-load generation. Every load (hidden or visible) installs
     * its bridge under a unique name (`RedeemBridge$generation`), so a load's
     * teardown can only ever remove its own bridge. All call sites enter on
     * [Dispatchers.Main], so this needs no locking.
     */
    private var generationCounter = 0

    private fun nextGeneration(): Int = ++generationCounter

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
            // Explicit MATCH_PARENT is required: without it the page's viewport
            // units (100vh/100%) resolve to 0 and the RedeemSG loading screen
            // collapses to the top of the page instead of centering (the
            // diagnostic WebView set MATCH_PARENT explicitly and rendered
            // correctly). Fixed 2026-08-07.
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            configureSession(view, browserLike = true)
            view.webViewClient = visibleClient
        }
    }

    override suspend fun extractForAdd(context: Context, url: String): ExtractionResult =
        withContext(Dispatchers.Main) {
            val webView = hiddenWebViewFactory?.invoke(context) ?: createHiddenWebView(context)
            try {
                val deferred = CompletableDeferred<ExtractionResult>()
                val bridgeName = "RedeemBridge${nextGeneration()}"
                val nonce = newNonce()
                // Main-frame network failures complete the extraction result
                // (H6) instead of silently timing out. On API 26+ the view's
                // existing client (asset loader, etc.) is chained; on API 24-25
                // it cannot be read back, so the explicit delegate is used when
                // present and the view's own client is left untouched otherwise
                // (legacy path keeps working, fail-soft timeout applies).
                val originalClient = if (Build.VERSION.SDK_INT >= 26) {
                    webView.webViewClient
                } else {
                    null
                }
                if (Build.VERSION.SDK_INT >= 26) {
                    webView.webViewClient = ExtractionWebViewClient(originalClient, deferred)
                } else if (fallbackInjectionDelegate != null) {
                    webView.webViewClient = ExtractionWebViewClient(fallbackInjectionDelegate, deferred)
                }
                extract(
                    webView = webView,
                    url = url,
                    browserLike = false,
                    bridgeName = bridgeName,
                    nonce = nonce,
                    deferred = deferred,
                )
            } finally {
                runCatching { webView.stopLoading() }
                runCatching { webView.destroy() }
            }
        }

    suspend fun extractFromVisibleWebView(webView: WebView, url: String): ExtractionResult =
        withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<ExtractionResult>()
            val bridgeName = "RedeemBridge${nextGeneration()}"
            val nonce = newNonce()
            // Per-load extraction client: wraps whatever client the view
            // currently has (the engine's base client, a previous extraction
            // client, or a test's asset-loader client) so interception and
            // main-frame error handling keep working. On API 24–25 the view's
            // client cannot be read back, so the engine-owned view uses its
            // known base client and any other view (tests) falls back to the
            // explicit delegate.
            val previousClient = if (Build.VERSION.SDK_INT >= 26) {
                webView.webViewClient
            } else if (webView === visibleWebView) {
                visibleClient
            } else {
                fallbackInjectionDelegate
            }
            webView.webViewClient = ExtractionWebViewClient(previousClient, deferred)
            try {
                extract(
                    webView = webView,
                    url = url,
                    browserLike = true,
                    bridgeName = bridgeName,
                    nonce = nonce,
                    deferred = deferred,
                )
            } finally {
                // The coordinator serializes visible loads (it cancels and
                // joins the previous load before the next one starts), so this
                // restore can never clobber a newer load's client.
                webView.webViewClient = previousClient ?: WebViewClient()
            }
        }

    private suspend fun extract(
        webView: WebView,
        url: String,
        browserLike: Boolean,
        bridgeName: String,
        nonce: String,
        deferred: CompletableDeferred<ExtractionResult>,
    ): ExtractionResult {
        var scriptHandler: ScriptHandler? = null
        try {
            // The try starts before ANY setup: a failure in session
            // configuration, bridge installation, injection installation, or
            // loadUrl() must surface as an explicit Failure, never as an
            // uncaught exception (refactor H6).
            configureSession(webView, browserLike = browserLike)
            if (browserLike) {
                // Reuse hygiene: the superseded load (which may still be in
                // flight - loads survive screen exit) is aborted by this tap.
                webView.stopLoading()
            }
            val expectedToken = VoucherToken.tokenFromUrl(url)
            if (expectedToken == null) {
                return ExtractionResult.Failure(ExtractionResult.FailureReason.PARSE_ERROR)
            }
            val bridge = RedeemBridge(
                expectedNonce = nonce,
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
            webView.addJavascriptInterface(bridge, bridgeName)
            val path = installInjection(
                webView,
                forceFallbackInjection,
                if (browserLike) visibleClient else fallbackInjectionDelegate,
                expectedToken,
                bridgeName,
                nonce,
                targetApiHost,
                allowedPageOrigin,
                // Rewrite fallback with no delegate response (production
                // default, refactor H5): fail the extraction fast while the
                // page still loads natively below.
                onNoInjection = {
                    deferred.complete(ExtractionResult.Failure(ExtractionResult.FailureReason.PARSE_ERROR))
                },
            )
            scriptHandler = (path as? InjectionPath.DocumentStart)?.scriptHandler
            if (path is InjectionPath.Unavailable) {
                // No injection is possible on this WebView build (H5): the
                // page still loads so the user can use it, but extraction
                // fails fast instead of waiting out the timeout.
                webView.loadUrl(url)
                return ExtractionResult.Failure(ExtractionResult.FailureReason.PARSE_ERROR)
            }
            webView.loadUrl(url)
            return withTimeout(extractionTimeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            // Deliberately NOT stopping the visible load: the user is looking
            // at that page and it may still finish (operator throttling has
            // made RedeemSG loads take minutes before - spec 02 §2.4
            // revision). The bridge and script are removed in `finally`, so a
            // late delivery no-ops.
            return ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT)
        } catch (e: CancellationException) {
            // Cancellation (delete, superseding tap) aborts the visible load;
            // the caller owns the cancellation, so it is re-thrown.
            runCatching { webView.stopLoading() }
            throw e
        } catch (e: Exception) {
            // Any other failure during setup/load/await must surface as an
            // explicit Failure (refactor H6), never as an uncaught exception
            // that could crash the coordinator scope or strand the add flow
            // in Working. The page yielded no parseable data, so PARSE_ERROR
            // is the closest fail-soft classification.
            runCatching { webView.stopLoading() }
            return ExtractionResult.Failure(ExtractionResult.FailureReason.PARSE_ERROR)
        } finally {
            scriptHandler?.let { handler ->
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    runCatching { handler.remove() }
                }
            }
            // Keyed by the per-load unique name: this can only ever remove
            // THIS load's bridge (refactor H1).
            runCatching { webView.removeJavascriptInterface(bridgeName) }
        }
    }

    private fun createHiddenWebView(context: Context): WebView = WebView(context)

    /** Per-load random nonce - unguessable, so a forged bridge call is dropped. */
    private fun newNonce(): String = java.util.UUID.randomUUID().toString()

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
        // like a normal browser. Add-time shares that same persistent session:
        // Android's CookieManager is process-wide, so wiping it here for a
        // "fresh" add-time load would destroy the visible session on every
        // add. The fresh hidden instance still loads network-fresh for its own
        // request via LOAD_NO_CACHE; it just does not wipe the shared session.
    }

    /**
     * Per-load client (refactor H1/H6): delegates everything to the previous
     * client so existing interception (HtmlRewrite fallback, test asset
     * loaders) keeps working, and completes [deferred] with NETWORK_ERROR on
     * main-frame load failures (so real network failures are reported as
     * network errors, not as timeouts or parse errors).
     *
     * There is deliberately NO epoch gate on the delivery path: with per-load
     * unique bridge names a previous page's callback can only reach its own
     * (removed) bridge, and a gate that waits for onPageStarted would
     * sometimes drop the CURRENT page's first-tick callback - the page's
     * scripts run on the renderer thread and can deliver before the
     * main-thread onPageStarted dispatch lands (observed on WebView 150).
     */
    private class ExtractionWebViewClient(
        private val delegate: WebViewClient?,
        private val deferred: CompletableDeferred<ExtractionResult>,
    ) : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
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
            // Main-frame failures are network failures. An abort caused by our
            // own stopLoading() (supersession/cancellation) can also land here,
            // but the affected job is always cancelled by then, so completing
            // its deferred is harmless. First completion wins, so a racing
            // bridge callback is harmless either way.
            if (request.isForMainFrame) {
                deferred.complete(ExtractionResult.Failure(ExtractionResult.FailureReason.NETWORK_ERROR))
            }
            delegate?.onReceivedError(view, request, error)
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (request.isForMainFrame) {
                deferred.complete(ExtractionResult.Failure(ExtractionResult.FailureReason.NETWORK_ERROR))
            }
            delegate?.onReceivedHttpError(view, request, errorResponse)
        }
    }

    companion object {
        const val EXTRACTION_TIMEOUT_MS = 10_000L

        /** Production policy: the capture wrapper activates only on the official voucher page. */
        const val DEFAULT_ALLOWED_PAGE_ORIGIN: String = "https://voucher.redeem.gov.sg"

        /** Production policy: only the real RedeemSG API host may be captured. */
        const val DEFAULT_TARGET_API_HOST: String = "api-cdc.redeem.gov.sg"
    }
}
