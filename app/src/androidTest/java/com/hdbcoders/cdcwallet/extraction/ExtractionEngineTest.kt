package com.hdbcoders.cdcwallet.extraction

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Extraction engine tests against a synthetic page served through
 * WebViewAssetLoader that replicates the confirmed API response shape
 * (spec 02 §2.2). Never touches the real RedeemSG hosts.
 *
 * The engine's production policies (injection origin + API host) are
 * deliberately strict (refactor H2/H3); these tests pass the asset-loader
 * host through the engine's explicit test seams - never through the
 * production policy.
 */
@RunWith(AndroidJUnit4::class)
class ExtractionEngineTest {

    private lateinit var context: Context
    private lateinit var assetLoader: WebViewAssetLoader

    private val assetOrigin = "https://appassets.androidplatform.net"

    private val testPageUrl = "https://appassets.androidplatform.net/TestToken1"
    private val noApiPageUrl = "https://appassets.androidplatform.net/NoApiPage"
    private val badTargetPageUrl = "https://appassets.androidplatform.net/BrokenFetch"
    private val malformedPageUrl = "https://appassets.androidplatform.net/Malformed"

    @Before
    fun setUp() {
        // Refactor D5: shared fixture setup (loader + debugging).
        WebViewFixtures.enableWebViewDebugging()
        context = ApplicationProvider.getApplicationContext()
        assetLoader = WebViewFixtures.buildAssetLoader()
    }

    private fun assetLoaderClient(): WebViewClient = assetPageLoaderClient(assetLoader)

