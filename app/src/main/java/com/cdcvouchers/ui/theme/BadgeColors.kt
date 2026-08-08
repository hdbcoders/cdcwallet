package com.cdcvouchers.ui.theme

import androidx.compose.ui.graphics.Color

/** Container/content pair for a colored pill or badge. */
internal data class BadgeColors(val container: Color, val content: Color)

internal val AmberBadgeLight = BadgeColors(Color(0xFFFFE0B2), Color(0xFF8D4E00))
internal val AmberBadgeDark = BadgeColors(Color(0xFF4B3100), Color(0xFFFFD180))
internal val GreenBadgeLight = BadgeColors(Color(0xFFC8E6C9), Color(0xFF1B5E20))
internal val GreenBadgeDark = BadgeColors(Color(0xFF1E3B22), Color(0xFFA5D6A7))

// Category chip palettes (redesigned ticket card). The container is the
// mockup's `*-soft` alpha tint; the content is the category hue itself.
internal val HeartlandChipLight = BadgeColors(LightRedesignColors.heartSoft, LightRedesignColors.heart)
internal val HeartlandChipDark = BadgeColors(DarkRedesignColors.heartSoft, DarkRedesignColors.heart)
internal val SupermarketChipLight = BadgeColors(LightRedesignColors.marketSoft, LightRedesignColors.market)
internal val SupermarketChipDark = BadgeColors(DarkRedesignColors.marketSoft, DarkRedesignColors.market)
internal val ClimateChipLight = BadgeColors(LightRedesignColors.climateSoft, LightRedesignColors.climate)
internal val ClimateChipDark = BadgeColors(DarkRedesignColors.climateSoft, DarkRedesignColors.climate)

/** Temporary alias consumed by VoucherListScreen's summary until its Phase 3
 *  rewrite replaces the card with the mockup's BalanceHero. */
internal val SummaryCardContainerLight = LightRedesignColors.summaryStart
