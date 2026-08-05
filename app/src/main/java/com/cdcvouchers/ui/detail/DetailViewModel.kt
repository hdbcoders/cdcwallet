package com.cdcvouchers.ui.detail

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.extraction.ExtractionCoordinator
import com.cdcvouchers.extraction.ExtractionEngine
import com.cdcvouchers.extraction.ExtractionResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DetailUiState(
    val isLoaded: Boolean = false,
    val voucher: VoucherGroup? = null,
)

class DetailViewModel(
    private val repository: VoucherRepository,
    private val extractionEngine: ExtractionEngine,
    private val extractionCoordinator: ExtractionCoordinator,
    private val voucherId: String,
    private val voucherUrl: String,
) : ViewModel() {

    /** `isLoaded` goes true after the first DB emission, distinguishing
     *  "still loading" from "row missing" (replaces the nullable sentinel in MainActivity).
     *  Observes by id with no archived filter (spec 05 §5.4): a tap on an archived
     *  voucher must open its detail screen just like a main-list tap. */
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
    var refreshMessage by mutableStateOf<String?>(null)
        private set

    fun onPageProgressChanged(progress: Int) { pageProgress = progress }

    fun onNewWebViewReady(webView: WebView) {
        if (refreshStarted) {
            // Rotation re-attached the persistent WebView: the page and any
            // in-flight load survived, so there is nothing to do.
            return
        }
        refreshStarted = true
        // Delegate to the app-scoped coordinator: the extraction runs in a
        // scope that survives screen exit (02 §2.7), so backing out mid-load
        // still updates the row. The banner is best-effort UI state.
        extractionCoordinator.launchVisible(
            voucherId = voucherId,
            voucherUrl = voucherUrl,
            webView = webView,
            onResult = { result ->
                if (result is ExtractionResult.Failure) {
                    refreshMessage = when (result.reason) {
                        // Spec 02 §2.7: timeout is treated as a parse error.
                        ExtractionResult.FailureReason.PARSE_ERROR,
                        ExtractionResult.FailureReason.TIMEOUT -> "Website data failed to parse"
                        ExtractionResult.FailureReason.NETWORK_ERROR -> "Unable to load website"
                    }
                }
            },
        )
    }

    fun consumeRefreshMessage() { refreshMessage = null }
}
