package com.cdcwallet.data.backup

import android.content.Context
import android.net.Uri
import com.cdcwallet.data.VoucherRepository
import com.cdcwallet.data.model.VoucherBackupPayload
import com.cdcwallet.data.model.VoucherGroup
import com.cdcwallet.data.token.VoucherToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Backup orchestration (spec 06). Export assembles the payload from the
 * repository and writes the encrypted file; import performs no network
 * request of any kind - restored rows come straight from the payload and
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
     * local, using the canonical case-sensitive comparison (01 §1.4) - never a
     * second implementation. Existing entries always take precedence.
     *
     * @return the number of rows actually imported
     */
    suspend fun importMerge(payload: VoucherBackupPayload): Int {
        val existing = repository.findAll()
        // dedupe via the canonical comparison so in-payload duplicates can
        // never trip the unique index inside bulkInsert (01 §1.4 semantics).
        val merged = dedupeByToken(mergeVouchers(existing, payload.vouchers))
        if (merged.isEmpty()) return 0
        repository.bulkInsert(merged)
        return merged.size
    }

    /** Replace mode (spec 06 §6.3) - the most destructive operation in the app. */
    suspend fun importReplace(payload: VoucherBackupPayload) {
        repository.replaceAll(dedupeByToken(payload.vouchers))
    }
}

/**
 * Pure merge computation, kept testable. Duplicate detection uses the
 * canonical `VoucherToken.isDuplicate` (01 §1.4 / 06 §6.3) - never a second
 * implementation. O(n+m): the existing token set is built once.
 */
fun mergeVouchers(
    existing: List<VoucherGroup>,
    incoming: List<VoucherGroup>,
): List<VoucherGroup> {
    val existingTokens = existing.mapTo(HashSet()) { it.token }
    return incoming.filter { candidate ->
        existingTokens.none { existingToken ->
            VoucherToken.isDuplicate(candidate.token, existingToken)
        }
    }
}

/**
 * Dedupe a list by token using the canonical comparison, keeping the first
 * occurrence of each token (01 §1.4 semantics for in-payload duplicates).
 */
fun dedupeByToken(vouchers: List<VoucherGroup>): List<VoucherGroup> {
    val seen = HashSet<String>()
    return vouchers.filter { voucher ->
        seen.none { seenToken -> VoucherToken.isDuplicate(voucher.token, seenToken) } &&
            seen.add(voucher.token)
    }
}
