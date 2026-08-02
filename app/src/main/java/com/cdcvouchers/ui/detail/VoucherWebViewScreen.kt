package com.cdcvouchers.ui.detail

import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.data.model.VoucherRefreshData
import com.cdcvouchers.extraction.ExtractionEngine
import com.cdcvouchers.extraction.ExtractionResult
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Tap-to-open-and-refresh (spec 04 §4.4, call site C2 of 02 §2.4). The WebView
 * shown to the user is the same instance that performs the extraction — never
 * a second hidden WebView. On success the cached row updates and the list
 * recomposes when the user returns; on failure the row is marked stale via
 * `lastRefreshError` and a non-blocking banner appears — the WebView stays
 * usable either way.
 *
 * The extraction is triggered from the AndroidView `update` callback (which
 * runs in the current snapshot with the freshly created view in hand) rather
 * than a LaunchedEffect polling snapshot state, which can observe stale values
 * until a frame advances.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherWebViewScreen(
    voucher: VoucherGroup,
    repository: VoucherRepository,
    extractionEngine: ExtractionEngine,
    onBack: () -> Unit,
    webViewFactory: (Context) -> WebView = { WebView(it) },
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var refreshStarted by remember { mutableStateOf(false) }
    var pageProgress by remember { mutableStateOf(100) }

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
            if (pageProgress < 100) {
                LinearProgressIndicator(
                    progress = { pageProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AndroidView(
                factory = { context ->
                    webViewFactory(context).also { webView ->
                        webView.webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress
                            }
                        }
                    }
                },
            update = { view ->
                if (!refreshStarted) {
                    refreshStarted = true
                    scope.launch {
                        when (val result = extractionEngine.extractFromVisibleWebView(view, voucher.url)) {
                            is ExtractionResult.Success -> repository.updateFromRefresh(
                                voucher.id,
                                VoucherRefreshData(
                                    campaignName = result.campaignName,
                                    validityStatus = result.validityStatus,
                                    expiryDate = result.expiryDate,
                                    categoryBalances = result.categoryBalances,
                                    lastRefreshedAt = Instant.now(),
                                ),
                            )
                            is ExtractionResult.Failure -> {
                                repository.recordRefreshFailure(voucher.id, result.reason.name)
                                val message = when (result.reason) {
                                    ExtractionResult.FailureReason.PARSE_ERROR ->
                                        "Website data failed to parse"
                                    ExtractionResult.FailureReason.NETWORK_ERROR,
                                    ExtractionResult.FailureReason.TIMEOUT ->
                                        "Unable to load website"
                                }
                                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
                            }
                        }
                    }
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}
