package com.cdcvouchers

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cdcvouchers.addflow.AddVoucherFlow
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.ui.add.AddVoucherScreen
import com.cdcvouchers.ui.detail.VoucherWebViewScreen
import com.cdcvouchers.ui.list.ArchivedVoucherScreen
import com.cdcvouchers.ui.list.VoucherListScreen
import com.cdcvouchers.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as VoucherApp).container

    private val flow: AddVoucherFlow by lazy {
        AddVoucherFlow(
            repository = container.repository,
            extractionEngine = container.extractionEngine,
        )
    }

    private var navControllerRef: NavController? = null

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
        super.onCreate(savedInstanceState)
        // §0.4 security bar: keep the app out of screenshots/recents previews.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val sharedUrl = intent?.getStringExtra(Intent.EXTRA_TEXT)
        setContent {
            MaterialTheme {
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
) {
    if (isColdStart && sharedUrl != null) {
        LaunchedEffect(Unit) {
            navController.navigate("add?url=${Uri.encode(sharedUrl)}")
        }
    }

    NavHost(navController = navController, startDestination = "list") {
        composable("list") {
            VoucherListScreen(
                repository = container.repository,
                onAddClick = { navController.navigate("add") },
                onOpenVoucher = { voucher -> navController.navigate("detail/${voucher.id}") },
                onArchivedClick = { navController.navigate("archived") },
                onSettingsClick = { navController.navigate("settings") },
            )
        }
        composable("archived") {
            ArchivedVoucherScreen(
                repository = container.repository,
                onBack = { navController.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(
                backupFlow = container.backupFlow,
                repository = container.repository,
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
        ) { entry ->
            AddVoucherScreen(
                flow = flow,
                initialUrl = entry.arguments?.getString("url"),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "detail/{voucherId}",
            arguments = listOf(navArgument("voucherId") { type = NavType.StringType }),
        ) { entry ->
            val voucherId = entry.arguments?.getString("voucherId").orEmpty()
            // `null` = first emission still pending; only pop once the state is
            // loaded AND the row is really missing — the initial empty frame
            // must never pop the route (live-walkthrough catch).
            val vouchers: List<VoucherGroup>? by container.repository.observeActive()
                .collectAsState(initial = null)
            val loaded = vouchers
            when {
                loaded == null -> {}
                loaded.firstOrNull { it.id == voucherId } == null ->
                    LaunchedEffect(Unit) { navController.popBackStack() }
                else -> VoucherWebViewScreen(
                    voucher = loaded.first { it.id == voucherId },
                    repository = container.repository,
                    extractionEngine = container.extractionEngine,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
