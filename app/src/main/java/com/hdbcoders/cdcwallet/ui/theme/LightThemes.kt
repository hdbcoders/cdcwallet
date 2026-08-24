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
 * Contrast contract per palette (WCAG AA on that palette's own surfaces):
 *   - accentText >= 4.5:1 on surface and background;
 *   - heroText    >= 4.5:1 on both summaryStart and summaryEnd;
 *   - onAccent    >= 4.5:1 on gold;
 *   - ok / danger >= 4.5:1 on surface when used as status text.
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

private fun RedesignColors.resolved(): RedesignColors = copy(
    // Unspecified slots resolve to the shared defaults so each palette only
    // states what differs from the norm.
    accentText = if (accentText == Color.Unspecified) gold else accentText,
    heroText = if (heroText == Color.Unspecified) textPrimary else heroText,
)

/** Cream - the shipped default (identical to pre-registry LightRedesignColors). */
private val creamColors = RedesignColors(
    background = Color(0xFFF5F1E7),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    hairline = Color(0xFFE6E0D2),
    hairlineSoft = Color(0xFFEEE9DC),
    textPrimary = Color(0xFF211C13),
    textSecondary = Color(0xFF756E5C),
    textTertiary = Color(0xFFA79F8A),
    gold = Color(0xFFB07F27),
    goldSoft = Color(0x1FB07F27), // rgba(176,127,39,0.12)
    climate = Color(0xFF3568C4),
    climateSoft = Color(0x1F3568C4), // rgba(53,104,196,0.12)
    market = Color(0xFF7B54D6),
    marketSoft = Color(0x1F7B54D6), // rgba(123,84,214,0.12)
    heart = Color(0xFF1E7F4C),
    heartSoft = Color(0x1F1E7F4C), // rgba(30,127,76,0.12)
    ok = Color(0xFF2E9358),
    warning = Color(0xFFC58A1F),
    categoryFallback = Color(0xFF8A8578),
    categoryFallbackSoft = Color(0x338A8578),
    summaryStart = Color(0xFFF3E3C4),
    summaryEnd = Color(0xFFECD7A9),
    summaryBorder = Color(0x59B07F27), // rgba(176,127,39,0.35)
    summaryHairline = Color(0x33B07F27), // rgba(176,127,39,0.20)
    danger = Color(0xFFC7373F),
    dangerSoft = Color(0x1AC7373F), // rgba(199,55,63,0.10)
    // accentText #8F6716: AA-safe gold for text (~4.8:1); fills keep #B07F27.
    accentText = Color(0xFF8F6716),
).resolved()

private fun creamM3(colors: RedesignColors): ColorScheme = lightColorScheme(
    primary = colors.gold,
    onPrimary = colors.onAccent,
    primaryContainer = colors.goldSoft,
    onPrimaryContainer = Color(0xFF4C3309),
    secondary = colors.textSecondary,
    onSecondary = Color.White,
    secondaryContainer = colors.hairlineSoft,
    onSecondaryContainer = colors.textPrimary,
    tertiary = Color(0xFF5A6B7A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDEE8F0),
    onTertiaryContainer = Color(0xFF17242E),
    background = colors.background,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.hairlineSoft,
    onSurfaceVariant = colors.textSecondary,
    surfaceTint = colors.gold,
    surfaceContainerLowest = colors.surface,
    surfaceContainerLow = Color(0xFFFAF7EF),
    surfaceContainer = Color(0xFFF4EFE3),
    surfaceContainerHigh = colors.hairlineSoft,
    surfaceContainerHighest = colors.hairline,
    outline = colors.textTertiary,
    outlineVariant = colors.hairline,
    error = colors.danger,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF5F0A10),
)

/** Jade - celadon canvas, jade accent (accent passes AA as text directly). */
private val jadeColors = RedesignColors(
    background = Color(0xFFECF5EF),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    hairline = Color(0xFFDCEAE2),
    hairlineSoft = Color(0xFFE7F0EB),
    textPrimary = Color(0xFF16281F),
    textSecondary = Color(0xFF4E6459),
    textTertiary = Color(0xFF93A79B),
    gold = Color(0xFF0E6B58), // jade: fills, icons, borders AND text
    goldSoft = Color(0x1F0E6B58), // rgba(14,107,88,0.12)
    climate = Color(0xFF3568C4),
    climateSoft = Color(0x1F3568C4), // rgba(53,104,196,0.12)
    market = Color(0xFF7B54D6),
    marketSoft = Color(0x1F7B54D6), // rgba(123,84,214,0.12)
    heart = Color(0xFF5C7A3D),
    heartSoft = Color(0x1F5C7A3D), // rgba(92,122,61,0.12)
    ok = Color(0xFF108244),
    warning = Color(0xFFC58A1F),
    categoryFallback = Color(0xFF868B80),
    categoryFallbackSoft = Color(0x33868B80),
    summaryStart = Color(0xFFDFF3EA),
    summaryEnd = Color(0xFFCDE9DC),
    summaryBorder = Color(0x520E6B58), // rgba(14,107,88,0.32)
    summaryHairline = Color(0x330E6B58), // rgba(14,107,88,0.20)
    danger = Color(0xFFC7373F),
    dangerSoft = Color(0x1AC7373F), // rgba(199,55,63,0.10)
).resolved()

