package com.cdcvouchers.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.backup.BackupFlow
import com.cdcvouchers.data.model.VoucherBackupPayload
import com.cdcvouchers.ui.components.AppDialogSurface
import com.cdcvouchers.ui.components.DialogButtonRow
import com.cdcvouchers.ui.theme.ThemeMode
import com.cdcvouchers.ui.theme.ThemeModeStore
import kotlinx.coroutines.flow.collect
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings screen (spec 06): encrypted backup export to Downloads and import
 * via the system file picker. Import never touches the network — restored rows
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
    backupBytesProvider: (() -> ByteArray?)? = null,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val vm: SettingsViewModel = viewModel(
        initializer = { SettingsViewModel(backupFlow, repository, context.applicationContext) },
    )
    val state by vm.uiState.collectAsState()
    val activeCount by vm.activeCount.collectAsState()
    val archivedCount by vm.archivedCount.collectAsState()

    // API 24-28 write to the public Downloads directory directly (scoped
    // storage starts at 29), which needs WRITE_EXTERNAL_STORAGE granted at
    // runtime on API 23+ — request it before the first export on those
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
                is SettingsEvent.Snackbar -> snackbarHostState.showSnackbar(event.message)
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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
            )
            ModeOption(
                label = "Follow system",
                selected = themeModeStore.mode == ThemeMode.SYSTEM,
                onClick = { themeModeStore.setThemeMode(ThemeMode.SYSTEM) },
            )
            ModeOption(
                label = "Light",
                selected = themeModeStore.mode == ThemeMode.LIGHT,
                onClick = { themeModeStore.setThemeMode(ThemeMode.LIGHT) },
            )
            ModeOption(
                label = "Dark",
                selected = themeModeStore.mode == ThemeMode.DARK,
                onClick = { themeModeStore.setThemeMode(ThemeMode.DARK) },
            )
            Text(
                text = "How the app looks. Follow system matches your phone's setting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Backup",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(
                onClick = { vm.openExportDialog() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export backup")
            }
            Text(
                text = "Saves an encrypted copy of all your voucher links to Downloads. You'll set a password — remember it, it can't be recovered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { startImport() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import backup")
            }
            Text(
                text = "Restores from a backup file you exported before. Nothing is downloaded during import.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.exportDialogOpen) {
        BackupPasswordDialog(
            requireConfirmation = true,
            confirmLabel = "Export",
            onConfirm = { password -> vm.onExportPasswordConfirmed(password) },
            onDismiss = { vm.dismissExportDialog() },
        )
    }

    when (state.passwordDialogFor) {
        PasswordDialogTarget.IMPORT -> {
            BackupPasswordDialog(
                requireConfirmation = false,
                confirmLabel = "Import",
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

    state.pendingReplace?.let { payload ->
        val message = "Replace all data? This will delete your ${activeCount + archivedCount} " +
            "currently saved vouchers and replace them with this backup. This can't be undone."
        ConfirmReplaceDialog(
            message = message,
            onReplace = { vm.onReplaceConfirmed() },
            onCancel = { vm.onReplaceDismissed() },
        )
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

    AppDialogSurface(onDismissRequest = onDismiss) {
        Text(
            text = if (requireConfirmation) "Set a backup password" else "Backup password",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (requireConfirmation) {
                "This password encrypts the backup file. It is never stored — if you forget it, the backup can't be opened."
            } else {
                "Enter the password this backup was exported with."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Backup password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("backup_password"),
        )
        if (requireConfirmation) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = { Text("Confirm password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("backup_confirm_password"),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        DialogButtonRow(
            cancelLabel = "Cancel",
            onCancel = onDismiss,
            confirmLabel = confirmLabel,
            onConfirm = { onConfirm(password) },
            confirmEnabled = valid,
        )
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
    val date = payload.createdAt
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(BACKUP_DATE_FORMAT)
    val archived = payload.vouchers.count { it.isArchived }

    AppDialogSurface(onDismissRequest = onDismiss) {
        Text(
            text = "Backup from $date · ${payload.vouchers.size} vouchers ($archived archived). Import this backup?",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Merge adds only links you don't already have. Replace deletes everything currently saved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ModeOption(
            label = "Merge with existing data",
            selected = mode == ImportMode.MERGE,
            onClick = { mode = ImportMode.MERGE },
        )
        ModeOption(
            label = "Replace existing data",
            selected = mode == ImportMode.REPLACE,
            onClick = { mode = ImportMode.REPLACE },
        )
        Spacer(modifier = Modifier.height(24.dp))
        DialogButtonRow(
            cancelLabel = "Cancel",
            onCancel = onDismiss,
            confirmLabel = "Import",
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
    message: String,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    AppDialogSurface(onDismissRequest = onCancel) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        DialogButtonRow(
            cancelLabel = "Cancel",
            onCancel = onCancel,
            confirmLabel = "Replace",
            onConfirm = onReplace,
            destructive = true,
        )
    }
}

private val BACKUP_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
