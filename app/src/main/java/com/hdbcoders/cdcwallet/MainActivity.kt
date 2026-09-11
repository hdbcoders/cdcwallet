package com.hdbcoders.cdcwallet

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.hdbcoders.cdcwallet.addflow.AddVoucherFlow
import com.hdbcoders.cdcwallet.ui.theme.AppLanguage
import com.hdbcoders.cdcwallet.ui.theme.AppTheme
import com.hdbcoders.cdcwallet.ui.theme.LocalAppLanguage
import com.hdbcoders.cdcwallet.ui.theme.wrapWithLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app's single Activity: lifecycle, intent handling, theme bootstrapping
 * and the language wrap. The navigation graph itself lives in [AppNavHost]
 * (H2: god-file split) so this file stays about the Activity.
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as VoucherApp).container

    private val flow: AddVoucherFlow by lazy {
        // Debug-seam opt-in (instrumented tests): a share intent that sets
        // [EXTRA_DEV_FIXTURE_ADD] routes the add through the debug variant's
        // fixture flow (synthetic appassets host, never a real RedeemSG host).
        // Production/release builds ignore the extra entirely (the application
        // class returns null there), so this branch is dead in release.
        val debugAppSeam = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            intent?.getBooleanExtra(EXTRA_DEV_FIXTURE_ADD, false) == true
        val flowFromSeam = if (debugAppSeam) {
            (application as VoucherApp).devFixtureAddFlow(container.repository)
        } else {
            null
        }
        flowFromSeam ?: AddVoucherFlow(
            repository = container.repository,
            extractionEngine = container.extractionEngine,
        )
    }

    companion object {
        /**
         * Debug-only intent extra (see [flow]): false/absent in production,
         * and ignored entirely by non-debuggable builds. Kept as a plain
         * constant so the (debug-gated) instrumented tests can reference it.
         */
        const val EXTRA_DEV_FIXTURE_ADD = "dev_fixture_add"
    }

    private var navControllerRef: NavController? = null

    /** A share URL that arrived while navigation was not ready yet (refactor
     *  M7): queued instead of dropped, consumed by the NavHost when it exists. */
    private var pendingShareUrl by mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        // In-app language (LanguageStore): wrap the base context so resource
        // resolution uses the app language before the activity is created.
        // The store always resolves to one of the four concrete languages
        // (first launch: the system language when it is one of the four,
        // otherwise English - see LanguageStore), so the wrap is
        // unconditional. Note: getApplication() is null here (Activity.attach
        // assigns it after attachBaseContext), so the app is read from the
        // base context.
        val app = newBase.applicationContext as? VoucherApp
        val language = app?.container?.languageStore?.language
        super.attachBaseContext(newBase.wrapWithLocale(language?.locale ?: AppLanguage.EN.locale))
    }

    /** Persists a language change and rebuilds the activity so the new
     *  locale's resources take effect app-wide. */
    fun setAppLanguage(language: AppLanguage) {
        container.languageStore.setAppLanguage(language)
        recreate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Refactor M7: accept only genuine text shares - everything else is
        // ignored (no URL, no navigation).
        if (intent.action != Intent.ACTION_SEND) return
        if (intent.type != "text/plain") return
        val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (sharedUrl.isNullOrBlank()) return
        // Warm share into an already-running app (singleTask): route to the
        // add screen with the link, mirroring the cold-start path below. If
        // navigation is not ready yet (startup race), queue the URL instead
        // of dropping it - the NavHost consumes the queue when it exists.
        setIntent(intent)
        val nav = navControllerRef
        if (nav == null) {
            pendingShareUrl = sharedUrl
        } else {
            nav.navigate("add?url=${Uri.encode(sharedUrl)}") { launchSingleTop = true }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // API 31+: install the OS splash and keep it on screen until the
        // database bootstrap settles (refactor M2): readiness now keys on the
        // IO-backed bootstrap, not on the first DB read from the main thread.
        // A Failed state also releases the splash - the UI then shows the
        // fatal error screen instead of hanging.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ready = java.util.concurrent.atomic.AtomicBoolean(false)
            installSplashScreen().setKeepOnScreenCondition { !ready.get() }
            lifecycleScope.launch {
                container.databaseBootstrap.state
                    .first { it !is DatabaseBootstrapState.Initializing }
                ready.set(true)
            }
        }
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // REQ-13: silent update check when the app opens - one Play query per
        // 24h (UpdateChecker's own throttle), flag-only, never shows UI. Runs
        // off the main thread (Play binder call via Tasks.await).
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                container.updateChecker.silentCheckIfDue()
            }
        }
        val sharedUrl = intent?.getStringExtra(Intent.EXTRA_TEXT)
        setContent {
            // AppTheme publishes every Modifier.testTag as an accessibility
            // resource-id for the whole tree (see ThemeMode.kt), so UIAutomator
            // / uiautomator dump / external agent bridges can address nodes
            // deterministically (AGENTS.md: navigate via UIAutomator,
            // screenshots last resort).
            AppTheme(
                container.themeModeStore.mode,
                palette = container.themeModeStore.palette,
                darkPalette = container.themeModeStore.darkTheme,
                fontScale = container.fontScaleStore.scale,
                dyslexiaFont = container.dyslexiaFontStore.let { if (it.enabled) it.font else null },
            ) {
                CompositionLocalProvider(
                    LocalAppLanguage provides container.languageStore.language,
                ) {
                // Keep the Android window background in sync with the effective
                // Compose theme. The XML theme's white window background would
                // otherwise flash through during pop transitions (both screens
                // are mid-fade, so neither covers the window) - and in dark
                // mode it would flash white on a dark surface.
                val window = this@MainActivity.window
                val bg = MaterialTheme.colorScheme.background
                SideEffect {
                    window.setBackgroundDrawable(ColorDrawable(bg.toArgb()))
                }
                val navController = rememberNavController()
                DisposableEffect(navController) {
                    navControllerRef = navController
                    onDispose { navControllerRef = null }
                }
                AppNavHost(
                    container = container,
                    flowProvider = { flow },
                    sharedUrl = sharedUrl,
                    isColdStart = savedInstanceState == null,
                    navController = navController,
                    fontScaleStore = container.fontScaleStore,
                    dyslexiaFontStore = container.dyslexiaFontStore,
                    onLanguageSelected = { language -> setAppLanguage(language) },
                    pendingShareUrl = pendingShareUrl,
                    onPendingShareUrlConsumed = { pendingShareUrl = null },
                )
                }
            }
        }
    }
}
