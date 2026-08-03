package com.cdcvouchers.ui.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.ui.theme.LocalAppIsDark
import com.cdcvouchers.ui.theme.SummaryCardContainerLight
import kotlinx.coroutines.flow.collect

/**
 * Main voucher list (spec 04, 05). Owns the aggregate summary and the
 * Archive/Delete overflow menu (long-press and ⋮ both open it, via the shared
 * VoucherRow); the Archived screen is reachable from the persistent app-bar
 * entry. State and DB calls live in [VoucherListViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherListScreen(
    repository: VoucherRepository,
    onAddClick: () -> Unit,
    onOpenVoucher: (VoucherGroup) -> Unit,
    onArchivedClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: VoucherListViewModel = viewModel(
        initializer = { VoucherListViewModel(repository) },
    )
    val vouchers by vm.vouchers.collectAsState()
    val archivedCount by vm.archivedCount.collectAsState()
    val sorted = remember(vouchers) { sortActive(vouchers) }
    val summary = remember(sorted) { summarizeActive(sorted) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is ListEvent.ArchivedUndo -> {
                    val result = snackbarHostState.showSnackbar("Archived", actionLabel = "Undo")
                    if (result == SnackbarResult.ActionPerformed) vm.restore(event.voucherId)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Voucher Links") },
                actions = {
                    TextButton(onClick = onArchivedClick) {
                        Text("Archived ($archivedCount)")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add voucher")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary") {
                SummaryCard(summary)
            }
            if (sorted.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No voucher links yet — add one with the + button.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(sorted, key = { it.id }) { voucher ->
                    VoucherRow(
                        voucher = voucher,
                        menuExpanded = vm.menuForId == voucher.id,
                        onClick = { onOpenVoucher(voucher) },
                        onMenuExpandedChange = { open ->
                            vm.setMenu(if (open) voucher.id else null)
                        },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            onClick = {
                                vm.setMenu(null)
                                vm.archive(voucher)
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

@Composable
private fun SummaryCard(summary: ListSummary) {
    val dark = LocalAppIsDark.current
    val containerColor = if (dark) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        SummaryCardContainerLight
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = if (dark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = summaryHeadline(summary),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (summary.categoryTotals.isNotEmpty()) {
                    Text(
                        text = summary.categoryTotals.joinToString(" · ") {
                            "${formatSgd(it.remainingValue)} ${it.category}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
