package com.hdbcoders.cdcwallet.dev

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hdbcoders.cdcwallet.VoucherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only broadcast receiver (declared exported in the debug manifest so
 * `adb shell am broadcast` can reach it):
 *
 *   # Reseed if empty (idempotent). Re-enables auto-seed.
 *   adb shell am broadcast -a com.hdbcoders.cdcwallet.action.SEED_DEV_DATA
 *
 *   # Force reseed (delete + reinsert the dev rows only, user vouchers kept):
 *   adb shell am broadcast -a com.hdbcoders.cdcwallet.action.SEED_DEV_DATA --ez force true
 *
 *   # Drop ALL rows (active + archived + dev fixtures) and disable auto-seed,
 *   # so the DB stays empty across relaunches until you reseed:
 *   adb shell am broadcast -a com.hdbcoders.cdcwallet.action.CLEAR_DEV_DATA
 *
 * Every action runs via [goAsync] so the broadcast process stays alive until
 * the database operation actually completes - otherwise an immediate
 * relaunch/force-stop could kill the delete before it finishes.
 */
class SeedDevDataReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VoucherApp
        val pendingResult = goAsync()
        // Refactor D3: cancel any in-flight visible extraction before mutating
        // rows - the stable fixture IDs mean a late result could land on a
        // freshly re-inserted row after a clear/reseed.
        app.container.extractionCoordinator.cancelAll()
        when (intent.action) {
            DevActions.ACTION_CLEAR -> {
                // Disable auto-seed first, then wipe every row (transactional
                // repository replace, refactor D2 - no direct DAO access from
                // debug code). Relaunching the app now leaves the DB empty
                // until a SEED broadcast is sent.
                DevActions.setAutoSeedEnabled(app, false)
                scope.launch {
                    try {
                        app.container.repository.replaceAll(emptyList())
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            else -> { // DevActions.ACTION_SEED
                DevActions.setAutoSeedEnabled(app, true)
                val force = intent.getBooleanExtra("force", false)
                scope.launch {
                    try {
                        if (force) DevSeeder.forceSeed(app.container.repository)
                        else DevSeeder.seedIfEmpty(app.container.repository)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}