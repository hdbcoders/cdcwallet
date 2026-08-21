package com.hdbcoders.cdcwallet.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hdbcoders.cdcwallet.data.RoomVoucherRepository
import com.hdbcoders.cdcwallet.data.db.AppDatabase
import com.hdbcoders.cdcwallet.data.db.SqlCipherNative
import com.hdbcoders.cdcwallet.extraction.ExtractionCoordinator
import com.hdbcoders.cdcwallet.extraction.ExtractionEngine
import com.hdbcoders.cdcwallet.ui.list.VoucherListScreen
import com.hdbcoders.cdcwallet.ui.theme.AppTheme
import com.hdbcoders.cdcwallet.ui.theme.LanguageStore
import com.hdbcoders.cdcwallet.ui.theme.ThemeMode
import com.hdbcoders.cdcwallet.ui.theme.ThemeModeStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Theme rework (spec change): the app inherits the system dark/light default
 * exactly once at first launch (ThemeModeStore bake), and the switch moved
 * out of Settings into the hamburger menu as a Dark Mode / Light Mode toggle.
 */
@RunWith(AndroidJUnit4::class)
class ThemeToggleInstrumentedTest {

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
        // Fresh theme prefs per test: a persisted choice from a previous test
        // would leak state into the first-launch bake.
        themePrefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        database.close()
        themePrefs().edit().clear().commit()
    }

    @Test
    fun firstLaunchBakesSystemDefaultAndPersistsIt() {
        val store = ThemeModeStore(appContext)
        val systemDark = (appContext.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        assertEquals(if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT, store.mode)
        // A second store reads the same value - the bake was persisted, so
        // the app stops following the system after first launch.
        assertEquals(store.mode, ThemeModeStore(appContext).mode)
    }

    @Test
    fun unknownStoredThemeValuesAreRewrittenToConcreteModeAtInit() {
        // Refactor M22 (spec 07 §7.2): a stored value that is not a concrete
        // mode - missing (first launch), the legacy "system", or any
        // unknown/corrupted value including case variants - must be REWRITTEN
        // to the resolved concrete mode at store init, so the app never
        // re-follows the system theme afterwards. ThemeModeStoreTest pins the
        // pure predicate; this pins the PERSISTENCE half.
        val systemDark = (appContext.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val baked = if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT
        val rawSeeds = listOf(null, "system", "bogus", "Light", "DARK ")

        rawSeeds.forEach { seed ->
            themePrefs().edit().clear().commit()
            seed?.let { themePrefs().edit().putString("theme_mode", it).commit() }

            val store = ThemeModeStore(appContext)
            // The store resolves the concrete mode...
            assertEquals("seed '$seed' must resolve to the baked mode", baked, store.mode)
            // ...and PERSISTS it at init - a fresh store sees the concrete
            // value, not the corrupt seed (rewrite happened once).
            assertEquals(
                "seed '$seed' must be rewritten on disk",
                baked.name.lowercase(),
                themePrefs().getString("theme_mode", null),
            )
            assertEquals(store.mode, ThemeModeStore(appContext).mode)
        }
    }

    @Test
    fun hamburgerToggleSwitchesLightToDarkAndLabelFollows() {
        val store = ThemeModeStore(appContext)
        store.setThemeMode(ThemeMode.LIGHT)
        val repository = RoomVoucherRepository(database)
        composeRule.setContent {
            AppTheme(store.mode) {
                VoucherListScreen(
                    repository = repository,
                    extractionCoordinator = ExtractionCoordinator(repository, ExtractionEngine()),
                    onAddClick = {},
                    onOpenVoucher = {},
                    onArchivedClick = {},
                    onSettingsClick = {},
                    onAboutClick = {},
                    languageStore = LanguageStore(appContext),
                    onLanguageSelected = {},
                    onToggleTheme = { store.toggle() },
                )
            }
        }
        // Light mode → the hamburger offers "Dark Mode".
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Dark Mode").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(ThemeMode.DARK, store.mode)
        // Reopen: now dark, so the menu offers "Light Mode".
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Light Mode").assertIsDisplayed()
        // The choice persists.
        assertEquals(ThemeMode.DARK, ThemeModeStore(appContext).mode)
    }

    private fun themePrefs() =
        appContext.getSharedPreferences("voucher_theme_prefs", Context.MODE_PRIVATE)
}
