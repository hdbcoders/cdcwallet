package com.hdbcoders.cdcwallet.ui.list

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.ui.components.AppDialogSurface
import com.hdbcoders.cdcwallet.ui.components.DialogButtonRow
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
            text = stringResource(R.string.delete_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.delete_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        DialogButtonRow(
            cancelLabel = stringResource(R.string.cancel),
            onCancel = onDismiss,
            confirmLabel = stringResource(R.string.delete),
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
