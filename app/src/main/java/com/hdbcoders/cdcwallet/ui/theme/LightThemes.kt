package com.hdbcoders.cdcwallet.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.hdbcoders.cdcwallet.R

/**
 * User-selectable light palettes (spec 07 §7.2, theme-picker feature).
 *
 * HOW TO ADD A NEW THEME (three touches, nothing else):
 *   1. add an enum entry to [LightPalette];
 *   2. write its token table + `lightColorScheme` in this file, following the
 *      CREAM entry as the template;
 *   3. add the display-name string in all four locales
 *      (values, values-zh-rCN, values-ms, values-ta).
 * The Settings picker reads [LightPalette.entries], so it picks the new row
 * up automatically.
 *
 * Contrast contract per palette (WCAG AA on that palette's own surfaces,
 * machine-verified 2026-08-26 with the WCAG 2.x relative-luminance formula):
 *   - accentText >= 4.5:1 on surface and background;
 *   - warningText >= 4.5:1 on every light surface/background AND on the
 *     amber soft washes (raw [RedesignColors.warning] amber is fills/icons
 *     only - it fails 2.4-3.0:1 as text);
 *   - heroAccentText (the hero eyebrow and `$` glyph) >= 4.5:1 on BOTH
 *     summaryStart and summaryEnd;
 *   - onAccent    >= 4.5:1 on accent;
 *   - ok / danger >= 4.5:1 on surface when used as status text.
 *
 * DARK mode resolves through the same [RedesignColors.resolved] and is held
 * to the same bar (measured 2026-08-26): heroAccentText/accentText = gold
 * #C9A24B (6.7/7.6 on the near-black gradient stops; 6.0-7.7 on surfaces),
 * warningText = amber #C58A1F (5.5 surface / 6.2 canvas), dangerText =
 * #EC7178 (>=4.5 on surface AND surfaceRaised), onFill = ink #0F141B
 * (>3:0 on every lifted status/category fill).
 */
enum class LightPalette(val labelRes: Int) {
    CREAM(R.string.palette_cream),
    JADE(R.string.palette_jade),
    OCEAN(R.string.palette_ocean),
}

/**
 * A light palette: redesign tokens + the M3 scheme derived from them. The
 * token table drives components via LocalRedesignColors; the M3 scheme only
 * feeds dialogs/menus/FABs. Dark mode is NOT part of this registry - it stays
 * the single navy+gold DarkRedesignColors/DarkScheme for every palette.
 */
data class LightTheme(
    val palette: LightPalette,
    val colors: RedesignColors,
    val m3: ColorScheme,
)

// Unspecified text-context slots resolve via RedesignColors.resolved()
// (internal, shared with dark mode) applied at each palette's construction.

/** Cream - the shipped default (identical to pre-registry LightRedesignColors). */
private val creamColors = RedesignColors(
    background = Color(0xFFF5F1E7),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    hairline = Color(0xFFE6E0D2),
    hairlineSoft = Color(0xFFEEE9DC),
    textPrimary = Color(0xFF211C13),
    textSecondary = Color(0xFF706957), // was #756E5C (4.4958:1 on bg - missed AA by 0.004); now 4.84:1
    textTertiary = Color(0xFFA79F8A),
    accent = Color(0xFFB07F27),
    accentSoft = Color(0x1FB07F27), // rgba(176,127,39,0.12)
    climate = Color(0xFF3568C4),
    climateSoft = Color(0x1F3568C4), // rgba(53,104,196,0.12)
    market = Color(0xFF7B54D6),
    marketSoft = Color(0x1F7B54D6), // rgba(123,84,214,0.12)
    heart = Color(0xFF1E7F4C),
    heartSoft = Color(0x1F1E7F4C), // rgba(30,127,76,0.12)
    ok = Color(0xFF207947), // was #2E9358 (3.87:1 on white - violated the contrast contract); 5.40 white / 4.79 bg
    warning = Color(0xFFC58A1F),
    warningText = Color(0xFF75500A), // dark amber: >=4.95:1 on every cream surface/wash; raw amber is fills/icons only
    categoryFallback = Color(0xFF8A8578),
    categoryFallbackSoft = Color(0x338A8578),
    summaryStart = Color(0xFFF3E3C4),
    summaryEnd = Color(0xFFECD7A9),
    summaryBorder = Color(0x59B07F27), // rgba(176,127,39,0.35)
    summaryHairline = Color(0x33B07F27), // rgba(176,127,39,0.20)
    danger = Color(0xFFC7373F),
    dangerSoft = Color(0x1AC7373F), // rgba(199,55,63,0.10)
    // accentText #8F6716: AA-safe gold for text (4.52 bg / 5.10 surface); fills keep #B07F27.
    accentText = Color(0xFF8F6716),
    heroAccentText = Color(0xFF70521C), // hero eyebrow + dollar glyph: 5.70 / 5.10 on the gradient stops (#8A6220 failed at 4.31/3.86, raw accent 2.81/2.51)
).resolved()

