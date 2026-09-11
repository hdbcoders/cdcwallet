package com.hdbcoders.cdcwallet.update

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hdbcoders.cdcwallet.MainActivity
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.dev.DevActions
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * REQ-13 launch-level integration (spec 07 §7.4, audit C7): the seam the unit
 * tests cover in halves — UpdateCheckerTest proves the 24h throttle, and
 * UpdateFeatureInstrumentedTest drives the badge/popup via debug broadcasts —
 * is the app-open wiring: `MainActivity.onCreate` calls
 * `silentCheckIfDue()`, and the resulting flag must reach the header with
 * ZERO user interaction (a silent check never shows UI, never needs a tap).
 *
 * Determinism: the SIMULATE override is set BEFORE the cold start so the
 * launch-time silent check consumes it (a debug override bypasses the 24h
 * throttle - see UpdateChecker.silentCheckIfDue). The real Play query cannot
 * run under instrumentation; the override stands in for the flag-set.
 */
@RunWith(AndroidJUnit4::class)
class SilentUpdateLaunchInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action))
    }

    @After
    fun cleanup() {
        broadcast(DevActions.ACTION_CLEAR_UPDATE_SIM)
    }

    @Test
    fun launchTimeSilentCheckReachesHeaderWithoutAnyMenuInteraction() {
        // The silent check must find the override waiting at app open.
        broadcast(DevActions.ACTION_CLEAR_UPDATE_SIM)
        broadcast(DevActions.ACTION_SIMULATE_UPDATE)

        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        ).use {
            // Splash released (bootstrap Ready -> the header is up) and the
            // launch-time silent check flipped the flag: the badge appears
            // with ZERO menu interaction.
            composeRule.waitUntil(timeoutMillis = 20_000) {
                composeRule.onAllNodesWithContentDescription("Update available")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("Update available").assertIsDisplayed()

            // The menu slot swapped to "Tap to update" too - read without any
            // user-triggered check having run.
            composeRule.onNodeWithTag("header-menu").performClick()
            composeRule.onNodeWithText("Tap to update").assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.check_for_update)).assertDoesNotExist()
        }
    }
}