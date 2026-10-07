package com.hdbcoders.cdcwallet.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hdbcoders.cdcwallet.data.VoucherRepository
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.extraction.ExtractionCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoucherListViewModel(
    private val repository: VoucherRepository,
    private val extractionCoordinator: ExtractionCoordinator,
) : ViewModel() {

    val vouchers: StateFlow<List<VoucherGroup>> = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archivedCount: StateFlow<Int> = repository.observeArchivedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    var menuForId by mutableStateOf<String?>(null)
        private set
    var pendingDelete by mutableStateOf<VoucherGroup?>(null)
        private set

    /**
     * Second-pin confirmation gate: set when the user picks Pin while another
     * voucher is already pinned. The screen renders the dialog from this; the
     * swap happens only on explicit confirm.
     */
    var pendingPin by mutableStateOf<VoucherGroup?>(null)
        private set

    /**
     * One-shot Copy-URL request (refactor M16): the ACTION lives in the
     * ViewModel; the screen executes clipboard + toast as an effect and
     * consumes the request.
     */
    var copyRequest by mutableStateOf<String?>(null)
        private set

    /**
     * One-shot scroll request (theme-era UX addition): set after a successful
     * pin or confirmed pin swap so the screen can bring the freshly pinned
     * row (position 0 after sortActive) back into view. Unpin does NOT set
     * this - the user stays where they are. The screen consumes it after
     * scrolling.
     */
    var scrollToPinned by mutableStateOf(false)
        private set

    fun setMenu(id: String?) { menuForId = id }

    fun requestCopy(url: String) { copyRequest = url }

    fun consumeCopyRequest() { copyRequest = null }

    fun consumeScrollToPinned() { scrollToPinned = false }

    fun requestDelete(voucher: VoucherGroup) { pendingDelete = voucher }
    fun dismissDelete() { pendingDelete = null }

    /**
     * Pin request from a row's kebab. If another voucher is already pinned,
     * stage the swap behind the confirmation gate; otherwise pin immediately.
     */
    fun requestPin(voucher: VoucherGroup) {
        val alreadyPinned = vouchers.value.any { it.isPinned && it.id != voucher.id }
        // Close the menu in BOTH paths: the dialog must not compete with a
        // still-open dropdown for focus/matchers.
        setMenu(null)
        if (alreadyPinned) {
            pendingPin = voucher
        } else {
            scrollToPinned = true
            viewModelScope.launch { repository.setPinned(voucher.id, true) }
        }
    }

    /** Confirmed swap: unpin the current pin, pin the newly chosen row. */
    fun confirmPinSwap() {
        val target = pendingPin ?: return
        pendingPin = null
        setMenu(null)
        scrollToPinned = true
        viewModelScope.launch {
            repository.setPinned(target.id, true) // repository unpins the old pin atomically
        }
    }

    fun dismissPinSwap() { pendingPin = null }

    fun unpin(id: String) {
        setMenu(null)
        viewModelScope.launch { repository.setPinned(id, false) }
    }

    fun archive(voucher: VoucherGroup) {
        viewModelScope.launch {
            repository.archive(voucher.id)
        }
    }

    fun restore(id: String) { viewModelScope.launch { repository.restore(id) } }

    fun delete(id: String) {
        // Cancel any in-flight extraction for this voucher first (02 §2.7),
        // then remove the row.
        extractionCoordinator.cancel(id)
        viewModelScope.launch { repository.delete(id) }
    }
}
