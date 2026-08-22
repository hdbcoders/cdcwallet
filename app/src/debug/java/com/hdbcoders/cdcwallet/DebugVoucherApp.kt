package com.hdbcoders.cdcwallet

import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.hdbcoders.cdcwallet.addflow.AddVoucherFlow
import com.hdbcoders.cdcwallet.data.VoucherRepository
import com.hdbcoders.cdcwallet.dev.DevActions
import com.hdbcoders.cdcwallet.dev.DevFixtureFlow
import com.hdbcoders.cdcwallet.dev.DevSeeder
import com.hdbcoders.cdcwallet.dev.SeedDevDataReceiver
import com.hdbcoders.cdcwallet.dev.UpdateDevReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only Application (lives under `src/debug`, so release builds keep the
 * plain [VoucherApp]; the debug manifest points at this class). Auto-seeds the
 * dev fixtures on launch so a fresh install/cleared-data shows them with no
 * extra step - unless auto-seed was disabled by CLEAR_DEV_DATA. Re-seed /
 * force-reseed / clear-all from adb is handled by [SeedDevDataReceiver], which
 * is registered here DYNAMICALLY: on API 36 the background-broadcast policy
 * silently drops implicit broadcasts to manifest-declared receivers, while a
 * receiver registered from the running process always receives them.
 *
 *   adb shell am broadcast -a com.hdbcoders.cdcwallet.action.SEED_DEV_DATA
 *   adb shell am broadcast -a com.hdbcoders.cdcwallet.action.CLEAR_DEV_DATA
 */
class DebugVoucherApp : VoucherApp() {

    private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Debug-only bootstrap override (audit C2 / spec 04 §4.8): lets the
     * instrumented fatal-bootstrap UI test put the running app into the
     * honest `Failed -> fatal-error-screen` state deterministically, and -
     * crucially - get a healthy bootstrap back afterwards (clearing the
     * container's [AppContainer.bootstrapOverride] seam falls back to the
     * once-per-process real bootstrap). Mirrors the REQ-13 update
     * simulation: the debug override stands in for the real failure trigger.
     * Never compiled into release builds.
     */
    fun setBootstrapFailureSimulated(simulated: Boolean) {
        container.bootstrapOverride = if (simulated) {
            // Same initializer contract as the production bootstrap; the
            // injected throw resolves into DatabaseBootstrapState.Failed
            // exactly like a real SQLCipher open failure would.
            DatabaseBootstrap { throw IllegalStateException("simulated bootstrap failure (debug seam)") }
                .also { it.start() }
        } else {
            null
        }
    }

    /**
     * Fixture-seamed add flow for the share-intent instrumented test (see
     * [DevFixtureFlow]): synthetic appassets host, never a real RedeemSG host.
     */
    override fun devFixtureAddFlow(repository: VoucherRepository): AddVoucherFlow? =
        DevFixtureFlow.buildAddFlow(this, repository)

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            SeedDevDataReceiver(),
            IntentFilter().apply {
                addAction(DevActions.ACTION_SEED)
                addAction(DevActions.ACTION_CLEAR)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        // REQ-13: update-check simulation (badge / menu slot / popup testing).
        ContextCompat.registerReceiver(
            this,
            UpdateDevReceiver(),
            IntentFilter().apply {
                addAction(DevActions.ACTION_SIMULATE_UPDATE)
                addAction(DevActions.ACTION_CLEAR_UPDATE_SIM)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        if (DevActions.autoSeedEnabled(this)) {
            // Best-effort warm seed on every launch: seeds only if missing, so
            // it's a no-op once the fixtures exist. Awaits the database
            // bootstrap (refactor M2) before touching the repository.
            seedScope.launch {
                runCatching {
                    container.databaseBootstrap.awaitReady()
                    DevSeeder.seedIfEmpty(container.repository)
                }
            }
        }
    }
}