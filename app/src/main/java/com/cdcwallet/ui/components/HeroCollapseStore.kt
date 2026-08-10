package com.cdcwallet.ui.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Persists whether the list's balance hero is collapsed (SharedPreferences,
 * same pattern as ThemeModeStore). Held in Compose snapshot state so a toggle
 * recomposes the hero instantly; the choice survives app restarts. Default is
 * expanded (false).
 */
class HeroCollapseStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var collapsed by mutableStateOf(prefs.getBoolean(KEY_COLLAPSED, false))
        private set

    fun toggle() {
        collapsed = !collapsed
        prefs.edit().putBoolean(KEY_COLLAPSED, collapsed).apply()
    }

    private companion object {
        const val PREFS_NAME = "voucher_ui_prefs"
        const val KEY_COLLAPSED = "hero_collapsed"
    }
}
