package com.hdbcoders.cdcwallet.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.ui.theme.DarkPalette
import com.hdbcoders.cdcwallet.ui.theme.DarkThemes
import com.hdbcoders.cdcwallet.ui.theme.LightPalette
import com.hdbcoders.cdcwallet.ui.theme.LightThemes
import com.hdbcoders.cdcwallet.ui.theme.LocalRedesignColors
import com.hdbcoders.cdcwallet.ui.theme.RedesignColors

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
@Composable
internal fun AppearanceSection(
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
internal fun DarkAppearanceSection(
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
