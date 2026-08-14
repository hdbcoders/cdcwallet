package com.cdcwallet.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The two dyslexia-friendly typefaces the app bundles (both SIL OFL 1.1,
 * see `licenses/`). Both are Latin-script fonts: non-Latin glyphs (中文 /
 * தமிழ் UI text) fall back to the system font per-glyph automatically.
 */
enum class AppDyslexiaFont {
    ATKINSON,
    OPEN_DYSLEXIC,
}

/**
 * Pure resolution of the persisted dyslexia-font choice. Unknown or missing
 * values fall back to [AppDyslexiaFont.ATKINSON] - the product default when
 * the dyslexia-friendly option is enabled.
 */
internal fun resolveDyslexiaFont(stored: String?): AppDyslexiaFont =
    AppDyslexiaFont.entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
        ?: AppDyslexiaFont.ATKINSON

/**
 * Persists the dyslexia-friendly font preference in SharedPreferences (same
 * pattern as FontScaleStore / ThemeModeStore). Held in Compose snapshot state
 * so a change recomposes the whole app instantly.
 *
 * The feature is OFF by default (no stored value -> off, so existing installs
 * are unaffected). Turning it ON always starts at [AppDyslexiaFont.ATKINSON]
 * (the default choice); turning it OFF reverts the app to its normal
 * typefaces. While ON, [setFont] switches between the two bundled fonts.
 */
class DyslexiaFontStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Whether the dyslexia-friendly font option is on. */
    var enabled by mutableStateOf(false)
        private set

    /** The chosen font - meaningful only while [enabled]; defaults to [AppDyslexiaFont.ATKINSON]. */
    var font by mutableStateOf(AppDyslexiaFont.ATKINSON)
        private set

    init {
        enabled = prefs.getBoolean(KEY_ENABLED, false)
        font = resolveDyslexiaFont(prefs.getString(KEY_FONT, null))
    }

    /**
     * Turns the dyslexia-friendly option on/off. Enabling always starts at
     * [AppDyslexiaFont.ATKINSON] (the default choice); disabling reverts the
     * app to its normal typefaces. The method is named [setDyslexiaFontEnabled]
     * because the `enabled` property's auto-generated setter would clash with
     * a same-named method (same pattern as FontScaleStore.setFontScale).
     */
    fun setDyslexiaFontEnabled(newEnabled: Boolean) {
        enabled = newEnabled
        prefs.edit().putBoolean(KEY_ENABLED, newEnabled).apply()
        if (newEnabled) {
            // Every enable starts at the default choice (Atkinson).
            font = AppDyslexiaFont.ATKINSON
            prefs.edit().putString(KEY_FONT, AppDyslexiaFont.ATKINSON.name.lowercase()).apply()
        }
    }

    /** Switches between the two bundled fonts while the option is on. */
    fun setChosenFont(newFont: AppDyslexiaFont) {
        font = newFont
        prefs.edit().putString(KEY_FONT, newFont.name.lowercase()).apply()
    }

    private companion object {
        const val PREFS_NAME = "voucher_dyslexia_font_prefs"
        const val KEY_ENABLED = "dyslexia_font_enabled"
        const val KEY_FONT = "dyslexia_font"
    }
}
