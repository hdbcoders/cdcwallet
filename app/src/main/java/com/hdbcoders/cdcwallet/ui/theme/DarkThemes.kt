package com.hdbcoders.cdcwallet.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.hdbcoders.cdcwallet.R

/**
 * User-selectable dark palettes (spec 07 §7.2). The mirror of
 * LightThemes.kt for dark mode: the dark theme is no longer a hardcoded
 * singleton - it is a registry entry, so adding a second dark theme later is
 * a one-entry change.
 *
 * HOW TO ADD A NEW DARK THEME (three touches, nothing else):
 *   1. add an enum entry to [DarkPalette];
 *   2. write its token table + `darkColorScheme` in this file, following the
 *      OBSIDIAN_GOLD entry as the template;
 *   3. add the display-name string in all four locales
 *      (values, values-zh-rCN, values-ms, values-ta).
 * The Settings picker reads [DarkPalette.entries], so it picks the new row
 * up automatically.
 *
 * Contrast contract: the same WCAG AA bar as the light palettes, measured on
 * the dark surfaces (see the LightThemes.kt header) - accentText /
 * heroAccentText gold #C9A24B (6.7/7.6 on the near-black gradient stops;
 * 6.0-7.7 on surfaces), warningText amber #C58A1F (5.5 surface / 6.2
 * canvas), dangerText #EC7178 (>=4.5 on surface AND surfaceRaised), onFill
 * ink #0F141B (>3:0 on every lifted status/category fill).
 */
enum class DarkPalette(val labelRes: Int) {
    OBSIDIAN_GOLD(R.string.dark_palette_obsidian_gold),
}

/**
 * A dark palette: redesign tokens + the M3 scheme derived from them, same
 * shape as [LightTheme]. [colors] drives components via LocalRedesignColors;
 * the M3 scheme feeds dialogs/menus/FABs.
 */
data class DarkTheme(
    val palette: DarkPalette,
    val colors: RedesignColors,
    val m3: ColorScheme,
)

/**
 * Obsidian Gold - the app's shipped dark theme (the classic navy + gold
 * look). Tokens live in [DarkRedesignColors] (RedesignColors.kt); this M3
 * scheme maps them with the same commented-rule discipline as the light
 * palettes (no orphan literals): every role either maps a token or states
 * its derivation rule.
 */
private fun obsidianGoldM3(colors: RedesignColors): ColorScheme = darkColorScheme(
    primary = colors.accent,
    onPrimary = colors.onAccent,
    primaryContainer = colors.accentSoft,
    onPrimaryContainer = Color(0xFFE9D9A8), // pale gold: 9.3:1 on accentSoft-over-surface
    // Derived from textSecondary/background - same rule as the light schemes;
    // replaces the orphan slate family that answered to no token.
    secondary = colors.textSecondary,
    onSecondary = colors.background,
    secondaryContainer = colors.hairlineSoft,
    onSecondaryContainer = Color(0xFFD7E0EA),
    tertiary = colors.textSecondary,
    onTertiary = colors.background,
    tertiaryContainer = colors.hairlineSoft,
    onTertiaryContainer = colors.textPrimary,
    background = colors.background,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceRaised,
    onSurfaceVariant = colors.textSecondary,
    surfaceTint = colors.accent,
    surfaceContainerLowest = Color(0xFF0B1017),
    surfaceContainerLow = Color(0xFF141B25),
    surfaceContainer = Color(0xFF171F2B),
    surfaceContainerHigh = Color(0xFF202A3A),
    surfaceContainerHighest = Color(0xFF263244),
    outline = colors.textTertiary,
    outlineVariant = colors.hairline,
    error = colors.danger,
    onError = Color(0xFF2A0A0C),
    // Derived from danger like the light schemes: container = danger washed
    // over the elevated surface, on-container = danger lightened toward
    // white until >=4.5 (measures 9.4).
    errorContainer = Color(0xFF3B171B),
    onErrorContainer = Color(0xFFF5B8BC),
    // M3 completeness - without these, baseline greys/purple leak through
    // (e.g. Snackbars). inverseSurface is intentionally LIGHT in dark mode:
    // it flips transient surfaces (Snackbar) to the classic light card.
    inverseSurface = colors.textPrimary,
    inverseOnSurface = colors.background,
    inversePrimary = Color(0xFFB07F27), // the CREAM accent: gold button on the light inverse card, ink content
    surfaceDim = colors.background,
    surfaceBright = colors.surfaceRaised,
    scrim = Color.Black,
)

/**
 * Registry - the single place a dark palette maps to concrete colors. Today
 * it holds exactly one entry (Obsidian Gold, the shipped dark theme); adding
 * a second dark theme = one enum entry + one registry row + four strings.
 */
val DarkThemes: Map<DarkPalette, DarkTheme> = mapOf(
    DarkPalette.OBSIDIAN_GOLD to DarkTheme(
        DarkPalette.OBSIDIAN_GOLD,
        DarkRedesignColors,
        obsidianGoldM3(DarkRedesignColors),
    ),
)

/** Default when nothing (or something invalid) is persisted. */
fun defaultDarkPalette(): DarkPalette = DarkPalette.OBSIDIAN_GOLD

/** Parse a stored string, falling back to Obsidian Gold on null/unknown. */
fun parseStoredDarkPalette(stored: String?): DarkPalette =
    DarkPalette.entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
        ?: DarkPalette.OBSIDIAN_GOLD