    /** Serves the WrongOriginPage fixture from a NON-official origin. */
    private fun wrongOriginClient(): WebViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            if (request.url.host == "evil.example.com") {
                return WebResourceResponse(
                    "text/html",
                    "UTF-8",
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        .context.assets.open("WrongOriginPage"),
                )
            }
            return assetLoader.shouldInterceptRequest(request.url)
        }
    }

    private fun wrongOriginWebView(): WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        webViewClient = wrongOriginClient()
    }

    /**
     * The redirect vector uses a client-side (JS) redirect fixture instead of
     * a simulated server 302: WebResourceResponse rejects 3xx status codes
     * and WebViewAssetLoader cannot serve redirects, so a JS navigation is
     * the closest achievable simulation - same risky property: the tapped
     * URL renders a DIFFERENT voucher's page. Served via the shared fixture
     * client, so the default engine() seam applies.
     */

    /**
     * Serves the Forge fixture from the NON-official evil.example.com origin
     * while the host page (IframeForgery) loads on the official fixture
     * origin: a cross-origin iframe forgery (refactor H3 iframe vector).
     */
    private fun iframeForgeryClient(): WebViewClient {
        val base = assetPageLoaderClient(assetLoader)
        return object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                if (request.url.host == "evil.example.com" && request.url.path?.trim('/') == "Forge") {
                    return WebResourceResponse(
                        "text/html",
                        "UTF-8",
                        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                            .context.assets.open("Forge"),
                    )
                }
                return base.shouldInterceptRequest(view, request)
            }
        }
    }

    private fun webViewWithIframeForgeryClient(): WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        webViewClient = iframeForgeryClient()
    }

    /**
     * Engine with the asset-loader fixture host passed through the test seams.
     * The asset-loader delegate is the default on purpose: on API 24–25 the
     * view's client cannot be read back (no getter), so the fallback rewrite
     * path needs the delegate explicitly - without it every test would hit
     * `InjectionPath.Unavailable` there instead of the fixture behavior.
     */
    private fun engine(
        hiddenFactory: ((Context) -> WebView)? = null,
        forceFallback: Boolean = false,
        delegate: WebViewClient? = assetLoaderClient(),
        timeoutMs: Long = 10_000,
    ): ExtractionEngine = WebViewFixtures.fixtureEngine(
        assetLoader = assetLoader,
        timeoutMs = timeoutMs,
        hiddenWebViewFactory = hiddenFactory,
        forceFallbackInjection = forceFallback,
        fallbackInjectionDelegate = delegate,
    )

    @Test
    fun hiddenWebViewExtractsWhitelistedData() = runTest {
        val result = engine().extractForAdd(context, testPageUrl)

        assertTrue("expected Success, got $result", result is ExtractionResult.Success)
        val success = result as ExtractionResult.Success
        assertEquals("CDC Vouchers 2026", success.campaignName)
        assertEquals(ValidityStatus.ACTIVE, success.validityStatus)
        assertEquals(LocalDate.of(2026, 12, 31), success.expiryDate)
        assertEquals(
            listOf(
                com.hdbcoders.cdcwallet.data.model.CategoryBalance("heartland", BigDecimal("50")),
                com.hdbcoders.cdcwallet.data.model.CategoryBalance("supermarket", BigDecimal("25.5")),
            ),
            success.categoryBalances,
        )
    }

    @Test
    fun onlyWhitelistedFieldsSurviveExtraction() = runTest {
        // The synthetic response contains the resident's home address and merchant
        // names (spec 02 §2.2). The extracted Success must carry nothing but the
        // six whitelisted fields - the DTO has nowhere else for data to land.
        val result = engine().extractForAdd(context, testPageUrl) as ExtractionResult.Success

        assertEquals(2, result.categoryBalances.size)
        result.categoryBalances.forEach { balance ->
            assertTrue(balance.category in setOf("heartland", "supermarket"))
            assertTrue(balance.remainingValue > BigDecimal.ZERO)
        }
    }

    @Test
    fun timeoutWhenPageNeverFetches() = runTest {
        val result = engine().extractForAdd(context, noApiPageUrl)
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT),
            result,
        )
    }

    @Test
    fun networkErrorIsReported() = runTest {
        val result = engine().extractForAdd(context, badTargetPageUrl)
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.NETWORK_ERROR),
            result,
        )
    }

    @Test
    fun forcedHtmlRewriteFallbackStillCatchesFirstFetch() = runTest {
        // API 24–25 cannot read the pre-existing client back (no getter), so
        // the delegate is supplied explicitly; on API 26+ it is read from the
        // WebView and this parameter is ignored.
        val result = engine(forceFallback = true, delegate = assetLoaderClient())
            .extractForAdd(context, testPageUrl)
        assertTrue("expected Success via fallback, got $result", result is ExtractionResult.Success)
        val success = result as ExtractionResult.Success
        assertEquals("CDC Vouchers 2026", success.campaignName)
        assertEquals(
            listOf(
                com.hdbcoders.cdcwallet.data.model.CategoryBalance("heartland", BigDecimal("50")),
                com.hdbcoders.cdcwallet.data.model.CategoryBalance("supermarket", BigDecimal("25.5")),
            ),
            success.categoryBalances,
        )
    }

    /**
     * Refactor H5: the production-default path when document-start injection
     * is unavailable. Production supplies no delegate that intercepts the
     * main document, so the rewrite fallback cannot inject anything - the
     * extraction must fail fast (never burn the timeout) while the page still
     * loads through the WebView's own network stack. The clientless WebView
     * mirrors production's fresh hidden instance; the 30s budget proves the
     * fast-fail (this test completes in well under 30s).
     */
    @Test
    fun productionDefaultWithoutInjectionReportsFailureFast() = runTest {
        val result = ExtractionEngine(
            forceFallbackInjection = true,
            hiddenWebViewFactory = { WebView(context) },
            extractionTimeoutMs = 30_000,
            allowedPageOrigin = assetOrigin,
            targetApiHost = "appassets.androidplatform.net",
        ).extractForAdd(context, testPageUrl)
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.PARSE_ERROR),
            result,
        )
    }

    @Test
    fun cancellingMidFlightTearsDownHiddenWebView() = runTest {
        val created = CompletableDeferred<Unit>()
        val factoryEngine = engine(
            hiddenFactory = { context ->
                WebViewFixtures.assetWebView(context, assetLoader).also { created.complete(Unit) }
            },
        )
        val scope = CoroutineScope(Dispatchers.Main + Job())
        var cancelled = false
        val job = scope.launch {
            try {
                factoryEngine.extractForAdd(context, noApiPageUrl)
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        // Refactor D10: deterministic start signal - cancel only after the
        // hidden WebView was actually created, instead of a fixed sleep.
        created.await()
        job.cancelAndJoin()
        assertTrue("extraction should have been cancelled", cancelled)
        scope.cancel()
    }

    @Test
    fun malformedResponseIsParseError() = runTest {
        val result = engine().extractForAdd(context, malformedPageUrl)
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.PARSE_ERROR),
            result,
        )
    }

    @Test
    fun unavailableInjectionPathFailsFastWithParseError() = runTest {
        // Refactor D12: no document-start support AND no rewrite delegate ->
        // InjectionPath.Unavailable. The page still loads, but extraction
        // fails fast instead of burning the timeout (refactor H5). A bare
        // WebView (no client) + forced fallback with a null delegate produces
        // exactly that path.
        val unavailableEngine = engine(
            hiddenFactory = { WebView(it) },
            forceFallback = true,
            delegate = null,
        )
        val result = unavailableEngine.extractForAdd(context, testPageUrl)
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.PARSE_ERROR),
            result,
        )
    }

    @Test
    fun mainFrameLoadErrorReportsNetworkError() = runTest {
        // Refactor D12: a main-frame WebView load error must map to an
        // explicit NETWORK_ERROR. The main frame is forced to fail with
        // settings.blockNetworkLoads = true - a genuine network failure that
        // every WebView version reports via onReceivedError, with no
        // interception semantics involved (the old null-data-intercept fixture
        // relied on API 26+ client read-back: on API 24-25 the engine cannot
        // read the view's client, so a fixture client on the view is replaced
        // and the page loads normally instead of failing).
        //
        // The explicit no-op delegate is required on API 24-25: the engine
        // only installs its per-load client (the onReceivedError ->
        // NETWORK_ERROR mapping) there when a delegate is provided, since the
        // view's own client cannot be read back.
        val failingEngine = engine(
            hiddenFactory = { context ->
                WebView(context).apply {
                    settings.blockNetworkLoads = true
                }
            },
            delegate = WebViewClient(),
        )
        val result = failingEngine.extractForAdd(context, testPageUrl)
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.NETWORK_ERROR),
            result,
        )
    }

    @Test
    fun hiddenExtractionTimeoutReturnsTimeoutWithinBudget() = runTest {
        // Refactor D12: a page that never delivers the API response times out
        // at the configured budget instead of running indefinitely.
        val start = System.currentTimeMillis()
        val result = engine(timeoutMs = 2_000)
            .extractForAdd(context, "https://appassets.androidplatform.net/SlowPage")
        val elapsed = System.currentTimeMillis() - start
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT),
            result,
        )
        assertTrue("timeout must fire near the 2s budget, took ${elapsed}ms", elapsed < 8_000)
    }

    // --- Refactor H3: the capture wrapper only accepts the official page
    // origin, the exact API host/path, and the expected voucher token, and
    // the bridge drops forged calls without the per-load nonce. All five
    // vectors the revision demands are covered: redirects, iframes, wrong
    // hosts, wrong tokens, and direct bridge calls from unrelated documents. ---

    @Test
    fun mainDocumentRedirectToDifferentVoucherIsNeverCaptured() = runTest {
        // The tapped URL redirects (client-side) to a DIFFERENT voucher's
        // page. The wrapper's token check binds capture to the expected
        // token, so the redirected page's payload must never be attributed
        // to the tapped row. A server-side 302 cannot be simulated -
        // WebResourceResponse rejects 3xx status codes and the asset loader
        // cannot serve redirects - so the fixture navigates via JS, which
        // exercises the same property. On the document-start path the
        // redirected document gets the tapped URL's expected token and
        // refuses the other voucher's API response; on the rewrite path the
        // redirected document carries no wrapper at all. Both yield "never
        // captured", so the assertion is not-Success rather than a specific
        // reason.
        val result = engine().extractForAdd(context, "https://appassets.androidplatform.net/RedirectFrom")
        assertTrue(
            "redirect to a different voucher must never be captured, got $result",
            result !is ExtractionResult.Success,
        )
    }

    @Test
    fun crossOriginIframeCannotDeliverPayload() = runTest {
        // The host page loads on the official fixture origin and embeds a
        // cross-origin iframe whose document tries forged bridge calls via
        // window.top / window.parent / its own window. Same-origin policy
        // blocks the cross-origin window references, the bridge object is
        // main-frame-only, and the per-load nonce is unknown to the frame -
        // nothing may be delivered (refactor H3 iframe vector).
        val result = engine(hiddenFactory = { webViewWithIframeForgeryClient() }, delegate = iframeForgeryClient())
            .extractForAdd(context, "https://appassets.androidplatform.net/IframeForgery")
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT),
            result,
        )
    }

    @Test
    fun nonOfficialOriginPageIsNeverCaptured() = runTest {
        // The page is served from evil.example.com (a non-official origin): the
        // wrapper bails out there, so neither its fetch nor its direct bridge
        // calls may be observed. On API 24-25 the delegate must be supplied
        // explicitly (the view's client cannot be read back).
        val result = engine(hiddenFactory = { wrongOriginWebView() }, delegate = wrongOriginClient())
            .extractForAdd(context, "https://evil.example.com/WrongOriginPage")
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT),
            result,
        )
    }

    @Test
    fun wrongApiHostIsNeverCaptured() = runTest {
        val result = engine().extractForAdd(context, "https://appassets.androidplatform.net/WrongHostFetch")
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT),
            result,
        )
    }

    @Test
    fun wrongTokenIsNeverCaptured() = runTest {
        val result = engine().extractForAdd(context, "https://appassets.androidplatform.net/WrongTokenFetch")
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT),
            result,
        )
    }

    @Test
    fun wrongApiPathIsNeverCaptured() = runTest {
        // The right host and token, but a trailing segment after the token:
        // the token-anchored match rejects it. (The live endpoint drifted to a
        // version-prefixed path '/v1/public/vouchers/groups/{token}', so the
        // match anchors on the token, not on the path start.)
        val result = engine().extractForAdd(context, "https://appassets.androidplatform.net/WrongPathFetch")
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT),
            result,
        )
    }

    @Test
    fun directBridgeCallWithoutNonceIsDropped() = runTest {
        // The page guesses the predictable bridge names and passes a fabricated
        // payload, but it cannot know the per-load nonce: the extraction must
        // time out instead of succeeding with the fabricated data.
        val result = engine().extractForAdd(context, "https://appassets.androidplatform.net/DirectBridgeCall")
        assertEquals(
            ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT),
            result,
        )
    }
}
