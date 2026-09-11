@file:OptIn(ExperimentalLayoutApi::class)

package com.hdbcoders.cdcwallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.data.model.CategoryBalance
import com.hdbcoders.cdcwallet.ui.list.ListSummary
import com.hdbcoders.cdcwallet.ui.list.formatSgd
import com.hdbcoders.cdcwallet.ui.theme.AppTypefaces
import com.hdbcoders.cdcwallet.ui.theme.CategoryVisuals
import com.hdbcoders.cdcwallet.ui.theme.LocalAppLanguage
import com.hdbcoders.cdcwallet.ui.theme.LocalAppTypefaces
import com.hdbcoders.cdcwallet.ui.theme.LocalRedesignColors
import com.hdbcoders.cdcwallet.ui.theme.RedesignColors
import com.hdbcoders.cdcwallet.ui.theme.categoryVisuals
import com.hdbcoders.cdcwallet.ui.theme.localizeCategory

/**
 * The category half of the expanded hero: the top-3 category mini-rows by
 * value, one per row with 8dp spacing. Callers place the "+N more" line
 * ([MoreCategoriesLine]) separately, because in the two-column layout it
 * hangs below the row that the balance amount aligns to.
 */
@Composable
internal fun CategoryRows(
    summary: ListSummary,
    c: RedesignColors,
    dark: Boolean,
    stacked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        summary.categoryTotals
            .sortedByDescending { it.remainingValue }
            .take(3)
            .forEach { balance -> CategoryMiniRow(balance, dark, stacked) }
    }
}

/** The "+N more" line shown under the top-3 rows when more categories exist
 *  than fit. Compact: an explicit tight [lineHeight] (the ambient body
 *  lineHeight would otherwise reserve nearly a full category row's height for
 *  a 10sp label) and a small top gap. [textAlign] right-aligns it at the
 *  lower right of the card in both the two-column and the stacked layouts. */
