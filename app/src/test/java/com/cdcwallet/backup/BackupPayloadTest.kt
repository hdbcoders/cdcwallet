package com.cdcwallet.backup

import com.cdcwallet.data.backup.BackupService
import com.cdcwallet.data.model.CategoryBalance
import com.cdcwallet.data.model.ValidityStatus
import com.cdcwallet.data.model.VoucherBackupPayload
import com.cdcwallet.data.model.VoucherGroup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class BackupPayloadTest {

    private val service = BackupService()

    private fun voucher() = VoucherGroup(
        id = "id-1",
        token = "Token1",
        url = "https://voucher.redeem.gov.sg/groups/Token1",
        campaignName = "CDC Vouchers 2026",
        validityStatus = ValidityStatus.ACTIVE,
        expiryDate = null,
        categoryBalances = listOf(
            CategoryBalance("heartland", BigDecimal("50")),
            CategoryBalance("supermarket", BigDecimal("25.5")),
        ),
        dateAdded = Instant.ofEpochMilli(1_700_000_000_000),
        lastRefreshedAt = null,
        lastRefreshError = null,
        isArchived = true,
    )

    @Test
    fun payloadRoundTripsThroughEncryptedFile() {
        val payload = VoucherBackupPayload(
            createdAt = Instant.ofEpochMilli(1_700_000_000_000),
            vouchers = listOf(voucher()),
        )

        val encrypted = service.encryptPayload(payload, "backup-password")
        val restored = service.decryptPayload(encrypted, "backup-password")

        assertEquals(payload, restored)
    }

    @Test
    fun encryptedFileContainsNoPlaintextPayloadFields() {
        val payload = VoucherBackupPayload(
            createdAt = Instant.now(),
            vouchers = listOf(voucher()),
        )
        val encrypted = service.encryptPayload(payload, "backup-password")

        val asText = String(encrypted, Charsets.UTF_8)
        for (field in listOf(
            "formatVersion",
            "createdAt",
            "vouchers",
            "campaignName",
            "categoryBalances",
            "isArchived",
            "CDC Vouchers 2026",
        )) {
            assertTrue("encrypted bytes must not contain '$field'", !asText.contains(field))
        }
    }

    @Test
    fun payloadContainsOnlyVoucherGroupFields() {
        val payload = VoucherBackupPayload(
            createdAt = Instant.now(),
            vouchers = listOf(voucher()),
        )
        val json = Json { encodeDefaults = true }
        val root = json.parseToJsonElement(json.encodeToString(payload)).jsonObject

        // Envelope carries exactly: formatVersion, createdAt, vouchers.
        assertEquals(
            setOf("formatVersion", "createdAt", "vouchers"),
            root.keys,
        )

        // Each voucher entry carries exactly the VoucherGroup field set -
        // nothing WebView-related (cache, cookies, browser state) can leak in.
        val voucherFields = root.getValue("vouchers").jsonArray.map { it.jsonObject.keys }
        assertTrue(voucherFields.isNotEmpty())
        val expected = setOf(
            "id",
            "token",
            "url",
            "campaignName",
            "validityStatus",
            "expiryDate",
            "categoryBalances",
            "dateAdded",
            "lastRefreshedAt",
            "lastRefreshError",
            "isArchived",
        )
        voucherFields.forEach { keys ->
            assertEquals(expected, keys)
        }
    }

    @Test
    fun unsupportedFormatVersionFailsWithGenericMessage() {
        val json = """{"formatVersion":99,"createdAt":123,"vouchers":[]}"""
        val encrypted = com.cdcwallet.data.backup.BackupCrypto.encrypt(
            json.toByteArray(),
            "password",
        )
        val e = org.junit.Assert.assertThrows(
            com.cdcwallet.data.backup.BackupException::class.java,
        ) {
            service.decryptPayload(encrypted, "password")
        }
        assertEquals(com.cdcwallet.data.backup.BackupException.GENERIC_MESSAGE, e.message)
    }
}
