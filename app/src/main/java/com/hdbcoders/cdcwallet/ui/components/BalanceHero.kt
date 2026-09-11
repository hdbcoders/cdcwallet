package com.hdbcoders.cdcwallet.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.ui.list.ListSummary
import com.hdbcoders.cdcwallet.ui.theme.LocalAppIsDark
import com.hdbcoders.cdcwallet.ui.theme.LocalRedesignColors
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
 *
 * H2 (god-file split): the three rendered states live in sibling files -
 * [CollapsedBalance] / [NoUsableVouchers] in BalanceHeroCollapsed.kt, and
 * [ExpandedBalance] with its balance block in BalanceHeroExpanded.kt, with the
 * category rows in BalanceHeroCategories.kt. This file owns the public entry
 * point and the card chrome only.
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
    // Per-palette AA-safe accent-on-gradient (eyebrow + dollar glyph; dark
    // resolves to the accent via RedesignColors.resolved()).
    val eyebrowColor = c.heroAccentText
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
