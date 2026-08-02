package com.cdcvouchers.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdcvouchers.data.VoucherRepository

/**
 * Archived screen (spec 05 §5.4): same row layout and overflow pattern as the
 * main list, with Restore/Delete instead of Archive/Delete. Delete shares the
 * one confirmation dialog; empty state is a plain message, not a blank screen.
 * State and DB calls live in [ArchivedVoucherViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedVoucherScreen(
    repository: VoucherRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: ArchivedVoucherViewModel = viewModel(
        initializer = { ArchivedVoucherViewModel(repository) },
    )
    val sorted by vm.vouchers.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Archived") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (sorted.isEmpty()) {
            Text(
                text = "No archived vouchers yet",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(padding).padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sorted, key = { it.id }) { voucher ->
                    VoucherRow(
                        voucher = voucher,
                        menuExpanded = vm.menuForId == voucher.id,
                        onClick = {},
                        onMenuExpandedChange = { open ->
                            vm.setMenu(if (open) voucher.id else null)
                        },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Restore") },
                            onClick = {
                                vm.setMenu(null)
                                vm.restore(voucher.id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                vm.setMenu(null)
                                vm.requestDelete(voucher)
                            },
                        )
                    }
                }
            }
        }
    }

    vm.pendingDelete?.let { voucher ->
        DeleteVoucherDialog(
            onConfirm = {
                vm.dismissDelete()
                vm.delete(voucher.id)
            },
            onDismiss = { vm.dismissDelete() },
        )
    }
}
