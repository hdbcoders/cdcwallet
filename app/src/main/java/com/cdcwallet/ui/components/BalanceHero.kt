@file:OptIn(ExperimentalLayoutApi::class)

package com.cdcwallet.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcwallet.R
import com.cdcwallet.data.model.CategoryBalance
import com.cdcwallet.ui.list.ListSummary
import com.cdcwallet.ui.list.formatSgd
import com.cdcwallet.ui.theme.FrauncesDisplayFontFamily
import com.cdcwallet.ui.theme.LocalAppIsDark
import com.cdcwallet.ui.theme.LocalRedesignColors
import com.cdcwallet.ui.theme.PlexMonoFontFamily
import com.cdcwallet.ui.theme.RedesignColors
import com.cdcwallet.ui.theme.categoryVisuals
import com.cdcwallet.ui.theme.rememberReduceMotion

/**
 * The redesign's balance hero (mockup): gold gradient card pinned above the
 * list. Left shows the eyebrow, the big serif total (gold `$`) and the
 * "N voucher links" meta; right shows up to three category mini-rows (tinted
 * square icon + name + mono amount). The rest is left to [+N more].
 *
 * The whole card is tappable ([onToggle]) and collapses to a compact state
 * showing just "Balance:" + the total. All text that used to ellipsize/truncate
 * (total, meta, category rows) is now FlowRow-based: items share a line while
 * they fit and wrap to their own line when they would intersect — nothing is
 * ever cut off, at any font scale.
 *
 * Responsive stacking: when the effective font scale (system × app, capped)
 * is at or above [BALANCE_STACK_THRESHOLD] (1.5), the expanded card switches
 * from the two-column layout to a stacked top+bottom one — the eyebrow sits
 * left with the total right-aligned on the same row, the "N voucher links"
 * meta goes on a second row below, and the category rows become full-width
 * beneath — so long category names never squeeze into mid-word breaks at
 * large text sizes. The collapsed state is unaffected.
 */
@Composable
fun BalanceHero(
    summary: ListSummary,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onToggle: () -> Unit = {},
) {
    val c = LocalRedesignColors.current
    val dark = LocalAppIsDark.current
    val reduceMotion = rememberReduceMotion()
    val eyebrowColor = if (dark) c.textTertiary else Color(0xFF8A6220)
    Surface(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, c.summaryBorder),
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = if (reduceMotion) tween(0) else tween(220),
            )
            .clickable(onClick = onToggle)
            .testTag("balance-hero"),
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(c.summaryStart, c.summaryEnd),
                        start = androidx.compose.ui.geometry.Offset.Zero,
                        end = androidx.compose.ui.geometry.Offset(900f, 900f),
                    ),
                )
                .padding(horizontal = 20.dp, vertical = if (collapsed) 6.dp else 18.dp),
        ) {
            if (collapsed) {
                CollapsedBalance(summary, c)
            } else {
                ExpandedBalance(summary, c, dark, eyebrowColor)
            }
        }
    }
}

/**
 * Collapsed state: "Balance:" label on the left (vertically centered across
 * the full card height) and the total right-aligned via SpaceBetween. They
 * share one row while they fit; the amount unit wraps to its own row when
 * they would intersect (large font scales) — no ellipsis, no clipping.
 *
 * Both sizes come from typography roles (label = headlineMedium, amount =
 * headlineLarge), so the amount is the dominant element and the future
 * app-wide font-size feature scales both centrally.
 */
@Composable
private fun CollapsedBalance(summary: ListSummary, c: RedesignColors) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        maxLines = 2,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle.Default) {
            Text(
                // Fixed role size (headlineMedium ≈ its current rendered
                // size) so the label no longer tracks the amount's height —
                // the amount (headlineLarge) is now the dominant element.
                text = stringResource(R.string.balance),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = PlexMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                ),
                color = c.textSecondary,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FrauncesDisplayFontFamily,
                color = c.gold,
                modifier = Modifier
                    .padding(end = 1.dp)
                    .offset(y = (-1.4).dp),
            )
            Text(
                // Typography role so the upcoming font-size feature scales
                // the amount centrally (AppTypography maps headlineLarge to
                // Fraunces Medium, 32sp) — bigger than the label by design.
                text = formatSgd(summary.total).removePrefix("$"),
                style = MaterialTheme.typography.headlineLarge,
                color = c.textPrimary,
            )
        }
    }
}

/** Effective font scale at which the expanded hero switches from the
 *  two-column (left + right) mockup layout to a stacked (top + bottom)
 *  layout, so the categories keep full width at large text sizes. */
internal const val BALANCE_STACK_THRESHOLD = 1.5f

/** Pure decision: stack the expanded hero when the effective font scale
 *  (system × app, capped) is at or above [BALANCE_STACK_THRESHOLD]. */
internal fun shouldStackBalanceHero(effectiveFontScale: Float): Boolean =
    effectiveFontScale >= BALANCE_STACK_THRESHOLD

