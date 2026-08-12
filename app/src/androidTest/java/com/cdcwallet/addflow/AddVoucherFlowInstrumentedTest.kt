package com.cdcwallet.addflow

import android.content.Context
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewAssetLoader
import com.cdcwallet.MainActivity
import com.cdcwallet.data.RoomVoucherRepository
import com.cdcwallet.data.db.AppDatabase
import com.cdcwallet.data.db.SqlCipherNative
import com.cdcwallet.data.model.ValidityStatus
import com.cdcwallet.extraction.ExtractionEngine
import com.cdcwallet.extraction.assetPageLoaderClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * End-to-end add flow against the real SQLCipher repository and the real
 * extraction engine, with synthetic pages served through WebViewAssetLoader
 * (same shape as the real API response, spec 02 §2.2). Never touches real
 * RedeemSG hosts.
 */
@RunWith(AndroidJUnit4::class)
class AddVoucherFlowInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private val assetLoader: WebViewAssetLoader by lazy {
        WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler(
                "/",
                WebViewAssetLoader.AssetsPathHandler(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context,
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

    private fun assetWebView(context: Context): WebView =
        WebView(context).apply { webViewClient = assetPageLoaderClient(assetLoader) }

    /** Engine with the asset-loader fixture host passed through the test seams. */
    private fun extractionEngine(): ExtractionEngine = ExtractionEngine(
        hiddenWebViewFactory = { assetWebView(it) },
        // Success-required tests: 30s budget so slow emulator loads under the
        // full suite don't abort the extraction at the 10s production default.
        extractionTimeoutMs = 30_000,
        allowedPageOrigin = "https://appassets.androidplatform.net",
        targetApiHost = "appassets.androidplatform.net",
        // API 24-25 cannot read the view's client back (no getter): the
        // rewrite-fallback path needs the delegate explicitly.
        fallbackInjectionDelegate = assetPageLoaderClient(assetLoader),
    )

    @Test
    fun happyPathAddsRowWithRealData() = runTest {
        val repository = RoomVoucherRepository(database)
        val flow = AddVoucherFlow(
            repository = repository,
            extractionEngine = extractionEngine(),
            validator = VoucherLinkValidator(allowedHost = "appassets.androidplatform.net"),
        )

        val result = flow.add(appContext, "https://appassets.androidplatform.net/TestToken1")

        assertTrue(result is AddVoucherResult.Added)
        val voucher = (result as AddVoucherResult.Added).voucher
        assertEquals("CDC Vouchers 2026", voucher.campaignName)
        assertEquals(ValidityStatus.ACTIVE, voucher.validityStatus)
        assertEquals(
            listOf(
                com.cdcwallet.data.model.CategoryBalance("heartland", BigDecimal("50")),
                com.cdcwallet.data.model.CategoryBalance("supermarket", BigDecimal("25.5")),
            ),
            voucher.categoryBalances,
        )
        val found = runBlocking { repository.findByToken("TestToken1") }
        assertEquals("CDC Vouchers 2026", found?.campaignName)
        assertTrue(found?.categoryBalances.orEmpty().isNotEmpty())
    }

    @Test
    fun failedFetchStillAddsRowAsUnverified() = runTest {
        val repository = RoomVoucherRepository(database)
        val flow = AddVoucherFlow(
            repository = repository,
            extractionEngine = extractionEngine(),
            validator = VoucherLinkValidator(allowedHost = "appassets.androidplatform.net"),
        )

        val result = flow.add(appContext, "https://appassets.androidplatform.net/BrokenFetch")

        assertTrue(result is AddVoucherResult.AddedUnverified)
        val voucher = (result as AddVoucherResult.AddedUnverified).voucher
        assertEquals(ValidityStatus.UNVERIFIED, voucher.validityStatus)
        assertEquals("NETWORK_ERROR", voucher.lastRefreshError)
    }

    @Test
    fun shareIntentTextArrivesInTheAddField() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, SHARED_TEXT)
            // Explicit component, as resolved when the user picks this app from
            // the system share sheet.
            setClassName("com.cdcwallet", "com.cdcwallet.MainActivity")
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText(SHARED_TEXT).assertIsDisplayed()
        }
    }

    private companion object {
        const val SHARED_TEXT = "https://voucher.redeem.gov.sg/SharedTokenABC"
    }
}
