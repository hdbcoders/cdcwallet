package com.cdcwallet.dev

import com.cdcwallet.data.VoucherRepository

/**
 * Debug-only seeder (spec dev tooling). Inserts the fixed [DevSeedData] rows
 * through the real repository so they behave exactly like user vouchers.
 * Never runs in release (lives under `src/debug`, guarded by the debug
 * Application subclass).
 */
object DevSeeder {

    /**
     * Insert all dev rows if the DB has none of the dev seed tokens. Safe on a
     * fresh install or cleared data; no-op when already seeded.
     */
    suspend fun seedIfEmpty(repository: VoucherRepository) {
        val devTokens = DevSeedData.vouchers.mapTo(HashSet()) { it.token }
        val existing = repository.findAll()
        val present = existing.any { devTokens.contains(it.token) }
        if (present) return
        repository.bulkInsert(DevSeedData.vouchers)
    }

    /**
     * Delete and re-insert the dev rows regardless of current state. Only the
     * dev-seed tokens are removed — the user's own vouchers are untouched.
     */
    suspend fun forceSeed(repository: VoucherRepository) {
        val devTokens = DevSeedData.vouchers.mapTo(HashSet()) { it.token }
        val existing = repository.findAll()
        val devRows = existing.filter { devTokens.contains(it.token) }
        if (devRows.isNotEmpty()) {
            devRows.forEach { repository.delete(it.id) }
        }
        repository.bulkInsert(DevSeedData.vouchers)
    }
}