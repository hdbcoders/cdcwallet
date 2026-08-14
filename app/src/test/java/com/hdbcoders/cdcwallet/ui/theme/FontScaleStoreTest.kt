package com.hdbcoders.cdcwallet.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure decision logic behind the persisted text-size choice: the five
 * app-level scale levels and their resolution from a stored value. The
 * device-side half (reading SharedPreferences) lives in FontScaleStore.
 */
class FontScaleStoreTest {

    @Test
    fun missingChoiceFallsBackToDefault() {
        assertEquals(AppFontScale.DEFAULT, resolveFontScale(stored = null))
    }

    @Test
    fun eachStoredLevelResolves() {
        assertEquals(AppFontScale.DEFAULT, resolveFontScale("default"))
        assertEquals(AppFontScale.LARGE, resolveFontScale("large"))
        assertEquals(AppFontScale.EXTRA_LARGE, resolveFontScale("extra_large"))
        assertEquals(AppFontScale.HUGE, resolveFontScale("huge"))
    }

    @Test
    fun storedValueIsCaseInsensitive() {
        assertEquals(AppFontScale.LARGE, resolveFontScale("LARGE"))
        assertEquals(AppFontScale.HUGE, resolveFontScale("Huge"))
    }

    @Test
    fun unknownStoredValueFallsBackToDefault() {
        assertEquals(AppFontScale.DEFAULT, resolveFontScale("bogus"))
        assertEquals(AppFontScale.DEFAULT, resolveFontScale("2x"))
    }

    @Test
    fun multipliersMatchTheFourSpecifiedLevels() {
        assertEquals(1f, AppFontScale.DEFAULT.multiplier, 0f)
        assertEquals(1.25f, AppFontScale.LARGE.multiplier, 0f)
        assertEquals(1.5f, AppFontScale.EXTRA_LARGE.multiplier, 0f)
        assertEquals(1.75f, AppFontScale.HUGE.multiplier, 0f)
    }

    @Test
    fun effectiveScaleIsSystemTimesAppBelowTheCap() {
        // System 1.0 × app levels: all four levels stay distinct.
        assertEquals(1f, effectiveFontScale(1f, 1f), 0f)
        assertEquals(1.25f, effectiveFontScale(1f, 1.25f), 0f)
        assertEquals(1.5f, effectiveFontScale(1f, 1.5f), 0f)
        assertEquals(1.75f, effectiveFontScale(1f, 1.75f), 0f)
        // A large system font stacks normally below the cap.
        assertEquals(1.625f, effectiveFontScale(1.3f, 1.25f), 0f)
    }

    @Test
    fun effectiveScaleIsCappedAtMaxTotal() {
        assertEquals(MAX_TOTAL_FONT_SCALE, effectiveFontScale(2f, 2f), 0f)
        assertEquals(MAX_TOTAL_FONT_SCALE, effectiveFontScale(1f, 2f), 0f)
        assertEquals(MAX_TOTAL_FONT_SCALE, effectiveFontScale(2f, 1f), 0f)
        assertEquals(MAX_TOTAL_FONT_SCALE, effectiveFontScale(1.25f, 1.75f), 0f)
        // Below the cap, the product passes through untouched.
        assertEquals(1.6f, effectiveFontScale(1.6f, 1f), 0f)
    }
}
