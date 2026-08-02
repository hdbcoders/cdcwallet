package com.cdcvouchers.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.model.VoucherGroup
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ListEvent {
    data class ArchivedUndo(val voucherId: String) : ListEvent
}

class VoucherListViewModel(
    private val repository: VoucherRepository,
) : ViewModel() {

    val vouchers: StateFlow<List<VoucherGroup>> = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archivedCount: StateFlow<Int> = repository.observeArchivedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    var menuForId by mutableStateOf<String?>(null)
        private set
    var pendingDelete by mutableStateOf<VoucherGroup?>(null)
        private set

    private val _events = Channel<ListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setMenu(id: String?) { menuForId = id }

    fun requestDelete(voucher: VoucherGroup) { pendingDelete = voucher }
    fun dismissDelete() { pendingDelete = null }

    fun archive(voucher: VoucherGroup) {
        viewModelScope.launch {
            repository.archive(voucher.id)
            _events.send(ListEvent.ArchivedUndo(voucher.id))
        }
    }

    fun restore(id: String) { viewModelScope.launch { repository.restore(id) } }
    fun delete(id: String) { viewModelScope.launch { repository.delete(id) } }
}
