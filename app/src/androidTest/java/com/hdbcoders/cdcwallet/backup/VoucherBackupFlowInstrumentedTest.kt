package com.hdbcoders.cdcwallet.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hdbcoders.cdcwallet.data.RoomVoucherRepository
import com.hdbcoders.cdcwallet.data.backup.BackupException
import com.hdbcoders.cdcwallet.data.backup.BackupFileStore
import com.hdbcoders.cdcwallet.data.backup.BackupFlow
import com.hdbcoders.cdcwallet.data.backup.BackupService
import com.hdbcoders.cdcwallet.data.db.AppDatabase
import com.hdbcoders.cdcwallet.data.db.SqlCipherNative
import com.hdbcoders.cdcwallet.data.model.CategoryBalance
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import com.hdbcoders.cdcwallet.data.model.VoucherBackupPayload
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.experimental.xor
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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

    /** Bytes of the user's pre-existing `cdcvoucher.backup` (if any), snapshotted
     *  before the test. The app's own export deletes previous exports first
     *  (spec 06 §6.2: one predictable file), so the tests would otherwise
     *  replace it - the tearDown restores these bytes verbatim. */
    private var preExistingBackupBytes: ByteArray? = null

    /** Start of the test run, used to identify files THIS run created. */
    private var testStartMillis = 0L

    @Before
    fun setUp() {
        SqlCipherNative.load()
        database = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .openHelperFactory(SupportOpenHelperFactory("test-passphrase".toByteArray()))
            .allowMainThreadQueries()
            .build()
        testStartMillis = System.currentTimeMillis()
        preExistingBackupBytes = readCurrentBackupBytes()
        // Wipe stale cdcvoucher* artifacts from EARLIER runs: MediaStore keeps
        // the original DATE_ADDED when a file is re-created, so the
        // this-run-only cleanup can miss leftovers (e.g. "cdcvoucher.backup
        // (6)") that break the exactly-one assertions. tearDown restores the
        // pre-existing snapshot byte-for-byte.
        deleteAllBackupArtifacts()
    }

    /** Deletes EVERY `cdcvoucher*` entry in Downloads, stale or fresh. Both the
     *  MediaStore row AND its physical file - this emulator's MediaStore can
     *  leave the file behind when only the row is deleted, and a leftover
     *  physical file makes the next insert collide on `files._data`. The
     *  physical path is derived from the display name (the DATA column is
     *  rejected by this MediaStore). */
    private fun deleteAllBackupArtifacts() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = appContext.contentResolver
                val rows = mutableListOf<Pair<Long, String>>()
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                    arrayOf("cdcvoucher%"),
                    null,
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        rows.add(cursor.getLong(idCol) to cursor.getString(nameCol))
                    }
                }
                val downloadsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                rows.forEach { (id, name) ->
                    resolver.delete(
                        ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                        null,
                        null,
                    )
                    runCatching { File(downloadsDir, name).delete() }
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.listFiles { it.name.startsWith("cdcvoucher") }?.forEach { it.delete() }
            }
        }
    }

    @After
    fun tearDown() {
        database.close()
        deleteBackupsCreatedThisRun()
        restorePreExistingBackup()
    }

    /** The export tests write real files to Downloads. Delete exactly the files
     *  THIS run created (MediaStore DATE_ADDED / file mtime >= test start) -
     *  never anything that was already there. */
    private fun deleteBackupsCreatedThisRun() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = appContext.contentResolver
                // Collect IDs first, then delete: deleting rows while the
                // cursor iterates makes moveToNext skip entries.
                val ids = mutableListOf<Long>()
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND " +
                        "${MediaStore.MediaColumns.DATE_ADDED} >= ?",
                    arrayOf("cdcvoucher%", (testStartMillis / 1000).toString()),
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) ids.add(cursor.getLong(0))
                }
                ids.forEach { id ->
                    resolver.delete(
                        ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            id,
                        ),
                        null,
                        null,
                    )
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS,
                )
                dir.listFiles {
                    it.name.startsWith("cdcvoucher") && it.lastModified() >= testStartMillis
                }?.forEach { it.delete() }
            }
        }
    }

    /** Bytes of the current `cdcvoucher.backup`, or null when absent. */
    private fun readCurrentBackupBytes(): ByteArray? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            var result: ByteArray? = null
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(BackupFileStore.FILE_NAME),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    resolver.openInputStream(
                        ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            cursor.getLong(0),
                        ),
                    )?.use { result = it.readBytes() }
                }
            }
            result
        } else {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                BackupFileStore.FILE_NAME,
            )
            if (file.exists()) file.readBytes() else null
        }
    }.getOrNull()

    /** Put the pre-existing backup back byte-for-byte (the app's export
     *  deleted it before writing its own). */
    private fun restorePreExistingBackup() {
        val bytes = preExistingBackupBytes ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = appContext.contentResolver
                resolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf(BackupFileStore.FILE_NAME),
                )
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, BackupFileStore.FILE_NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
            } else {
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    BackupFileStore.FILE_NAME,
                ).writeBytes(bytes)
            }
        }
    }

    /** Same API/permission gate as the export tests: storage GID is
     *  unavailable to the test process on API 24-25 (see
     *  [exportWritesGenuinelyEncryptedFileToDownloads]). */
    private fun grantStorageAccess() {
        assumeTrue("storage GID unavailable to the test process on API 24-25", Build.VERSION.SDK_INT >= 26)
        if (Build.VERSION.SDK_INT in 24..27) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "pm grant ${appContext.packageName} android.permission.WRITE_EXTERNAL_STORAGE",
            )
        }
    }

    /** Display names of every `cdcvoucher*` entry currently in Downloads
     *  (MediaStore on API 29+, raw file listing before), sorted for stable
     *  assertions. */
    private fun downloadEntries(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val names = mutableListOf<String>()
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("cdcvoucher%"),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) names.add(cursor.getString(0))
            }
            names.sorted()
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.listFiles { it.name.startsWith("cdcvoucher") }?.map { it.name }?.sorted().orEmpty()
        }

    /** Creates a user-owned Downloads file that shares the app's backup prefix
     *  but is NOT part of the exact export family ("cdcvoucher.backup.notes"
     *  is the file the old prefix-match cleanup would wrongly have caught).
     *  Deletes any leftover physical file first - MediaStore can orphan the
     *  file when its row is removed, and a stale physical file makes the
     *  insert collide on `files._data`. */
    private fun writeUnrelatedDownloadsFile(bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloadsDir, "cdcvoucher.backup.notes").delete()
            File(downloadsDir, "cdcvoucher.backup.notes.txt").delete()
            val resolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "cdcvoucher.backup.notes")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore insert failed for unrelated file")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "cdcvoucher.backup.notes",
            ).writeBytes(bytes)
        }
    }

    /** Bytes of the unrelated `cdcvoucher.backup.notes*` file, or null.
     *  MediaStore appends the extension from the MIME type when the inserted
     *  name has none ("cdcvoucher.backup.notes" -> "...notes.txt"), so match
     *  by prefix. */
    private fun readUnrelatedDownloadsFile(): ByteArray? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            var result: ByteArray? = null
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("cdcvoucher.backup.notes%"),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    resolver.openInputStream(
                        ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            cursor.getLong(0),
                        ),
                    )?.use { result = it.readBytes() }
                }
            }
            result
        } else {
            val f = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "cdcvoucher.backup.notes",
            )
            if (f.exists()) f.readBytes() else null
        }

    private fun voucher(
        token: String,
        name: String = token,
        archived: Boolean = false,
    ) = VoucherGroup(
        id = "id-$token",
        token = token,
        url = "https://voucher.redeem.gov.sg/$token",
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
        waitUntilPasswordFocused()
        composeRule.onNodeWithTag("backup_password").performTextInput(password)
        composeRule.onNodeWithText("Import").performClick()
    }

    /** The backup password field auto-focuses on dialog mount (retry loop up
     *  to ~2s); the bounded wait gives the M3 dialog time to land it under
     *  emulator load (M20 - the app retries the FocusRequester itself). */
    private fun waitUntilPasswordFocused(timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onNodeWithTag("backup_password")
                .fetchSemanticsNode().config[SemanticsProperties.Focused]
        }
    }

    /** Decrypt runs on Dispatchers.IO (P1 item 3) - wait for the async result
     *  (summary dialog or error snackbar) before asserting on it. */
    private fun waitUntilNodeAppears(text: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The busy dialog must show while a backup operation runs - PBKDF2 gives
     *  a real (sub-second) window to catch it in. */
    private fun waitUntilProgressAppears(timeoutMillis: Long = 5_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag("backup-progress").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilProgressGone(timeoutMillis: Long = 5_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag("backup-progress").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun summaryText(payload: VoucherBackupPayload): String {
        val date = payload.createdAt.atZone(ZoneId.systemDefault())
            .toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
        val archived = payload.vouchers.count { it.isArchived }
        return "Backup from $date · ${payload.vouchers.size} vouchers ($archived archived). Import this backup?"
    }

    @Test
    fun exportWritesGenuinelyEncryptedFileToDownloads() {
        // API 24-28 write to the public Downloads dir, which needs the runtime
        // permission the SettingsScreen normally requests. This test drives
        // BackupFlow directly, so grant it like a user would. On API 24-27 the
        // grant must go through the shell (UiAutomation#grantRuntimePermission
        // is API 28+); on API 28 the permission state must already exist when
        // the instrumentation process starts, otherwise the sdcard_rw GID is
        // never applied to the running process and writes get EACCES - CI
        // grants the permission with `pm grant` before am instrument.
        // On API 24-25 the instrumentation runs under the TEST apk's process,
        // which never receives the sdcard_rw GID no matter how the grant is
        // issued (verified empirically on API 24) - a pre-existing platform
        // limitation of this suite, so the export tests are gated to API 26+.
        assumeTrue("storage GID unavailable to the test process on API 24-25", Build.VERSION.SDK_INT >= 26)
        if (Build.VERSION.SDK_INT in 24..27) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "pm grant ${appContext.packageName} android.permission.WRITE_EXTERNAL_STORAGE",
            )
        }
        val repository = RoomVoucherRepository(database)
        runBlocking {
            repository.insert(voucher("TokenOne", "CDC Vouchers 2026"))
            repository.insert(voucher("TokenTwo", "SG60", archived = true))
        }
        val flow = BackupFlow(repository)

        val uri = runBlocking { flow.export(appContext, "backup-passphrase") }

        // API 29+ returns content://media/external/downloads/...; API 24-28
        // return file:///storage/emulated/0/Download/... - both contain "download".
        assertTrue(uri.toString().contains("download", ignoreCase = true))
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
    fun repeatedExportLeavesExactlyOneBackup() {
        grantStorageAccess()
        val repository = RoomVoucherRepository(database)
        runBlocking { repository.insert(voucher("TokenOne", "First run")) }
        val flow = BackupFlow(repository)
        runBlocking { flow.export(appContext, "backup-passphrase") }

        // H8 (spec 06 §6.6): re-exporting replaces the previous file, leaving
        // exactly one cdcvoucher.backup in Downloads - never a "(1)" sibling.
        runBlocking { repository.insert(voucher("TokenTwo", "Second run", archived = true)) }
        runBlocking { flow.export(appContext, "backup-passphrase") }

        assertEquals(
            "repeated export must leave exactly one cdcvoucher.backup, was: ${downloadEntries()}",
            listOf(BackupFileStore.FILE_NAME),
            downloadEntries(),
        )
        // The surviving file carries the SECOND export's content (latest wins).
        val bytes = readCurrentBackupBytes()!!
        val decrypted = service.decryptPayload(bytes, "backup-passphrase")
        assertEquals(setOf("TokenOne", "TokenTwo"), decrypted.vouchers.map { it.token }.toSet())
    }

    @Test
    fun cleanupNeverTouchesUnrelatedDownloadsFile() {
        grantStorageAccess()
        // A user file sharing the "cdcvoucher" prefix but OUTSIDE the exact
        // export family - the old prefix-match cleanup ("cdcvoucher...%")
        // would have deleted it (refactor H8).
        val unrelatedBytes = "my private notes, not a backup".toByteArray()
        writeUnrelatedDownloadsFile(unrelatedBytes)

        val repository = RoomVoucherRepository(database)
        runBlocking { repository.insert(voucher("TokenOne")) }
        runBlocking { BackupFlow(repository).export(appContext, "backup-passphrase") }

        assertTrue(
            "unrelated Downloads file must survive the export's cleanup " +
                "(read back ${readUnrelatedDownloadsFile()?.size ?: "null"} bytes)",
            readUnrelatedDownloadsFile()?.contentEquals(unrelatedBytes) == true,
        )
        assertEquals(
            "downloads must hold exactly the export and the unrelated file",
            2,
            downloadEntries().size,
        )
        assertTrue(
            "the export's exact file must be present",
            downloadEntries().contains(BackupFileStore.FILE_NAME),
        )
        assertTrue(
            "the unrelated file must still be present (MediaStore may append a " +
                "MIME extension to its name)",
            downloadEntries().any { it.startsWith("cdcvoucher.backup.notes") },
        )
    }

    @Test
    fun failedReExportNeverDestroysPreviousBackup() {
        grantStorageAccess()
        val repository = RoomVoucherRepository(database)
        runBlocking { repository.insert(voucher("TokenOne")) }
        val flow = BackupFlow(repository)
        runBlocking { flow.export(appContext, "backup-passphrase") }
        val before = readCurrentBackupBytes()!!

        // Sabotage the re-export (database closed -> reading rows throws before
        // any file write). H8: the previous valid backup must survive a
        // failed re-export byte-for-byte.
        database.close()
        var threw = false
        try {
            runBlocking { flow.export(appContext, "backup-passphrase") }
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("sabotaged re-export must fail", threw)
        assertArrayEquals(
            "failed re-export must not destroy the previous backup",
            before,
            readCurrentBackupBytes(),
        )
    }

    @Test
    fun wrongPasswordAndCorruptedFileShowIdenticalMessage() {
        val repository = RoomVoucherRepository(database)
        val validBytes = encryptedPayload("backup-passphrase", voucher("TokenOne"))
        var currentBytes: ByteArray = validBytes

        settingsContent(repository, bytesProvider = { currentBytes })

        // Wrong password.
        importWithPassword("wrong-password")
        waitUntilNodeAppears(BackupException.GENERIC_MESSAGE)
        composeRule.onNodeWithText(BackupException.GENERIC_MESSAGE).assertIsDisplayed()
        composeRule.waitUntil(6_000) {
            composeRule.onAllNodesWithText(BackupException.GENERIC_MESSAGE)
                .fetchSemanticsNodes().isEmpty()
        }

        // Corrupted file, correct password - must show the identical message.
        currentBytes = validBytes.copyOf().also { it[it.size - 1] = it[it.size - 1].xor(0x01) }
        importWithPassword("backup-passphrase")
        waitUntilNodeAppears(BackupException.GENERIC_MESSAGE)
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
        waitUntilNodeAppears(summaryText(payload))
        composeRule.onNodeWithText(summaryText(payload)).assertIsDisplayed()

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
        val payload = VoucherBackupPayload(
            createdAt = Instant.now(),
            vouchers = listOf(
                voucher("ABC", "duplicate"),
                voucher("abc", "case-variant"),
            ),
        )
        val bytes = service.encryptPayload(payload, "backup-passphrase")

        settingsContent(repository, bytesProvider = { bytes })
        importWithPassword("backup-passphrase")
        waitUntilNodeAppears(summaryText(payload))
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
        val payload = VoucherBackupPayload(
            createdAt = Instant.now(),
            vouchers = listOf(
                voucher("FromBackup1", "b1"),
                voucher("FromBackup2", "b2", archived = true),
            ),
        )
        val bytes = service.encryptPayload(payload, "backup-passphrase")

        settingsContent(repository, bytesProvider = { bytes })
        importWithPassword("backup-passphrase")
        waitUntilNodeAppears(summaryText(payload))
        composeRule.onNodeWithText("Replace existing data").performClick()
        composeRule.onNodeWithText("Import").performClick()

        // Spec 05 §5.3 (refactor M20, shared focus-retry helper): Cancel is the
        // DEFAULT-FOCUSED button on the replace dialog, built through the same
        // shared helper as the delete dialog (ConfirmReplaceDialog here and
        // DeleteVoucherDialog both use AppDialogSurface + DialogButtonRow with
        // the identical FocusRequester retry block).
        //
        // The focused state itself is NOT observable in this instrumented
        // harness for M3 dialog buttons - verified empirically on API 36: a
        // Focused-semantics poll started at dialog mount (inside the helper's
        // 500ms retry window) and one after explicit TAB key injection both
        // report false, while the same mechanism observes the password FIELD's
        // focus (waitUntilPasswordFocused). This matches the documented
        // limitation of the delete-dialog test
        // (deleteShowsExactDialogAndCancelKeepsRow: "the FocusRequester-based
        // default focus on Cancel is not observable in this headless test
        // environment, which never grants window focus"). The executable
        // assertion is therefore the same proxy that test uses: Cancel sits
        // leftmost - the default position the keyboard/DPAD lands on.

        // Exact copy, own confirmation, before anything is wiped. The dialog
        // is title + body; the count resolves the singular plural form.
        composeRule.onNodeWithText("Replace all data?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "This will delete your 1 currently saved voucher and replace them " +
                "with this backup. This can't be undone.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()

        // Secondary guard (same proxy as the delete-dialog test): Cancel is
        // leftmost.
        val cancelLeft = composeRule.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot.left
        val replaceLeft = composeRule.onNodeWithText("Replace").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Cancel should be the default (leftmost) button", cancelLeft < replaceLeft)

        // Cancel: nothing wiped.
        composeRule.onNodeWithText("Cancel").performClick()
        runBlocking {
            assertEquals(1, repository.findAll().size)
        }

        // Redo and confirm Replace: data matches the backup exactly.
        composeRule.onNodeWithText("Import backup").performClick()
        waitUntilPasswordFocused()
        composeRule.onNodeWithTag("backup_password").performTextInput("backup-passphrase")
        composeRule.onNodeWithText("Import").performClick()
        waitUntilNodeAppears(summaryText(payload))
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

    @Test
    fun importShowsProgressDialogThenSummary() {
        val repository = RoomVoucherRepository(database)
        val payload = VoucherBackupPayload(
            createdAt = Instant.now(),
            // Two vouchers: the summary plural renders "2 vouchers", which is
            // what summaryText() builds (a single voucher would render the
            // singular form and never match).
            vouchers = listOf(
                voucher("TokenOne", "CDC Vouchers 2026"),
                voucher("TokenTwo", "SG60", archived = true),
            ),
        )
        val bytes = service.encryptPayload(payload, "backup-passphrase")
        settingsContent(repository) { bytes }

        importWithPassword("backup-passphrase")

        // The decrypt window is real (PBKDF2) - the progress dialog must
        // appear with the import message…
        waitUntilProgressAppears()
        composeRule.onNodeWithText("Decrypting & Importing backup").assertIsDisplayed()
        // …then resolves into the summary dialog and the progress is gone.
        waitUntilNodeAppears(summaryText(payload))
        composeRule.onNodeWithText(summaryText(payload)).assertIsDisplayed()
        waitUntilProgressGone()
    }

    @Test
    fun exportShowsProgressDialogThenSnackbar() {
        // Same platform limitation as exportWritesGenuinelyEncryptedFileToDownloads:
        // on API 24-25 the instrumentation process never receives the sdcard_rw
        // GID, so this test is gated to API 26+.
        assumeTrue("storage GID unavailable to the test process on API 24-25", Build.VERSION.SDK_INT >= 26)
        if (Build.VERSION.SDK_INT in 24..27) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "pm grant ${appContext.packageName} android.permission.WRITE_EXTERNAL_STORAGE",
            )
        }
        val repository = RoomVoucherRepository(database)
        runBlocking { repository.insert(voucher("TokenOne", "CDC Vouchers 2026")) }
        settingsContent(repository) { null }

        composeRule.onNodeWithText("Export backup").performClick()
        waitUntilPasswordFocused()
        composeRule.onNodeWithTag("backup_password").performTextInput("backup-passphrase")
        composeRule.onNodeWithTag("backup_confirm_password").performTextInput("backup-passphrase")
        composeRule.onNodeWithText("Export").performClick()

        waitUntilProgressAppears()
        composeRule.onNodeWithText("Encrypting and Exporting backup").assertIsDisplayed()
        waitUntilNodeAppears("Backup saved to Downloads")
        waitUntilProgressGone()
    }
}
