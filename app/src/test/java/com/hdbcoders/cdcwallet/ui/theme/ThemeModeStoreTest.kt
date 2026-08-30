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
    fun mossGreenRegistryEntryMatchesShippedTokens() {
        // Guard against accidental edits to dark sibling #3 (visual gate
        // locked 2026-08-30 via tmp/dark-gate-3way: spruce canvas + sage-jade
        // accent; accent owns interactive, leaf heart/ok own status).
        val moss = DarkThemes.getValue(DarkPalette.MOSS_GREEN).colors
        assertEquals(Color(0xFF0F1712), moss.background)
        assertEquals(Color(0xFF7CC49A), moss.accent)
        assertEquals(Color(0xFF7CC49A), moss.accentText) // resolves to accent
        assertEquals(Color(0xFF7CC49A), moss.heroAccentText)
        assertEquals(Color(0xFFE3A63C), moss.warningText) // resolves to warning amber
        assertEquals(Color(0xFF0E1A13), moss.onFill)
        assertEquals(Color(0xFF0C1F15), moss.onAccent)
        // dangerText RESOLVES to raw danger: 4.81 on raised clears AA (no override).
        assertEquals(moss.danger, moss.dangerText)
    }

    @Test
    fun auberginePurpleRegistryEntryMatchesShippedTokens() {
        // Guard against accidental edits to dark sibling #4 (visual gate
        // locked 2026-08-30 via tmp/dark-gate-3way: aubergine canvas + orchid
        // accent ~57 deg from market violet - no role isolation needed).
        val aub = DarkThemes.getValue(DarkPalette.AUBERGINE_PURPLE).colors
        assertEquals(Color(0xFF140F1A), aub.background)
        assertEquals(Color(0xFFC4A0D4), aub.accent)
        assertEquals(Color(0xFFC4A0D4), aub.accentText) // resolves to accent
        assertEquals(Color(0xFFC4A0D4), aub.heroAccentText)
        assertEquals(Color(0xFFE3A63C), aub.warningText) // resolves to warning amber
        assertEquals(Color(0xFF150D1B), aub.onFill)
        assertEquals(Color(0xFF1C0F24), aub.onAccent)
        // dangerText RESOLVES to raw danger: 5.40 on raised clears AA (no override).
        assertEquals(aub.danger, aub.dangerText)
    }

    @Test
    fun darkPalettePickerVisibilityFlags() {
        // Moss Green is kept out of the picker for now (implemented and
        // registry-complete, hidden until product says show it); Ember Copper
        // stays visible alongside Aubergine Purple.
        assertTrue(DarkPalette.MOSS_GREEN.hiddenInPicker)
        assertFalse(DarkPalette.MIDNIGHT_GOLD.hiddenInPicker)
        assertFalse(DarkPalette.EMBER_COPPER.hiddenInPicker)
        assertFalse(DarkPalette.AUBERGINE_PURPLE.hiddenInPicker)
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
    fun darkPaletteParsingDefaultsToMidnightGoldOnNullOrUnknown() {
        assertEquals(DarkPalette.MIDNIGHT_GOLD, parseStoredDarkPalette(null))
        assertEquals(DarkPalette.MIDNIGHT_GOLD, parseStoredDarkPalette("garbage"))
        assertEquals(DarkPalette.MIDNIGHT_GOLD, parseStoredDarkPalette("NOCTURNE"))
        // Legacy-key migration: pre-rename installs persisted "obsidian_gold".
        // That key must keep resolving to this palette or existing users lose
        // their selection to the default.
        assertEquals(DarkPalette.MIDNIGHT_GOLD, parseStoredDarkPalette("obsidian_gold"))
        assertEquals(DarkPalette.MIDNIGHT_GOLD, parseStoredDarkPalette("OBSIDIAN_GOLD"))
    }

    @Test
    fun darkPaletteParsingAcceptsEveryEntryCaseInsensitively() {
        for (palette in DarkPalette.entries) {
            assertEquals(palette, parseStoredDarkPalette(palette.name))
            assertEquals(palette, parseStoredDarkPalette(palette.name.lowercase()))
        }
    }

    @Test
    fun midnightGoldRegistryEntryMatchesShippedTokens() {
        // Guard against accidental edits to the shipped default dark palette:
        // the registry entry must resolve to the exact shipped token table.
        val midnight = DarkThemes.getValue(DarkPalette.MIDNIGHT_GOLD)
        assertEquals(DarkRedesignColors, midnight.colors)
        // Contract-critical values (WCAG audit 2026-08-26): gold accentText
        // and amber warningText must stay AA on the dark surfaces.
        assertEquals(Color(0xFFC9A24B), midnight.colors.accentText)
        assertEquals(Color(0xFFC58A1F), midnight.colors.warningText)
    }
}
