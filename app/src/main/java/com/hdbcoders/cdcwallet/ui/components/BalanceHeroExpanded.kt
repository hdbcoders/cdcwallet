@file:OptIn(ExperimentalLayoutApi::class)

package com.hdbcoders.cdcwallet.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.ui.list.ListSummary
import com.hdbcoders.cdcwallet.ui.list.formatSgd
import com.hdbcoders.cdcwallet.ui.theme.LocalAppLanguage
import com.hdbcoders.cdcwallet.ui.theme.LocalAppTypefaces
import com.hdbcoders.cdcwallet.ui.theme.LocalRedesignColors
import com.hdbcoders.cdcwallet.ui.theme.RedesignColors
import com.hdbcoders.cdcwallet.ui.theme.localizeCategory
import com.hdbcoders.cdcwallet.ui.theme.rememberReduceMotion

/**
 * Responsive two-column sizing + stacking (replaces the old font-scale
 * threshold): the balance column is sized to exactly fit its `$`+total row
 * (plus a small safety buffer), and the category column gets the remainder
 * of the card. The expanded card keeps the two-column (left + right) mockup
 * layout while every category row fits in that remainder; as soon as ANY
 * category row would intersect with its own balance - i.e. [icon + name +
 * amount] needs more horizontal room than the category column provides -
 * the card stacks (top + bottom) so every row gets full card width. In the
 * two-column layout the balance block stretches to the height of the three
 * category rows, so the `$`+total bottom sits level with the 3rd category
 * row (the "+N more" line, when present, hangs below the row). Nothing is
 * ever cut off or split mid-word at any font scale.
 */
internal fun shouldStackBalanceHero(
    categoryColumnWidthPx: Float,
    categoryRowRequiredWidthsPx: List<Float>,
): Boolean = categoryRowRequiredWidthsPx.any { it > categoryColumnWidthPx }

