package com.cdcwallet.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure decision logic behind the persisted dyslexia-font choice: the two
 * bundled fonts and their resolution from a stored value. The device-side
 * half (SharedPreferences: default-off, reset-to-Atkinson on enable,
 * persistence) lives in DyslexiaFontStore and its instrumented tests.
 */
class DyslexiaFontStoreTest {

    @Test
    fun missingChoiceFallsBackToAtkinson() {
        assertEquals(AppDyslexiaFont.ATKINSON, resolveDyslexiaFont(stored = null))
    }

    @Test
    fun eachStoredFontResolves() {
        assertEquals(AppDyslexiaFont.ATKINSON, resolveDyslexiaFont("atkinson"))
        assertEquals(AppDyslexiaFont.OPEN_DYSLEXIC, resolveDyslexiaFont("open_dyslexic"))
    }

    @Test
    fun storedValueIsCaseInsensitive() {
        assertEquals(AppDyslexiaFont.ATKINSON, resolveDyslexiaFont("ATKINSON"))
        assertEquals(AppDyslexiaFont.OPEN_DYSLEXIC, resolveDyslexiaFont("Open_Dyslexic"))
    }

    @Test
    fun unknownStoredValueFallsBackToAtkinson() {
        assertEquals(AppDyslexiaFont.ATKINSON, resolveDyslexiaFont("bogus"))
        assertEquals(AppDyslexiaFont.ATKINSON, resolveDyslexiaFont("comic_sans"))
    }
}
