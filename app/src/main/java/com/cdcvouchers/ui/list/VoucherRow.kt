package com.cdcvouchers.ui.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cdcvouchers.data.model.VoucherGroup
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Row layout shared by the main list and the archived screen (spec 04 / 05).
 * Renders the visible ⋮ overflow button and the same overflow menu (whose
 * contents are supplied by the caller, Package 5's concern). Tap on the row
 * body is [onClick]; the overflow menu is opened only via the ⋮ button.
 */
@Composable
fun VoucherRow(
    voucher: VoucherGroup,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    val badge = badgeState(voucher)
    val expiryText = remember(voucher.expiryDate) {
        voucher.expiryDate?.let { "Expires " + EXPIRY_DATE_FORMAT.format(it) }
    }
    val balanceText = remember(voucher.categoryBalances) {
        if (voucher.categoryBalances.isEmpty()) {
            null
        } else {
            voucher.categoryBalances.joinToString(" · ") {
                "${it.category.replaceFirstChar(Char::uppercase)}: ${formatSgd(it.remainingValue)}"
            }
        }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = voucher.campaignName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { onMenuExpandedChange(true) }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More options for ${voucher.campaignName}",
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
                balanceText?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private val EXPIRY_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
