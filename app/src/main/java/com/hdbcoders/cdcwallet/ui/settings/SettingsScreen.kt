package com.hdbcoders.cdcwallet.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.data.VoucherRepository
import com.hdbcoders.cdcwallet.data.backup.BackupFlow
import com.hdbcoders.cdcwallet.data.model.VoucherBackupPayload
import com.hdbcoders.cdcwallet.ui.components.AppDialogSurface
import com.hdbcoders.cdcwallet.ui.components.DialogButtonRow
import com.hdbcoders.cdcwallet.ui.theme.LightPalette
import com.hdbcoders.cdcwallet.ui.theme.ThemeModeStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Settings screen (spec 06): encrypted backup export to Downloads and import
 * via the system file picker. Import never touches the network - restored rows
 * are plain cached entries from the payload. The `backupBytesProvider` seam
 * exists for instrumented tests (system picker is not drivable there);
 * production passes null and uses the real picker. Dialog/flow state lives in
 * [SettingsViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    backupFlow: BackupFlow,
    repository: VoucherRepository,
    themeModeStore: ThemeModeStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // TEST-ONLY SEAM (refactor M17): injects bytes instead of the system file
    // picker in the instrumented backup tests; production always passes null.
    backupBytesProvider: (() -> ByteArray?)? = null,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    // Snackbar event held in state: the message text is resolved at
    // composition time via stringResource/pluralStringResource so the active
    // app locale is always used.
    var pendingSnackbar by remember { mutableStateOf<SettingsEvent.Snackbar?>(null) }

    val vm: SettingsViewModel = viewModel(
        initializer = { SettingsViewModel(backupFlow, repository, context.applicationContext) },
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val activeCount by vm.activeCount.collectAsStateWithLifecycle()
    val archivedCount by vm.archivedCount.collectAsStateWithLifecycle()

    // API 24-28 write to the public Downloads directory directly (scoped
    // storage starts at 29), which needs WRITE_EXTERNAL_STORAGE granted at
    // runtime on API 23+ - request it before the first export on those
    // versions; API 29+ uses MediaStore and needs nothing.
    val exportPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> vm.onLegacyPermissionResult(granted) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) vm.onFilePicked(uri)
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is SettingsEvent.Snackbar -> pendingSnackbar = event
                SettingsEvent.RequestLegacyPermission ->
                    exportPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    fun startImport() {
        val provided = backupBytesProvider?.invoke()
        if (provided != null) {
            vm.onBackupBytesProvided(provided)
        } else {
            filePicker.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppearanceSection(
                selected = themeModeStore.palette,
                onSelect = { themeModeStore.setLightPalette(it) },
            )
            Text(
                text = stringResource(R.string.backup),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(
                onClick = { vm.openExportDialog() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.export_backup))
            }
            Text(
                text = stringResource(R.string.export_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { startImport() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.import_backup))
            }
            Text(
                text = stringResource(R.string.import_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingSnackbar?.let { event ->
        val text = if (event.pluralCount != null) {
            pluralStringResource(event.resId, event.pluralCount, *event.formatArgs.toTypedArray())
        } else {
            stringResource(event.resId, *event.formatArgs.toTypedArray())
        }
        LaunchedEffect(text) {
            snackbarHostState.showSnackbar(text)
            pendingSnackbar = null
        }
    }

    if (state.exportDialogOpen) {
        BackupPasswordDialog(
            requireConfirmation = true,
            confirmLabel = stringResource(R.string.export),
            onConfirm = { password -> vm.onExportPasswordConfirmed(password) },
            onDismiss = { vm.dismissExportDialog() },
        )
    }

    when (state.passwordDialogFor) {
        PasswordDialogTarget.IMPORT -> {
            BackupPasswordDialog(
                requireConfirmation = false,
                confirmLabel = stringResource(R.string.import_confirm),
                onConfirm = { password -> vm.onImportPasswordConfirmed(password) },
                onDismiss = { vm.onImportDialogDismissed() },
            )
        }
        null -> Unit
    }

    state.summaryPayload?.let { payload ->
        BackupSummaryDialog(
            payload = payload,
            currentCount = activeCount + archivedCount,
            onImport = { mode -> vm.onSummaryModeSelected(mode) },
            onDismiss = { vm.onSummaryDismissed() },
        )
    }

    if (state.pendingReplace != null) {
        ConfirmReplaceDialog(
            count = activeCount + archivedCount,
            onReplace = { vm.onReplaceConfirmed() },
            onCancel = { vm.onReplaceDismissed() },
        )
    }

    // Non-dismissable progress while a backup operation runs (decrypt/
    // merge/replace on import, encrypt + file write on export). The ViewModel
    // caps every operation at 10s, so the dialog can never hang forever.
    state.busyPhase?.let { phase ->
        BusyProgressDialog(
            message = stringResource(
                when (phase) {
                    BusyPhase.IMPORTING -> R.string.importing_backup
                    BusyPhase.EXPORTING -> R.string.exporting_backup
                },
            ),
        )
    }
}

@Composable
private fun BusyProgressDialog(message: String) {
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
/**
 * Appearance section (theme picker, spec 07 §7.2): one radio row per
 * [LightPalette] entry. Selecting a row calls [onSelect], which persists and
 * applies the palette instantly via ThemeModeStore snapshot state - no
 * restart needed. Dark/light mode itself is NOT controlled here; the drawer
 * toggle stays the only dark-mode switch.
 */
private fun AppearanceSection(
    selected: LightPalette,
    onSelect: (LightPalette) -> Unit,
) {
    Text(
        text = stringResource(R.string.settings_appearance),
        style = MaterialTheme.typography.titleMedium,
    )
    LightPalette.entries.forEach { palette ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = palette == selected,
                    onClick = { if (palette != selected) onSelect(palette) },
                )
                // TalkBack announces "selected"/"not selected" per row.
                .semantics { this.selected = palette == selected }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = palette == selected,
                onClick = null,
            )
            Text(
                text = stringResource(palette.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun BackupPasswordDialog(
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
private fun BackupSummaryDialog(
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
private fun ConfirmReplaceDialog(
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
