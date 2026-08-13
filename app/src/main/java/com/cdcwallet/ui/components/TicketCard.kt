@file:OptIn(ExperimentalLayoutApi::class)

package com.cdcwallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcwallet.R
import com.cdcwallet.data.model.VoucherGroup
import com.cdcwallet.ui.list.BadgeGlyph
import com.cdcwallet.ui.list.BadgePresentation
import com.cdcwallet.ui.list.BadgeTone
import com.cdcwallet.ui.list.badgePresentation
import com.cdcwallet.ui.list.badgeState
import com.cdcwallet.ui.list.formatSgd
import com.cdcwallet.ui.theme.LocalAppIsDark
import com.cdcwallet.ui.theme.FrauncesDisplayFontFamily
import com.cdcwallet.ui.theme.LocalRedesignColors
import com.cdcwallet.ui.theme.AppScaledContent
import com.cdcwallet.ui.theme.categoryVisuals
import com.cdcwallet.ui.theme.LocalAppLanguage
import com.cdcwallet.ui.theme.localizeCampaignName
import com.cdcwallet.ui.theme.localizeCategory
import java.time.format.DateTimeFormatter

/**
 * The redesign's ticket-style voucher card (mockup): Fraunces title row with
 * the ⋮ kebab (menu content supplied by the caller), an expiry row (green
 * ✓ dot + days-left for fine, amber/red warnings for soon/urgent, red for
 * expired / fully used), a perforated dashed divider, then either the
 * category pill + amount columns or the red status banner. Tap on the body
 * is [onClick].
 */
@Composable
fun TicketCard(
    voucher: VoucherGroup,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalRedesignColors.current
    val presentation = badgePresentation(badgeState(voucher))
    val datePattern = stringResource(R.string.date_pattern)
    val locale = LocalConfiguration.current.locales[0]
    val dateText = voucher.expiryDate?.let { date ->
        DateTimeFormatter.ofPattern(datePattern, locale).format(date)
    }
    val expiryText = dateText?.let { stringResource(R.string.expires, it) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = c.surface,
        border = BorderStroke(
            1.dp,
            c.hairline,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Title row.
            // Title row: the weighted name wraps within the space beside the
            // kebab (never ellipsized, never pushing the kebab off the line -
            // the kebab stays pinned to the card's right edge).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 7.dp),
            ) {
                Text(
                    text = localizeCampaignName(voucher.campaignName, LocalAppLanguage.current),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FrauncesDisplayFontFamily,
                    color = c.textPrimary,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(
                        onClick = { onMenuExpandedChange(true) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(
                                R.string.more_options,
                                localizeCampaignName(voucher.campaignName, LocalAppLanguage.current),
                            ),
                            tint = c.textTertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { onMenuExpandedChange(false) },
                        shape = RoundedCornerShape(14.dp),
                        containerColor = c.surfaceRaised,
                        border = BorderStroke(1.dp, c.hairline),
                    ) {
                        // DropdownMenu content lives in a popup window whose
                        // density ignores the app font scale - re-apply it so
                        // the kebab menu items scale with the text-size setting.
                        AppScaledContent {
                            menuContent()
                        }
                    }
                }
            }

            // Expiry row: green ✓ + days-left for fine; warning for soon/urgent;
            // red warning + status label for expired / fully used. A failed
            // refresh (refactor M5) swaps the line for the neutral stale
            // status instead of the expiry text.
            ExpiryRow(presentation, expiryText, stale = voucher.lastRefreshError != null)

            // Perforated divider.
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp),
            ) {
                drawLine(
                    color = c.hairline,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                )
            }

            // Body: pills + amounts, or the status banner. Driven exclusively by
            // BadgeState via BadgePresentation (refactor H10, spec 04 §4.2): the
            // "no balance" banner applies only to an ACTIVE zero-balance row -
            // a UNVERIFIED / NOT_STARTED row with no extracted balances must
            // never read as "fully used".
            val bannerResId = presentation.bannerResId
            if (bannerResId != null) {
                StatusBanner(
                    text = stringResource(bannerResId),
                    neutral = presentation.bannerNeutral,
                )
            } else {
                CategoryPills(voucher, Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }
    }
}

/** Expiry row: green ✓ + days-left (fine), amber/red warning (soon/urgent),
 *  red warning + status text (expired / fully used). FlowRow: status and
 *  expiry wrap to their own lines instead of ellipsizing at large font
 *  scales. Renders exclusively from [BadgePresentation] (refactor L3). */
@Composable
private fun ExpiryRow(
    presentation: BadgePresentation,
    expiryText: String?,
    stale: Boolean = false,
) {
    val c = LocalRedesignColors.current
    val statusColor = when (presentation.tone) {
        BadgeTone.DANGER -> c.danger
        BadgeTone.WARNING -> c.warning
        BadgeTone.OK -> c.ok
        BadgeTone.NEUTRAL -> c.textSecondary
    }
    FlowRow(
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
    ) {
        when (presentation.glyph) {
            BadgeGlyph.CHECK -> {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(c.ok, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    // Icon, not a "✓" text glyph: the glyph sits high in
                    // its line box (no descender), so it looked off-center.
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
            BadgeGlyph.WARNING -> WarningDot(Modifier.size(16.dp), statusColor)
            BadgeGlyph.NONE -> Spacer(Modifier.size(16.dp))
        }

        val statusText = presentation.pluralCount?.let { count ->
            pluralStringResource(presentation.labelResId, count.toInt(), count.toInt())
        } ?: stringResource(presentation.labelResId)
        Text(
            text = statusText,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = statusColor,
        )
        // Refactor M5: a failed refresh replaces the EXPIRY segment with the
        // neutral stale status - the badge label itself is untouched (spec 04
        // §4.2) - so cached values never LOOK current after a failure.
        val expirySegment = if (stale) stringResource(R.string.stale_refresh_status) else expiryText
        expirySegment?.let {
            Text(
                text = "· $it",
                fontSize = 12.5.sp,
                color = c.textSecondary,
            )
        }
    }
}

@Composable
private fun WarningDot(modifier: Modifier, color: Color) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            Icons.Outlined.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Red soft banner for expired / fully-redeemed vouchers. Mockup: plain text,
 *  no icon. [neutral] renders the banner on the neutral raised surface instead
 *  of the danger tint - used for UNVERIFIED rows (refactor H10), whose missing
 *  data is not a warning. */
@Composable
private fun StatusBanner(text: String, neutral: Boolean = false) {
    val c = LocalRedesignColors.current
    Surface(
        color = if (neutral) c.surfaceRaised else c.dangerSoft,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .padding(bottom = 14.dp)
            .fillMaxWidth(),
    ) {
        Text(
            text = text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = c.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** Category pill + amount columns for a voucher with remaining balance.
 *  FlowRow-based: at large font scales the pills wrap to their own lines
 *  (whole pill units) instead of squeezing a long name into a mid-word
 *  break, matching the no-truncation rule in spec 04. */
@Composable
private fun CategoryPills(voucher: VoucherGroup, modifier: Modifier = Modifier) {
    val dark = LocalAppIsDark.current
    val c = LocalRedesignColors.current
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        voucher.categoryBalances.forEach { balance ->
            val visuals = categoryVisuals(balance.category, dark)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    color = visuals.soft,
                    contentColor = visuals.color,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(visuals.color, CircleShape),
                        )
                        Text(
                            text = localizeCategory(balance.category, LocalAppLanguage.current),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    text = formatSgd(balance.remainingValue),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.textPrimary,
                    fontFamily = FrauncesDisplayFontFamily,
                )
            }
        }
    }
}
