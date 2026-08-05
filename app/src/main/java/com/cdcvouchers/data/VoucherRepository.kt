package com.cdcvouchers.data

import androidx.room.withTransaction
import com.cdcvouchers.data.db.AppDatabase
import com.cdcvouchers.data.db.VoucherDao
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.data.model.VoucherRefreshData
import kotlinx.coroutines.flow.Flow

/**
 * Repository surface (spec 01 §1.3). Other packages call this interface, never
 * Room directly.
 */
interface VoucherRepository {
    fun observeActive(): Flow<List<VoucherGroup>>
    fun observeArchived(): Flow<List<VoucherGroup>>
    fun observeActiveCount(): Flow<Int>
    fun observeArchivedCount(): Flow<Int>

    /** Single row by id, archived or not — detail screen needs it so tapping an
     *  archived voucher still opens it (spec 05 §5.4). Null when no row. */
    fun observeById(id: String): Flow<VoucherGroup?>

    /** @return false if a row with the same token already exists (defensive backstop). */
    suspend fun insert(voucher: VoucherGroup): Boolean

    /**
     * Insert many rows, skipping any whose token already exists (defensive
     * backstop, same semantics as [insert]). Returns the number inserted.
     */
    suspend fun bulkInsert(vouchers: List<VoucherGroup>): Int

    /** Safe no-op if the row no longer exists (spec 02 §2.7). */
    suspend fun updateFromRefresh(id: String, data: VoucherRefreshData)

    /** Fail-soft: records the error without touching other cached fields. */
    suspend fun recordRefreshFailure(id: String, error: String)

    suspend fun archive(id: String)
    suspend fun restore(id: String)
    suspend fun delete(id: String)
    suspend fun findByToken(token: String): VoucherGroup?
    suspend fun findAll(): List<VoucherGroup>
    suspend fun replaceAll(vouchers: List<VoucherGroup>)
}

class RoomVoucherRepository(
    private val database: AppDatabase,
    private val dao: VoucherDao = database.voucherDao(),
) : VoucherRepository {

    override fun observeActive(): Flow<List<VoucherGroup>> = dao.observeActive()

    override fun observeArchived(): Flow<List<VoucherGroup>> = dao.observeArchived()

    override fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()

    override fun observeArchivedCount(): Flow<Int> = dao.observeArchivedCount()

    override fun observeById(id: String): Flow<VoucherGroup?> = dao.observeById(id)

    override suspend fun insert(voucher: VoucherGroup): Boolean = try {
        dao.insert(voucher)
        true
    } catch (e: android.database.sqlite.SQLiteConstraintException) {
        false
    }

    override suspend fun updateFromRefresh(id: String, data: VoucherRefreshData) {
        dao.updateFromRefresh(
            id = id,
            campaignName = data.campaignName,
            validityStatus = data.validityStatus,
            expiryDate = data.expiryDate,
            categoryBalances = data.categoryBalances,
            lastRefreshedAt = data.lastRefreshedAt,
        )
    }

    override suspend fun recordRefreshFailure(id: String, error: String) {
        dao.recordRefreshFailure(id, error)
    }

    override suspend fun archive(id: String) {
        dao.setArchived(id, true)
    }

    override suspend fun restore(id: String) {
        dao.setArchived(id, false)
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    override suspend fun findByToken(token: String): VoucherGroup? =
        dao.findByToken(token)

    override suspend fun findAll(): List<VoucherGroup> = dao.findAll()

    override suspend fun replaceAll(vouchers: List<VoucherGroup>) {
        database.withTransaction {
            dao.deleteAll()
            if (vouchers.isNotEmpty()) dao.insertAll(vouchers)
        }
    }

    override suspend fun bulkInsert(vouchers: List<VoucherGroup>): Int {
        if (vouchers.isEmpty()) return 0
        return try {
            database.withTransaction {
                dao.insertAll(vouchers)
            }
            vouchers.size
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // Defensive backstop (same semantics as insert): a duplicate token
            // must not crash a bulk import. Room rolls back the aborted batch
            // transaction, so fall back to per-row inserts in fresh
            // transactions — valid rows still land; duplicates are skipped.
            vouchers.count { voucher ->
                try {
                    database.withTransaction { dao.insert(voucher) }
                    true
                } catch (e2: android.database.sqlite.SQLiteConstraintException) {
                    false
                }
            }
        }
    }
}
