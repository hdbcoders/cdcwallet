@file:OptIn(ExperimentalLayoutApi::class)

package com.cdcwallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cdcwallet.ui.theme.AppScaledContent

/** Shared dialog chrome: centered surface, 28dp corners, 24dp padding. */
@Composable
fun AppDialogSurface(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        // Dialog content lives in a separate window whose density ignores
        // the app font scale - re-apply it so dialog text scales too.
        AppScaledContent {
            Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp) {
                // Refactor L9: bounded, scrollable content - long translations
                // or the Huge text setting scroll inside the dialog instead of
                // clipping against the window.
                val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    content = content,
                )
            }
        }
    }
}

/** Standard trailing Cancel / Confirm row. FlowRow (refactor L9): the buttons
 *  wrap onto their own line instead of clipping when long translations or the
 *  Huge text setting no longer fit them side by side. */
@Composable
fun DialogButtonRow(
    cancelLabel: String,
    onCancel: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    cancelModifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        itemVerticalAlignment = Alignment.CenterVertically,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = onCancel, modifier = cancelModifier) { Text(cancelLabel) }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            colors = if (destructive) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
        ) { Text(confirmLabel) }
    }
}
