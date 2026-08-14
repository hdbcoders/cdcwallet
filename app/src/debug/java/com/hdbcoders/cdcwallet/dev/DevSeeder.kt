package com.hdbcoders.cdcwallet.dev

import com.hdbcoders.cdcwallet.data.VoucherRepository

/**
 * Debug-only seeder (spec dev tooling). Inserts the fixed [DevSeedData] rows
 * through the real repository so they behave exactly like user vouchers.
 * Never runs in release (lives under `src/debug`, guarded by the debug
 * Application subclass).
 */
object DevSeeder {

    /**
     * Insert the dev rows that are missing; existing dev rows (and all user
     * rows) are left untouched. Refactor D1: repairing partial fixture loss,
     * not all-or-nothing - the repository's bulkInsert skips duplicate tokens
     * (conflict-IGNORE), so this is idempotent.
     */
    suspend fun seedIfEmpty(repository: VoucherRepository) {
        val existing = repository.findAll()
        val missing = DevSeedData.vouchers.filterNot { row ->
            existing.any { it.token == row.token }
        }
        if (missing.isEmpty()) return
        repository.bulkInsert(missing)
    }

    /**
     * Delete and re-insert the dev rows regardless of current state, in ONE
     * transaction (refactor D2) so a partial failure never leaves the fixture
     * set half-deleted. Only the dev-seed tokens are removed - the user's own
     * vouchers are untouched.
     */
    suspend fun forceSeed(repository: VoucherRepository) {
        val devTokens = DevSeedData.vouchers.mapTo(HashSet()) { it.token }
        repository.replaceByTokens(DevSeedData.vouchers, devTokens)
    }
}