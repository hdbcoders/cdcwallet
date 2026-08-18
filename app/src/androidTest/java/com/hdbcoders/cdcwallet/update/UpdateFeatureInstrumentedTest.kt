package com.hdbcoders.cdcwallet.update

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasFlags
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hdbcoders.cdcwallet.MainActivity
import com.hdbcoders.cdcwallet.dev.DevActions
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * REQ-13 update feature, driven end-to-end on the REAL debug app through the
 * DevActions simulation broadcasts (no Play track needed):
 *  - badge absent + "Check for update" present in the no-update state
 *  - user-triggered check with an available update pops the closeable dialog
 *  - the menu slot swaps to "Tap to update" once the flag is set
 *  - "Not now" dismisses; the badge stays as long as the flag is set
 *  - "Tap to update" fires the Play Store intent with FLAG_ACTIVITY_NEW_TASK
 *    (regression: without the flag an application-context start is silently
 *    swallowed by the fail-soft catch - the tap appeared to do nothing)
 *
 * Uses testTags / localized-proof selectors where possible (the app may be in
 * any of the four languages when the suite runs).
 */
@RunWith(AndroidJUnit4::class)
class UpdateFeatureInstrumentedTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action))
    }

    @Before
    fun resetState() {
        // Espresso-Intents must be active before the tap-to-update test
        // fires (it intercepts the external Play Store launch).
        Intents.init()
        // Start every test from the clean no-update state.
        broadcast(DevActions.ACTION_CLEAR_UPDATE_SIM)
        rule.waitForIdle()
    }

    @After
    fun cleanup() {
        broadcast(DevActions.ACTION_CLEAR_UPDATE_SIM)
        Intents.release()
    }

    private fun openHamburgerMenu() {
        // The API<31 splash (bootstrap gate + SPLASH_MIN_MS) delays the header
        // on slow devices while the IO bootstrap runs - the compose rule
        // considers the app idle during it, so tests must WAIT for the header
        // instead of racing the splash (flaky 'node not found' on API 24).
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithTag("header-menu").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("header-menu").performClick()
        rule.waitForIdle()
    }

    @Test
    fun noUpdateState_showsCheckForUpdate_andNoBadge() {
        openHamburgerMenu()
        rule.onNodeWithText("Check for update").assertIsDisplayed()
        rule.onNodeWithText("Tap to update").assertDoesNotExist()
        rule.onNodeWithContentDescription("Update available").assertDoesNotExist()
    }

    @Test
    fun simulatedUpdate_userCheck_popsDialog_andDismissesViaNotNow() {
        broadcast(DevActions.ACTION_SIMULATE_UPDATE)

        openHamburgerMenu()
        rule.onNodeWithText("Check for update").performClick()

        // User check runs async (IO) - wait for the dialog.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Open Play Store").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Update available").assertIsDisplayed()

        // Dismiss without tapping the store button.
        rule.onNodeWithText("Not now").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Open Play Store").assertDoesNotExist()

        // The flag is set: the badge appears and the slot swaps.
        rule.onNodeWithContentDescription("Update available").assertIsDisplayed()
        openHamburgerMenu()
        rule.onNodeWithText("Tap to update").assertIsDisplayed()
        rule.onNodeWithText("Check for update").assertDoesNotExist()
        // The menu item carries the "!" dot so the update is visible there too.
        // (Unmerged tree: the DropdownMenuItem merges descendants into one
        // node, hiding the badge's tag; assertExists, not assertIsDisplayed -
        // presence is the regression guard here.)
        rule.onNodeWithTag("menu-update-badge", useUnmergedTree = true).assertExists()
    }

    @Test
    fun simulatedUpdate_tapToUpdate_opensPlayStorePage() {
        // Fail-soft: no real Play Store/browser launch during the test - the
        // intent is intercepted and asserted instead.
        intending(allOf(hasAction(Intent.ACTION_VIEW))).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, null),
        )

        broadcast(DevActions.ACTION_SIMULATE_UPDATE)

        // Trigger a check so the flag is set (badge + slot swap).
        openHamburgerMenu()
        rule.onNodeWithText("Check for update").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Open Play Store").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Not now").performClick()

        // Slot is now "Tap to update" - tapping must fire the Play Store
        // intent with FLAG_ACTIVITY_NEW_TASK (required when starting from the
        // application context; without it the launch is silently swallowed
        // by the fail-soft catch - the no-op-tap regression).
        openHamburgerMenu()
        rule.onNodeWithText("Tap to update").performClick()
        rule.waitForIdle()

        intended(
            allOf(
                hasAction(Intent.ACTION_VIEW),
                hasData("market://details?id=${context.packageName}"),
                hasFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ),
        )
    }
}
