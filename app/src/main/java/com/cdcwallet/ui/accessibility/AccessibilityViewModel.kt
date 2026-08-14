package com.cdcwallet.ui.accessibility

import androidx.lifecycle.ViewModel
import com.cdcwallet.ui.theme.AppDyslexiaFont
import com.cdcwallet.ui.theme.AppFontScale
import com.cdcwallet.ui.theme.DyslexiaFontStore
import com.cdcwallet.ui.theme.FontScaleStore

/**
 * Accessibility screen state machine (00 §0.4): the dyslexia-font and
 * text-size choices live in their stores (Compose snapshot state, so any
 * change recomposes the whole app instantly); this ViewModel is the screen's
 * single mutation surface.
 */
class AccessibilityViewModel(
    val dyslexiaFontStore: DyslexiaFontStore,
    val fontScaleStore: FontScaleStore,
) : ViewModel() {

    fun setDyslexiaFontEnabled(enabled: Boolean) =
        dyslexiaFontStore.setDyslexiaFontEnabled(enabled)

    fun setDyslexiaFont(font: AppDyslexiaFont) =
        dyslexiaFontStore.setChosenFont(font)

    fun setFontScale(scale: AppFontScale) =
        fontScaleStore.setFontScale(scale)
}
