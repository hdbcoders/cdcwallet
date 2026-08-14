package com.hdbcoders.cdcwallet.extraction

import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * The capture script, as a template. Installed at document-start; wraps the
 * page's own fetch/XHR and, for responses matching the voucher-groups API,
 * extracts ONLY the whitelisted fields (spec 02 §2.6) and delivers them to the
 * native bridge. Nothing else from the response is ever read or passed out of
 * the page.
 *
 * Three values are substituted per load ([captureScriptFor]):
 *  - `__EXPECTED_HOST__` - the exact API host the capture may match (hard
 *    rule 02 §2.1: only the real RedeemSG API; test fixtures override via the
 *    engine's `targetApiHost` seam);
 *  - `__EXPECTED_TOKEN__` - the voucher token of THIS load. A page's request
 *    only counts when its path token matches, so a payload from a different
 *    voucher (stale document, wrong row URL) is never captured (refactor H3);
 *  - `__BRIDGE_NAME__` - the per-load unique bridge name (refactor H1). A
 *    previous load's document looks up its own name, which is removed at that
 *    load's teardown, so a late callback can never land in the next load's
 *    bridge;
 *  - `__NONCE__` - a per-load random value that every bridge call must carry
 *    (refactor H3). The native side drops any call without the exact nonce, so
 *    an unrelated document cannot forge a payload even if it guesses the
 *    (predictable) bridge name;
 *  - `__ALLOWED_PAGE_ORIGIN__` - the only page origin the wrapper may
 *    activate on (refactor H3). The injection itself uses the `*` origin rule
 *    on purpose: Google WebView 150 (2025) does not run document-start
 *    scripts for exact-origin rule sets (verified empirically on
 *    WebView 150.0.7871.181 - only `*` fires), so the origin restriction is
 *    enforced inside the script instead: on any other origin the wrapper
 *    bails out immediately and never wraps fetch/XHR.
 */
private const val CAPTURE_SCRIPT_TEMPLATE =
    """
    (function () {
      var ALLOWED_PAGE_ORIGIN = __ALLOWED_PAGE_ORIGIN__;
      if (window.location.origin !== ALLOWED_PAGE_ORIGIN) return;
      var EXPECTED_HOST = __EXPECTED_HOST__;
      var EXPECTED_PATH = '/vouchers/groups/';
      var EXPECTED_TOKEN = __EXPECTED_TOKEN__;
      var BRIDGE_NAME = __BRIDGE_NAME__;
      var NONCE = __NONCE__;
      var captured = false;

      function toAbsoluteUrl(raw) {
        try {
          return new URL(String(raw), window.location.href).href;
        } catch (e) {
          return String(raw);
        }
      }

      // Token-anchored matching (refactor H3 + live-shape revision): the exact
      // API host, a path CONTAINING '/vouchers/groups/', and the expected
      // token as that prefix's immediate, final segment. The live endpoint has
      // drifted to '/v1/public/vouchers/groups/{token}' (observed 2026-08-12),
      // so the prefix position is deliberately not anchored to the path start.
      function isTargetUrl(url) {
        var u = null;
        try { u = new URL(url); } catch (e) { return false; }
        if (u.hostname !== EXPECTED_HOST) return false;
        var idx = u.pathname.indexOf(EXPECTED_PATH);
        if (idx === -1) return false;
        var token = u.pathname.substring(idx + EXPECTED_PATH.length);
        if (token.length === 0 || token.indexOf('/') !== -1) return false;
        return token === EXPECTED_TOKEN;
      }

      function whitelist(json) {
        if (!json || typeof json !== 'object') return null;
        var campaign = json.campaign;
        if (!campaign || typeof campaign !== 'object') return null;
        var vouchers = json.vouchers;
        if (!Array.isArray(vouchers) && json.data && typeof json.data === 'object') {
          vouchers = json.data.vouchers;
        }
        if (!Array.isArray(vouchers)) return null;
        var out = [];
        for (var i = 0; i < vouchers.length; i++) {
          var v = vouchers[i];
          if (!v || typeof v !== 'object') continue;
          out.push({
            state: typeof v.state === 'string' ? v.state : null,
            voucher_value: typeof v.voucher_value === 'number' || typeof v.voucher_value === 'string' ? v.voucher_value : null,
            type: typeof v.type === 'string' ? v.type : null
          });
        }
        return {
          campaign: {
            name: typeof campaign.name === 'string' ? campaign.name : null,
            validity: typeof campaign.validity === 'string' ? campaign.validity : null,
            validity_end: typeof campaign.validity_end === 'string' ? campaign.validity_end : null
          },
          vouchers: out
        };
      }

      function deliver(json) {
        if (captured) return;
        var payload = whitelist(json);
        if (payload === null) {
          fail('parse');
          return;
        }
        captured = true;
        var bridge = window[BRIDGE_NAME];
        if (bridge && typeof bridge.onData === 'function') {
          bridge.onData(JSON.stringify(payload), NONCE);
        }
      }

      function fail(kind) {
        if (captured) return;
        captured = true;
        var bridge = window[BRIDGE_NAME];
        if (bridge && typeof bridge.onError === 'function') {
          bridge.onError(kind, NONCE);
        }
      }

      function requestUrl(input) {
        if (input && typeof input === 'string') return input;
        if (input && typeof input === 'object' && input.url) return input.url;
        return '';
      }

      var origFetch = window.fetch;
      if (typeof origFetch === 'function') {
        window.fetch = function (input, init) {
          var url = toAbsoluteUrl(requestUrl(input));
          var target = isTargetUrl(url);
          try {
            return origFetch.call(this, input, init).then(function (response) {
              if (target) {
                var copy = response.clone();
                copy.text().then(function (text) {
                  var parsed = null;
                  try { parsed = JSON.parse(text); } catch (e) { parsed = null; }
                  if (parsed === null) { fail('parse'); return; }
                  deliver(parsed);
                }).catch(function () { fail('network'); });
              }
              return response;
            }).catch(function (err) {
              if (target) fail('network');
              throw err;
            });
          } catch (e) {
            if (target) fail('network');
            throw e;
          }
        };
      }

      var proto = XMLHttpRequest.prototype;
      var origOpen = proto.open;
      var origSend = proto.send;
      proto.open = function (method, url) {
        this.__redeemRequestUrl = url;
        return origOpen.apply(this, arguments);
      };
      proto.send = function () {
        var xhr = this;
        var target = isTargetUrl(toAbsoluteUrl(xhr.__redeemRequestUrl || ''));
        xhr.addEventListener('load', function () {
          if (!target) return;
          var parsed = null;
          try { parsed = JSON.parse(xhr.responseText); } catch (e) { parsed = null; }
          if (parsed === null) { fail('parse'); return; }
          deliver(parsed);
        });
        xhr.addEventListener('error', function () {
          if (target) fail('network');
        });
        return origSend.apply(this, arguments);
      };
    })();
    """

/**
 * Per-load capture script with the expected token, the unique bridge name, the
 * per-load nonce, the API host, and the allowed page origin substituted. The
 * values are embedded as JS string literals (escaped), so any token content is
 * safe.
 */
internal fun captureScriptFor(
    expectedToken: String,
    bridgeName: String,
    nonce: String,
    targetApiHost: String,
    allowedPageOrigin: String,
): String = CAPTURE_SCRIPT_TEMPLATE
    .replace("__ALLOWED_PAGE_ORIGIN__", jsStringLiteral(allowedPageOrigin))
    .replace("__EXPECTED_HOST__", jsStringLiteral(targetApiHost))
    .replace("__EXPECTED_TOKEN__", jsStringLiteral(expectedToken))
    .replace("__BRIDGE_NAME__", jsStringLiteral(bridgeName))
    .replace("__NONCE__", jsStringLiteral(nonce))

/** The capture script wrapped in a `<script>` tag for the HTML-rewrite fallback. */
internal fun injectionScriptTagFor(
    expectedToken: String,
    bridgeName: String,
    nonce: String,
    targetApiHost: String,
    allowedPageOrigin: String,
): String = "<script>${captureScriptFor(expectedToken, bridgeName, nonce, targetApiHost, allowedPageOrigin)}</script>"

private fun jsStringLiteral(value: String): String =
    "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"

internal sealed interface InjectionPath {
    data class DocumentStart(val scriptHandler: ScriptHandler) : InjectionPath
    data object HtmlRewrite : InjectionPath

    /**
     * No injection is possible on this WebView build: document-start scripts
     * are unsupported AND there is no delegate response to HTML-rewrite. The
     * page still loads normally; extraction reports a failure (refactor H5).
     */
    data object Unavailable : InjectionPath
}

/**
 * Installs the wrapper ahead of the page's own scripts (spec 02 §2.5):
 * primary path is addDocumentStartJavaScript; fallback is HTML rewriting via
 * shouldInterceptRequest, which requires a delegate that actually supplies the
 * main document (test/asset loader) - production has none. When the rewrite
 * client observes a main-document request that no delegate intercepts, it
 * invokes [onNoInjection] exactly once so the extraction can fail fast
 * (refactor H5) instead of burning the timeout; the page itself still loads
 * through the WebView's own network stack.
 *
 * The document-start rule set is always `*`: Google WebView 150 (2025) does
 * not run document-start scripts for exact-origin rule sets (verified
 * empirically on WebView 150.0.7871.181), so the origin restriction is
 * enforced inside the script against [allowedPageOrigin] instead (refactor
 * H3) - same security property, version-proof.
 */
internal fun installInjection(
    webView: WebView,
    forceFallback: Boolean,
    fallbackInjectionDelegate: WebViewClient? = null,
    expectedToken: String,
    bridgeName: String,
    nonce: String,
    targetApiHost: String,
    allowedPageOrigin: String,
    onNoInjection: () -> Unit,
): InjectionPath {
    if (!forceFallback && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        val handler = WebViewCompat.addDocumentStartJavaScript(
            webView,
            captureScriptFor(expectedToken, bridgeName, nonce, targetApiHost, allowedPageOrigin),
            setOf("*"),
        )
        return InjectionPath.DocumentStart(handler)
    }
    // API 24–25 (or a stale WebView) fallback. On API 26+ the prior client
    // (e.g. an asset-loader client under test, or the engine's own extraction
    // client) is read back and chained as the delegate so its interception
    // keeps working; on API 24–25 there is no getter, so the caller supplies
    // it explicitly. A delegate that never actually intercepts the main
    // document (production) is detected at rewrite time via [onNoInjection].
    val delegate = if (Build.VERSION.SDK_INT >= 26) {
        webView.webViewClient
    } else {
        fallbackInjectionDelegate
    }
    if (delegate == null) return InjectionPath.Unavailable
    webView.webViewClient = HtmlRewritingClient(
        delegate,
        injectionScriptTagFor(expectedToken, bridgeName, nonce, targetApiHost, allowedPageOrigin),
        onNoInjection,
    )
    return InjectionPath.HtmlRewrite
}

/**
 * Fallback injection: rewrites the main HTML document to embed the wrapper script
 * ahead of the page's own bundle, but ONLY when a delegate already supplies that
 * document (test/asset loader). No native HTTP client is ever opened to a
 * RedeemSG host (hard rule 02 §2.1): when there is no delegate response, the
 * request falls through to the WebView's own network stack, which loads the real
 * page without injection (refactor H5: the engine reports this as a failure
 * instead of waiting out the timeout).
 */
private class HtmlRewritingClient(
    private val delegate: WebViewClient,
    private val scriptTag: String,
    private val onNoInjection: () -> Unit,
) : WebViewClient() {

    private var rewrote = false

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val delegateResponse = delegate.shouldInterceptRequest(view, request)
        val mainDocument = request.isForMainFrame && !rewrote
        if (mainDocument) {
            rewrote = true
            if (delegateResponse?.data == null) {
                // The delegate does not supply the main document (production
                // default, refactor H5): no injection is possible on this
                // load. Report it exactly once - the extraction fails fast
                // while the page still loads natively.
                onNoInjection()
                return null
            }
            val rewritten = rewriteHtml(delegateResponse.data, delegateResponse.encoding)
            if (rewritten != null) {
                return rewritten
            }
            return delegateResponse
        }
        return delegateResponse
    }

    private fun rewriteHtml(input: InputStream, encoding: String?): WebResourceResponse? {
        val html = runCatching { input.readBytes().toString(Charsets.UTF_8) }.getOrNull()
            ?: return null
        val headStart = html.indexOf("<head", ignoreCase = true)
        val modified = if (headStart >= 0) {
            val tagEnd = html.indexOf('>', headStart)
            if (tagEnd >= 0) {
                html.substring(0, tagEnd + 1) + scriptTag + html.substring(tagEnd + 1)
            } else {
                html
            }
        } else {
            scriptTag + html
        }
        return WebResourceResponse(
            "text/html",
            encoding ?: "UTF-8",
            ByteArrayInputStream(modified.toByteArray(Charsets.UTF_8)),
        )
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        delegate.shouldOverrideUrlLoading(view, request)

    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        delegate.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        delegate.onPageFinished(view, url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError,
    ) {
        delegate.onReceivedError(view, request, error)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        delegate.onReceivedHttpError(view, request, errorResponse)
    }
}
