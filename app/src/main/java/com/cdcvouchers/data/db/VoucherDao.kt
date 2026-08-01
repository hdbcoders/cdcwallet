package com.cdcvouchers.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cdcvouchers.data.model.CategoryBalance
import com.cdcvouchers.data.model.ValidityStatus
import com.cdcvouchers.data.model.VoucherGroup
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface VoucherDao {

    /** Defensive backstop only — callers check for duplicates first (spec 01 §1.3). */
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

    @Query("SELECT * FROM voucher_groups WHERE token = :token LIMIT 1")
    suspend fun findByToken(token: String): VoucherGroup?

    /** All rows, archived or not — used by backup export and import merge (Package 6). */
    @Query("SELECT * FROM voucher_groups")
    suspend fun findAll(): List<VoucherGroup>

    @Query("DELETE FROM voucher_groups")
    suspend fun deleteAll(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(vouchers: List<VoucherGroup>)
}
