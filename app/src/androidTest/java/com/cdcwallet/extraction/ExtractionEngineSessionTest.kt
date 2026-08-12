package com.cdcwallet.extraction

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import com.cdcwallet.data.FakeVoucherRepository
import com.cdcwallet.data.model.CategoryBalance
import com.cdcwallet.data.model.ValidityStatus
import com.cdcwallet.data.model.VoucherGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

/**
 * Regression net for the shared-state races in ExtractionEngine (refactor H1):
 * the long-lived visible WebView is reused across taps, so a previous page's
 * late callback must never reach the next load's bridge, one load's teardown
 * must never remove another load's bridge/client, and the coordinator must
 * serialize visible loads (latest-tap-wins). Same asset-loader pattern as
 * `ExtractionEngineTest` - synthetic pages, never real RedeemSG hosts. The
 * fixture host is passed through the engine's explicit test seams.
 */
@RunWith(AndroidJUnit4::class)
class ExtractionEngineSessionTest {

    private lateinit var context: Context
    private lateinit var assetLoader: WebViewAssetLoader

    private val assetOrigin = "https://appassets.androidplatform.net"

    private val testPageUrl = "https://appassets.androidplatform.net/TestToken1"
    private val noApiPageUrl = "https://appassets.androidplatform.net/NoApiPage"
    private val slowPageUrl = "https://appassets.androidplatform.net/SlowPage"

    @Before
    fun setUp() {
        // Must run on the main thread - WebView versions ≤ ~100 enforce this;
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
        WebView(context).apply { webViewClient = assetLoaderClient() }

    private fun assetLoaderClient(): WebViewClient = assetPageLoaderClient(assetLoader)

    /** Engine with the asset-loader fixture host passed through the test seams. */
    private fun engine(timeoutMs: Long = 30_000): ExtractionEngine = ExtractionEngine(
        hiddenWebViewFactory = { webViewWithAssetLoader() },
        fallbackInjectionDelegate = assetLoaderClient(),
        extractionTimeoutMs = timeoutMs,
        allowedPageOrigin = assetOrigin,
        targetApiHost = "appassets.androidplatform.net",
    )

    private fun row(id: String, token: String, url: String) = VoucherGroup(
        id = id,
        token = token,
        url = url,
        campaignName = token,
        validityStatus = ValidityStatus.UNVERIFIED,
        expiryDate = null,
        categoryBalances = listOf(CategoryBalance("heartland", BigDecimal("1"))),
        dateAdded = Instant.now(),
        lastRefreshedAt = null,
        lastRefreshError = null,
    )

    /**
     * Acquires the engine's visible WebView for a test and guarantees it is
     * destroyed afterwards. The engine is per-test, so its long-lived instance
     * is test-owned here - leaving it alive would accumulate zombie WebViews
     * (and their renderer processes) across the suite and starve later tests
     * on the emulator.
     */
    private suspend fun <T> withVisibleWebView(engine: ExtractionEngine, block: suspend (WebView) -> T): T {
        val webView = withContext(Dispatchers.Main) { engine.acquireVisibleWebView(context) }
        try {
            return block(webView)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                runCatching { webView.stopLoading() }
                runCatching { webView.destroy() }
            }
        }
    }

    @Test
    fun concurrentHiddenAndVisibleExtractionsOnSameEngineBothSucceed() = runTest {
        // The visible WebView comes from the engine itself (the long-lived
        // instance per 02 §2.4 revision 2026-08-03); the asset loader is
        // chained as the epoch notifier's delegate so it works on API 24-25
        // too, where a pre-existing client cannot be read back.
        val engine = engine()
        var hidden: ExtractionResult? = null
        var visible: ExtractionResult? = null
        coroutineScope {
            launch { hidden = engine.extractForAdd(context, testPageUrl) }
            launch {
                visible = withVisibleWebView(engine) { visibleWebView ->
                    engine.extractFromVisibleWebView(visibleWebView, testPageUrl)
                }
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
        val engine = engine(timeoutMs = 10_000)
        val timedOut = engine.extractForAdd(context, noApiPageUrl)
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT),
            timedOut,
        )
        val result = engine.extractForAdd(context, testPageUrl)
        assertTrue("expected Success after timeout, got $result", result is ExtractionResult.Success)
        assertEquals("CDC Vouchers 2026", (result as ExtractionResult.Success).campaignName)
    }

    /**
     * Spec 02 §2.10 "epoch gating" criterion, exercised at the coordinator
     * level (refactor H1): tap voucher B while voucher A's page is still
     * loading. A's extraction must be superseded (cancelled before its
     * delayed fetch fires), B's row must hold B's payload - the slow page's
     * late fetch must never be attributed to B - and A's row must be
     * untouched.
     */
    @Test
    fun tappingSecondVoucherWhileFirstLoadsCancelsFirstAndNeverMisattributesPayloads() = runTest {
        val repo = FakeVoucherRepository()
        val engine = engine()
        val coordinator = ExtractionCoordinator(repo, engine)
        withVisibleWebView(engine) { webView ->
            repo.insert(row("id-slow", "SlowPage", slowPageUrl))
            repo.insert(row("id-fast", "TestToken1", testPageUrl))

            val slowResults = mutableListOf<ExtractionResult>()
            coordinator.launchVisible("id-slow", slowPageUrl, webView) { slowResults += it }

            // Wait until the first load actually started (bridge installed, page
            // loading) so the supersession races a real in-flight load.
            withContext(Dispatchers.Main) {
                withTimeout(15_000) { while (webView.url == null) delay(100) }
            }

            val fastResults = mutableListOf<ExtractionResult>()
            coordinator.launchVisible("id-fast", testPageUrl, webView) { fastResults += it }

            withContext(Dispatchers.Main) {
                withTimeout(45_000) { while (fastResults.isEmpty()) delay(100) }
                // Give any stray late callback a moment to land (it must not).
                delay(1_000)
            }

            // The second tap succeeded and its row holds its own payload.
            assertTrue("second tap must succeed, got $fastResults", fastResults.singleOrNull() is ExtractionResult.Success)
            val fastRow = repo.findByToken("TestToken1")
            assertEquals("CDC Vouchers 2026", fastRow?.campaignName)
            assertNull(fastRow?.lastRefreshError)

            // The first tap was superseded: no callback, no DB write.
            assertTrue("superseded tap must not invoke its callback", slowResults.isEmpty())
            val slowRow = repo.findByToken("SlowPage")
            assertEquals("SlowPage", slowRow?.campaignName)
            assertNull("superseded tap must not write the row", slowRow?.lastRefreshedAt)
        }
    }

