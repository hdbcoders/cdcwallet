package com.hdbcoders.cdcwallet.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.data.model.VoucherBackupPayload
import com.hdbcoders.cdcwallet.ui.components.AppDialogSurface
import com.hdbcoders.cdcwallet.ui.components.DialogButtonRow
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun BusyProgressDialog(message: String) {
    AppDialogSurface(onDismissRequest = {}) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.testTag("backup-progress"),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun BackupPasswordDialog(
    requireConfirmation: Boolean,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = password.isNotEmpty() && (!requireConfirmation || password == confirmation)
    val passwordFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    AppDialogSurface(onDismissRequest = onDismiss) {
        Text(
            text = if (requireConfirmation) {
                stringResource(R.string.set_backup_password)
            } else {
                stringResource(R.string.backup_password)
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (requireConfirmation) {
                stringResource(R.string.password_encrypt_desc)
            } else {
                stringResource(R.string.password_enter_desc)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.backup_password)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("backup_password").focusRequester(passwordFocus),
        )
        if (requireConfirmation) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = { Text(stringResource(R.string.confirm_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("backup_confirm_password"),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        DialogButtonRow(
            cancelLabel = stringResource(R.string.cancel),
            onCancel = onDismiss,
            confirmLabel = confirmLabel,
            onConfirm = { onConfirm(password) },
            confirmEnabled = valid,
        )
    }
    // M3 Dialog doesn't reliably land initial focus on the password field
    // (mirrors DeleteVoucherDialog - the request can be stolen during dialog
    // mount). Retry briefly (up to ~2s) until it sticks, then surface the
    // keyboard so the user can type without an extra tap. Applies to both the
    // export (password + confirm) and import (password) dialogs.
    LaunchedEffect(Unit) {
        repeat(40) {
            passwordFocus.requestFocus()
            delay(50)
        }
        keyboardController?.show()
    }
}

@Composable
internal fun BackupSummaryDialog(
    payload: VoucherBackupPayload,
    currentCount: Int,
    onImport: (ImportMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(ImportMode.MERGE) }
    // Date formatted in the app's active locale (pattern is a per-locale
    // resource; month names come from the locale itself).
    val datePattern = stringResource(R.string.date_pattern)
    val locale = LocalConfiguration.current.locales[0]
    val date = payload.createdAt
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern(datePattern, locale))
    val archived = payload.vouchers.count { it.isArchived }

    AppDialogSurface(onDismissRequest = onDismiss) {
        Text(
            text = pluralStringResource(
                R.plurals.backup_summary,
                payload.vouchers.size,
                date,
                payload.vouchers.size,
                archived,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.merge_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ModeOption(
            label = stringResource(R.string.merge),
            selected = mode == ImportMode.MERGE,
            onClick = { mode = ImportMode.MERGE },
        )
        ModeOption(
            label = stringResource(R.string.replace),
            selected = mode == ImportMode.REPLACE,
            onClick = { mode = ImportMode.REPLACE },
        )
        Spacer(modifier = Modifier.height(24.dp))
        DialogButtonRow(
            cancelLabel = stringResource(R.string.cancel),
            onCancel = onDismiss,
            confirmLabel = stringResource(R.string.import_confirm),
            onConfirm = { onImport(mode) },
        )
    }
}

@Composable
private fun ModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ConfirmReplaceDialog(
    count: Int,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    // Refactor M20: "Cancel" is the default-focused button, same convention
    // as the delete dialog - an accidental confirm-tap must not wipe data.
    val cancelFocus = remember { FocusRequester() }
    AppDialogSurface(onDismissRequest = onCancel) {
        Text(
            text = stringResource(R.string.replace_all_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = pluralStringResource(R.plurals.replace_all_body, count, count),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        DialogButtonRow(
            cancelLabel = stringResource(R.string.cancel),
            onCancel = onCancel,
            confirmLabel = stringResource(R.string.replace_confirm),
            onConfirm = onReplace,
            destructive = true,
            cancelModifier = Modifier.focusRequester(cancelFocus),
        )
    }
    // Same retry block as the delete dialog (M3 dialogs can steal focus
    // during mount); delete this if a future Compose/M3 update fixes it.
    LaunchedEffect(Unit) {
        repeat(10) {
            cancelFocus.requestFocus()
            delay(50)
        }
    }
}
