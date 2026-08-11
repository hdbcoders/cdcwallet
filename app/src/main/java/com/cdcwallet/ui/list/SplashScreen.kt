package com.cdcwallet.ui.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.cdcwallet.R

/**
 * Compose splash used on API < 31 (no OS system splash there): shows the
 * circular app logo centered on the theme background until the voucher
 * list's first DB read completes. (The gating lives in MainActivity, which
 * holds the splash on screen until the first `observeActive` emission; this
 * composable itself is just the logo.) The unpadded circle logo renders as
 * authored (no mask, flag peel visible); the API 31+ system splash uses the
 * padded `ic_splash_logo_circle` asset instead (see `04 §4.7`). Sized at
 * 222dp so the artwork ≈ 192dp, matching the system splash icon size.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_splash_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            // Artwork (circle) fills 86.5% of the 1024px canvas; sizing the
            // canvas at 222dp renders the artwork at ~192dp - the same visual
            // size as the API 31+ system splash icon (503px @ 420dpi). No
            // circular clip needed: the logo is already a perfect circle.
            modifier = Modifier.size(222.dp),
        )
    }
}
