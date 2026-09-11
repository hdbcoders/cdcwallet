package com.hdbcoders.cdcwallet

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
import com.hdbcoders.cdcwallet.ui.theme.DyslexiaFontStore
import com.hdbcoders.cdcwallet.ui.theme.FontScaleStore
import com.hdbcoders.cdcwallet.ui.theme.rememberReduceMotion
import com.hdbcoders.cdcwallet.update.UpdateCheckResult
import com.hdbcoders.cdcwallet.update.openPlayStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Minimum splash hold before the list appears, so the splash logo is actually
 * visible: on API 31+ the OS system-splash window covers cold startup
 * (~1.4s), so without this floor the Compose splash is never revealed.
 */
internal const val SPLASH_MIN_MS = 1_500L

/**
 * The app's navigation graph, extracted from [MainActivity] (H2: god-file
 * split) so the Activity holds only lifecycle/intent/theme concerns. Owns the
 * bootstrap gate, the share-intent routing, the REQ-13 update dialog, and every
 * route in the graph. [MainActivity] supplies the container and the callbacks
 * it alone can provide (language change, pending-share consumption).
 */
@Composable
internal fun AppNavHost(
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
