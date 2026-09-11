package com.hdbcoders.cdcwallet.list

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.hdbcoders.cdcwallet.data.RoomVoucherRepository
import com.hdbcoders.cdcwallet.data.db.AppDatabase
import com.hdbcoders.cdcwallet.data.db.SqlCipherNative
import com.hdbcoders.cdcwallet.data.model.CategoryBalance
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.extraction.ExtractionCoordinator
import com.hdbcoders.cdcwallet.extraction.ExtractionEngine
import com.hdbcoders.cdcwallet.ui.list.VoucherListScreen
import com.hdbcoders.cdcwallet.ui.theme.AppLanguage
import com.hdbcoders.cdcwallet.ui.theme.AppTheme
import com.hdbcoders.cdcwallet.ui.theme.LanguageStore
import com.hdbcoders.cdcwallet.ui.theme.ThemeMode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audit C8 (spec 04 §4.2, refactor M21): every custom/tappable control must
 * carry a minimum 48dp touch target. Two complementary probes:
 *
 *  - visible-size asserts for the explicitly-sized controls (kebab at 48dp,
 *    the hero toggle is far larger);
 *  - BEHAVIORAL edge taps for the header controls (hamburger, language
 *    dropdown trigger, Archived pill): the tap lands just OUTSIDE the
 *    control's visible bounds but INSIDE the 48dp ring that Material3's
 *    minimum interactive size guarantees, and must still fire the control.
 *    Semantics cannot expose the touch region in ui-test 1.11 (the
 *    MinimumTouchTargetSize semantics property was removed), so a hit-test
 *    probe is the executable form of the guarantee.
 */
@RunWith(AndroidJUnit4::class)
class TouchTargetSweepInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase

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

    @Test
    fun everyCustomControlHasAtLeastA48dpTouchTarget() {
        val repository = RoomVoucherRepository(database)
        runBlocking {
            repository.insert(
                VoucherGroup(
                    id = "a1",
                    token = "a1",
                    url = "https://example.com/a1",
                    campaignName = "Link One",
                    validityStatus = ValidityStatus.ACTIVE,
                    expiryDate = LocalDate.now().plusDays(30),
                    categoryBalances = listOf(CategoryBalance("heartland", BigDecimal("50"))),
                    dateAdded = Instant.now(),
                    lastRefreshedAt = null,
                    lastRefreshError = null,
                ),
            )
        }
        var archivedClicked = false
        composeRule.setContent {
            AppTheme(mode = ThemeMode.LIGHT) {
                VoucherListScreen(
                    repository = repository,
                    extractionCoordinator = ExtractionCoordinator(repository, ExtractionEngine()),
                    onAddClick = {},
                    onOpenVoucher = {},
                    onArchivedClick = { archivedClicked = true },
                    onSettingsClick = {}, onAboutClick = {},
                    languageStore = LanguageStore(appContext),
                    onLanguageSelected = {},
                )
            }
        }
        composeRule.waitForIdle()

        // 1) Explicitly-sized controls keep their visible size at 48dp+.
        composeRule.onNodeWithContentDescription("More options for Link One")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        // 2) Header controls: a tap 20dp BELOW the visual center (outside the
        //   ~34dp visible box, inside the 48dp guarantee ring) must still hit.
        //   Hamburger -> the navigation dropdown opens.
        composeRule.onNodeWithContentDescription("Menu").performTouchInput {
            click(center + Offset(0f, with(density) { 20.dp.toPx() }))
        }
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.waitForIdle()

        //   Language dropdown trigger -> the four-language dropdown opens.
        composeRule.onNodeWithContentDescription("Select language").performTouchInput {
            click(center + Offset(0f, with(density) { 20.dp.toPx() }))
        }
        composeRule.onNodeWithText("中文").assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.waitForIdle()

        //   Archived pill -> the callback fires.
        composeRule.onNodeWithTag("header-archived").performTouchInput {
            click(center + Offset(0f, with(density) { 20.dp.toPx() }))
        }
        composeRule.waitForIdle()
        assertTrue("archived pill edge tap must still count as a tap", archivedClicked)
    }
}