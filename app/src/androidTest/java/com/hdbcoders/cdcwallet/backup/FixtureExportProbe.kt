package com.hdbcoders.cdcwallet.backup

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hdbcoders.cdcwallet.data.backup.BackupService
import com.hdbcoders.cdcwallet.data.model.VoucherBackupPayload
import com.hdbcoders.cdcwallet.dev.DevSeedData
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Fixture-refresh probe (NOT a suite gate): encrypts the REAL debug seed rows
 * ([DevSeedData.vouchers]) under the fixture password and prints the artifact
 * as a single base64 line to logcat (`FIXTURE_B64`). The driver pushes those
 * bytes to `/sdcard/Download/cdcvoucher.backup` via shell so
 * [RealP7BackupMergeAcceptanceTest] can exercise the production import/merge
 * flow against app-generated data.
 *
 * Why not export through the Settings UI on-device: API 24-25 emulator images
 * mount shared storage without the FUSE permission bridge for app uids, so
 * the raw-file path in BackupFileStore cannot write there even with
 * WRITE_EXTERNAL_STORAGE granted (the same reason the suite's backup tests
 * read artifacts back through UiAutomation shell). Encryption here uses the
 * production [BackupService], identical to what the UI flow produces.
 */
@RunWith(AndroidJUnit4::class)
class FixtureExportProbe {

    @Test
    fun printSeedFixtureBase64() {
        Log.i("FIXTURE_B64", android.util.Base64.encodeToString(buildFixtureBytes(), android.util.Base64.NO_WRAP))
    }

    companion object {
        /** Must match RealP7BackupMergeAcceptanceTest's fixture password. */
        const val FIXTURE_PASSWORD = "ttt"

        /**
         * The acceptance fixture: REAL debug seed rows ([DevSeedData.vouchers])
         * re-homed onto the official host with matching single-segment tokens
         * so they pass the production import validator, encrypted under
         * [FIXTURE_PASSWORD] through the production [BackupService] - byte-for-
         * byte what the Settings export flow produces for this data.
         *
         * Shared by [RealP7BackupMergeAcceptanceTest] (in-process - no /sdcard
         * dependency, immune to sibling tests' Downloads hygiene) and this
         * probe (base64 out, for pushing the artifact to devices where a real
         * Downloads file is wanted).
         */
        fun buildFixtureBytes(): ByteArray {
            val rows = DevSeedData.vouchers.map { row ->
                val token = row.id.replace("-", "")
                val url = "https://voucher.redeem.gov.sg/$token?lang=en-US"
                row.copy(url = url, token = token)
            }
            return BackupService()
                .encryptPayload(
                    VoucherBackupPayload(createdAt = Instant.now(), vouchers = rows),
                    FIXTURE_PASSWORD,
                )
        }
    }
}
