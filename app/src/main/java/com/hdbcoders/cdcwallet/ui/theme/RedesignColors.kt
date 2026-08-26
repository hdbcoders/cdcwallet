package com.hdbcoders.cdcwallet.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The redesign's semantic tokens, taken verbatim from the product mockups
 * (light: cream + gold; dark: deep navy + gold). Components read these via
 * [LocalRedesignColors] instead of guessing Material3 roles, so the ticket
 * cards, hero, header and banners match the HTML pixel-for-pixel in both
 * modes. M3 roles (buttons, dialogs, text fields) are mapped from the same
 * tokens in [AppTheme]'s color schemes.
 */
data class RedesignColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val hairline: Color,
    val hairlineSoft: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /**
     * The palette's saturated accent: fills, icons, borders, the dashed
     * add-row outline and large display glyphs (`$`). Named `accent` because
     * only CREAM's is actually gold - JADE holds a deep green, OCEAN a deep
     * navy. For accent AS SMALL TEXT see [accentText].
     */
    val accent: Color,
    val accentSoft: Color,
    val climate: Color,
    val climateSoft: Color,
    val market: Color,
    val marketSoft: Color,
    val heart: Color,
    val heartSoft: Color,
    val ok: Color,
    /**
     * Amber urgency hue ("soon" state). Deliberately identical across both
     * themes - it was a hardcoded literal before centralization (refactor L6)
     * and no product-approved dark variant exists yet. FILLS/ICONS only:
     * status TEXT must use [warningText] (the raw amber fails WCAG AA as
     * text on every light surface, 2.4-3.0:1).
     */
    val warning: Color,
    /**
     * Fallback visuals for unknown categories (refactor L7): neutral warm
     * gray hue + its soft tint. Shared by both themes, same as the literals
     * previously hardcoded in CategoryVisuals.
     */
    val categoryFallback: Color,
    val categoryFallbackSoft: Color,
    val summaryStart: Color,
    val summaryEnd: Color,
    val summaryBorder: Color,
    /** Reserved: gold hairline for hero interior detailing (unconsumed yet). */
    val summaryHairline: Color,
    val danger: Color,
    val dangerSoft: Color,
    /**
     * Danger hue when used AS TEXT on elevated surfaces (delete menu items).
     * Defaults to [danger]; dark overrides with a brightened variant because
     * raw #E5636B dips below AA on surfaceRaised (4.35:1). Fills, badges and
     * banner washes keep the raw [danger].
     */
    val dangerText: Color = Color.Unspecified,
    /**
     * Glyph color painted ON status/category fills (the ok-circle check, the
     * category icons). Light palettes keep white - their fills were deepened
     * for exactly that (4.1-5.6:1). DARK lifts the same hues, where white
     * fails (2.2-2.8:1), so dark overrides with near-canvas ink (~7:1).
     */
    val onFill: Color = Color.White,
    /**
     * Accent color when used AS TEXT (menu selection, pins, add-row label).
     * Equals [accent] for palettes whose accent passes AA as body text (jade,
     * navy); darker variant for palettes where it does not (gold, bronze).
     * Light-mode slot; dark mode always uses its own accent directly.
     */
    val accentText: Color = Color.Unspecified,
    /**
     * On-accent content color (text/icons rendered on saturated accent fills,
     * e.g. the archived-count chip). Defaults to near-black ink.
     */
    val onAccent: Color = Color(0xFF17130A),
    /**
     * Amber urgency hue when used AS TEXT ("soon" / urgent status label at
     * 12.5sp). The raw [warning] amber fails AA on every light surface
     * (2.4-3.0:1), so light palettes must set a darkened amber here; falls
     * back to [warning] when unspecified (dark mode, where amber passes
     * 5.5:1 - deliberately kept as-is pending a product-approved variant,
     * see the [warning] doc below).
     */
    val warningText: Color = Color.Unspecified,
    /**
     * Accent hue as displayed ON THE HERO GRADIENT: the "BALANCE" eyebrow
     * and the big serif `$` glyph. Must pass AA (>= 4.5:1) on BOTH gradient
     * stops ([summaryStart]/[summaryEnd]) - accents/browns that read fine on
     * canvas fail there (e.g. CREAM's raw accent is 2.51-2.81:1 on its own
     * gradient). Falls back to [accent] when unspecified (dark mode, whose
     * hero gradient is near-black, keeping today's gold `$`).
     */
    val heroAccentText: Color = Color.Unspecified,
)

