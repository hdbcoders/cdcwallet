package com.hdbcoders.cdcwallet.dev

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hdbcoders.cdcwallet.VoucherApp
import com.hdbcoders.cdcwallet.update.UpdateAvailabilitySource
import com.hdbcoders.cdcwallet.update.UpdateCheckResult

/**
 * Debug-only receiver for the REQ-13 update-check simulation (declared
 * exported in the debug manifest, registered dynamically by DebugVoucherApp):
 *
 *   # Next check reports "update available" (badge + Tap to update + popup):
 *   adb shell am broadcast -a com.hdbcoders.cdcwallet.action.SIMULATE_UPDATE_AVAILABLE
 *
 *   # Remove the override and clear the flag back to "no update":
 *   adb shell am broadcast -a com.hdbcoders.cdcwallet.action.CLEAR_UPDATE_SIMULATION
 *
 * Release builds never contain this code (src/debug only).
 */
class UpdateDevReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VoucherApp
        val checker = app.container.updateChecker
        when (intent.action) {
            DevActions.ACTION_SIMULATE_UPDATE -> {
                checker.debugSourceOverride = UpdateAvailabilitySource {
                    UpdateCheckResult.Available
                }
            }
            DevActions.ACTION_CLEAR_UPDATE_SIM -> {
                checker.debugSourceOverride = null
                checker.clearDebugFlag()
            }
        }
    }
}
