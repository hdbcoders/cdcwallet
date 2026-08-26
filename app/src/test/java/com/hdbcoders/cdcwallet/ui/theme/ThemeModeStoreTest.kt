package com.hdbcoders.cdcwallet.ui.theme

import androidx.compose.ui.graphics.Color
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
    fun paletteParsingDefaultsToCreamOnNullOrUnknown() {
        assertEquals(LightPalette.CREAM, parseStoredPalette(null))
        assertEquals(LightPalette.CREAM, parseStoredPalette("garbage"))
        assertEquals(LightPalette.CREAM, parseStoredPalette("PARCHMENT"))
    }

    @Test
    fun paletteParsingAcceptsEveryEntryCaseInsensitively() {
        for (palette in LightPalette.entries) {
            assertEquals(palette, parseStoredPalette(palette.name))
            assertEquals(palette, parseStoredPalette(palette.name.lowercase()))
        }
    }

    @Test
    fun creamRegistryEntryMatchesShippedTokens() {
        // Guard against accidental edits to the shipped default palette.
        val cream = LightThemes.getValue(LightPalette.CREAM).colors
        assertEquals(Color(0xFFF5F1E7), cream.background)
        assertEquals(Color(0xFFB07F27), cream.accent)
        assertEquals(Color(0xFF8F6716), cream.accentText)
        assertEquals(Color(0xFF211C13), cream.textPrimary)
        // Contract-critical values (WCAG audit 2026-08-26): ok green and
        // warningText amber must stay AA on cream surfaces.
        assertEquals(Color(0xFF207947), cream.ok)
        assertEquals(Color(0xFF75500A), cream.warningText)
        assertEquals(Color(0xFF70521C), cream.heroAccentText)
    }

    @Test
    fun darkRegistryResolvesTextSlots() {
        // Regression guard (Aug 2026): DarkRedesignColors must pass through
        // resolved() like every light palette. Before this fix the three
        // slots were Unspecified in dark, rendering near-white instead of
        // gold/amber (hero eyebrow/$, menu selection, urgent status text).
        assertEquals(Color(0xFFC9A24B), DarkRedesignColors.accentText)
        assertEquals(Color(0xFFC9A24B), DarkRedesignColors.heroAccentText)
        assertEquals(Color(0xFFC58A1F), DarkRedesignColors.warningText)
        // Dark-specific overrides: brightened delete-item text on raised
        // menus; ink glyphs on lifted status/category fills.
        assertEquals(Color(0xFFEC7178), DarkRedesignColors.dangerText)
        assertEquals(Color(0xFF0F141B), DarkRedesignColors.onFill)
    }

    @Test
    fun emberCopperRegistryEntryMatchesShippedTokens() {
        // Guard against accidental edits to dark sibling #2 (visual gate
        // locked 2026-08-27: wood-tuned surfaces + clay-copper accent).
        val ember = DarkThemes.getValue(DarkPalette.EMBER_COPPER).colors
        assertEquals(Color(0xFF1A120B), ember.background)
        assertEquals(Color(0xFFCF8A62), ember.accent)
        assertEquals(Color(0xFFCF8A62), ember.accentText) // resolves to accent
        assertEquals(Color(0xFFCF8A62), ember.heroAccentText)
        assertEquals(Color(0xFFE3A63C), ember.warningText) // resolves to warning amber
        // Warm ink on fills - white glyphs fail on this palette's lifted hues.
        assertEquals(Color(0xFF1F1610), ember.onFill)
        assertEquals(Color(0xFF1B120B), ember.onAccent)
        // dangerText RESOLVES to raw danger: this palette's raised surface is
        // warm/lifted enough that the raw red stays >=4.5 (no override).
        assertEquals(ember.danger, ember.dangerText)
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

    @Test
    fun darkPaletteParsingDefaultsToObsidianGoldOnNullOrUnknown() {
        assertEquals(DarkPalette.OBSIDIAN_GOLD, parseStoredDarkPalette(null))
        assertEquals(DarkPalette.OBSIDIAN_GOLD, parseStoredDarkPalette("garbage"))
        assertEquals(DarkPalette.OBSIDIAN_GOLD, parseStoredDarkPalette("NOCTURNE"))
    }

    @Test
    fun darkPaletteParsingAcceptsEveryEntryCaseInsensitively() {
        for (palette in DarkPalette.entries) {
            assertEquals(palette, parseStoredDarkPalette(palette.name))
            assertEquals(palette, parseStoredDarkPalette(palette.name.lowercase()))
        }
    }

    @Test
    fun obsidianGoldRegistryEntryMatchesShippedTokens() {
        // Guard against accidental edits to the shipped default dark palette:
        // the registry entry must resolve to the exact shipped token table.
        val obsidian = DarkThemes.getValue(DarkPalette.OBSIDIAN_GOLD)
        assertEquals(DarkRedesignColors, obsidian.colors)
        // Contract-critical values (WCAG audit 2026-08-26): gold accentText
        // and amber warningText must stay AA on the dark surfaces.
        assertEquals(Color(0xFFC9A24B), obsidian.colors.accentText)
        assertEquals(Color(0xFFC58A1F), obsidian.colors.warningText)
    }
}
