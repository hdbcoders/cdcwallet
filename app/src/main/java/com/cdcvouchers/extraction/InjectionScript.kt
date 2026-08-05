package com.cdcvouchers.extraction

import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.io.InputStream

internal const val BRIDGE_NAME = "RedeemBridge"

/**
 * Injected at document-start. Wraps the page's own fetch/XHR and, for responses
 * matching the voucher-groups endpoint, extracts ONLY the whitelisted fields
 * (spec 02 §2.6) and delivers them to the native bridge. Nothing else from the
 * response is ever read or passed out of the page.
 */
internal const val CAPTURE_SCRIPT =
    """
    (function () {
      var pathPattern = /\/vouchers\/groups\//;
      var captured = false;

      function toAbsoluteUrl(raw) {
        try {
          return new URL(String(raw), window.location.href).href;
        } catch (e) {
          return String(raw);
        }
      }

      function isTargetUrl(url) {
        return pathPattern.test(url);
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
        if (window.RedeemBridge && typeof window.RedeemBridge.onData === 'function') {
          window.RedeemBridge.onData(JSON.stringify(payload));
        }
      }

      function fail(kind) {
        if (captured) return;
        captured = true;
        if (window.RedeemBridge && typeof window.RedeemBridge.onError === 'function') {
          window.RedeemBridge.onError(kind);
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
 * FRAGILE WORKAROUND (do not extend): some WebView builds resolve `100vh`
 * to 0, collapsing the SPA's fixed shell. This re-pins the container height
 * in px. `.css-14jkxbw` is a RedeemSG React build artifact that WILL change;
 * the heuristic fallback below is what actually survives rebuilds. Removable
 * once the underlying 100vh bug is fixed.
 */
internal const val VIEWPORT_FIX_SCRIPT =
    """
    (function () {
      var FIX_ID = 'cdcv-viewport-fix';
      function measured() {
        var ih = window.innerHeight;
        if (!(ih > 200)) return null;
        var root = document.getElementById('root');
        var shell = null;
        if (root) {
          var descendants = root.getElementsByTagName('div');
          for (var i = 0; i < descendants.length; i++) {
            var el = descendants[i];
            if (getComputedStyle(el).position !== 'fixed') continue;
            var r = el.getBoundingClientRect();
            if (r.height > ih * 0.5) { shell = el; break; }
          }
        }
        if (!shell) return null;
        var parent = shell.parentElement;
        if (!parent) return null;
        var top = shell.getBoundingClientRect().top;
        var masthead = 0;
        var container = null;
        var siblings = parent.children;
        for (var j = 0; j < siblings.length; j++) {
          var c = siblings[j];
          if (c === shell) continue;
          var cs = getComputedStyle(c);
          var hid = cs.overflow === 'hidden' || cs.overflowY === 'hidden' || cs.overflowX === 'hidden';
          var cr = c.getBoundingClientRect();
          if (!container && hid && cr.height < ih) { container = c; }
          else if (!container) { masthead += cr.height; }
        }
        if (!container) return null;
        var desired = Math.round(ih - top - masthead);
        return desired > 100 ? desired : null;
      }
      function apply() {
        var now = Date.now();
        if (now - lastApply < 500) return;
        lastApply = now;
        var desired = measured();
        if (desired === null) return;
        var styleEl = document.getElementById(FIX_ID);
        if (!styleEl) {
          styleEl = document.createElement('style');
          styleEl.id = FIX_ID;
          var head = document.head || document.documentElement;
          head.appendChild(styleEl);
        }
        var container = document.querySelector('.css-14jkxbw');
        var cls = container
          ? '.css-14jkxbw'
          : (function () {
              var root = document.getElementById('root');
              if (!root) return '';
              var divs = root.getElementsByTagName('div');
              for (var i = 0; i < divs.length; i++) {
                var st = getComputedStyle(divs[i]);
                if (st.position !== 'fixed') continue;
                var r = divs[i].getBoundingClientRect();
                if (r.height <= window.innerHeight * 0.5) continue;
                var parent = divs[i].parentElement;
                if (!parent) continue;
                for (var j = 0; j < parent.children.length; j++) {
                  var c = parent.children[j];
                  if (c === divs[i]) continue;
                  var cs = getComputedStyle(c);
                  if (cs.overflow === 'hidden' || cs.overflowY === 'hidden' || cs.overflowX === 'hidden') {
                    var first = (c.className || '').toString().split(' ')[0];
                    return first ? '.' + first : '';
                  }
                }
              }
              return '';
            })();
        if (!cls) return;
        styleEl.textContent = cls + '{height:' + desired + 'px !important;min-height:' + desired + 'px !important}';
      }
      function boot() {
        if (document.readyState === 'loading') {
          document.addEventListener('DOMContentLoaded', apply);
        } else { apply(); }
        window.addEventListener('resize', apply);
        setTimeout(apply, 1500);
        setTimeout(apply, 4000);
        setTimeout(apply, 9000);
        setTimeout(apply, 20000);
        setTimeout(apply, 40000);
        if (document.documentElement) {
          new MutationObserver(function () { apply(); })
            .observe(document.documentElement, { childList: true, subtree: true });
        }
      }
      var lastApply = 0;
      boot();
    })();
    """

/**
 * Modal height fix: the page's Chakra modal sizes itself with `vh` units
 * (`.chakra-modal__overlay` uses 100vw/100vh, the content uses max-height:vh),
 * but in Android WebView the `vh` unit can resolve to 0 (a WebView quirk —
 * `vw` works, `vh` doesn't), so the History modal renders at 0 height: the
 * data is in the DOM but nothing paints. For `position:fixed` elements,
 * `%`-based heights resolve against the viewport instead of the broken `vh`,
 * so we inject explicit `%` heights on the stable Chakra modal classes. Runs
 * on DOMContentLoaded and re-applies on open, since Chakra mounts the modal
 * lazily when the user taps "History".
 */
internal const val MODAL_HEIGHT_FIX_SCRIPT =
    """
    (function () {
      var FIX_ID = 'cdcv-modal-height-fix';
      var RULES =
        '.chakra-modal__overlay{height:100% !important}' +
        '.chakra-modal__content-container{height:100% !important}' +
        '.chakra-modal__content{max-height:85% !important;height:auto !important}' +
        '.chakra-modal__header{flex:0 0 auto}' +
        '.chakra-modal__body{overflow-y:auto;flex:1 1 auto;min-height:0}';
      function apply() {
        if (!document.querySelector('.chakra-modal__overlay')) return;
        var styleEl = document.getElementById(FIX_ID);
        if (!styleEl) {
          styleEl = document.createElement('style');
          styleEl.id = FIX_ID;
          styleEl.textContent = RULES;
          (document.head || document.documentElement).appendChild(styleEl);
        }
      }
      function boot() {
        if (document.readyState === 'loading') {
          document.addEventListener('DOMContentLoaded', apply);
        } else { apply(); }
        window.addEventListener('resize', apply);
        setTimeout(apply, 1500);
        setTimeout(apply, 4000);
        if (document.documentElement) {
          new MutationObserver(function () { apply(); })
            .observe(document.documentElement, { childList: true, subtree: true });
        }
      }
      boot();
    })();
    """

/** Capture wrapper + viewport fix (see notes on each). */
internal val INJECTION_SCRIPT: String =
    CAPTURE_SCRIPT + "\n" + VIEWPORT_FIX_SCRIPT + "\n" + MODAL_HEIGHT_FIX_SCRIPT

internal val INJECTION_SCRIPT_TAG: String = "<script>$INJECTION_SCRIPT</script>"

internal sealed interface InjectionPath {
    data class DocumentStart(val scriptHandler: androidx.webkit.ScriptHandler) : InjectionPath
    data object HtmlRewrite : InjectionPath
}

/**
 * Installs the wrapper ahead of the page's own scripts (spec 02 §2.5):
 * primary path is addDocumentStartJavaScript; fallback is HTML rewriting via
 * shouldInterceptRequest, which works on every supported WebView version.
 */
internal fun installInjection(
    webView: WebView,
    forceFallback: Boolean,
    fallbackInjectionDelegate: WebViewClient? = null,
): InjectionPath {
    if (!forceFallback && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        // "*" = any http(s) origin; an empty set would restrict the script to
        // non-http(s) documents only (about:blank, data:), where the real page
        // would never load.
        val handler = WebViewCompat.addDocumentStartJavaScript(webView, INJECTION_SCRIPT, setOf("*"))
        return InjectionPath.DocumentStart(handler)
    }
    // API 24–25 (or a stale WebView) fallback. On API 26+ the prior client
    // (e.g. an asset-loader client under test) is read back and chained as the
    // delegate so its interception keeps working; on API 24–25 there is no
    // getter, so the caller supplies it explicitly — production never sets a
    // client before this point, so this matters only for tests.
    val delegate = if (Build.VERSION.SDK_INT >= 26) {
        webView.webViewClient
    } else {
        fallbackInjectionDelegate
    }
    webView.webViewClient = HtmlRewritingClient(delegate)
    return InjectionPath.HtmlRewrite
}

/**
 * Fallback injection: rewrites the main HTML document to embed the wrapper script
 * ahead of the page's own bundle, but ONLY when a delegate already supplies that
 * document (test/asset loader). No native HTTP client is ever opened to a
 * RedeemSG host (hard rule 02 §2.1): when there is no delegate response, the
 * request falls through to the WebView's own network stack, which loads the real
 * page without injection (extraction degrades to fail-soft on legacy WebViews).
 */
private class HtmlRewritingClient(
    private val delegate: WebViewClient?,
) : WebViewClient() {

    private var rewrote = false

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val delegateResponse = delegate?.shouldInterceptRequest(view, request)
        val mainDocument = request.isForMainFrame && !rewrote
        if (mainDocument && delegateResponse != null && delegateResponse.data != null) {
            val rewritten = rewriteHtml(delegateResponse.data, delegateResponse.encoding)
            if (rewritten != null) {
                rewrote = true
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
                html.substring(0, tagEnd + 1) + INJECTION_SCRIPT_TAG + html.substring(tagEnd + 1)
            } else {
                html
            }
        } else {
            INJECTION_SCRIPT_TAG + html
        }
        return WebResourceResponse(
            "text/html",
            encoding ?: "UTF-8",
            ByteArrayInputStream(modified.toByteArray(Charsets.UTF_8)),
        )
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        delegate?.shouldOverrideUrlLoading(view, request) ?: false

    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        delegate?.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        delegate?.onPageFinished(view, url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError,
    ) {
        delegate?.onReceivedError(view, request, error)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        delegate?.onReceivedHttpError(view, request, errorResponse)
    }
}
