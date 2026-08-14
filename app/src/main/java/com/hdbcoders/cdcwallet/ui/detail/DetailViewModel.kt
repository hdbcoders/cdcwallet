package com.hdbcoders.cdcwallet.ui.detail

import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.data.VoucherRepository
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.extraction.ExtractionCoordinator
import com.hdbcoders.cdcwallet.extraction.ExtractionResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoaded: Boolean = false,
    val voucher: VoucherGroup? = null,
)

class DetailViewModel(
    private val repository: VoucherRepository,
    private val extractionCoordinator: ExtractionCoordinator,
    private val voucherId: String,
) : ViewModel() {

    /** `isLoaded` goes true after the first DB emission, distinguishing
     *  "still loading" from "row missing" (replaces the nullable sentinel in MainActivity).
     *  Observes by id with no archived filter (spec 05 §5.4): a tap on an archived
     *  voucher must open its detail screen just like a main-list tap.
     *
     *  The URL is derived from THIS reactive row (refactor M6): navigation
     *  carries only the id, so the extraction always uses the row's current
     *  URL - imports/replacements can never leave the screen loading a stale
     *  bearer URL baked into a navigation argument. */
    val uiState: StateFlow<DetailUiState> = repository.observeById(voucherId)
        .map { voucher -> DetailUiState(isLoaded = true, voucher = voucher) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    /** The WebView is the engine's long-lived instance (02 §2.4 revision
     *  2026-08-03): a fresh screen entry extracts exactly once; rotation only
     *  re-attaches the same instance, which must never re-extract or reload. */
    var refreshStarted by mutableStateOf(false)
        private set
    var pageProgress by mutableStateOf(100)
        private set

    /** Non-blocking banner text as a localized resource id (resolved by the
     *  screen so the active app locale is used). */
    var refreshMessageRes by mutableStateOf<Int?>(null)
        private set

    /** The WebView attached by the current screen, until the row lookup lands. */
    private var pendingWebView: WebView? = null

    init {
        // Refactor M18: the extraction must not start before the row lookup
        // confirms the row EXISTS. When the row arrives before the WebView
        // attaches, the attach path below launches; when it arrives after,
        // this observer launches it.
        viewModelScope.launch {
            val firstRow = repository.observeById(voucherId).first()
            val webView = pendingWebView
            if (firstRow != null && webView != null) {
                launchIfNeeded(firstRow, webView)
            }
        }
    }

    fun onPageProgressChanged(progress: Int) { pageProgress = progress }

    fun onNewWebViewReady(webView: WebView) {
        if (refreshStarted) {
            // Rotation re-attached the persistent WebView: the page and any
            // in-flight load survived, so there is nothing to do.
            return
        }
        pendingWebView = webView
        val state = uiState.value
        if (state.isLoaded) {
            state.voucher?.let { row -> launchIfNeeded(row, webView) }
        }
        // Not loaded yet: the init observer launches once the row lands.
    }

    private fun launchIfNeeded(row: VoucherGroup, webView: WebView) {
        if (refreshStarted) return
        refreshStarted = true
        // Delegate to the app-scoped coordinator: the extraction runs in a
        // scope that survives screen exit (02 §2.7), so backing out mid-load
        // still updates the row. The banner is best-effort UI state. The URL
        // comes from the reactive row - never from a navigation argument.
        extractionCoordinator.launchVisible(
            voucherId = row.id,
            voucherUrl = row.url,
            webView = webView,
            onResult = { result ->
                if (result is ExtractionResult.Failure) {
                    refreshMessageRes = when (result.reason) {
                        // Spec 02 §2.7: timeout is treated as a parse error.
                        ExtractionResult.FailureReason.PARSE_ERROR,
                        ExtractionResult.FailureReason.TIMEOUT -> R.string.detail_parse_error
                        ExtractionResult.FailureReason.NETWORK_ERROR -> R.string.detail_network_error
                    }
                }
            },
        )
    }

    fun consumeRefreshMessage() { refreshMessageRes = null }

    override fun onCleared() {
        // The screen is gone (back navigation pops the backstack entry): the
        // extraction and its database write continue app-scoped, but the
        // screen-bound callback must not retain this ViewModel (refactor H1).
        extractionCoordinator.detachResultCallback(voucherId)
    }
}
