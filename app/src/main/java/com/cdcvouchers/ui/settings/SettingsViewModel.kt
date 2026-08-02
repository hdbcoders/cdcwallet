package com.cdcvouchers.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.backup.BackupException
import com.cdcvouchers.data.backup.BackupFileStore
import com.cdcvouchers.data.backup.BackupFlow
import com.cdcvouchers.data.model.VoucherBackupPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PasswordDialogTarget { IMPORT }

enum class ImportMode { MERGE, REPLACE }

data class SettingsUiState(
    val exportDialogOpen: Boolean = false,
    val passwordDialogFor: PasswordDialogTarget? = null,
    val summaryPayload: VoucherBackupPayload? = null,
    val pendingReplace: VoucherBackupPayload? = null,
)

sealed interface SettingsEvent {
    data class Snackbar(val message: String) : SettingsEvent
    data object RequestLegacyPermission : SettingsEvent
}

class SettingsViewModel(
    private val backupFlow: BackupFlow,
    private val repository: VoucherRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val activeCount: StateFlow<Int> = repository.observeActiveCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val archivedCount: StateFlow<Int> = repository.observeArchivedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private var pendingImportBytes: ByteArray? = null
    private var pendingExportPassword: String? = null

    fun openExportDialog() { _uiState.update { it.copy(exportDialogOpen = true) } }
    fun dismissExportDialog() { _uiState.update { it.copy(exportDialogOpen = false) } }

    fun onExportPasswordConfirmed(password: String) {
        _uiState.update { it.copy(exportDialogOpen = false) }
        val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            appContext.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            pendingExportPassword = password
            _events.trySend(SettingsEvent.RequestLegacyPermission)
        } else {
            exportBackup(password)
        }
    }

    fun onLegacyPermissionResult(granted: Boolean) {
        val password = pendingExportPassword
        pendingExportPassword = null
        if (granted && password != null) {
            exportBackup(password)
        } else {
            _events.trySend(SettingsEvent.Snackbar("Couldn't save the backup"))
        }
    }

    private fun exportBackup(password: String) {
        viewModelScope.launch {
            val ok = runCatching { backupFlow.export(appContext, password) }.isSuccess
            _events.trySend(
                SettingsEvent.Snackbar(if (ok) "Backup saved to Downloads" else "Couldn't save the backup"),
            )
        }
    }

    /** Test seam: bytes injected instead of the system file picker. */
    fun onBackupBytesProvided(bytes: ByteArray) {
        pendingImportBytes = bytes
        _uiState.update { it.copy(passwordDialogFor = PasswordDialogTarget.IMPORT) }
    }

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { BackupFileStore.open(appContext, uri).readBytes() }.getOrNull()
            }
            if (bytes == null) {
                _events.trySend(SettingsEvent.Snackbar(BackupException.GENERIC_MESSAGE))
            } else {
                pendingImportBytes = bytes
                _uiState.update { it.copy(passwordDialogFor = PasswordDialogTarget.IMPORT) }
            }
        }
    }

    fun onImportPasswordConfirmed(password: String) {
        val bytes = pendingImportBytes ?: return
        pendingImportBytes = null
        _uiState.update { it.copy(passwordDialogFor = null) }
        viewModelScope.launch {
            val payload = withContext(Dispatchers.IO) {
                runCatching { backupFlow.decryptBackup(bytes, password) }.getOrNull()
            }
            if (payload == null) {
                _events.trySend(SettingsEvent.Snackbar(BackupException.GENERIC_MESSAGE))
            } else {
                _uiState.update { it.copy(summaryPayload = payload) }
            }
        }
    }

    fun onImportDialogDismissed() {
        pendingImportBytes = null
        _uiState.update { it.copy(passwordDialogFor = null) }
    }

    fun onSummaryModeSelected(mode: ImportMode) {
        val payload = _uiState.value.summaryPayload ?: return
        _uiState.update { it.copy(summaryPayload = null) }
        when (mode) {
            ImportMode.MERGE -> viewModelScope.launch {
                val imported = backupFlow.importMerge(payload)
                val message = if (imported == 1) "1 voucher imported" else "$imported vouchers imported"
                _events.trySend(SettingsEvent.Snackbar(message))
            }
            ImportMode.REPLACE -> _uiState.update { it.copy(pendingReplace = payload) }
        }
    }

    fun onSummaryDismissed() { _uiState.update { it.copy(summaryPayload = null) } }

    fun onReplaceConfirmed() {
        val payload = _uiState.value.pendingReplace ?: return
        _uiState.update { it.copy(pendingReplace = null) }
        viewModelScope.launch {
            runCatching { backupFlow.importReplace(payload) }
                .onSuccess { _events.trySend(SettingsEvent.Snackbar("Backup imported")) }
                .onFailure { _events.trySend(SettingsEvent.Snackbar("Couldn't import the backup")) }
        }
    }

    fun onReplaceDismissed() { _uiState.update { it.copy(pendingReplace = null) } }
}
