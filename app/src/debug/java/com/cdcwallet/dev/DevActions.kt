package com.cdcwallet.dev

import android.content.Context

/**
 * Shared action constants and persisted state for the debug-only dev tooling.
 * Lives under `src/debug` — never compiled into release builds.
 */
object DevActions {

    const val ACTION_SEED = "com.cdcwallet.action.SEED_DEV_DATA"
    const val ACTION_CLEAR = "com.cdcwallet.action.CLEAR_DEV_DATA"

    private const val PREFS = "dev_tooling"
    private const val KEY_AUTO_SEED = "auto_seed_enabled"

    /**
     * Whether [DebugVoucherApp] auto-seeds fixtures on launch. Default true;
     * CLEAR_DEV_DATA disables it (so the DB stays empty across relaunches),
     * SEED_DEV_DATA re-enables it.
     */
    fun autoSeedEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SEED, false)

    fun setAutoSeedEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SEED, enabled)
            .apply()
    }
}