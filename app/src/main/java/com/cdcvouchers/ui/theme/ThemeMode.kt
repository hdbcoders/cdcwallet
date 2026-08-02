package com.cdcvouchers.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** User-facing theme choice; SYSTEM is the default and matches today's behavior. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The app's *effective* dark state, as decided by AppTheme — never use
 * isSystemInDarkTheme() below AppTheme, since the user can force a mode that
 * differs from the system setting (which is what drives the system flag).
 */
val LocalAppIsDark = staticCompositionLocalOf { false }

/**
 * Persists the theme choice in SharedPreferences (same pattern as
 * SqlCipherPassphraseStore). The mode is held in Compose snapshot state so a
 * Settings change recomposes the whole app instantly.
 */
class ThemeModeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mode by mutableStateOf(load())
        private set

    private fun load(): ThemeMode = when (prefs.getString(KEY_MODE, null)) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }

    fun setThemeMode(newMode: ThemeMode) {
        mode = newMode
        prefs.edit().putString(KEY_MODE, newMode.name.lowercase()).apply()
    }

    private companion object {
        const val PREFS_NAME = "voucher_theme_prefs"
        const val KEY_MODE = "theme_mode"
    }
}

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalAppIsDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
            content = content,
        )
    }
}
