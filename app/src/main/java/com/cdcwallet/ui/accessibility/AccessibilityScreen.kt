package com.cdcwallet.ui.accessibility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdcwallet.R
import com.cdcwallet.ui.theme.AppDyslexiaFont
import com.cdcwallet.ui.theme.AppFontScale
import com.cdcwallet.ui.theme.AtkinsonFontFamily
import com.cdcwallet.ui.theme.DyslexiaFontStore
import com.cdcwallet.ui.theme.FontScaleStore
import com.cdcwallet.ui.theme.LocalAppTypefaces
import com.cdcwallet.ui.theme.OpenDyslexicFontFamily
import kotlin.math.roundToInt

/**
 * Accessibility screen: the dyslexia-friendly font option (off by default;
 * when on, a choice between the two bundled fonts, Atkinson Hyperlegible
 * default) and the app-wide text-size control, moved here from Settings so
 * every reading aid lives in one place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityScreen(
    dyslexiaFontStore: DyslexiaFontStore,
    fontScaleStore: FontScaleStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: AccessibilityViewModel = viewModel(
        initializer = { AccessibilityViewModel(dyslexiaFontStore, fontScaleStore) },
    )
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accessibility)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DyslexiaFontSection(vm)
            TextSizeSection(vm)
        }
    }
}

/**
 * The dyslexia-friendly font section: a switch (off by default) and, when
 * on, two selectable rows - Atkinson Hyperlegible (the default choice) and
 * OpenDyslexic - each with a live "Aa" preview rendered in that font. The
 * previews pin their own family explicitly so both fonts stay comparable
 * even while the screen itself renders in the chosen one.
 */
@Composable
private fun DyslexiaFontSection(vm: AccessibilityViewModel) {
    val store = vm.dyslexiaFontStore
    val enabled = store.enabled
    Text(
        text = stringResource(R.string.dyslexia_font),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.dyslexia_font_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = enabled,
            onCheckedChange = { vm.setDyslexiaFontEnabled(it) },
            modifier = Modifier.testTag("dyslexia-font-toggle"),
        )
    }
    if (enabled) {
        val chosen = store.font
        FontOptionRow(
            label = stringResource(R.string.dyslexia_font_atkinson),
            previewFamily = AtkinsonFontFamily,
            selected = chosen == AppDyslexiaFont.ATKINSON,
            testTag = "dyslexia-font-atkinson",
            onClick = { vm.setDyslexiaFont(AppDyslexiaFont.ATKINSON) },
        )
        FontOptionRow(
            label = stringResource(R.string.dyslexia_font_opendyslexic),
            previewFamily = OpenDyslexicFontFamily,
            selected = chosen == AppDyslexiaFont.OPEN_DYSLEXIC,
            testTag = "dyslexia-font-opendyslexic",
            onClick = { vm.setDyslexiaFont(AppDyslexiaFont.OPEN_DYSLEXIC) },
        )
    }
}

@Composable
private fun FontOptionRow(
    label: String,
    previewFamily: FontFamily,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(testTag)
            // Refactor M21: selected-state semantics for TalkBack
            // (radio-like announcement), not just the visual dot.
            .semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = "Aa",
            fontFamily = previewFamily,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The text-size control (moved from Settings): a left-to-right slider with
 * 4 discrete stops (Default → Huge, app scale capped at 1.75×) and a radio
 * dot marking each stop. Identical UX and test tags to the original; see
 * SettingsScreen for the full design notes.
 */
@Composable
private fun TextSizeSection(vm: AccessibilityViewModel) {
    val fontScaleStore = vm.fontScaleStore
    Text(
        text = stringResource(R.string.text_size),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.text_size_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val currentScale = fontScaleStore.scale
    val labels = AppFontScale.entries.associateWith { level ->
        stringResource(
            when (level) {
                AppFontScale.DEFAULT -> R.string.text_size_default
                AppFontScale.LARGE -> R.string.text_size_large
                AppFontScale.EXTRA_LARGE -> R.string.text_size_extra_large
                AppFontScale.HUGE -> R.string.text_size_huge
            },
        )
    }
    val currentLabel = labels.getValue(currentScale)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // The M3 thumb's centre travels from 10dp to width-10dp, so
        // the radio stops sit at the same fractions of that range.
        val trackStart = 10.dp
        val trackEnd = maxWidth - 10.dp
        Slider(
            value = currentScale.ordinal.toFloat(),
            onValueChange = { position ->
                vm.setFontScale(AppFontScale.entries[position.roundToInt()])
            },
            valueRange = 0f..AppFontScale.entries.lastIndex.toFloat(),
            steps = AppFontScale.entries.size - 2,
            modifier = Modifier
                .testTag("font-size-slider")
                .semantics { stateDescription = currentLabel },
        )
        AppFontScale.entries.forEachIndexed { index, level ->
            val fraction = index.toFloat() / (AppFontScale.entries.size - 1)
            val stopX = trackStart + (trackEnd - trackStart) * fraction
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    // 48dp box centred on the stop. It has no pointer
                    // input, so touches fall through to the slider.
                    .offset(x = stopX - 24.dp)
                    .size(48.dp)
                    .semantics { contentDescription = labels.getValue(level) }
                    .testTag("font-size-radio-${level.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                // Non-interactive (onClick = null) so the dot never
                // intercepts a drag - touches pass through to the slider.
                RadioButton(selected = level == currentScale, onClick = null)
            }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // "Aa" at the current level's app-wide size: X.sp paints at
        // X × LocalDensity.fontScale, so 14.sp renders exactly like
        // 14sp text at the selected level - and grows/shrinks live
        // as the slider moves. The family comes from the theme, so the
        // preview also shows the dyslexia font when that option is on.
        val typefaces = LocalAppTypefaces.current
        Text(
            text = "Aa",
            fontSize = PREVIEW_BASE_SP.sp,
            fontFamily = typefaces.body,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = currentLabel,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = multiplierHint(currentScale),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Numeric hint for a scale level, e.g. "1×", "1.25×", "2×". */
private fun multiplierHint(level: AppFontScale): String {
    val m = level.multiplier
    val text = if (m % 1f == 0f) m.toInt().toString() else m.toString()
    return "${text}×"
}

/** Base size for the "Aa" preview row (14sp at the DEFAULT level). */
private const val PREVIEW_BASE_SP = 14f
