package com.cdcvouchers.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cdcvouchers.addflow.AddVoucherFlow
import com.cdcvouchers.addflow.AddVoucherResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private sealed interface AddStatus {
    data object Idle : AddStatus
    data object Working : AddStatus
    data class Message(val text: String, val isError: Boolean) : AddStatus
}

/**
 * Paste/share add screen (spec 03). Runs the exact add sequence and cancels any
 * in-flight fetch when the composable leaves composition (03 §3.3) — the flow's
 * suspend chain aborts, no row is inserted, and Package 2 tears down the hidden
 * WebView.
 */
@Composable
fun AddVoucherScreen(
    flow: AddVoucherFlow,
    initialUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<AddStatus>(AddStatus.Idle) }
    var autoSubmitted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    fun submit(input: String) {
        status = AddStatus.Working
        scope.launch {
            val result = flow.add(context, input)
            status = when (result) {
                is AddVoucherResult.InvalidFormat ->
                    AddStatus.Message("This doesn't look like a RedeemSG voucher link.", isError = true)
                is AddVoucherResult.Duplicate ->
                    AddStatus.Message("This link is already in your list.", isError = false)
                is AddVoucherResult.Added ->
                    AddStatus.Message("Added — ${result.voucher.campaignName}", isError = false)
                is AddVoucherResult.AddedUnverified ->
                    AddStatus.Message("Added — couldn't verify it yet; tap the entry to check later.", isError = false)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (initialUrl != null && !autoSubmitted) {
            autoSubmitted = true
            url = initialUrl
            submit(initialUrl)
        }
    }

    // rememberCoroutineScope is cancelled when this composable leaves
    // composition, which aborts any in-flight add (03 §3.3): no row is
    // inserted from an abandoned fetch and the hidden WebView is torn down.

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Add a voucher link",
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Paste voucher link") },
            singleLine = true,
            enabled = status !is AddStatus.Working,
        )
        Button(
            onClick = { submit(url) },
            modifier = Modifier.fillMaxWidth(),
            enabled = url.isNotBlank() && status !is AddStatus.Working,
        ) {
            Text("Add")
        }
        when (val s = status) {
            is AddStatus.Working -> CircularProgressIndicator()
            is AddStatus.Message -> Text(
                text = s.text,
                color = if (s.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            is AddStatus.Idle -> {}
        }
    }
}
