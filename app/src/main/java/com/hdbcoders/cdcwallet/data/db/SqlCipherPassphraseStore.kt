package com.hdbcoders.cdcwallet.data.db

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Owns the SQLCipher database passphrase. The passphrase itself is a fresh random
 * 256-bit value per install; it is wrapped (encrypted) with a Keystore-backed AES key
 * and only the wrapped blob is persisted (spec 01 §1.5). Keystore keys are not
 * directly exportable, so this is the standard wrap-pattern.
 */
class SqlCipherPassphraseStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun obtainPassphrase(): String {
        val wrapped = prefs.getString(KEY_WRAPPED, null)
        if (wrapped != null) return unwrap(wrapped)
        val passphrase = generatePassphrase()
        // Synchronous persistence (refactor M1): the wrapped key must be
        // durable on disk BEFORE the database is opened - a process kill in
        // between would otherwise leave the database without a recoverable
        // key. Runs on the DatabaseBootstrap's IO scope, never on main.
        prefs.edit().putString(KEY_WRAPPED, wrap(passphrase)).commit()
        return passphrase
    }

    private fun generatePassphrase(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return tryCreateKey(requireStrongBox = true)
    }

    private fun tryCreateKey(requireStrongBox: Boolean): SecretKey {
        try {
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
            if (requireStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE,
            )
            generator.init(builder.build())
            return generator.generateKey()
        } catch (e: Exception) {
            if (requireStrongBox) return tryCreateKey(requireStrongBox = false)
            throw e
        }
    }

    private fun wrap(passphrase: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val cipherText = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            SEPARATOR +
            Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

    private fun unwrap(wrapped: String): String {
        val (ivBase64, cipherTextBase64) = wrapped.split(SEPARATOR, limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keystoreKey(),
            GCMParameterSpec(TAG_BITS, Base64.decode(ivBase64, Base64.NO_WRAP)),
        )
        val plainText = cipher.doFinal(Base64.decode(cipherTextBase64, Base64.NO_WRAP))
        return String(plainText, Charsets.UTF_8)
    }

    private companion object {
        const val PREFS_NAME = "voucher_secure_prefs"
        const val KEY_WRAPPED = "wrapped_db_passphrase"
        const val KEY_ALIAS = "voucher_db_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val SEPARATOR = ":"
    }
}
