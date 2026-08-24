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
    val gold: Color,
    val goldSoft: Color,
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
     * and no product-approved dark variant exists yet.
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
    val summaryHairline: Color,
    val danger: Color,
    val dangerSoft: Color,
    /**
     * Accent color when used AS TEXT (menu selection, pins, add-row label).
     * Equals [gold] for palettes whose accent passes AA as body text (jade,
     * navy); darker variant for palettes where it does not (gold, bronze).
     * Light-mode slot; dark mode always uses its own accent directly.
     */
    val accentText: Color = Color.Unspecified,
    /**
     * Text color for the hero card (eyebrow + $ glyph). Defaults to
     * [textPrimary]; only palettes with a pale hero gradient override it.
     */
    val heroText: Color = Color.Unspecified,
    /**
     * On-accent content color (text/icons rendered on saturated accent fills,
     * e.g. the archived-count chip). Defaults to near-black ink.
     */
    val onAccent: Color = Color(0xFF17130A),
)

/** Effective redesign tokens for the current theme, provided by [AppTheme]. */
val LocalRedesignColors = staticCompositionLocalOf { LightRedesignColors }

/** Dark mode - deep navy canvas, raised navy surfaces, gold accents. */
val DarkRedesignColors = RedesignColors(
    background = Color(0xFF0F141C),
    surface = Color(0xFF171F2B),
    surfaceRaised = Color(0xFF202A3A),
    hairline = Color(0xFF2A3A4D),
    hairlineSoft = Color(0xFF253242),
    textPrimary = Color(0xFFF1F3F7),
    textSecondary = Color(0xFF94A3B4),
    textTertiary = Color(0xFF64717F),
    gold = Color(0xFFC9A24B),
    goldSoft = Color(0x24C9A24B), // rgba(201,162,75,0.14)
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
)
