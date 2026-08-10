package com.cdcwallet.ui.add

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdcwallet.R
import com.cdcwallet.addflow.AddVoucherFlow
import com.cdcwallet.addflow.AddVoucherResult
import kotlinx.coroutines.launch

sealed interface AddUiStatus {
    data object Idle : AddUiStatus
    data object Working : AddUiStatus

    /** Localized message: a string resource id + format args (resolved by the
     *  screen via stringResource so the active app locale is used). */
    data class Message(
        @StringRes val resId: Int,
        val isError: Boolean,
        val formatArgs: List<Any> = emptyList(),
    ) : AddUiStatus
}

class AddVoucherViewModel(
    private val flow: AddVoucherFlow,
    private val appContext: Context,
) : ViewModel() {

    var url by mutableStateOf("")
        private set
    var status by mutableStateOf<AddUiStatus>(AddUiStatus.Idle)
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

    fun submit() {
        if (status is AddUiStatus.Working) return
        status = AddUiStatus.Working
        viewModelScope.launch {
            status = when (val result = flow.add(appContext, url)) {
                is AddVoucherResult.InvalidFormat ->
                    AddUiStatus.Message(R.string.add_invalid, isError = true)
                is AddVoucherResult.Duplicate ->
                    AddUiStatus.Message(R.string.add_duplicate, isError = false)
                is AddVoucherResult.Added ->
                    AddUiStatus.Message(
                        R.string.add_added,
                        isError = false,
                        formatArgs = listOf(result.voucher.campaignName),
                    )
                is AddVoucherResult.AddedUnverified ->
                    AddUiStatus.Message(R.string.add_added_unverified, isError = false)
            }
        }
    }
}
