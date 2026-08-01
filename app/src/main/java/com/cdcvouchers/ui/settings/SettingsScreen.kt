package com.cdcvouchers.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.backup.BackupException
import com.cdcvouchers.data.backup.BackupFileStore
import com.cdcvouchers.data.backup.BackupFlow
import com.cdcvouchers.data.model.VoucherBackupPayload
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings screen (spec 06): encrypted backup export to Downloads and import
 * via the system file picker. Import never touches the network — restored rows
 * are plain cached entries from the payload. The `backupBytesProvider` seam
 * exists for instrumented tests (system picker is not drivable there);
 * production passes null and uses the real picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    backupFlow: BackupFlow,
    repository: VoucherRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backupBytesProvider: (() -> ByteArray?)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var exportDialogOpen by remember { mutableStateOf(false) }
    var pendingImportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var passwordDialogFor by remember { mutableStateOf<PasswordDialogTarget?>(null) }
    var summaryPayload by remember { mutableStateOf<VoucherBackupPayload?>(null) }
    var pendingReplace by remember { mutableStateOf<VoucherBackupPayload?>(null) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }

    val activeCount by repository.observeActive().collectAsState(initial = emptyList())
    val archivedCount by repository.observeArchived().collectAsState(initial = emptyList())

    fun runExport(password: String) {
        exportDialogOpen = false
        scope.launch {
            val result = runCatching { backupFlow.export(context, password) }
            snackbarHostState.showSnackbar(
                if (result.isSuccess) "Backup saved to Downloads" else "Couldn't save the backup",
            )
        }
    }

    // API 24-28 write to the public Downloads directory directly (scoped
    // storage starts at 29), which needs WRITE_EXTERNAL_STORAGE granted at
    // runtime on API 23+ — request it before the first export on those
    // versions; API 29+ uses MediaStore and needs nothing.
    val exportPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val password = pendingExportPassword ?: return@rememberLauncherForActivityResult
        pendingExportPassword = null
        if (granted) {
            runExport(password)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Couldn't save the backup")
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = runCatching { BackupFileStore.open(context, uri).readBytes() }.getOrNull()
                if (bytes == null) {
                    snackbarHostState.showSnackbar(BackupException.GENERIC_MESSAGE)
                } else {
                    pendingImportBytes = bytes
                    passwordDialogFor = PasswordDialogTarget.IMPORT
                }
            }
        }
    }

    fun startImport() {
        val provided = backupBytesProvider?.invoke()
        if (provided != null) {
            pendingImportBytes = provided
            passwordDialogFor = PasswordDialogTarget.IMPORT
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
                text = "Backup",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(
                onClick = { exportDialogOpen = true },
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

    if (exportDialogOpen) {
        BackupPasswordDialog(
            requireConfirmation = true,
            confirmLabel = "Export",
            onConfirm = { password ->
                exportDialogOpen = false
                val needsLegacyPermission =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED
                if (needsLegacyPermission) {
                    pendingExportPassword = password
                    exportPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    runExport(password)
                }
            },
            onDismiss = { exportDialogOpen = false },
        )
    }

    when (passwordDialogFor) {
        PasswordDialogTarget.IMPORT -> {
            val bytes = pendingImportBytes
            if (bytes != null) {
                BackupPasswordDialog(
                    requireConfirmation = false,
                    confirmLabel = "Import",
                    onConfirm = { password ->
                        passwordDialogFor = null
                        pendingImportBytes = null
                        val payload = runCatching { backupFlow.decryptBackup(bytes, password) }
                            .getOrNull()
                        if (payload == null) {
                            scope.launch {
                                snackbarHostState.showSnackbar(BackupException.GENERIC_MESSAGE)
                            }
                        } else {
                            summaryPayload = payload
                        }
                    },
                    onDismiss = {
                        passwordDialogFor = null
                        pendingImportBytes = null
                    },
                )
            }
        }
        null -> Unit
    }

    summaryPayload?.let { payload ->
        val archived = payload.vouchers.count { it.isArchived }
        BackupSummaryDialog(
            payload = payload,
            currentCount = activeCount.size + archivedCount.size,
            onImport = { mode ->
                summaryPayload = null
                when (mode) {
                    ImportMode.MERGE -> scope.launch {
                        val imported = backupFlow.importMerge(payload)
                        val message = if (imported == 1) {
                            "1 voucher imported"
                        } else {
                            "$imported vouchers imported"
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                    ImportMode.REPLACE -> pendingReplace = payload
                }
            },
            onDismiss = { summaryPayload = null },
        )
    }

    pendingReplace?.let { payload ->
        val message = "Replace all data? This will delete your ${activeCount.size + archivedCount.size} " +
            "currently saved vouchers and replace them with this backup. This can't be undone."
        ConfirmReplaceDialog(
            message = message,
            onReplace = {
                pendingReplace = null
                scope.launch {
                    backupFlow.importReplace(payload)
                    snackbarHostState.showSnackbar("Backup imported")
                }
            },
            onCancel = { pendingReplace = null },
        )
    }
}

private enum class PasswordDialogTarget { IMPORT }

private enum class ImportMode { MERGE, REPLACE }

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

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp)) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(password) }, enabled = valid) {
                        Text(confirmLabel)
                    }
                }
            }
        }
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp)) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onImport(mode) }) { Text("Import") }
                }
            }
        }
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
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onReplace,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text("Replace")
                    }
                }
            }
        }
    }
}

private val BACKUP_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
