package com.hdbcoders.cdcwallet.extraction

import android.util.Log
import android.webkit.JavascriptInterface

/**
 * JS bridge target injected into the page under a per-load unique name
 * (refactor H1). The injected wrapper (InjectionScript) calls onData with the
 * whitelisted payload or onError with a failure kind, each time carrying the
 * per-load [expectedNonce]. Calls without the nonce - e.g. a forged direct
 * call from an unrelated document - are dropped (refactor H3).
 *
 * Invoked from the WebView's JS engine on a background thread; callers complete
 * a thread-safe deferred.
 */
internal class RedeemBridge(
    private val expectedNonce: String,
    private val onDataCallback: (String) -> Unit,
    private val onErrorCallback: (String) -> Unit,
) {
    @JavascriptInterface
    fun onData(payloadJson: String, nonce: String) {
        if (nonce != expectedNonce) return
        Log.d(TAG, "onData(${payloadJson.length} chars)")
        onDataCallback(payloadJson)
    }

    @JavascriptInterface
    fun onError(kind: String, nonce: String) {
        if (nonce != expectedNonce) return
        Log.d(TAG, "onError($kind)")
        onErrorCallback(kind)
    }

    private companion object {
        const val TAG = "RedeemBridge"
    }
}
