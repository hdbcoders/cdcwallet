package com.cdcwallet.ui.list

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdcwallet.R
import com.cdcwallet.data.VoucherRepository
import com.cdcwallet.data.model.VoucherGroup
import com.cdcwallet.extraction.ExtractionCoordinator
import com.cdcwallet.ui.components.AppHeader
import com.cdcwallet.ui.components.BalanceHero
import com.cdcwallet.ui.components.DashedAddRow
import com.cdcwallet.ui.components.TicketCard
import com.cdcwallet.ui.theme.AppLanguage
import com.cdcwallet.ui.theme.LanguageStore
import com.cdcwallet.ui.theme.LocalRedesignColors
import com.cdcwallet.ui.theme.rememberReduceMotion

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
    languageStore: LanguageStore,
    onLanguageSelected: (AppLanguage) -> Unit,
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
    val clipboardManager = LocalClipboardManager.current
    // Toast on Copy URL (REQ-10 feedback): the activity context is wrapped
    // with the active app locale, so the message follows the app language.
    val context = LocalContext.current
    val linkCopiedLabel = stringResource(R.string.link_copied)
    // Copy-URL effect (refactor M16): the ACTION is requested in the
    // ViewModel; the screen executes clipboard + toast here and consumes it.
    LaunchedEffect(vm.copyRequest) {
        vm.copyRequest?.let { url ->
            clipboardManager.setText(AnnotatedString(url))
            Toast.makeText(context, linkCopiedLabel, Toast.LENGTH_SHORT).show()
            vm.consumeCopyRequest()
        }
    }
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
                onToggleTheme = onToggleTheme,
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
                            val c = LocalRedesignColors.current
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_url)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Link, contentDescription = null, tint = c.textSecondary)
                                },
                                onClick = {
                                    vm.setMenu(null)
                                    vm.requestCopy(voucher.url)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.archive)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Folder, contentDescription = null, tint = c.textSecondary)
                                },
                                onClick = {
                                    vm.setMenu(null)
                                    vm.archive(voucher)
                                },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = c.hairline,
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.delete), color = c.danger)
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = c.danger)
                                },
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
