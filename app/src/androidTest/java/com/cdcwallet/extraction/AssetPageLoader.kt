package com.cdcwallet.extraction

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

/**
 * Names of the synthetic page fixtures (files in the test apk's assets root).
 * Token-shaped URLs (no file extension) keep the engine's token validation
 * realistic - the expected token is the page URL's last path segment.
 */
val FIXTURE_PAGE_NAMES: Set<String> = setOf(
    "TestToken1",
    "NoApiPage",
    "BrokenFetch",
    "Malformed",
    "FailPage",
    "SlowPage",
    "WrongHostFetch",
    "WrongTokenFetch",
    "WrongPathFetch",
    "DirectBridgeCall",
    "IframeForgery",
    "RedirectFrom",
)

/**
 * WebView client for the synthetic extraction fixtures. WebViewAssetLoader
 * guesses the MIME type from the file extension, so the extensionless
 * token-shaped page URLs would be served as `application/octet-stream` and
 * never execute scripts; this client serves the known page paths with an
 * explicit `text/html` MIME (from the TEST apk's assets) and delegates
 * everything else - including the voucher-groups API responses - to the
 * asset loader.
 */
fun assetPageLoaderClient(assetLoader: WebViewAssetLoader): WebViewClient {
    val testAssets = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
    return object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            val path = request.url.path ?: return assetLoader.shouldInterceptRequest(request.url)
            val pageName = path.trim('/')
            if (pageName in FIXTURE_PAGE_NAMES) {
                return WebResourceResponse("text/html", "UTF-8", testAssets.open(pageName))
            }
            return assetLoader.shouldInterceptRequest(request.url)
        }
    }
}
