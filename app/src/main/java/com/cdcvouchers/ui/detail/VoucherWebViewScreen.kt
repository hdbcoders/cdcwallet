package com.cdcvouchers.ui.detail

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.extraction.ExtractionEngine

/**
 * Tap-to-open-and-refresh (spec 04 §4.4, call site C2 of 02 §2.4). The WebView
 * shown to the user is the same instance that performs the extraction — never
 * a second hidden WebView. On success the cached row updates and the list
 * recomposes when the user returns; on failure the row is marked stale via
 * `lastRefreshError` and a non-blocking banner appears — the WebView stays
 * usable either way.
 *
 * All state lives in [DetailViewModel], which survives rotation: a recreated
 * WebView rehydrates the page (viewport fix re-applied, no re-extraction).
 * The WebView itself is destroyed on dispose to avoid native resource leaks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherWebViewScreen(
    voucherId: String,
    repository: VoucherRepository,
    extractionEngine: ExtractionEngine,
    onBack: () -> Unit,
    webViewFactory: (Context) -> WebView = { WebView(it) },
    modifier: Modifier = Modifier,
) {
    val vm: DetailViewModel = viewModel(
        key = "detail-$voucherId",
        initializer = { DetailViewModel(repository, extractionEngine, voucherId) },
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val state by vm.uiState.collectAsState()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val voucher = state.voucher
    if (!state.isLoaded) return
    if (voucher == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(voucher.campaignName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (vm.pageProgress < 100) {
                LinearProgressIndicator(
                    progress = { vm.pageProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AndroidView(
                factory = { context ->
                    webViewFactory(context).also { webView ->
                        webView.webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                vm.onPageProgressChanged(newProgress)
                            }
                        }
                        webViewRef = webView
                    }
                },
                update = { view -> vm.onNewWebViewReady(view, voucher.url) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val view = webViewRef
            if (view != null) {
                runCatching { view.stopLoading() }
                runCatching { (view.parent as? ViewGroup)?.removeView(view) }
                runCatching { view.destroy() }
            }
        }
    }

    LaunchedEffect(vm.refreshMessage) {
        vm.refreshMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
            vm.consumeRefreshMessage()
        }
    }
}