@Composable
private fun ExpandedBalance(
    summary: ListSummary,
    c: RedesignColors,
    dark: Boolean,
    eyebrowColor: Color,
) {
    // Effective font scale = system × app, capped (AppTheme provides it via
    // LocalDensity). Above the threshold the hero stacks so the category
    // rows get full width; below it the mockup's two-column layout is kept.
    val stacked = shouldStackBalanceHero(LocalDensity.current.fontScale)
    Crossfade(
        targetState = stacked,
        animationSpec = if (rememberReduceMotion()) tween(0) else tween(220),
        label = "balance-hero-layout",
    ) { isStacked ->
        if (isStacked) {
            // Top + bottom: balance block spans the card; categories below.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BalanceBlock(summary, c, eyebrowColor, stacked = true)
                CategoriesBlock(
                    summary = summary,
                    c = c,
                    dark = dark,
                    moreAlignment = Alignment.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            // Left + right (mockup): balance left, categories right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BalanceBlock(
                    summary, c, eyebrowColor,
                    stacked = false,
                    modifier = Modifier.weight(1f),
                )
                CategoriesBlock(
                    summary = summary,
                    c = c,
                    dark = dark,
                    moreAlignment = Alignment.End,
                    modifier = Modifier
                        .weight(1.6f)
                        .padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * The balance half of the expanded hero: eyebrow, total amount, and the
 * "N voucher links" meta. Two-column mode ([stacked] = false) keeps today's
 * left column (all three stacked, left-aligned). Stacked mode spans the
 * card: the eyebrow sits left with the amount right-aligned on the same
 * row, and the meta in a second row below the eyebrow.
 */
@Composable
private fun BalanceBlock(
    summary: ListSummary,
    c: RedesignColors,
    eyebrowColor: Color,
    stacked: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (stacked) {
            // Row 1: eyebrow left, amount right (they wrap to separate rows
            // only if they would collide at extreme sizes).
            FlowRow(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                itemVerticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Eyebrow(eyebrowColor)
                Amount(summary, c)
            }
            // Row 2: meta below the eyebrow.
            MetaRow(summary, c)
        } else {
            Eyebrow(eyebrowColor)
            Amount(summary, c)
            MetaRow(summary, c)
        }
    }
}

/** The "REMAINING BALANCE" eyebrow. 9.5sp + 1.0sp tracking (mockup was
 *  10.5sp/1.6sp): at large app font scales the monospace glyphs plus wide
 *  tracking overflow narrow columns and break mid-word; the smaller base
 *  keeps the label wrapping at word boundaries while looking identical at 1×. */
@Composable
private fun Eyebrow(eyebrowColor: Color) {
    Text(
        text = stringResource(R.string.remaining_balance).uppercase(),
        fontFamily = PlexMonoFontFamily,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.0.sp,
        color = eyebrowColor,
    )
}

/** The total amount: `$` and digits as separate FlowRow items so an
 *  over-wide number wraps below the `$` instead of ellipsizing. */
@Composable
private fun Amount(summary: ListSummary, c: RedesignColors) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        itemVerticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "$",
            fontSize = 21.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FrauncesDisplayFontFamily,
            color = c.gold,
            modifier = Modifier
                .padding(end = 2.dp)
                .offset(y = (-1.9).dp),
        )
        Text(
            text = formatSgd(summary.total).removePrefix("$"),
            fontSize = 42.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FrauncesDisplayFontFamily,
            color = c.textPrimary,
            lineHeight = 42.sp,
            letterSpacing = (-0.8).sp,
            // Optical nudge: the Fraunces '$' draws its stem ~4px below its
            // body, so the digits are shifted down ~12px to sit on the '$'s
            // line — reads as aligned.
            modifier = Modifier.offset(y = 4.6.dp),
        )
    }
}

/** The "N voucher links" meta line: icon + text as FlowRow items so the
 *  text wraps instead of ellipsizing. */
@Composable
private fun MetaRow(summary: ListSummary, c: RedesignColors) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Icon(
            Icons.Outlined.Link,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = c.textSecondary.copy(alpha = 0.7f),
        )
        Text(
            text = pluralStringResource(
                R.plurals.hero_links,
                summary.linkCount,
                summary.linkCount,
            ),
            fontSize = 12.sp,
            color = c.textSecondary,
        )
    }
}

/**
 * The category half of the expanded hero: the top-3 category mini-rows by
 * value plus a "+N more" line. Two-column mode is the right column with the
 * "+N more" aligned to the card's end; stacked mode lays full-width rows
 * with the "+N more" aligned to the side of [moreAlignment].
 */
@Composable
private fun CategoriesBlock(
    summary: ListSummary,
    c: RedesignColors,
    dark: Boolean,
    moreAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = moreAlignment,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        val top = summary.categoryTotals
            .sortedByDescending { it.remainingValue }
            .take(3)
        top.forEach { balance -> CategoryMiniRow(balance, dark) }
        val hidden = summary.categoryTotals.size - top.size
        if (hidden > 0) {
            Text(
                text = stringResource(R.string.more_categories, hidden),
                fontFamily = PlexMonoFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = c.textTertiary,
            )
        }
    }
}

/**
 * Category mini row as a FlowRow: the [icon + name] unit and the amount unit
 * share one line (name left, amount right via SpaceBetween) while they fit;
 * the amount wraps to its own line below the name when they would intersect.
 * Neither unit is ever truncated.
 */
@Composable
private fun CategoryMiniRow(balance: CategoryBalance, dark: Boolean) {
    val c = LocalRedesignColors.current
    val visuals = categoryVisuals(balance.category, dark)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        maxLines = 2,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(21.6.dp)
                    .background(visuals.color, RoundedCornerShape(7.2.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    visuals.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = balance.category,
                fontSize = 13.2.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textSecondary,
            )
        }
        Text(
            text = formatSgd(balance.remainingValue),
            fontFamily = FrauncesDisplayFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.textPrimary,
        )
    }
}
