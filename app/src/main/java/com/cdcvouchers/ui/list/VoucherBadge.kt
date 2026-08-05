package com.cdcvouchers.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cdcvouchers.ui.theme.AmberBadgeDark
import com.cdcvouchers.ui.theme.AmberBadgeLight
import com.cdcvouchers.ui.theme.GreenBadgeDark
import com.cdcvouchers.ui.theme.GreenBadgeLight
import com.cdcvouchers.ui.theme.LocalAppIsDark

/**
 * Spec 04 §4.2 badge. Distinct per state: label text (announced by TalkBack),
 * color, and icon — never color alone, for colorblind users. Rendered without
 * a background pill: just the status symbol + label tinted per state.
 * UNVERIFIED is styled outside the red/amber/green urgency scale entirely.
 */
@Composable
fun VoucherBadge(state: BadgeState, modifier: Modifier = Modifier) {
    val contentColor: Color
    val icon: ImageVector
    when (state) {
        is BadgeState.Active -> when (state.urgency) {
            Urgency.URGENT -> {
                contentColor = MaterialTheme.colorScheme.error
                icon = Icons.Default.Warning
            }
            Urgency.SOON -> {
                val palette = if (LocalAppIsDark.current) AmberBadgeDark else AmberBadgeLight
                contentColor = palette.content
                icon = Icons.Default.Warning
            }
            Urgency.FINE -> {
                val palette = if (LocalAppIsDark.current) GreenBadgeDark else GreenBadgeLight
                contentColor = palette.content
                icon = Icons.Default.CheckCircle
            }
        }
        BadgeState.Expired -> {
            contentColor = MaterialTheme.colorScheme.error
            icon = Icons.Default.Warning
        }
        BadgeState.NoBalance -> {
            // Same red + warning treatment as Expired (spec 04 §4.2).
            contentColor = MaterialTheme.colorScheme.error
            icon = Icons.Default.Warning
        }
        BadgeState.NotStarted -> {
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            icon = Icons.Default.Info
        }
        BadgeState.Unverified -> {
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            icon = Icons.Default.Refresh
        }
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = contentColor)
        Text(badgeLabel(state), style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
}
