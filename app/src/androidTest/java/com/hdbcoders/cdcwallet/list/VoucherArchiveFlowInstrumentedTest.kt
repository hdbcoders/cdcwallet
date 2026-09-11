package com.hdbcoders.cdcwallet.list

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import com.hdbcoders.cdcwallet.data.RoomVoucherRepository
import com.hdbcoders.cdcwallet.data.db.AppDatabase
import com.hdbcoders.cdcwallet.data.db.SqlCipherNative
import com.hdbcoders.cdcwallet.data.model.CategoryBalance
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.extraction.ExtractionCoordinator
import com.hdbcoders.cdcwallet.extraction.ExtractionEngine
import com.hdbcoders.cdcwallet.extraction.WebViewFixtures
import com.hdbcoders.cdcwallet.ui.detail.VoucherWebViewScreen
import com.hdbcoders.cdcwallet.ui.list.ArchivedVoucherScreen
import com.hdbcoders.cdcwallet.ui.list.VoucherListScreen
import com.hdbcoders.cdcwallet.ui.theme.AppTheme
import com.hdbcoders.cdcwallet.ui.theme.LanguageStore
import com.hdbcoders.cdcwallet.ui.theme.ThemeMode
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
 * Archive / delete / restore flows (spec 05): the visible ⋮ overflow button as
 * the single entry point to the same menu, single-tap archive with no
 * confirmation and no snackbar (refactor D15; spec 05 §5.2), confirmation-gated
 * delete with the exact shared dialog (Cancel default-focused), the persistent
 * Archived entry point with its own Restore/Delete menu, archived rows
 * remaining tappable (05 §5.4), and delete racing an in-flight tap-refresh
 * (02 §2.7) - the row must not be resurrected.
 */
@RunWith(AndroidJUnit4::class)
class VoucherArchiveFlowInstrumentedTest {

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

    private fun voucher(
        id: String,
        name: String,
        status: ValidityStatus = ValidityStatus.ACTIVE,
        expiry: LocalDate? = LocalDate.now().plusDays(30),
    ) = VoucherGroup(
        id = id,
        token = id,
        url = "https://example.com/$id",
        campaignName = name,
        validityStatus = status,
        expiryDate = expiry,
        categoryBalances = listOf(CategoryBalance("heartland", BigDecimal("50"))),
        dateAdded = Instant.now(),
        lastRefreshedAt = null,
        lastRefreshError = null,
    )

