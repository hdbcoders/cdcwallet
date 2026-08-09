package com.cdcvouchers.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcvouchers.R
import com.cdcvouchers.data.model.CategoryBalance
import com.cdcvouchers.ui.list.ListSummary
import com.cdcvouchers.ui.list.formatSgd
import com.cdcvouchers.ui.theme.FrauncesDisplayFontFamily
import com.cdcvouchers.ui.theme.LocalAppIsDark
import com.cdcvouchers.ui.theme.LocalRedesignColors
import com.cdcvouchers.ui.theme.PlexMonoFontFamily
import com.cdcvouchers.ui.theme.categoryVisuals

/**
 * The redesign's balance hero (mockup): gold gradient card pinned above the
 * list. Left shows the eyebrow, the big serif total (gold `$`) and the
 * "N voucher links" meta; right shows up to three category mini-rows (tinted
 * square icon + name + mono amount). The rest is left to [+N more].
 */
@Composable
fun BalanceHero(summary: ListSummary, modifier: Modifier = Modifier) {
    val c = LocalRedesignColors.current
    val dark = LocalAppIsDark.current
    val eyebrowColor = if (dark) c.textTertiary else Color(0xFF8A6220)
    Surface(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, c.summaryBorder),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth(),
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
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Left: eyebrow + amount + meta. Grows to fill (mockup:
                // .balance-left { flex: 1 1 auto }).
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.remaining_balance).uppercase(),
                        fontFamily = PlexMonoFontFamily,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.6.sp,
                        color = eyebrowColor,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FrauncesDisplayFontFamily,
                            color = c.gold,
                            modifier = Modifier.padding(end = 2.dp),
                        )
                        Text(
                            text = formatSgd(summary.total).removePrefix("$"),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FrauncesDisplayFontFamily,
                            color = c.textPrimary,
                            lineHeight = 42.sp,
                            letterSpacing = (-0.8).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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

                // Right: category mini rows (top 3 by value). Shares the card
                // width with the balance side (reference: icon group starts
                // ~mid-card, amounts at the card's right edge). Wider than the
                // left so the scaled-up rows (27dp icon + label + amount)
                // fit on one line without wrapping.
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .padding(top = 2.dp),
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
        }
    }
}

@Composable
private fun CategoryMiniRow(balance: CategoryBalance, dark: Boolean) {
    val c = LocalRedesignColors.current
    val visuals = categoryVisuals(balance.category, dark)
    // Mockup .cat-mini: space-between — icon+name group on the left, amount
    // right-aligned to the (content-width) column's edge.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(27.dp)
                    .background(visuals.color, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    visuals.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = balance.category,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textSecondary,
                maxLines = 1,
            )
        }
        Text(
            text = formatSgd(balance.remainingValue),
            fontFamily = FrauncesDisplayFontFamily,
            fontSize = 18.75.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.textPrimary,
            maxLines = 1,
        )
    }
}
