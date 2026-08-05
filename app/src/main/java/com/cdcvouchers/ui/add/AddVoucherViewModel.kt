package com.cdcvouchers.ui.add

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdcvouchers.addflow.AddVoucherFlow
import com.cdcvouchers.addflow.AddVoucherResult
import kotlinx.coroutines.launch

sealed interface AddUiStatus {
    data object Idle : AddUiStatus
    data object Working : AddUiStatus
    data class Message(val text: String, val isError: Boolean) : AddUiStatus
}

/**
 * Set once when the submitted link duplicates an existing entry (spec 03 §3.2
 * step 2). The screen surfaces this to navigation so the app can return to the
 * list and scroll/highlight the existing voucher.
 */
sealed interface AddUiEvent {
    data class Duplicate(val voucherId: String) : AddUiEvent
}

class AddVoucherViewModel(
    private val flow: AddVoucherFlow,
    private val appContext: Context,
) : ViewModel() {

    var url by mutableStateOf("")
        private set
    var status by mutableStateOf<AddUiStatus>(AddUiStatus.Idle)
        private set
    var duplicateEvent by mutableStateOf<AddUiEvent?>(null)
        private set

    private var autoSubmitted = false

    /** Called once from LaunchedEffect with the share intent URL. Survives
     *  rotation (the flag lives in the VM), so a rotation never re-submits. */
    fun setInitialUrl(value: String) {
        if (autoSubmitted) return
        autoSubmitted = true
        url = value
        submit()
    }

    fun onUrlChange(value: String) { url = value }

    /** Clear the duplicate event after navigation has consumed it. */
    fun consumeDuplicate() { duplicateEvent = null }

    fun submit() {
        if (status is AddUiStatus.Working) return
        status = AddUiStatus.Working
        viewModelScope.launch {
            status = when (val result = flow.add(appContext, url)) {
                is AddVoucherResult.InvalidFormat ->
                    AddUiStatus.Message("This doesn't look like a RedeemSG voucher link.", isError = true)
                is AddVoucherResult.Duplicate -> {
                    duplicateEvent = AddUiEvent.Duplicate(result.existing.id)
                    AddUiStatus.Message("This link is already in your list.", isError = false)
                }
                is AddVoucherResult.Added ->
                    AddUiStatus.Message("Added — ${result.voucher.campaignName}", isError = false)
                is AddVoucherResult.AddedUnverified ->
                    AddUiStatus.Message("Added — couldn't verify it yet; tap the entry to check later.", isError = false)
            }
        }
    }
}
