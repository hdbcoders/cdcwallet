package com.cdcvouchers.extraction

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression net for the shared-state race in ExtractionEngine (refactor P1
 * item 1): `injectionPath` used to be a mutable instance field on the app-wide
 * singleton engine, so two overlapping extractions could remove the OTHER
 * call's script handler and leak its own. All teardown now happens inside
 * `extract()`'s `finally`, owned per-call. Same asset-loader pattern as
 * `ExtractionEngineTest` — synthetic pages, never real RedeemSG hosts.
 */
@RunWith(AndroidJUnit4::class)
class ExtractionEngineSessionTest {

    private lateinit var context: Context
    private lateinit var assetLoader: WebViewAssetLoader

    private val testPageUrl = "https://appassets.androidplatform.net/testpage.html"
    private val noApiPageUrl = "https://appassets.androidplatform.net/noapi.html"

    @Before
    fun setUp() {
        // Must run on the main thread — WebView versions ≤ ~100 enforce this;
        // newer ones tolerate it either way. Same pattern as VoucherApp.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        context = ApplicationProvider.getApplicationContext()
        val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(testContext))
            .build()
    }

    private fun webViewWithAssetLoader(): WebView =
        WebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
            }
        }

    private fun assetLoaderClient(): WebViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
    }

    @Test
    fun concurrentHiddenAndVisibleExtractionsOnSameEngineBothSucceed() = runTest {
        // The visible WebView comes from the engine itself (the long-lived
        // instance per 02 §2.4 revision 2026-08-03); the asset loader is
        // chained as the epoch notifier's delegate so it works on API 24-25
        // too, where a pre-existing client cannot be read back.
        val engine = ExtractionEngine(
            hiddenWebViewFactory = { webViewWithAssetLoader() },
            fallbackInjectionDelegate = assetLoaderClient(),
            // Success-required test: 30s budget so slow emulator loads under
            // the full suite don't abort the extraction at the 10s default.
            extractionTimeoutMs = 30_000,
        )
        var hidden: ExtractionResult? = null
        var visible: ExtractionResult? = null
        coroutineScope {
            launch { hidden = engine.extractForAdd(context, testPageUrl) }
            launch {
                val visibleWebView = withContext(Dispatchers.Main) { engine.acquireVisibleWebView(context) }
                visible = engine.extractFromVisibleWebView(visibleWebView, testPageUrl)
            }
        }
        assertTrue("expected Success, got $hidden", hidden is ExtractionResult.Success)
        assertTrue("expected Success, got $visible", visible is ExtractionResult.Success)
        val hiddenSuccess = hidden as ExtractionResult.Success
        val visibleSuccess = visible as ExtractionResult.Success
        assertEquals("CDC Vouchers 2026", hiddenSuccess.campaignName)
        assertEquals("CDC Vouchers 2026", visibleSuccess.campaignName)
    }

    @Test
    fun extractionTimeoutDoesNotPoisonNextExtraction() = runTest {
        val engine = ExtractionEngine(hiddenWebViewFactory = { webViewWithAssetLoader() })
        val timedOut = engine.extractForAdd(context, noApiPageUrl)
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT),
            timedOut,
        )
        val result = engine.extractForAdd(context, testPageUrl)
        assertTrue("expected Success after timeout, got $result", result is ExtractionResult.Success)
        assertEquals("CDC Vouchers 2026", (result as ExtractionResult.Success).campaignName)
    }
}
