package com.cdcvouchers.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.cdcvouchers.R

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
            colorScheme = if (dark) darkColorScheme() else MistyBlueLightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

/** Roboto Flex, the app's typeface — clean, neutral, civic (see design notes). */
private val AppFontFamily = FontFamily(
    androidx.compose.ui.text.font.Font(
        R.font.roboto_flex,
        weight = FontWeight.Normal,
    ),
)

/**
 * App-wide typography based on Inter. Starts from the Material3 defaults and
 * swaps every role's font family to Inter, keeping all sizes/weights.
 */
private val AppTypography: Typography = with(Typography()) {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = AppFontFamily),
        displayMedium = displayMedium.copy(fontFamily = AppFontFamily),
        displaySmall = displaySmall.copy(fontFamily = AppFontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = AppFontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = AppFontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = AppFontFamily),
        titleLarge = titleLarge.copy(fontFamily = AppFontFamily),
        titleMedium = titleMedium.copy(fontFamily = AppFontFamily),
        titleSmall = titleSmall.copy(fontFamily = AppFontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = AppFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = AppFontFamily),
        bodySmall = bodySmall.copy(fontFamily = AppFontFamily),
        labelLarge = labelLarge.copy(fontFamily = AppFontFamily),
        labelMedium = labelMedium.copy(fontFamily = AppFontFamily),
        labelSmall = labelSmall.copy(fontFamily = AppFontFamily),
    )
}

/**
 * Misty Blue light theme — a calm, cool pale-blue-grey palette replacing the
 * default Material3 lavender cast. Background/surfaces are near-white with a
 * faint blue tint; primary is a muted steel blue that pairs with the app's
 * existing light-blue summary card (#D9E7FF).
 */
private val MistyBlueLightScheme = lightColorScheme(
    primary = Color(0xFF3D6B8E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E7F5),
    onPrimaryContainer = Color(0xFF12344C),
    secondary = Color(0xFF5A6B7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEE8F0),
    onSecondaryContainer = Color(0xFF17242E),
    tertiary = Color(0xFF6E5C8A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8DFF3),
    onTertiaryContainer = Color(0xFF271A3C),
    background = Color(0xFFF6F9FB),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF6F9FB),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEAF1F5),
    onSurfaceVariant = Color(0xFF3F4A52),
    surfaceTint = Color(0xFF3D6B8E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5F8),
    surfaceContainer = Color(0xFFECF1F5),
    surfaceContainerHigh = Color(0xFFE6ECF1),
    surfaceContainerHighest = Color(0xFFE0E7ED),
    outline = Color(0xFF7A848C),
    outlineVariant = Color(0xFFCBD7DE),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color.White,
    onErrorContainer = Color(0xFF410002),
)
