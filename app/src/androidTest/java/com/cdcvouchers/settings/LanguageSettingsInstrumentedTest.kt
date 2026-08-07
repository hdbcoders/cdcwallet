package com.cdcvouchers.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cdcvouchers.data.RoomVoucherRepository
import com.cdcvouchers.data.backup.BackupFlow
import com.cdcvouchers.data.db.AppDatabase
import com.cdcvouchers.data.db.SqlCipherNative
import com.cdcvouchers.ui.settings.SettingsScreen
import com.cdcvouchers.ui.theme.AppLanguage
import com.cdcvouchers.ui.theme.LanguageStore
import com.cdcvouchers.ui.theme.ThemeModeStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * In-app language (i18n): the LanguageStore default/persistence contract and
 * the Settings screen's Language section wiring — selecting a language
 * persists the choice and highlights the row. The activity-recreation side
 * (attachBaseContext) is covered by manual QA.
 */
@RunWith(AndroidJUnit4::class)
class LanguageSettingsInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private lateinit var languageStore: LanguageStore

    @Before
    fun setUp() {
        SqlCipherNative.load()
        database = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .openHelperFactory(SupportOpenHelperFactory("test-passphrase".toByteArray()))
            .allowMainThreadQueries()
            .build()
        languageStore = LanguageStore(appContext)
    }

    @After
    fun tearDown() {
        database.close()
        // Never leak a non-default language into other tests in this process.
        languageStore.setAppLanguage(AppLanguage.SYSTEM)
    }

    @Test
    fun storeDefaultsToSystem() {
        assertEquals(AppLanguage.SYSTEM, languageStore.language)
    }

    @Test
    fun storePersistsChoice() {
        languageStore.setAppLanguage(AppLanguage.TA)
        assertEquals(AppLanguage.TA, LanguageStore(appContext).language)
    }

    @Test
    fun selectingChinesePersistsAndHighlightsTheRow() {
        val repository = RoomVoucherRepository(database)
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    backupFlow = BackupFlow(repository),
                    repository = repository,
                    themeModeStore = ThemeModeStore(appContext),
                    languageStore = languageStore,
                    onLanguageSelected = { languageStore.setAppLanguage(it) },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("中文").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("中文").performClick()
        assertEquals(AppLanguage.ZH, languageStore.language)
        composeRule.onNodeWithText("中文").assertIsSelected()
    }
}
