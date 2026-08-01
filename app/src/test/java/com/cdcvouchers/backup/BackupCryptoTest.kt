package com.cdcvouchers.backup

import com.cdcvouchers.data.backup.BackupCrypto
import com.cdcvouchers.data.backup.BackupException
import kotlin.experimental.xor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    @Test
    fun roundTripEncryptDecryptRestoresPlaintext() {
        val plaintext = """{"formatVersion":1,"createdAt":123,"vouchers":[]}""".toByteArray()
        val encrypted = BackupCrypto.encrypt(plaintext, "correct horse battery staple")

        assertTrue(encrypted.isNotEmpty())
        assertNotEquals(
            String(plaintext, Charsets.UTF_8),
            String(encrypted, Charsets.UTF_8),
        )
        assertEquals(
            String(plaintext, Charsets.UTF_8),
            String(BackupCrypto.decrypt(encrypted, "correct horse battery staple"), Charsets.UTF_8),
        )
    }

    @Test
    fun wrongPasswordFailsWithGenericMessage() {
        val encrypted = BackupCrypto.encrypt("secret".toByteArray(), "right-password")
        val e = assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(encrypted, "wrong-password")
        }
        assertEquals(BackupException.GENERIC_MESSAGE, e.message)
    }

    @Test
    fun corruptedFileFailsWithGenericMessage() {
        val encrypted = BackupCrypto.encrypt("secret".toByteArray(), "right-password")
        val corrupted = encrypted.copyOf().also { it[it.size - 1] = it[it.size - 1].xor(0x01) }
        val e = assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(corrupted, "right-password")
        }
        assertEquals(BackupException.GENERIC_MESSAGE, e.message)
    }

    @Test
    fun truncatedFileFailsWithGenericMessage() {
        val encrypted = BackupCrypto.encrypt("secret".toByteArray(), "right-password")
        val truncated = encrypted.copyOfRange(0, encrypted.size - 8)
        val e = assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(truncated, "right-password")
        }
        assertEquals(BackupException.GENERIC_MESSAGE, e.message)
    }

    @Test
    fun randomFileFailsWithGenericMessage() {
        val garbage = ByteArray(256) { it.toByte() }
        val e = assertThrows(BackupException::class.java) {
            BackupCrypto.decrypt(garbage, "right-password")
        }
        assertEquals(BackupException.GENERIC_MESSAGE, e.message)
    }

    @Test
    fun encryptedOutputContainsNoPlaintext() {
        val marker = "campaignName"
        val plaintext = """{"$marker":"CDC Vouchers 2026","vouchers":[{"token":"abc"}]}"""
            .toByteArray()
        val encrypted = BackupCrypto.encrypt(plaintext, "password")

        val asText = String(encrypted, Charsets.UTF_8)
        assertTrue("ciphertext must not leak plaintext", !asText.contains(marker))
        assertTrue("ciphertext must not leak plaintext", !asText.contains("CDC Vouchers 2026"))
    }

    @Test
    fun sameInputProducesDifferentCiphertextPerExport() {
        val plaintext = "same".toByteArray()
        val first = BackupCrypto.encrypt(plaintext, "password")
        val second = BackupCrypto.encrypt(plaintext, "password")
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun pbkdf2IterationCountIsOwasBaseline() {
        assertEquals(600_000, BackupCrypto.PBKDF2_ITERATIONS)
    }
}
