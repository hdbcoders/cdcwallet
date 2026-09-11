package com.hdbcoders.cdcwallet

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
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
 * The failure is injected through the debug-only bootstrap seam
 * ([DebugVoucherApp.setBootstrapFailureSimulated]) - the same "the debug
 * override stands in for the real trigger" pattern the REQ-13 update
 * simulation uses (see also SilentUpdateLaunchInstrumentedTest): while the
 * flag is set, the container exposes a bootstrap whose initializer throws,
 * so [MainActivity] observes `DatabaseBootstrapState.Failed` and renders the
 * fatal screen through the exact production branch (no synthetic UI, no
 * special-cased layout). The original CorruptDatabaseOnceTest design
 * (planting a zeroed `voucher.db` from the harness) cannot work inside the
 * live suite: the bootstrap is once-per-process and the suite's own
 * auto-seed keeps a live SQLCipher handle that rewrites the file back to
 * healthy, and the old `@After` deleting the app's real DB poisoned every
 * later test in the process ("no such table" cascade). The seam is
 * deterministic, order-independent, and self-cleaning: clearing the flag
 * falls back to the once-per-process real bootstrap, leaving the app
 * healthy for every subsequent test.
 */
@RunWith(AndroidJUnit4::class)
class FatalBootstrapScreenInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val debugApp: DebugVoucherApp
        get() = context.applicationContext as DebugVoucherApp

    @Before
    fun flagSimulatedBootstrapFailure() {
        debugApp.setBootstrapFailureSimulated(true)
    }

    @After
    fun clearSimulatedBootstrapFailure() {
        debugApp.setBootstrapFailureSimulated(false)
    }

    @Test
    fun bootstrapFailureShowsFatalErrorScreenInsteadOfHanging() {
        // The splash must resolve into the fatal error state - bounded wait,
        // never hang, never a silently-unencrypted list.
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        ).use {
            composeRule.waitUntil(timeoutMillis = 20_000) {
                composeRule.onAllNodesWithText(FATAL_TEXT).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(FATAL_TEXT).assertIsDisplayed()
        }
    }

    private companion object {
        const val FATAL_TEXT =
            "The app could not start. Please restart the app to try again."
    }
}