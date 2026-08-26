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
    EMBER_COPPER(R.string.dark_palette_ember_copper),
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
 * Ember Copper - dark sibling #2: the kopitiam after last orders, charcoal
 * TIMBER under a tungsten lamp (no cool tones anywhere). Built for the two
 * real dark scenes: bedside at night AND bright-day checking, so secondary
 * text stays >= 8:1 on every surface.
 *
 * Contrast contract (machine-verified 2026-08-27): accentText /
 * heroAccentText = clay-copper #CF8A62 - 6.6/6.1/5.5 on bg/surface/raised,
 * 4.68/5.64 on the hero gradient stops; onAccent ink #1B120B = 6.56 on the
 * accent fill; warningText resolves to raw amber #E3A63C (8.0 surface,
 * washes pass); dangerText resolves to danger #EF6B74 (>=5.2 on surface AND
 * raised - this palette needs no brightened override); onFill warm ink
 * #1F1610 = 5.4-8.6 on every status/category fill; textTertiary clears 4.5
 * on all three backings (a first for any palette).
 *
 * ACCENT-vs-AMBER role isolation (documented per contract): accent hue sits
 * ~16 deg from warning amber, so the two never share a ROLE - amber appears
 * only as urgency text beside its status glyph, copper owns every
 * interactive surface. Hues stay close BY DESIGN (same wood-light family).
 *
 * Declared ABOVE the registry: Kotlin initializes top-level vals in file
 * order, and DarkThemes below consumes this table at construction.
 */
private val emberColors = RedesignColors(
    background = Color(0xFF1A120B), // walnut charcoal
    surface = Color(0xFF241910),
    surfaceRaised = Color(0xFF2F2114),
    hairline = Color(0xFF4A3826),
    hairlineSoft = Color(0xFF3A2B1D),
    textPrimary = Color(0xFFF7EEE0),
    textSecondary = Color(0xFFCFB89D),
    textTertiary = Color(0xFF9E8870),
    accent = Color(0xFFCF8A62), // muted clay-copper: fills, icons, borders AND text
    accentSoft = Color(0x2ECF8A62), // rgba(207,138,98,0.18)
    climate = Color(0xFF5E93DE),
    climateSoft = Color(0x295E93DE), // rgba(94,147,222,0.16)
    market = Color(0xFF9C7CE0),
    marketSoft = Color(0x299C7CE0), // rgba(156,124,224,0.16)
    heart = Color(0xFF4FA86B),
    heartSoft = Color(0x294FA86B), // rgba(79,168,107,0.16)
    ok = Color(0xFF71C68E),
    warning = Color(0xFFE3A63C),
    categoryFallback = Color(0xFF98908A),
    categoryFallbackSoft = Color(0x3398908A),
    summaryStart = Color(0xFF452A17), // lamp cone on teak
    summaryEnd = Color(0xFF331D0F),
    summaryBorder = Color(0x66CF8A62), // rgba(207,138,98,0.40)
    summaryHairline = Color(0x29CF8A62), // rgba(207,138,98,0.16)
    danger = Color(0xFFEF6B74),
    dangerSoft = Color(0x1FEF6B74), // rgba(239,107,116,0.12)
    onAccent = Color(0xFF1B120B), // warm ink on clay fills
    onFill = Color(0xFF1F1610), // warm ink glyphs - dark's lifted fills drop white to <3
).resolved()

/** Registry - the single place a dark palette maps to concrete colors. */
val DarkThemes: Map<DarkPalette, DarkTheme> = mapOf(
    DarkPalette.OBSIDIAN_GOLD to DarkTheme(
        DarkPalette.OBSIDIAN_GOLD,
        DarkRedesignColors,
        obsidianGoldM3(DarkRedesignColors),
    ),
    DarkPalette.EMBER_COPPER to DarkTheme(
        DarkPalette.EMBER_COPPER,
        emberColors,
        emberM3(emberColors),
    ),
)

private fun emberM3(colors: RedesignColors): ColorScheme = darkColorScheme(
    primary = colors.accent,
    onPrimary = colors.onAccent,
    primaryContainer = colors.accentSoft,
    onPrimaryContainer = Color(0xFFF4DCC5), // pale clay: 9.7:1 on accentSoft-over-surface
    // Derived from textSecondary/background - same rule as Obsidian Gold and
    // the light schemes; no orphan literals.
    secondary = colors.textSecondary,
    onSecondary = colors.background,
    secondaryContainer = colors.hairlineSoft,
    onSecondaryContainer = colors.textPrimary,
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
    surfaceContainerLowest = Color(0xFF150E08),
    surfaceContainerLow = Color(0xFF1F150C),
    surfaceContainer = colors.surface,
    surfaceContainerHigh = colors.hairlineSoft,
    surfaceContainerHighest = colors.hairline,
    outline = colors.textTertiary,
    outlineVariant = colors.hairline,
    error = colors.danger,
    onError = Color(0xFF330C0F), // deep maroon on the lifted red: 5.85
    // Derived from danger like every scheme here: container = danger washed
    // over the elevated surface (@14% over raised); on-container = danger
    // lightened toward white until >=4.5 (measures ~8.2).
    errorContainer = Color(0xFF4A2B22),
    onErrorContainer = Color(0xFFF8BCC0),
    // M3 completeness - inverseSurface is intentionally LIGHT in dark mode:
    // transient surfaces (Snackbar) flip to the classic light card. The
    // inverse button uses the flagship light accent (CREAM gold), same rule
    // as Obsidian Gold.
    inverseSurface = colors.textPrimary,
    inverseOnSurface = colors.background,
    inversePrimary = Color(0xFFB07F27),
    surfaceDim = colors.background,
    surfaceBright = colors.surfaceRaised,
    scrim = Color.Black,
)

/** Default when nothing (or something invalid) is persisted. */
fun defaultDarkPalette(): DarkPalette = DarkPalette.OBSIDIAN_GOLD

/** Parse a stored string, falling back to Obsidian Gold on null/unknown. */
fun parseStoredDarkPalette(stored: String?): DarkPalette =
    DarkPalette.entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
        ?: DarkPalette.OBSIDIAN_GOLD