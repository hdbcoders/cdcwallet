package com.hdbcoders.cdcwallet.addflow

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewAssetLoader
import com.hdbcoders.cdcwallet.data.RoomVoucherRepository
import com.hdbcoders.cdcwallet.data.db.AppDatabase
import com.hdbcoders.cdcwallet.data.db.SqlCipherNative
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.extraction.ExtractionEngine
import com.hdbcoders.cdcwallet.extraction.RequestCountingClient
import com.hdbcoders.cdcwallet.extraction.WebViewFixtures
import com.hdbcoders.cdcwallet.extraction.assetPageLoaderClient
import com.hdbcoders.cdcwallet.ui.add.AddVoucherScreen
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * Duplicate-add UX (spec 03 §3.2 step 2): the add flow ends on the add screen
 * with "Link not added. Voucher already in your list." - no navigation away,
 * no list highlight, no second row. Drives the real AddVoucherScreen + flow
 * against the SQLCipher repository; the duplicate check runs before any fetch,
 * so no WebView/network is involved.
 */
@RunWith(AndroidJUnit4::class)
class DuplicateAddScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase

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

    private fun flow(repository: RoomVoucherRepository): AddVoucherFlow =
        AddVoucherFlow(
            repository = repository,
            extractionEngine = ExtractionEngine(
                hiddenWebViewFactory = { assetWebView(it) },
                allowedPageOrigin = WebViewFixtures.ASSET_ORIGIN,
                targetApiHost = WebViewFixtures.ASSET_DOMAIN,
            ),
            validator = VoucherLinkValidator(allowedHost = "appassets.androidplatform.net"),
        )

    private fun assetWebView(context: Context): WebView =
        WebViewFixtures.assetWebView(context, assetLoader)

    private val assetLoader: WebViewAssetLoader by lazy { WebViewFixtures.buildAssetLoader() }

    @Test
    fun duplicateSubmitShowsMessageAndStaysOnAddScreen() {
        val repository = RoomVoucherRepository(database)
        val url = "https://appassets.androidplatform.net/TestToken1"
        runBlocking {
            repository.insert(
                VoucherGroup(
                    id = "id-existing",
                    token = "TestToken1",
                    url = url,
                    campaignName = "CDC Vouchers 2026",
                    validityStatus = ValidityStatus.ACTIVE,
                    expiryDate = null,
                    categoryBalances = emptyList(),
                    dateAdded = Instant.now(),
                    lastRefreshedAt = null,
                    lastRefreshError = null,
                ),
            )
        }
        var backCalled = false
        composeRule.setContent {
            AddVoucherScreen(
                flow = flow(repository),
                onBack = { backCalled = true },
            )
        }

        // The paste field is auto-focused on arrival so the user can type or
        // paste without an extra tap.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(isFocused() and hasText("Paste voucher link"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(appContext.getString(R.string.paste_link)).performTextInput(url)
        composeRule.onNodeWithText(appContext.getString(R.string.add)).performClick()

        // The flow ends on the add screen with the new terminal message.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Link not added. Voucher already in your list.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Link not added. Voucher already in your list.")
            .assertIsDisplayed()
        // The screen is still the add screen (field + button still present)…
        composeRule.onNodeWithText(appContext.getString(R.string.paste_link)).assertIsDisplayed()
        composeRule.onNodeWithText(appContext.getString(R.string.add)).assertIsDisplayed()
        // …no navigation happened…
        assertFalse(backCalled)
        // …and no second row was inserted.
        val rows = runBlocking { repository.findAll() }
        assertEquals(1, rows.size)
        assertEquals(1, rows.count { it.token == "TestToken1" })
    }

    @Test
    fun duplicateSubmitNeverCreatesAWebViewOrTouchesTheNetwork() {
        // Refactor D9: the duplicate check must run strictly before any
        // extraction - a duplicate submit may never construct the hidden
        // WebView, and the counting client proves zero intercepted requests
        // even if one were constructed.
        val repository = RoomVoucherRepository(database)
        val url = "https://appassets.androidplatform.net/TestToken1"
        runBlocking {
            repository.insert(
                VoucherGroup(
                    id = "id-existing",
                    token = "TestToken1",
                    url = url,
                    campaignName = "CDC Vouchers 2026",
                    validityStatus = ValidityStatus.ACTIVE,
                    expiryDate = null,
                    categoryBalances = emptyList(),
                    dateAdded = Instant.now(),
                    lastRefreshedAt = null,
                    lastRefreshError = null,
                ),
            )
        }
        val factoryInvocations = AtomicInteger(0)
        var countingClient: RequestCountingClient? = null
        val countingEngine = ExtractionEngine(
            hiddenWebViewFactory = { context ->
                factoryInvocations.incrementAndGet()
                WebView(context).apply {
                    webViewClient =
                        RequestCountingClient(assetPageLoaderClient(assetLoader)).also { countingClient = it }
                }
            },
            allowedPageOrigin = WebViewFixtures.ASSET_ORIGIN,
            targetApiHost = WebViewFixtures.ASSET_DOMAIN,
        )
        composeRule.setContent {
            AddVoucherScreen(
                flow = AddVoucherFlow(
                    repository = repository,
                    extractionEngine = countingEngine,
                    validator = VoucherLinkValidator(allowedHost = "appassets.androidplatform.net"),
                ),
                onBack = {},
            )
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(isFocused() and hasText("Paste voucher link"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(appContext.getString(R.string.paste_link)).performTextInput(url)
        composeRule.onNodeWithText(appContext.getString(R.string.add)).performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Link not added. Voucher already in your list.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        // The strongest zero-network proof: the hidden WebView was never even
        // created, so nothing could have been fetched.
        assertEquals(0, factoryInvocations.get())
        assertEquals(0, countingClient?.requestCount?.get() ?: 0)
    }
}
