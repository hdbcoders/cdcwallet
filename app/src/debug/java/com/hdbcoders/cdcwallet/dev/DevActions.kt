package com.hdbcoders.cdcwallet.dev

import android.content.Context

/**
 * Shared action constants and persisted state for the debug-only dev tooling.
 * Lives under `src/debug` - never compiled into release builds.
 */
object DevActions {

    const val ACTION_SEED = "com.hdbcoders.cdcwallet.action.SEED_DEV_DATA"
    const val ACTION_CLEAR = "com.hdbcoders.cdcwallet.action.CLEAR_DEV_DATA"

    /**
     * REQ-13 update-check simulation: makes the next update check report
     * "update available", so the "!" badge, the "Tap to update" menu slot and
     * the update popup can be exercised on a debug build without a real Play
     * track. CLEAR removes the override AND the flag.
     */
    const val ACTION_SIMULATE_UPDATE = "com.hdbcoders.cdcwallet.action.SIMULATE_UPDATE_AVAILABLE"
    const val ACTION_CLEAR_UPDATE_SIM = "com.hdbcoders.cdcwallet.action.CLEAR_UPDATE_SIMULATION"

    private const val PREFS = "dev_tooling"
    private const val KEY_AUTO_SEED = "auto_seed_enabled"

    /**
     * Whether [DebugVoucherApp] auto-seeds fixtures on launch. Default true
     * (refactor D1): a fresh debug install is seeded with no extra step;
     * CLEAR_DEV_DATA disables it (so the DB stays empty across relaunches),
     * SEED_DEV_DATA re-enables it.
     */
    fun autoSeedEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SEED, true)

    fun setAutoSeedEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SEED, enabled)
            .apply()
    }
}