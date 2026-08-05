package com.cdcvouchers.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.ui.theme.LocalAppIsDark
import com.cdcvouchers.ui.theme.SummaryCardContainerLight
import com.cdcvouchers.ui.theme.rememberReduceMotion
import kotlinx.coroutines.flow.collect

/**
 * Main voucher list (spec 04, 05). Owns the aggregate summary and the
 * Archive/Delete overflow menu (opened via the ⋮ button in the shared
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
    val reduceMotion = rememberReduceMotion()
    val sorted = remember(vouchers) { sortActive(vouchers) }
    val summary = remember(sorted) { summarizeActive(sorted) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is ListEvent.ArchivedUndo -> {
                    val result = snackbarHostState.showSnackbar(
                        "Archived",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
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
            AnimatedVisibility(
                // Entrance only: scale+fade the FAB in when the screen appears.
                visible = true,
                enter = if (reduceMotion) {
                    EnterTransition.None
                } else {
                    scaleIn(tween(220), initialScale = 0.85f) + fadeIn(tween(220))
                },
            ) {
                ExtendedFloatingActionButton(
                    onClick = onAddClick,
                    // Brand purple from the reference (Add-voucher) button.
                    containerColor = Color(0xFF5D3FD3),
                    contentColor = Color.White,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                    )
                    Text(
                        text = "Add Voucher",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Pinned summary: fixed above the list, never scrolls with it.
            SummaryCard(
                summary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                            modifier = if (reduceMotion) {
                                // System reduce-motion: disable item animations entirely.
                                Modifier.animateItem(
                                    fadeInSpec = null,
                                    placementSpec = null,
                                    fadeOutSpec = null,
                                )
                            } else {
                                Modifier.animateItem()
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
private fun SummaryCard(summary: ListSummary, modifier: Modifier = Modifier) {
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
        modifier = modifier.fillMaxWidth(),
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
