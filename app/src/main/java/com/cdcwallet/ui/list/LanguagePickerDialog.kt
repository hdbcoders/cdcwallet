package com.cdcwallet.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cdcwallet.R
import com.cdcwallet.ui.components.AppDialogSurface
import com.cdcwallet.ui.theme.AppLanguage

/**
 * Standalone language picker (spec 07 §7.5): opened from the Translate button
 * in the main list's top bar. Lists the four app languages — the active one is
 * checked; tapping an entry applies it (persisted + activity recreate by the
 * caller) and dismisses the dialog.
 */
@Composable
fun LanguagePickerDialog(
    current: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialogSurface(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.language),
            style = MaterialTheme.typography.titleLarge,
        )
        Column(modifier = Modifier.padding(top = 8.dp)) {
            LanguageOption(
                label = stringResource(R.string.language_en),
                selected = current == AppLanguage.EN,
                onClick = {
                    onLanguageSelected(AppLanguage.EN)
                    onDismiss()
                },
            )
            LanguageOption(
                label = stringResource(R.string.language_zh),
                selected = current == AppLanguage.ZH,
                onClick = {
                    onLanguageSelected(AppLanguage.ZH)
                    onDismiss()
                },
            )
            LanguageOption(
                label = stringResource(R.string.language_ms),
                selected = current == AppLanguage.MS,
                onClick = {
                    onLanguageSelected(AppLanguage.MS)
                    onDismiss()
                },
            )
            LanguageOption(
                label = stringResource(R.string.language_ta),
                selected = current == AppLanguage.TA,
                onClick = {
                    onLanguageSelected(AppLanguage.TA)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun LanguageOption(
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
