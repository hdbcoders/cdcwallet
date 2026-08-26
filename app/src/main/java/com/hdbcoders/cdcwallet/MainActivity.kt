package com.hdbcoders.cdcwallet

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.hdbcoders.cdcwallet.update.UpdateCheckResult
import com.hdbcoders.cdcwallet.update.openPlayStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hdbcoders.cdcwallet.addflow.AddVoucherFlow
import com.hdbcoders.cdcwallet.ui.accessibility.AccessibilityScreen
import com.hdbcoders.cdcwallet.ui.add.AddVoucherScreen
import com.hdbcoders.cdcwallet.ui.detail.VoucherWebViewScreen
import com.hdbcoders.cdcwallet.ui.list.ArchivedVoucherScreen
import com.hdbcoders.cdcwallet.ui.list.SplashScreen
import com.hdbcoders.cdcwallet.ui.list.VoucherListScreen
import com.hdbcoders.cdcwallet.ui.settings.AboutScreen
import com.hdbcoders.cdcwallet.ui.settings.SettingsScreen
import com.hdbcoders.cdcwallet.ui.theme.AppLanguage
import com.hdbcoders.cdcwallet.ui.theme.AppTheme
import com.hdbcoders.cdcwallet.ui.theme.DyslexiaFontStore
import com.hdbcoders.cdcwallet.ui.theme.FontScaleStore
import com.hdbcoders.cdcwallet.ui.theme.LocalAppLanguage
import com.hdbcoders.cdcwallet.ui.theme.rememberReduceMotion
import com.hdbcoders.cdcwallet.ui.theme.wrapWithLocale

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