private fun jadeM3(colors: RedesignColors): ColorScheme = lightColorScheme(
    primary = colors.gold,
    onPrimary = Color.White,
    primaryContainer = colors.summaryStart,
    onPrimaryContainer = Color(0xFF0E3B30),
    secondary = colors.textSecondary,
    onSecondary = Color.White,
    secondaryContainer = colors.hairlineSoft,
    onSecondaryContainer = colors.textPrimary,
    tertiary = Color(0xFF50606E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE5EC),
    onTertiaryContainer = Color(0xFF14222B),
    background = colors.background,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.hairlineSoft,
    onSurfaceVariant = colors.textSecondary,
    surfaceTint = colors.gold,
    surfaceContainerLowest = colors.surface,
    surfaceContainerLow = Color(0xFFF4FAF6),
    surfaceContainer = colors.hairlineSoft,
    surfaceContainerHigh = colors.hairline,
    surfaceContainerHighest = Color(0xFFCFE3DA),
    outline = colors.textTertiary,
    outlineVariant = colors.hairline,
    error = colors.danger,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF5F0A10),
)

/**
 * Ocean - tonal blue with no white anywhere. Hero gradient is saturated
 * enough that even navy fails AA on its dark end, so heroText stays
 * textPrimary; pill labels are neutral while icons carry the category hue
 * (pillLabel slot) - that split frees climate to be a genuine blue.
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
    gold = Color(0xFF14528F), // deep navy: fills, icons, borders AND text
    goldSoft = Color(0x2914528F), // rgba(20,82,143,0.16)
    climate = Color(0xFF2A6FDB),
    climateSoft = Color(0x292A6FDB), // rgba(42,111,219,0.16)
    market = Color(0xFF7B54D6),
    marketSoft = Color(0x297B54D6), // rgba(123,84,214,0.16)
    heart = Color(0xFF2E7D52),
    heartSoft = Color(0x292E7D52), // rgba(46,125,82,0.16)
    ok = Color(0xFF0C7749),
    warning = Color(0xFFC58A1F),
    categoryFallback = Color(0xFF5C7893),
    categoryFallbackSoft = Color(0x335C7893),
    summaryStart = Color(0xFFA7CFF2),
    summaryEnd = Color(0xFF82B7E9),
    summaryBorder = Color(0x6B14528F), // rgba(20,82,143,0.42)
    summaryHairline = Color(0x3D14528F), // rgba(20,82,143,0.24)
    danger = Color(0xFFB32E36),
    dangerSoft = Color(0x24B32E36), // rgba(179,46,54,0.14)
    onAccent = Color(0xFFE3F1FC), // pale sky instead of ink on navy fills
).resolved()

private fun oceanM3(colors: RedesignColors): ColorScheme = lightColorScheme(
    primary = colors.gold,
    onPrimary = colors.onAccent,
    primaryContainer = Color(0xFFC7DFF5),
    onPrimaryContainer = Color(0xFF0D3D6B),
    secondary = colors.textSecondary,
    onSecondary = colors.onAccent,
    secondaryContainer = colors.hairlineSoft,
    onSecondaryContainer = colors.textPrimary,
    tertiary = Color(0xFF4C5F70),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6E2EC),
    onTertiaryContainer = Color(0xFF121F2A),
    background = colors.background,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.hairlineSoft,
    onSurfaceVariant = colors.textSecondary,
    surfaceTint = colors.gold,
    surfaceContainerLowest = Color(0xFFF2F8FE),
    surfaceContainerLow = Color(0xFFE9F3FC),
    surfaceContainer = colors.surface,
    surfaceContainerHigh = colors.hairlineSoft,
    surfaceContainerHighest = Color(0xFFBEDAF3),
    outline = colors.textTertiary,
    outlineVariant = colors.hairline,
    error = colors.danger,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF5F0A10),
)

/** Registry - the single place a palette maps to concrete colors. */
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