    /**
     * Latest-tap-wins for the SAME voucher (refactor H1): the coordinator used
     * to ignore a new refresh while an old one was active. A re-tap must
     * cancel and restart - the second tap's extraction result wins.
     */
    @Test
    fun retapOfSameVoucherDuringActiveRefreshIsLatestTapWins() = runTest {
        val repo = FakeVoucherRepository()
        val engine = engine()
        val coordinator = ExtractionCoordinator(repo, engine)
        withVisibleWebView(engine) { webView ->
            repo.insert(row("id-same", "SlowPage", slowPageUrl))

            val firstResults = mutableListOf<ExtractionResult>()
            coordinator.launchVisible("id-same", slowPageUrl, webView) { firstResults += it }
            withContext(Dispatchers.Main) {
                withTimeout(15_000) { while (webView.url == null) delay(100) }
            }

            // Same row id, but the re-tap loads the fast page: only latest-tap-wins
            // can land the fast page's data on the row (the slow page's data would
            // arrive ~20s later under the old ignore-while-active behavior).
            val secondResults = mutableListOf<ExtractionResult>()
            coordinator.launchVisible("id-same", testPageUrl, webView) { secondResults += it }

            withContext(Dispatchers.Main) {
                withTimeout(45_000) { while (secondResults.isEmpty()) delay(100) }
                delay(1_000)
            }

            assertTrue("re-tap must succeed, got $secondResults", secondResults.singleOrNull() is ExtractionResult.Success)
            val row = repo.findByToken("SlowPage")
            assertEquals("CDC Vouchers 2026", row?.campaignName)
            assertTrue("first (superseded) tap must not report", firstResults.isEmpty())
        }
    }

    /**
     * Cross-job cleanup isolation (refactor H1): after a superseded load is
     * cancelled mid-flight, the shared visible WebView must be fully clean -
     * a follow-up extraction on the same instance succeeds (no leftover
     * bridge, script handler, or client).
     */
    @Test
    fun extractionAfterSupersededLoadSucceedsOnSameWebView() = runTest {
        val repo = FakeVoucherRepository()
        val engine = engine()
        val coordinator = ExtractionCoordinator(repo, engine)
        withVisibleWebView(engine) { webView ->
            repo.insert(row("id-slow", "SlowPage", slowPageUrl))
            repo.insert(row("id-fast", "TestToken1", testPageUrl))

            coordinator.launchVisible("id-slow", slowPageUrl, webView) {}
            withContext(Dispatchers.Main) {
                withTimeout(15_000) { while (webView.url == null) delay(100) }
            }
            coordinator.launchVisible("id-fast", testPageUrl, webView) {}
            withContext(Dispatchers.Main) {
                withTimeout(45_000) { while (repo.findByToken("TestToken1")?.lastRefreshedAt == null) delay(100) }
            }

            val thirdResults = mutableListOf<ExtractionResult>()
            coordinator.launchVisible("id-fast", testPageUrl, webView) { thirdResults += it }
            withContext(Dispatchers.Main) {
                withTimeout(45_000) { while (thirdResults.isEmpty()) delay(100) }
            }
            assertTrue("follow-up extraction must succeed, got $thirdResults", thirdResults.singleOrNull() is ExtractionResult.Success)
        }
    }

    /**
     * Screen-callback detachment (refactor H1): when the detail screen leaves,
     * its callback must be dropped while the app-scoped database completion
     * still runs.
     */
    @Test
    fun detachingScreenCallbackStillAppliesDatabaseResult() = runTest {
        val repo = FakeVoucherRepository()
        val engine = engine()
        val coordinator = ExtractionCoordinator(repo, engine)
        withVisibleWebView(engine) { webView ->
            repo.insert(row("id-fast", "TestToken1", testPageUrl))

            val results = mutableListOf<ExtractionResult>()
            coordinator.launchVisible("id-fast", testPageUrl, webView) { results += it }
            // The screen leaves before the extraction settles.
            coordinator.detachResultCallback("id-fast")

            withContext(Dispatchers.Main) {
                withTimeout(45_000) { while (repo.findByToken("TestToken1")?.lastRefreshedAt == null) delay(100) }
                delay(1_000)
            }

            // The DB write happened app-scoped...
            assertEquals("CDC Vouchers 2026", repo.findByToken("TestToken1")?.campaignName)
            // ...but the detached screen callback never fired.
            assertTrue("detached callback must not fire", results.isEmpty())
        }
    }
}
