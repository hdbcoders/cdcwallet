package com.cdcwallet

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.cdcwallet.addflow.AddVoucherFlow
import com.cdcwallet.ui.add.AddVoucherScreen
import com.cdcwallet.ui.detail.VoucherWebViewScreen
import com.cdcwallet.ui.list.ArchivedVoucherScreen
import com.cdcwallet.ui.list.SplashScreen
import com.cdcwallet.ui.list.VoucherListScreen
import com.cdcwallet.ui.settings.AboutScreen
import com.cdcwallet.ui.settings.SettingsScreen
import com.cdcwallet.ui.theme.AppLanguage
import com.cdcwallet.ui.theme.AppTheme
import com.cdcwallet.ui.theme.FontScaleStore
import com.cdcwallet.ui.theme.rememberReduceMotion
import com.cdcwallet.ui.theme.wrapWithLocale

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as VoucherApp).container

    private val flow: AddVoucherFlow by lazy {
        AddVoucherFlow(
            repository = container.repository,
            extractionEngine = container.extractionEngine,
        )
    }

    private var navControllerRef: NavController? = null

    override fun attachBaseContext(newBase: Context) {
        // In-app language (LanguageStore): wrap the base context so resource
        // resolution uses the app language before the activity is created.
        // The store always resolves to one of the four concrete languages
        // (first launch: the system language when it is one of the four,
        // otherwise English — see LanguageStore), so the wrap is
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
        val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!sharedUrl.isNullOrBlank()) {
            // Warm share into an already-running app (singleTask): route to the
            // add screen with the link, mirroring the cold-start path below.
            setIntent(intent)
            navControllerRef?.navigate("add?url=${Uri.encode(sharedUrl)}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // API 31+: install the OS splash and keep it on screen until the
        // list's first DB read completes — the user never lands on an empty
        // list, and the splash doubles as the load mask (no Compose splash
        // needed on this path).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ready = java.util.concurrent.atomic.AtomicBoolean(false)
            installSplashScreen().setKeepOnScreenCondition { !ready.get() }
            lifecycleScope.launch {
                container.repository.observeActive().first()
                ready.set(true)
            }
        }
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val sharedUrl = intent?.getStringExtra(Intent.EXTRA_TEXT)
        setContent {
            AppTheme(container.themeModeStore.mode, container.fontScaleStore.scale) {
                // Keep the Android window background in sync with the effective
                // Compose theme. The XML theme's white window background would
                // otherwise flash through during pop transitions (both screens
                // are mid-fade, so neither covers the window) — and in dark
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
                    flow = flow,
                    sharedUrl = sharedUrl,
                    isColdStart = savedInstanceState == null,
                    navController = navController,
                    fontScaleStore = container.fontScaleStore,
                    onLanguageSelected = { language -> setAppLanguage(language) },
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(
    container: AppContainer,
    flow: AddVoucherFlow,
    sharedUrl: String?,
    isColdStart: Boolean,
    navController: NavHostController,
    fontScaleStore: FontScaleStore,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    if (isColdStart && sharedUrl != null) {
        LaunchedEffect(Unit) {
            navController.navigate("add?url=${Uri.encode(sharedUrl)}")
        }
    }

    val reduceMotion = rememberReduceMotion()

    // One-shot: does the list's first DB load still need masking? Scoped here
    // (above the NavHost) so it survives back navigation and rotation but
    // resets on a true process death — the splash masks only the initial
    // load, never a return to the list (the back-arrow bug).
    var listLoadedOnce by rememberSaveable { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = "list",
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
    ) {
        composable("list") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+: the OS system splash (kept on screen by
                // setKeepOnScreenCondition until the first DB read) masks the
                // load — render the list directly, never an empty list.
                VoucherListScreen(
                    repository = container.repository,
                    extractionCoordinator = container.extractionCoordinator,
                    onAddClick = { navController.navigate("add") },
                    onOpenVoucher = { voucher ->
                        navController.navigate("detail/${voucher.id}?url=${Uri.encode(voucher.url)}")
                    },
                    onArchivedClick = { navController.navigate("archived") },
                    onSettingsClick = { navController.navigate("settings") },
                    onAboutClick = { navController.navigate("about") },
                    onToggleTheme = { container.themeModeStore.toggle() },
                    heroCollapsed = container.heroCollapseStore.collapsed,
                    onToggleHeroCollapsed = { container.heroCollapseStore.toggle() },
                    languageStore = container.languageStore,
                    onLanguageSelected = onLanguageSelected,
                )
            } else {
                // API < 31: no system splash, so the Compose splash masks the
                // initial load — hold the logo until the first DB read
                // completes, then crossfade into the list. Uses the
                // repository flow directly (no ViewModel) — the screen below
                // owns its own ViewModel. `listLoadedOnce` (rememberSaveable
                // at AppNavHost scope) latches true after the initial load so
                // returning to the list via back never re-shows the splash.
                val loaded by produceState(initialValue = false) {
                    container.repository.observeActive().first()
                    value = true
                }
                val minHoldElapsed by produceState(initialValue = false) {
                    delay(SPLASH_MIN_MS)
                    value = true
                }
                LaunchedEffect(loaded, minHoldElapsed) {
                    if (loaded && minHoldElapsed) listLoadedOnce = true
                }
                Crossfade(
                    targetState = listLoadedOnce,
                    animationSpec = tween(220),
                    label = "splash-to-list",
                ) { ready ->
                    if (ready) {
                        VoucherListScreen(
                            repository = container.repository,
                            extractionCoordinator = container.extractionCoordinator,
                            onAddClick = { navController.navigate("add") },
                            onOpenVoucher = { voucher ->
                                navController.navigate("detail/${voucher.id}?url=${Uri.encode(voucher.url)}")
                            },
                            onArchivedClick = { navController.navigate("archived") },
                            onSettingsClick = { navController.navigate("settings") },
                    onAboutClick = { navController.navigate("about") },
                            onToggleTheme = { container.themeModeStore.toggle() },
                            heroCollapsed = container.heroCollapseStore.collapsed,
                            onToggleHeroCollapsed = { container.heroCollapseStore.toggle() },
                            languageStore = container.languageStore,
                            onLanguageSelected = onLanguageSelected,
                        )
                    } else {
                        SplashScreen()
                    }
                }
            }
        }
        composable(
            route = "archived",
            enterTransition = { layerEnter(reduceMotion) },
            exitTransition = { layerExit(reduceMotion) },
            popEnterTransition = { layerPopEnter(reduceMotion) },
            popExitTransition = { layerPopExit(reduceMotion) },
        ) {
            ArchivedVoucherScreen(
                repository = container.repository,
                extractionCoordinator = container.extractionCoordinator,
                onOpenVoucher = { voucher ->
                    navController.navigate("detail/${voucher.id}?url=${Uri.encode(voucher.url)}")
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "settings",
            enterTransition = { layerEnter(reduceMotion) },
            exitTransition = { layerExit(reduceMotion) },
            popEnterTransition = { layerPopEnter(reduceMotion) },
            popExitTransition = { layerPopExit(reduceMotion) },
        ) {
            SettingsScreen(
                backupFlow = container.backupFlow,
                repository = container.repository,
                fontScaleStore = fontScaleStore,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "about",
            enterTransition = { layerEnter(reduceMotion) },
            exitTransition = { layerExit(reduceMotion) },
            popEnterTransition = { layerPopEnter(reduceMotion) },
            popExitTransition = { layerPopExit(reduceMotion) },
        ) {
            AboutScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "add?url={url}",
            arguments = listOf(
                navArgument("url") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            enterTransition = { drillInEnter(reduceMotion) },
            exitTransition = { drillInExit(reduceMotion) },
            popEnterTransition = { drillInPopEnter(reduceMotion) },
            popExitTransition = { drillInPopExit(reduceMotion) },
        ) { entry ->
            AddVoucherScreen(
                flow = flow,
                initialUrl = entry.arguments?.getString("url"),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "detail/{voucherId}?url={url}",
            arguments = listOf(
                navArgument("voucherId") { type = NavType.StringType },
                navArgument("url") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            enterTransition = { drillInEnter(reduceMotion) },
            exitTransition = { drillInExit(reduceMotion) },
            popEnterTransition = { drillInPopEnter(reduceMotion) },
            popExitTransition = { drillInPopExit(reduceMotion) },
        ) { entry ->
            VoucherWebViewScreen(
                voucherId = entry.arguments?.getString("voucherId").orEmpty(),
                voucherUrl = entry.arguments?.getString("url").orEmpty(),
                repository = container.repository,
                extractionEngine = container.extractionEngine,
                extractionCoordinator = container.extractionCoordinator,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Navigation motion duration — short enough to feel snappy, long enough to read. */
private const val NAV_TRANSITION_MS = 280

/**
 * Minimum splash hold before the list appears, so the splash logo is actually
 * visible: on API 31+ the OS system-splash window covers cold startup
 * (~1.4s), so without this floor the Compose splash is never revealed.
 */
private const val SPLASH_MIN_MS = 1_500L

/**
 * Drill-in motion for push/pop navigation (list → detail / add): the incoming
 * screen slides in from the right, the outgoing one slides out to the left.
 * Reverse for pop. All specs collapse to [EnterTransition.None] /
 * [ExitTransition.None] when the user has system reduce-motion enabled.
 */
private fun drillInEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) EnterTransition.None
    else slideInHorizontally(tween(NAV_TRANSITION_MS)) { it } + fadeIn(tween(NAV_TRANSITION_MS))

private fun drillInExit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) ExitTransition.None
    else slideOutHorizontally(tween(NAV_TRANSITION_MS)) { -it / 3 } + fadeOut(tween(NAV_TRANSITION_MS))

private fun drillInPopEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) EnterTransition.None
    else slideInHorizontally(tween(NAV_TRANSITION_MS)) { -it / 3 } + fadeIn(tween(NAV_TRANSITION_MS))

private fun drillInPopExit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) ExitTransition.None
    else slideOutHorizontally(tween(NAV_TRANSITION_MS)) { it } + fadeOut(tween(NAV_TRANSITION_MS))

/**
 * Layer motion for modal-ish screens (archived / settings): the incoming
 * screen slides up from the bottom like a sheet; pop slides it back down.
 * Collapses to no motion under system reduce-motion.
 */
private fun layerEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) EnterTransition.None
    else slideInVertically(tween(NAV_TRANSITION_MS)) { it } + fadeIn(tween(NAV_TRANSITION_MS))

private fun layerExit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) ExitTransition.None
    else slideOutVertically(tween(NAV_TRANSITION_MS)) { it / 3 } + fadeOut(tween(NAV_TRANSITION_MS))

private fun layerPopEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) EnterTransition.None
    else slideInVertically(tween(NAV_TRANSITION_MS)) { it / 3 } + fadeIn(tween(NAV_TRANSITION_MS))

private fun layerPopExit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) ExitTransition.None
    else slideOutVertically(tween(NAV_TRANSITION_MS)) { it } + fadeOut(tween(NAV_TRANSITION_MS))
