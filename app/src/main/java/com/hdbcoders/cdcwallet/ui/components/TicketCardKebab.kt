package com.hdbcoders.cdcwallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.ui.theme.AppScaledContent
import com.hdbcoders.cdcwallet.ui.theme.LocalAppLanguage
import com.hdbcoders.cdcwallet.ui.theme.LocalRedesignColors
import com.hdbcoders.cdcwallet.ui.theme.localizeCampaignName

/**
 * The card's kebab corner: the ⋮ overflow affordance, the optional pin marker,
 * the dropdown menu, and the row-level custom a11y/agent actions.
 *
 * The kebab is its OWN element overlaid on the card, NOT a sibling in the
 * name's layout flow: the campaign name wraps freely downward and the ⋮ stays
 * anchored top-right. The caller passes `Modifier.align(Alignment.TopEnd)`
 * (the extraction seam): the 48dp IconButton starts flush with the card top
 * and its content is centered, so the 18dp icon's center is at 24dp - exactly
 * the first name line's pinned center (see [TicketTitleBand]). The two stay
 * aligned as the font size grows.
 *
 * Pin marker (B1, device-local): a non-interactive gold pushpin to the LEFT of
 * the kebab in a Row, so the kebab's right edge stays flush with the card
 * corner and the pin grows LEFTWARD (never into the kebab's 48dp target).
 * Sized sp->dp with an 18dp cap so even max text scale cannot push the stack
 * past the name's 70dp wrap inset by more than ~2dp. contentDescription = null
 * keeps TalkBack merged on the card's single announcement.
 */
@Composable
internal fun TicketKebab(
    voucher: VoucherGroup,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    rowActions: VoucherRowActions?,
    modifier: Modifier = Modifier,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalRedesignColors.current
    val density = LocalDensity.current
    // Labels for the row-level custom a11y/agent actions (pin / archive /
    // delete): the same operations as the kebab menu, exposed so UIAutomator
    // and accessibility services can trigger them without opening the menu.
    val pinActionLabel = rowActions?.let {
        stringResource(if (it.isPinned) R.string.unpin else R.string.pin)
    }
    val archiveActionLabel = rowActions?.let { stringResource(it.middleLabelRes) }
    val deleteActionLabel = rowActions?.let { stringResource(R.string.delete) }

    Row(
        modifier = modifier
            .padding(end = 10.dp)
            .semantics {
                // Custom a11y/agent actions on the row: lets UIAutomator
                // and accessibility services drive the overflow menu's
                // operations directly without opening it.
                val actions = buildList {
                    pinActionLabel?.let { label ->
                        add(CustomAccessibilityAction(label) { rowActions.onPinClick(); true })
                    }
                    archiveActionLabel?.let { label ->
                        add(CustomAccessibilityAction(label) { rowActions.onMiddleClick(); true })
                    }
                    deleteActionLabel?.let { label ->
                        add(CustomAccessibilityAction(label) { rowActions.onDelete(); true })
                    }
                }
                customActions = actions
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (voucher.isPinned) {
            val pinSize = with(density) { 16.sp.toDp() }.coerceAtMost(20.dp)
            Icon(
                Icons.Filled.PushPin,
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier
                    .padding(end = 6.dp)
                    // Visual-only nudge toward the kebab glyph; capped
                    // so even at max font scale the pin's right edge
                    // stays clear of the kebab button's left edge.
                    .offset(x = 10.dp)
                    .size(pinSize),
            )
        }
        IconButton(
            onClick = { onMenuExpandedChange(true) },
            modifier = Modifier
                .size(48.dp)
                .testTag("voucher-kebab-${voucher.id}"),
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(
                    R.string.more_options,
                    localizeCampaignName(voucher.campaignName, LocalAppLanguage.current),
                ),
                // textSecondary, not textTertiary: the kebab is the row's
                // only menu affordance - tertiary drops to 3.3:1 on dark
                // cards (2.9 on menus), below the 3:0 UI-component bar.
                tint = c.textSecondary,
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
