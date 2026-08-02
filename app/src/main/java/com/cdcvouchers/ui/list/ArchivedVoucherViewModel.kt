package com.cdcvouchers.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.model.VoucherGroup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArchivedVoucherViewModel(
    private val repository: VoucherRepository,
) : ViewModel() {

    val vouchers: StateFlow<List<VoucherGroup>> = repository.observeArchived()
        .map { sortActive(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var menuForId by mutableStateOf<String?>(null)
        private set
    var pendingDelete by mutableStateOf<VoucherGroup?>(null)
        private set

    fun setMenu(id: String?) { menuForId = id }
    fun requestDelete(voucher: VoucherGroup) { pendingDelete = voucher }
    fun dismissDelete() { pendingDelete = null }
    fun restore(id: String) { viewModelScope.launch { repository.restore(id) } }
    fun delete(id: String) { viewModelScope.launch { repository.delete(id) } }
}
