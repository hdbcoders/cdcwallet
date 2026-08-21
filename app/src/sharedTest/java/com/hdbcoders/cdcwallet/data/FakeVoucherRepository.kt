package com.hdbcoders.cdcwallet.data

import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.data.model.VoucherRefreshData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory [VoucherRepository] for unit + instrumented tests (P2). Lives in
 * `src/sharedTest` so there is exactly ONE contract-faithful fake for both the
 * JVM and androidTest loops - Android cannot see `test` sources from
 * androidTest, and two copies drifted (refactor D4; test/asset audit B9).
 * Backed by a [MutableStateFlow]; models the Room contracts faithfully:
 * [insert] refuses a duplicate token, [bulkInsert] is atomic and skips
 * duplicate tokens while returning the REAL inserted count (conflict-IGNORE
 * semantics, refactor M10), [replaceAll]/[replaceByTokens] replace wholesale,
 * [archive]/[restore] flip `isArchived`, [delete] removes the row.
 */
class FakeVoucherRepository : VoucherRepository {

    private val _vouchers = MutableStateFlow<List<VoucherGroup>>(emptyList())

    /** Current raw rows, archived or not. */
    fun snapshot(): List<VoucherGroup> = _vouchers.value

    /** Simulates the unique-token backstop rejecting a row (refactor M3). */
    var failInsert = false

    val insertCalls = AtomicInteger(0)
    val findCalls = AtomicInteger(0)

    override fun observeActive(): Flow<List<VoucherGroup>> =
        _vouchers.map { list -> list.filter { !it.isArchived } }

    override fun observeArchived(): Flow<List<VoucherGroup>> =
        _vouchers.map { list -> list.filter { it.isArchived } }

    override fun observeActiveCount(): Flow<Int> =
        _vouchers.map { list -> list.count { !it.isArchived } }

    override fun observeArchivedCount(): Flow<Int> =
        _vouchers.map { list -> list.count { it.isArchived } }

    override fun observeById(id: String): Flow<VoucherGroup?> =
        _vouchers.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun insert(voucher: VoucherGroup): Boolean {
        insertCalls.incrementAndGet()
        if (failInsert) return false
        val rows = _vouchers.value
        if (rows.any { it.token == voucher.token }) return false
        _vouchers.value = rows + voucher
        return true
    }

    override suspend fun updateFromRefresh(id: String, data: VoucherRefreshData) {
        _vouchers.value = _vouchers.value.map { row ->
            if (row.id == id) {
                row.copy(
                    campaignName = data.campaignName,
                    validityStatus = data.validityStatus,
                    expiryDate = data.expiryDate,
                    categoryBalances = data.categoryBalances,
                    lastRefreshedAt = data.lastRefreshedAt,
                    lastRefreshError = null,
                )
            } else {
                row
            }
        }
    }

    override suspend fun recordRefreshFailure(id: String, error: String) {
        _vouchers.value = _vouchers.value.map { row ->
            if (row.id == id) row.copy(lastRefreshError = error) else row
        }
    }

    override suspend fun archive(id: String) {
        _vouchers.value = _vouchers.value.map { row ->
            if (row.id == id) row.copy(isArchived = true) else row
        }
    }

    override suspend fun restore(id: String) {
        _vouchers.value = _vouchers.value.map { row ->
            if (row.id == id) row.copy(isArchived = false) else row
        }
    }

    override suspend fun delete(id: String) {
        _vouchers.value = _vouchers.value.filterNot { it.id == id }
    }

    override suspend fun findByToken(token: String): VoucherGroup? {
        findCalls.incrementAndGet()
        return _vouchers.value.firstOrNull { it.token == token }
    }

    override suspend fun findAll(): List<VoucherGroup> = _vouchers.value

    override suspend fun replaceAll(vouchers: List<VoucherGroup>) {
        _vouchers.value = vouchers
    }

    override suspend fun replaceByTokens(vouchers: List<VoucherGroup>, tokens: Set<String>) {
        _vouchers.value = _vouchers.value.filterNot { it.token in tokens } + vouchers
    }

    override suspend fun bulkInsert(vouchers: List<VoucherGroup>): Int {
        if (vouchers.isEmpty()) return 0
        // Refactor D4: conflict-IGNORE semantics like the real repository -
        // rows whose token already exists are skipped, and the returned count
        // is the number of rows actually inserted.
        val rows = _vouchers.value
        val existingTokens = rows.mapTo(HashSet()) { it.token }
        val fresh = vouchers.filter { it.token !in existingTokens }
        _vouchers.value = rows + fresh
        return fresh.size
    }
}
