package com.hdbcoders.cdcwallet.ui.components

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.ui.theme.LocalRedesignColors

/**
 * The row-menu's middle action: Archive on the main list, Restore on the
 * archived list. Everything else in the menu (Copy URL, divider, Delete) is
 * identical on both screens.
 */
enum class RowMenuMiddleAction { ARCHIVE, RESTORE }

/** Second-item pin action (main list only); archived screen passes null. */
enum class RowMenuPinAction { PIN, UNPIN }

/**
 * Shared kebab-menu content for voucher rows (refactor L2): Copy URL, the
 * caller's middle action, a divider, and Delete. One implementation so labels,
 * icons, accessibility, and behavior cannot drift between the two screens.
 */
@Composable
fun ColumnScope.VoucherRowMenuContent(
    onCopyUrl: () -> Unit,
    middleAction: RowMenuMiddleAction,
    onMiddleClick: () -> Unit,
    onDelete: () -> Unit,
    /**
     * Pin/Unpin as the SECOND menu item (main list only - the archived screen
     * omits it). Null hides the slot entirely so the shared component cannot
     * drift between screens.
     */
    pinAction: RowMenuPinAction? = null,
    onPinClick: () -> Unit = {},
) {
    val c = LocalRedesignColors.current
    val middleLabel = stringResource(
        when (middleAction) {
            RowMenuMiddleAction.ARCHIVE -> R.string.archive
            RowMenuMiddleAction.RESTORE -> R.string.restore
        },
    )
    val middleIcon = when (middleAction) {
        RowMenuMiddleAction.ARCHIVE -> Icons.Filled.Folder
        RowMenuMiddleAction.RESTORE -> Icons.Filled.Restore
    }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.copy_url)) },
        leadingIcon = {
            Icon(Icons.Filled.Link, contentDescription = null, tint = c.textSecondary)
        },
        onClick = onCopyUrl,
    )
    if (pinAction != null) {
        val pinLabel = stringResource(
            when (pinAction) {
                RowMenuPinAction.PIN -> R.string.pin
                RowMenuPinAction.UNPIN -> R.string.unpin
            },
        )
        val pinIcon = when (pinAction) {
            RowMenuPinAction.PIN -> Icons.Filled.PushPin
            RowMenuPinAction.UNPIN -> Icons.Outlined.PushPin
        }
        DropdownMenuItem(
            text = { Text(pinLabel) },
            leadingIcon = {
                Icon(pinIcon, contentDescription = null, tint = c.textSecondary)
            },
            onClick = onPinClick,
        )
    }
    DropdownMenuItem(
        text = { Text(middleLabel) },
        leadingIcon = {
            Icon(middleIcon, contentDescription = null, tint = c.textSecondary)
        },
        onClick = onMiddleClick,
    )
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp),
        color = c.hairline,
    )
    DropdownMenuItem(
        text = {
            // dangerText: raw danger dips below AA on surfaceRaised menus in
            // dark mode (4.35:1); the text-context slot is brightened there.
            Text(stringResource(R.string.delete), color = c.dangerText)
        },
        leadingIcon = {
            Icon(Icons.Filled.Delete, contentDescription = null, tint = c.dangerText)
        },
        onClick = onDelete,
    )
}

/**
 * Platform-effect bridge for Copy URL (REQ-10 feedback, refactor L11): writes
 * the clipboard and shows the localized toast when the ViewModel requests a
 * copy, then consumes the request. The ACTION is requested in the ViewModel;
 * the screen hosts this shared bridge. Toast text resolves at composition time
 * so the message follows the active app locale.
 */
@Composable
fun CopyUrlEffect(copyRequest: String?, onConsumed: () -> Unit) {
    val clipboard = LocalClipboard.current
    // Toast on Copy URL (REQ-10 feedback): the activity context is wrapped
    // with the active app locale, so the message follows the app language.
    val context = LocalContext.current
    val linkCopiedLabel = stringResource(R.string.link_copied)
    LaunchedEffect(copyRequest) {
        copyRequest?.let { url ->
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("voucher link", url)))
            Toast.makeText(context, linkCopiedLabel, Toast.LENGTH_SHORT).show()
            onConsumed()
        }
    }
}
