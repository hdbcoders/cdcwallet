package com.cdcwallet.list

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cdcwallet.data.model.CategoryBalance
import com.cdcwallet.data.model.ValidityStatus
import com.cdcwallet.data.model.VoucherGroup
import com.cdcwallet.ui.components.BalanceHero
import com.cdcwallet.ui.components.HeroCollapseStore
import com.cdcwallet.ui.components.TicketCard
import com.cdcwallet.ui.list.ListSummary
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

/**
 * Balance hero collapse + adaptive FlowRow behavior (spec 04 §4.3 change):
 * tapping the hero collapses it to a compact "BALANCE" + total card; every
 * text that used to ellipsize/truncate (total, meta, category rows, ticket
 * title) now wraps instead. The no-truncation proof is geometric: each text
 * node's TextLayoutResult must report no visual overflow, and line positions
 * are asserted via bounds overlap. Amount strings follow formatSgd's output
 * ("1,234.5" - trailing zeros stripped; category amounts include the "$").
 */
@RunWith(AndroidJUnit4::class)
class BalanceHeroAdaptiveTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    private val summary = ListSummary(
        total = BigDecimal("1234.50"),
        categoryTotals = listOf(
            CategoryBalance("Heartland", BigDecimal("1000.00")),
            CategoryBalance("Supermarket", BigDecimal("200.00")),
            CategoryBalance("Climate", BigDecimal("34.50")),
        ),
        linkCount = 3,
    )

    @Before
    fun setUp() {
        appContext.getSharedPreferences("voucher_ui_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        appContext.getSharedPreferences("voucher_ui_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun tapCollapsesAndExpandsTheHero() {
        var collapsed by mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                BalanceHero(
                    summary = summary,
                    collapsed = collapsed,
                    onToggle = { collapsed = !collapsed },
                )
            }
        }
        // Expanded: category rows are visible.
        composeRule.onNodeWithText("Heartland", useUnmergedTree = true).assertIsDisplayed()
        val expandedHeight =
            composeRule.onNodeWithTag("balance-hero").fetchSemanticsNode().boundsInRoot.height

        composeRule.onNodeWithTag("balance-hero").performClick()
        composeRule.waitForIdle()

        // Collapsed: "BALANCE" + total only; categories gone; card shorter.
        composeRule.onNodeWithText("BALANCE", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("1,234.5", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Heartland", useUnmergedTree = true).assertDoesNotExist()
        val collapsedHeight =
            composeRule.onNodeWithTag("balance-hero").fetchSemanticsNode().boundsInRoot.height
        assertTrue("collapsed hero should be shorter than expanded", collapsedHeight < expandedHeight)

        // Tap again: expanded again.
        composeRule.onNodeWithTag("balance-hero").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Heartland", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun collapsedLabelAndAmountShareOneLineAtDefaultFontScale() {
        composeRule.setContent {
            MaterialTheme {
                BalanceHero(summary = summary, collapsed = true, onToggle = {})
            }
        }
        composeRule.onNodeWithText("BALANCE", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("1,234.5", useUnmergedTree = true).assertIsDisplayed()
        assertSameLine("BALANCE", "1,234.5")
        // The amount is right-aligned to the card edge and the label is
        // vertically centered across the card's full height.
        val hero = composeRule.onNodeWithTag("balance-hero").fetchSemanticsNode().boundsInRoot
        val amount = textNode("1,234.5")
        val label = textNode("BALANCE")
        assertTrue(
            "amount should hug the card's right edge (amount.right=${amount.right}, hero.right=${hero.right})",
            amount.right > hero.right - 120f && amount.right <= hero.right,
        )
        assertTrue(
            "label should sit at the card's left edge (label.left=${label.left}, hero.left=${hero.left})",
            label.left >= hero.left && label.left < hero.left + 120f,
        )
        assertTrue(
            "label should be vertically centered in the card (labelCenterY=${label.center.y}, heroCenterY=${hero.center.y})",
            kotlin.math.abs(label.center.y - hero.center.y) < 30f,
        )
        assertTrue(
            "label should fill most of the card height (label.height=${label.height}, hero.height=${hero.height})",
            label.height > hero.height * 0.5f,
        )
        assertNotTruncated("BALANCE")
        assertNotTruncated("1,234.5")
    }

    @Test
    fun collapsedLabelAndAmountStackAtLargeFontScale() {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale = 2.5f),
                ) {
                    BalanceHero(summary = summary, collapsed = true, onToggle = {})
                }
            }
        }
        composeRule.onNodeWithText("BALANCE", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("1,234.5", useUnmergedTree = true).assertIsDisplayed()
        assertOnSeparateLines("BALANCE", "1,234.5")
        assertNotTruncated("BALANCE")
        assertNotTruncated("1,234.5")
    }

    @Test
    fun narrowCardWrapsCategoryRowsWithoutTruncation() {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(160.dp)) {
                    BalanceHero(summary = summary, collapsed = false, onToggle = {})
                }
            }
        }
        // Category name and amount are forced to separate lines - both fully visible.
        composeRule.onNodeWithText("Heartland", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("$1,000", useUnmergedTree = true).assertIsDisplayed()
        assertOnSeparateLines("Heartland", "$1,000")
        assertNotTruncated("Heartland")
        assertNotTruncated("$1,000")
        // The big total wraps rather than truncates.
        assertNotTruncated("1,234.5")
    }

    @Test
    fun expandedHeroStaysSideBySideWhenCategoryRowsFit() {
        // Short category names fit in the two-column category column, so the
        // mockup's left + right layout is kept.
        composeRule.setContent {
            MaterialTheme {
                BalanceHero(summary = summary, collapsed = false, onToggle = {})
            }
        }
        composeRule.onNodeWithText("1,234.5", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("BALANCE", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Heartland", useUnmergedTree = true).assertIsDisplayed()
        // Side-by-side: the left column's first element (eyebrow) and the
        // right column's first category row start on the same top band.
        assertSameLine("BALANCE", "Heartland")
    }

    @Test
    fun expandedHeroStacksWhenACategoryRowWouldIntersectItsBalance() {
        // A long category name needs more width than the two-column category
        // column provides → the card stacks so rows get the full width.
        val longSummary = summary.copy(
            categoryTotals = listOf(
                CategoryBalance(
                    "Community Development Council Supermarket Voucher",
                    BigDecimal("1000.00"),
                ),
                CategoryBalance("Climate", BigDecimal("234.50")),
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                BalanceHero(summary = longSummary, collapsed = false, onToggle = {})
            }
        }
        composeRule.onNodeWithText("BALANCE", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("1,234.5", useUnmergedTree = true).assertIsDisplayed()
        // Row 1: eyebrow left, amount right on the same line.
        assertSameLine("BALANCE", "1,234.5")
        val eyebrow = textNode("BALANCE")
        val amount = textNode("1,234.5")
        assertTrue(
            "amount should sit right of the eyebrow (eyebrow.right=${eyebrow.right}, amount.left=${amount.left})",
            amount.left > eyebrow.right,
        )
        // Categories below the amount (stacked).
        assertOnSeparateLines("1,234.5", "Community Development Council Supermarket Voucher")
    }

    @Test
    fun expandedHeroShowsCenteredMessageWhenTotalIsZero() {
        // Empty list / everything expired or fully used: the two-column
        // breakdown would be blank, so the expanded hero shows one centered
        // message instead of "BALANCE $0" + an empty right column.
        val zeroSummary = summary.copy(
            total = BigDecimal.ZERO,
            categoryTotals = emptyList(),
        )
        var collapsed by mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                BalanceHero(
                    summary = zeroSummary,
                    collapsed = collapsed,
                    onToggle = { collapsed = !collapsed },
                )
            }
        }
        composeRule.onNodeWithText("There are no usable vouchers", useUnmergedTree = true)
            .assertIsDisplayed()
        // No BALANCE eyebrow / no amount in this state.
        composeRule.onNodeWithText("BALANCE", useUnmergedTree = true).assertDoesNotExist()
        // The message is horizontally centered in the card.
        val message = composeRule
            .onNodeWithText("There are no usable vouchers", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val hero = composeRule.onNodeWithTag("balance-hero").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "message should be centered (message.center.x=${message.center.x}, hero.center.x=${hero.center.x})",
            kotlin.math.abs(message.center.x - hero.center.x) < 50f,
        )
        // Collapsing still works from the zero state: compact "BALANCE $0".
        composeRule.onNodeWithTag("balance-hero").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("BALANCE", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("0", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun storePersistsCollapsedStateAcrossInstances() {
        val store = HeroCollapseStore(appContext)
        assertFalse("default is expanded", store.collapsed)
        store.toggle()
        assertTrue(store.collapsed)
        // A new instance simulates an app restart: the choice persisted.
        assertTrue(HeroCollapseStore(appContext).collapsed)
    }

    @Test
    fun ticketTitleWrapsInsteadOfEllipsizing() {
        val longName =
            "CDC Vouchers 2026 Extended Community Development Council Campaign Name"
        val voucher = VoucherGroup(
            id = "id-1",
            token = "tok",
            url = "https://voucher.redeem.gov.sg/tok",
            campaignName = longName,
            validityStatus = ValidityStatus.ACTIVE,
            expiryDate = null,
            categoryBalances = listOf(CategoryBalance("Heartland", BigDecimal("50.00"))),
            dateAdded = Instant.now(),
            lastRefreshedAt = null,
            lastRefreshError = null,
        )
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(220.dp)) {
                    TicketCard(
                        voucher = voucher,
                        menuExpanded = false,
                        onMenuExpandedChange = {},
                        onClick = {},
                    ) {}
                }
            }
        }
        composeRule.onNodeWithText(longName, useUnmergedTree = true).assertIsDisplayed()
        assertNotTruncated(longName)
        // The kebab stays pinned to the card's right edge even with a long
        // wrapped name - only the campaign text wraps.
        val kebab = composeRule
            .onNodeWithContentDescription("More options for", substring = true)
            .fetchSemanticsNode().boundsInRoot
        val nameBox = composeRule.onNodeWithText(longName, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "kebab should hug the card's right edge (kebab.right=${kebab.right}, box=220dp→${with(composeRule.density) { 220.dp.toPx() }})",
            kebab.right > with(composeRule.density) { 220.dp.toPx() } - 80f,
        )
        assertTrue(
            "kebab should overlap the name's first line (kebab.top=${kebab.top}, name.top=${nameBox.top}, name.bottom=${nameBox.bottom})",
            kebab.top < nameBox.bottom && nameBox.top < kebab.bottom,
        )
    }

    /** Same FlowRow line ⇒ the two nodes' vertical intervals overlap. */
    private fun assertSameLine(first: String, second: String) {
        val a = textNode(first)
        val b = textNode(second)
        assertTrue(
            "'$first' and '$second' should share a line (a.top=${a.top}, b.top=${b.top}, a.bottom=${a.bottom})",
            b.top < a.bottom && a.top < b.bottom,
        )
    }

    /** Wrapped ⇒ the second node sits fully below the first. */
    private fun assertOnSeparateLines(first: String, second: String) {
        val a = textNode(first)
        val b = textNode(second)
        assertTrue(
            "'$second' should be below '$first' (a.bottom=${a.bottom}, b.top=${b.top})",
            b.top >= a.bottom - 1f,
        )
    }

    /**
     * The no-ellipsis proof: the last rendered line ends at the full text
     * length (getLineEnd with visibleEnd=true). An ellipsized or clipped
     * line would end earlier. (hasVisualOverflow is NOT used: its strict
     * float comparison false-positives on trailing letterSpacing.)
     */
    private fun assertNotTruncated(text: String) {
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        val action = node.config[SemanticsActions.GetTextLayoutResult]
        assertTrue("'$text' exposes no text-layout action", action != null)
        action!!.action?.invoke(results)
        assertTrue("'$text' produced no layout result", results.isNotEmpty())
        val layout = results.first()
        val lastLine = layout.lineCount - 1
        assertTrue("'$text' has no lines", lastLine >= 0)
        assertTrue(
            "'$text' is truncated (last line ends at ${layout.getLineEnd(lastLine, visibleEnd = true)} of ${text.length})",
            layout.getLineEnd(lastLine, visibleEnd = true) == text.length,
        )
    }

    private fun textNode(text: String) =
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
}
