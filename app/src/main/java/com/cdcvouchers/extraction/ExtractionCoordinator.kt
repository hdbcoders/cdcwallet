package com.cdcvouchers.extraction

import android.webkit.WebView
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.model.VoucherRefreshData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * App-scoped orchestrator for visible-WebView extractions (spec 02 §2.7
 * concurrency rules). It owns:
 *  - a scope that **survives screen exit** — the detail screen's ViewModel is
 *    destroyed on back, but the extraction must finish in the background and
 *    still write the result (02 §2.7 "in-flight loads survive screen exit");
 *  - a registry of in-flight extractions keyed by `VoucherGroup.id`, so a
 *    delete can cancel the specific load before removing the row (02 §2.7
 *    "cancel extraction on delete").
 *
 * Lives in [com.cdcvouchers.AppContainer] — one instance for the app. The
 * add-time hidden-WebView path is NOT routed here: it is a one-shot flow owned
 * by the add screen, whose cancellation teardown lives in [ExtractionEngine].
 */
class ExtractionCoordinator(
    private val repository: VoucherRepository,
    private val extractionEngine: ExtractionEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val inFlight = HashMap<String, Job>()

    /**
     * Start (or reuse) the tap-refresh extraction for [voucherId]. The
     * coroutine runs in the app-scoped [scope], so it survives leaving the
     * detail screen; [onResult] is invoked when it settles.
     */
    fun launchVisible(
        voucherId: String,
        voucherUrl: String,
        webView: WebView,
        onResult: (ExtractionResult) -> Unit,
    ) {
        val existing = inFlight[voucherId]
        if (existing != null && existing.isActive) return
        val job = scope.launch {
            val result = extractionEngine.extractFromVisibleWebView(webView, voucherUrl)
            // Apply the result even if the screen is gone — the DB write is the
            // durable part; the UI callback is best-effort.
            applyResult(voucherId, result)
            ensureActive()
            onResult(result)
        }
        // Drop the registry entry when the job settles (success, failure, or
        // cancellation) so the map never grows unboundedly.
        job.invokeOnCompletion { inFlight.remove(voucherId, job) }
        inFlight[voucherId] = job
    }

    /** Cancel any in-flight extraction for [voucherId] (spec 02 §2.7). */
    fun cancel(voucherId: String) {
        inFlight.remove(voucherId)?.cancel()
    }

    private suspend fun applyResult(voucherId: String, result: ExtractionResult) {
        when (result) {
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
            is ExtractionResult.Failure -> repository.recordRefreshFailure(voucherId, result.reason.name)
        }
    }
}
