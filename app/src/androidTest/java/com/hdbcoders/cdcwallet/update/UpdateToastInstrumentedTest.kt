package com.hdbcoders.cdcwallet.update

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.hdbcoders.cdcwallet.MainActivity
import com.hdbcoders.cdcwallet.dev.DevActions
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * REQ-13 user-check feedback smoke test, driven with UIAutomator SELECTORS
 * only (per AGENTS.md - no raw coordinate taps).
 *
 * Verifies the "Check for update" tap fires and the check completes without
 * crashing. The toast itself is NOT assertable via UiAutomation: toasts live
 * in their own transient window, outside the active-window accessibility
 * hierarchy UiDevice queries (verified on API 24 + 36). Toast firing is
 * instead evidenced by WindowManager/NotificationService logs
 * ("Surface(name=Toast)" / "Toast already killed") and by the on-screen
 * toast on the emulator. The Available-result popup path IS assertable and
 * is covered by UpdateFeatureInstrumentedTest.
 */
@RunWith(AndroidJUnit4::class)
class UpdateToastInstrumentedTest {

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun launchApp() {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )
        // Wait out the splash (bootstrap gate) - the header appears after it.
        assertNotNull("hamburger menu did not appear", device.wait(Until.findObject(By.desc("Menu")), 20_000))
        // Fresh state: no simulated update, no stale flag.
        context.sendBroadcast(Intent(DevActions.ACTION_CLEAR_UPDATE_SIM))
        device.waitForIdle()
    }

    @Test
    fun noUpdateUserCheck_firesAndKeepsAppAlive() {
        val menu = device.wait(Until.findObject(By.desc("Menu")), 5_000)
        assertNotNull(menu)
        menu.click()

        val checkItem = device.wait(Until.findObject(By.text("Check for update")), 5_000)
        assertNotNull("'Check for update' menu item not found", checkItem)
        checkItem.click()

        // The click must close the menu - that proves onCheckForUpdate fired
        // (outside-dismiss also closes it, but the app stays alive either way
        // and the toast evidence comes from the window manager logs).
        val menuClosed = device.wait(Until.gone(By.text("Check for update")), 5_000)
        assertTrue("menu did not close after tapping 'Check for update'", menuClosed)

        // The check runs async; give it time to complete (Play binder call)
        // and confirm the app is still responsive with no crash.
        device.waitForIdle()
        assertNotNull("app lost the header after the check", device.wait(Until.findObject(By.desc("Menu")), 10_000))
    }
}
