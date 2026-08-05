package com.cdcvouchers.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.model.CategoryBalance
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.extraction.ExtractionCoordinator
import com.cdcvouchers.ui.theme.LocalAppIsDark
import com.cdcvouchers.ui.theme.SummaryCardContainerLight
import com.cdcvouchers.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
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
    extractionCoordinator: ExtractionCoordinator,
    onAddClick: () -> Unit,
    onOpenVoucher: (VoucherGroup) -> Unit,
    onArchivedClick: () -> Unit,
    onSettingsClick: () -> Unit,
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
                state = listState,
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
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(116.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: total balance, spread to fill the card height.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text(
                    text = "Remaining Balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatSgd(summary.total),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.Link,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Across ${summary.linkCount} voucher ${if (summary.linkCount == 1) "link" else "links"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            VerticalDivider(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxHeight(),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            // Right: top 3 categories by value, then "+N more".
            Column(
                modifier = Modifier.weight(0.9f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val top = summary.categoryTotals
                    .sortedByDescending { it.remainingValue }
                    .take(3)
                top.forEachIndexed { index, balance ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 3.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    SummaryCategoryRow(balance)
                }
                val hidden = summary.categoryTotals.size - 3
                if (hidden > 0) {
                    Text(
                        text = "+$hidden more",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One category row in the summary breakdown: pastel icon + name + amount. */
@Composable
private fun SummaryCategoryRow(balance: CategoryBalance) {
    val (icon, iconBg, iconTint) = categoryIcon(balance.category)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(iconBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = iconTint,
            )
        }
        Text(
            text = balance.category,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatSgd(balance.remainingValue),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Category icon + circle color + icon tint. Unknown categories fall back to a star. */
@Composable
private fun categoryIcon(category: String): Triple<ImageVector, Color, Color> {
    val dark = LocalAppIsDark.current
    return when (category.trim().lowercase()) {
        "climate" -> Triple(
            Icons.Outlined.Eco,
            if (dark) Color(0xFF2E4A73) else Color(0xFFBBD4F5),
            if (dark) Color(0xFFA5C1E8) else Color(0xFF1A448A),
        )
        "heartland" -> Triple(
            Icons.Outlined.Favorite,
            if (dark) Color(0xFF2E5B36) else Color(0xFFBDE8C5),
            if (dark) Color(0xFFA5D6A7) else Color(0xFF1B5E20),
        )
        "supermarket" -> Triple(
            Icons.Outlined.ShoppingCart,
            if (dark) Color(0xFF4A3470) else Color(0xFFDCCCF2),
            if (dark) Color(0xFFD1C4E9) else Color(0xFF4A148C),
        )
        else -> Triple(
            Icons.Outlined.Star,
            if (dark) Color(0xFF3A3A3A) else Color(0xFFDDE1E6),
            if (dark) Color(0xFFB0BEC5) else Color(0xFF455A64),
        )
    }
}
