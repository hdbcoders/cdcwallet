package com.cdcwallet.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdcwallet.R
import com.cdcwallet.addflow.AddVoucherFlow
import com.cdcwallet.ui.theme.LocalAppLanguage
import com.cdcwallet.ui.theme.localizeCampaignName
import kotlinx.coroutines.delay

/**
 * Paste/share add screen (spec 03). Runs the exact add sequence; the in-flight
 * fetch is cancelled when the destination leaves the back stack
 * (viewModelScope teardown, 03 §3.3): no row is inserted from an abandoned
 * fetch and Package 2 tears down the hidden WebView.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVoucherScreen(
    flow: AddVoucherFlow,
    initialUrl: String? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: AddVoucherViewModel = viewModel(
        initializer = { AddVoucherViewModel(flow, context.applicationContext) },
    )
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(initialUrl) {
        initialUrl?.let { vm.onUrlArrived(it) }
        // Auto-activate the paste field on arrival so the user can type or
        // paste without an extra tap. Skipped when a share intent already
        // pre-filled the URL - there the user only needs to confirm, so we
        // don't pop the keyboard over the Add button. A single requestFocus
        // can be dropped while the node/window is still mounting, so retry
        // briefly (mirrors DeleteVoucherDialog / BackupPasswordDialog).
        if (initialUrl == null) {
            repeat(10) {
                focusRequester.requestFocus()
                delay(50)
            }
            keyboardController?.show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = vm.url,
                onValueChange = vm::onUrlChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                label = { Text(stringResource(R.string.paste_link)) },
                singleLine = true,
                enabled = vm.status !is AddUiStatus.Working,
            )
            Button(
                onClick = vm::submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = vm.url.isNotBlank() && vm.status !is AddUiStatus.Working,
            ) {
                Text(stringResource(R.string.add))
            }
            when (val s = vm.status) {
                is AddUiStatus.Working -> CircularProgressIndicator()
                is AddUiStatus.Message -> Text(
                    // Refactor L8: the campaign name in the add-success
                    // message goes through the same glossary localization as
                    // the cards and detail before it is formatted in.
                    text = s.campaignName?.let {
                        stringResource(s.resId, localizeCampaignName(it, LocalAppLanguage.current))
                    } ?: stringResource(s.resId, *s.formatArgs.toTypedArray()),
                    color = if (s.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                is AddUiStatus.Idle -> {}
            }
        }
    }
}
