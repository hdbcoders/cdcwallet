package com.hdbcoders.cdcwallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.ui.list.badgePresentation
import com.hdbcoders.cdcwallet.ui.list.badgeState
import com.hdbcoders.cdcwallet.ui.theme.LocalRedesignColors
import java.time.format.DateTimeFormatter

/**
 * The redesign's ticket-style voucher card (mockup): Fraunces title row with
 * the ⋮ kebab (menu content supplied by the caller), an expiry row (green
 * ✓ dot + days-left for fine, amber/red warnings for soon/urgent, red for
 * expired / fully used), a perforated dashed divider, then either the
 * category pill + amount columns or the red status banner. Tap on the body
 * is [onClick].
 *
 * H2 (god-file split): the card composes four extracted sections -
 * [TicketTitleBand], [ExpiryRow], [TicketPerforatedDivider] and [TicketBody]
 * (TicketCardSections.kt) plus [TicketKebab] (TicketCardKebab.kt). This file
 * owns the card chrome and the section layout only.
 */
@Composable
fun TicketCard(
    voucher: VoucherGroup,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Row-level custom a11y/agent actions (pin/archive/delete), mirroring the
     * kebab menu's operations so UIAutomator and accessibility services can
     * trigger them without opening the menu. Null omits the actions entirely.
     */
    rowActions: VoucherRowActions? = null,
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
            // Stable agent/test handle: "voucher-card-<id>" lets UIAutomator
            // and instrumented tests address a specific row without relying
            // on localized text.
            .testTag("voucher-card-${voucher.id}")
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TicketTitleBand(voucher)
                // Expiry row: green ✓ + days-left for fine; warning for soon/urgent;
                // red warning + status label for expired / fully used. A failed
                // refresh (refactor M5) swaps the line for the neutral stale
                // status instead of the expiry text.
                ExpiryRow(presentation, expiryText, stale = voucher.lastRefreshError != null)

                TicketPerforatedDivider()

                TicketBody(voucher, presentation)
            }

            // Kebab: its OWN element, anchored to the card's top-right corner.
            TicketKebab(
                voucher = voucher,
                menuExpanded = menuExpanded,
                onMenuExpandedChange = onMenuExpandedChange,
                rowActions = rowActions,
                modifier = Modifier.align(Alignment.TopEnd),
                menuContent = menuContent,
            )
        }
    }
}

/**
 * Row-level custom a11y/agent actions for [TicketCard], mirroring the kebab
 * menu's operations so UIAutomator and accessibility services can pin,
 * archive/restore, or delete a voucher row without opening the menu.
 *
 * @param isPinned       drives the Pin/Unpin action label
 * @param middleLabelRes Archive (main list) or Restore (archived screen)
 * @param onPinClick     invoked by the Pin/Unpin custom action
 * @param onMiddleClick  invoked by the archive/restore custom action
 * @param onDelete       invoked by the delete custom action
 */
data class VoucherRowActions(
    val isPinned: Boolean,
    val middleLabelRes: Int,
    val onPinClick: () -> Unit,
    val onMiddleClick: () -> Unit,
    val onDelete: () -> Unit,
)
