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
 * Second-pin confirmation gate: shown when the user picks Pin while another
 * voucher is already pinned. Confirming unpins the current pin and pins the
 * new choice (one atomic repository swap). "Cancel" is default-focused so an
 * accidental confirm-tap cannot change the pin; the confirm action is NOT
 * destructive-styled because nothing is destroyed - the old pin simply loses
 * its convenience flag.
 */
@Composable
fun PinSwapDialog(
    pinnedName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    AppDialogSurface(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.pin_swap_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.pin_swap_body, pinnedName),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        DialogButtonRow(
            cancelLabel = stringResource(R.string.cancel),
            onCancel = onDismiss,
            confirmLabel = stringResource(R.string.pin),
            onConfirm = onConfirm,
            destructive = false,
            cancelModifier = Modifier.focusRequester(cancelFocus),
        )
    }
    // Same M3 focus-retry workaround as DeleteVoucherDialog (05 §5.3): retry
    // briefly until Cancel actually holds initial focus.
    LaunchedEffect(Unit) {
        repeat(40) {
            cancelFocus.requestFocus()
            delay(50)
        }
    }
}
