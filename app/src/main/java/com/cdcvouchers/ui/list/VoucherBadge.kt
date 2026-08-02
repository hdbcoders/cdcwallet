package com.cdcvouchers.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cdcvouchers.ui.theme.LocalAppIsDark

private val AmberContainerLight = Color(0xFFFFE0B2)
private val AmberContentLight = Color(0xFF8D4E00)
private val AmberContainerDark = Color(0xFF4B3100)
private val AmberContentDark = Color(0xFFFFD180)

/**
 * Spec 04 §4.2 badge. Distinct per state: label text (announced by TalkBack),
 * color, and icon — never color alone, for colorblind users. UNVERIFIED is
 * styled outside the red/amber/green urgency scale entirely.
 */
@Composable
fun VoucherBadge(state: BadgeState, modifier: Modifier = Modifier) {
    val containerColor: Color
    val contentColor: Color
    val icon: ImageVector
    when (state) {
        is BadgeState.Active -> when (state.urgency) {
            Urgency.URGENT -> {
                containerColor = MaterialTheme.colorScheme.errorContainer
                contentColor = MaterialTheme.colorScheme.onErrorContainer
                icon = Icons.Default.Warning
            }
            Urgency.SOON -> {
                val dark = LocalAppIsDark.current
                containerColor = if (dark) AmberContainerDark else AmberContainerLight
                contentColor = if (dark) AmberContentDark else AmberContentLight
                icon = Icons.Default.Warning
            }
            Urgency.FINE -> {
                containerColor = MaterialTheme.colorScheme.secondaryContainer
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                icon = Icons.Default.CheckCircle
            }
        }
        BadgeState.Expired -> {
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
            icon = Icons.Default.Warning
        }
        BadgeState.NotStarted -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            icon = Icons.Default.Info
        }
        BadgeState.Unverified -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            icon = Icons.Default.Refresh
        }
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(badgeLabel(state), style = MaterialTheme.typography.labelSmall)
        }
    }
}
