package com.cdcvouchers.ui.theme

import androidx.compose.ui.graphics.Color

internal data class BadgeColors(val container: Color, val content: Color)

internal val AmberBadgeLight = BadgeColors(Color(0xFFFFE0B2), Color(0xFF8D4E00))
internal val AmberBadgeDark = BadgeColors(Color(0xFF4B3100), Color(0xFFFFD180))
internal val GreenBadgeLight = BadgeColors(Color(0xFFC8E6C9), Color(0xFF1B5E20))
internal val GreenBadgeDark = BadgeColors(Color(0xFF1E3B22), Color(0xFFA5D6A7))

/** Flat informational banner container used by the list summary card. */
internal val SummaryCardContainerLight = Color(0xFFD9E7FF)
