@file:OptIn(ExperimentalLayoutApi::class)

package com.hdbcoders.cdcwallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.ui.list.ListSummary
import com.hdbcoders.cdcwallet.ui.list.formatSgd
import com.hdbcoders.cdcwallet.ui.theme.LocalAppTypefaces
import com.hdbcoders.cdcwallet.ui.theme.RedesignColors

/**
 * Collapsed state: the eyebrow label on the left (vertically centered across
 * the full card height) and the total right-aligned via SpaceBetween. They
 * share one row while they fit; the amount unit wraps to its own row when
 * they would intersect (large font scales) - no ellipsis, no clipping.
 *
 * The label uses the same [eyebrowColor] as the expanded hero's eyebrow,
 * so the label reads identically in both card states.
 *
 * Both sizes come from typography roles (label = headlineMedium, amount =
 * headlineLarge), so the amount is the dominant element and the future
 * app-wide font-size feature scales both centrally.
 */
@Composable
internal fun CollapsedBalance(
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
                color = c.heroAccentText,
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
internal fun NoUsableVouchers(c: RedesignColors) {
    Text(
        text = stringResource(R.string.no_usable_vouchers),
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        color = c.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
