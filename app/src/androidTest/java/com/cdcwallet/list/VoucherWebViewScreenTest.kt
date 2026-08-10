package com.cdcwallet.list

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
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
import com.cdcwallet.ui.detail.VoucherWebViewScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * banner. Synthetic pages served via WebViewAssetLoader — never real RedeemSG
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

    private fun assetLoaderClient(): WebViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
    }

    private fun assetWebView(context: Context): WebView =
        WebView(context).apply { webViewClient = assetLoaderClient() }

    /** The screen extracts through the engine's visible path; the epoch-gate
     *  client wraps the view's existing client on API 26+, and on API 24–25
     *  (no getter) it falls back to the engine's explicit delegate. A 30s
     *  extraction budget (vs the 10s production default) keeps slow emulator
     *  loads from aborting the extraction under full-suite load. */
    private fun extractionEngine(): ExtractionEngine = ExtractionEngine(
        fallbackInjectionDelegate = assetLoaderClient(),
        extractionTimeoutMs = 30_000,
    )

    @Test
    fun unverifiedRowTransitionsToRealStatusOnTapRefresh() {
        runBlocking {
            val repository = RoomVoucherRepository(database)
            val url = "https://appassets.androidplatform.net/testpage.html"
            val unverified = VoucherGroup(
                id = "id-u1",
                token = "testpage.html",
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
                        voucherUrl = url,
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
                    val row = repository.findByToken("testpage.html")
                    if (row?.validityStatus == ValidityStatus.ACTIVE) break
                    delay(100)
                }
            }

            val updated = repository.findByToken("testpage.html")
            assertEquals(ValidityStatus.ACTIVE, updated?.validityStatus)
            assertEquals("CDC Vouchers 2026", updated?.campaignName)
            // Category names are canonicalized to capitalized form on every
            // repository write (see VoucherRepository.normalizedCategories).
            assertEquals(
                listOf(
                    CategoryBalance("Heartland", BigDecimal("50")),
                    CategoryBalance("Supermarket", BigDecimal("25.5")),
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
            val url = "https://appassets.androidplatform.net/failpage.html"
            val cached = VoucherGroup(
                id = "id-cached",
                token = "Broken",
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
                        voucherUrl = url,
                        repository = repository,
                        extractionEngine = extractionEngine(),
                        extractionCoordinator = ExtractionCoordinator(repository, extractionEngine()),
                        onBack = {},
                        webViewFactory = ::assetWebView,
                    )
                }
            }

            withTimeout(20_000) {
                while (true) {
                    val row = repository.findByToken("Broken")
                    if (row?.lastRefreshError == "NETWORK_ERROR") break
                    delay(100)
                }
            }

            val updated = repository.findByToken("Broken")
            assertEquals(ValidityStatus.ACTIVE, updated?.validityStatus)
            assertEquals("CDC Vouchers 2026", updated?.campaignName)
            assertEquals("NETWORK_ERROR", updated?.lastRefreshError)

            composeRule.waitForIdle()
            composeRule.onNodeWithText("Unable to load website").assertIsDisplayed()
        }
    }
}
