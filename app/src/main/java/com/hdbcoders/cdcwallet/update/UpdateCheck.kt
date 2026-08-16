package com.hdbcoders.cdcwallet.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Outcome of an update-availability query (REQ-13). [Available] is the only
 * state that ever surfaces in the UI: [NotAvailable] and [Failed] are both
 * silent (fail-soft), but kept distinct so tests can assert the mapping.
 */
sealed interface UpdateCheckResult {
    data object Available : UpdateCheckResult
    data object NotAvailable : UpdateCheckResult
    data object Failed : UpdateCheckResult
}

/**
 * Seam around "is a newer version on the Play Store?" - the real source goes
 * through Play's In-App Updates API (a Play Services binder call, never a
 * network request from the app). Tests inject a fake source.
 */
fun interface UpdateAvailabilitySource {
    suspend fun check(): UpdateCheckResult
}

/**
 * Production source (REQ-13): queries [AppUpdateManagerFactory]'s
 * `appUpdateInfo`. Sideloaded installs and devices without Play Services
 * report not-available (or throw) - both degrade silently, per fail-soft.
 */
class PlayUpdateAvailabilitySource(context: Context) : UpdateAvailabilitySource {

    private val manager by lazy { AppUpdateManagerFactory.create(context.applicationContext) }

    override suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            // Tasks.await blocks the calling thread; we're on IO - never the
            // main thread (cross-cutting threading rule).
            val info = Tasks.await(manager.appUpdateInfo)
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                UpdateCheckResult.Available
            } else {
                UpdateCheckResult.NotAvailable
            }
        } catch (e: Exception) {
            // No Play Store, sideloaded install, transient binder error -
            // the update state is untouched and the app keeps working.
            UpdateCheckResult.Failed
        }
    }
}

/** Pure throttle decision: check when never checked, or the interval elapsed. */
internal fun isCheckDue(lastCheckMs: Long, now: Long, intervalMs: Long): Boolean =
    lastCheckMs == 0L || now - lastCheckMs >= intervalMs

/** Pure stale-flag decision: the flag described an OLDER installed version. */
internal fun shouldClearStaleFlag(flagged: Boolean, flagVersionCode: Int, installedVersionCode: Int): Boolean =
    flagged && flagVersionCode != installedVersionCode

/**
 * The app's Play Store listing URIs as strings (pure - unit-testable; the
 * Android [Uri] parsing happens only in [openPlayStore]). `market://` first
 * (opens the Play Store app); the https fallback is used when no handler
 * exists (e.g. some emulators / non-Play devices).
 */
fun playStoreMarketUri(packageName: String): String =
    "market://details?id=$packageName"

fun playStoreWebUri(packageName: String): String =
    "https://play.google.com/store/apps/details?id=$packageName"

/**
 * Opens the Play Store page for [packageName], falling back from `market://`
 * to the https listing. Fail-soft: if nothing can handle either URI (no Play
 * Store on the device), the tap is silently ignored - never a crash.
 *
 * Both intents carry [Intent.FLAG_ACTIVITY_NEW_TASK]: the caller passes the
 * application context (never an Activity), and starting an activity from a
 * non-Activity context throws `AndroidRuntimeException` without that flag -
 * which the fail-soft catch would swallow, making the tap a no-op.
 */
fun openPlayStore(context: Context, packageName: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(playStoreMarketUri(packageName)))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(playStoreWebUri(packageName)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(fallback)
        } catch (e2: Exception) {
            // No browser either - degrade silently.
        }
    }
}
