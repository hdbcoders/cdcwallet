package com.hdbcoders.cdcwallet.update

import android.content.Context

/**
 * Persistence seam for the update check state (REQ-13): the last-check
 * timestamp (24h throttle) and the persisted "update available" flag together
 * with the installed versionCode the flag was set at (so a stale flag is
 * cleared when the app itself updates). The interface keeps the checker
 * logic unit-testable without Android; [PrefsUpdateCheckStore] is the
 * production SharedPreferences implementation.
 */
interface UpdateCheckStore {
    /** 0 when never checked. */
    fun lastCheckMs(): Long

    fun stampLastCheck(now: Long)

    /** True while a "new version available" flag is persisted. */
    fun isFlagged(): Boolean

    /** The installed versionCode the flag was set at; -1 when never flagged. */
    fun flagVersionCode(): Int

    fun setFlagged(installedVersionCode: Int)

    fun clearFlagged()
}

class PrefsUpdateCheckStore(context: Context) : UpdateCheckStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun lastCheckMs(): Long = prefs.getLong(KEY_LAST_CHECK, 0L)

    override fun stampLastCheck(now: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
    }

    override fun isFlagged(): Boolean = prefs.getBoolean(KEY_FLAGGED, false)

    override fun flagVersionCode(): Int = prefs.getInt(KEY_FLAG_VERSION, -1)

    override fun setFlagged(installedVersionCode: Int) {
        prefs.edit()
            .putBoolean(KEY_FLAGGED, true)
            .putInt(KEY_FLAG_VERSION, installedVersionCode)
            .apply()
    }

    override fun clearFlagged() {
        prefs.edit()
            .remove(KEY_FLAGGED)
            .remove(KEY_FLAG_VERSION)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "voucher_update_prefs"
        const val KEY_LAST_CHECK = "last_check_ms"
        const val KEY_FLAGGED = "update_available"
        const val KEY_FLAG_VERSION = "flag_version_code"
    }
}