private fun creamM3(colors: RedesignColors): ColorScheme = lightColorScheme(
    primary = colors.accent,
    onPrimary = colors.onAccent,
    primaryContainer = colors.accentSoft,
    onPrimaryContainer = Color(0xFF4C3309),
    secondary = colors.textSecondary,
    onSecondary = Color.White,
    secondaryContainer = colors.hairlineSoft,
    onSecondaryContainer = colors.textPrimary,
    // Derived from the palette's own textSecondary instead of an orphan slate
    // literal; white-on-tertiary passes AA in every palette.
    tertiary = colors.textSecondary,
    onTertiary = Color.White,
    tertiaryContainer = colors.hairlineSoft,
    onTertiaryContainer = colors.textPrimary,
    background = colors.background,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.hairlineSoft,
    onSurfaceVariant = colors.textSecondary,
    surfaceTint = colors.accent,
    surfaceContainerLowest = colors.surface,
    surfaceContainerLow = Color(0xFFFAF7EF),
    surfaceContainer = Color(0xFFF4EFE3),
    surfaceContainerHigh = colors.hairlineSoft,
    surfaceContainerHighest = colors.hairline,
    outline = colors.textTertiary,
    outlineVariant = colors.hairline,
    error = colors.danger,
    onError = Color.White,
    // Derived from the palette's own danger (danger @12% over white;
    // onErrorContainer = danger x 0.28) instead of copy-pasted Material
    // defaults - text on container measures 14:1.
    errorContainer = Color(0xFFF8E7E8),
    onErrorContainer = Color(0xFF380F12),
)

/** Jade - tonal mint with no white anywhere (designer mockup + review fixes:
 *  ok deepened to #0C7749 and danger to #B32E36 because status text sits on
 *  mint cards; onAccent is pale mint #E4FBF0 instead of white). */
