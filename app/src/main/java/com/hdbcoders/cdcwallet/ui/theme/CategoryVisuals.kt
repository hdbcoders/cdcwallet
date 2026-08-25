package com.hdbcoders.cdcwallet.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Visual identity for a category: its hue, the soft tint used as chip/badge
 * container, and the icon shown on the tinted square. Matching is
 * case-insensitive (extracted names vary). Unknown categories fall back to a
 * neutral star on the [RedesignColors.categoryFallback] tokens (refactor L7).
 *
 * Colors come from [LocalRedesignColors] - i.e. the ACTIVE light palette -
 * so each palette's retuned category hues actually render (they were pinned
 * to CREAM via the global alias before).
 */
data class CategoryVisuals(
    val color: Color,
    val soft: Color,
    val icon: ImageVector,
)

@Composable
internal fun categoryVisuals(category: String): CategoryVisuals {
    val r = LocalRedesignColors.current
    return when (category.trim().lowercase()) {
        "climate" -> CategoryVisuals(r.climate, r.climateSoft, Icons.Outlined.WaterDrop)
        "heartland" -> CategoryVisuals(r.heart, r.heartSoft, Icons.Outlined.Storefront)
        "supermarket" -> CategoryVisuals(r.market, r.marketSoft, Icons.Outlined.ShoppingCart)
        else -> CategoryVisuals(
            r.categoryFallback,
            r.categoryFallbackSoft,
            Icons.Outlined.Star,
        )
    }
}
