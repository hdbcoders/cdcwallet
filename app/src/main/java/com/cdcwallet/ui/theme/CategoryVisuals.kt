package com.cdcwallet.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Visual identity for a category: its hue, the soft tint used as chip/badge
 * container, and the icon shown on the tinted square. Matching is
 * case-insensitive (extracted names vary). Unknown categories fall back to a
 * neutral star. Uses [LocalRedesignColors] tokens so both themes agree.
 */
data class CategoryVisuals(
    val color: Color,
    val soft: Color,
    val icon: ImageVector,
)

internal fun categoryVisuals(category: String, dark: Boolean): CategoryVisuals {
    val r = if (dark) DarkRedesignColors else LightRedesignColors
    return when (category.trim().lowercase()) {
        "climate" -> CategoryVisuals(r.climate, r.climateSoft, Icons.Outlined.WaterDrop)
        "heartland" -> CategoryVisuals(r.heart, r.heartSoft, Icons.Outlined.Storefront)
        "supermarket" -> CategoryVisuals(r.market, r.marketSoft, Icons.Outlined.ShoppingCart)
        else -> CategoryVisuals(
            Color(0xFF8A8578),
            Color(0x338A8578),
            Icons.Outlined.Star,
        )
    }
}
