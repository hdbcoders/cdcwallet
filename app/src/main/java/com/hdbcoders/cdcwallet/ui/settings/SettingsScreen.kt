package com.hdbcoders.cdcwallet.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.data.VoucherRepository
import com.hdbcoders.cdcwallet.data.backup.BackupFlow
import com.hdbcoders.cdcwallet.ui.theme.ThemeMode
import com.hdbcoders.cdcwallet.ui.theme.ThemeModeStore
import kotlinx.coroutines.flow.collect

/**
 * Settings screen (spec 06): encrypted backup export to Downloads and import
 * via the system file picker. Import never touches the network - restored rows
 * are plain cached entries from the payload. The `backupBytesProvider` seam
 * exists for instrumented tests (system picker is not drivable there);
 * production passes null and uses the real picker. Dialog/flow state lives in
 * [SettingsViewModel].
 *
 * H2 (god-file split): the theme-picker sections live in
 * SettingsAppearanceSection.kt and the backup dialogs in
 * SettingsBackupDialogs.kt; this file owns the screen scaffold and the
 * ViewModel wiring.
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
                darkModeActive = themeModeStore.mode == ThemeMode.DARK,
            )
            DarkAppearanceSection(
                selected = themeModeStore.darkTheme,
                onSelect = { themeModeStore.setDarkPalette(it) },
                darkModeActive = themeModeStore.mode == ThemeMode.DARK,
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
