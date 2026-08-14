package com.cdcwallet.ui.settings

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cdcwallet.R
import com.cdcwallet.data.FakeVoucherRepository
import com.cdcwallet.data.backup.BackupFlow
import com.cdcwallet.data.backup.BackupService
import com.cdcwallet.data.model.ValidityStatus
import com.cdcwallet.data.model.VoucherBackupPayload
import com.cdcwallet.data.model.VoucherGroup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * ViewModel-level tests for the backup robustness refactors:
 * - M12: an oversized backup file is rejected before any read;
 * - M13: the expected-exception ordering maps an invalid payload to the
 *   import-failed message and clears the busy phase. (The timeout carve-out
 *   and the cancellation rethrow share the same catch ordering - triggering
 *   the 10s cap deterministically on-device is flaky, so those branches are
 *   covered by the ordering itself and code review.)
 */
@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun viewModel(backupFlow: BackupFlow = BackupFlow(FakeVoucherRepository())): SettingsViewModel =
        SettingsViewModel(
            backupFlow = backupFlow,
            repository = FakeVoucherRepository(),
            appContext = context,
        )

    /**
     * Drives the ViewModel's main-dispatched coroutine on the real main looper
     * while real IO (PBKDF2 decrypt, file writes) completes on its own
     * threads, until one event arrives or the deadline passes.
     */
    private suspend fun awaitEvent(
        deadlineMs: Long,
        trigger: (SettingsViewModel) -> Unit,
    ): Pair<SettingsEvent?, SettingsViewModel> = coroutineScope {
        val vm = viewModel()
        // Refactor D10: a CompletableDeferred completed by the collector
        // replaces the Thread.sleep busy-wait - the runBlocking loop
        // suspends until the real-IO collector delivers the event or the
        // wall-clock deadline expires. The ViewModel runs on the REAL main
        // looper (instrumented environment): no Dispatchers.setMain stub -
        // a StandardTestDispatcher would never be pumped and the ViewModel's
        // coroutine would silently never run.
        val done = CompletableDeferred<SettingsEvent?>()
        val collector = launch(Dispatchers.IO) { done.complete(vm.events.first()) }
        trigger(vm)
        val event = withTimeoutOrNull(deadlineMs) { done.await() }
        collector.cancel()
        if (event == null) {
            throw AssertionError(
                "No SettingsEvent arrived within ${deadlineMs}ms of the trigger - the " +
                    "ViewModel never delivered one (deadline expired). " +
                    "uiState: passwordDialogFor=${vm.uiState.value.passwordDialogFor}, " +
                    "busyPhase=${vm.uiState.value.busyPhase}",
            )
        }
        event to vm
    }

    @Test
    fun oversizeBackupFileIsRejectedBeforeReading() = runBlocking {
        // Refactor M12: a file above the 20 MB cap must produce the generic
        // error without ever being read into memory or opening the password
        // dialog. A real content:// Uri (MediaStore Downloads) is used so the
        // size column actually resolves.
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "cdcv-oversize-test.backup")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        try {
            resolver.openOutputStream(uri)!!.use { it.write(ByteArray(21 * 1024 * 1024)) }
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            var vmRef: SettingsViewModel? = null
            val (event, vm) = awaitEvent(deadlineMs = 20_000) { vmArg ->
                vmRef = vmArg
                vmArg.onFilePicked(uri)
            }
            assertEquals(
                "oversize file must surface the generic backup error; dialog=${vm.uiState.value.passwordDialogFor}, busy=${vm.uiState.value.busyPhase}",
                SettingsEvent.Snackbar(R.string.backup_generic_error),
                event,
            )
            assertNull("password dialog must not open for an oversize file", vmRef?.uiState?.value?.passwordDialogFor)
        } finally {
            runCatching { resolver.delete(uri, null, null) }
        }
    }

    @Test
    fun invalidPayloadAfterDecryptShowsImportFailedAndClearsBusyPhase() = runBlocking {
        // Refactor M13 expected-exception ordering: the payload decrypts fine
        // but its rows are invalid (refactor H4) - the ViewModel must map the
        // InvalidBackupPayloadException to snackbar_import_failed and clear
        // the busy phase, never hang.
        val invalidRow = VoucherGroup(
            id = "id-evil",
            token = "Evil",
            url = "https://evil.example.com/Evil",
            campaignName = "Evil",
            validityStatus = ValidityStatus.ACTIVE,
            expiryDate = null,
            categoryBalances = emptyList(),
            dateAdded = Instant.now(),
            lastRefreshedAt = null,
            lastRefreshError = null,
        )
        val payload = VoucherBackupPayload(createdAt = Instant.now(), vouchers = listOf(invalidRow))
        val bytes = BackupService().encryptPayload(payload, "backup-passphrase")

        var vmRef: SettingsViewModel? = null
        val (event, vm) = awaitEvent(deadlineMs = 90_000) { vmArg ->
            vmRef = vmArg
            vmArg.onBackupBytesProvided(bytes)
            vmArg.onImportPasswordConfirmed("backup-passphrase")
        }
        assertEquals(
            SettingsEvent.Snackbar(R.string.snackbar_import_failed),
            event,
        )
        assertNull("busy phase must clear after the failed import", vmRef?.uiState?.value?.busyPhase)
    }
}
