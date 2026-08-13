package com.cdcwallet.data

import com.cdcwallet.data.model.VoucherGroup
import com.cdcwallet.data.model.VoucherRefreshData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [VoucherRepository] for instrumented coordinator tests (mirrors
 * the unit-test fake - androidTest cannot see the `test` source set). Backed
 * by a [MutableStateFlow]; mirrors Room semantics: [insert] refuses a
 * duplicate token, [archive]/[restore] flip `isArchived`, [delete] removes
 * the row.
 */
class FakeVoucherRepository : VoucherRepository {

    private val _vouchers = MutableStateFlow<List<VoucherGroup>>(emptyList())

    /** Current raw rows, archived or not. */
    fun snapshot(): List<VoucherGroup> = _vouchers.value

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

    override suspend fun findByToken(token: String): VoucherGroup? =
        _vouchers.value.firstOrNull { it.token == token }

    override suspend fun findAll(): List<VoucherGroup> = _vouchers.value

    override suspend fun replaceAll(vouchers: List<VoucherGroup>) {
        _vouchers.value = vouchers
    }

    override suspend fun replaceByTokens(vouchers: List<VoucherGroup>, tokens: Set<String>) {
        _vouchers.value = _vouchers.value.filterNot { it.token in tokens } + vouchers
    }

    override suspend fun bulkInsert(vouchers: List<VoucherGroup>): Int {
        if (vouchers.isEmpty()) return 0
        // Refactor D4: conflict-IGNORE semantics like the real repository.
        val rows = _vouchers.value
        val existingTokens = rows.mapTo(HashSet()) { it.token }
        val fresh = vouchers.filter { it.token !in existingTokens }
        _vouchers.value = rows + fresh
        return fresh.size
    }
}
