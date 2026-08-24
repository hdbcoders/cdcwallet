package com.hdbcoders.cdcwallet.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.data.VoucherRepository
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.extraction.ExtractionCoordinator
import com.hdbcoders.cdcwallet.ui.components.CopyUrlEffect
import com.hdbcoders.cdcwallet.ui.components.RowMenuMiddleAction
import com.hdbcoders.cdcwallet.ui.components.TicketCard
import com.hdbcoders.cdcwallet.ui.components.VoucherRowActions
import com.hdbcoders.cdcwallet.ui.components.VoucherRowMenuContent
import com.hdbcoders.cdcwallet.ui.theme.rememberReduceMotion

/**
 * Archived screen (spec 05 §5.4): same ticket-row layout and overflow pattern
 * as the main list (restyled alongside it), with Restore/Delete instead of
 * Archive/Delete, and tap-to-open retained - tapping an archived row opens the
 * real URL in-app exactly like a main-list tap (04 §4.4, 02 §2.7). Delete
 * shares the one confirmation dialog; empty state is a plain message, not a
 * blank screen. State and DB calls live in [ArchivedVoucherViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedVoucherScreen(
    repository: VoucherRepository,
    extractionCoordinator: ExtractionCoordinator,
    onOpenVoucher: (VoucherGroup) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: ArchivedVoucherViewModel = viewModel(
        initializer = { ArchivedVoucherViewModel(repository, extractionCoordinator) },
    )
    val sorted by vm.vouchers.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotion()
    // Copy-URL effect (refactor M16 + L11) - see VoucherListScreen.
    CopyUrlEffect(copyRequest = vm.copyRequest, onConsumed = { vm.consumeCopyRequest() })

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archived)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (sorted.isEmpty()) {
            Text(
                text = stringResource(R.string.no_archived),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(padding).padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sorted, key = { it.id }) { voucher ->
                    TicketCard(
                        voucher = voucher,
                        menuExpanded = vm.menuForId == voucher.id,
                        onClick = { onOpenVoucher(voucher) },
                        onMenuExpandedChange = { open ->
                            vm.setMenu(if (open) voucher.id else null)
                        },
                        // Row-level custom a11y/agent actions mirror this row's
                        // kebab menu: Restore + Delete (no pin slot on the
                        // archived screen, matching VoucherRowMenuContent).
                        rowActions = VoucherRowActions(
                            isPinned = false,
                            middleLabelRes = R.string.restore,
                            onPinClick = {},
                            onMiddleClick = { vm.restore(voucher.id) },
                            onDelete = { vm.requestDelete(voucher) },
                        ),
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
                        VoucherRowMenuContent(
                            onCopyUrl = {
                                vm.setMenu(null)
                                vm.requestCopy(voucher.url)
                            },
                            middleAction = RowMenuMiddleAction.RESTORE,
                            onMiddleClick = {
                                vm.setMenu(null)
                                vm.restore(voucher.id)
                            },
                            onDelete = {
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
