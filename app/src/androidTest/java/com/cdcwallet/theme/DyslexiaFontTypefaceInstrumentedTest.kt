package com.cdcwallet.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cdcwallet.ui.theme.AppDyslexiaFont
import com.cdcwallet.ui.theme.AppTheme
import com.cdcwallet.ui.theme.AtkinsonFontFamily
import com.cdcwallet.ui.theme.FrauncesDisplayFontFamily
import com.cdcwallet.ui.theme.LocalAppTypefaces
import com.cdcwallet.ui.theme.OpenDyslexicFontFamily
import com.cdcwallet.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dyslexia-friendly font option: with a non-null [AppDyslexiaFont],
 * AppTheme swaps every typography role AND the display/body/mono roles that
 * components read via [LocalAppTypefaces] to the chosen family; null (option
 * off) keeps the redesign's typefaces.
 */
@RunWith(AndroidJUnit4::class)
class DyslexiaFontTypefaceInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Fresh prefs per test - a persisted choice would leak across tests.
        dyslexiaPrefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        dyslexiaPrefs().edit().clear().commit()
    }

    @Test
    fun defaultModeKeepsTheRedesignTypefaces() {
        var body: FontFamily? = null
        var display: FontFamily? = null
        composeRule.setContent {
            AppTheme(ThemeMode.LIGHT, dyslexiaFont = null) {
                body = MaterialTheme.typography.bodyMedium.fontFamily
                display = LocalAppTypefaces.current.display
            }
        }
        composeRule.waitForIdle()
        assertFalse("body must not be the dyslexia font when off", sameFamily(body, AtkinsonFontFamily))
        assertFalse("body must not be OpenDyslexic when off", sameFamily(body, OpenDyslexicFontFamily))
        assertTrue("display must stay Fraunces when off", sameFamily(display, FrauncesDisplayFontFamily))
    }

    @Test
    fun atkinsonSwapsEveryRoleAndTypeface() {
        var body: FontFamily? = null
        var label: FontFamily? = null
        var displayRole: FontFamily? = null
        var typefaceDisplay: FontFamily? = null
        var typefaceBody: FontFamily? = null
        var typefaceMono: FontFamily? = null
        composeRule.setContent {
            AppTheme(ThemeMode.LIGHT, dyslexiaFont = AppDyslexiaFont.ATKINSON) {
                body = MaterialTheme.typography.bodyMedium.fontFamily
                label = MaterialTheme.typography.labelSmall.fontFamily
                displayRole = MaterialTheme.typography.displayLarge.fontFamily
                val t = LocalAppTypefaces.current
                typefaceDisplay = t.display
                typefaceBody = t.body
                typefaceMono = t.mono
            }
        }
        composeRule.waitForIdle()
        val expected = AtkinsonFontFamily
        assertTrue(sameFamily(body, expected))
        assertTrue(sameFamily(label, expected))
        assertTrue(sameFamily(displayRole, expected))
        assertTrue(sameFamily(typefaceDisplay, expected))
        assertTrue(sameFamily(typefaceBody, expected))
        assertTrue(sameFamily(typefaceMono, expected))
    }

    @Test
    fun openDyslexicSwapsEveryRoleAndTypeface() {
        var body: FontFamily? = null
        var typefaceDisplay: FontFamily? = null
        var typefaceMono: FontFamily? = null
        composeRule.setContent {
            AppTheme(ThemeMode.LIGHT, dyslexiaFont = AppDyslexiaFont.OPEN_DYSLEXIC) {
                body = MaterialTheme.typography.bodyMedium.fontFamily
                typefaceDisplay = LocalAppTypefaces.current.display
                typefaceMono = LocalAppTypefaces.current.mono
            }
        }
        composeRule.waitForIdle()
        assertTrue(sameFamily(body, OpenDyslexicFontFamily))
        assertTrue(sameFamily(typefaceDisplay, OpenDyslexicFontFamily))
        assertTrue(sameFamily(typefaceMono, OpenDyslexicFontFamily))
    }

    /** Structural comparison of the fonts list (FontListFontFamily equals compares fonts). */
    private fun sameFamily(a: FontFamily?, b: FontFamily): Boolean =
        a is FontListFontFamily && b is FontListFontFamily && a.fonts == b.fonts

    private fun dyslexiaPrefs() =
        appContext.getSharedPreferences("voucher_dyslexia_font_prefs", Context.MODE_PRIVATE)
}
