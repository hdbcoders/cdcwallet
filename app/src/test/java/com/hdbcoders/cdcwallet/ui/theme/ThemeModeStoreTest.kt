package com.hdbcoders.cdcwallet.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure decision logic behind the first-launch theme bake (spec change: the
 * app inherits the system dark/light default exactly once, then only manual
 * toggles). The device-side half - reading uiMode out of the Configuration -
 * lives in ThemeModeStore and is covered by the instrumented bake test.
 */
class ThemeModeStoreTest {

    @Test
    fun noStoredChoiceBakesSystemLight() {
        assertEquals(ThemeMode.LIGHT, resolveInitialMode(isSystemDark = false, stored = null))
    }

    @Test
    fun noStoredChoiceBakesSystemDark() {
        assertEquals(ThemeMode.DARK, resolveInitialMode(isSystemDark = true, stored = null))
    }

    @Test
    fun storedLightWinsOverSystemDark() {
        assertEquals(ThemeMode.LIGHT, resolveInitialMode(isSystemDark = true, stored = "light"))
    }

    @Test
    fun storedDarkWinsOverSystemLight() {
        assertEquals(ThemeMode.DARK, resolveInitialMode(isSystemDark = false, stored = "dark"))
    }

    @Test
    fun legacySystemValueResolvesFromDevice() {
        assertEquals(ThemeMode.DARK, resolveInitialMode(isSystemDark = true, stored = "system"))
        assertEquals(ThemeMode.LIGHT, resolveInitialMode(isSystemDark = false, stored = "system"))
    }

    @Test
    fun unknownStoredValueResolvesFromDevice() {
        assertEquals(ThemeMode.LIGHT, resolveInitialMode(isSystemDark = false, stored = "bogus"))
        assertEquals(ThemeMode.DARK, resolveInitialMode(isSystemDark = true, stored = "bogus"))
    }

    @Test
    fun unknownStoredValueIsMarkedForRewrite() {
        // Refactor M22: every non-concrete stored value - first launch, the
        // legacy "system" value, and any unknown/corrupted value including
        // case variants - must be rewritten to the resolved concrete mode, so
        // a later system-theme change can never flip the app's mode.
        assertTrue(storedModeNeedsRewrite(null))
        assertTrue(storedModeNeedsRewrite("system"))
        assertTrue(storedModeNeedsRewrite("bogus"))
        assertTrue(storedModeNeedsRewrite("Light"))
        assertTrue(storedModeNeedsRewrite("DARK"))
    }

    @Test
    fun concreteStoredValuesAreNotRewritten() {
        assertFalse(storedModeNeedsRewrite("light"))
        assertFalse(storedModeNeedsRewrite("dark"))
    }
}
