package com.hdbcoders.cdcwallet.ui.add

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.addflow.AddVoucherFlow
import com.hdbcoders.cdcwallet.addflow.AddVoucherResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface AddUiStatus {
    data object Idle : AddUiStatus
    data object Working : AddUiStatus

    /** Localized message: a string resource id + format args (resolved by the
     *  screen via stringResource so the active app locale is used). When
     *  [campaignName] is set (the add-success message), the screen localizes
     *  it through the campaign glossary before formatting (refactor L8). */
    data class Message(
        @param:StringRes val resId: Int,
        val isError: Boolean,
        val formatArgs: List<Any> = emptyList(),
        val campaignName: String? = null,
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

    /** The active submit job - cancelled when a NEW url arrives, so a
     *  re-shared link reuses the add screen instead of stacking a second
     *  in-flight extraction (refactor M7, latest-add-wins). */
    private var submitJob: Job? = null

    /** The URL already submitted; re-delivery (rotation) is ignored, a NEW
     *  value resubmits. */
    private var submittedUrl: String? = null

    /** Called with the share-intent / pasted URL. Survives rotation (a
     *  re-delivered same URL is ignored, so a rotation never re-submits). */
    fun onUrlArrived(value: String) {
        if (submittedUrl == value) return
        submittedUrl = value
        url = value
        submit()
    }

    fun onUrlChange(value: String) { url = value }

    fun submit() {
        // Supersede any in-flight add: the previous extraction's hidden
        // WebView is torn down by the engine's cancellation path (02 §2.7).
        submitJob?.cancel()
        status = AddUiStatus.Working
        submitJob = viewModelScope.launch {
            status = try {
                when (val result = flow.add(appContext, url)) {
                    is AddVoucherResult.InvalidFormat ->
                        AddUiStatus.Message(R.string.add_invalid, isError = true)
                    is AddVoucherResult.Duplicate ->
                        AddUiStatus.Message(R.string.add_duplicate, isError = false)
                    is AddVoucherResult.Added ->
                        AddUiStatus.Message(
                            R.string.add_added,
                            isError = false,
                            campaignName = result.voucher.campaignName,
                        )
                    is AddVoucherResult.AddedUnverified ->
                        AddUiStatus.Message(R.string.add_added_unverified, isError = false)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Screen abandonment: the scope is tearing down, let it.
                throw e
            } catch (e: Exception) {
                // Refactor M3: every add must reach a terminal UI state - an
                // unexpected storage failure must never strand the screen in
                // Working.
                AddUiStatus.Message(R.string.add_failed_generic, isError = true)
            }
        }
    }
}
