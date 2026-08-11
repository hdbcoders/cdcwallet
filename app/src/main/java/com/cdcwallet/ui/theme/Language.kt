package com.cdcwallet.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/**
 * The app's four user-facing languages. There is no "follow system" choice:
 * the initial default is derived from the device locale on first launch (see
 * [LanguageStore]), and the user's explicit pick thereafter wins. Each value
 * maps to the [Locale] whose resource qualifiers (`values-*`) carry the
 * translations.
 */
enum class AppLanguage(val locale: Locale) {
    EN(Locale.ENGLISH),
    // Simplified Chinese (the Singapore standard). The explicit zh-CN locale
    // resolves `values-zh-rCN`. Any system Chinese region (including zh-TW)
    // maps to ZH — Simplified Chinese is the only Chinese variant the app
    // offers, so a zh-TW device gets the simplified script rather than English.
    ZH(Locale("zh", "CN")),
    MS(Locale("ms")),
    TA(Locale("ta")),
}

/**
 * The currently active app language, provided by [AppTheme] from the
 * [LanguageStore]. Composable display-time localizers (e.g.
 * [localizeCategory]) read this instead of `LocalConfiguration`, because the
 * activity wraps its resources via `attachBaseContext` — the composition
 * local is the explicit, reliable source.
 */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.EN }

/**
 * Persists the in-app language choice in SharedPreferences (same pattern as
 * ThemeModeStore). The choice is held in Compose snapshot state so the
 * language picker recomposes instantly; applying it app-wide happens in
 * MainActivity.attachBaseContext via [wrapWithLocale].
 */
class LanguageStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language by mutableStateOf(load())
        private set

    private fun load(): AppLanguage = when (prefs.getString(KEY_LANGUAGE, null)) {
        "en" -> AppLanguage.EN
        "zh" -> AppLanguage.ZH
        "ms" -> AppLanguage.MS
        "ta" -> AppLanguage.TA
        // No explicit choice yet: default to the system language when it is
        // one of the four the app ships, otherwise English.
        else -> defaultFromSystem()
    }

    /** First-launch default: the system language if it is one of the four
     *  app languages (any `zh` region → Simplified Chinese), else English. */
    private fun defaultFromSystem(): AppLanguage = when (Locale.getDefault().language) {
        "zh" -> AppLanguage.ZH
        "ms" -> AppLanguage.MS
        "ta" -> AppLanguage.TA
        else -> AppLanguage.EN
    }

    fun setAppLanguage(newLanguage: AppLanguage) {
        language = newLanguage
        prefs.edit().putString(KEY_LANGUAGE, newLanguage.name.lowercase()).apply()
    }

    private companion object {
        const val PREFS_NAME = "voucher_language_prefs"
        const val KEY_LANGUAGE = "app_language"
    }
}

/**
 * Wraps [base] so its resources resolve in [locale] — the manual per-app
 * locale mechanism for this single-activity app (no appcompat dependency,
 * which would force an AppCompat theme conversion).
 */
fun Context.wrapWithLocale(locale: Locale): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}
