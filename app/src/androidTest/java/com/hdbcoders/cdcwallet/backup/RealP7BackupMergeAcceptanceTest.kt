package com.hdbcoders.cdcwallet.backup

import android.util.Base64
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.hdbcoders.cdcwallet.data.RoomVoucherRepository
import com.hdbcoders.cdcwallet.data.backup.BackupFlow
import com.hdbcoders.cdcwallet.data.backup.BackupService
import com.hdbcoders.cdcwallet.data.db.AppDatabase
import com.hdbcoders.cdcwallet.data.db.SqlCipherNative
import com.hdbcoders.cdcwallet.ui.settings.SettingsScreen
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §9.2 P8 merge-import acceptance helper (NOT part of the standard gates).
 *
 * Proves the REAL export artifact round-trips through the REAL production
 * pipeline: the actual `cdcvoucher.backup` written by the P7 phase on this
 * device, decrypted with the P7 password, and merged through
 * [SettingsViewModel.onSummaryModeSelected] -> [BackupFlow.importMerge].
 *
 * The only leg removed from the on-device flow is the OS file picker, which is
 * unusable under automation on this API-24 emulator (DocumentsUI drops
 * injected item taps and its keyboard focus lands on the wrong file). The
 * artifact bytes are read via UiAutomation's shell (the test process lacks the
 * sdcard_rw GID on API 24, so a direct file read is EACCES) and handed to the
 * same `backupBytesProvider` seam the standard backup suite uses.
 *
 * Self-proving: the in-memory database starts with every backup row EXCEPT
 * one; a green run means that exact row came back through the production
 * merge - visible proof on the final list, not an unchanged-looking state.
 */
@RunWith(AndroidJUnit4::class)
class RealP7BackupMergeAcceptanceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun realP7BackupMergesMissingRowThroughProductionFlow() {
        SqlCipherNative.load()

        // --- 1. Read the REAL artifact over the shell (binary-safe via base64) ---
        val b64 = device.executeShellCommand("base64 /sdcard/Download/cdcvoucher.backup").trim()
        val realBytes = Base64.decode(b64, Base64.DEFAULT)
        // "CDCB" magic, as written by BackupCrypto (spec 06 §6.4).
        assertEquals(0x43, realBytes[0].toInt() and 0xFF)
        assertEquals(0x44, realBytes[1].toInt() and 0xFF)
        assertEquals(0x43, realBytes[2].toInt() and 0xFF)
        assertEquals(0x42, realBytes[3].toInt() and 0xFF)

        // --- 2. Decrypt it: proves the P7 password, reveals the payload rows ---
        val payload = BackupService().decryptPayload(realBytes, "acceptance-pass-1")
        assertEquals("real P7 backup must contain rows", false, payload.vouchers.isEmpty())
        val missing = payload.vouchers.first()
        val present = payload.vouchers.drop(1)

        // --- 3. In-memory DB holding every row EXCEPT the target one ---
        val database = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .openHelperFactory(SupportOpenHelperFactory("test-passphrase".toByteArray()))
            .allowMainThreadQueries()
            .build()
        val repository = RoomVoucherRepository(database)
        runBlocking {
            present.forEach { repository.insert(it) }
            assertNull("precondition: target row absent", repository.findByToken(missing.token))
        }

        // --- 4. Real Settings flow, seam-fed with the real bytes ---
        val flow = BackupFlow(repository)
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    backupFlow = flow,
                    repository = repository,
                    onBack = {},
                    backupBytesProvider = { realBytes },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Import backup").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(
                "Enter the password this backup was exported with.",
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("backup_password").performTextInput("acceptance-pass-1")
        composeRule.onNodeWithText("Import").performClick()

        // --- 5. Decrypt -> summary dialog (MERGE default) -> commit ---
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(
                "Backup from ",
                substring = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Import").performClick()

        // --- 6. Prove the production merge restored the missing row ---
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(
                "Couldn't open this backup",
                substring = true,
            ).fetchSemanticsNodes().isEmpty()
        }
        val restored = runBlocking { repository.findByToken(missing.token) }
        assertNotNull("real P7 backup merge must restore the missing row", restored)
        assertEquals(missing.campaignName, restored!!.campaignName)

        database.close()
    }
}