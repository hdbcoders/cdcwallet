package com.cdcwallet.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Single failure mode for backup decrypt/import (spec 06 §6.3): the same
 * generic message for wrong password, corrupted file, unsupported version —
 * never distinguishing causes, which would leak probing information.
 */
class BackupException(message: String) : Exception(message) {
    companion object {
        const val GENERIC_MESSAGE =
            "Couldn't open this backup. Check your password and try again."
    }
}

/**
 * AES-256-GCM authenticated encryption with PBKDF2-HMAC-SHA256 key derivation
 * (spec 06 §6.4). File layout: 4-byte magic "CDCB" || 16-byte salt || 12-byte
 * IV || GCM ciphertext (+ 16-byte tag). The backup password is never stored —
 * derivation happens per operation and is unrecoverable if forgotten.
 */
object BackupCrypto {

    /** OWASP baseline iteration count for PBKDF2-HMAC-SHA256 (spec 06 §6.4). */
    const val PBKDF2_ITERATIONS = 600_000

    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private val MAGIC = byteArrayOf(0x43, 0x44, 0x43, 0x42) // "CDCB"

    fun encrypt(plaintext: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return MAGIC + salt + iv + ciphertext
    }

    fun decrypt(bytes: ByteArray, password: String): ByteArray {
        try {
            require(bytes.size > MAGIC.size + SALT_LENGTH + IV_LENGTH)
            check(MAGIC.contentEquals(bytes.copyOfRange(0, MAGIC.size)))
            var offset = MAGIC.size
            val salt = bytes.copyOfRange(offset, offset + SALT_LENGTH).also { offset += SALT_LENGTH }
            val iv = bytes.copyOfRange(offset, offset + IV_LENGTH).also { offset += IV_LENGTH }
            val ciphertext = bytes.copyOfRange(offset, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw BackupException(BackupException.GENERIC_MESSAGE)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKey =
        SecretKeySpec(pbkdf2Sha256(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS), "AES")

    /**
     * PBKDF2-HMAC-SHA256 (RFC 2898) implemented directly over [Mac]. The
     * framework provider SecretKeyFactory "PBKDF2WithHmacSHA256" only exists on
     * API 26+, which would break backup export/import on API 24-25. Output is
     * byte-identical to the standard derivation, so backups interoperate across
     * Android versions (and with test vectors). HmacSHA256 is available since API 1.
     */
    internal fun pbkdf2Sha256(password: String, salt: ByteArray, iterations: Int, keyLengthBits: Int): ByteArray {
        require(iterations > 0)
        val prf = Mac.getInstance("HmacSHA256")
        prf.init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hashLength = prf.macLength
        val dkLength = keyLengthBits / 8
        val blockCount = (dkLength + hashLength - 1) / hashLength
        val derived = ByteArray(dkLength)
        var derivedOffset = 0
        for (block in 1..blockCount) {
            val firstBlock = ByteArray(salt.size + 4)
            salt.copyInto(firstBlock)
            firstBlock[salt.size] = (block ushr 24).toByte()
            firstBlock[salt.size + 1] = (block ushr 16).toByte()
            firstBlock[salt.size + 2] = (block ushr 8).toByte()
            firstBlock[salt.size + 3] = block.toByte()
            var u = prf.doFinal(firstBlock)
            val xor = u.copyOf()
            repeat(iterations - 1) {
                u = prf.doFinal(u)
                for (i in xor.indices) xor[i] = (xor[i].toInt() xor u[i].toInt()).toByte()
            }
            val copyLength = minOf(hashLength, dkLength - derivedOffset)
            xor.copyInto(derived, derivedOffset, 0, copyLength)
            derivedOffset += copyLength
        }
        return derived
    }
}
