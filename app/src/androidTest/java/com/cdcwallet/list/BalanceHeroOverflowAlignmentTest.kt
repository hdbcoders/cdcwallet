package com.cdcwallet.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cdcwallet.data.model.CategoryBalance
import com.cdcwallet.ui.components.BalanceHero
import com.cdcwallet.ui.list.ListSummary
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import kotlin.math.abs

/**
 * Stacked-hero overflow rules: when a category row cannot fit on one line,
 * the balance amount must always end up right-aligned - on the name's last
 * line for multi-word names (rule 1), on its own row below for single-word
 * names / mid-word breaks (rules 2/3). A narrow card forces the stacked
 * layout; the fit case must stay on one line, right-aligned.
 */
@RunWith(AndroidJUnit4::class)
class BalanceHeroOverflowAlignmentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun summaryOf(vararg rows: Pair<String, String>): ListSummary = ListSummary(
        total = BigDecimal("1234.50"),
        categoryTotals = rows.map { (name, value) -> CategoryBalance(name, BigDecimal(value)) },
        linkCount = rows.size,
    )

    @Test
    fun singleWordOverflowKeepsAmountRightAlignedOnItsOwnRow() {
        // "Supercalifragilistic" (20 chars) + "$200" ≈ 209dp > the 200dp card
        // → the amount overflows to its own row; the second row ("Climate",
        // short) still fits on one line.
        val s = summaryOf(
            "Supercalifragilistic" to "200.00",
            "Climate" to "34.50",
        )
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(200.dp)) {
                    BalanceHero(summary = s, collapsed = false, onToggle = {})
                }
            }
        }
        composeRule.onNodeWithText("Supercalifragilistic", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("$200", useUnmergedTree = true).assertIsDisplayed()
        // Rule 2: name on line 1, amount on its own row below.
        assertOnSeparateLines("Supercalifragilistic", "$200")
        assertRightAligned("$200")
        assertNotTruncated("Supercalifragilistic")
        // The fitting row keeps the one-line layout (name left, amount right).
        assertSameLine("Climate", "$34.5")
        assertRightAligned("$34.5")
    }

    @Test
    fun multiWordOverflowSharesTheAmountWithTheNamesLastLine() {
        // 4-word name ≈ 297dp > the 220dp card → overflow; every word
        // ("Development", 11 chars ≈ 80dp) is narrower than the card, so the
        // wrap is word-safe by construction.
        val s = summaryOf(
            "Community Development Council Supermarket" to "200.00",
            "Climate" to "34.50",
        )
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(220.dp)) {
                    BalanceHero(summary = s, collapsed = false, onToggle = {})
                }
            }
        }
        val name = "Community Development Council Supermarket"
        composeRule.onNodeWithText(name, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("$200", useUnmergedTree = true).assertIsDisplayed()
        // Rule 1: the name wraps to 2+ lines...
        val nameLayout = layoutOf(name)
        assertTrue("name should wrap to 2+ lines, was ${nameLayout.lineCount}", nameLayout.lineCount >= 2)
        assertNotTruncated(name)
        // ...word-safely: every non-last line ends at a word boundary (the
        // wrap's space is the char right after the visible line end).
        for (line in 0 until nameLayout.lineCount - 1) {
            val end = nameLayout.getLineEnd(line, visibleEnd = true)
            assertTrue(
                "line $line must end at a word boundary, ended at '${
                    name.substring(end - 2, end).trim()
                }'",
                end < name.length && name[end] == ' ',
            )
        }
        // ...and the amount shares the LAST line, right-aligned.
        val nameBox = textNode(name)
        val amountBox = textNode("$200")
        assertTrue(
            "amount should align with the name's last line (amount.bottom=${amountBox.bottom}, name.bottom=${nameBox.bottom})",
            abs(amountBox.bottom - nameBox.bottom) < 10f,
        )
        assertRightAligned("$200")
    }

    @Test
    fun fitCaseStaysOnOneLineWithAmountRightAligned() {
        // Both rows fit the 220dp card → the legacy one-line FlowRow layout.
        val s = summaryOf(
            "Supermarket" to "200.00",
            "Climate" to "34.50",
        )
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(220.dp)) {
                    BalanceHero(summary = s, collapsed = false, onToggle = {})
                }
            }
        }
        assertSameLine("Supermarket", "$200")
        assertRightAligned("$200")
        assertSameLine("Climate", "$34.5")
    }

    /** Same line ⇒ the two nodes' vertical intervals overlap. */
    private fun assertSameLine(first: String, second: String) {
        val a = textNode(first)
        val b = textNode(second)
        assertTrue(
            "'$first' and '$second' should share a line (a.top=${a.top}, b.top=${b.top})",
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
     * The amount is right-aligned: its last (only) line's visual right edge
     * hugs the text node's right edge. For the full-width overflow-row amount
     * (rule 2) that means the glyphs end at the card's right edge; for the
     * intrinsic-width amounts (fit / rule 1) the node itself sits at the
     * right edge, so the line fills it. A left-aligned overflow row would
     * end its line far short of the node's right edge.
     */
    private fun assertRightAligned(text: String) {
        val node = textNode(text)
        val layout = layoutOf(text)
        val lineRight = layout.getLineRight(layout.lineCount - 1)
        assertTrue(
            "'$text' should be right-aligned (lineRight=$lineRight, nodeWidth=${node.width})",
            lineRight > node.width - 20f,
        )
    }

    /** Full text rendered, no ellipsis. */
    private fun assertNotTruncated(text: String) {
        val layout = layoutOf(text)
        val lastLine = layout.lineCount - 1
        assertTrue(
            "'$text' is truncated (last line ends at ${layout.getLineEnd(lastLine, visibleEnd = true)} of ${text.length})",
            layout.getLineEnd(lastLine, visibleEnd = true) == text.length,
        )
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        val action = node.config[SemanticsActions.GetTextLayoutResult]
        assertTrue("'$text' exposes no text-layout action", action != null)
        action!!.action?.invoke(results)
        assertTrue("'$text' produced no layout result", results.isNotEmpty())
        return results.first()
    }

    private fun textNode(text: String) =
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
}
