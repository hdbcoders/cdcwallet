package com.cdcvouchers.list

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cdcvouchers.data.RoomVoucherRepository
import com.cdcvouchers.data.db.AppDatabase
import com.cdcvouchers.data.db.SqlCipherNative
import com.cdcvouchers.data.model.CategoryBalance
import com.cdcvouchers.data.model.ValidityStatus
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.extraction.ExtractionCoordinator
import com.cdcvouchers.extraction.ExtractionEngine
import com.cdcvouchers.ui.list.VoucherListScreen
import com.cdcvouchers.ui.theme.LanguageStore
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
            MaterialTheme {
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
        composeRule.onNodeWithText("Couldn't verify, tap to check").assertIsDisplayed()
        composeRule.onNodeWithText("Expired").assertIsDisplayed()
        composeRule.onNodeWithText("Not started").assertIsDisplayed()

        // Spec 04 §4.3: total excludes the UNVERIFIED entry from value and count.
        // The hero renders the eyebrow in uppercase and splits "$" from the
        // amount into separate Text nodes (mockup), so assert per-node.
        composeRule.onNodeWithText("REMAINING BALANCE").assertIsDisplayed()
        composeRule.onNodeWithText("92.5").assertIsDisplayed()
        composeRule.onNodeWithText("4 voucher links").assertIsDisplayed()
        // Category breakdown rows (top 3 by value) render in the right column.
        // Use onFirst() because category names also appear on voucher cards.
        for (part in listOf(
            "groceries",
            "heartland",
            "supermarket",
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
            MaterialTheme {
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
            MaterialTheme {
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
            MaterialTheme {
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
        composeRule.onNodeWithText("No voucher links yet — add one with the + button.")
            .assertIsDisplayed()
    }

    @Test
    fun addVoucherRowTriggersAddClick() {
        val repository = RoomVoucherRepository(database)
        var addClicked = false
        composeRule.setContent {
            MaterialTheme {
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
        // must route to the add flow (regression guard — the row previously
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
            MaterialTheme {
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
        // no longer in the top bar — Settings and About App live in the drawer.
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("About App").assertIsDisplayed()
        composeRule.onNodeWithText("About App").performClick()
        composeRule.waitForIdle()
        assertTrue(aboutClicked)
        assertFalse(settingsClicked)
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
}
