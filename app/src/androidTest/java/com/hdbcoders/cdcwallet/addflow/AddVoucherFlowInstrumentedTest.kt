package com.hdbcoders.cdcwallet.addflow

import android.app.Application
import android.content.Context
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewAssetLoader
import com.hdbcoders.cdcwallet.MainActivity
import com.hdbcoders.cdcwallet.VoucherApp
import com.hdbcoders.cdcwallet.data.RoomVoucherRepository
import com.hdbcoders.cdcwallet.data.db.AppDatabase
import com.hdbcoders.cdcwallet.data.db.SqlCipherNative
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import com.hdbcoders.cdcwallet.dev.DevActions
import com.hdbcoders.cdcwallet.extraction.ExtractionEngine
import com.hdbcoders.cdcwallet.extraction.WebViewFixtures
import com.hdbcoders.cdcwallet.extraction.assetPageLoaderClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
    val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private val assetLoader: WebViewAssetLoader by lazy { WebViewFixtures.buildAssetLoader() }

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
        WebViewFixtures.assetWebView(context, assetLoader)

    /** Engine with the asset-loader fixture host passed through the test seams
     *  (30s budget: success-required tests under full-suite emulator load). */
    private fun extractionEngine(): ExtractionEngine =
        WebViewFixtures.fixtureEngine(assetLoader, hiddenWebViewFactory = ::assetWebView)

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
                com.hdbcoders.cdcwallet.data.model.CategoryBalance("heartland", BigDecimal("50")),
                com.hdbcoders.cdcwallet.data.model.CategoryBalance("supermarket", BigDecimal("25.5")),
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
    fun sharedUrlRunsTheFullAddPipelineToAPersistedRow() {
        // The D9 pipeline test, routed through the REAL share intent (the
        // former shareIntentTextArrivesInTheAddField only proved the text
        // reaches the Add field; this stronger test subsumes it): intent
        // arrival -> validation -> duplicate check -> fixture extraction ->
        // row persisted, against MainActivity's real routing. Fixture
        // components come from the debug seam (DevFixtureFlow) via
        // EXTRA_DEV_FIXTURE_ADD - never the production policy, never a real
        // RedeemSG host.
        val app = ApplicationProvider.getApplicationContext<Application>() as VoucherApp
        runBlocking {
            app.container.databaseBootstrap.awaitReady()
            // Fresh real DB: drop ALL rows (incl. the dev seed) so the fixture
            // token lands on the Added path, not the Duplicate one.
            app.sendBroadcast(Intent(DevActions.ACTION_CLEAR))
            withTimeout(15_000) {
                while (app.container.repository.findByToken("TestToken1") != null) {
                    delay(100)
                }
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, FIXTURE_SHARE_URL)
            putExtra(MainActivity.EXTRA_DEV_FIXTURE_ADD, true)
            // Explicit component, as resolved when the user picks this app from
            // the system share sheet. app.packageName carries the debug
            // ".debug" applicationId suffix (639feb7) - never the base id.
            setClassName(app.packageName, "com.hdbcoders.cdcwallet.MainActivity")
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            // The arrived URL auto-submits; wait for the success message.
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodesWithText("Added:", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        // The row landed in the real repository with the extracted payload.
        val found = runBlocking { app.container.repository.findByToken("TestToken1") }
        assertEquals("CDC Vouchers 2026", found?.campaignName)
        assertEquals(ValidityStatus.ACTIVE, found?.validityStatus)
        assertTrue(found?.categoryBalances.orEmpty().isNotEmpty())
    }

    private companion object {
        const val FIXTURE_SHARE_URL = "https://appassets.androidplatform.net/TestToken1"
    }
}
