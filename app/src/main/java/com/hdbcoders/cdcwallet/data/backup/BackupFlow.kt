package com.hdbcoders.cdcwallet.data.backup

import android.content.Context
import android.net.Uri
import com.hdbcoders.cdcwallet.data.VoucherRepository
import com.hdbcoders.cdcwallet.data.model.VoucherBackupPayload
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.data.token.VoucherToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Backup orchestration (spec 06). Export assembles the payload from the
 * repository and writes the encrypted file; import performs no network
 * request of any kind - restored rows come straight from the payload and
 * behave as normal cached entries from then on.
 *
 * Every import is validated row-by-row BEFORE any database mutation
 * (refactor H4): the strict add-flow URL policy, the case-sensitive
 * token/URL consistency, route-safe ids, bounded fields, and nonnegative
 * balances. An invalid payload is rejected wholesale - replace mode never
 * deletes existing rows for a backup that would not import. Synthetic test
 * hosts go through the validator seam, never through the production policy.
 */
class BackupFlow(
    private val repository: VoucherRepository,
    private val service: BackupService = BackupService(),
    private val importValidator: BackupImportValidator = BackupImportValidator(),
) {

    /** Export all rows (active + archived) to Downloads, encrypted. */
    suspend fun export(context: Context, password: String): Uri = withContext(Dispatchers.IO) {
        val payload = VoucherBackupPayload(
            createdAt = Instant.now(),
            vouchers = repository.findAll(),
        )
        BackupFileStore.writeToDownloads(context, service.encryptPayload(payload, password))
    }

    /**
     * @throws BackupException with the generic message for wrong password,
     * corrupted/truncated input, or an unsupported format version - the UI
     * must never distinguish between these.
     * @throws InvalidBackupPayloadException when the payload decrypts but its
     * rows are invalid (refactor H4) - the UI may distinguish this from a
     * decryption failure.
     */
    fun decryptBackup(bytes: ByteArray, password: String): VoucherBackupPayload {
        val payload = service.decryptPayload(bytes, password)
        // Fail early: an invalid backup never even reaches the merge/replace
        // summary dialog.
        importValidator.validate(payload)
        return payload
    }

    /**
     * Merge mode (spec 06 §6.3): import only rows whose token isn't already
     * local, using the canonical case-sensitive comparison (01 §1.4) - never a
     * second implementation. Existing entries always take precedence.
     *
     * @return the number of rows actually imported
     * @throws InvalidBackupPayloadException when any incoming row is invalid
     */
    suspend fun importMerge(payload: VoucherBackupPayload): Int {
        val validated = importValidator.validate(payload)
        val existing = repository.findAll()
        // dedupe via the canonical comparison so in-payload duplicates can
        // never trip the unique index inside bulkInsert (01 §1.4 semantics).
        val merged = dedupeByToken(mergeVouchers(existing, validated))
        if (merged.isEmpty()) return 0
        // Refactor M11: report the ACTUAL insertion count the repository
        // performed (conflict-ignore skips any token that raced in), not the
        // size of the filtered list.
        return repository.bulkInsert(merged)
    }

    /**
     * Replace mode (spec 06 §6.3) - the most destructive operation in the app.
     * Validation runs BEFORE [VoucherRepository.replaceAll]: an invalid backup
     * never wipes the existing rows (refactor H4).
     *
     * @throws InvalidBackupPayloadException when any incoming row is invalid
     */
    suspend fun importReplace(payload: VoucherBackupPayload) {
        val validated = importValidator.validate(payload)
        repository.replaceAll(dedupeByToken(validated))
    }
}

/**
 * Pure merge computation, kept testable. Duplicate detection uses the
 * canonical `VoucherToken.isDuplicate` semantics (01 §1.4 / 06 §6.3): exact,
 * case-sensitive token equality - which is precisely HashSet equality, so the
 * set's O(1) `contains` IS the canonical comparison (never a second
 * implementation, just the same predicate over a hash index). O(n+m).
 */
fun mergeVouchers(
    existing: List<VoucherGroup>,
    incoming: List<VoucherGroup>,
): List<VoucherGroup> {
    val existingTokens = existing.mapTo(HashSet()) { it.token }
    return incoming.filter { candidate ->
        candidate.token !in existingTokens
    }
}

/**
 * Dedupe a list by token using the canonical comparison, keeping the first
 * occurrence of each token (01 §1.4 semantics for in-payload duplicates).
 * O(n): HashSet `add` returns false exactly when the canonical comparison
 * already matched (see mergeVouchers).
 */
fun dedupeByToken(vouchers: List<VoucherGroup>): List<VoucherGroup> {
    val seen = HashSet<String>()
    return vouchers.filter { voucher ->
        seen.add(voucher.token)
    }
}
