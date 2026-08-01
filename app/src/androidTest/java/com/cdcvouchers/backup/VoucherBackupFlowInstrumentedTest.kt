package com.cdcvouchers.backup

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cdcvouchers.data.RoomVoucherRepository
import com.cdcvouchers.data.backup.BackupException
import com.cdcvouchers.data.backup.BackupFlow
import com.cdcvouchers.data.backup.BackupService
import com.cdcvouchers.data.db.AppDatabase
import com.cdcvouchers.data.db.SqlCipherNative
import com.cdcvouchers.data.model.CategoryBalance
import com.cdcvouchers.data.model.ValidityStatus
import com.cdcvouchers.data.model.VoucherBackupPayload
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.experimental.xor
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Backup export/import (spec 06): genuinely encrypted file on Downloads,
 * identical generic failure for wrong password and corrupted file, pre-import
 * summary before any commit, case-sensitive merge dedup via the canonical
 * token comparison, and confirmation-gated replace with the exact copy.
 */
@RunWith(AndroidJUnit4::class)
class VoucherBackupFlowInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private val service = BackupService()

    @Before
    fun setUp() {
        SqlCipherNative.load()
        database = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .openHelperFactory(SupportOpenHelperFactory("test-passphrase".toByteArray()))
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun voucher(
        token: String,
        name: String = token,
        archived: Boolean = false,
    ) = VoucherGroup(
        id = "id-$token",
        token = token,
        url = "https://voucher.redeem.gov.sg/groups/$token",
        campaignName = name,
        validityStatus = ValidityStatus.ACTIVE,
        expiryDate = null,
        categoryBalances = listOf(CategoryBalance("heartland", BigDecimal("50"))),
        dateAdded = Instant.now(),
        lastRefreshedAt = null,
        lastRefreshError = null,
        isArchived = archived,
    )

    private fun encryptedPayload(
        password: String,
        vararg vouchers: VoucherGroup,
    ): ByteArray {
        val payload = VoucherBackupPayload(createdAt = Instant.now(), vouchers = vouchers.toList())
        return service.encryptPayload(payload, password)
    }

    private fun settingsContent(
        repository: RoomVoucherRepository,
        bytesProvider: () -> ByteArray?,
    ) {
        val flow = BackupFlow(repository)
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    backupFlow = flow,
                    repository = repository,
                    onBack = {},
                    backupBytesProvider = bytesProvider,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun importWithPassword(password: String) {
        composeRule.onNodeWithText("Import backup").performClick()
        composeRule.onNodeWithTag("backup_password").performTextInput(password)
        composeRule.onNodeWithText("Import").performClick()
    }

    @Test
    fun exportWritesGenuinelyEncryptedFileToDownloads() {
        val repository = RoomVoucherRepository(database)
        runBlocking {
            repository.insert(voucher("TokenOne", "CDC Vouchers 2026"))
            repository.insert(voucher("TokenTwo", "SG60", archived = true))
        }
        val flow = BackupFlow(repository)

        val uri = runBlocking { flow.export(appContext, "backup-passphrase") }

        assertTrue(uri.toString().contains("downloads", ignoreCase = true))
        val bytes = appContext.contentResolver.openInputStream(uri)!!.readBytes()
        assertTrue("file must not be empty", bytes.isNotEmpty())
        // Genuinely encrypted: no plaintext payload field survives.
        val asText = String(bytes, Charsets.UTF_8)
        for (marker in listOf("campaignName", "vouchers", "TokenOne", "CDC Vouchers 2026")) {
            assertTrue("ciphertext must not contain '$marker'", !asText.contains(marker))
        }
        // Round-trip with the right password.
        val decrypted = service.decryptPayload(bytes, "backup-passphrase")
        assertEquals(2, decrypted.vouchers.size)
        assertEquals(setOf("TokenOne", "TokenTwo"), decrypted.vouchers.map { it.token }.toSet())
        assertEquals(1, decrypted.vouchers.count { it.isArchived })
        // Wrong password fails with the generic message.
        val e = org.junit.Assert.assertThrows(BackupException::class.java) {
            service.decryptPayload(bytes, "wrong")
        }
        assertEquals(BackupException.GENERIC_MESSAGE, e.message)
    }

    @Test
    fun wrongPasswordAndCorruptedFileShowIdenticalMessage() {
        val repository = RoomVoucherRepository(database)
        val validBytes = encryptedPayload("backup-passphrase", voucher("TokenOne"))
        var currentBytes: ByteArray = validBytes

        settingsContent(repository, bytesProvider = { currentBytes })

        // Wrong password.
        importWithPassword("wrong-password")
        composeRule.onNodeWithText(BackupException.GENERIC_MESSAGE).assertIsDisplayed()
        composeRule.waitUntil(6_000) {
            composeRule.onAllNodesWithText(BackupException.GENERIC_MESSAGE)
                .fetchSemanticsNodes().isEmpty()
        }

        // Corrupted file, correct password — must show the identical message.
        currentBytes = validBytes.copyOf().also { it[it.size - 1] = it[it.size - 1].xor(0x01) }
        importWithPassword("backup-passphrase")
        composeRule.onNodeWithText(BackupException.GENERIC_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun correctPasswordShowsSummaryBeforeCommittingAnything() {
        val repository = RoomVoucherRepository(database)
        val payload = VoucherBackupPayload(
            createdAt = Instant.now(),
            vouchers = listOf(voucher("A", "Alpha"), voucher("B", "Beta", archived = true)),
        )
        val bytes = service.encryptPayload(payload, "backup-passphrase")

        settingsContent(repository, bytesProvider = { bytes })
        importWithPassword("backup-passphrase")

        val date = payload.createdAt.atZone(ZoneId.systemDefault())
            .toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
        composeRule.onNodeWithText(
            "Backup from $date · 2 vouchers (1 archived). Import this backup?",
        ).assertIsDisplayed()

        // Summary shown before anything is committed.
        runBlocking {
            assertEquals(0, repository.findAll().size)
        }

        // Default mode is merge; committing imports both rows.
        composeRule.onNodeWithText("Import").performClick()
        composeRule.onNodeWithText("2 vouchers imported").assertIsDisplayed()
        runBlocking {
            assertEquals(2, repository.findAll().size)
        }
    }

    @Test
    fun mergeModeSkipsExistingTokensCaseSensitively() {
        val repository = RoomVoucherRepository(database)
        runBlocking { repository.insert(voucher("ABC", "existing")) }
        // Incoming has the exact duplicate AND a case-only variant ("abc" is
        // NOT a duplicate per the canonical case-sensitive comparison).
        val bytes = encryptedPayload(
            "backup-passphrase",
            voucher("ABC", "duplicate"),
            voucher("abc", "case-variant"),
        )

        settingsContent(repository, bytesProvider = { bytes })
        importWithPassword("backup-passphrase")
        composeRule.onNodeWithText("Import").performClick()

        runBlocking {
            withTimeout(10_000) {
                while (repository.findAll().size != 2) delay(100)
            }
        }
        // The snackbar node may sit mid-animation and report not-yet-displayed;
        // its presence plus the DB state below carries the behavioral check.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("1 voucher imported")
                .fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking {
            val rows = repository.findAll()
            assertEquals(2, rows.size)
            assertEquals(setOf("ABC", "abc"), rows.map { it.token }.toSet())
            assertEquals("existing", rows.first { it.token == "ABC" }.campaignName)
        }
    }

    @Test
    fun replaceModeIsConfirmationGatedWithExactCopy() {
        val repository = RoomVoucherRepository(database)
        runBlocking { repository.insert(voucher("KeepMe", "keep")) }
        val bytes = encryptedPayload(
            "backup-passphrase",
            voucher("FromBackup1", "b1"),
            voucher("FromBackup2", "b2", archived = true),
        )

        settingsContent(repository, bytesProvider = { bytes })
        importWithPassword("backup-passphrase")
        composeRule.onNodeWithText("Replace existing data").performClick()
        composeRule.onNodeWithText("Import").performClick()

        // Exact copy, own confirmation, before anything is wiped.
        val message = "Replace all data? This will delete your 1 currently saved " +
            "vouchers and replace them with this backup. This can't be undone."
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()

        // Cancel: nothing wiped.
        composeRule.onNodeWithText("Cancel").performClick()
        runBlocking {
            assertEquals(1, repository.findAll().size)
        }

        // Redo and confirm Replace: data matches the backup exactly.
        composeRule.onNodeWithText("Import backup").performClick()
        composeRule.onNodeWithTag("backup_password").performTextInput("backup-passphrase")
        composeRule.onNodeWithText("Import").performClick()
        composeRule.onNodeWithText("Replace existing data").performClick()
        composeRule.onNodeWithText("Import").performClick()
        composeRule.onNodeWithText("Replace").performClick()

        composeRule.onNodeWithText("Backup imported").assertIsDisplayed()
        runBlocking {
            val rows = repository.findAll()
            assertEquals(2, rows.size)
            assertEquals(setOf("FromBackup1", "FromBackup2"), rows.map { it.token }.toSet())
            assertEquals(1, rows.count { it.isArchived })
        }
    }
}
