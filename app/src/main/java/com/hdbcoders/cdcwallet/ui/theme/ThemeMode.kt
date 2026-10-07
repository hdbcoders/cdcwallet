@file:OptIn(ExperimentalTextApi::class)

package com.hdbcoders.cdcwallet.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.hdbcoders.cdcwallet.R

/**
 * User-facing theme choice. The app never tracks the system after the first
 * launch: the system default is baked into a concrete mode on first run (see
 * [resolveInitialMode]), and [ThemeModeStore.toggle] flips between the two
 * modes afterwards.
 */
enum class ThemeMode { LIGHT, DARK }

/**
 * The app's *effective* dark state, as decided by AppTheme - never use
 * isSystemInDarkTheme() below AppTheme, since the user can force a mode that
 * differs from the system setting (which is what drives the system flag).
 */
val LocalAppIsDark = staticCompositionLocalOf { false }

/**
 * Pure decision for the app's first concrete theme mode - the system default
 * is inherited exactly once. An explicit stored choice wins; otherwise (first
 * launch, or a legacy "system"/unknown value from before the SYSTEM option
 * was removed) the user's dark/light system setting is snapshotted.
 */
internal fun resolveInitialMode(isSystemDark: Boolean, stored: String?): ThemeMode =
    when (stored) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> if (isSystemDark) ThemeMode.DARK else ThemeMode.LIGHT
    }

/**
 * True when the stored value is not a concrete mode and must be rewritten to
 * the resolved one (refactor M22): first launch, the legacy "system" value,
 * AND any unknown/corrupted value (including case variants). Rewriting bakes
 * the system default exactly once - a later system-theme change must never
 * flip the app's mode.
 */
internal fun storedModeNeedsRewrite(stored: String?): Boolean =
    stored != "light" && stored != "dark"

/**
 * Persists the theme choice in SharedPreferences (same pattern as
 * SqlCipherPassphraseStore). The mode is held in Compose snapshot state so a
 * theme change recomposes the whole app instantly.
 */
class ThemeModeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mode by mutableStateOf(ThemeMode.LIGHT)
        private set

    /**
     * Light-mode palette choice (theme picker). Only meaningful when [mode]
     * is LIGHT; dark mode resolves through [darkPalette] instead.
     */
    var palette by mutableStateOf(defaultLightPalette())
        private set

    /**
     * Dark-theme palette choice (theme picker, Settings → Dark Themes). Only
     * meaningful when [mode] is DARK; light mode resolves through [palette]
     * instead. Unknown/corrupt stored values fall back to Midnight Gold, the
     * shipped default (same rule as [palette] → Cream).
     */
    var darkTheme by mutableStateOf(defaultDarkPalette())
        private set

    init {
        // First launch (or a legacy "Follow system" install, or any unknown
        // stored value - refactor M22): snapshot the system dark/light default
        // into a concrete persisted mode, then stop following the system - the
        // hamburger toggle is the only way to change it afterwards.
        val stored = prefs.getString(KEY_MODE, null)
        mode = resolveInitialMode(isSystemDark(context), stored)
        if (storedModeNeedsRewrite(stored)) {
            prefs.edit().putString(KEY_MODE, mode.name.lowercase()).apply()
        }
        palette = parseStoredPalette(prefs.getString(KEY_PALETTE, null))
        darkTheme = parseStoredDarkPalette(prefs.getString(KEY_DARK_PALETTE, null))
    }

    /** Flips LIGHT ↔ DARK. */
    fun toggle() = setThemeMode(if (mode == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT)

    fun setThemeMode(newMode: ThemeMode) {
        mode = newMode
        prefs.edit().putString(KEY_MODE, newMode.name.lowercase()).apply()
    }

    /** Persists and applies a light palette (Settings Appearance picker). */
    fun setLightPalette(newPalette: LightPalette) {
        palette = newPalette
        prefs.edit().putString(KEY_PALETTE, newPalette.name.lowercase()).apply()
    }

    /** Persists and applies a dark palette (Settings Dark Themes picker). */
    fun setDarkPalette(newPalette: DarkPalette) {
        darkTheme = newPalette
        prefs.edit().putString(KEY_DARK_PALETTE, newPalette.name.lowercase()).apply()
    }

    private fun isSystemDark(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private companion object {
        const val PREFS_NAME = "voucher_theme_prefs"
        const val KEY_MODE = "theme_mode"
        const val KEY_PALETTE = "light_palette"
        const val KEY_DARK_PALETTE = "dark_palette"
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun AppTheme(
    mode: ThemeMode,
    palette: LightPalette = defaultLightPalette(),
    darkPalette: DarkPalette = defaultDarkPalette(),
    fontScale: AppFontScale = AppFontScale.DEFAULT,
    dyslexiaFont: AppDyslexiaFont? = null,
    content: @Composable () -> Unit,
) {
    val dark = mode == ThemeMode.DARK
    val lightTheme = LightThemes.getValue(palette)
    val darkTheme = DarkThemes.getValue(darkPalette)
    val redesign = if (dark) darkTheme.colors else lightTheme.colors
    // App-level text size: every `sp` in the app (typography roles and the
    // hardcoded sizes in components) is scaled by the chosen multiplier on
    // top of the system font scale, capped at MAX_TOTAL_FONT_SCALE so
    // fixed-height chrome never clips at extreme compound scales. `dp`
    // layouts are untouched. The whole UI recomposes instantly when the
    // store's scale changes.
    val base = LocalDensity.current
    val scaled = Density(base.density, effectiveFontScale(base.fontScale, fontScale.multiplier))
    // Dyslexia-friendly typefaces: non-null swaps every text role (display,
    // body, mono) to the chosen font; null keeps the redesign's typefaces.
    val typefaces = if (dyslexiaFont == null) {
        DefaultTypefaces
    } else {
        val family = dyslexiaFontFamily(dyslexiaFont)
        AppTypefaces(display = family, body = family, mono = family)
    }
    CompositionLocalProvider(
        LocalDensity provides scaled,
        LocalAppFontScale provides fontScale,
        LocalAppTypefaces provides typefaces,
        LocalAppIsDark provides dark,
        LocalRedesignColors provides redesign,
        // M3's LocalContentColor defaults to Color.Black and MaterialTheme does
        // not provide it (only Surface does); this app's screens are custom
        // layouts with no root Surface, so implicit text colors would render
        // black - invisible in dark mode. Provide the theme's primary text
        // color at the root like a Surface would.
        LocalContentColor provides redesign.textPrimary,
    ) {
        MaterialTheme(
            colorScheme = if (dark) darkTheme.m3 else lightTheme.m3,
            typography = typographyFor(dyslexiaFont),
        ) {
            // Root semantics node carrying testTagsAsResourceId = true: the
            // whole app subtree publishes its Modifier.testTag as an
            // accessibility resource-id so UIAutomator / uiautomator dump /
            // external agent bridges can address nodes deterministically.
            // Pure metadata - no effect on layout, rendering, or behavior.
            Box(modifier = Modifier.semantics { testTagsAsResourceId = true }) {
                content()
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Typefaces                                                           */
/* ------------------------------------------------------------------ */

/**
 * Fraunces - the redesign's display serif (voucher names, hero amount,
 * section titles). Bundled as the Google Fonts variable font; the weight axis
 * is pinned per [FontWeight] via [FontVariation] so API 26+ renders true
 * weights while API 24/25 fall back to the default instance.
 */
private val FrauncesFontFamily = FontFamily(
    Font(R.font.fraunces_variable, weight = FontWeight.Normal),
    Font(
        R.font.fraunces_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.fraunces_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

/**
 * Fraunces at the display optical size (opsz 42) - for large display text
 * like the hero balance. Browsers apply font-optical-sizing automatically
 * (opsz ≈ rendered size); Android does not, and the font's fvar default is
 * opsz=9 (the text cut), so large text needs the axis pinned explicitly to
 * render like the mockup. Keep the base family at the text cut for small
 * type; use this family only at display sizes.
 */
internal val FrauncesDisplayFontFamily = FontFamily(
    Font(
        R.font.fraunces_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.Setting("opsz", 42f),
        ),
    ),
    Font(
        R.font.fraunces_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.Setting("opsz", 42f),
        ),
    ),
    Font(
        R.font.fraunces_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
            FontVariation.Setting("opsz", 42f),
        ),
    ),
)

/**
 * Inter - the redesign's body typeface (labels, meta, banners). Same variable
 * font handling as Fraunces.
 */
private val InterFontFamily = FontFamily(
    Font(R.font.inter_variable, weight = FontWeight.Normal),
    Font(
        R.font.inter_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/**
 * IBM Plex Mono - the redesign's numeric/mono typeface (balances, counts,
 * eyebrow labels, language button). Static weights bundled.
 */
internal val PlexMonoFontFamily = FontFamily(
    Font(R.font.ibmplexmono_regular, weight = FontWeight.Normal),
    Font(R.font.ibmplexmono_medium, weight = FontWeight.Medium),
    Font(R.font.ibmplexmono_semibold, weight = FontWeight.SemiBold),
    Font(R.font.ibmplexmono_bold, weight = FontWeight.Bold),
)

/**
 * OpenDyslexic - the classic dyslexia typeface (heavy weighted bottoms).
 * Static Regular/Bold weights bundled; OFL 1.1 (assets/licenses/OFL-OpenDyslexic.txt).
 * Latin-script only - non-Latin glyphs fall back to the system font.
 */
internal val OpenDyslexicFontFamily = FontFamily(
    Font(R.font.opendyslexic_regular, weight = FontWeight.Normal),
    Font(R.font.opendyslexic_bold, weight = FontWeight.Bold),
)

/**
 * Atkinson Hyperlegible - Braille Institute's legibility typeface. Static
 * Regular/Bold weights bundled; OFL 1.1 (assets/licenses/OFL-AtkinsonHyperlegible.txt).
 * Latin-script only - non-Latin glyphs fall back to the system font.
 */
internal val AtkinsonFontFamily = FontFamily(
    Font(R.font.atkinson_regular, weight = FontWeight.Normal),
    Font(R.font.atkinson_bold, weight = FontWeight.Bold),
)

/**
 * The three font roles the redesign's screens use directly (outside the
 * typography roles): [display] (hero amounts, ticket titles), [body], and
 * [mono] (balances, counts, the header pill). Components read
 * [LocalAppTypefaces] instead of hardcoding families, so the dyslexia-font
 * option swaps every text surface at once.
 */
data class AppTypefaces(
    val display: FontFamily,
    val body: FontFamily,
    val mono: FontFamily,
)

/** The redesign's typefaces - the DEFAULT (dyslexia option off) set. */
private val DefaultTypefaces = AppTypefaces(
    display = FrauncesDisplayFontFamily,
    body = InterFontFamily,
    mono = PlexMonoFontFamily,
)

/**
 * The app's active font roles, provided by [AppTheme]. Plain composition
 * locals propagate into popup windows (DropdownMenu, Dialog), so popup
 * content reads the swapped families too - same mechanism as
 * [LocalAppFontScale].
 */
val LocalAppTypefaces = staticCompositionLocalOf { DefaultTypefaces }

/** The bundled family for a dyslexia-font choice. */
private fun dyslexiaFontFamily(font: AppDyslexiaFont): FontFamily = when (font) {
    AppDyslexiaFont.ATKINSON -> AtkinsonFontFamily
    AppDyslexiaFont.OPEN_DYSLEXIC -> OpenDyslexicFontFamily
}

/**
 * App-wide typography following the redesign: Fraunces for display/headline
 * (and title roles used for voucher names / banner titles), Inter for body
 * and labels. Numeric amounts inside cards use [PlexMonoFontFamily] explicitly
 * at their call sites (see BalanceHero / TicketCard), matching the mockup.
 */
private val AppTypography: Typography = with(Typography()) {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = FrauncesFontFamily, fontWeight = FontWeight.Medium),
        displayMedium = displayMedium.copy(fontFamily = FrauncesFontFamily, fontWeight = FontWeight.Medium),
        displaySmall = displaySmall.copy(fontFamily = FrauncesFontFamily, fontWeight = FontWeight.Medium),
        headlineLarge = headlineLarge.copy(fontFamily = FrauncesFontFamily, fontWeight = FontWeight.Medium),
        headlineMedium = headlineMedium.copy(fontFamily = FrauncesFontFamily, fontWeight = FontWeight.Medium),
        headlineSmall = headlineSmall.copy(fontFamily = FrauncesFontFamily, fontWeight = FontWeight.Medium),
        titleLarge = titleLarge.copy(fontFamily = FrauncesFontFamily, fontWeight = FontWeight.Medium),
        titleMedium = titleMedium.copy(fontFamily = FrauncesFontFamily, fontWeight = FontWeight.Medium),
        titleSmall = titleSmall.copy(fontFamily = FrauncesFontFamily, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontFamily = InterFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = InterFontFamily),
        bodySmall = bodySmall.copy(fontFamily = InterFontFamily),
        labelLarge = labelLarge.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium),
    )
}

/**
 * The active typography: the redesign's [AppTypography] when the dyslexia
 * option is off; the same role sizes/weights with every role mapped to the
 * chosen dyslexia family when on. Weights the bundled fonts lack (Medium,
 * SemiBold) are filled by Compose's default font synthesis.
 */
private fun typographyFor(font: AppDyslexiaFont?): Typography {
    if (font == null) return AppTypography
    val family = dyslexiaFontFamily(font)
    return with(AppTypography) {
        Typography(
            displayLarge = displayLarge.copy(fontFamily = family),
            displayMedium = displayMedium.copy(fontFamily = family),
            displaySmall = displaySmall.copy(fontFamily = family),
            headlineLarge = headlineLarge.copy(fontFamily = family),
            headlineMedium = headlineMedium.copy(fontFamily = family),
            headlineSmall = headlineSmall.copy(fontFamily = family),
            titleLarge = titleLarge.copy(fontFamily = family),
            titleMedium = titleMedium.copy(fontFamily = family),
            titleSmall = titleSmall.copy(fontFamily = family),
            bodyLarge = bodyLarge.copy(fontFamily = family),
            bodyMedium = bodyMedium.copy(fontFamily = family),
            bodySmall = bodySmall.copy(fontFamily = family),
            labelLarge = labelLarge.copy(fontFamily = family),
            labelMedium = labelMedium.copy(fontFamily = family),
            labelSmall = labelSmall.copy(fontFamily = family),
        )
    }
}

/* ------------------------------------------------------------------ */
/* Color schemes                                                       */
/* ------------------------------------------------------------------ */
/* The light M3 schemes live in LightThemes.kt (one derivation per       */
/* palette); the dark M3 scheme moved into DarkThemes.kt as the          */
 /* Midnight Gold entry (registry). AppTheme resolves both via the         */
/* registries.                                                          */
