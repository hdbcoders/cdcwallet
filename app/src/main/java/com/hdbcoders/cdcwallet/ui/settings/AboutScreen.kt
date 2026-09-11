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
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.about_source_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // Resolved in composable scope (not inside the click
                    // lambda, which is not a composable context) and captured
                    // for the URL open.
                    val sourceLink = stringResource(R.string.about_source_link)
                    Text(
                        text = stringResource(R.string.about_source_link),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            // The click lambda is not a composable context, so
                            // the URL is resolved once at composition time and
                            // captured here - lint LocalContextGetResourceValueCall
                            // forbids context.getString reads in composables.
                            openUrl(context, sourceLink)
                        },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.about_privacy_policy),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = PRIVACY_POLICY_URL,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            openUrl(context, PRIVACY_POLICY_URL)
                        },
                    )
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

/** Opens a URL in the device browser (privacy policy / source code links). */
private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