@Composable
private fun AppNavHost(
    container: AppContainer,
    flowProvider: () -> AddVoucherFlow,
    sharedUrl: String?,
    isColdStart: Boolean,
    navController: NavHostController,
    fontScaleStore: FontScaleStore,
    dyslexiaFontStore: DyslexiaFontStore,
    onLanguageSelected: (AppLanguage) -> Unit,
    pendingShareUrl: String?,
    onPendingShareUrlConsumed: () -> Unit,
) {
    // Cold-start race fix (tester crash on v1): the caller passes a lambda
    // instead of the AddVoucherFlow instance. Evaluating the argument at the
    // call site would force MainActivity.flow's lazy - which builds
    // container.repository - during the FIRST composition, before the
    // bootstrap gate below runs. The lambda is only invoked inside the "add"
    // route below, which is composed strictly after the bootstrap reaches
    // Ready, so container.repository is guaranteed to be available there.
    // Database readiness gate (refactor M2): the whole UI waits for the
    // IO-backed bootstrap. Failed -> fatal error screen (the splash must
    // never hang); Initializing -> Compose splash on API < 31 (API 31+ is
    // covered by the OS splash held via keepOnScreenCondition).
    val bootstrap by container.databaseBootstrap.state.collectAsState()
    if (bootstrap is DatabaseBootstrapState.Failed) {
        FatalErrorScreen()
        return
    }
    if (bootstrap is DatabaseBootstrapState.Initializing) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            SplashScreen()
        }
        return
    }

    if (isColdStart && sharedUrl != null) {
        LaunchedEffect(Unit) {
            navController.navigate("add?url=${Uri.encode(sharedUrl)}") { launchSingleTop = true }
        }
    }

    // Refactor M7: consume a share that arrived before navigation was ready.
    LaunchedEffect(pendingShareUrl) {
        pendingShareUrl?.let { url ->
            onPendingShareUrlConsumed()
            navController.navigate("add?url=${Uri.encode(url)}") { launchSingleTop = true }
        }
    }

    val reduceMotion = rememberReduceMotion()

    // REQ-13: update availability drives the "!" badge and the menu slot.
    // Snapshot state read directly - recomposes the header when the silent
    // check (or a user check) flips the flag.
    val updateAvailable = container.updateChecker.updateAvailable
    // The user-triggered check always responds visibly (a silent tap reads as
    // a broken feature): popup on Available, toast on NotAvailable/Failed.
    var updateDialogVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val upToDateText = stringResource(R.string.up_to_date)
    val checkFailedText = stringResource(R.string.update_check_failed)
    val onCheckForUpdate: () -> Unit = {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                container.updateChecker.userCheck()
            }
            when (result) {
                UpdateCheckResult.Available -> updateDialogVisible = true
                UpdateCheckResult.NotAvailable ->
                    Toast.makeText(appContext, upToDateText, Toast.LENGTH_SHORT).show()
                UpdateCheckResult.Failed ->
                    Toast.makeText(appContext, checkFailedText, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val onTapToUpdate = {
        openPlayStore(appContext, appContext.packageName)
    }

    // One-shot: does the list's first DB load still need masking? Scoped here
    // (above the NavHost) so it survives back navigation and rotation but
    // resets on a true process death - the splash masks only the initial
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
                // load - render the list directly, never an empty list.
                VoucherListScreen(
                    repository = container.repository,
                    extractionCoordinator = container.extractionCoordinator,
                    onAddClick = { navController.navigate("add") },
                    onOpenVoucher = { voucher ->
                        navController.navigate("detail/${voucher.id}")
                    },
                    onArchivedClick = { navController.navigate("archived") },
                    onSettingsClick = { navController.navigate("settings") },
                    onAboutClick = { navController.navigate("about") },
                    onAccessibilityClick = { navController.navigate("accessibility") },
                    onToggleTheme = { container.themeModeStore.toggle() },
                    heroCollapsed = container.heroCollapseStore.collapsed,
                    onToggleHeroCollapsed = { container.heroCollapseStore.toggle() },
                    languageStore = container.languageStore,
                    onLanguageSelected = onLanguageSelected,
                    updateAvailable = updateAvailable,
                    onCheckForUpdate = onCheckForUpdate,
                    onTapToUpdate = onTapToUpdate,
                )
            } else {
                // API < 31: no system splash, so the Compose splash masks the
                // initial load - hold the logo until the first DB read
                // completes, then crossfade into the list. Uses the
                // repository flow directly (no ViewModel) - the screen below
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
                                navController.navigate("detail/${voucher.id}")
                            },
                            onArchivedClick = { navController.navigate("archived") },
                            onSettingsClick = { navController.navigate("settings") },
                            onAboutClick = { navController.navigate("about") },
                            onAccessibilityClick = { navController.navigate("accessibility") },
                            onToggleTheme = { container.themeModeStore.toggle() },
                            heroCollapsed = container.heroCollapseStore.collapsed,
                            onToggleHeroCollapsed = { container.heroCollapseStore.toggle() },
                            languageStore = container.languageStore,
                            onLanguageSelected = onLanguageSelected,
                            updateAvailable = updateAvailable,
                            onCheckForUpdate = onCheckForUpdate,
                            onTapToUpdate = onTapToUpdate,
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
                    navController.navigate("detail/${voucher.id}")
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
                themeModeStore = container.themeModeStore,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "accessibility",
            enterTransition = { layerEnter(reduceMotion) },
            exitTransition = { layerExit(reduceMotion) },
            popEnterTransition = { layerPopEnter(reduceMotion) },
            popExitTransition = { layerPopExit(reduceMotion) },
        ) {
            AccessibilityScreen(
                dyslexiaFontStore = dyslexiaFontStore,
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
                flow = flowProvider(),
                initialUrl = entry.arguments?.getString("url"),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "detail/{voucherId}",
            arguments = listOf(
                navArgument("voucherId") { type = NavType.StringType },
            ),
            enterTransition = { drillInEnter(reduceMotion) },
            exitTransition = { drillInExit(reduceMotion) },
            popEnterTransition = { drillInPopEnter(reduceMotion) },
            popExitTransition = { drillInPopExit(reduceMotion) },
        ) { entry ->
            VoucherWebViewScreen(
                voucherId = entry.arguments?.getString("voucherId").orEmpty(),
                repository = container.repository,
                extractionEngine = container.extractionEngine,
                extractionCoordinator = container.extractionCoordinator,
                onBack = { navController.popBackStack() },
            )
        }
    }

    // REQ-13: closeable update dialog - shown ONLY when a USER-triggered
    // check finds an update (silent checks never pop it). Dismissible via
    // "Not now", outside tap and back; "Open Play Store" deep-links to the
    // listing (market:// with https fallback).
    if (updateDialogVisible) {
        AlertDialog(
            onDismissRequest = { updateDialogVisible = false },
            title = { Text(stringResource(R.string.update_available)) },
            text = { Text(stringResource(R.string.update_available_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateDialogVisible = false
                        onTapToUpdate()
                    },
                ) {
                    Text(stringResource(R.string.open_play_store))
                }
            },
            dismissButton = {
                TextButton(onClick = { updateDialogVisible = false }) {
                    Text(stringResource(R.string.not_now))
                }
            },
        )
    }
}

/** Navigation motion duration - short enough to feel snappy, long enough to read. */
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

/**
 * Shown when the database bootstrap failed (refactor M2): the splash must
 * never hang on an initialization failure - it resolves into an explicit,
 * readable error state instead.
 */
@Composable
private fun FatalErrorScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.fatal_startup_error),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
