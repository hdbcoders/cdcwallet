package com.hdbcoders.cdcwallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.ui.theme.AppLanguage
import com.hdbcoders.cdcwallet.ui.theme.AppScaledContent
import com.hdbcoders.cdcwallet.ui.theme.LocalAppIsDark
import com.hdbcoders.cdcwallet.ui.theme.LocalRedesignColors
import com.hdbcoders.cdcwallet.ui.theme.LocalAppTypefaces

/**
 * The redesign's app header (mockup): hamburger with a Dark/Light Mode
 * toggle plus Settings/About dropdown on the left, and on the right the
 * `文A` language toggle with an inline dropdown plus the Archived pill
 * carrying a gold count badge.
 *
 * The frame is a Material3 TopAppBar (spec 04's "[hamburger] title
 * [Translate] [Archived]" layout) with the mockup's bespoke controls in its
 * slots. The bar is transparent so the theme background (painted full-bleed
 * by MainActivity) shows through, and the default windowInsets keep the
 * header clear of the status bar in both light and dark modes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHeader(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    archivedCount: Int,
    onArchivedClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onAccessibilityClick: () -> Unit = {},
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRedesignColors.current
    val typefaces = LocalAppTypefaces.current
    var menuOpen by remember { mutableStateOf(false) }
    var langOpen by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {},
        navigationIcon = {
            Box(modifier = Modifier.padding(start = 14.dp)) {
                IconBtn(
                    contentDescription = stringResource(R.string.menu),
                    modifier = Modifier.testTag("header-menu"),
                    onClick = { menuOpen = true },
                ) {
                    Column(
                        modifier = Modifier.width(16.dp),
                        verticalArrangement = Arrangement.spacedBy(3.5.dp),
                    ) {
                        repeat(3) { i ->
                            Box(
                                modifier = Modifier
                                    .height(1.6.dp)
                                    .width(if (i == 1) 11.dp else 16.dp)
                                    .background(c.textSecondary, RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(14.dp),
                    containerColor = c.surfaceRaised,
                    border = BorderStroke(1.dp, c.hairline),
                ) {
                    // DropdownMenu content lives in a popup window whose
                    // density ignores the app font scale - re-apply it so
                    // menu text scales with the text-size setting.
                    AppScaledContent {
                        val dark = LocalAppIsDark.current
                        DropdownMenuItem(
                            // Theme toggle (spec change): the menu shows the mode
                            // the user can switch TO - "Dark Mode" when light,
                            // "Light Mode" when dark.
                            text = {
                                MenuLabel(
                                    stringResource(
                                        if (dark) R.string.light_mode else R.string.dark_mode,
                                    ),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                    contentDescription = null,
                                    tint = c.textSecondary,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onToggleTheme()
                            },
                        )
                        DropdownMenuItem(
                            text = { MenuLabel(stringResource(R.string.settings)) },
                            leadingIcon = {
                                Icon(Icons.Filled.Settings, contentDescription = null, tint = c.textSecondary)
                            },
                            onClick = {
                                menuOpen = false
                                onSettingsClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { MenuLabel(stringResource(R.string.accessibility)) },
                            leadingIcon = {
                                Icon(Icons.Filled.Accessibility, contentDescription = null, tint = c.textSecondary)
                            },
                            onClick = {
                                menuOpen = false
                                onAccessibilityClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { MenuLabel(stringResource(R.string.about_app)) },
                            leadingIcon = {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = c.textSecondary)
                            },
                            onClick = {
                                menuOpen = false
                                onAboutClick()
                            },
                        )
                    }
                }
            }
        },
        actions = {
            Row(
                modifier = Modifier.padding(end = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconBtn(
                        contentDescription = stringResource(R.string.select_language),
                        modifier = Modifier.testTag("header-language"),
                        onClick = { langOpen = true },
                        // Grows with the "文A" text (min 34dp) and pads the
                        // glyphs internally so they don't touch the button's
                        // edges; icon-only buttons keep the default fixed 34dp.
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "文A",
                            color = c.textSecondary,
                            // The CJK glyph's line box carries large vertical
                            // padding; drop it so the button height tracks the
                            // glyphs and the contentPadding is the visible
                            // breathing room.
                            style = LocalTextStyle.current.copy(
                                fontFamily = typefaces.mono,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                // 1em line: the CJK fallback's default line
                                // box is ~1.4em, which would make the button
                                // much taller than the glyphs. Pin it to the
                                // font size so the button height tracks the
                                // text and the contentPadding is the visible
                                // breathing room.
                                lineHeight = 13.sp,
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                            ),
                        )
                    }
                    DropdownMenu(
                        expanded = langOpen,
                        onDismissRequest = { langOpen = false },
                        shape = RoundedCornerShape(14.dp),
                        containerColor = c.surfaceRaised,
                        border = BorderStroke(1.dp, c.hairline),
                    ) {
                        AppScaledContent {
                            AppLanguage.entries.forEach { lang ->
                                val selected = lang == currentLanguage
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = languageLabel(lang),
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) c.gold else c.textPrimary,
                                        )
                                    },
                                    trailingIcon = if (selected) {
                                        {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = c.gold,
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        langOpen = false
                                        onLanguageSelected(lang)
                                    },
                                    // Refactor M21: selected-state semantics for
                                    // TalkBack (radio-like announcement), not just
                                    // the visual checkmark.
                                    modifier = Modifier.semantics { this.selected = selected },
                                )
                            }
                        }
                    }
                }

                // Archived pill (gold count badge).
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.surfaceRaised)
                        .clickable(onClick = onArchivedClick)
                        .padding(start = 11.dp, end = 7.dp, top = 7.dp, bottom = 7.dp)
                        .testTag("header-archived"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.Archive,
                        contentDescription = null,
                        tint = c.textSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    // Same treatment as the language toggle: the inherited
                    // bodyLarge lineHeight (24sp) would make the box ~2x the
                    // glyph, inflating the pill at large text sizes. Pin the
                    // box to the font size and drop font padding.
                    Text(
                        text = stringResource(R.string.archived),
                        style = LocalTextStyle.current.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textSecondary,
                            lineHeight = 12.5.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.gold)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = archivedCount.toString(),
                            // Same line-box pinning as the label above.
                            style = LocalTextStyle.current.copy(
                                fontFamily = typefaces.mono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF17130A),
                                lineHeight = 11.sp,
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                            ),
                            modifier = Modifier.testTag("archived-count"),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun languageLabel(lang: AppLanguage): String = when (lang) {
    AppLanguage.EN -> stringResource(R.string.language_en)
    AppLanguage.ZH -> stringResource(R.string.language_zh)
    AppLanguage.MS -> stringResource(R.string.language_ms)
    AppLanguage.TA -> stringResource(R.string.language_ta)
}

@Composable
private fun MenuLabel(text: String) {
    val c = LocalRedesignColors.current
    Text(
        text = text,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Medium,
        color = c.textPrimary,
    )
}

/** Header icon button: rounded, raised surface + hairline border. Fixed at
 *  [minSize] unless [contentPadding] makes the content larger - icon-only
 *  buttons keep the default 34dp; text-bearing buttons (the language
 *  switcher) grow with their text at large font scales. */
@Composable
private fun IconBtn(
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    minSize: Dp = 34.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val c = LocalRedesignColors.current
    Box(
        modifier = modifier
            // clip/background/clickable OUTSIDE the padding so the visible
            // button includes the contentPadding - a background inside
            // padding would only paint the unpadded text area and the glyphs
            // would hug the surface edges.
            .clip(RoundedCornerShape(10.dp))
            .background(c.surfaceRaised)
            .clickable(onClick = onClick)
            .defaultMinSize(minWidth = minSize, minHeight = minSize)
            .padding(contentPadding)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
