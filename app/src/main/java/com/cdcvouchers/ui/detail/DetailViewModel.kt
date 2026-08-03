package com.cdcvouchers.ui.detail

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.data.model.VoucherRefreshData
import com.cdcvouchers.extraction.ExtractionEngine
import com.cdcvouchers.extraction.ExtractionResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class DetailUiState(
    val isLoaded: Boolean = false,
    val voucher: VoucherGroup? = null,
)

class DetailViewModel(
    private val repository: VoucherRepository,
    private val extractionEngine: ExtractionEngine,
    private val voucherId: String,
    private val voucherUrl: String,
) : ViewModel() {

    /** `isLoaded` goes true after the first DB emission, distinguishing
     *  "still loading" from "row missing" (replaces the nullable sentinel in MainActivity). */
    val uiState: StateFlow<DetailUiState> = repository.observeActive()
        .map { list -> DetailUiState(isLoaded = true, voucher = list.firstOrNull { it.id == voucherId }) }
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
        viewModelScope.launch {
            when (val result = extractionEngine.extractFromVisibleWebView(webView, voucherUrl)) {
                is ExtractionResult.Success -> repository.updateFromRefresh(
                    voucherId,
                    VoucherRefreshData(
                        campaignName = result.campaignName,
                        validityStatus = result.validityStatus,
                        expiryDate = result.expiryDate,
                        categoryBalances = result.categoryBalances,
                        lastRefreshedAt = Instant.now(),
                    ),
                )
                is ExtractionResult.Failure -> {
                    repository.recordRefreshFailure(voucherId, result.reason.name)
                    refreshMessage = when (result.reason) {
                        ExtractionResult.FailureReason.PARSE_ERROR -> "Website data failed to parse"
                        ExtractionResult.FailureReason.NETWORK_ERROR,
                        ExtractionResult.FailureReason.TIMEOUT -> "Unable to load website"
                    }
                }
            }
        }
    }

    fun consumeRefreshMessage() { refreshMessage = null }
}
