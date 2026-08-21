package com.hdbcoders.cdcwallet.addflow

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.hdbcoders.cdcwallet.MainActivity
import com.hdbcoders.cdcwallet.VoucherApp
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audit C4 (spec 03 §3.4, refactor M7): a share that arrives while the Add
 * destination is already live must REUSE it (launchSingleTop) instead of
 * stacking a second Add screen, and its arrival cancels the previous in-flight
 * fetch job so exactly one add session ever persists a row. This is the
 * abandoned-session seam M7 exists for.
 *
 * Driven through the REAL app with the debug fixture seam (EXTRA_DEV_FIXTURE_ADD):
 * share #1 is a slow fixture page (extraction in flight), share #2 a fast one.
 * The queued-before-navigation half of M7 (pendingShareUrl) is a startup race
 * with no deterministic hook; its consumer reuses the exact same
 * navigate+launchSingleTop call this test exercises on the warm path.
 */
@RunWith(AndroidJUnit4::class)
class ShareIntentReuseInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val slowUrl = "https://appassets.androidplatform.net/SlowPage"
    private val fastUrl = "https://appassets.androidplatform.net/TestToken1"

    private fun shareIntent(url: String) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
        putExtra(MainActivity.EXTRA_DEV_FIXTURE_ADD, true)
        setClassName(context.packageName, "com.hdbcoders.cdcwallet.MainActivity")
    }

    @Before
    fun clearFixtureRows() {
        // The debug app auto-seeds dev rows at process start; this test owns
        // only the fixture-host rows. Delete any from a previous run so the
        // "exactly one verified add" assertion is deterministic. Waits for the
        // bootstrap (it can still be Initializing on slow devices/API 24).
        val app = context.applicationContext as VoucherApp
        runBlocking {
            app.container.databaseBootstrap.awaitReady()
            app.container.repository.findAll()
                .filter { it.url.startsWith("https://appassets.androidplatform.net/") }
                .forEach { app.container.repository.delete(it.id) }
        }
    }

    @Test
    fun secondShareReusesLiveAddScreenAndCancelsPriorInFlightJob() {
        // Cold start with share #1: the Add screen opens and submits the slow
        // link - the extraction stays in flight (no API asset for SlowPage).
        ActivityScenario.launch<MainActivity>(shareIntent(slowUrl)).use { scenario ->
            composeRule.waitUntil(timeoutMillis = 20_000) {
                composeRule.onAllNodesWithText(slowUrl, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            // Warm share #2 while the Add screen is live (M7): the activity is
            // singleTask, so this startActivity is delivered to the existing
            // instance's onNewIntent - the exact production warm-share path -
            // which routes it back to the live Add destination via
            // launchSingleTop. Whether the first job is still IN FLIGHT at
            // this moment depends on the platform: the slow fixture hangs on
            // API 26+ but fails fast on API 24-25, so capture it first.
            val slowJobStillInFlight = composeRule.onAllNodesWithText(
                "Added, but couldn't verify",
                substring = true,
            ).fetchSemanticsNodes().isEmpty()
            context.startActivity(shareIntent(fastUrl).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            // The second URL wins: the fast fixture extraction completes
            // (added message).
            composeRule.waitUntil(timeoutMillis = 20_000) {
                composeRule.onAllNodesWithText("Added:", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            // The reuse must never spin a SECOND verified voucher out of the
            // first session: an in-flight job is cancelled (no row), and a
            // job that already finished before the reuse can only have left an
            // UNVERIFIED placeholder - never an ACTIVE row. (The debug app
            // auto-seeds dev rows at process start, so count only the
            // fixture-host rows this test drives.)
            val app = context.applicationContext as VoucherApp
            val fixtureRows = runBlocking { app.container.repository.findAll() }
                .filter { it.url.startsWith("https://appassets.androidplatform.net/") }
            val activeRows = fixtureRows.filter { it.validityStatus == ValidityStatus.ACTIVE }
            assertEquals(
                "exactly one verified add may be persisted - the second share's",
                1,
                activeRows.size,
            )
            assertEquals("second share wins", "TestToken1", activeRows.single().token)
            if (slowJobStillInFlight) {
                // The prior in-flight job was cancelled: no row at all from it.
                assertEquals(
                    "a cancelled in-flight session must not persist any row",
                    1,
                    fixtureRows.size,
                )
                assertEquals("TestToken1", fixtureRows.single().token)
            } else {
                fixtureRows.filter { it.token == "SlowPage" }.forEach { row ->
                    assertEquals(
                        "a pre-finished first session may only be an UNVERIFIED placeholder",
                        ValidityStatus.UNVERIFIED,
                        row.validityStatus,
                    )
                }
            }

            // Single destination: ONE back press lands on the list (a stacked
            // second Add screen would require a second press).
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithContentDescription("Menu")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
        }
    }
}