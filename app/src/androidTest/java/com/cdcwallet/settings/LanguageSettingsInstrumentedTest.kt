package com.cdcwallet.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cdcwallet.data.RoomVoucherRepository
import com.cdcwallet.data.db.AppDatabase
import com.cdcwallet.data.db.SqlCipherNative
import com.cdcwallet.extraction.ExtractionCoordinator
import com.cdcwallet.extraction.ExtractionEngine
import com.cdcwallet.ui.list.LanguagePickerDialog
import com.cdcwallet.ui.list.VoucherListScreen
import com.cdcwallet.ui.theme.AppLanguage
import com.cdcwallet.ui.theme.LanguageStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * In-app language (i18n, spec 07 §7.5): the LanguageStore default/persistence
 * contract and the standalone picker flow — the Translate button in the main
 * list's app bar opens the picker (outside Settings), selecting a language
 * persists the choice and dismisses. The activity-recreation side
 * (attachBaseContext) is covered by manual QA.
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
        // zh-TW is not one of the app's locales (zh-CN is) — any zh region
        // still maps to Simplified Chinese, the only Chinese variant offered.
        Locale.setDefault(Locale("zh", "TW"))
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
    fun translateButtonOpensPickerAndSelectionPersistsAndDismisses() {
        val repository = RoomVoucherRepository(database)
        composeRule.setContent {
            MaterialTheme {
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
        // The standalone app-bar button (outside Settings) opens the picker.
        composeRule.onNodeWithContentDescription("Select language").performClick()
        composeRule.onNodeWithText("中文").assertIsDisplayed()
        composeRule.onNodeWithText("中文").performClick()
        assertEquals(AppLanguage.ZH, languageStore.language)
        // Selecting persists and dismisses the picker.
        composeRule.onNodeWithText("中文").assertDoesNotExist()
    }

    @Test
    fun pickerChecksTheActiveLanguage() {
        languageStore.setAppLanguage(AppLanguage.MS)
        composeRule.setContent {
            MaterialTheme {
                LanguagePickerDialog(
                    current = languageStore.language,
                    onLanguageSelected = { languageStore.setAppLanguage(it) },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("Bahasa Melayu").assertIsDisplayed().assertIsSelected()
    }

    private companion object {
        const val PREFS_NAME = "voucher_language_prefs"
    }
}
