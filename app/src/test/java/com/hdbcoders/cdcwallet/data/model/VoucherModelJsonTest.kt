package com.hdbcoders.cdcwallet.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class VoucherModelJsonTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun sampleVoucher() = VoucherGroup(
        id = "uuid-1",
        token = "TokEn1",
        url = "https://voucher.redeem.gov.sg/TokEn1?lang=en-US",
        campaignName = "CDC Vouchers 2026",
        validityStatus = ValidityStatus.ACTIVE,
        expiryDate = LocalDate.of(2026, 12, 31),
        categoryBalances = listOf(
            CategoryBalance("heartland", BigDecimal("250.50")),
            CategoryBalance("supermarket", BigDecimal("150.00")),
        ),
        dateAdded = Instant.parse("2026-07-01T08:00:00Z"),
        lastRefreshedAt = Instant.parse("2026-08-01T09:30:00Z"),
        lastRefreshError = null,
        isArchived = false,
    )

    @Test
    fun `voucher group round-trips through json`() {
        val original = sampleVoucher()
        val encoded = json.encodeToString(VoucherGroup.serializer(), original)
        val decoded = json.decodeFromString(VoucherGroup.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `bigdecimal precision survives serialization`() {
        val balance = CategoryBalance("heartland", BigDecimal("250.50"))
        val encoded = json.encodeToString(CategoryBalance.serializer(), balance)
        val decoded = json.decodeFromString(CategoryBalance.serializer(), encoded)
        assertEquals(BigDecimal("250.50"), decoded.remainingValue)
    }

    @Test
    fun `unverified state with nulls round-trips`() {
        val unverified = sampleVoucher().copy(
            validityStatus = ValidityStatus.UNVERIFIED,
            expiryDate = null,
            categoryBalances = emptyList(),
            lastRefreshedAt = null,
            lastRefreshError = "Website data failed to parse",
        )
        val encoded = json.encodeToString(VoucherGroup.serializer(), unverified)
        val decoded = json.decodeFromString(VoucherGroup.serializer(), encoded)
        assertEquals(unverified, decoded)
    }

    @Test
    fun `backup payload round-trips`() {
        val payload = VoucherBackupPayload(
            formatVersion = 1,
            createdAt = Instant.parse("2026-08-01T10:00:00Z"),
            vouchers = listOf(sampleVoucher()),
        )
        val encoded = json.encodeToString(VoucherBackupPayload.serializer(), payload)
        val decoded = json.decodeFromString(VoucherBackupPayload.serializer(), encoded)
        assertEquals(payload, decoded)
    }
}
