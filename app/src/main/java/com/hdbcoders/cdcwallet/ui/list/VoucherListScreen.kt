package com.hdbcoders.cdcwallet.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.data.VoucherRepository
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.extraction.ExtractionCoordinator
import com.hdbcoders.cdcwallet.ui.components.AppHeader
import com.hdbcoders.cdcwallet.ui.components.BalanceHero
import com.hdbcoders.cdcwallet.ui.components.CopyUrlEffect
import com.hdbcoders.cdcwallet.ui.components.DashedAddRow
import com.hdbcoders.cdcwallet.ui.components.RowMenuMiddleAction
import com.hdbcoders.cdcwallet.ui.components.TicketCard
import com.hdbcoders.cdcwallet.ui.components.VoucherRowMenuContent
import com.hdbcoders.cdcwallet.ui.theme.AppLanguage
import com.hdbcoders.cdcwallet.ui.theme.LanguageStore
import com.hdbcoders.cdcwallet.ui.theme.rememberReduceMotion

/**
 * Main voucher list (spec 04, 05) in the redesign: custom header (hamburger
 * dropdown + language toggle + Archived pill), the fixed gold BalanceHero,
 * ticket-style rows with the ⋮ overflow menu, and a dashed "Add Voucher" row
 * at the bottom. Behavior is unchanged from the previous Material3 chrome:
 * sorting, badge states, and the language
 * picker (now the header's dropdown).
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
    onAccessibilityClick: () -> Unit = {},
    languageStore: LanguageStore,
    onLanguageSelected: (AppLanguage) -> Unit,
    updateAvailable: Boolean = false,
    onCheckForUpdate: () -> Unit = {},
    onTapToUpdate: () -> Unit = {},
    modifier: Modifier = Modifier,
    onToggleTheme: () -> Unit = {},
    heroCollapsed: Boolean = false,
    onToggleHeroCollapsed: () -> Unit = {},
) {
    val vm: VoucherListViewModel = viewModel(
        initializer = { VoucherListViewModel(repository, extractionCoordinator) },
    )
    val vouchers by vm.vouchers.collectAsStateWithLifecycle()
    val archivedCount by vm.archivedCount.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotion()
    val sorted = remember(vouchers) { sortActive(vouchers) }
    val summary = remember(sorted) { summarizeActive(sorted) }
    // Copy-URL effect (refactor M16 + L11): the ACTION is requested in the
    // ViewModel; the shared bridge executes clipboard + toast and consumes it.
    CopyUrlEffect(copyRequest = vm.copyRequest, onConsumed = { vm.consumeCopyRequest() })
    // The LazyColumn scrolls via this state (overflow-menu / row animations).
    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                currentLanguage = languageStore.language,
                onLanguageSelected = onLanguageSelected,
                archivedCount = archivedCount,
                onArchivedClick = onArchivedClick,
                onSettingsClick = onSettingsClick,
                onAboutClick = onAboutClick,
                onAccessibilityClick = onAccessibilityClick,
                onToggleTheme = onToggleTheme,
                updateAvailable = updateAvailable,
                onCheckForUpdate = onCheckForUpdate,
                onTapToUpdate = onTapToUpdate,
            )
            BalanceHero(
                summary = summary,
                collapsed = heroCollapsed,
                onToggle = onToggleHeroCollapsed,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // The window is edge-to-edge on every API level, so the list
                // must clear the navigation bar itself (the M3 TopAppBar
                // already handles the status bar via its windowInsets).
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 6.dp,
                    bottom = 26.dp +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                            VoucherRowMenuContent(
                                onCopyUrl = {
                                    vm.setMenu(null)
                                    vm.requestCopy(voucher.url)
                                },
                                middleAction = RowMenuMiddleAction.ARCHIVE,
                                onMiddleClick = {
                                    vm.setMenu(null)
                                    vm.archive(voucher)
                                },
                                onDelete = {
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
