package com.hdbcoders.cdcwallet.accessibility

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hdbcoders.cdcwallet.ui.accessibility.AccessibilityScreen
import com.hdbcoders.cdcwallet.ui.components.AppHeader
import com.hdbcoders.cdcwallet.ui.theme.AppDyslexiaFont
import com.hdbcoders.cdcwallet.ui.theme.AppLanguage
import com.hdbcoders.cdcwallet.ui.theme.AppTheme
import com.hdbcoders.cdcwallet.ui.theme.DyslexiaFontStore
import com.hdbcoders.cdcwallet.ui.theme.FontScaleStore
import com.hdbcoders.cdcwallet.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dyslexia-friendly font option end to end: the toggle is off by
 * default; enabling starts at Atkinson (default choice) and reveals the two
 * font rows; switching fonts persists; disabling hides the rows and reverts
 * the theme; unknown stored values fall back to off/Atkinson; and the
 * hamburger menu exposes the Accessibility entry point.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityScreenInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Fresh prefs per test - a persisted choice from a previous test
        // would leak into the default resolution.
        dyslexiaPrefs().edit().clear().commit()
        fontPrefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        dyslexiaPrefs().edit().clear().commit()
        fontPrefs().edit().clear().commit()
    }

    private fun setScreenContent(store: DyslexiaFontStore = DyslexiaFontStore(appContext)) {
        composeRule.setContent {
            AppTheme(ThemeMode.LIGHT) {
                AccessibilityScreen(
                    dyslexiaFontStore = store,
                    fontScaleStore = FontScaleStore(appContext),
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun toggleIsOffByDefaultAndFontChoicesHidden() {
        setScreenContent()
        composeRule.onNodeWithTag("dyslexia-font-toggle").assertIsOff()
        composeRule.onNodeWithTag("dyslexia-font-atkinson").assertDoesNotExist()
        composeRule.onNodeWithTag("dyslexia-font-opendyslexic").assertDoesNotExist()
    }

    @Test
    fun enablingDefaultsToAtkinsonAndRevealsChoices() {
        val store = DyslexiaFontStore(appContext)
        setScreenContent(store)
        composeRule.onNodeWithTag("dyslexia-font-toggle").performClick()
        composeRule.waitForIdle()
        assertTrue(store.enabled)
        assertEquals(AppDyslexiaFont.ATKINSON, store.font)
        composeRule.onNodeWithTag("dyslexia-font-toggle").assertIsOn()
        composeRule.onNodeWithTag("dyslexia-font-atkinson").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("dyslexia-font-opendyslexic").assertIsDisplayed()
    }

    @Test
    fun switchingToOpenDyslexicPersists() {
        val store = DyslexiaFontStore(appContext)
        setScreenContent(store)
        composeRule.onNodeWithTag("dyslexia-font-toggle").performClick()
        composeRule.onNodeWithTag("dyslexia-font-opendyslexic").performClick()
        composeRule.waitForIdle()
        assertEquals(AppDyslexiaFont.OPEN_DYSLEXIC, store.font)
        // A fresh store reads the same value - the choice was persisted.
        val fresh = DyslexiaFontStore(appContext)
        assertTrue(fresh.enabled)
        assertEquals(AppDyslexiaFont.OPEN_DYSLEXIC, fresh.font)
    }

    @Test
    fun disablingRevertsHidesChoicesAndPersists() {
        val store = DyslexiaFontStore(appContext)
        setScreenContent(store)
        composeRule.onNodeWithTag("dyslexia-font-toggle").performClick()
        composeRule.onNodeWithTag("dyslexia-font-opendyslexic").performClick()
        composeRule.onNodeWithTag("dyslexia-font-toggle").performClick()
        composeRule.waitForIdle()
        assertFalse(store.enabled)
        composeRule.onNodeWithTag("dyslexia-font-toggle").assertIsOff()
        composeRule.onNodeWithTag("dyslexia-font-atkinson").assertDoesNotExist()
        // Disabling persisted: a fresh store is off too.
        assertFalse(DyslexiaFontStore(appContext).enabled)
    }

    @Test
    fun reEnablingResetsTheChoiceToAtkinson() {
        val store = DyslexiaFontStore(appContext)
        store.setDyslexiaFontEnabled(true)
        store.setChosenFont(AppDyslexiaFont.OPEN_DYSLEXIC)
        store.setDyslexiaFontEnabled(false)
        store.setDyslexiaFontEnabled(true)
        assertEquals(AppDyslexiaFont.ATKINSON, store.font)
        assertEquals(AppDyslexiaFont.ATKINSON, DyslexiaFontStore(appContext).font)
    }

    @Test
    fun unknownStoredFontFallsBackToAtkinson() {
        // Hand-edit prefs to a corrupt value - the store must recover.
        dyslexiaPrefs().edit()
            .putBoolean("dyslexia_font_enabled", true)
            .putString("dyslexia_font", "comic_sans")
            .commit()
        val store = DyslexiaFontStore(appContext)
        assertTrue(store.enabled)
        assertEquals(AppDyslexiaFont.ATKINSON, store.font)
    }

    @Test
    fun hamburgerMenuShowsTheAccessibilityEntry() {
        var clicked = false
        composeRule.setContent {
            AppTheme(ThemeMode.LIGHT) {
                MaterialTheme {
                    AppHeader(
                        currentLanguage = AppLanguage.EN,
                        onLanguageSelected = {},
                        archivedCount = 0,
                        onArchivedClick = {},
                        onSettingsClick = {},
                        onAboutClick = {},
                        onAccessibilityClick = { clicked = true },
                        onToggleTheme = {},
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Accessibility").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue("clicking the menu item must invoke the callback", clicked)
    }

    private fun dyslexiaPrefs() =
        appContext.getSharedPreferences("voucher_dyslexia_font_prefs", Context.MODE_PRIVATE)

    private fun fontPrefs() =
        appContext.getSharedPreferences("voucher_font_prefs", Context.MODE_PRIVATE)
}
