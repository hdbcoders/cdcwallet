package com.cdcwallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cdcwallet.R
import com.cdcwallet.ui.theme.AppLanguage
import com.cdcwallet.ui.theme.LocalAppIsDark
import com.cdcwallet.ui.theme.LocalRedesignColors
import com.cdcwallet.ui.theme.PlexMonoFontFamily

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
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRedesignColors.current
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
                    val dark = LocalAppIsDark.current
                    DropdownMenuItem(
                        // Theme toggle (spec change): the menu shows the mode
                        // the user can switch TO — "Dark Mode" when light,
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
                    ) {
                        Text(
                            text = "文A",
                            fontFamily = PlexMonoFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = c.textSecondary,
                        )
                    }
                    DropdownMenu(
                        expanded = langOpen,
                        onDismissRequest = { langOpen = false },
                        shape = RoundedCornerShape(14.dp),
                        containerColor = c.surfaceRaised,
                        border = BorderStroke(1.dp, c.hairline),
                    ) {
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
                            )
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
                    Text(
                        text = stringResource(R.string.archived),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textSecondary,
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
                            fontFamily = PlexMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF17130A),
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

/** Mockup icon button: 34dp, 10dp corners, raised surface + hairline border. */
@Composable
private fun IconBtn(
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val c = LocalRedesignColors.current
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.surfaceRaised)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
