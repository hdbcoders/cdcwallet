package com.cdcwallet.extraction

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Per-load epoch gate for the long-lived visible WebView (spec 02 §2.7
 * revision 2026-08-03). A previous page's document can still deliver a
 * payload after the next tap installs its bridge; such callbacks must be
 * dropped until the current target page actually started. Opened by the
 * epoch-notifier WebViewClient on the first `onPageStarted` of the load;
 * read from the WebView's JS thread, hence atomic. A payload can only be
 * delivered by a page whose document is alive, and the previous document
 * dies at the new navigation's commit — which is also when the gate opens —
 * so "drop while closed" admits exactly the current page's payload and
 * nothing else.
 */
internal class LoadGate {
    private val started = AtomicBoolean(false)

    fun open() {
        started.set(true)
    }

    val isOpen: Boolean get() = started.get()
}
