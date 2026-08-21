package com.hdbcoders.cdcwallet.dev

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.hdbcoders.cdcwallet.MainActivity
import com.hdbcoders.cdcwallet.addflow.AddVoucherFlow
import com.hdbcoders.cdcwallet.addflow.VoucherLinkValidator
import com.hdbcoders.cdcwallet.data.VoucherRepository
import com.hdbcoders.cdcwallet.extraction.ExtractionEngine
import java.io.IOException

/**
 * Debug-only fixture add flow (spec 03 coverage harness). Lives under
 * `src/debug`, so release builds never compile it.
 *
 * Mirror of the instrumented-test fixture stack (the synthetic
 * `appassets.androidplatform.net` package served through WebViewAssetLoader),
 * but serving the fixture HTML/JSON from THIS app's own assets
 * (`src/debug/assets`) - the test APK's assets are not on the app classpath,
 * so an app-side flow cannot reuse the androidTest fixture loader. [VoucherApp]
 * exposes it via [VoucherApp.devFixtureAddFlow]; [MainActivity] wires it only
 * when a share intent carries [MainActivity.EXTRA_DEV_FIXTURE_ADD] on a
 * debuggable build. Never touches a real RedeemSG host.
 */
object DevFixtureFlow {

    const val ASSET_DOMAIN = "appassets.androidplatform.net"
    const val ASSET_ORIGIN = "https://appassets.androidplatform.net"

    private fun assetLoader(context: Context): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .setDomain(ASSET_DOMAIN)
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()

    /**
     * Serves the extensionless fixture page (`/TestToken1`) with an explicit
     * `text/html` MIME from this app's assets; the API JSON
     * (`/vouchers/groups/TestToken1`) exists as an asset too, so serving it
     * directly is byte-identical to the loader fallback - same as the
     * test-process [com.hdbcoders.cdcwallet.extraction.assetPageLoaderClient].
     */
    private fun client(loader: WebViewAssetLoader, context: Context): WebViewClient =
        object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val path = request.url.path ?: return loader.shouldInterceptRequest(request.url)
                return try {
                    WebResourceResponse("text/html", "UTF-8", context.assets.open(path.trim('/')))
                } catch (_: IOException) {
                    loader.shouldInterceptRequest(request.url)
                }
            }
        }

    fun buildAddFlow(context: Context, repository: VoucherRepository): AddVoucherFlow {
        val loader = assetLoader(context)
        val client = client(loader, context)
        val engine = ExtractionEngine(
            forceFallbackInjection = false,
            hiddenWebViewFactory = { WebView(context).apply { webViewClient = client } },
            fallbackInjectionDelegate = client,
            extractionTimeoutMs = 30_000,
            allowedPageOrigin = ASSET_ORIGIN,
            targetApiHost = ASSET_DOMAIN,
        )
        return AddVoucherFlow(
            repository = repository,
            extractionEngine = engine,
            validator = VoucherLinkValidator(allowedHost = ASSET_DOMAIN),
        )
    }
}