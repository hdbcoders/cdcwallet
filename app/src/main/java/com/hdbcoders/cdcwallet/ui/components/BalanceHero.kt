@file:OptIn(ExperimentalLayoutApi::class)

package com.hdbcoders.cdcwallet.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.hdbcoders.cdcwallet.data.model.CategoryBalance
import com.hdbcoders.cdcwallet.ui.list.ListSummary
import com.hdbcoders.cdcwallet.ui.list.formatSgd
import com.hdbcoders.cdcwallet.ui.theme.AppTypefaces
import com.hdbcoders.cdcwallet.ui.theme.CategoryVisuals
import com.hdbcoders.cdcwallet.ui.theme.LocalAppIsDark
import com.hdbcoders.cdcwallet.ui.theme.LocalAppLanguage
import com.hdbcoders.cdcwallet.ui.theme.LocalAppTypefaces
import com.hdbcoders.cdcwallet.ui.theme.LocalRedesignColors
import com.hdbcoders.cdcwallet.ui.theme.RedesignColors
import com.hdbcoders.cdcwallet.ui.theme.categoryVisuals
import com.hdbcoders.cdcwallet.ui.theme.localizeCategory
import com.hdbcoders.cdcwallet.ui.theme.rememberReduceMotion

/**
 * The redesign's balance hero (mockup): gold gradient card pinned above the
 * list. Left shows the eyebrow and the big serif total (gold `$`); right
 * shows up to three category mini-rows (tinted square icon + name + mono
 * amount). The rest is left to [+N more].
 *
 * The whole card is tappable ([onToggle]) and collapses to a compact state
 * showing just "Balance:" + the total. All text that used to ellipsize/truncate
 * (total, category rows) is now FlowRow-based: items share a line while
 * they fit and wrap to their own line when they would intersect - nothing is
 * ever cut off, at any font scale.
 */

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
    val eyebrowColor = if (dark) c.gold else Color(0xFF8A6220)
    // Refactor M21: expanded/collapsed state is announced to TalkBack.
    val expandStateDescription = stringResource(
        if (collapsed) R.string.hero_collapsed_desc else R.string.hero_expanded_desc,
    )
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
            .semantics { stateDescription = expandStateDescription }
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
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            if (collapsed) {
                CollapsedBalance(summary, c, eyebrowColor)
            } else if (summary.total.signum() == 0) {
                // No usable value anywhere (empty list, or everything expired
                // / fully used): the category breakdown would be blank, so
                // show a single centered message instead of an empty two
                // -column card.
                NoUsableVouchers(c)
            } else {
                ExpandedBalance(summary, c, dark, eyebrowColor)
            }
        }
    }
}

/**
 * Collapsed state: the eyebrow label on the left (vertically centered across
 * the full card height) and the total right-aligned via SpaceBetween. They
 * share one row while they fit; the amount unit wraps to its own row when
 * they would intersect (large font scales) - no ellipsis, no clipping.
 *
 * The label uses the same [eyebrowColor] as the expanded hero's eyebrow
 * (warm gold #8A6220 in light mode), so the label reads identically in both
 * card states.
 *
 * Both sizes come from typography roles (label = headlineMedium, amount =
 * headlineLarge), so the amount is the dominant element and the future
 * app-wide font-size feature scales both centrally.
 */
@Composable
private fun CollapsedBalance(
    summary: ListSummary,
    c: RedesignColors,
    eyebrowColor: Color,
) {
    val typefaces = LocalAppTypefaces.current
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
                // size) so the label no longer tracks the amount's height -
                // the amount (headlineLarge) is now the dominant element.
                text = stringResource(R.string.balance).uppercase(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = typefaces.mono,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                ),
                color = eyebrowColor,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = typefaces.display,
                color = c.gold,
                modifier = Modifier
                    .padding(end = 1.dp)
                    .offset(y = (-1.4).dp),
            )
            Text(
                // Typography role so the upcoming font-size feature scales
                // the amount centrally (AppTypography maps headlineLarge to
                // Fraunces Medium, 32sp) - bigger than the label by design.
                text = formatSgd(summary.total).removePrefix("$"),
                style = MaterialTheme.typography.headlineLarge,
                color = c.textPrimary,
            )
        }
    }
}

/**
 * Shown instead of the two-column breakdown when there is no usable value
 * across the vouchers (total == 0 — empty list, or everything expired or
 * fully used): a single centered line. The card still collapses to the
 * compact "BALANCE $0" state on tap.
 */
@Composable
private fun NoUsableVouchers(c: RedesignColors) {
    Text(
        text = stringResource(R.string.no_usable_vouchers),
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        color = c.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Stack the expanded hero when ANY category row would intersect with its own
 * balance - the [icon + name + amount] unit needs more horizontal room than
 * the category column provides (the FlowRow inside [CategoryMiniRow] would
 * wrap the amount onto a second line). The category column is the row's
 * remainder after the balance column has taken exactly what its `$`+total
 * row needs, so this also covers the case where the two columns cannot
 * coexist at all. Stacking gives every row the full card width instead.
 */
internal fun shouldStackBalanceHero(
    categoryColumnWidthPx: Float,
    categoryRowRequiredWidthsPx: List<Float>,
): Boolean = categoryRowRequiredWidthsPx.any { it > categoryColumnWidthPx }

@Composable
private fun ExpandedBalance(
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
private fun BalanceBlock(
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
private fun Eyebrow(eyebrowColor: Color, fontSize: TextUnit) {
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
private fun Amount(summary: ListSummary, c: RedesignColors) {
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
            color = c.gold,
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

/**
 * The category half of the expanded hero: the top-3 category mini-rows by
 * value, one per row with 8dp spacing. Callers place the "+N more" line
 * ([MoreCategoriesLine]) separately, because in the two-column layout it
 * hangs below the row that the balance amount aligns to.
 */
@Composable
private fun CategoryRows(
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
private fun MoreCategoriesLine(
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
            color = c.textTertiary,
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
private fun CategoryMiniRow(
    balance: CategoryBalance,
    dark: Boolean,
    stacked: Boolean = false,
) {
    val c = LocalRedesignColors.current
    val typefaces = LocalAppTypefaces.current
    val visuals = categoryVisuals(balance.category, dark)
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
private fun CategoryFlowRow(
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

/** The category's tinted square icon. */
@Composable
private fun CategoryIcon(visuals: CategoryVisuals) {
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
}
