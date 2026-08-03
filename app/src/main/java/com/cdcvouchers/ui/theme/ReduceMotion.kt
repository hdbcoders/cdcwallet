package com.cdcvouchers.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the user has asked the system to reduce/remove animations
 * (Settings > Accessibility > Remove animations).
 *
 * This Compose version (BOM 2026.06.01 / ui 1.11.4) does not expose
 * `LocalReduceMotion`, so we read the same platform signal the framework
 * uses: the global animator-duration scale, which Android sets to 0 when
 * "Remove animations" is enabled. Falls back to "animations allowed" if the
 * setting cannot be read (e.g. a restricted profile); the read is wrapped in
 * runCatching so it can never crash a composition.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}
