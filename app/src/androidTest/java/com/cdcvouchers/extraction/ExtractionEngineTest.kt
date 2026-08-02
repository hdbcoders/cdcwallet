package com.cdcvouchers.extraction

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewAssetLoader
import com.cdcvouchers.data.model.ValidityStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Extraction engine tests against a synthetic page served through
 * WebViewAssetLoader that replicates the confirmed API response shape
 * (spec 02 §2.2). Never touches the real RedeemSG hosts.
 */
@RunWith(AndroidJUnit4::class)
class ExtractionEngineTest {

    private lateinit var context: Context
    private lateinit var assetLoader: WebViewAssetLoader

    private val testPageUrl = "https://appassets.androidplatform.net/testpage.html"
    private val noApiPageUrl = "https://appassets.androidplatform.net/noapi.html"
    private val badTargetPageUrl = "https://appassets.androidplatform.net/badtarget.html"
    private val malformedPageUrl = "https://appassets.androidplatform.net/malformed.html"

    @Before
    fun setUp() {
        WebView.setWebContentsDebuggingEnabled(true)
        context = ApplicationProvider.getApplicationContext()
        val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(testContext))
            .build()
    }

    private fun assetLoaderClient(): WebViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
    }

    private fun webViewWithAssetLoader(): WebView =
        WebView(context).apply { webViewClient = assetLoaderClient() }

    @Test
    fun hiddenWebViewExtractsWhitelistedData() = runTest {
        val engine = ExtractionEngine(hiddenWebViewFactory = { webViewWithAssetLoader() })
        val result = engine.extractForAdd(context, testPageUrl)

        assertTrue("expected Success, got $result", result is ExtractionResult.Success)
        val success = result as ExtractionResult.Success
        assertEquals("CDC Vouchers 2026", success.campaignName)
        assertEquals(ValidityStatus.ACTIVE, success.validityStatus)
        assertEquals(LocalDate.of(2026, 12, 31), success.expiryDate)
        assertEquals(
            listOf(
                com.cdcvouchers.data.model.CategoryBalance("heartland", BigDecimal("50")),
                com.cdcvouchers.data.model.CategoryBalance("supermarket", BigDecimal("25.5")),
            ),
            success.categoryBalances,
        )
    }

    @Test
    fun onlyWhitelistedFieldsSurviveExtraction() = runTest {
        // The synthetic response contains the resident's home address and merchant
        // names (spec 02 §2.2). The extracted Success must carry nothing but the
        // six whitelisted fields — the DTO has nowhere else for data to land.
        val engine = ExtractionEngine(hiddenWebViewFactory = { webViewWithAssetLoader() })
        val result = engine.extractForAdd(context, testPageUrl) as ExtractionResult.Success

        assertEquals(2, result.categoryBalances.size)
        result.categoryBalances.forEach { balance ->
            assertTrue(balance.category in setOf("heartland", "supermarket"))
            assertTrue(balance.remainingValue > BigDecimal.ZERO)
        }
    }

    @Test
    fun timeoutWhenPageNeverFetches() = runTest {
        val engine = ExtractionEngine(hiddenWebViewFactory = { webViewWithAssetLoader() })
        val result = engine.extractForAdd(context, noApiPageUrl)
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT),
            result,
        )
    }

    @Test
    fun networkErrorIsReported() = runTest {
        val engine = ExtractionEngine(hiddenWebViewFactory = { webViewWithAssetLoader() })
        val result = engine.extractForAdd(context, badTargetPageUrl)
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.NETWORK_ERROR),
            result,
        )
    }

    @Test
    fun forcedHtmlRewriteFallbackStillCatchesFirstFetch() = runTest {
        // API 24–25 cannot read the pre-existing client back (no getter), so
        // the delegate is supplied explicitly; on API 26+ it is read from the
        // WebView and this parameter is ignored.
        val engine = ExtractionEngine(
            forceFallbackInjection = true,
            hiddenWebViewFactory = { webViewWithAssetLoader() },
            fallbackInjectionDelegate = assetLoaderClient(),
        )
        val result = engine.extractForAdd(context, testPageUrl)
        assertTrue("expected Success via fallback, got $result", result is ExtractionResult.Success)
        val success = result as ExtractionResult.Success
        assertEquals("CDC Vouchers 2026", success.campaignName)
        assertEquals(
            listOf(
                com.cdcvouchers.data.model.CategoryBalance("heartland", BigDecimal("50")),
                com.cdcvouchers.data.model.CategoryBalance("supermarket", BigDecimal("25.5")),
            ),
            success.categoryBalances,
        )
    }

    @Test
    fun cancellingMidFlightTearsDownHiddenWebView() = runTest {
        val engine = ExtractionEngine(hiddenWebViewFactory = { webViewWithAssetLoader() })
        val scope = CoroutineScope(Dispatchers.Main + Job())
        var cancelled = false
        val job: Job = scope.launch {
            try {
                engine.extractForAdd(context, noApiPageUrl)
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        withContext(Dispatchers.IO) { Thread.sleep(2_000) }
        job.cancelAndJoin()
        assertTrue("extraction should have been cancelled", cancelled)
        scope.cancel()
    }

    @Test
    fun malformedResponseIsParseError() = runTest {
        val engine = ExtractionEngine(hiddenWebViewFactory = { webViewWithAssetLoader() })
        val result = engine.extractForAdd(context, malformedPageUrl)
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.PARSE_ERROR),
            result,
        )
    }
}
