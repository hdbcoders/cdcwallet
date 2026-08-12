package com.cdcwallet.ui.detail

import android.content.Context
import android.os.Build
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdcwallet.R
import com.cdcwallet.data.VoucherRepository
import com.cdcwallet.extraction.ExtractionCoordinator
import com.cdcwallet.extraction.ExtractionEngine
import com.cdcwallet.ui.theme.LocalAppLanguage
import com.cdcwallet.ui.theme.localizeCampaignName

/**
 * Tap-to-open-and-refresh (spec 04 §4.4, call site C2 of 02 §2.4). The WebView
 * shown to the user is the same instance that performs the extraction - never
 * a second hidden WebView. On success the cached row updates and the list
 * recomposes when the user returns; on failure the row is marked stale via
 * `lastRefreshError` and a non-blocking banner appears - the WebView stays
 * usable either way.
 *
 * The WebView is the engine's **long-lived instance** (02 §2.4 revision
 * 2026-08-03): this screen acquires it on entry and detaches it on exit -
 * it never creates or destroys it. Every tap still reloads the URL (fresh
 * data, per 02 §2.7), so a re-tap of the same voucher is a reload. State
 * lives in [DetailViewModel], which survives rotation: re-attaching the same
 * instance after rotation must not re-extract or reload, which the VM's
 * `refreshStarted` guard ensures. A `WebChromeClient` for progress reporting
 * is fine here; the engine owns the `WebViewClient` slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherWebViewScreen(
    voucherId: String,
    voucherUrl: String,
    repository: VoucherRepository,
    extractionEngine: ExtractionEngine,
    extractionCoordinator: ExtractionCoordinator,
    onBack: () -> Unit,
    webViewFactory: (Context) -> WebView = { extractionEngine.acquireVisibleWebView(it) },
    modifier: Modifier = Modifier,
) {
    val vm: DetailViewModel = viewModel(
        key = "detail-$voucherId",
        initializer = {
            DetailViewModel(repository, extractionEngine, extractionCoordinator, voucherId, voucherUrl)
        },
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val state by vm.uiState.collectAsState()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    /**
     * The screen-owned progress client, captured at attach time. On dispose
     * it is released only when the WebView still carries THIS client (a newer
     * detail screen may have replaced it during animated navigation).
     */
    var chromeClientRef by remember { mutableStateOf<WebChromeClient?>(null) }

    /**
     * The view hierarchy this screen attached the WebView into (recorded in
     * the AndroidView update block). The engine's instance is long-lived and
     * can be re-parented by a NEWER detail screen during animated navigation
     * (refactor H1); on dispose we must detach it only when it is still in
     * OUR host, never out of a newer screen's.
     */
    var attachedHostRef by remember { mutableStateOf<ViewGroup?>(null) }

    val voucher = state.voucher
    if (state.isLoaded && voucher == null) {
        LaunchedEffect(Unit) { onBack() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(localizeCampaignName(voucher?.campaignName.orEmpty(), LocalAppLanguage.current)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                        val progressClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                vm.onPageProgressChanged(newProgress)
                            }
                        }
                        webView.webChromeClient = progressClient
                        webViewRef = webView
                        chromeClientRef = progressClient
                    }
                },
                update = { view ->
                    // Record the host we attached into - ONCE per screen
                    // lifetime (refactor H1). Recomposition must not re-record
                    // it: during animated navigation a newer screen may have
                    // re-parented the shared instance, and a later update
                    // would then point at the NEWER host, making the dispose
                    // guard detach the view out of the newer screen.
                    if (attachedHostRef == null) {
                        attachedHostRef = view.parent as? ViewGroup
                    }
                    vm.onNewWebViewReady(view)
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Detach only if the engine's long-lived instance is still inside
            // THIS screen's host (02 §2.4 revision 2026-08-03: detach-only,
            // never stopLoading/destroy). During animated navigation a newer
            // detail screen may already have re-parented the view into its own
            // host - removing it from there would blank the newer screen
            // (refactor H1). Best-effort: if the interop container already
            // removed the view, there is nothing to detach.
            val view = webViewRef
            if (view != null) {
                val stillOurs = view.parent === attachedHostRef
                if (stillOurs) {
                    runCatching { (view.parent as? ViewGroup)?.removeView(view) }
                }
                // Release the screen-owned progress client when the WebView
                // still carries it, so it cannot retain this screen's
                // ViewModel after the screen is gone. API 26+ has the
                // getWebChromeClient getter for an identity check; API 24-25
                // does not, so there the client is released only when no
                // newer screen can own the instance (a newer screen that took
                // it would have re-parented the view into its own host).
                if (Build.VERSION.SDK_INT >= 26) {
                    if (view.webChromeClient === chromeClientRef) {
                        runCatching { view.webChromeClient = null }
                    }
                } else {
                    val someoneElseTookIt = view.parent != null && !stillOurs
                    if (!someoneElseTookIt) {
                        runCatching { view.webChromeClient = null }
                    }
                }
            }
        }
    }

    // Resolved at composition time so the snackbar text follows the active
    // app locale; the LaunchedEffect below shows it when it changes.
    val refreshMessage = vm.refreshMessageRes?.let { stringResource(it) }
    LaunchedEffect(vm.refreshMessageRes) {
        refreshMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
            vm.consumeRefreshMessage()
        }
    }
}
