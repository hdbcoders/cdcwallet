package com.hdbcoders.cdcwallet

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audit C2, assertion half (spec 04 §4.8, refactor M2/M14): when the
 * database bootstrap FAILS, the UI must show the honest fatal-error state -
 * the splash must never hang and the app must never sail on un-encrypted.
 * `DatabaseBootstrapTest` (JVM) proves the state holder; this proves the
 * activity consumes it.
 *
 * Prerequisite (harness, MUST run in the process BEFORE this class):
 * ```
 * adb shell am force-stop com.hdbcoders.cdcwallet
 * adb shell run-as com.hdbcoders.cdcwallet sh -c \
 *   'head -c 4096 /dev/zero > databases/voucher.db'
 * ```
 * Overwriting the SQLCipher file while NO app process is alive is the only
 * deterministic corruption: the debug app auto-starts the bootstrap at
 * process launch (auto-seed), and a live SQLCipher handle rewrites the file
 * back to a healthy database when its process dies - so in-process corruption
 * never sticks. This class then launches MainActivity in a FRESH process
 * whose bootstrap fails on the garbage file. [After] deletes the corrupt file
 * so later fresh processes start clean.
 */
@RunWith(AndroidJUnit4::class)
class FatalBootstrapScreenInstrumentedTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun ensureCorruptDatabaseIsPlanted() {
        val dbFile = context.getDatabasePath(DatabaseBootstrap.DB_NAME)
        check(dbFile.exists() && dbFile.length() > 0) {
            "run CorruptDatabaseOnceTest in a previous fresh process first"
        }
    }

    @After
    fun deleteCorruptDatabase() {
        // The corrupt file is gone; the next fresh process creates a new,
        // healthy database (the passphrase store is Keystore-backed and
        // unaffected by the file deletion).
        context.getDatabasePath(DatabaseBootstrap.DB_NAME).delete()
    }

    @Test
    fun bootstrapFailureShowsFatalErrorScreenInsteadOfHanging() {
        // The splash must resolve into the fatal error state - bounded wait,
        // never hang, never a silently-unencrypted list.
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText(FATAL_TEXT).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(FATAL_TEXT).assertIsDisplayed()
    }

    private companion object {
        const val FATAL_TEXT =
            "The app could not start. Please restart the app to try again."
    }
}