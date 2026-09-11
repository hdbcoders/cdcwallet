package com.hdbcoders.cdcwallet.list

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hdbcoders.cdcwallet.data.RoomVoucherRepository
import com.hdbcoders.cdcwallet.data.db.AppDatabase
import com.hdbcoders.cdcwallet.data.db.SqlCipherNative
import com.hdbcoders.cdcwallet.data.model.CategoryBalance
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.extraction.ExtractionCoordinator
import com.hdbcoders.cdcwallet.extraction.ExtractionEngine
import com.hdbcoders.cdcwallet.ui.list.VoucherListScreen
import com.hdbcoders.cdcwallet.ui.theme.AppTheme
import com.hdbcoders.cdcwallet.ui.theme.LanguageStore
import com.hdbcoders.cdcwallet.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Main list screen (spec 04): sorting with UNVERIFIED pinned on top, badge
 * rendering, aggregate summary, overflow ⋮ buttons, tap routing and the
 * overflow menu.
 */
@RunWith(AndroidJUnit4::class)
class VoucherListScreenTest {

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

    private fun voucher(
        id: String,
        name: String,
        status: ValidityStatus,
        expiry: LocalDate?,
        balances: List<CategoryBalance> = emptyList(),
        lastRefreshError: String? = null,
    ) = VoucherGroup(
        id = id,
        token = id,
        url = "https://example.com/$id",
        campaignName = name,
        validityStatus = status,
        expiryDate = expiry,
        categoryBalances = balances,
        dateAdded = Instant.now(),
        lastRefreshedAt = null,
        lastRefreshError = lastRefreshError,
    )

