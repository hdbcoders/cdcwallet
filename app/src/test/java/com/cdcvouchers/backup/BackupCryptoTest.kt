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

    @Test
    fun pbkdf2Sha256MatchesStandardVector() {
        // Published PBKDF2-HMAC-SHA256 vectors (password="password", salt="salt",
        // 256-bit key). The pure-Kotlin derivation must match the standard byte
        // for byte so backups stay interchangeable with SecretKeyFactory-based
        // encryption on API 26+.
        val expected1 = "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b"
        val expected4096 = "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a"
        val salt = "salt".toByteArray(Charsets.UTF_8)

        val hex1 = BackupCrypto.pbkdf2Sha256("password", salt, 1, 256).toHex()
        val hex4096 = BackupCrypto.pbkdf2Sha256("password", salt, 4096, 256).toHex()

        assertEquals(expected1, hex1)
        assertEquals(expected4096, hex4096)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
