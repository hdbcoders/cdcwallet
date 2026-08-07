package com.cdcvouchers.ui.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cdcvouchers.R
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.ui.theme.BadgeColors
import com.cdcvouchers.ui.theme.ClimateChipDark
import com.cdcvouchers.ui.theme.ClimateChipLight
import com.cdcvouchers.ui.theme.HeartlandChipDark
import com.cdcvouchers.ui.theme.HeartlandChipLight
import com.cdcvouchers.ui.theme.LocalAppIsDark
import com.cdcvouchers.ui.theme.SupermarketChipDark
import com.cdcvouchers.ui.theme.SupermarketChipLight
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

/**
 * Row layout shared by the main list and the archived screen (spec 04 / 05).
 * Renders the visible ⋮ overflow button and the same overflow menu (whose
 * contents are supplied by the caller, Package 5's concern). Tap on the row
 * body is [onClick]; the overflow menu is opened only via the ⋮ button.
 *
 * Card layout (reference redesign): title row → badge + expiry → divider →
 * footer. The footer is either a row of colored category chips with an amount
 * each (voucher has remaining balance) or a red call-out box with a wallet
 * icon and "$0 remaining" (voucher fully redeemed).
 */
@Composable
fun VoucherRow(
    voucher: VoucherGroup,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    val badge = badgeState(voucher)
    // Date formatted in the app's active locale: the pattern is a per-locale
    // resource and the month names come from the locale itself.
    val datePattern = stringResource(R.string.date_pattern)
    val locale = LocalConfiguration.current.locales[0]
    val dateText = remember(voucher.expiryDate, locale) {
        voucher.expiryDate?.let { date ->
            DateTimeFormatter.ofPattern(datePattern, locale).format(date)
        }
    }
    val expiryText = dateText?.let { stringResource(R.string.expires, it) }
    // A voucher is "fully redeemed" when nothing remains to spend.
    val fullyRedeemed = remember(voucher.categoryBalances) {
        voucher.categoryBalances.none { it.remainingValue > BigDecimal.ZERO }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            // Duplicate-add highlight (spec 03 §3.2): flash a subtle primary tint.
            color = if (isHighlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            border = BorderStroke(
                1.dp,
                if (isHighlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isHighlighted) Modifier.testTag("highlighted-${voucher.id}") else Modifier,
                )
                .clickable(onClick = onClick),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp),
                ) {
                    Text(
                        text = voucher.campaignName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(
                            onClick = { onMenuExpandedChange(true) },
                            // Shrink the touch target so the 48dp default
                            // doesn't inflate the title row height.
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options, voucher.campaignName),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { onMenuExpandedChange(false) },
                        ) {
                            menuContent()
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    VoucherBadge(badge)
                    expiryText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                if (badge is BadgeState.Expired) {
                    // Expired card: no balance amount — just the expiration text.
                    ExpiredFooter()
                } else if (fullyRedeemed) {
                    NoBalanceFooter()
                } else {
                    BalanceChips(voucher, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp))
                }
            }
        }
    }
}

/**
 * Footer for a voucher with remaining balance. Categories sit side by side,
 * each as a centered column with the colored chip on top and its amount below;
 * a vertical divider separates adjacent categories (reference redesign).
 */
@Composable
private fun BalanceChips(voucher: VoucherGroup, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        voucher.categoryBalances.forEachIndexed { index, balance ->
            if (index > 0) {
                VerticalDivider(
                    thickness = 0.5.dp,
                    modifier = Modifier.height(40.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CategoryChip(balance.category, chipPalette(balance.category))
                Text(
                    text = formatSgd(balance.remainingValue),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/**
 * Chip palette for a category name. Only CDC categories Heartland and
 * Supermarket carry their own colors (from the payload `type`). Everything
 * else — Climate included, where the category is the campaign's first word
 * because the payload `type` is null — defaults to the Climate blue
 * (spec 02 §2.2). Matching is case-insensitive since extracted names can vary.
 */
@Composable
private fun chipPalette(category: String): BadgeColors {
    val dark = LocalAppIsDark.current
    return when (category.trim().lowercase()) {
        "heartland" -> if (dark) HeartlandChipDark else HeartlandChipLight
        "supermarket" -> if (dark) SupermarketChipDark else SupermarketChipLight
        else -> if (dark) ClimateChipDark else ClimateChipLight
    }
}

/** A single colored pill chip for a category name. */
@Composable
private fun CategoryChip(category: String, palette: BadgeColors) {
    Surface(
        color = palette.container,
        contentColor = palette.content,
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = category,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** Red call-out footer for an expired voucher (redesign spec): no balance shown. */
@Composable
private fun ExpiredFooter() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.expired_footer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** Red call-out footer for a fully redeemed voucher (reference design). */
@Composable
private fun NoBalanceFooter() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.zero_remaining, formatSgd(BigDecimal.ZERO)),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.no_balance_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