/** Effective redesign tokens for the current theme, provided by [AppTheme]. */
val LocalRedesignColors = staticCompositionLocalOf { LightRedesignColors }

/**
 * Resolves the Unspecified text-context slots to their per-mode defaults.
 * EVERY theme - light palettes AND dark - must pass through this exactly
 * once at construction: consumers read these slots unconditionally, so an
 * unresolved slot silently renders as inherited LocalContentColor (the dark
 * regression of Aug 2026: gold/amber text shipped as near-white because
 * resolution only ran on the light palettes).
 */
internal fun RedesignColors.resolved(): RedesignColors = copy(
    accentText = if (accentText == Color.Unspecified) accent else accentText,
    warningText = if (warningText == Color.Unspecified) warning else warningText,
    heroAccentText = if (heroAccentText == Color.Unspecified) accent else heroAccentText,
    dangerText = if (dangerText == Color.Unspecified) danger else dangerText,
)

/**
 * Dark mode - deep navy canvas, raised navy surfaces, gold accents. This is
 * the Obsidian Gold dark palette's token table (the DarkThemes.kt registry),
 * the shipped dark theme used for every light palette (product decision).
 * Text-slot
 * fallbacks resolve here too: hero eyebrow/`$` and menu selection render
 * GOLD (#C9A24B, 6.7/7.6 on the hero stops, 6.0-7.7 on surfaces), urgent
 * status text renders AMBER (#C58A1F, 5.5 surface / 6.2 canvas), delete-item
 * text uses the brightened [RedesignColors.dangerText], and fill glyphs use
 * ink instead of white (dark's lifted pastel fills drop white to 2.2-2.8:1).
 */
val DarkRedesignColors = RedesignColors(
    background = Color(0xFF0F141C),
    surface = Color(0xFF171F2B),
    surfaceRaised = Color(0xFF202A3A),
    hairline = Color(0xFF2A3A4D),
    hairlineSoft = Color(0xFF253242),
    textPrimary = Color(0xFFF1F3F7),
    textSecondary = Color(0xFF94A3B4),
    textTertiary = Color(0xFF64717F),
    accent = Color(0xFFC9A24B),
    accentSoft = Color(0x24C9A24B), // rgba(201,162,75,0.14)
    climate = Color(0xFF4C86D6),
    climateSoft = Color(0x244C86D6), // rgba(76,134,214,0.14)
    market = Color(0xFF8B6FE0),
    marketSoft = Color(0x248B6FE0), // rgba(139,111,224,0.14)
    heart = Color(0xFF3CAD74),
    heartSoft = Color(0x243CAD74), // rgba(60,173,116,0.14)
    ok = Color(0xFF6FBF8B),
    warning = Color(0xFFC58A1F),
    categoryFallback = Color(0xFF8A8578),
    categoryFallbackSoft = Color(0x338A8578),
    summaryStart = Color(0xFF2A2013),
    summaryEnd = Color(0xFF1B140C),
    summaryBorder = Color(0x4DC9A24B), // rgba(201,162,75,0.30)
    summaryHairline = Color(0x29C9A24B), // rgba(201,162,75,0.16)
    danger = Color(0xFFE5636B),
    dangerSoft = Color(0x1FE5636B), // rgba(229,99,107,0.12)
    dangerText = Color(0xFFEC7178), // brightened for surfaceRaised: 4.35 -> >=4.5 (verify in audit)
    onFill = Color(0xFF0F141B), // ink glyphs: ~6-7:1 on the lifted ok/heart/climate/market fills
).resolved()
