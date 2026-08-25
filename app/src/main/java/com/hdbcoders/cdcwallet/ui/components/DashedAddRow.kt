package com.hdbcoders.cdcwallet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.ui.theme.LocalRedesignColors

/**
 * The redesign's "Add Voucher" row (mockup): an accent dashed-border rounded
 * strip at the bottom of the list. Replaces the old floating action button.
 * Label text uses [RedesignColors.accentText] (the AA-safe accent-as-text
 * slot); the dashed border and icon keep the raw saturated accent.
 */
@Composable
fun DashedAddRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRedesignColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .testTag("add-voucher-row")
            .drawBehind {
                drawRoundRect(
                    color = c.accent,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                )
            }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = stringResource(R.string.add_voucher),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = c.accentText,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
