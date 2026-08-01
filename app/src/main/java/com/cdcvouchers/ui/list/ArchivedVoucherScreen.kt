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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.model.VoucherGroup
import kotlinx.coroutines.launch

/**
 * Archived screen (spec 05 §5.4): same row layout and overflow pattern as the
 * main list, with Restore/Delete instead of Archive/Delete. Delete shares the
 * one confirmation dialog; empty state is a plain message, not a blank screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedVoucherScreen(
    repository: VoucherRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val archived by repository.observeArchived().collectAsState(initial = emptyList())
    val sorted = remember(archived) { sortActive(archived) }
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<VoucherGroup?>(null) }

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
                        menuExpanded = menuFor == voucher.id,
                        onClick = {},
                        onMenuExpandedChange = { open ->
                            menuFor = if (open) voucher.id else null
                        },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Restore") },
                            onClick = {
                                menuFor = null
                                scope.launch { repository.restore(voucher.id) }
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
