package com.cdcvouchers.data.backup

import android.content.Context
import android.net.Uri
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.model.VoucherBackupPayload
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.data.token.VoucherToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Backup orchestration (spec 06). Export assembles the payload from the
 * repository and writes the encrypted file; import performs no network
 * request of any kind — restored rows come straight from the payload and
 * behave as normal cached entries from then on.
 */
class BackupFlow(
    private val repository: VoucherRepository,
    private val service: BackupService = BackupService(),
) {

    /** Export all rows (active + archived) to Downloads, encrypted. */
    suspend fun export(context: Context, password: String): Uri = withContext(Dispatchers.IO) {
        val payload = VoucherBackupPayload(
            createdAt = Instant.now(),
            vouchers = repository.findAll(),
        )
        BackupFileStore.writeToDownloads(context, service.encryptPayload(payload, password))
    }

    /** @throws BackupException with the generic message on any failure. */
    fun decryptBackup(bytes: ByteArray, password: String): VoucherBackupPayload =
        service.decryptPayload(bytes, password)

    /**
     * Merge mode (spec 06 §6.3): import only rows whose token isn't already
     * local, using the canonical case-sensitive comparison (01 §1.4) — never a
     * second implementation. Existing entries always take precedence.
     *
     * @return the number of rows actually imported
     */
    suspend fun importMerge(payload: VoucherBackupPayload): Int {
        val existing = repository.findAll()
        var imported = 0
        for (candidate in mergeVouchers(existing, payload.vouchers)) {
            if (repository.insert(candidate)) imported++
        }
        return imported
    }

    /** Replace mode (spec 06 §6.3) — the most destructive operation in the app. */
    suspend fun importReplace(payload: VoucherBackupPayload) {
        repository.replaceAll(payload.vouchers)
    }
}

/**
 * Pure merge computation, kept testable. Duplicate detection delegates to the
 * single canonical implementation `VoucherToken.isDuplicate` (case-sensitive,
 * spec 01 §1.4 / 06 §6.3) — do not add another token comparison here.
 */
fun mergeVouchers(
    existing: List<VoucherGroup>,
    incoming: List<VoucherGroup>,
): List<VoucherGroup> =
    incoming.filter { candidate ->
        existing.none { VoucherToken.isDuplicate(candidate.token, it.token) }
    }
