package com.cdcvouchers.ui.list

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.cdcvouchers.ui.components.AppDialogSurface
import com.cdcvouchers.ui.components.DialogButtonRow
import kotlinx.coroutines.delay

/**
 * The one shared delete-confirmation dialog (spec 05 §5.3), used identically
 * from the main list and the archived screen. Exact copy; "Cancel" is the
 * default-focused button so an accidental confirm-tap can't delete; "Delete"
 * carries destructive styling.
 */
@Composable
fun DeleteVoucherDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    AppDialogSurface(onDismissRequest = onDismiss) {
        Text(
            text = "Delete this voucher link?",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "You may not be able to get this link back once it's deleted.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        DialogButtonRow(
            cancelLabel = "Cancel",
            onCancel = onDismiss,
            confirmLabel = "Delete",
            onConfirm = onConfirm,
            destructive = true,
            cancelModifier = Modifier.focusRequester(cancelFocus),
        )
    }
    // M3 Dialog doesn't reliably land initial focus on a button; the request can
    // be stolen during dialog mount. Retry briefly (up to ~0.5s) until it sticks.
    // If a future Compose/M3 update fixes dialog focus, delete this block.
    LaunchedEffect(Unit) {
        repeat(10) {
            cancelFocus.requestFocus()
            delay(50)
        }
    }
}
