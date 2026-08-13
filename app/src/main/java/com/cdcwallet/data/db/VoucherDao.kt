package com.cdcwallet.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cdcwallet.data.model.CategoryBalance
import com.cdcwallet.data.model.ValidityStatus
import com.cdcwallet.data.model.VoucherGroup
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface VoucherDao {

    /** Defensive backstop only - callers check for duplicates first (spec 01 §1.3). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(voucher: VoucherGroup)

    /** Safe no-op (returns 0) if the row no longer exists (spec 02 §2.7). */
    @Query(
        """
        UPDATE voucher_groups
        SET campaignName = :campaignName,
            validityStatus = :validityStatus,
            expiryDate = :expiryDate,
            categoryBalances = :categoryBalances,
            lastRefreshedAt = :lastRefreshedAt,
            lastRefreshError = NULL
        WHERE id = :id
        """
    )
    suspend fun updateFromRefresh(
        id: String,
        campaignName: String,
        validityStatus: ValidityStatus,
        expiryDate: LocalDate?,
        categoryBalances: List<CategoryBalance>,
        lastRefreshedAt: Instant,
    ): Int

    /** Fail-soft: sets lastRefreshError without touching cached fields. */
    @Query("UPDATE voucher_groups SET lastRefreshError = :error WHERE id = :id")
    suspend fun recordRefreshFailure(id: String, error: String): Int

    @Query("UPDATE voucher_groups SET isArchived = :isArchived WHERE id = :id")
    suspend fun setArchived(id: String, isArchived: Boolean): Int

    @Query("DELETE FROM voucher_groups WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM voucher_groups WHERE isArchived = 0")
    fun observeActive(): Flow<List<VoucherGroup>>

    @Query("SELECT * FROM voucher_groups WHERE isArchived = 1")
    fun observeArchived(): Flow<List<VoucherGroup>>

    @Query("SELECT COUNT(*) FROM voucher_groups WHERE isArchived = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM voucher_groups WHERE isArchived = 1")
    fun observeArchivedCount(): Flow<Int>

    @Query("SELECT * FROM voucher_groups WHERE token = :token LIMIT 1")
    suspend fun findByToken(token: String): VoucherGroup?

    /** Single row by id, archived or not - used by the detail screen so a tap
     *  on an archived voucher can still open it (spec 05 §5.4). Emits null when
     *  no row matches. */
    @Query("SELECT * FROM voucher_groups WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<VoucherGroup?>

    /** All rows, archived or not - used by backup export and import merge (Package 6). */
    @Query("SELECT * FROM voucher_groups")
    suspend fun findAll(): List<VoucherGroup>

    @Query("DELETE FROM voucher_groups")
    suspend fun deleteAll(): Int

    /**
     * Bulk insert with conflict-IGNORE semantics (refactor M10): duplicates are
     * skipped INSIDE the single transaction instead of aborting it - callers
     * count real insertions from the returned row ids (-1 = skipped).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(vouchers: List<VoucherGroup>): List<Long>
}
