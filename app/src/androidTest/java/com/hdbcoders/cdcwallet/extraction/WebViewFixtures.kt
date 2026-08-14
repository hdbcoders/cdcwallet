package com.hdbcoders.cdcwallet.extraction

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared synthetic-WebView fixture tooling (refactor D5): the asset loader,
 * its client, asset-backed WebView factories, and an engine pre-wired with
 * the fixture host through the production test seams. Centralized here so
 * interception behavior changes in ONE place instead of drifting across the
 * instrumented suites.
 */
object WebViewFixtures {

    const val ASSET_DOMAIN = "appassets.androidplatform.net"
    const val ASSET_ORIGIN = "https://appassets.androidplatform.net"

    /** Builds the asset loader serving the synthetic page fixtures. */
    fun buildAssetLoader(): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .setDomain(ASSET_DOMAIN)
            .addPathHandler(
                "/",
                WebViewAssetLoader.AssetsPathHandler(
                    InstrumentationRegistry.getInstrumentation().context,
                ),
            )
            .build()

    /** Fresh WebView with the asset-loader fixture client installed. */
    fun assetWebView(context: Context, assetLoader: WebViewAssetLoader): WebView =
        WebView(context).apply { webViewClient = assetPageLoaderClient(assetLoader) }

    /**
     * Engine with the asset-loader fixture host passed through the test seams
     * (never through the production policy). A 30s default budget (vs the 10s
     * production default) keeps slow emulator loads from aborting extractions
     * under full-suite load.
     */
    fun fixtureEngine(
        assetLoader: WebViewAssetLoader,
        timeoutMs: Long = 30_000,
        hiddenWebViewFactory: ((Context) -> WebView)? = null,
        forceFallbackInjection: Boolean = false,
        fallbackInjectionDelegate: WebViewClient? = assetPageLoaderClient(assetLoader),
    ): ExtractionEngine = ExtractionEngine(
        forceFallbackInjection = forceFallbackInjection,
        hiddenWebViewFactory = hiddenWebViewFactory ?: { context -> assetWebView(context, assetLoader) },
        fallbackInjectionDelegate = fallbackInjectionDelegate,
        extractionTimeoutMs = timeoutMs,
        allowedPageOrigin = ASSET_ORIGIN,
        targetApiHost = ASSET_DOMAIN,
    )

    /**
     * Enables WebView debugging. Must run on the main thread - WebView
     * versions ≤ ~100 enforce this; newer ones tolerate it either way. Same
     * pattern as VoucherApp.
     */
    fun enableWebViewDebugging() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}

/**
 * Wraps a delegate client and counts every main-frame/subresource
 * interception (refactor D9), so tests can prove zero extraction/network
 * activity (e.g. duplicate submits that must never reach the WebView).
 */
class RequestCountingClient(private val delegate: WebViewClient) : WebViewClient() {

    val requestCount = AtomicInteger(0)

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        requestCount.incrementAndGet()
        return delegate.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        delegate.shouldOverrideUrlLoading(view, request)

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        delegate.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        delegate.onPageFinished(view, url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        delegate.onReceivedError(view, request, error)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        delegate.onReceivedHttpError(view, request, errorResponse)
    }
}
