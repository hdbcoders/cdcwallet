package com.hdbcoders.cdcwallet.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import com.hdbcoders.cdcwallet.ui.theme.AppLanguage
import com.hdbcoders.cdcwallet.ui.theme.AppTheme
import com.hdbcoders.cdcwallet.ui.theme.LanguageStore
import com.hdbcoders.cdcwallet.ui.theme.ThemeMode
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * In-app language (i18n, spec 07 §7.1, REQ-11): the LanguageStore
 * default/persistence contract and the header language dropdown - the main
 * list's app bar is [hamburger] title [language dropdown] [Archived]; the
 * dropdown lists the four languages with the active one checked, and selecting
 * applies immediately, persists, and dismisses (refactor D15: the earlier
 * standalone Translate button + `AppDialogSurface` picker were replaced by the
 * dropdown). The activity-recreation side (attachBaseContext) is covered by
 * manual QA.
 */
@RunWith(AndroidJUnit4::class)
class LanguageSettingsInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private lateinit var languageStore: LanguageStore
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        SqlCipherNative.load()
        database = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .openHelperFactory(SupportOpenHelperFactory("test-passphrase".toByteArray()))
            .allowMainThreadQueries()
            .build()
        originalLocale = Locale.getDefault()
        // Fresh prefs per test: the store derives its first-launch default
        // from the system locale, so a persisted choice from a previous test
        // would leak state.
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        languageStore = LanguageStore(appContext)
    }

    @After
    fun tearDown() {
        database.close()
        Locale.setDefault(originalLocale)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun storeDefaultsToSystemLanguageWhenSupported() {
        // zh-TW is not one of the app's locales (zh-CN is) - any zh region
        // still maps to Simplified Chinese, the only Chinese variant offered.
        Locale.setDefault(Locale.forLanguageTag("zh-TW"))
        assertEquals(AppLanguage.ZH, LanguageStore(appContext).language)
    }

    @Test
    fun storeDefaultsToEnglishWhenSystemLanguageUnsupported() {
        Locale.setDefault(Locale.FRENCH)
        assertEquals(AppLanguage.EN, LanguageStore(appContext).language)
    }

    @Test
    fun storePersistsChoice() {
        languageStore.setAppLanguage(AppLanguage.TA)
        assertEquals(AppLanguage.TA, LanguageStore(appContext).language)
    }

    @Test
    fun headerLanguageDropdown_appliesSelectionAndDismisses() {
        val repository = RoomVoucherRepository(database)
        // Deterministic active language regardless of the emulator's locale.
        languageStore.setAppLanguage(AppLanguage.EN)
        composeRule.setContent {
            AppTheme(mode = ThemeMode.LIGHT) {
                VoucherListScreen(
                    repository = repository,
                    extractionCoordinator = ExtractionCoordinator(repository, ExtractionEngine()),
                    onAddClick = {},
                    onOpenVoucher = {},
                    onArchivedClick = {},
                    onSettingsClick = {}, onAboutClick = {},
                    languageStore = languageStore,
                    onLanguageSelected = { languageStore.setAppLanguage(it) },
                )
            }
        }
        // Spec 07 §7.1 (refactor D15): the header language dropdown lists the
        // four languages with the active one checked; selecting applies
        // immediately and dismisses.
        composeRule.onNodeWithContentDescription("Select language").performClick()
        listOf("English", "中文", "Bahasa Melayu", "தமிழ்").forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
        composeRule.onNodeWithText("English").assertIsSelected()

        composeRule.onNodeWithText("中文").performClick()
        assertEquals(AppLanguage.ZH, languageStore.language)
        // Selecting persists and dismisses the dropdown.
        composeRule.onNodeWithText("中文").assertDoesNotExist()
    }

    private companion object {
        const val PREFS_NAME = "voucher_language_prefs"
    }
}
