package com.cdcwallet.list

import android.content.Context
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import com.cdcwallet.data.RoomVoucherRepository
import com.cdcwallet.data.db.AppDatabase
import com.cdcwallet.data.db.SqlCipherNative
import com.cdcwallet.data.model.CategoryBalance
import com.cdcwallet.data.model.ValidityStatus
import com.cdcwallet.data.model.VoucherGroup
import com.cdcwallet.extraction.ExtractionCoordinator
import com.cdcwallet.extraction.ExtractionEngine
import com.cdcwallet.extraction.assetPageLoaderClient
import com.cdcwallet.ui.detail.VoucherWebViewScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Tap-to-open-and-refresh (spec 04 §4.4, acceptance §4.6): the visible WebView
 * is the extracting WebView; success transitions an UNVERIFIED row to a real
 * status; failure keeps cached data, marks it stale, and shows the non-blocking
 * banner. Synthetic pages served via WebViewAssetLoader - never real RedeemSG
 * hosts.
 */
@RunWith(AndroidJUnit4::class)
class VoucherWebViewScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase

    private val assetLoader: WebViewAssetLoader by lazy {
        WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler(
                "/",
                WebViewAssetLoader.AssetsPathHandler(
                    InstrumentationRegistry.getInstrumentation().context,
                ),
            )
            .build()
    }

    @Before
    fun setUp() {
        SqlCipherNative.load()
        database = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .openHelperFactory(SupportOpenHelperFactory("test-passphrase".toByteArray()))
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun assetLoaderClient(): WebViewClient = assetPageLoaderClient(assetLoader)

    private fun assetWebView(context: Context): WebView =
        WebView(context).apply { webViewClient = assetLoaderClient() }

    /** The screen extracts through the engine's visible path; the epoch-gate
     *  client wraps the view's existing client on API 26+, and on API 24–25
     *  (no getter) it falls back to the engine's explicit delegate. A 30s
     *  extraction budget (vs the 10s production default) keeps slow emulator
     *  loads from aborting the extraction under full-suite load. The
     *  asset-loader fixture host is passed through the engine's test seams. */
    private fun extractionEngine(): ExtractionEngine = ExtractionEngine(
        fallbackInjectionDelegate = assetLoaderClient(),
        extractionTimeoutMs = 30_000,
        allowedPageOrigin = "https://appassets.androidplatform.net",
        targetApiHost = "appassets.androidplatform.net",
    )

    @Test
    fun unverifiedRowTransitionsToRealStatusOnTapRefresh() {
        runBlocking {
            val repository = RoomVoucherRepository(database)
            val url = "https://appassets.androidplatform.net/TestToken1"
            val unverified = VoucherGroup(
                id = "id-u1",
                token = "TestToken1",
                url = url,
                campaignName = "testpage.html",
                validityStatus = ValidityStatus.UNVERIFIED,
                expiryDate = null,
                categoryBalances = emptyList(),
                dateAdded = Instant.now(),
                lastRefreshedAt = null,
                lastRefreshError = "NETWORK_ERROR",
            )
            repository.insert(unverified)

            composeRule.setContent {
                MaterialTheme {
                    VoucherWebViewScreen(
                        voucherId = unverified.id,
                                                repository = repository,
                        extractionEngine = extractionEngine(),
                        extractionCoordinator = ExtractionCoordinator(repository, extractionEngine()),
                        onBack = {},
                        webViewFactory = ::assetWebView,
                    )
                }
            }
            composeRule.waitForIdle()

            withTimeout(45_000) {
                while (true) {
                    val row = repository.findByToken("TestToken1")
                    if (row?.validityStatus == ValidityStatus.ACTIVE) break
                    delay(100)
                }
            }

            val updated = repository.findByToken("TestToken1")
            assertEquals(ValidityStatus.ACTIVE, updated?.validityStatus)
            assertEquals("CDC Vouchers 2026", updated?.campaignName)
            // Refactor M8: raw scraped category values are stored verbatim -
            // the repository never mutates them. Capitalization happens only
            // at display time (canonicalizeCategoryForDisplay).
            assertEquals(
                listOf(
                    CategoryBalance("heartland", BigDecimal("50")),
                    CategoryBalance("supermarket", BigDecimal("25.5")),
                ),
                updated?.categoryBalances,
            )
            assertNull(updated?.lastRefreshError)
        }
    }

    @Test
    fun failedRefreshKeepsCachedDataMarksStaleAndShowsBanner() {
        runBlocking {
            val repository = RoomVoucherRepository(database)
            val url = "https://appassets.androidplatform.net/FailPage"
            val cached = VoucherGroup(
                id = "id-cached",
                token = "FailPage",
                url = url,
                campaignName = "CDC Vouchers 2026",
                validityStatus = ValidityStatus.ACTIVE,
                expiryDate = LocalDate.now().plusDays(20),
                categoryBalances = listOf(CategoryBalance("heartland", BigDecimal("50"))),
                dateAdded = Instant.now(),
                lastRefreshedAt = Instant.now(),
                lastRefreshError = null,
            )
            repository.insert(cached)

            composeRule.setContent {
                MaterialTheme {
                    VoucherWebViewScreen(
                        voucherId = cached.id,
                                                repository = repository,
                        extractionEngine = extractionEngine(),
                        extractionCoordinator = ExtractionCoordinator(repository, extractionEngine()),
                        onBack = {},
                        webViewFactory = ::assetWebView,
                    )
                }
            }

            withTimeout(45_000) {
                while (true) {
                    val row = repository.findByToken("FailPage")
                    if (row?.lastRefreshError != null) break
                    delay(100)
                }
            }

            val updated = repository.findByToken("FailPage")
            assertEquals(ValidityStatus.ACTIVE, updated?.validityStatus)
            assertEquals("CDC Vouchers 2026", updated?.campaignName)
            assertEquals("NETWORK_ERROR", updated?.lastRefreshError)

            composeRule.waitForIdle()
            composeRule.onNodeWithText("Unable to load website").assertIsDisplayed()
        }
    }

    /**
     * Refactor H1: the engine's long-lived WebView is shared, so during an
     * animated push a NEWER detail screen can re-parent it into its own host
     * before the OLD screen disposes. The old screen's dispose must NOT detach
     * the view out of the newer screen's host.
     */
    @Test
    fun disposingOldScreenLeavesReparentedWebViewAttachedToNewerHost() {
        val repository = RoomVoucherRepository(database)
        val url = "https://appassets.androidplatform.net/FailPage"
        runBlocking {
            repository.insert(
                VoucherGroup(
                    id = "id-detach-1",
                    token = "FailPage",
                    url = url,
                    campaignName = "FailPage",
                    validityStatus = ValidityStatus.UNVERIFIED,
                    expiryDate = null,
                    categoryBalances = emptyList(),
                    dateAdded = Instant.now(),
                    lastRefreshedAt = null,
                    lastRefreshError = null,
                ),
            )
        }
        var showDetail by mutableStateOf(true)
        val captured = arrayOfNulls<WebView>(1)
        composeRule.setContent {
            MaterialTheme {
                if (showDetail) {
                    VoucherWebViewScreen(
                        voucherId = "id-detach-1",
                                                repository = repository,
                        extractionEngine = extractionEngine(),
                        extractionCoordinator = ExtractionCoordinator(repository, extractionEngine()),
                        onBack = {},
                        webViewFactory = { ctx -> assetWebView(ctx).also { captured[0] = it } },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val webView = captured[0] ?: error("WebView not created")

        // Simulate the newer detail screen re-parenting the shared instance
        // during animated navigation (its factory calls acquireVisibleWebView,
        // which removes the view from any previous parent first).
        val newerHost = FrameLayout(appContext)
        composeRule.runOnUiThread {
            (webView.parent as? ViewGroup)?.removeView(webView)
            newerHost.addView(webView)
        }

        // The old screen is disposed (back navigation completes the pop).
        composeRule.runOnUiThread { showDetail = false }
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            assertEquals(
                "the newer screen's host must still own the shared WebView",
                newerHost,
                webView.parent,
            )
        }
    }

    /** Refactor H1: a screen that still owns the WebView detaches it on dispose. */
    @Test
    fun disposingScreenDetachesWebViewItStillOwnsAndClearsItsProgressClient() {
        val repository = RoomVoucherRepository(database)
        val url = "https://appassets.androidplatform.net/FailPage"
        runBlocking {
            repository.insert(
                VoucherGroup(
                    id = "id-detach-2",
                    token = "FailPage",
                    url = url,
                    campaignName = "FailPage",
                    validityStatus = ValidityStatus.UNVERIFIED,
                    expiryDate = null,
                    categoryBalances = emptyList(),
                    dateAdded = Instant.now(),
                    lastRefreshedAt = null,
                    lastRefreshError = null,
                ),
            )
        }
        var showDetail by mutableStateOf(true)
        val captured = arrayOfNulls<WebView>(1)
        composeRule.setContent {
            MaterialTheme {
                if (showDetail) {
                    VoucherWebViewScreen(
                        voucherId = "id-detach-2",
                                                repository = repository,
                        extractionEngine = extractionEngine(),
                        extractionCoordinator = ExtractionCoordinator(repository, extractionEngine()),
                        onBack = {},
                        webViewFactory = { ctx -> assetWebView(ctx).also { captured[0] = it } },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Wait until the extraction settles: page-progress events have by then
        // recomposed the screen, so the screen's dispose guard knows its host.
        runBlocking {
            withTimeout(30_000) {
                while (true) {
                    val row = repository.findByToken("FailPage")
                    if (row?.lastRefreshError != null) break
                    delay(100)
                }
            }
        }

        composeRule.runOnUiThread { showDetail = false }
        composeRule.waitForIdle()

        val webView = captured[0] ?: error("WebView not created")
        composeRule.runOnUiThread {
            assertNull("the screen must detach its own WebView", webView.parent)
            // API 24-25 have no getWebChromeClient getter; the release itself
            // is guarded the same way in production, so the assertion only
            // runs where the getter exists.
            if (Build.VERSION.SDK_INT >= 26) {
                assertNull("the screen must release its progress client", webView.webChromeClient)
            }
        }
    }

    /**
     * Refactor M18: the extraction must never start before the row lookup
     * confirms the row exists - a missing row pops the screen back and the
     * WebView never loads anything.
     */
    @Test
    fun missingRowPopsBackWithoutLoading() {
        val repository = RoomVoucherRepository(database) // empty DB
        var backCalled = false
        val captured = arrayOfNulls<WebView>(1)
        composeRule.setContent {
            MaterialTheme {
                VoucherWebViewScreen(
                    voucherId = "does-not-exist",
                    repository = repository,
                    extractionEngine = extractionEngine(),
                    extractionCoordinator = ExtractionCoordinator(repository, extractionEngine()),
                    onBack = { backCalled = true },
                    webViewFactory = { ctx -> assetWebView(ctx).also { captured[0] = it } },
                )
            }
        }
        composeRule.waitForIdle()

        // The row lookup completes with null -> the screen pops back.
        composeRule.waitUntil(5_000) { backCalled }
        assertTrue("screen must pop back for a missing row", backCalled)
        // The WebView may be created by the factory, but the row-gated VM
        // must never have launched an extraction - no loadUrl ever fired.
        composeRule.runOnUiThread {
            assertNull("missing row must never start a load", captured[0]?.url)
        }
    }
}
