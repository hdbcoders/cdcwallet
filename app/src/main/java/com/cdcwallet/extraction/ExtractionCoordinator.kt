package com.cdcwallet.extraction

import android.webkit.WebView
import com.cdcwallet.data.VoucherRepository
import com.cdcwallet.data.model.VoucherRefreshData
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
 *  - a scope that **survives screen exit** - the detail screen's ViewModel is
 *    destroyed on back, but the extraction must finish in the background and
 *    still write the result (02 §2.7 "in-flight loads survive screen exit");
 *  - **the single visible extraction slot** (refactor H1). The app has one
 *    visible WebView, so at most one visible extraction exists at a time.
 *    A new tap (same or different voucher) supersedes the in-flight one:
 *    latest-tap-wins cancels the previous job and `join()`s it, so its
 *    per-load teardown (bridge removal, client restore) finishes before the
 *    next load touches the shared WebView - one load's `finally` can never
 *    remove the next load's bridge or client;
 *  - delete-cancellation (02 §2.7 "cancel extraction on delete"): [cancel]
 *    aborts the extraction only when it belongs to the deleted row;
 *  - screen-callback detachment (refactor H1): [detachResultCallback] drops
 *    the UI callback of a screen that has gone away while the database
 *    completion (which is app-scoped) still runs.
 *
 * Lives in [com.cdcwallet.AppContainer] - one instance for the app. The
 * add-time hidden-WebView path is NOT routed here: it is a one-shot flow owned
 * by the add screen, whose cancellation teardown lives in [ExtractionEngine].
 */
class ExtractionCoordinator(
    private val repository: VoucherRepository,
    private val extractionEngine: ExtractionEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The single in-flight visible extraction, if any. */
    private var visibleJob: Job? = null
    private var visibleVoucherId: String? = null

    /** The screen callback of the current load; null when the screen left. */
    private var visibleOnResult: ((ExtractionResult) -> Unit)? = null

    /**
     * Start the tap-refresh extraction for [voucherId]. Latest-tap-wins: any
     * in-flight visible extraction (same or different voucher) is cancelled
     * and awaited first, so only the most recent tap's extraction can write
     * the database. The coroutine runs in the app-scoped [scope], so it
     * survives leaving the detail screen; [onResult] is invoked when it
     * settles and the screen callback is still attached.
     */
    fun launchVisible(
        voucherId: String,
        voucherUrl: String,
        webView: WebView,
        onResult: (ExtractionResult) -> Unit,
    ) {
        val previous = visibleJob
        if (previous != null && previous.isActive) {
            previous.cancel()
        }
        // Set synchronously (not inside the coroutine): a caller that detaches
        // the callback right after launching must not be undone by the job
        // body re-attaching it later.
        visibleOnResult = onResult
        val job = scope.launch {
            // Wait for the superseded load's teardown before touching the
            // shared WebView (its finally restores the client slot).
            previous?.join()
            ensureActive()
            val result = extractionEngine.extractFromVisibleWebView(webView, voucherUrl)
            // Apply the result even if the screen is gone - the DB write is the
            // durable part; the UI callback is best-effort. Both are guarded:
            // no failure below may crash the app-scoped scope (the engine
            // itself never throws, but the DB write or a screen callback is
            // still user-supplied code).
            ensureActive()
            runCatching { applyResult(voucherId, result) }
            ensureActive()
            runCatching { visibleOnResult?.invoke(result) }
        }
        // Drop the registry entry when the job settles (success, failure, or
        // cancellation) so the slot never goes stale.
        job.invokeOnCompletion {
            if (visibleJob === job) {
                visibleJob = null
                visibleVoucherId = null
                visibleOnResult = null
            }
        }
        visibleJob = job
        visibleVoucherId = voucherId
    }

    /** Cancel any in-flight extraction for [voucherId] (spec 02 §2.7). */
    fun cancel(voucherId: String) {
        if (visibleVoucherId == voucherId) {
            visibleJob?.cancel()
        }
    }

    /**
     * Cancel any in-flight visible extraction regardless of voucher (refactor
     * D3): used by the debug clear/reseed tools, whose stable fixture IDs mean
     * a late result could otherwise write onto a freshly re-inserted row with
     * the same id.
     */
    fun cancelAll() {
        visibleJob?.cancel()
    }

    /**
     * Detach the UI callback of a detail screen that has gone away (refactor
     * H1). The extraction itself and its database write continue - only the
     * screen-bound callback (banner state) is dropped, so the ViewModel is not
     * retained past the load.
     */
    fun detachResultCallback(voucherId: String) {
        if (visibleVoucherId == voucherId) {
            visibleOnResult = null
        }
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
            is ExtractionResult.Failure -> repository.recordRefreshFailure(
                voucherId,
                // Shared classification (refactor M5): timeout persists as
                // PARSE_ERROR - identical to the add-time save path.
                result.reason.persistedName,
            )
        }
    }
}
