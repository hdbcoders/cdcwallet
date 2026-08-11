package com.cdcwallet.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * App-level text size choice. The app multiplier is applied on top of the
 * system font scale at the theme root (see [AppTheme]), so every `sp` text
 * in the app scales together - including the hardcoded sizes in the
 * components - while `dp` layouts stay fixed. Max level is 1.75×; the
 * product (system × app) is additionally capped at [MAX_TOTAL_FONT_SCALE].
 */
enum class AppFontScale(val multiplier: Float) {
    DEFAULT(1f),
    LARGE(1.25f),
    EXTRA_LARGE(1.5f),
    HUGE(1.75f),
}

/**
 * Pure resolution of the persisted text-size choice. Unknown or missing
 * values fall back to the default (1×); the stored value is compared
 * case-insensitively so legacy/hand-edited values still resolve.
 */
internal fun resolveFontScale(stored: String?): AppFontScale =
    AppFontScale.entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
        ?: AppFontScale.DEFAULT

/**
 * Cap on the total effective font scale (system × app): text never renders
 * above 1.75×, whatever the system and app settings combine to. Product
 * decision - keeps the enlarged UI readable and consistent for elderly
 * users even when the phone's own system font is also set very large.
 */
const val MAX_TOTAL_FONT_SCALE = 1.75f

/**
 * Effective font scale applied by [AppTheme]: the system font scale times
 * the app's chosen multiplier, capped at [MAX_TOTAL_FONT_SCALE]. The app's
 * four levels stay distinct at the default system size; larger system font
 * sizes push the top levels into the cap.
 */
internal fun effectiveFontScale(systemFontScale: Float, appMultiplier: Float): Float =
    (systemFontScale * appMultiplier).coerceAtMost(MAX_TOTAL_FONT_SCALE)

/**
 * The app's chosen text-size level, provided by [AppTheme]. Plain
 * composition locals DO propagate into popup windows (DropdownMenu, Dialog),
 * so popup content reads this to re-apply the app scale - unlike
 * [LocalDensity], which popup windows shadow with the window's own density.
 */
val LocalAppFontScale = staticCompositionLocalOf { AppFontScale.DEFAULT }

/**
 * Re-applies the app font scale inside a popup/dialog window. Popup content
 * (DropdownMenu, Dialog) measures with the WINDOW's density - the system
 * font scale but not the app's chosen scale - so text there would ignore
 * the text-size setting (verified: kebab/hamburger menu items stay 1× at
 * app scale 2×). Wrapping popup content in this recomputes the effective
 * scale from the window density + [LocalAppFontScale].
 *
 * Only use inside popups: outside one, the composition's [LocalDensity]
 * already carries the app scale, and applying this again would double it.
 */
@Composable
fun AppScaledContent(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val appScale = LocalAppFontScale.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            base.density,
            effectiveFontScale(base.fontScale, appScale.multiplier),
        ),
        content = content,
    )
}

/**
 * Persists the text-size choice in SharedPreferences (same pattern as
 * ThemeModeStore / HeroCollapseStore). Held in Compose snapshot state so a
 * change recomposes the whole app instantly.
 *
 * The property's auto-generated setter is named `setScale` and would clash
 * with a same-named method, so the API is [setFontScale] (mirroring
 * ThemeModeStore's setThemeMode).
 */
class FontScaleStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var scale by mutableStateOf(AppFontScale.DEFAULT)
        private set

    init {
        scale = resolveFontScale(prefs.getString(KEY_SCALE, null))
    }

    fun setFontScale(newScale: AppFontScale) {
        scale = newScale
        prefs.edit().putString(KEY_SCALE, newScale.name.lowercase()).apply()
    }

    private companion object {
        const val PREFS_NAME = "voucher_font_prefs"
        const val KEY_SCALE = "font_scale"
    }
}
