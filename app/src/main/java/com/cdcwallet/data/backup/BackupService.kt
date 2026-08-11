package com.cdcwallet.data.backup

import com.cdcwallet.data.model.VoucherBackupPayload
import kotlinx.serialization.json.Json

/**
 * Payload <-> encrypted-file codec (spec 06 §6.2/§6.4). The payload contains
 * only VoucherGroup rows - never WebView cache, cookies, or browser state
 * (there is none persisted, per 02 §2.7). Format version and creation
 * timestamp live inside the encrypted payload.
 */
class BackupService {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encryptPayload(payload: VoucherBackupPayload, password: String): ByteArray =
        BackupCrypto.encrypt(json.encodeToString(payload).toByteArray(Charsets.UTF_8), password)

    /**
     * @throws BackupException with the generic message for wrong password,
     * corrupted/truncated input, or an unsupported format version - the UI
     * must never distinguish between these.
     */
    fun decryptPayload(bytes: ByteArray, password: String): VoucherBackupPayload {
        val plaintext = BackupCrypto.decrypt(bytes, password)
        val payload = runCatching {
            json.decodeFromString<VoucherBackupPayload>(plaintext.toString(Charsets.UTF_8))
        }.getOrElse {
            throw BackupException(BackupException.GENERIC_MESSAGE)
        }
        if (payload.formatVersion != 1) {
            throw BackupException(BackupException.GENERIC_MESSAGE)
        }
        return payload
    }
}
