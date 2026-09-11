package com.hdbcoders.cdcwallet.theme

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hdbcoders.cdcwallet.ui.accessibility.AccessibilityScreen
import com.hdbcoders.cdcwallet.ui.theme.AppFontScale
import com.hdbcoders.cdcwallet.ui.theme.AppScaledContent
import com.hdbcoders.cdcwallet.ui.theme.AppTheme
import com.hdbcoders.cdcwallet.ui.theme.DyslexiaFontStore
import com.hdbcoders.cdcwallet.ui.theme.FontScaleStore
import com.hdbcoders.cdcwallet.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App-wide text size: the multiplier applied in AppTheme must render text
 * proportionally larger (measured, not just asserted on state), the
 * Accessibility screen control must write through to the store, and the
 * choice must persist.
 */
@RunWith(AndroidJUnit4::class)
class FontScaleInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Fresh font prefs per test - a persisted choice from a previous test
        // would leak into the default resolution.
        fontPrefs().edit().clear().commit()
        dyslexiaPrefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        fontPrefs().edit().clear().commit()
        dyslexiaPrefs().edit().clear().commit()
    }

    @Test
    fun largerScaleRendersProportionallyLargerText() {
        var level by mutableStateOf(AppFontScale.DEFAULT)
        composeRule.setContent {
            AppTheme(ThemeMode.LIGHT, fontScale = level) {
                Text("Scale probe", fontSize = 10.sp)
            }
        }
        composeRule.waitForIdle()
        fun measuredHeight(): Float {
            val rect = composeRule.onNodeWithText("Scale probe").getUnclippedBoundsInRoot()
            return (rect.bottom - rect.top).value
        }
        val atDefault = measuredHeight()
        level = AppFontScale.HUGE
        composeRule.waitForIdle()
        val atLargest = measuredHeight()
        // 1.75× scale must measurably render taller text than 1×.
        assertTrue(
            "1.75× ($atLargest) should be much taller than 1× ($atDefault)",
            atLargest > atDefault * 1.5f,
        )
    }

    @Test
    fun accessibilitySelectionUpdatesAndPersists() {
        // Both input methods drive the same slider/radio state (former
        // accessibilityTextSizeSelectionUpdatesAndPersists +
        // accessibilityRadioSelectionUpdatesAndPersists): tapping the slider
        // near its right end selects the last of 4 stops = Huge; the radio on
        // the Huge stop selects it directly. Both must persist to a fresh store.
        val store = FontScaleStore(appContext)
        store.setFontScale(AppFontScale.DEFAULT)
        composeRule.setContent {
            MaterialTheme {
                AccessibilityScreen(
                    dyslexiaFontStore = DyslexiaFontStore(appContext),
                    fontScaleStore = store,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("font-size-slider")
            .performScrollTo()
            .performTouchInput { click(Offset(width * 0.95f, centerY)) }
        composeRule.waitForIdle()
        assertEquals(AppFontScale.HUGE, store.scale)
        // A fresh store reads the same value - the slider choice was persisted.
        assertEquals(AppFontScale.HUGE, FontScaleStore(appContext).scale)

        // Reset so the radio click is a real selection, not a no-op.
        store.setFontScale(AppFontScale.DEFAULT)
        composeRule.onNodeWithTag("font-size-radio-huge").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(AppFontScale.HUGE, store.scale)
        // A fresh store reads the same value - the radio choice was persisted.
        assertEquals(AppFontScale.HUGE, FontScaleStore(appContext).scale)
    }

    @Test
    fun sliderCanBeDraggedToAnotherLevel() {
        val store = FontScaleStore(appContext)
        store.setFontScale(AppFontScale.DEFAULT)
        composeRule.setContent {
            MaterialTheme {
                AccessibilityScreen(
                    dyslexiaFontStore = DyslexiaFontStore(appContext),
                    fontScaleStore = store,
                    onBack = {},
                )
            }
        }
        // The radio dots must not block dragging: swipe the thumb from 30%
        // to 70% of the track and the selection must move off Default.
        composeRule.onNodeWithTag("font-size-slider")
            .performScrollTo()
            .performTouchInput {
                swipe(
                    start = center - Offset(width * 0.3f, 0f),
                    end = center + Offset(width * 0.3f, 0f),
                    durationMillis = 500,
                )
            }
        composeRule.waitForIdle()
        assertTrue(
            "drag should move off Default, was ${store.scale}",
            store.scale.ordinal > AppFontScale.DEFAULT.ordinal,
        )
    }

    @Test
    fun popupMenuTextScalesWithFontSize() {
        // Popup windows (DropdownMenu/Dialog) shadow the composition's
        // LocalDensity with the window density - without the AppScaledContent
        // wrapper, menu text would ignore the text-size setting.
        var level by mutableStateOf(AppFontScale.DEFAULT)
        composeRule.setContent {
            AppTheme(ThemeMode.LIGHT, fontScale = level) {
                Box {
                    DropdownMenu(expanded = true, onDismissRequest = {}) {
                        AppScaledContent {
                            Text("Popup item", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        fun itemHeight(): Float {
            val rect = composeRule.onNodeWithText("Popup item").getUnclippedBoundsInRoot()
            return (rect.bottom - rect.top).value
        }
        val atDefault = itemHeight()
        level = AppFontScale.HUGE
        composeRule.waitForIdle()
        val atLargest = itemHeight()
        assertTrue(
            "popup text should scale with the app font size: $atDefault → $atLargest",
            atLargest > atDefault * 1.5f,
        )
    }

    private fun fontPrefs() =
        appContext.getSharedPreferences("voucher_font_prefs", Context.MODE_PRIVATE)

    private fun dyslexiaPrefs() =
        appContext.getSharedPreferences("voucher_dyslexia_font_prefs", Context.MODE_PRIVATE)
}