@Composable
internal fun MoreCategoriesLine(
    summary: ListSummary,
    c: RedesignColors,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    val hidden = summary.categoryTotals.size - 3
    if (hidden > 0) {
        val typefaces = LocalAppTypefaces.current
        Text(
            text = stringResource(R.string.more_categories, hidden),
            fontFamily = typefaces.mono,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            // textSecondary, not textTertiary: at 10sp tertiary sits at
            // 3.3:1 in dark (2.9 on raised), below AA-small.
            color = c.textSecondary,
            textAlign = textAlign,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/**
 * Category mini row: the [icon + name] unit and the amount unit share one
 * line (name left, amount right via SpaceBetween) while they fit. In the
 * stacked hero ([stacked] = true) the amount must never land left-aligned on
 * an overflow row: a multi-word name wraps word-safely (no mid-word breaks)
 * with the amount sharing its last line, right-aligned; a single-word name -
 * or a word forced to break mid-word - keeps the amount right-aligned on its
 * own row below. The two-column hero keeps the legacy FlowRow behavior
 * ([stacked] = false). Neither unit is ever truncated.
 */
@Composable
internal fun CategoryMiniRow(
    balance: CategoryBalance,
    dark: Boolean,
    stacked: Boolean = false,
) {
    val c = LocalRedesignColors.current
    val typefaces = LocalAppTypefaces.current
    val visuals = categoryVisuals(balance.category)
    val name = localizeCategory(balance.category, LocalAppLanguage.current)
    val amount = formatSgd(balance.remainingValue)
    if (!stacked) {
        CategoryFlowRow(name, amount, visuals, typefaces, c)
        return
    }
    // Stacked rendering, measurement-driven (same TextMeasurer pattern as
    // ExpandedBalance): decide fit vs overflow up front, so the amount is
    // always right-aligned in every outcome.
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val nameStyle = LocalTextStyle.current.copy(
        fontSize = 13.2.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val amountStyle = TextStyle(
        fontFamily = typefaces.display,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val iconAndGapPx = with(density) { (21.6.dp + 6.dp).toPx() }
        val nameWidthPx = textMeasurer
            .measure(AnnotatedString(name), nameStyle).size.width.toFloat()
        val amountWidthPx = textMeasurer
            .measure(AnnotatedString(amount), amountStyle).size.width.toFloat()
        // Height of ONE rendered text line, measured with the actual name so
        // the fallback font (e.g. Tamil) determines the metrics. The icon
        // centers against the FIRST line's line box - the single-line
        // convention. Top-aligning the icon to the block top would float it
        // above the glyphs, because fallback fonts carry ascent padding
        // inside the line box.
        val firstLineHeightPx = textMeasurer
            .measure(AnnotatedString(name), nameStyle, maxLines = 1)
            .size.height.toFloat()
        val iconBoxModifier = Modifier.height(with(density) { firstLineHeightPx.toDp() })
        val contentWidthPx = with(density) { maxWidth.toPx() }
        // 2dp safety buffer: the FlowRow wraps a hair before the arithmetic
        // sum (same buffer as the hero's own balance-row measurement).
        val fits = iconAndGapPx + nameWidthPx + amountWidthPx +
            with(density) { 2.dp.toPx() } <= contentWidthPx
        if (fits) {
            CategoryFlowRow(name, amount, visuals, typefaces, c)
        } else if (name.split(Regex("\\s+")).size > 1) {
            // Rule 1: multi-word name wraps word-safely (no maxLines cap);
            // the amount shares the name's LAST line, right-aligned.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    // Top-align the icon box with the name block; the box is
                    // one line tall and centers the icon, so the icon sits
                    // beside the name's FIRST line (matching single-line rows)
                    // whatever the fallback font's ascent padding.
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = iconBoxModifier, contentAlignment = Alignment.Center) {
                        CategoryIcon(visuals)
                    }
                    Text(
                        text = name,
                        style = nameStyle,
                        color = c.textSecondary,
                    )
                }
                Text(
                    text = amount,
                    style = amountStyle,
                    color = c.textPrimary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        } else {
            // Rules 2/3: single-word name stays on line 1 (breaking mid-word
            // only if the word itself exceeds the row width); the amount goes
            // to its own row directly below, right-aligned.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    // Same first-line centering as rule 1: the box is one line
                    // tall, so a word forced to break mid-word (rule 3) still
                    // keeps the icon beside the first line.
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = iconBoxModifier, contentAlignment = Alignment.Center) {
                        CategoryIcon(visuals)
                    }
                    Text(
                        text = name,
                        style = nameStyle,
                        color = c.textSecondary,
                    )
                }
                Text(
                    text = amount,
                    style = amountStyle,
                    color = c.textPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** The legacy one-line category row: FlowRow with SpaceBetween (name left,
 *  amount right) and a 2-line cap. Used by the two-column hero and by the
 *  stacked hero whenever everything fits on one line. */
@Composable
internal fun CategoryFlowRow(
    name: String,
    amount: String,
    visuals: CategoryVisuals,
    typefaces: AppTypefaces,
    c: RedesignColors,
) {
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
            CategoryIcon(visuals)
            Text(
                text = name,
                fontSize = 13.2.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textSecondary,
            )
        }
        Text(
            text = amount,
            fontFamily = typefaces.display,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.textPrimary,
        )
    }
}

/** The category's tinted square icon. Glyph uses [RedesignColors.onFill]:
 *  white in light (fills are deepened for it), ink in dark (lifted fills
 *  drop white to 2.2-2.8:1). */
@Composable
internal fun CategoryIcon(visuals: CategoryVisuals) {
    val c = LocalRedesignColors.current
    Box(
        modifier = Modifier
            .size(21.6.dp)
            .background(visuals.color, RoundedCornerShape(7.2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            visuals.icon,
            contentDescription = null,
            tint = c.onFill,
            modifier = Modifier.size(12.dp),
        )
    }
}
