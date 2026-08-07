package com.cdcvouchers.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * User-facing language choice; SYSTEM is the default and matches today's
 * behavior (the app follows the device locale). Each explicit choice maps to
 * the [Locale] whose resource qualifiers (`values-*`) carry the translations.
 */
enum class AppLanguage(val locale: Locale?) {
    SYSTEM(null),
    EN(Locale.ENGLISH),
    // Simplified Chinese (the Singapore standard). The explicit zh-CN locale
    // resolves `values-zh-rCN`; a device set to zh-TW under "Follow system"
    // simply falls back to English rather than showing the wrong script.
    ZH(Locale("zh", "CN")),
    MS(Locale("ms")),
    TA(Locale("ta")),
}

/**
 * Persists the in-app language choice in SharedPreferences (same pattern as
 * ThemeModeStore). The choice is held in Compose snapshot state so the
 * Settings screen recomposes instantly; applying it app-wide happens in
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
        else -> AppLanguage.SYSTEM
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
