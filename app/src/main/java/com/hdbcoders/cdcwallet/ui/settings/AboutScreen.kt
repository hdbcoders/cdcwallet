package com.hdbcoders.cdcwallet.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.hdbcoders.cdcwallet.BuildConfig
import com.hdbcoders.cdcwallet.R

/**
 * Hosted privacy-policy URL shown in the About page.
 *
 * Settled 2026-09-12: this is the permanent hosted address - it stays a GitHub
 * blob URL rather than moving to a dedicated policy page. The Play Console
 * privacy-policy field must point at this same address; if this constant ever
 * changes, update the Console field in the same release.
 *
 * Fragility worth knowing: it hard-codes org/repo/branch/path, no test covers
 * it, and a repo rename or a move of PRIVACY.md breaks the link silently.
 */
private const val PRIVACY_POLICY_URL = "https://github.com/hdbcoders/cdcwallet/blob/main/PRIVACY.md"

/**
 * Public source repository, shown as the About page's source link.
 *
 * A Kotlin constant next to [PRIVACY_POLICY_URL], not a string resource like
 * the prose around it: it is a URL, byte-identical in all four locales, and
 * living in the per-locale strings.xml files invited a translator to localise
 * it. Both URLs are now declared the same way, in the same place.
 */
private const val SOURCE_CODE_URL = "https://github.com/hdbcoders/cdcwallet"

/**
 * About App page (drawer → About App): a short description of the app
 * (hobby project, no data collection, internet only for the RedeemSG site,
 * AI-made non-English translations), credits for the AI contributors, a
 * link to the source code, and the app version pinned to the lower-right
 * corner. Reached from the navigation drawer's About entry; back arrow
 * returns to the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_app)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        // Refactor L9: scrollable content - long translations or the Huge
        // text setting scroll inside the screen instead of clipping. The
        // version line stays pinned to the lower-right corner (product
        // decision): the scroll column reserves bottom space so the last
        // content line can never hide behind it.
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 56.dp),
            ) {
                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.about_credits_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.about_credit_architecture),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.about_credit_design),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.about_credit_code),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.about_code_review),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.about_source_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinkText(url = SOURCE_CODE_URL) { openUrl(context, SOURCE_CODE_URL) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.about_privacy_policy),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinkText(url = PRIVACY_POLICY_URL) { openUrl(context, PRIVACY_POLICY_URL) }
                }
            }
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }
}

/**
 * A tappable URL line on the About page.
 *
 * Names the action via onClickLabel: without it TalkBack offers the bare
 * "double tap to activate" and reads out only the raw URL, with no hint of
 * what tapping does.
 *
 * NOTE - no link *role*. androidx.compose.ui.semantics.Role in the resolved
 * Compose UI (1.12.0) exposes Button, Checkbox, Switch, RadioButton, Tab,
 * Image, DropdownList, ValuePicker and Carousel, and no `Link`, so a link
 * cannot be declared as one here and `role = Role.Button` would actively
 * mislabel it. Announcing a real link needs an inline link annotation
 * (`buildAnnotatedString { withLink(...) }`), which also restyles the text -
 * a visual change that wants a device check, so it is deliberately left out.
 *
 * Extracted rather than repeated: the two links differed only in their URL,
 * and this semantics fix would otherwise have had to be applied - and kept -
 * in two places.
 */
@Composable
private fun LinkText(url: String, onClick: () -> Unit) {
    Text(
        text = url,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(
            onClickLabel = stringResource(R.string.about_open_link),
            onClick = onClick,
        ),
    )
}

/**
 * Opens a URL in the device browser (privacy policy / source code links).
 *
 * Fail-soft, matching [com.hdbcoders.cdcwallet.update.openPlayStore]: on a
 * device with no `ACTION_VIEW` handler (no browser installed - e.g. a
 * debloated ROM) the tap degrades to a no-op. Letting the
 * `ActivityNotFoundException` escape would crash the app out of a settings
 * link, which is the loudest possible way to break the fail-soft rule.
 *
 * Unlike `openPlayStore` the caller passes an Activity context, so no
 * `FLAG_ACTIVITY_NEW_TASK` is required here.
 */
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // No browser installed - degrade silently (AGENTS.md: fail soft).
    }
}
