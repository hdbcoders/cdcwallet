package com.cdcvouchers.ui.theme

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
    val summaryStart: Color,
    val summaryEnd: Color,
    val summaryBorder: Color,
    val summaryHairline: Color,
    val danger: Color,
    val dangerSoft: Color,
)

/** Effective redesign tokens for the current theme, provided by [AppTheme]. */
val LocalRedesignColors = staticCompositionLocalOf { LightRedesignColors }

/** Light mode — cream canvas, white surfaces, gold accents. */
val LightRedesignColors = RedesignColors(
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
    summaryStart = Color(0xFFF3E3C4),
    summaryEnd = Color(0xFFECD7A9),
    summaryBorder = Color(0x59B07F27), // rgba(176,127,39,0.35)
    summaryHairline = Color(0x33B07F27), // rgba(176,127,39,0.20)
    danger = Color(0xFFC7373F),
    dangerSoft = Color(0x1AC7373F), // rgba(199,55,63,0.10)
)

/** Dark mode — deep navy canvas, raised navy surfaces, gold accents. */
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
    summaryStart = Color(0xFF2A2013),
    summaryEnd = Color(0xFF1B140C),
    summaryBorder = Color(0x4DC9A24B), // rgba(201,162,75,0.30)
    summaryHairline = Color(0x29C9A24B), // rgba(201,162,75,0.16)
    danger = Color(0xFFE5636B),
    dangerSoft = Color(0x1FE5636B), // rgba(229,99,107,0.12)
)
