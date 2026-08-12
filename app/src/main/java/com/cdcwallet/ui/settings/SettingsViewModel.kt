package com.cdcwallet.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.AnyRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdcwallet.R
import com.cdcwallet.data.VoucherRepository
import com.cdcwallet.data.backup.BackupFileStore
import com.cdcwallet.data.backup.BackupFlow
import com.cdcwallet.data.backup.InvalidBackupPayloadException
import com.cdcwallet.data.model.VoucherBackupPayload
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
import kotlinx.coroutines.withTimeout

enum class PasswordDialogTarget { IMPORT }

enum class ImportMode { MERGE, REPLACE }

/** Which backup operation is running and blocking the UI behind a progress
 *  dialog. Import covers decrypt + merge/replace; export covers encrypt +
 *  file write. */
enum class BusyPhase { IMPORTING, EXPORTING }

data class SettingsUiState(
    val exportDialogOpen: Boolean = false,
    val passwordDialogFor: PasswordDialogTarget? = null,
    val summaryPayload: VoucherBackupPayload? = null,
    val pendingReplace: VoucherBackupPayload? = null,
    val busyPhase: BusyPhase? = null,
)

sealed interface SettingsEvent {
    /** Localized snackbar: string-resource id (or plurals id when
     *  [pluralCount] is set) + format args. Resolved by the screen so the
     *  active app locale is used. */
    data class Snackbar(
        @AnyRes val resId: Int,
        val formatArgs: List<Any> = emptyList(),
        val pluralCount: Int? = null,
    ) : SettingsEvent
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
            _events.trySend(SettingsEvent.Snackbar(R.string.snackbar_save_failed))
        }
    }

    private fun exportBackup(password: String) {
        _uiState.update { it.copy(busyPhase = BusyPhase.EXPORTING) }
        viewModelScope.launch {
            try {
                val ok = runCatching {
                    withTimeout(BACKUP_OP_TIMEOUT_MS) { backupFlow.export(appContext, password) }
                }.isSuccess
                _events.trySend(
                    SettingsEvent.Snackbar(
                        if (ok) R.string.snackbar_backup_saved else R.string.snackbar_save_failed,
                    ),
                )
            } finally {
                _uiState.update { it.copy(busyPhase = null) }
            }
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
                _events.trySend(SettingsEvent.Snackbar(R.string.backup_generic_error))
            } else {
                pendingImportBytes = bytes
                _uiState.update { it.copy(passwordDialogFor = PasswordDialogTarget.IMPORT) }
            }
        }
    }

    fun onImportPasswordConfirmed(password: String) {
        val bytes = pendingImportBytes ?: return
        pendingImportBytes = null
        _uiState.update { it.copy(passwordDialogFor = null, busyPhase = BusyPhase.IMPORTING) }
        viewModelScope.launch {
            try {
                val payload = withContext(Dispatchers.IO) {
                    withTimeout(BACKUP_OP_TIMEOUT_MS) { backupFlow.decryptBackup(bytes, password) }
                }
                _uiState.update { it.copy(summaryPayload = payload) }
            } catch (e: InvalidBackupPayloadException) {
                // The file decrypted, but its rows are invalid (refactor H4):
                // "check your password" would be the wrong hint here.
                _events.trySend(SettingsEvent.Snackbar(R.string.snackbar_import_failed))
            } catch (e: Exception) {
                _events.trySend(SettingsEvent.Snackbar(R.string.backup_generic_error))
            } finally {
                _uiState.update { it.copy(busyPhase = null) }
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
            ImportMode.MERGE -> {
                _uiState.update { it.copy(busyPhase = BusyPhase.IMPORTING) }
                viewModelScope.launch {
                    try {
                        val imported = withTimeout(BACKUP_OP_TIMEOUT_MS) {
                            backupFlow.importMerge(payload)
                        }
                        _events.trySend(
                            SettingsEvent.Snackbar(
                                R.plurals.snackbar_imported_count,
                                formatArgs = listOf(imported),
                                pluralCount = imported,
                            ),
                        )
                    } catch (e: InvalidBackupPayloadException) {
                        _events.trySend(SettingsEvent.Snackbar(R.string.snackbar_import_failed))
                    } catch (e: Exception) {
                        _events.trySend(SettingsEvent.Snackbar(R.string.backup_generic_error))
                    } finally {
                        _uiState.update { it.copy(busyPhase = null) }
                    }
                }
            }
            ImportMode.REPLACE -> _uiState.update { it.copy(pendingReplace = payload) }
        }
    }

    fun onSummaryDismissed() { _uiState.update { it.copy(summaryPayload = null) } }

    fun onReplaceConfirmed() {
        val payload = _uiState.value.pendingReplace ?: return
        _uiState.update { it.copy(pendingReplace = null, busyPhase = BusyPhase.IMPORTING) }
        viewModelScope.launch {
            try {
                withTimeout(BACKUP_OP_TIMEOUT_MS) { backupFlow.importReplace(payload) }
                _events.trySend(SettingsEvent.Snackbar(R.string.snackbar_backup_imported))
            } catch (e: Exception) {
                _events.trySend(SettingsEvent.Snackbar(R.string.snackbar_import_failed))
            } finally {
                _uiState.update { it.copy(busyPhase = null) }
            }
        }
    }

    fun onReplaceDismissed() { _uiState.update { it.copy(pendingReplace = null) } }

    private companion object {
        /** Hard cap on any backup op (decrypt/export/merge/replace): PBKDF2 at
         *  600k iterations is the dominant cost (~1–2s worst case), so 10s is
         *  generous headroom - fail-soft clears the progress dialog and shows
         *  the operation's error message instead of hanging forever. */
        const val BACKUP_OP_TIMEOUT_MS = 10_000L
    }
}
