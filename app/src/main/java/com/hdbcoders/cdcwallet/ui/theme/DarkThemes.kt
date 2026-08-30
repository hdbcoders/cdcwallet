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
 *      MIDNIGHT_GOLD entry as the template;
 *   3. add the display-name string in all four locales
 *      (values, values-zh-rCN, values-ms, values-ta).
 * The Settings picker reads [DarkPalette.entries], so it picks the new row
 * up automatically.
 *
 * PICKER VISIBILITY: [DarkPalette.hiddenInPicker] removes a palette's row
 * from Settings → Dark Themes WITHOUT unregistering it - a hidden palette
 * stays in the registry, keeps parsing its stored key, and a user who
 * already had it selected keeps it active until they pick another. Moss
 * Green is hidden this way for now (implemented and registry-complete, kept
 * out of the picker until product says show it); Ember Copper remains
 * visible alongside Aubergine Purple.
 *
 * Contrast contract: the same WCAG AA bar as the light palettes, measured on
 * the dark surfaces (see the LightThemes.kt header) - accentText /
 * heroAccentText gold #C9A24B (6.7/7.6 on the near-black gradient stops;
 * 6.0-7.7 on surfaces), warningText amber #C58A1F (5.5 surface / 6.2
 * canvas), dangerText #EC7178 (>=4.5 on surface AND surfaceRaised), onFill
 * ink #0F141B (>3:0 on every lifted status/category fill).
 */
enum class DarkPalette(val labelRes: Int, val hiddenInPicker: Boolean = false) {
    MIDNIGHT_GOLD(R.string.dark_palette_midnight_gold),
    EMBER_COPPER(R.string.dark_palette_ember_copper),
    MOSS_GREEN(R.string.dark_palette_moss_green, hiddenInPicker = true),
    AUBERGINE_PURPLE(R.string.dark_palette_aubergine_purple),
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
 * Midnight Gold - the app's shipped dark theme (the classic deep-navy + gold
 * look; renamed from "Obsidian Gold" because the canvas is unmistakably
 * navy, not jet-black - Midnight names the scene honestly). Tokens live in
 * [DarkRedesignColors] (RedesignColors.kt); this M3
 * scheme maps them with the same commented-rule discipline as the light
 * palettes (no orphan literals): every role either maps a token or states
 * its derivation rule.
 */
