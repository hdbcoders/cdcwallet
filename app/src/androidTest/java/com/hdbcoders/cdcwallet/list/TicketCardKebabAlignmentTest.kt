package com.hdbcoders.cdcwallet.list

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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hdbcoders.cdcwallet.data.model.CategoryBalance
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.ui.components.TicketCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

/**
 * Kebab first-line alignment (spec 04 redesign): the ⋮ kebab is its own
 * element overlaid on the card top-right, and the campaign name's FIRST line
 * is vertically centered in the same 48dp title band as the kebab icon.
 * The center of the first line box (from the real TextLayoutResult) must
 * equal the kebab icon's center at EVERY font scale - growing the font must
 * never let the name drift away from the kebab.
 */
@RunWith(AndroidJUnit4::class)
class TicketCardKebabAlignmentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val shortName = "Durian"
    private val longName =
        "CDC Vouchers 2026 (January) Supermarket Household Groceries & Daily Essentials Multi-Category Family Spending Programme"

    private fun voucher(name: String) = VoucherGroup(
        id = "id-$name",
        token = "tok-$name",
        url = "https://test.local/$name",
        campaignName = name,
        validityStatus = ValidityStatus.ACTIVE,
        expiryDate = LocalDate.of(2026, 8, 31),
        categoryBalances = listOf(CategoryBalance("Supermarket", BigDecimal("200.00"))),
        dateAdded = Instant.parse("2026-01-01T00:00:00Z"),
        lastRefreshedAt = Instant.parse("2026-01-01T00:00:00Z"),
        lastRefreshError = null,
    )

    /**
     * Measures the already-composed [TicketCard] (see the two scale-loop tests
     * below): the campaign name's first line center must equal the kebab
     * icon's center, within 1dp, at the given font scale.
     */
    private fun assertKebabCenteredOnFirstLine(scale: Float, name: String) {
        val nameNode = composeRule.onNodeWithText(name, useUnmergedTree = true)
            .fetchSemanticsNode()
        val nameBounds = nameNode.boundsInRoot
        val results = mutableListOf<TextLayoutResult>()
        val action = nameNode.config[SemanticsActions.GetTextLayoutResult]
        assertTrue("campaign name must expose its layout", action != null)
        action!!.action?.invoke(results)
        assertTrue("campaign name produced a layout", results.isNotEmpty())
        val layout = results.first()
        assertTrue("campaign name should wrap to at least one line", layout.lineCount >= 1)

        // The Text has ONLY a top padding (no bottom padding), so the layout
        // block's top in root = node.bottom - layout.height. First line box
        // center = layout block top + lineTop(0) + lineHeight(0)/2.
        val layoutHeight = layout.size.height
        val layoutTopInRoot = nameBounds.bottom - layoutHeight
        val lineCenter = layoutTopInRoot + layout.getLineTop(0) +
            (layout.getLineBottom(0) - layout.getLineTop(0)) / 2f

        val kebab = composeRule
            .onNodeWithContentDescription("More options for $name")
            .fetchSemanticsNode().boundsInRoot
        val kebabCenter = (kebab.top + kebab.bottom) / 2f

        val tolerancePx = with(composeRule.density) { 1.dp.toPx() }
        assertTrue(
            "first line center ($lineCenter) must equal kebab center ($kebabCenter) at font scale $scale (tolerance ${tolerancePx}px)",
            abs(lineCenter - kebabCenter) <= tolerancePx,
        )
    }

    /**
     * One composition per name shape, re-measured at every font scale: the
     * scale is Compose state, so updating it recomposes the card (and its
     * font-scaled density) without a second setContent.
     */
    private fun assertKebabAlignedAtEveryFontScale(name: String) {
        var scale by mutableStateOf(1.0f)
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale = scale),
                ) {
                    Box(modifier = Modifier.width(220.dp)) {
                        TicketCard(
                            voucher = voucher(name),
                            menuExpanded = false,
                            onMenuExpandedChange = {},
                            onClick = {},
                        ) {}
                    }
                }
            }
        }
        listOf(1.0f, 1.75f, 2.0f).forEach { s ->
            scale = s
            composeRule.waitForIdle()
            assertKebabCenteredOnFirstLine(s, name)
        }
    }

    @Test
    fun singleLineNameKebabAlignedAtEveryFontScale() =
        assertKebabAlignedAtEveryFontScale(shortName)

    @Test
    fun multiLineNameKebabAlignedAtEveryFontScale() =
        assertKebabAlignedAtEveryFontScale(longName)
}
