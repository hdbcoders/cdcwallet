package com.hdbcoders.cdcwallet.backup

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hdbcoders.cdcwallet.data.backup.BackupService
import com.hdbcoders.cdcwallet.data.model.CategoryBalance
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import com.hdbcoders.cdcwallet.data.model.VoucherBackupPayload
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

/**
 * §9.2 forged-backup probe (NOT a suite gate): encrypts a payload holding a
 * NONOFFICIAL URL under a VALID password, prints the artifact as base64 to
 * logcat so the acceptance driver can push it to Downloads as
 * `cdcvoucher.backup`. Import validation must reject it (wrong-vs-valid
 * password discrimination is tested separately in the suite).
 */
@RunWith(AndroidJUnit4::class)
class ForgeBackupProbe {

    @Test
    fun printForgedBackupBase64() {
        val forged = VoucherGroup(
            id = "evil",
            token = "evil",
            url = "https://evil.example.com/evil",
            campaignName = "Forged",
            validityStatus = ValidityStatus.ACTIVE,
            expiryDate = null,
            categoryBalances = listOf(CategoryBalance("heartland", BigDecimal("1"))),
            dateAdded = Instant.now(),
            lastRefreshedAt = null,
            lastRefreshError = null,
            isArchived = false,
        )
        val bytes = BackupService()
            .encryptPayload(VoucherBackupPayload(createdAt = Instant.now(), vouchers = listOf(forged)), "acceptance-pass-1")
        Log.i("FORGE_B64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
    }
}