private fun midnightGoldM3(colors: RedesignColors): ColorScheme = darkColorScheme(
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

/**
 * Moss Green - dark sibling #3: the spruce greenhouse after dark (deep
 * green-charcoal canvas, sage-jade accent). The dark sibling of the JADE
 * light palette. Built for the two real dark scenes: bedside at night AND
 * bright-day checking, so secondary text stays >= 7:1 on every surface.
 *
 * Contrast contract (machine-verified 2026-08-30, HTML gate
 * tmp/dark-gate-3way): accentText / heroAccentText = sage-jade #7CC49B -
 * 8.87/7.99/7.00 on bg/surface/raised, 7.22/8.50 on the hero gradient stops;
 * onAccent ink #0C1F15 = 8.36 on the accent fill; warningText resolves to
 * raw amber #E3A63C (8.50 bg / 7.65 surface / 6.71 raised); dangerText
 * resolves to danger #EF6B74 (6.10/5.49/4.81 - raw passes raised, this
 * palette needs no brightened override); onFill green ink #0E1A13 = 5.4-8.7
 * on every status/category fill; textTertiary clears 4.5 on all three
 * backings.
 *
 * ACCENT-vs-HEART/OK role isolation (documented per contract): the mint-jade
 * accent hue sits ~15 deg from the leaf-green heart/ok fills, so the two
 * never share a ROLE - the accent owns every interactive surface (dashed
 * add row, pin, chip, menu selection), heart/ok own status and category
 * only. Hues stay close BY DESIGN (same botanic family).
 *
 * Declared ABOVE the registry: Kotlin initializes top-level vals in file
 * order, and DarkThemes below consumes this table at construction.
 */
private val mossColors = RedesignColors(
    background = Color(0xFF0F1712), // spruce charcoal
    surface = Color(0xFF16221B),
    surfaceRaised = Color(0xFF1F2D24),
    hairline = Color(0xFF2F4237),
    hairlineSoft = Color(0xFF26352C),
    textPrimary = Color(0xFFEEF6F0),
    textSecondary = Color(0xFFA3BCAD),
    textTertiary = Color(0xFF83A08F),
    accent = Color(0xFF7CC49A), // sage-jade: fills, icons, borders AND text
    accentSoft = Color(0x297CC49A), // rgba(124,196,154,0.16)
    climate = Color(0xFF5E93DE),
    climateSoft = Color(0x295E93DE), // rgba(94,147,222,0.16)
    market = Color(0xFF9C7CE0),
    marketSoft = Color(0x299C7CE0), // rgba(156,124,224,0.16)
    heart = Color(0xFF55AE72), // leaf-green: status/category only, never interactive
    heartSoft = Color(0x2955AE72), // rgba(85,174,114,0.16)
    ok = Color(0xFF6EC888),
    warning = Color(0xFFE3A63C),
    categoryFallback = Color(0xFF97A39B),
    categoryFallbackSoft = Color(0x3397A39B),
    summaryStart = Color(0xFF1C2B20), // moss floor under torchlight
    summaryEnd = Color(0xFF121C15),
    summaryBorder = Color(0x617CC49A), // rgba(124,196,154,0.38)
    summaryHairline = Color(0x297CC49A), // rgba(124,196,154,0.16)
    danger = Color(0xFFEF6B74),
    dangerSoft = Color(0x1FEF6B74), // rgba(239,107,116,0.12)
    onAccent = Color(0xFF0C1F15), // green ink on jade fills
    onFill = Color(0xFF0E1A13), // green ink glyphs - dark's lifted fills drop white to <3
).resolved()

/**
 * Aubergine Purple - dark sibling #4: the plum orchid at dusk (deep
 * aubergine canvas, pink-mauve orchid accent). Same two-scene build as its
 * siblings: secondary text >= 7:1 on every surface.
 *
 * Contrast contract (machine-verified 2026-08-30, HTML gate
 * tmp/dark-gate-3way): accentText / heroAccentText = orchid #C4A0D4 -
 * 8.38/7.85/7.17 on bg/surface/raised, 7.08/8.13 on the hero gradient stops;
 * onAccent ink #1C0F24 = 8.16 on the accent fill; warningText resolves to
 * raw amber #E3A63C (8.79 bg / 8.24 surface / 7.53 raised); dangerText
 * resolves to danger #EF6B74 (6.31/5.92/5.40 - raw passes raised, no
 * override); onFill plum ink #150D1B = 5.8-9.2 on every status/category
 * fill; textTertiary clears 4.5 on all three backings.
 *
 * ACCENT-vs-MARKET hue separation (no role isolation needed, unlike Ember's
 * accent-vs-amber note): the orchid accent sits ~315 deg, the market violet
 * ~258 deg - a ~57 deg gap on the wheel. A naive lilac accent (~285 deg)
 * would have collided with market; the pink-mauve shift is deliberate.
 *
 * Declared ABOVE the registry: Kotlin initializes top-level vals in file
 * order, and DarkThemes below consumes this table at construction.
 */
private val aubergineColors = RedesignColors(
    background = Color(0xFF140F1A), // aubergine charcoal
    surface = Color(0xFF1D1526),
    surfaceRaised = Color(0xFF271C33),
    hairline = Color(0xFF3D2D4E),
    hairlineSoft = Color(0xFF322443),
    textPrimary = Color(0xFFF3EFF7),
    textSecondary = Color(0xFFB4A6C4),
    textTertiary = Color(0xFF9185A5),
    accent = Color(0xFFC4A0D4), // pink-mauve orchid: fills, icons, borders AND text
    accentSoft = Color(0x29C4A0D4), // rgba(196,160,212,0.16)
    climate = Color(0xFF5E93DE),
    climateSoft = Color(0x295E93DE), // rgba(94,147,222,0.16)
    market = Color(0xFF9C7CE0), // violet: category only; ~57 deg from the accent
    marketSoft = Color(0x299C7CE0), // rgba(156,124,224,0.16)
    heart = Color(0xFF55AE72),
    heartSoft = Color(0x2955AE72), // rgba(85,174,114,0.16)
    ok = Color(0xFF71C68E),
    warning = Color(0xFFE3A63C),
    categoryFallback = Color(0xFF9A8FA3),
    categoryFallbackSoft = Color(0x339A8FA3),
    summaryStart = Color(0xFF2B1B38), // plum dusk lamp
    summaryEnd = Color(0xFF1A1122),
    summaryBorder = Color(0x61C4A0D4), // rgba(196,160,212,0.38)
    summaryHairline = Color(0x29C4A0D4), // rgba(196,160,212,0.16)
    danger = Color(0xFFEF6B74),
    dangerSoft = Color(0x1FEF6B74), // rgba(239,107,116,0.12)
    onAccent = Color(0xFF1C0F24), // plum ink on orchid fills
    onFill = Color(0xFF150D1B), // plum ink glyphs - dark's lifted fills drop white to <3
).resolved()

/** Registry - the single place a dark palette maps to concrete colors. */
val DarkThemes: Map<DarkPalette, DarkTheme> = mapOf(
    DarkPalette.MIDNIGHT_GOLD to DarkTheme(
        DarkPalette.MIDNIGHT_GOLD,
        DarkRedesignColors,
        midnightGoldM3(DarkRedesignColors),
    ),
    DarkPalette.EMBER_COPPER to DarkTheme(
        DarkPalette.EMBER_COPPER,
        emberColors,
        emberM3(emberColors),
    ),
    DarkPalette.MOSS_GREEN to DarkTheme(
        DarkPalette.MOSS_GREEN,
        mossColors,
        mossM3(mossColors),
    ),
    DarkPalette.AUBERGINE_PURPLE to DarkTheme(
        DarkPalette.AUBERGINE_PURPLE,
        aubergineColors,
        aubergineM3(aubergineColors),
    ),
)

private fun emberM3(colors: RedesignColors): ColorScheme = darkColorScheme(
    primary = colors.accent,
    onPrimary = colors.onAccent,
    primaryContainer = colors.accentSoft,
    onPrimaryContainer = Color(0xFFF4DCC5), // pale clay: 9.7:1 on accentSoft-over-surface
    // Derived from textSecondary/background - same rule as Midnight Gold and
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
    // as Midnight Gold.
    inverseSurface = colors.textPrimary,
    inverseOnSurface = colors.background,
    inversePrimary = Color(0xFFB07F27),
    surfaceDim = colors.background,
    surfaceBright = colors.surfaceRaised,
    scrim = Color.Black,
)

/**
 * Moss Green M3 - same mapping discipline as Ember Copper: every role maps
 * a token or states its derivation rule, no orphan literals except the
 * container ramps (derived from the spruce canvas, as in both siblings).
 */
private fun mossM3(colors: RedesignColors): ColorScheme = darkColorScheme(
    primary = colors.accent,
    onPrimary = colors.onAccent,
    primaryContainer = colors.accentSoft,
    onPrimaryContainer = Color(0xFFDCF2E4), // pale mint: 10.09:1 on accentSoft-over-surface
    // Derived from textSecondary/background - same rule as the siblings.
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
    surfaceContainerLowest = Color(0xFF0B110D),
    surfaceContainerLow = Color(0xFF131C16),
    surfaceContainer = colors.surface,
    surfaceContainerHigh = colors.hairlineSoft,
    surfaceContainerHighest = colors.hairline,
    outline = colors.textTertiary,
    outlineVariant = colors.hairline,
    error = colors.danger,
    onError = Color(0xFF330C0F), // deep maroon on the lifted red: 5.85
    // Derived from danger like every scheme here: container = danger washed
    // over the elevated surface (@14% over raised); on-container = danger
    // lightened toward white until >=4.5 (measures 7.35).
    errorContainer = Color(0xFF3C362F),
    onErrorContainer = Color(0xFFF8BCC0),
    // M3 completeness - inverseSurface intentionally LIGHT (Snackbar flips
    // to the classic light card); inverse button = CREAM gold, same rule.
    inverseSurface = colors.textPrimary,
    inverseOnSurface = colors.background,
    inversePrimary = Color(0xFFB07F27),
    surfaceDim = colors.background,
    surfaceBright = colors.surfaceRaised,
    scrim = Color.Black,
)

/**
 * Aubergine Purple M3 - same mapping discipline; container ramps derived
 * from the aubergine canvas.
 */
private fun aubergineM3(colors: RedesignColors): ColorScheme = darkColorScheme(
    primary = colors.accent,
    onPrimary = colors.onAccent,
    primaryContainer = colors.accentSoft,
    onPrimaryContainer = Color(0xFFEBDDF5), // pale lilac: 10.17:1 on accentSoft-over-surface
    // Derived from textSecondary/background - same rule as the siblings.
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
    surfaceContainerLowest = Color(0xFF0E0A13),
    surfaceContainerLow = Color(0xFF171020),
    surfaceContainer = colors.surface,
    surfaceContainerHigh = colors.hairlineSoft,
    surfaceContainerHighest = colors.hairline,
    outline = colors.textTertiary,
    outlineVariant = colors.hairline,
    error = colors.danger,
    onError = Color(0xFF330C0F), // deep maroon on the lifted red: 5.85
    // Derived from danger like every scheme here: container = danger washed
    // over the elevated surface (@14% over raised); on-container = danger
    // lightened toward white until >=4.5 (measures 8.12).
    errorContainer = Color(0xFF43273C),
    onErrorContainer = Color(0xFFF8BCC0),
    // M3 completeness - inverseSurface intentionally LIGHT (Snackbar flips
    // to the classic light card); inverse button = CREAM gold, same rule.
    inverseSurface = colors.textPrimary,
    inverseOnSurface = colors.background,
    inversePrimary = Color(0xFFB07F27),
    surfaceDim = colors.background,
    surfaceBright = colors.surfaceRaised,
    scrim = Color.Black,
)

/** Default when nothing (or something invalid) is persisted. */
fun defaultDarkPalette(): DarkPalette = DarkPalette.MIDNIGHT_GOLD

/**
 * Parse a stored string, falling back to Midnight Gold on null/unknown.
 *
 * Legacy-key migration: releases before the rename persisted the shipped
 * dark palette as "obsidian_gold". That key must keep resolving to this
 * palette (its enum name changed, its identity did not) or every existing
 * user silently drops back to the default - indistinguishable from their
 * choice being lost. Never remove this alias while any install may still
 * carry the old pref.
 */
fun parseStoredDarkPalette(stored: String?): DarkPalette {
    if (!stored.isNullOrBlank() && stored.equals("obsidian_gold", ignoreCase = true)) {
        return DarkPalette.MIDNIGHT_GOLD
    }
    return DarkPalette.entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
        ?: DarkPalette.MIDNIGHT_GOLD
}