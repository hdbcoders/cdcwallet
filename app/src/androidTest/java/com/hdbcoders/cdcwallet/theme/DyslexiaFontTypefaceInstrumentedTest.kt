package com.hdbcoders.cdcwallet.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hdbcoders.cdcwallet.ui.theme.AppDyslexiaFont
import com.hdbcoders.cdcwallet.ui.theme.AppTheme
import com.hdbcoders.cdcwallet.ui.theme.AtkinsonFontFamily
import com.hdbcoders.cdcwallet.ui.theme.FrauncesDisplayFontFamily
import com.hdbcoders.cdcwallet.ui.theme.LocalAppTypefaces
import com.hdbcoders.cdcwallet.ui.theme.OpenDyslexicFontFamily
import com.hdbcoders.cdcwallet.ui.theme.ThemeMode
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
    fun dyslexiaFontsSwapEveryRoleAndTypeface() {
        // One composition per font, re-read after the state change recomposes:
        // the font choice is Compose state (no second setContent). Every
        // typography role AND the LocalAppTypefaces roles must swap to the
        // chosen family (former atkinsonSwaps… / openDyslexicSwaps… merged).
        var font by mutableStateOf(AppDyslexiaFont.ATKINSON)
        var body: FontFamily? = null
        var label: FontFamily? = null
        var displayRole: FontFamily? = null
        var typefaceDisplay: FontFamily? = null
        var typefaceBody: FontFamily? = null
        var typefaceMono: FontFamily? = null
        composeRule.setContent {
            AppTheme(ThemeMode.LIGHT, dyslexiaFont = font) {
                body = MaterialTheme.typography.bodyMedium.fontFamily
                label = MaterialTheme.typography.labelSmall.fontFamily
                displayRole = MaterialTheme.typography.displayLarge.fontFamily
                val t = LocalAppTypefaces.current
                typefaceDisplay = t.display
                typefaceBody = t.body
                typefaceMono = t.mono
            }
        }
        AppDyslexiaFont.entries.forEach { chosen ->
            font = chosen
            composeRule.waitForIdle()
            val expected =
                if (chosen == AppDyslexiaFont.ATKINSON) AtkinsonFontFamily else OpenDyslexicFontFamily
            assertTrue("body must be $chosen", sameFamily(body, expected))
            assertTrue("label must be $chosen", sameFamily(label, expected))
            assertTrue("display role must be $chosen", sameFamily(displayRole, expected))
            assertTrue("typeface display must be $chosen", sameFamily(typefaceDisplay, expected))
            assertTrue("typeface body must be $chosen", sameFamily(typefaceBody, expected))
            assertTrue("typeface mono must be $chosen", sameFamily(typefaceMono, expected))
        }
    }

    /** Structural comparison of the fonts list (FontListFontFamily equals compares fonts). */
    private fun sameFamily(a: FontFamily?, b: FontFamily): Boolean =
        a is FontListFontFamily && b is FontListFontFamily && a.fonts == b.fonts

    private fun dyslexiaPrefs() =
        appContext.getSharedPreferences("voucher_dyslexia_font_prefs", Context.MODE_PRIVATE)
}
