package com.hdbcoders.cdcwallet.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Owns the update-availability state (REQ-13): a persisted "!" flag plus the
 * 24-hour throttled silent check. Held in Compose snapshot state so the
 * header badge and the menu slot recompose instantly.
 *
 * Two entry points, mirroring the spec exactly:
 *  - [silentCheckIfDue] - app-open hook. At most ONE Play query per
 *    24 hours (or never checked). Never shows UI - it only sets the flag.
 *  - [userCheck] - "Check for update" menu action. Unthrottled, returns the
 *    result so the caller can show the update popup. The flag is also set,
 *    so the menu slot flips to "Tap to update" afterwards.
 *
 * Fail-soft everywhere: any source failure leaves the flag untouched and
 * still stamps the timestamp, so a broken Play connection cannot hammer the
 * binder with retries. A stale flag (the installed app updated since the
 * flag was set) is cleared at construction.
 */
class UpdateChecker(
    private val store: UpdateCheckStore,
    private val source: UpdateAvailabilitySource,
    private val clock: () -> Long = System::currentTimeMillis,
    private val installedVersionCode: () -> Int,
) {

    /** True while a newer version is known to exist - drives the "!" badge
     *  and the "Tap to update" menu slot. */
    var updateAvailable by mutableStateOf(store.isFlagged())
        private set

    init {
        // The flag described an OLDER installed version: the user has already
        // updated, so the "!" must not outlive the update it advertised.
        if (shouldClearStaleFlag(updateAvailable, store.flagVersionCode(), installedVersionCode())) {
            updateAvailable = false
            store.clearFlagged()
        }
    }

    /** App-open check: throttled to once per [CHECK_INTERVAL_MS]. Flag-only. */
    suspend fun silentCheckIfDue() {
        if (updateAvailable) return // Already known - nothing to query.
        if (!isCheckDue(store.lastCheckMs(), clock(), CHECK_INTERVAL_MS)) return
        runCheck()
    }

    /** User-triggered check: never throttled. Returns the result for the UI. */
    suspend fun userCheck(): UpdateCheckResult {
        if (updateAvailable) return UpdateCheckResult.Available
        return runCheck()
    }

    private suspend fun runCheck(): UpdateCheckResult {
        val result = try {
            // Debug/test seam: DevActions.SIMULATE_UPDATE_AVAILABLE swaps the
            // source so the full UI flow can be exercised without a Play
            // track. Production always uses the injected real source.
            (debugSourceOverride ?: source).check()
        } catch (e: Exception) {
            UpdateCheckResult.Failed
        }
        store.stampLastCheck(clock())
        if (result == UpdateCheckResult.Available) {
            updateAvailable = true
            store.setFlagged(installedVersionCode())
        }
        return result
    }

    /** Debug/test seam - set only by DevActions (src/debug), never in release. */
    internal var debugSourceOverride: UpdateAvailabilitySource? = null

    /** Debug/test seam - DevActions.CLEAR_UPDATE_SIMULATION. */
    internal fun clearDebugFlag() {
        updateAvailable = false
        store.clearFlagged()
    }

    companion object {
        const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }
}