private val jadeColors = RedesignColors(
    background = Color(0xFFB9E6C9),
    surface = Color(0xFFD2F0DE),
    surfaceRaised = Color(0xFFD2F0DE),
    hairline = Color(0xFF8FCDA8),
    hairlineSoft = Color(0xFFBFE3CC),
    textPrimary = Color(0xFF0F231A),
    textSecondary = Color(0xFF256B45),
    textTertiary = Color(0xFF5FA37D),
    accent = Color(0xFF0A6E52), // jade: fills, icons, borders AND text (4.5:1 canvas, 5.1:1 surface)
    accentSoft = Color(0x290A6E52), // rgba(10,110,82,0.16)
    climate = Color(0xFF3568C4),
    climateSoft = Color(0x293568C4), // rgba(53,104,196,0.16)
    market = Color(0xFF7B54D6),
    marketSoft = Color(0x297B54D6), // rgba(123,84,214,0.16)
    heart = Color(0xFF55701F), // olive shift: separates heartland from the accent/ok blue-greens
    heartSoft = Color(0x2955701F), // rgba(85,112,31,0.16)
    ok = Color(0xFF0C7749),
    warning = Color(0xFFC58A1F),
    warningText = Color(0xFF75500A), // >=5.19:1 on mint surfaces/washes; raw amber is fills/icons only
    categoryFallback = Color(0xFF6F8F7C),
    categoryFallbackSoft = Color(0x336F8F7C),
    summaryStart = Color(0xFF9FE0B8),
    summaryEnd = Color(0xFF7FD29E),
    summaryBorder = Color(0x6B0A6E52), // rgba(10,110,82,0.42)
    summaryHairline = Color(0x3D0A6E52), // rgba(10,110,82,0.24)
    danger = Color(0xFFB32E36),
    dangerSoft = Color(0x24B32E36), // rgba(179,46,54,0.14)
    onAccent = Color(0xFFE4FBF0), // pale mint instead of ink/white on jade fills
    heroAccentText = Color(0xFF074D39), // hero eyebrow + dollar glyph: 6.49 / 5.45 on the gradient stops (accent alone fails at 4.11/3.45)
).resolved()

private fun jadeM3(colors: RedesignColors): ColorScheme = lightColorScheme(
    primary = colors.accent,
    onPrimary = colors.onAccent,
    primaryContainer = Color(0xFFC9EBD8),
    onPrimaryContainer = Color(0xFF074D39),
    secondary = colors.textSecondary,
    onSecondary = colors.onAccent,
    secondaryContainer = colors.hairlineSoft,
    onSecondaryContainer = colors.textPrimary,
    tertiary = colors.textSecondary, // derived, same rule as creamM3
    onTertiary = Color.White,
    tertiaryContainer = colors.hairlineSoft,
    onTertiaryContainer = colors.textPrimary,
    background = colors.background,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.hairlineSoft,
    onSurfaceVariant = colors.textSecondary,
    surfaceTint = colors.accent,
    surfaceContainerLowest = Color(0xFFE6F7ED),
    surfaceContainerLow = Color(0xFFD8F1E2),
    surfaceContainer = colors.surface,
    surfaceContainerHigh = colors.hairlineSoft,
    surfaceContainerHighest = Color(0xFFA5D6B8),
    outline = colors.textTertiary,
    outlineVariant = colors.hairline,
    error = colors.danger,
    onError = Color.White,
    // Derived from the palette's danger (same rule as creamM3) - 14.5:1 text.
    errorContainer = Color(0xFFF6E6E7),
    onErrorContainer = Color(0xFF320D0F),
)

/**
 * Ocean - tonal blue with no white anywhere. Hero gradient end is deepened
 * (#65A4E6, was #82B7E9) so the balance card anchors against the previously
 * near-identical surface tone, and the eyebrow/glyph get their own deep-navy
 * slot ([heroAccentText]) because even the navy accent fails AA on the pale
 * gradient start.
 */
