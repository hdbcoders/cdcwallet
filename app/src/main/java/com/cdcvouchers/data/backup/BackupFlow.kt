package com.cdcvouchers.data.backup

import android.content.Context
import android.net.Uri
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.model.VoucherBackupPayload
import com.cdcvouchers.data.model.VoucherGroup
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
        // distinctBy uses case-sensitive token equality — identical to
        // VoucherToken.isDuplicate, so in-payload duplicates can never trip the
        // unique index inside bulkInsert (01 §1.4 semantics preserved).
        val merged = mergeVouchers(existing, payload.vouchers).distinctBy { it.token }
        if (merged.isEmpty()) return 0
        repository.bulkInsert(merged)
        return merged.size
    }

    /** Replace mode (spec 06 §6.3) — the most destructive operation in the app. */
    suspend fun importReplace(payload: VoucherBackupPayload) {
        repository.replaceAll(payload.vouchers.distinctBy { it.token })
    }
}

/**
 * Pure merge computation, kept testable. Duplicate detection is exact
 * case-sensitive token equality (01 §1.4 / 06 §6.3) — the same semantics as
 * `VoucherToken.isDuplicate` — do not add another token comparison here.
 * O(n+m): the existing token set is built once.
 */
fun mergeVouchers(
    existing: List<VoucherGroup>,
    incoming: List<VoucherGroup>,
): List<VoucherGroup> {
    val existingTokens = existing.mapTo(HashSet()) { it.token }
    return incoming.filter { candidate ->
        !existingTokens.contains(candidate.token)
    }
}
