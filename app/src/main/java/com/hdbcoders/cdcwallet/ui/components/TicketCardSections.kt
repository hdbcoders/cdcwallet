@file:OptIn(ExperimentalLayoutApi::class, ExperimentalTextApi::class)

package com.hdbcoders.cdcwallet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.ui.list.BadgeGlyph
import com.hdbcoders.cdcwallet.ui.list.BadgePresentation
import com.hdbcoders.cdcwallet.ui.list.BadgeTone
import com.hdbcoders.cdcwallet.ui.list.formatSgd
import com.hdbcoders.cdcwallet.ui.theme.LocalAppLanguage
import com.hdbcoders.cdcwallet.ui.theme.LocalAppTypefaces
import com.hdbcoders.cdcwallet.ui.theme.LocalRedesignColors
import com.hdbcoders.cdcwallet.ui.theme.categoryVisuals
import com.hdbcoders.cdcwallet.ui.theme.localizeCampaignName
import com.hdbcoders.cdcwallet.ui.theme.localizeCategory

/**
 * The card's title band: the free-flowing campaign name whose FIRST line is
 * vertically centered in a fixed 48dp slot, so it aligns with the kebab
 * (which is centered in its own 48dp target) at any font scale.
 *
 * The line height is MEASURED via onTextLayout rather than assumed from
 * lineHeight = 20.sp: with the platform's includeFontPadding the rendered
 * line box is taller than the declared line height (verified by instrumented
 * test: a 20.sp-based pad drifted 3-4.5px at 1.75x font scale). The measured
 * height keeps the padding exact, so the first-line center stays pinned to
 * 24dp as the font grows. (Clamped at 0: beyond ~2.4x font scale the line is
 * taller than the slot and simply starts at the card top instead of clipping
 * above it.)
 *
 * The measurement state is self-contained: only this Text consumes the pad,
 * so TicketCard needs no shared state with the kebab it aligns to.
 */
@Composable
internal fun TicketTitleBand(voucher: VoucherGroup) {
    val c = LocalRedesignColors.current
    val typefaces = LocalAppTypefaces.current
    val density = LocalDensity.current
    var firstNameLineHeightPx by remember { mutableStateOf(0f) }
    val titleBandPad = if (firstNameLineHeightPx > 0f) {
        with(density) { ((48.dp.toPx() - firstNameLineHeightPx) / 2f).coerceAtLeast(0f).toDp() }
    } else {
        // First frame: nominal 20sp line height; onTextLayout corrects it.
        with(density) { ((48.dp.toPx() - 20.sp.toPx()) / 2f).coerceAtLeast(0f).toDp() }
    }
    // Campaign name: free-flowing. The end inset (70dp = 48 kebab + 12
    // spacing + 10 edge) matches the old title row's wrap width, so existing
    // names don't reflow.
    Text(
        text = localizeCampaignName(voucher.campaignName, LocalAppLanguage.current),
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = typefaces.display,
        color = c.textPrimary,
        lineHeight = 20.sp,
        onTextLayout = { result ->
            if (result.lineCount > 0) {
                val lineHeight = result.getLineBottom(0) - result.getLineTop(0)
                if (lineHeight != firstNameLineHeightPx) firstNameLineHeightPx = lineHeight
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 70.dp, top = titleBandPad),
    )
}

/** Perforated divider between the title/expiry band and the card body. */
@Composable
internal fun TicketPerforatedDivider() {
    val c = LocalRedesignColors.current
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
}

/**
 * The card body: pills + amounts, or the status banner. Driven exclusively by
 * BadgeState via BadgePresentation (refactor H10, spec 04 §4.2): the
 * "no balance" banner applies only to an ACTIVE zero-balance row - a
 * UNVERIFIED / NOT_STARTED row with no extracted balances must never read as
 * "fully used".
 */
@Composable
internal fun TicketBody(voucher: VoucherGroup, presentation: BadgePresentation) {
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

/** Expiry row: green ✓ + days-left (fine), amber/red warning (soon/urgent),
 *  red warning + status text (expired / fully used). FlowRow: status and
 *  expiry wrap to their own lines instead of ellipsizing at large font
 *  scales. Renders exclusively from [BadgePresentation] (refactor L3). */
@Composable
internal fun ExpiryRow(
    presentation: BadgePresentation,
    expiryText: String?,
    stale: Boolean = false,
) {
    val c = LocalRedesignColors.current
    // Fills/icons use the raw status hues; TEXT uses the AA-safe text slots
    // (warningText exists because raw amber fails 2.4-3.0:1 as text on light
    // surfaces - see RedesignColors).
    val statusColor = when (presentation.tone) {
        BadgeTone.DANGER -> c.danger
        BadgeTone.WARNING -> c.warning
        BadgeTone.OK -> c.ok
        BadgeTone.NEUTRAL -> c.textSecondary
    }
    val statusTextColor = when (presentation.tone) {
        BadgeTone.DANGER -> c.danger
        BadgeTone.WARNING -> c.warningText
        BadgeTone.OK -> c.ok
        BadgeTone.NEUTRAL -> c.textSecondary
    }
    FlowRow(
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(start = 18.dp, top = 4.dp, end = 18.dp, bottom = 8.dp),
    ) {
        // Glyph + status are ONE FlowRow unit (tester-visible on the
        // Zero-Balance ticket at large font scales): as separate items the
        // 16dp icon fit while the status text wrapped, leaving the triangle
        // stranded alone on its own row. The inner Row wraps them together -
        // the icon can never split from the text it describes.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            tint = c.onFill,
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
            // Pin the line box to the font size and drop the font's internal
            // padding (same pattern as the AppHeader 文A / Archived pill): the
            // default 12.5sp line box carries ~1.9x the glyph height in empty
            // air, which at large font scales makes a wrapped expiry row look
            // like a huge gap between "days left" and the "· expires" segment.
            val rowTextStyle = LocalTextStyle.current.copy(
                fontSize = 12.5.sp,
                lineHeight = 12.5.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            )
            Text(
                text = statusText,
                fontWeight = FontWeight.SemiBold,
                color = statusTextColor,
                style = rowTextStyle,
            )
        }
        // Refactor M5: a failed refresh replaces the EXPIRY segment with the
        // neutral stale status - the badge label itself is untouched (spec 04
        // §4.2) - so cached values never LOOK current after a failure.
        val expirySegment = if (stale) stringResource(R.string.stale_refresh_status) else expiryText
        expirySegment?.let {
            Text(
                text = "· $it",
                color = c.textSecondary,
                style = LocalTextStyle.current.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 12.5.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            )
        }
    }
}

@Composable
internal fun WarningDot(modifier: Modifier, color: Color) {
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
internal fun StatusBanner(text: String, neutral: Boolean = false) {
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
internal fun CategoryPills(voucher: VoucherGroup, modifier: Modifier = Modifier) {
    val c = LocalRedesignColors.current
    val typefaces = LocalAppTypefaces.current
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        voucher.categoryBalances.forEach { balance ->
            val visuals = categoryVisuals(balance.category)
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
                    fontFamily = typefaces.display,
                )
            }
        }
    }
}
