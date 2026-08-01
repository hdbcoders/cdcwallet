package com.cdcvouchers.extraction

import android.util.Log
import android.webkit.JavascriptInterface

/**
 * JS bridge target injected into the page. The injected wrapper (InjectionScript)
 * calls onData with the whitelisted payload or onError with a failure kind.
 * Invoked from the WebView's JS engine on a background thread; callers complete a
 * thread-safe deferred.
 */
internal class RedeemBridge(
    private val onDataCallback: (String) -> Unit,
    private val onErrorCallback: (String) -> Unit,
) {
    @JavascriptInterface
    fun onData(payloadJson: String) {
        Log.d(TAG, "onData(${payloadJson.length} chars)")
        onDataCallback(payloadJson)
    }

    @JavascriptInterface
    fun onError(kind: String) {
        Log.d(TAG, "onError($kind)")
        onErrorCallback(kind)
    }

    private companion object {
        const val TAG = "RedeemBridge"
    }
}
