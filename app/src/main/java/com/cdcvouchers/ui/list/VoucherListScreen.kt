package com.cdcvouchers.ui.list

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.model.VoucherGroup
import kotlinx.coroutines.launch

/**
 * Main voucher list (spec 04, 05). Owns the aggregate summary and the
 * Archive/Delete overflow menu (long-press and ⋮ both open it, via the shared
 * VoucherRow); the Archived screen is reachable from the persistent app-bar
 * entry.
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
    val vouchers by repository.observeActive().collectAsState(initial = emptyList())
    val archivedCount by repository.observeArchived().collectAsState(initial = emptyList())
    val sorted = remember(vouchers) { sortActive(vouchers) }
    val summary = remember(sorted) { summarizeActive(sorted) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<VoucherGroup?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Voucher Links") },
                actions = {
                    TextButton(onClick = onArchivedClick) {
                        Text("Archived (${archivedCount.size})")
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
                        menuExpanded = menuFor == voucher.id,
                        onClick = { onOpenVoucher(voucher) },
                        onMenuExpandedChange = { open ->
                            menuFor = if (open) voucher.id else null
                        },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            onClick = {
                                menuFor = null
                                scope.launch {
                                    repository.archive(voucher.id)
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Archived",
                                        actionLabel = "Undo",
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        repository.restore(voucher.id)
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuFor = null
                                pendingDelete = voucher
                            },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { voucher ->
        DeleteVoucherDialog(
            onConfirm = {
                pendingDelete = null
                scope.launch { repository.delete(voucher.id) }
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun SummaryCard(summary: ListSummary) {
    val containerColor =
        if (isSystemInDarkTheme()) Color(0xFF1D2B53) else Color(0xFFD9E7FF)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("S$", style = MaterialTheme.typography.labelLarge)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Summary",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(summaryHeadline(summary), style = MaterialTheme.typography.titleMedium)
                if (summary.categoryTotals.isNotEmpty()) {
                    Text(
                        text = summary.categoryTotals.joinToString(" · ") {
                            "${formatSgd(it.remainingValue)} ${it.category}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