@Composable
internal fun ExpandedBalance(
    summary: ListSummary,
    c: RedesignColors,
    dark: Boolean,
    eyebrowColor: Color,
) {
    val density = LocalDensity.current
    val typefaces = LocalAppTypefaces.current
    val textMeasurer = rememberTextMeasurer()
    // Measure with the same effective styles CategoryMiniRow renders with:
    // the name inherits LocalTextStyle (theme body → Inter), the amount is
    // explicitly Fraunces. TextMeasurer uses the same font resolver, so the
    // widths match what Text actually draws.
    val nameStyle = LocalTextStyle.current.copy(
        fontSize = 13.2.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val amountStyle = TextStyle(
        fontFamily = typefaces.display,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
    )
    // Balance row styles - mirror the Amount composable exactly, so the
    // measured `$`+total width matches what Text actually draws.
    val dollarStyle = TextStyle(
        fontFamily = typefaces.display,
        fontSize = 21.sp,
        fontWeight = FontWeight.Medium,
    )
    val totalStyle = TextStyle(
        fontFamily = typefaces.display,
        fontSize = 42.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.8).sp,
    )
    // Eyebrow style - mirror the Eyebrow composable exactly (two-column size
    // 12.sp, mono family, SemiBold, 1.0.sp tracking), so the measured width
    // matches what Text actually draws.
    val eyebrowStyle = TextStyle(
        fontFamily = typefaces.mono,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.0.sp,
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacingPx = with(density) { 14.dp.toPx() }
        val iconAndGapPx = with(density) { (21.6.dp + 6.dp).toPx() }
        val contentWidthPx = with(density) { maxWidth.toPx() }
        val categoryRowWidthsPx = summary.categoryTotals
            .sortedByDescending { it.remainingValue }
            .take(3)
            .map { balance ->
                // Measure the DISPLAYED (localized) name - the geometric
                // stacking decision must match what CategoryMiniRow renders.
                val displayedName = localizeCategory(balance.category, LocalAppLanguage.current)
                val nameWidth = textMeasurer
                    .measure(AnnotatedString(displayedName), nameStyle).size.width.toFloat()
                val amountWidth = textMeasurer
                    .measure(
                        AnnotatedString(formatSgd(balance.remainingValue)),
                        amountStyle,
                    ).size.width.toFloat()
                iconAndGapPx + nameWidth + amountWidth
            }
        // The `$` + total row, measured exactly as the Amount FlowRow lays it
        // out: `$` glyph + its 2dp end padding, then the 2dp FlowRow spacing,
        // then the digits. A 2dp safety buffer is added because the FlowRow
        // wraps a hair before the arithmetic sum of its items (observed on
        // device at default font scale).
        val balanceRowWidthPx = with(density) {
            val dollarPx = textMeasurer
                .measure(AnnotatedString("$"), dollarStyle).size.width.toFloat() + 2.dp.toPx()
            val digitsPx = textMeasurer
                .measure(
                    AnnotatedString(formatSgd(summary.total).removePrefix("$")),
                    totalStyle,
                ).size.width.toFloat()
            dollarPx + 2.dp.toPx() + digitsPx + 2.dp.toPx()
        }
        // Responsive two-column: the balance column must fit BOTH the
        // `$`+total row and the "BALANCE" eyebrow above it. The eyebrow is
        // wider than the amount at large font scales in the dyslexia fonts
        // (wider glyphs + synthesized SemiBold + 1.0sp tracking), so the
        // column takes the wider of the two - if that squeezes the category
        // column, shouldStackBalanceHero stacks instead. The eyebrow never
        // wraps to a second line.
        val eyebrowWidthPx = textMeasurer
            .measure(
                AnnotatedString(stringResource(R.string.balance_eyebrow).uppercase()),
                eyebrowStyle,
            ).size.width.toFloat()
        val balanceColumnWidthPx = max(balanceRowWidthPx, eyebrowWidthPx)
        val categoryColumnWidthPx = contentWidthPx - spacingPx - balanceColumnWidthPx
        val stacked = shouldStackBalanceHero(categoryColumnWidthPx, categoryRowWidthsPx)
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
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CategoryRows(summary, c, dark, stacked = true)
                        // Same convention as the two-column layout: the
                        // "+N more" line sits at the lower right of the card.
                        MoreCategoriesLine(summary, c, TextAlign.End)
                    }
                }
            } else {
                // Left + right (mockup): balance left, categories right. The
                // columns are sized to their content (not fixed weights), so
                // `$` + total always share one line while categories keep the
                // remainder. The row is as tall as the three category rows and
                // the balance block stretches to it, so the `$`+total bottom
                // sits level with the 3rd category row; the compact "+N more"
                // line hangs below the row.
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        BalanceBlock(
                            summary, c, eyebrowColor,
                            stacked = false,
                            modifier = Modifier
                                .width(with(density) { balanceColumnWidthPx.toDp() })
                                .fillMaxHeight(),
                        )
                        CategoryRows(
                            summary, c, dark,
                            modifier = Modifier
                                .width(with(density) { categoryColumnWidthPx.toDp() })
                                .padding(top = 2.dp),
                        )
                    }
                    MoreCategoriesLine(
                        summary, c, TextAlign.End,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * The balance half of the expanded hero: the eyebrow and the total amount.
 * Two-column mode ([stacked] = false) keeps the eyebrow at the top and
 * stretches the column (the caller fills the row height), so the amount
 * bottom-aligns with the 3rd category row. Stacked mode spans the card: the
 * eyebrow sits left with the amount right-aligned on the same row.
 */
@Composable
internal fun BalanceBlock(
    summary: ListSummary,
    c: RedesignColors,
    eyebrowColor: Color,
    stacked: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = if (stacked) Arrangement.Top else Arrangement.SpaceBetween,
    ) {
        if (stacked) {
            // Eyebrow left, amount right (they wrap to separate rows only
            // if they would collide at extreme sizes).
            FlowRow(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                itemVerticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 18sp: the stacked row pairs the label with the 42sp amount,
                // so it needs to hold its own beside the number.
                Eyebrow(eyebrowColor, 18.sp)
                Amount(summary, c)
            }
        } else {
            Eyebrow(eyebrowColor, 12.sp)
            Amount(summary, c)
        }
    }
}

/** The "BALANCE" eyebrow (uppercase, Plex Mono) with 1.0sp tracking. The
 *  size varies by layout: the stacked mode shares a row with the 42sp
 *  amount, so it uses the larger [fontSize] (18sp) to hold its own beside
 *  the number; the two-column mode keeps the smaller 12sp eyebrow above the
 *  amount. */
@Composable
internal fun Eyebrow(eyebrowColor: Color, fontSize: TextUnit) {
    val typefaces = LocalAppTypefaces.current
    Text(
        text = stringResource(R.string.balance_eyebrow).uppercase(),
        fontFamily = typefaces.mono,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.0.sp,
        color = eyebrowColor,
    )
}

/** The total amount: `$` and digits as separate FlowRow items so an
 *  over-wide number wraps below the `$` instead of ellipsizing. */
@Composable
internal fun Amount(summary: ListSummary, c: RedesignColors) {
    val typefaces = LocalAppTypefaces.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        itemVerticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "$",
            fontSize = 21.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = typefaces.display,
            color = c.heroAccentText,
            modifier = Modifier
                .padding(end = 2.dp)
                .offset(y = (-1.9).dp),
        )
        Text(
            text = formatSgd(summary.total).removePrefix("$"),
            fontSize = 42.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = typefaces.display,
            color = c.textPrimary,
            lineHeight = 42.sp,
            letterSpacing = (-0.8).sp,
            // Optical nudge: the Fraunces '$' draws its stem ~4px below its
            // body, so the digits are shifted down ~12px to sit on the '$'s
            // line - reads as aligned.
            modifier = Modifier.offset(y = 4.6.dp),
        )
    }
}