    @Test
    fun listRendersSortedWithUnverifiedPinnedBadgesAndSummary() {
        val repository = RoomVoucherRepository(database)
        val today = LocalDate.now()
        runBlocking {
            repository.insert(
                voucher(
                    "a10", "Link Ten", ValidityStatus.ACTIVE, today.plusDays(10),
                    listOf(
                        CategoryBalance("heartland", BigDecimal("50")),
                        CategoryBalance("supermarket", BigDecimal("25.5")),
                    ),
                ),
            )
            repository.insert(
                voucher(
                    "a40", "Link Forty", ValidityStatus.ACTIVE, today.plusDays(40),
                    listOf(CategoryBalance("groceries", BigDecimal("10"))),
                ),
            )
            repository.insert(
                voucher("u1", "u.html", ValidityStatus.UNVERIFIED, null, lastRefreshError = "NETWORK_ERROR"),
            )
            repository.insert(
                voucher(
                    "ns", "Link Not Started", ValidityStatus.NOT_STARTED, today.plusDays(5),
                    listOf(CategoryBalance("heartland", BigDecimal("5"))),
                ),
            )
            repository.insert(
                voucher(
                    "ex", "Link Expired", ValidityStatus.EXPIRED, today.minusDays(2),
                    listOf(CategoryBalance("merchants", BigDecimal("2"))),
                ),
            )
        }

        composeRule.setContent {
            AppTheme(mode = ThemeMode.LIGHT) {
                VoucherListScreen(
                    repository = repository,
                    extractionCoordinator = ExtractionCoordinator(repository, ExtractionEngine()),
                    onAddClick = {},
                    onOpenVoucher = {},
                    onArchivedClick = {}, onSettingsClick = {}, onAboutClick = {},
                    languageStore = LanguageStore(appContext),
                    onLanguageSelected = {},
                )
            }
        }

        // Spec 04 §4.1: UNVERIFIED pinned above everyone else; rest by soonest expiry.
        // The taller redesigned cards leave later rows below the fold, so the
        // position-order check covers the initially-visible rows and later rows
        // are asserted after scrolling.
        assertTopToBottomOrder(
            "u.html",
            "Link Expired",
            "Link Not Started",
        )

        // Spec 04 §4.2: badge states render distinctly (Unverified / Expired /
        // Not started are visible up top; Link Ten's days-left is asserted
        // after scrolling to it below).
        composeRule.onNodeWithText("Tap to check").assertIsDisplayed()
        composeRule.onNodeWithText("Expired").assertIsDisplayed()
        composeRule.onNodeWithText("Not started").assertIsDisplayed()

        // Refactor H10 (spec 04 §4.2): the UNVERIFIED row shows the neutral
        // unverified footer in its body - never the "no more balance" banner
        // (empty balances are not proof of being fully used).
        composeRule.onNodeWithText("Tap to check your balance", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("This voucher link has no more balance").assertCountEquals(0)

        // Refactor M21: accessibility semantics, not just visible text -
        // the kebab is a 48dp touch target and the hero announces its
        // expanded state. (Badge + expiry are announced together for TalkBack
        // by the card's own merged clickable semantics - no separate
        // mergeDescendants needed, which also keeps per-node finders working
        // in the merged tree.)
        composeRule.onNodeWithContentDescription("More options for Link Expired")
            .assertWidthIsEqualTo(48.dp)
        composeRule.onNodeWithTag("balance-hero")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        // Spec 04 §4.3: the total counts only ACTIVE entries (refactor H9) -
        // the UNVERIFIED row and the NOT_STARTED/EXPIRED balances contribute
        // nothing to the value or the count. The hero renders the eyebrow in
        // uppercase and splits "$" from the amount into separate Text nodes
        // (mockup), so assert per-node.
        composeRule.onNodeWithText("BALANCE").assertIsDisplayed()
        composeRule.onNodeWithText("85.5").assertIsDisplayed()
        // Category breakdown rows (top 3 by value) render in the right column.
        // Use onFirst() because category names also appear on voucher cards.
        for (part in listOf(
            "Groceries",
            "Heartland",
            "Supermarket",
        )) {
            composeRule.onAllNodesWithText(part, substring = true).onFirst().assertIsDisplayed()
        }

        // Link Ten (4th row) may sit below the fold; scroll it into view.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Link Ten"))
        composeRule.onNodeWithText("Link Ten").assertIsDisplayed()
        composeRule.onNodeWithText("10 days left").assertIsDisplayed()

        // Last row (Link Forty) sits below the fold; scroll it into view.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Link Forty"))
        composeRule.onNodeWithText("Link Forty").assertIsDisplayed()
        composeRule.onNodeWithText("40 days left").assertIsDisplayed()
    }

    @Test
    fun staleRowShowsRefreshFailedStatusInsteadOfExpiry() {
        // Refactor M5: a row whose last refresh failed swaps its expiry
        // segment for the neutral stale status - the badge label itself is
        // untouched, and cached expiry must never LOOK current.
        val repository = RoomVoucherRepository(database)
        runBlocking {
            repository.insert(
                voucher(
                    "stale1", "Stale Link", ValidityStatus.ACTIVE, LocalDate.now().plusDays(30),
                    listOf(CategoryBalance("heartland", BigDecimal("10"))),
                    lastRefreshError = "NETWORK_ERROR",
                ),
            )
        }

        composeRule.setContent {
            AppTheme(mode = ThemeMode.LIGHT) {
                VoucherListScreen(
                    repository = repository,
                    extractionCoordinator = ExtractionCoordinator(repository, ExtractionEngine()),
                    onAddClick = {},
                    onOpenVoucher = {},
                    onArchivedClick = {}, onSettingsClick = {}, onAboutClick = {},
                    languageStore = LanguageStore(appContext),
                    onLanguageSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Stale Link").assertIsDisplayed()
        composeRule.onNodeWithText("30 days left").assertIsDisplayed()
        composeRule.onNodeWithText("· Couldn't refresh · tap to retry").assertIsDisplayed()
        // The stale swap REPLACES the expiry segment.
        composeRule.onAllNodesWithText("· Expires", substring = true).assertCountEquals(0)
    }

    @Test
    fun everyRowHasAccessibleOverflowButton() {
        val repository = RoomVoucherRepository(database)
        runBlocking {
            repository.insert(
                voucher("a10", "Link Ten", ValidityStatus.ACTIVE, LocalDate.now().plusDays(10)),
            )
            repository.insert(
                voucher("u1", "u.html", ValidityStatus.UNVERIFIED, null, lastRefreshError = "NETWORK_ERROR"),
            )
        }

        composeRule.setContent {
            AppTheme(mode = ThemeMode.LIGHT) {
                VoucherListScreen(
                    repository = repository,
                    extractionCoordinator = ExtractionCoordinator(repository, ExtractionEngine()),
                    onAddClick = {},
                    onOpenVoucher = {},
                    onArchivedClick = {}, onSettingsClick = {}, onAboutClick = {},
                    languageStore = LanguageStore(appContext),
                    onLanguageSelected = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("More options for Link Ten")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More options for u.html")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun tapOpensVoucherAndOverflowButtonOpensMenu() {
        val repository = RoomVoucherRepository(database)
        runBlocking {
            repository.insert(
                voucher("a10", "Link Ten", ValidityStatus.ACTIVE, LocalDate.now().plusDays(10)),
            )
            repository.insert(
                voucher("a40", "Link Forty", ValidityStatus.ACTIVE, LocalDate.now().plusDays(40)),
            )
        }
        var openedId: String? = null

        composeRule.setContent {
            AppTheme(mode = ThemeMode.LIGHT) {
                VoucherListScreen(
                    repository = repository,
                    extractionCoordinator = ExtractionCoordinator(repository, ExtractionEngine()),
                    onAddClick = {},
                    onOpenVoucher = { openedId = it.id },
                    onArchivedClick = {}, onSettingsClick = {}, onAboutClick = {},
                    languageStore = LanguageStore(appContext),
                    onLanguageSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Link Ten").performClick()
        assertEquals("a10", openedId)

        composeRule.onNodeWithContentDescription("More options for Link Forty")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Archive").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun emptyListShowsPrompt() {
        val repository = RoomVoucherRepository(database)
        composeRule.setContent {
            AppTheme(mode = ThemeMode.LIGHT) {
                VoucherListScreen(
                    repository = repository,
                    extractionCoordinator = ExtractionCoordinator(repository, ExtractionEngine()),
                    onAddClick = {},
                    onOpenVoucher = {},
                    onArchivedClick = {}, onSettingsClick = {}, onAboutClick = {},
                    languageStore = LanguageStore(appContext),
                    onLanguageSelected = {},
                )
            }
        }
        composeRule.onNodeWithText("No voucher links yet. Add one with the + button.")
            .assertIsDisplayed()
    }

    @Test
    fun addVoucherRowTriggersAddClick() {
        val repository = RoomVoucherRepository(database)
        var addClicked = false
        composeRule.setContent {
            AppTheme(mode = ThemeMode.LIGHT) {
                VoucherListScreen(
                    repository = repository,
                    extractionCoordinator = ExtractionCoordinator(repository, ExtractionEngine()),
                    onAddClick = { addClicked = true },
                    onOpenVoucher = {},
                    onArchivedClick = {}, onSettingsClick = {}, onAboutClick = {},
                    languageStore = LanguageStore(appContext),
                    onLanguageSelected = {},
                )
            }
        }
        // The redesign's dashed "Add Voucher" row replaces the FAB: tapping it
        // must route to the add flow (regression guard - the row previously
        // rendered without a clickable).
        composeRule.onNodeWithText("Add Voucher").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(addClicked)
    }

    @Test
    fun hamburgerOpensDrawerWithSettingsAndAboutEntries() {
        val repository = RoomVoucherRepository(database)
        var settingsClicked = false
        var aboutClicked = false
        composeRule.setContent {
            AppTheme(mode = ThemeMode.LIGHT) {
                VoucherListScreen(
                    repository = repository,
                    extractionCoordinator = ExtractionCoordinator(repository, ExtractionEngine()),
                    onAddClick = {},
                    onOpenVoucher = {},
                    onArchivedClick = {},
                    onSettingsClick = { settingsClicked = true },
                    onAboutClick = { aboutClicked = true },
                    languageStore = LanguageStore(appContext),
                    onLanguageSelected = {},
                )
            }
        }
        // The hamburger (top-left) opens the navigation drawer; the gear is
        // no longer in the top bar - Settings and About App live in the drawer.
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Dark Mode").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("About App").assertIsDisplayed()
        composeRule.onNodeWithText("About App").performClick()
        composeRule.waitForIdle()
        assertTrue(aboutClicked)
        assertFalse(settingsClicked)
    }

    @Test
    fun allSixBadgeStatesAnnounceTheirLabelsThroughMergedCardSemantics() {
        // Refactor D11: TalkBack reads the CARD's merged clickable semantics
        // (title + badge + expiry), so each badge state must surface its
        // label inside that merged node - not just as visible text.
        val repository = RoomVoucherRepository(database)
        val today = LocalDate.now()
        runBlocking {
            repository.insert(
                voucher("u", "U Card", ValidityStatus.UNVERIFIED, null, lastRefreshError = "NETWORK_ERROR"),
            )
            repository.insert(
                voucher(
                    "n", "N Card", ValidityStatus.NOT_STARTED, today.plusDays(5),
                    listOf(CategoryBalance("heartland", BigDecimal("5"))),
                ),
            )
            repository.insert(
                voucher("e", "E Card", ValidityStatus.EXPIRED, today.minusDays(1), emptyList()),
            )
            repository.insert(
                voucher("z", "Z Card", ValidityStatus.ACTIVE, today.plusDays(100), emptyList()),
            )
            repository.insert(
                voucher(
                    "s", "S Card", ValidityStatus.ACTIVE, today.plusDays(10),
                    listOf(CategoryBalance("heartland", BigDecimal("50"))),
                ),
            )
            repository.insert(
                voucher(
                    "f", "F Card", ValidityStatus.ACTIVE, today.plusDays(40),
                    listOf(CategoryBalance("heartland", BigDecimal("50"))),
                ),
            )
        }

        composeRule.setContent {
            AppTheme(mode = ThemeMode.LIGHT) {
                VoucherListScreen(
                    repository = repository,
                    extractionCoordinator = ExtractionCoordinator(repository, ExtractionEngine()),
                    onAddClick = {},
                    onOpenVoucher = {},
                    onArchivedClick = {}, onSettingsClick = {}, onAboutClick = {},
                    languageStore = LanguageStore(appContext),
                    onLanguageSelected = {},
                )
            }
        }

        // Each label is announced through a merged, clickable card node; rows
        // below the fold are scrolled to first (LazyColumn composes lazily).
        composeRule.onNode(hasText("Tap to check") and hasClickAction())
            .assertIsDisplayed()
        composeRule.onNode(hasText("Not started") and hasClickAction()).assertIsDisplayed()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Expired") and hasClickAction())
        composeRule.onNode(hasText("Expired") and hasClickAction()).assertIsDisplayed()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Fully used") and hasClickAction())
        composeRule.onNode(hasText("Fully used") and hasClickAction()).assertIsDisplayed()
        // SOON + FINE both carry the days-left plural label on their cards.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("days left", substring = true) and hasClickAction())
        composeRule.onAllNodes(hasText("days left", substring = true) and hasClickAction())
            .assertCountEquals(2)

        // Custom header controls keep their accessibility identities.
        composeRule.onNodeWithContentDescription("Menu").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Select language").assertIsDisplayed()
        // The Archived pill announces its count through its merged semantics.
        composeRule.onNode(hasText("Archived") and hasClickAction()).assertIsDisplayed()
    }

    private fun assertTopToBottomOrder(vararg texts: String) {
        val positions = texts.map { text ->
            composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        }
        positions.zipWithNext().forEachIndexed { index, (above, below) ->
            assertTrue(
                "'${texts[index]}' should render above '${texts[index + 1]}'",
                above < below,
            )
        }
    }

    // ---- pin / unpin (main list only) ----

    private fun listContent(repository: RoomVoucherRepository) {
        composeRule.setContent {
            AppTheme(mode = ThemeMode.LIGHT) {
                VoucherListScreen(
                    repository = repository,
                    extractionCoordinator = ExtractionCoordinator(repository, ExtractionEngine()),
                    onAddClick = {},
                    onOpenVoucher = {},
                    onArchivedClick = {}, onSettingsClick = {}, onAboutClick = {},
                    languageStore = LanguageStore(appContext),
                    onLanguageSelected = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun kebabShowsPinAsSecondItemAndUnpinForPinnedRow() {
        val repository = RoomVoucherRepository(database)
        val today = LocalDate.now()
        runBlocking {
            repository.insert(voucher("p1", "Pin One", ValidityStatus.ACTIVE, today.plusDays(10)))
            repository.insert(voucher("p2", "Pin Two", ValidityStatus.ACTIVE, today.plusDays(20)))
        }
        listContent(repository)

        composeRule.onNodeWithContentDescription("More options for Pin Two").performClick()
        // Second item: sits between Copy URL and Archive.
        composeRule.onNodeWithText("Copy URL").assertIsDisplayed()
        composeRule.onNodeWithText("Pin").assertIsDisplayed()
        composeRule.onNodeWithText("Archive").assertIsDisplayed()

        composeRule.onNodeWithText("Pin").performClick()
        composeRule.waitForIdle()

        // Pinned row now offers Unpin in the same slot.
        runBlocking { assertTrue(database.voucherDao().findAll().single { it.id == "p2" }.isPinned) }
        composeRule.onNodeWithContentDescription("More options for Pin Two").performClick()
        composeRule.onNodeWithText("Unpin").assertIsDisplayed()
    }

    @Test
    fun pinnedRowRendersAboveEverythingElse() {
        val repository = RoomVoucherRepository(database)
        val today = LocalDate.now()
        runBlocking {
            repository.insert(voucher("u1", "u.html", ValidityStatus.UNVERIFIED, null))
            repository.insert(voucher("top", "Pin Target", ValidityStatus.ACTIVE, today.plusDays(30)))
        }
        listContent(repository)

        composeRule.onNodeWithContentDescription("More options for Pin Target").performClick()
        composeRule.onNodeWithText("Pin").performClick()
        composeRule.waitForIdle()

        assertTopToBottomOrder("Pin Target", "u.html")
    }

    @Test
    fun pinSecondVoucherRaisesGateAndConfirmSwaps() {
        val repository = RoomVoucherRepository(database)
        val today = LocalDate.now()
        runBlocking {
            repository.insert(voucher("one", "Pin One", ValidityStatus.ACTIVE, today.plusDays(10)))
            repository.insert(voucher("two", "Pin Two", ValidityStatus.ACTIVE, today.plusDays(20)))
        }
        listContent(repository)

        // Pin the first voucher.
        composeRule.onNodeWithContentDescription("More options for Pin One").performClick()
        composeRule.onNodeWithText("Pin").performClick()
        composeRule.waitForIdle()

        // Pin a second: gate appears naming the current pin; Cancel keeps it.
        composeRule.onNodeWithContentDescription("More options for Pin Two").performClick()
        composeRule.onNodeWithText("Pin").performClick()
        composeRule.onNodeWithText("Pin this voucher?").assertIsDisplayed()
        composeRule.onNodeWithText("Pin One is pinned and will be unpinned.").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        runBlocking {
            val rows = database.voucherDao().findAll().associateBy { it.id }
            assertTrue(rows.getValue("one").isPinned)
            assertTrue(!rows.getValue("two").isPinned)
        }

        // Re-open, this time confirm: the swap unpins one and pins two.
        composeRule.onNodeWithContentDescription("More options for Pin Two").performClick()
        composeRule.onNodeWithText("Pin").performClick()
        composeRule.onNodeWithText("Pin this voucher?").assertIsDisplayed()
        // The menu closed when the gate staged, so exactly one "Pin" node
        // (the dialog's confirm) remains.
        composeRule.onNodeWithText("Pin").performClick()
        awaitDbTrue {
            val rows = database.voucherDao().findAll().associateBy { it.id }
            rows.getValue("two").isPinned && !rows.getValue("one").isPinned
        }
        awaitDbTrue {
            val rows = database.voucherDao().findAll().associateBy { it.id }
            rows.getValue("two").isPinned
        }
        awaitDbTrue {
            val rows = database.voucherDao().findAll().associateBy { it.id }
            !rows.getValue("one").isPinned
        }
    }

    /** Bounded DB poll: waits until [condition] holds, failing after ~5s.
     * Runs on the instrumentation thread (not the app main thread), so the
     * short runBlocking per poll cannot deadlock recomposition. */
    private fun awaitDbTrue(condition: suspend () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (runBlocking { condition() }) return
            android.os.SystemClock.sleep(50)
        }
        assertTrue("condition not met within 5s", runBlocking { condition() })
    }

    @Test
    fun unpinReturnsListToNormalSortOrder() {
        val repository = RoomVoucherRepository(database)
        val today = LocalDate.now()
        runBlocking {
            repository.insert(
                voucher("soon", "Link Soon", ValidityStatus.ACTIVE, today.plusDays(5)),
            )
            repository.insert(
                voucher("late", "Link Late", ValidityStatus.ACTIVE, today.plusDays(60)),
            )
        }
        listContent(repository)

        // Pin the later-expiry row: it jumps above Link Soon.
        composeRule.onNodeWithContentDescription("More options for Link Late").performClick()
        composeRule.onNodeWithText("Pin").performClick()
        composeRule.waitForIdle()
        assertTopToBottomOrder("Link Late", "Link Soon")

        // Unpin: normal soonest-expiry sort resumes. Room invalidation +
        // recomposition lag behind the click, so poll the DB deterministically
        // before asserting visual order.
        composeRule.onNodeWithContentDescription("More options for Link Late").performClick()
        composeRule.onNodeWithText("Unpin").performClick()
        awaitDbTrue {
            !database.voucherDao().findAll().single { it.id == "late" }.isPinned
        }
        composeRule.waitForIdle()
        assertTopToBottomOrder("Link Soon", "Link Late")
    }
}
