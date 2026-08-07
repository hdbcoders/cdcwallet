package com.cdcvouchers.ui.list

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdcvouchers.R
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.extraction.ExtractionCoordinator
import com.cdcvouchers.ui.theme.rememberReduceMotion

/**
 * Archived screen (spec 05 §5.4): same row layout and overflow pattern as the
 * main list, with Restore/Delete instead of Archive/Delete, and tap-to-open
 * retained — tapping an archived row opens the real URL in-app exactly like a
 * main-list tap (04 §4.4, 02 §2.7). Delete shares the one confirmation
 * dialog; empty state is a plain message, not a blank screen. State and DB
 * calls live in [ArchivedVoucherViewModel].
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
    val sorted by vm.vouchers.collectAsState()
    val reduceMotion = rememberReduceMotion()
    val clipboardManager = LocalClipboardManager.current
    // Toast on Copy URL (REQ-10 feedback): the activity context is wrapped
    // with the active app locale, so the message follows the app language.
    val context = LocalContext.current
    // Resolved at composition time (lint-clean locale-aware resolution); the
    // toast shows this string.
    val linkCopiedLabel = stringResource(R.string.link_copied)

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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                            text = { Text(stringResource(R.string.copy_url)) },
                            onClick = {
                                vm.setMenu(null)
                                clipboardManager.setText(AnnotatedString(voucher.url))
                                Toast.makeText(
                                    context,
                                    linkCopiedLabel,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.restore)) },
                            onClick = {
                                vm.setMenu(null)
                                vm.restore(voucher.id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
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
