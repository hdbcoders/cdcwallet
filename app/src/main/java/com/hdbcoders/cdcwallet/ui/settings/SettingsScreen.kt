package com.hdbcoders.cdcwallet.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.data.VoucherRepository
import com.hdbcoders.cdcwallet.data.backup.BackupFlow
import com.hdbcoders.cdcwallet.data.model.VoucherBackupPayload
import com.hdbcoders.cdcwallet.ui.components.AppDialogSurface
import com.hdbcoders.cdcwallet.ui.components.DialogButtonRow
import com.hdbcoders.cdcwallet.ui.theme.DarkPalette
import com.hdbcoders.cdcwallet.ui.theme.DarkThemes
import com.hdbcoders.cdcwallet.ui.theme.LightPalette
import com.hdbcoders.cdcwallet.ui.theme.LightThemes
import com.hdbcoders.cdcwallet.ui.theme.LocalRedesignColors
import com.hdbcoders.cdcwallet.ui.theme.RedesignColors
import com.hdbcoders.cdcwallet.ui.theme.ThemeMode
import com.hdbcoders.cdcwallet.ui.theme.ThemeModeStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Settings screen (spec 06): encrypted backup export to Downloads and import
 * via the system file picker. Import never touches the network - restored rows
 * are plain cached entries from the payload. The `backupBytesProvider` seam
 * exists for instrumented tests (system picker is not drivable there);
 * production passes null and uses the real picker. Dialog/flow state lives in
 * [SettingsViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    backupFlow: BackupFlow,
    repository: VoucherRepository,
    themeModeStore: ThemeModeStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // TEST-ONLY SEAM (refactor M17): injects bytes instead of the system file
    // picker in the instrumented backup tests; production always passes null.
    backupBytesProvider: (() -> ByteArray?)? = null,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    // Snackbar event held in state: the message text is resolved at
    // composition time via stringResource/pluralStringResource so the active
    // app locale is always used.
    var pendingSnackbar by remember { mutableStateOf<SettingsEvent.Snackbar?>(null) }

    val vm: SettingsViewModel = viewModel(
        initializer = { SettingsViewModel(backupFlow, repository, context.applicationContext) },
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val activeCount by vm.activeCount.collectAsStateWithLifecycle()
    val archivedCount by vm.archivedCount.collectAsStateWithLifecycle()

    // API 24-28 write to the public Downloads directory directly (scoped
    // storage starts at 29), which needs WRITE_EXTERNAL_STORAGE granted at
    // runtime on API 23+ - request it before the first export on those
    // versions; API 29+ uses MediaStore and needs nothing.
    val exportPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> vm.onLegacyPermissionResult(granted) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) vm.onFilePicked(uri)
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is SettingsEvent.Snackbar -> pendingSnackbar = event
                SettingsEvent.RequestLegacyPermission ->
                    exportPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    fun startImport() {
        val provided = backupBytesProvider?.invoke()
        if (provided != null) {
            vm.onBackupBytesProvided(provided)
        } else {
            filePicker.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppearanceSection(
                selected = themeModeStore.palette,
                onSelect = { themeModeStore.setLightPalette(it) },
                darkModeActive = themeModeStore.mode == ThemeMode.DARK,
            )
            DarkAppearanceSection(
                selected = themeModeStore.darkTheme,
                onSelect = { themeModeStore.setDarkPalette(it) },
                darkModeActive = themeModeStore.mode == ThemeMode.DARK,
            )
            Text(
                text = stringResource(R.string.backup),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(
                onClick = { vm.openExportDialog() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.export_backup))
            }
            Text(
                text = stringResource(R.string.export_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { startImport() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.import_backup))
            }
            Text(
                text = stringResource(R.string.import_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingSnackbar?.let { event ->
        val text = if (event.pluralCount != null) {
            pluralStringResource(event.resId, event.pluralCount, *event.formatArgs.toTypedArray())
        } else {
            stringResource(event.resId, *event.formatArgs.toTypedArray())
        }
        LaunchedEffect(text) {
            snackbarHostState.showSnackbar(text)
            pendingSnackbar = null
        }
    }

    if (state.exportDialogOpen) {
        BackupPasswordDialog(
            requireConfirmation = true,
            confirmLabel = stringResource(R.string.export),
            onConfirm = { password -> vm.onExportPasswordConfirmed(password) },
            onDismiss = { vm.dismissExportDialog() },
        )
    }

    when (state.passwordDialogFor) {
        PasswordDialogTarget.IMPORT -> {
            BackupPasswordDialog(
                requireConfirmation = false,
                confirmLabel = stringResource(R.string.import_confirm),
                onConfirm = { password -> vm.onImportPasswordConfirmed(password) },
                onDismiss = { vm.onImportDialogDismissed() },
            )
        }
        null -> Unit
    }

    state.summaryPayload?.let { payload ->
        BackupSummaryDialog(
            payload = payload,
            currentCount = activeCount + archivedCount,
            onImport = { mode -> vm.onSummaryModeSelected(mode) },
            onDismiss = { vm.onSummaryDismissed() },
        )
    }

    if (state.pendingReplace != null) {
        ConfirmReplaceDialog(
            count = activeCount + archivedCount,
            onReplace = { vm.onReplaceConfirmed() },
            onCancel = { vm.onReplaceDismissed() },
        )
    }

    // Non-dismissable progress while a backup operation runs (decrypt/
    // merge/replace on import, encrypt + file write on export). The ViewModel
    // caps every operation at 10s, so the dialog can never hang forever.
    state.busyPhase?.let { phase ->
        BusyProgressDialog(
            message = stringResource(
                when (phase) {
                    BusyPhase.IMPORTING -> R.string.importing_backup
                    BusyPhase.EXPORTING -> R.string.exporting_backup
                },
            ),
        )
    }
}

@Composable
private fun BusyProgressDialog(message: String) {
    AppDialogSurface(onDismissRequest = {}) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.testTag("backup-progress"),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
/**
 * Appearance section (theme picker, spec 07 §7.2): one swatch cell per
 * [LightPalette] entry, three per row, NO visible names - the palette name
 * rides in each cell's contentDescription, so TalkBack still announces
 * "Champagne Gold, radio button, selected". Each cell previews the palette
 * (hero gradient + accent dot) with a hairline border; the selected cell
 * swaps to a 2dp ring in the palette's text-safe accent slot plus a corner
 * check badge. Selecting a cell calls [onSelect], which persists and applies
 * the palette instantly via ThemeModeStore snapshot state - no restart
 * needed. Dark/light mode itself is NOT controlled here; the drawer toggle
 * stays the only dark-mode switch, and [darkModeActive] shows a hint that
 * palettes affect light mode only.
 */
private fun AppearanceSection(
    selected: LightPalette,
    onSelect: (LightPalette) -> Unit,
    darkModeActive: Boolean = false,
) {
    Text(
        text = stringResource(R.string.settings_light_appearance),
        style = MaterialTheme.typography.titleMedium,
    )
    PaletteSwatchGrid(
        entries = LightPalette.entries,
        selected = selected,
        onSelect = onSelect,
    ) { palette ->
        val colors = LightThemes.getValue(palette).colors
        SwatchSpec(
            label = stringResource(palette.labelRes),
            gradient = listOf(colors.summaryStart, colors.summaryEnd),
            accent = colors.accent,
            onAccent = colors.onAccent,
            ring = colors.accentText,
        )
    }
    if (darkModeActive) {
        Text(
            text = stringResource(R.string.settings_palette_light_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The per-palette visuals a swatch cell needs (built at the call site from
 *  the palette's token table - light sections preview the hero gradient,
 *  dark sections the canvas-to-raised gradient). */
private data class SwatchSpec(
    val label: String,
    val gradient: List<Color>,
    val accent: Color,
    val onAccent: Color,
    val ring: Color,
)

/**
 * Swatch-cell grid for both theme-picker sections: chunks the visible
 * palette entries into rows of three equal-width cells (the 3-per-line
 * color-picker layout; a future 4th visible palette wraps cleanly). Cells
 * carry NO visible name - the palette name lives in contentDescription, so
 * TalkBack announces "<name>, radio button, selected".
 */
@Composable
private fun <T> PaletteSwatchGrid(
    entries: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    spec: @Composable (T) -> SwatchSpec,
) {
    entries.chunked(3).forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 12dp top clearance for the selected cell's overhanging badge.
                .padding(top = 12.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.forEach { palette ->
                val s = spec(palette)
                SwatchCell(
                    label = s.label,
                    selected = palette == selected,
                    gradient = s.gradient,
                    accent = s.accent,
                    onAccent = s.onAccent,
                    ring = s.ring,
                    onClick = { if (palette != selected) onSelect(palette) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One selectable palette swatch: the two-tone preview chip (per-palette
 * gradient + accent dot) with a hairline border. The selected cell swaps the
 * border to a 2dp ring in the palette's text-safe accent slot
 * ([RedesignColors.accentText] - resolves to raw accent where the accent
 * itself passes, e.g. jade/navy) and hangs a corner check badge off the
 * chip: accent fill with an on-accent drawn check, plus a 2dp ambient-color
 * knockout ring so the badge never vanishes against the chip's gradient.
 * The whole cell is the touch target (~119x72dp, well above the 48dp
 * minimum).
 *
 * Structure matters: the chip gradient is CLIPPED in an inner layer, while
 * the badge is an unclipped SIBLING - clipping the cell itself would cut
 * the overhanging badge down to an invisible corner arc.
 */
@Composable
private fun SwatchCell(
    label: String,
    selected: Boolean,
    gradient: List<Color>,
    accent: Color,
    onAccent: Color,
    ring: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRedesignColors.current
    val shape = RoundedCornerShape(14.dp)
    // The touch target + semantics live on the outer box; the BORDER lives
    // on the chip layer and the badge is a sibling drawn AFTER it - a border
    // on the outer box would draw over the badge and cut across the tick.
    Box(
        modifier = modifier
            .height(72.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = label
                this.selected = selected
            },
    ) {
        // Clipped chip layer: the two-tone preview, its accent dot, and the
        // border (hairline unselected / 2dp text-safe accent ring selected).
        // Only this layer is rounded-clip - the badge below must overhang
        // freely.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Brush.linearGradient(gradient))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) ring else c.hairline,
                    shape = shape,
                ),
        ) {
            // Accent dot, bottom-left - the palette's interactive hue at a glance.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 10.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        if (selected) {
            // Check badge: accent fill + on-accent drawn check, knockout
            // ring in the ambient background so it separates from the chip.
            // Drawn after the chip layer, so it covers the ring at the
            // corner instead of being crossed by it.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .border(2.dp, c.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = onAccent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * Dark Themes section (dark-palette picker, spec 07 §7.2): one swatch cell
 * per visible [DarkPalette] entry (hiddenInPicker rows are skipped), three
 * per row, NO visible names - the palette name rides in contentDescription
 * so TalkBack announces e.g. "Midnight Gold, radio button, selected". Each
 * cell previews the palette (canvas-to-raised gradient + accent dot) with a
 * hairline border; the selected cell swaps to a 2dp accent ring plus a
 * corner check badge. Selecting a cell calls [onSelect], which persists and
 * applies the palette instantly via ThemeModeStore snapshot state - no
 * restart needed. This is the mirror of [AppearanceSection]: dark/light mode
 * itself is NOT controlled here (the drawer toggle stays the mode switch),
 * and while light mode is active ([darkModeActive] == false) a hint explains
 * the palettes affect dark mode only.
 */
@Composable
private fun DarkAppearanceSection(
    selected: DarkPalette,
    onSelect: (DarkPalette) -> Unit,
    darkModeActive: Boolean = true,
) {
    Text(
        text = stringResource(R.string.settings_dark_appearance),
        style = MaterialTheme.typography.titleMedium,
    )
    // hiddenInPicker entries (currently Moss Green) stay registered and
    // parseable - a user who already had one selected keeps it active - but
    // never render a cell here.
    PaletteSwatchGrid(
        entries = DarkPalette.entries.filter { !it.hiddenInPicker },
        selected = selected,
        onSelect = onSelect,
    ) { palette ->
        val colors = DarkThemes.getValue(palette).colors
        SwatchSpec(
            label = stringResource(palette.labelRes),
            gradient = listOf(colors.background, colors.surfaceRaised),
            accent = colors.accent,
            onAccent = colors.onAccent,
            ring = colors.accentText,
        )
    }
    if (!darkModeActive) {
        Text(
            text = stringResource(R.string.settings_palette_dark_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}


@Composable
private fun BackupPasswordDialog(
    requireConfirmation: Boolean,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = password.isNotEmpty() && (!requireConfirmation || password == confirmation)
    val passwordFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    AppDialogSurface(onDismissRequest = onDismiss) {
        Text(
            text = if (requireConfirmation) {
                stringResource(R.string.set_backup_password)
            } else {
                stringResource(R.string.backup_password)
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (requireConfirmation) {
                stringResource(R.string.password_encrypt_desc)
            } else {
                stringResource(R.string.password_enter_desc)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.backup_password)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("backup_password").focusRequester(passwordFocus),
        )
        if (requireConfirmation) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = { Text(stringResource(R.string.confirm_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("backup_confirm_password"),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        DialogButtonRow(
            cancelLabel = stringResource(R.string.cancel),
            onCancel = onDismiss,
            confirmLabel = confirmLabel,
            onConfirm = { onConfirm(password) },
            confirmEnabled = valid,
        )
    }
    // M3 Dialog doesn't reliably land initial focus on the password field
    // (mirrors DeleteVoucherDialog - the request can be stolen during dialog
    // mount). Retry briefly (up to ~2s) until it sticks, then surface the
    // keyboard so the user can type without an extra tap. Applies to both the
    // export (password + confirm) and import (password) dialogs.
    LaunchedEffect(Unit) {
        repeat(40) {
            passwordFocus.requestFocus()
            delay(50)
        }
        keyboardController?.show()
    }
}

@Composable
private fun BackupSummaryDialog(
    payload: VoucherBackupPayload,
    currentCount: Int,
    onImport: (ImportMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(ImportMode.MERGE) }
    // Date formatted in the app's active locale (pattern is a per-locale
    // resource; month names come from the locale itself).
    val datePattern = stringResource(R.string.date_pattern)
    val locale = LocalConfiguration.current.locales[0]
    val date = payload.createdAt
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern(datePattern, locale))
    val archived = payload.vouchers.count { it.isArchived }

    AppDialogSurface(onDismissRequest = onDismiss) {
        Text(
            text = pluralStringResource(
                R.plurals.backup_summary,
                payload.vouchers.size,
                date,
                payload.vouchers.size,
                archived,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.merge_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ModeOption(
            label = stringResource(R.string.merge),
            selected = mode == ImportMode.MERGE,
            onClick = { mode = ImportMode.MERGE },
        )
        ModeOption(
            label = stringResource(R.string.replace),
            selected = mode == ImportMode.REPLACE,
            onClick = { mode = ImportMode.REPLACE },
        )
        Spacer(modifier = Modifier.height(24.dp))
        DialogButtonRow(
            cancelLabel = stringResource(R.string.cancel),
            onCancel = onDismiss,
            confirmLabel = stringResource(R.string.import_confirm),
            onConfirm = { onImport(mode) },
        )
    }
}

@Composable
private fun ModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ConfirmReplaceDialog(
    count: Int,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    // Refactor M20: "Cancel" is the default-focused button, same convention
    // as the delete dialog - an accidental confirm-tap must not wipe data.
    val cancelFocus = remember { FocusRequester() }
    AppDialogSurface(onDismissRequest = onCancel) {
        Text(
            text = stringResource(R.string.replace_all_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = pluralStringResource(R.plurals.replace_all_body, count, count),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        DialogButtonRow(
            cancelLabel = stringResource(R.string.cancel),
            onCancel = onCancel,
            confirmLabel = stringResource(R.string.replace_confirm),
            onConfirm = onReplace,
            destructive = true,
            cancelModifier = Modifier.focusRequester(cancelFocus),
        )
    }
    // Same retry block as the delete dialog (M3 dialogs can steal focus
    // during mount); delete this if a future Compose/M3 update fixes it.
    LaunchedEffect(Unit) {
        repeat(10) {
            cancelFocus.requestFocus()
            delay(50)
        }
    }
}
