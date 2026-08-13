package com.cdcwallet

import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.cdcwallet.dev.DevActions
import com.cdcwallet.dev.DevSeeder
import com.cdcwallet.dev.SeedDevDataReceiver
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
 *   adb shell am broadcast -a com.cdcwallet.action.SEED_DEV_DATA
 *   adb shell am broadcast -a com.cdcwallet.action.CLEAR_DEV_DATA
 */
class DebugVoucherApp : VoucherApp() {

    private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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