private val oceanColors = RedesignColors(
    background = Color(0xFFBFD9F0),
    surface = Color(0xFFD9EAFA),
    surfaceRaised = Color(0xFFD9EAFA),
    hairline = Color(0xFFA9CBEA),
    hairlineSoft = Color(0xFFCFE3F4),
    textPrimary = Color(0xFF0C1B2A),
    textSecondary = Color(0xFF1F4E73),
    textTertiary = Color(0xFF6A93B8),
    accent = Color(0xFF14528F), // deep navy: fills, icons, borders AND text
    accentSoft = Color(0x2914528F), // rgba(20,82,143,0.16)
    climate = Color(0xFF2A6FDB),
    climateSoft = Color(0x292A6FDB), // rgba(42,111,219,0.16)
    market = Color(0xFF7B54D6),
    marketSoft = Color(0x297B54D6), // rgba(123,84,214,0.16)
    heart = Color(0xFF2E7D52),
    heartSoft = Color(0x292E7D52), // rgba(46,125,82,0.16)
    ok = Color(0xFF0C7749),
    warning = Color(0xFFC58A1F),
    warningText = Color(0xFF75500A), // >=5.15:1 on sky surfaces/washes; raw amber is fills/icons only
    categoryFallback = Color(0xFF5C7893),
    categoryFallbackSoft = Color(0x335C7893),
    summaryStart = Color(0xFFA7CFF2),
    summaryEnd = Color(0xFF65A4E6),
    summaryBorder = Color(0x6B14528F), // rgba(20,82,143,0.42)
    summaryHairline = Color(0x3D14528F), // rgba(20,82,143,0.24)
    danger = Color(0xFFB32E36),
    dangerSoft = Color(0x24B32E36), // rgba(179,46,54,0.14)
    onAccent = Color(0xFFE3F1FC), // pale sky instead of ink on navy fills
    heroAccentText = Color(0xFF0A3560), // hero eyebrow + dollar glyph: 7.60 / 4.73 on the gradient stops (accent alone fails at 3.04 on end)
).resolved()

private fun oceanM3(colors: RedesignColors): ColorScheme = lightColorScheme(
    primary = colors.accent,
    onPrimary = colors.onAccent,
    primaryContainer = Color(0xFFC7DFF5),
    onPrimaryContainer = Color(0xFF0D3D6B),
    secondary = colors.textSecondary,
    onSecondary = colors.onAccent,
    secondaryContainer = colors.hairlineSoft,
    onSecondaryContainer = colors.textPrimary,
    tertiary = colors.textSecondary, // derived, same rule as creamM3
    onTertiary = Color.White,
    tertiaryContainer = colors.hairlineSoft,
    onTertiaryContainer = colors.textPrimary,
    background = colors.background,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.hairlineSoft,
    onSurfaceVariant = colors.textSecondary,
    surfaceTint = colors.accent,
    surfaceContainerLowest = Color(0xFFF2F8FE),
    surfaceContainerLow = Color(0xFFE9F3FC),
    surfaceContainer = colors.surface,
    surfaceContainerHigh = colors.hairlineSoft,
    surfaceContainerHighest = Color(0xFFBEDAF3),
    outline = colors.textTertiary,
    outlineVariant = colors.hairline,
    error = colors.danger,
    onError = Color.White,
    // Derived from the palette's danger (same rule as creamM3) - 14.5:1 text.
    errorContainer = Color(0xFFF6E6E7),
    onErrorContainer = Color(0xFF320D0F),
)

/**
 * Registry - the single place a palette maps to concrete colors.
 *
 * surfaceContainerLowest rule: equals [RedesignColors.surface] when the
 * surface is already near-white (CREAM), else a dedicated lighter tint
 * (JADE/OCEAN) so dialogs/sheets always read above the canvas.
 */
val LightThemes: Map<LightPalette, LightTheme> = mapOf(
    LightPalette.CREAM to LightTheme(LightPalette.CREAM, creamColors, creamM3(creamColors)),
    LightPalette.JADE to LightTheme(LightPalette.JADE, jadeColors, jadeM3(jadeColors)),
    LightPalette.OCEAN to LightTheme(LightPalette.OCEAN, oceanColors, oceanM3(oceanColors)),
)

/** Default when nothing (or something invalid) is persisted. */
fun defaultLightPalette(): LightPalette = LightPalette.CREAM

/** Parse a stored string, falling back to CREAM on null/unknown. */
fun parseStoredPalette(stored: String?): LightPalette =
    LightPalette.entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
        ?: LightPalette.CREAM

/** Backwards-compatible alias: the active light palette's token table. */
val LightRedesignColors: RedesignColors
    get() = LightThemes.getValue(defaultLightPalette()).colors
