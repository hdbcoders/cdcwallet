package com.hdbcoders.cdcwallet.backup

import com.hdbcoders.cdcwallet.data.backup.dedupeByToken
import com.hdbcoders.cdcwallet.data.backup.mergeVouchers
import com.hdbcoders.cdcwallet.data.model.CategoryBalance
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class BackupMergeTest {

    private fun voucher(token: String, name: String = token) = VoucherGroup(
        id = "id-$token",
        token = token,
        url = "https://voucher.redeem.gov.sg/$token",
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

    @Test
    fun mergeVouchersDropsInPayloadDuplicates() {
        val existing = listOf(voucher("A"))
        val incoming = listOf(voucher("A", "dup-of-existing"), voucher("A", "dup-in-payload"), voucher("B"))

        // The existing-set filter removes the token that matches; the merge
        // function itself keeps in-payload duplicates (dedup is importMerge's
        // job via distinctBy) - so only B is a new token, but the two As are
        // both filtered against the existing set. Assert the filter result.
        val merged = mergeVouchers(existing, incoming)

        assertEquals(listOf("B"), merged.map { it.token })
    }

    @Test
    fun mergeVouchersIsCaseSensitive() {
        val existing = listOf(voucher("ABC"))
        val incoming = listOf(voucher("abc", "lowercase-copy"))

        val merged = mergeVouchers(existing, incoming)

        assertEquals(listOf("abc"), merged.map { it.token })
    }

    @Test
    fun dedupeByTokenKeepsFirstOccurrenceCaseSensitively() {
        val dups = listOf(
            voucher("A", "first"),
            voucher("A", "second"),
            voucher("a", "case-different"),
            voucher("B"),
        )

        val deduped = dedupeByToken(dups)

        // Canonical comparison (01 §1.4) is case-sensitive: "a" is distinct
        // from "A"; the two "A"s collapse to the first occurrence.
        assertEquals(listOf("A", "a", "B"), deduped.map { it.token })
        assertEquals("first", deduped[0].campaignName)
    }
}
