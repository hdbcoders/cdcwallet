package com.cdcvouchers.backup

import com.cdcvouchers.data.backup.mergeVouchers
import com.cdcvouchers.data.model.CategoryBalance
import com.cdcvouchers.data.model.ValidityStatus
import com.cdcvouchers.data.model.VoucherGroup
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class BackupMergeTest {

    private fun voucher(token: String, name: String = token) = VoucherGroup(
        id = "id-$token",
        token = token,
        url = "https://voucher.redeem.gov.sg/groups/$token",
        campaignName = name,
        validityStatus = ValidityStatus.ACTIVE,
        expiryDate = null,
        categoryBalances = listOf(CategoryBalance("heartland", BigDecimal("50"))),
        dateAdded = Instant.now(),
        lastRefreshedAt = null,
        lastRefreshError = null,
    )

    @Test
    fun mergeSkipsExactTokenDuplicates() {
        val existing = listOf(voucher("ABC"), voucher("DEF"))
        val incoming = listOf(voucher("ABC", "incoming-dup"), voucher("GHI"))

        val merged = mergeVouchers(existing, incoming)

        assertEquals(listOf("GHI"), merged.map { it.token })
    }

    @Test
    fun mergeKeepsTokensDifferingOnlyInCase() {
        val existing = listOf(voucher("ABC"))
        val incoming = listOf(voucher("abc", "case-different"))

        // Canonical comparison (01 §1.4) is case-sensitive: "abc" is NOT a
        // duplicate of "ABC" and must be imported.
        val merged = mergeVouchers(existing, incoming)

        assertEquals(listOf("abc"), merged.map { it.token })
    }

    @Test
    fun mergeKeepsEverythingWhenNoOverlap() {
        val existing = listOf(voucher("ABC"))
        val incoming = listOf(voucher("XYZ"), voucher("QRS"))

        val merged = mergeVouchers(existing, incoming)

        assertEquals(listOf("XYZ", "QRS"), merged.map { it.token })
    }

    @Test
    fun mergeKeepsIncomingOrderAndExistingEntriesTakePrecedence() {
        val existing = listOf(voucher("MID", "local"))
        val incoming = listOf(voucher("FIRST"), voucher("MID", "backup"), voucher("LAST"))

        val merged = mergeVouchers(existing, incoming)

        // Existing "MID" wins; the backup's MID is dropped; order is incoming order.
        assertEquals(listOf("FIRST", "LAST"), merged.map { it.token })
    }
}
