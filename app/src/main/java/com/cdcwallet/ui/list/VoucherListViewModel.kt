package com.cdcwallet.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdcwallet.data.VoucherRepository
import com.cdcwallet.data.model.VoucherGroup
import com.cdcwallet.extraction.ExtractionCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoucherListViewModel(
    private val repository: VoucherRepository,
    private val extractionCoordinator: ExtractionCoordinator,
) : ViewModel() {

    val vouchers: StateFlow<List<VoucherGroup>> = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * True once the repository's active-voucher flow has emitted its first
     * (real) value - distinguishes "DB still loading/decrypting" (splash shown)
     * from "DB is empty" (empty list is a valid loaded state).
     */
    val isLoaded: StateFlow<Boolean> = repository.observeActive()
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val archivedCount: StateFlow<Int> = repository.observeArchivedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    var menuForId by mutableStateOf<String?>(null)
        private set
    var pendingDelete by mutableStateOf<VoucherGroup?>(null)
        private set

    fun setMenu(id: String?) { menuForId = id }

    fun requestDelete(voucher: VoucherGroup) { pendingDelete = voucher }
    fun dismissDelete() { pendingDelete = null }

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
