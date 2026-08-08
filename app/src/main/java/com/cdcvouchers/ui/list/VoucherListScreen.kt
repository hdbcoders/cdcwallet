package com.cdcvouchers.ui.list

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.cdcvouchers.ui.components.AppHeader
import com.cdcvouchers.ui.components.BalanceHero
import com.cdcvouchers.ui.components.DashedAddRow
import com.cdcvouchers.ui.components.TicketCard
import com.cdcvouchers.ui.theme.AppLanguage
import com.cdcvouchers.ui.theme.LanguageStore
import com.cdcvouchers.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Main voucher list (spec 04, 05) in the redesign: custom header (hamburger
 * dropdown + language toggle + Archived pill), the fixed gold BalanceHero,
 * ticket-style rows with the ⋮ overflow menu, and a dashed "Add Voucher" row
 * at the bottom. Behavior is unchanged from the previous Material3 chrome:
 * sorting, badge states, duplicate-add highlight scroll, archive undo
 * snackbar, and the language picker (now the header's dropdown).
 */
@Composable
fun VoucherListScreen(
    repository: VoucherRepository,
    extractionCoordinator: ExtractionCoordinator,
    onAddClick: () -> Unit,
    onOpenVoucher: (VoucherGroup) -> Unit,
    onArchivedClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    languageStore: LanguageStore,
    onLanguageSelected: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
    highlightVoucherId: String? = null,
    onHighlightConsumed: () -> Unit = {},
) {
    val vm: VoucherListViewModel = viewModel(
        initializer = { VoucherListViewModel(repository, extractionCoordinator) },
    )
    val vouchers by vm.vouchers.collectAsState()
    val archivedCount by vm.archivedCount.collectAsState()
    val reduceMotion = rememberReduceMotion()
    val sorted = remember(vouchers) { sortActive(vouchers) }
    val summary = remember(sorted) { summarizeActive(sorted) }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    // Toast on Copy URL (REQ-10 feedback): the activity context is wrapped
    // with the active app locale, so the message follows the app language.
    val context = LocalContext.current
    val linkCopiedLabel = stringResource(R.string.link_copied)
    val archivedLabel = stringResource(R.string.archived)
    val undoLabel = stringResource(R.string.undo)
    // Scroll + flash the row requested by a duplicate-add (spec 03 §3.2 step 2).
    val listState = rememberLazyListState()
    var highlightedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(highlightVoucherId, sorted) {
        val target = highlightVoucherId ?: return@LaunchedEffect
        val index = sorted.indexOfFirst { it.id == target }
        if (index >= 0) {
            highlightedId = target
            listState.animateScrollToItem(index)
            onHighlightConsumed()
            // Brief flash, then clear the highlight.
            delay(1800)
            highlightedId = null
        } else {
            // Row not present (e.g. archived) — nothing to highlight.
            onHighlightConsumed()
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is ListEvent.ArchivedUndo -> {
                    val result = snackbarHostState.showSnackbar(
                        archivedLabel,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.restore(event.voucherId)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                currentLanguage = languageStore.language,
                onLanguageSelected = onLanguageSelected,
                archivedCount = archivedCount,
                onArchivedClick = onArchivedClick,
                onSettingsClick = onSettingsClick,
                onAboutClick = onAboutClick,
            )
            BalanceHero(
                summary = summary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (sorted.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.empty_list),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                } else {
                    items(sorted, key = { it.id }) { voucher ->
                        TicketCard(
                            voucher = voucher,
                            menuExpanded = vm.menuForId == voucher.id,
                            isHighlighted = voucher.id == highlightedId,
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
                                text = { Text(stringResource(R.string.archive)) },
                                onClick = {
                                    vm.setMenu(null)
                                    vm.archive(voucher)
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
                item(key = "add-row") {
                    AnimatedVisibility(
                        visible = true,
                        enter = if (reduceMotion) {
                            EnterTransition.None
                        } else {
                            scaleIn(tween(220), initialScale = 0.92f) + fadeIn(tween(220))
                        },
                    ) {
                        DashedAddRow(
                            onClick = onAddClick,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        )
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