    private fun assetWebView(context: Context): WebView =
        WebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
            }
        }

    private fun coordinator(repository: RoomVoucherRepository) =
        ExtractionCoordinator(repository, ExtractionEngine())

    private fun listContent(repository: RoomVoucherRepository, onOpenVoucher: (VoucherGroup) -> Unit = {}) {
        composeRule.setContent {
            AppTheme(mode = ThemeMode.LIGHT) {
                VoucherListScreen(
                    repository = repository,
                    extractionCoordinator = coordinator(repository),
                    onAddClick = {},
                    onOpenVoucher = onOpenVoucher,
                    onArchivedClick = {}, onSettingsClick = {}, onAboutClick = {},
                    languageStore = LanguageStore(appContext),
                    onLanguageSelected = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun archivedContent(
        repository: RoomVoucherRepository,
        onOpenVoucher: (VoucherGroup) -> Unit = {},
    ) {
        composeRule.setContent {
            AppTheme(mode = ThemeMode.LIGHT) {
                ArchivedVoucherScreen(
                    repository = repository,
                    extractionCoordinator = coordinator(repository),
                    onOpenVoucher = onOpenVoucher,
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun <T> waitFor(timeoutMs: Long = 15_000, block: () -> T?): T {
        var last: T? = null
        runBlocking {
            withTimeout(timeoutMs) {
                while (true) {
                    last = block()
                    if (last != null) return@withTimeout
                    delay(100)
                }
            }
        }
        return last as T
    }
    @Test
    fun archiveFromMenuHidesRowWithoutUndoSnackbar() {
        val repository = RoomVoucherRepository(database)
        runBlocking {
            repository.insert(voucher("a1", "Link One"))
            repository.insert(voucher("a2", "Link Two"))
        }
        listContent(repository)

        composeRule.onNodeWithTag("archived-count", useUnmergedTree = true).assertTextEquals("0")
        composeRule.onNodeWithContentDescription("More options for Link One").performClick()
        composeRule.onNodeWithText("Archive").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        composeRule.onNodeWithText("Archive").performClick()

        // Spec 05 §5.2 (product change 2026-08-09): archive is single-tap with
        // no dialog and no undo snackbar; the row leaves the main list and is
        // reversible from the Archived screen.
        composeRule.onNodeWithText("Undo").assertDoesNotExist()
        composeRule.onNodeWithText("Link One").assertDoesNotExist()
        composeRule.onNodeWithText("Link Two").assertIsDisplayed()
        composeRule.onNodeWithTag("archived-count", useUnmergedTree = true).assertTextEquals("1")

        // Row still exists in the database, just flagged archived - not destroyed.
        waitFor {
            val row = runBlocking { repository.findByToken("a1") }
            if (row?.isArchived == true) row else null
        }
    }

    @Test
    fun copyUrlMenuItemCopiesVoucherUrlToClipboard() {
        val repository = RoomVoucherRepository(database)
        runBlocking { repository.insert(voucher("a1", "Link One")) }
        listContent(repository)

        composeRule.onNodeWithContentDescription("More options for Link One").performClick()
        composeRule.onNodeWithText("Copy URL").assertIsDisplayed()
        composeRule.onNodeWithText("Copy URL").performClick()
        composeRule.waitForIdle()

        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        assertEquals(
            "https://example.com/a1",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )
        // REQ-10 feedback: a brief "Link copied" toast confirms the copy so
        // the tap never feels dead. Toast windows are not exposed to
        // UiAutomation on API 36, so the toast itself is covered by manual QA.
    }

    @Test
    fun archivedScreenCopyUrlCopiesToClipboard() {
        val repository = RoomVoucherRepository(database)
        runBlocking {
            repository.insert(voucher("arch1", "Archived One"))
            repository.archive("arch1")
        }
        archivedContent(repository)

        composeRule.onNodeWithContentDescription("More options for Archived One").performClick()
        composeRule.onNodeWithText("Copy URL").performClick()
        composeRule.waitForIdle()

        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        assertEquals(
            "https://example.com/arch1",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )
    }

    @Test
    fun deleteShowsExactDialogAndCancelKeepsRow() {
        val repository = RoomVoucherRepository(database)
        runBlocking { repository.insert(voucher("a1", "Link One")) }
        listContent(repository)

        composeRule.onNodeWithContentDescription("More options for Link One").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        // Spec 05 §5.3: exact shared copy; Cancel is the default (leftmost)
        // button, Delete carries destructive styling. (The FocusRequester-based
        // default focus on Cancel is not observable in this headless test
        // environment, which never grants window focus.)
        composeRule.onNodeWithText("Delete this voucher link?").assertIsDisplayed()
        composeRule.onNodeWithText("You may not be able to get this link back once it's deleted.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        val cancelLeft = composeRule.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot.left
        val deleteLeft = composeRule.onNodeWithText("Delete").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Cancel should be the default (leftmost) button", cancelLeft < deleteLeft)
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Delete this voucher link?").assertDoesNotExist()
        runBlocking {
            assertTrue(repository.findByToken("a1") != null)
        }
    }

    @Test
    fun deleteConfirmRemovesRowPermanently() {
        val repository = RoomVoucherRepository(database)
        runBlocking { repository.insert(voucher("a1", "Link One")) }
        listContent(repository)

        composeRule.onNodeWithContentDescription("More options for Link One").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        waitFor {
            val row = runBlocking { repository.findByToken("a1") }
            if (row == null) true else null
        }
        composeRule.onNodeWithText("Link One").assertDoesNotExist()
        composeRule.onNodeWithText("No voucher links yet. Add one with the + button.")
            .assertIsDisplayed()
    }

    @Test
    fun archivedScreenTapOpensVoucher() {
        val repository = RoomVoucherRepository(database)
        runBlocking {
            repository.insert(voucher("arch1", "Archived One"))
            repository.archive("arch1")
        }
        var openedId: String? = null
        archivedContent(repository) { openedId = it.id }

        // Spec 05 §5.4: archiving must not disable the row's tap - it opens
        // the real page in-app exactly like a main-list tap.
        composeRule.onNodeWithText("Archived One").performClick()
        composeRule.waitForIdle()
        assertEquals("arch1", openedId)
    }

    @Test
    fun archivedScreenShowsEmptyStateAndRestoreMovesRowBack() {
        val repository = RoomVoucherRepository(database)
        archivedContent(repository)
        composeRule.onNodeWithText("No archived vouchers yet").assertIsDisplayed()

        runBlocking {
            repository.insert(voucher("arch1", "Archived One"))
            repository.archive("arch1")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Archived One").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More options for Archived One").performClick()
        composeRule.onNodeWithText("Restore").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        composeRule.onNodeWithText("Restore").performClick()

        waitFor {
            val row = runBlocking { repository.findByToken("arch1") }
            if (row != null && !row.isArchived) row else null
        }
        composeRule.onNodeWithText("Archived One").assertDoesNotExist()
        composeRule.onNodeWithText("No archived vouchers yet").assertIsDisplayed()
    }

    @Test
    fun archivedScreenDeleteUsesSameDialog() {
        val repository = RoomVoucherRepository(database)
        runBlocking {
            repository.insert(voucher("arch1", "Archived One"))
            repository.archive("arch1")
        }
        archivedContent(repository)

        composeRule.onNodeWithContentDescription("More options for Archived One").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNodeWithText("Delete this voucher link?").assertIsDisplayed()
        composeRule.onNodeWithText("You may not be able to get this link back once it's deleted.")
            .assertIsDisplayed()
        val cancelLeft = composeRule.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot.left
        val deleteLeft = composeRule.onNodeWithText("Delete").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Cancel should be the default (leftmost) button", cancelLeft < deleteLeft)
    }

    @Test
    fun deleteWhileTapRefreshInFlightDoesNotResurrectRow() {
        val repository = RoomVoucherRepository(database)
        val url = "ftp://internal.local/vouchers/groups/Slow"
        val slow = VoucherGroup(
            id = "id-slow",
            token = "Slow",
            url = url,
            campaignName = "Slow",
            validityStatus = ValidityStatus.UNVERIFIED,
            expiryDate = null,
            categoryBalances = emptyList(),
            dateAdded = Instant.now(),
            lastRefreshedAt = null,
            lastRefreshError = null,
        )
        runBlocking { repository.insert(slow) }

        composeRule.setContent {
            AppTheme(mode = ThemeMode.LIGHT) {
                VoucherWebViewScreen(
                    voucherId = slow.id,
                                        repository = repository,
                    extractionEngine = ExtractionEngine(),
                    extractionCoordinator = coordinator(repository),
                    onBack = {},
                    webViewFactory = ::assetWebView,
                )
            }
        }
        composeRule.waitForIdle()

        // The tap-refresh is now in flight against a URL that never loads
        // (engine times out at 10s). Delete from another path (spec 05 §5.6).
        runBlocking { repository.delete("id-slow") }

        // Wait past the engine timeout: the late failure/result must not
        // resurrect the deleted row, and nothing may crash. Bounded polling
        // (refactor D10: no nested runBlocking, no Thread.sleep) - there is
        // no completion signal for a negative assertion, so the loop waits
        // out the real engine timeout.
        runBlocking {
            withTimeout(20_000) {
                var resurrected = false
                for (i in 0..150) {
                    delay(100)
                    if (repository.findByToken("Slow") != null) {
                        resurrected = true
                        break
                    }
                }
                assertTrue("deleted row must not be resurrected", !resurrected)
            }
        }
        assertNull(runBlocking { repository.findByToken("Slow") })
    }

    @Test
    fun deleteViaListMenuWhileDetailRefreshInFlightDoesNotResurrectRow() {
        // Refactor D6: the production path - an engine-owned visible
        // extraction (no injected WebView factory; a URL that never loads, so
        // the engine times out) is in flight, then the LIST screen's
        // ViewModel performs the delete (vm.delete -> coordinator.cancel for
        // the in-flight job -> row removed). The late result must never
        // resurrect the row.
        val repository = RoomVoucherRepository(database)
        val url = "ftp://internal.local/vouchers/groups/Slow"
        val slow = VoucherGroup(
            id = "id-slow",
            token = "Slow",
            url = url,
            campaignName = "Slow",
            validityStatus = ValidityStatus.UNVERIFIED,
            expiryDate = null,
            categoryBalances = emptyList(),
            dateAdded = Instant.now(),
            lastRefreshedAt = null,
            lastRefreshError = null,
        )
        runBlocking { repository.insert(slow) }

        // One shared coordinator across the launch and the delete - the
        // app-scoped slot the list VM cancels must be the one the extraction
        // runs on.
        val sharedCoordinator = coordinator(repository)
        val engine = ExtractionEngine()

        // Start the tap-refresh on the engine-owned visible WebView (the exact
        // production mechanism the detail screen invokes) against a URL that
        // never completes, so the extraction is in flight while the delete
        // below runs. composeRule allows ONE setContent per test, so the
        // launch is driven directly instead of through a second screen.
        composeRule.runOnUiThread {
            val webView = engine.acquireVisibleWebView(appContext)
            sharedCoordinator.launchVisible(slow.id, url, webView) { }
        }

        // The main list deletes through the real kebab menu + ViewModel path
        // (vm.delete -> coordinator.cancel -> row removed).
        composeRule.setContent {
            AppTheme(mode = ThemeMode.LIGHT) {
                VoucherListScreen(
                    repository = repository,
                    extractionCoordinator = sharedCoordinator,
                    onAddClick = {},
                    onOpenVoucher = {},
                    onArchivedClick = {},
                    onSettingsClick = {}, onAboutClick = {},
                    languageStore = LanguageStore(appContext),
                    onLanguageSelected = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("More options for Slow").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        // Step 3: wait out the engine timeout - the late result must not
        // resurrect the deleted row, and nothing may crash.
        runBlocking {
            withTimeout(20_000) {
                var resurrected = false
                for (i in 0..150) {
                    delay(100)
                    if (repository.findByToken("Slow") != null) {
                        resurrected = true
                        break
                    }
                }
                assertTrue("deleted row must not be resurrected", !resurrected)
            }
        }
        assertNull(runBlocking { repository.findByToken("Slow") })
    }
}
