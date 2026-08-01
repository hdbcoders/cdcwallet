package com.cdcvouchers.extraction

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal const val BRIDGE_NAME = "RedeemBridge"

/**
 * Injected at document-start. Wraps the page's own fetch/XHR and, for responses
 * matching the voucher-groups endpoint, extracts ONLY the whitelisted fields
 * (spec 02 §2.6) and delivers them to the native bridge. Nothing else from the
 * response is ever read or passed out of the page.
 */
internal const val INJECTION_SCRIPT =
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
internal fun installInjection(webView: WebView, forceFallback: Boolean): InjectionPath {
    if (!forceFallback && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        // "*" = any http(s) origin; an empty set would restrict the script to
        // non-http(s) documents only (about:blank, data:), where the real page
        // would never load.
        val handler = WebViewCompat.addDocumentStartJavaScript(webView, INJECTION_SCRIPT, setOf("*"))
        return InjectionPath.DocumentStart(handler)
    }
    val existing = webView.webViewClient
    webView.webViewClient = HtmlRewritingClient(existing)
    return InjectionPath.HtmlRewrite
}

/**
 * Fallback injection: rewrites the main HTML document to embed the wrapper script
 * ahead of the page's own bundle. Delegates all other client behavior to the
 * previous client, and rewrites the delegate's own response when one is supplied.
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
        if (mainDocument && delegateResponse == null && isFetchable(request)) {
            val response = fetchAndRewrite(request)
            if (response != null) {
                rewrote = true
                return response
            }
            return null
        }
        return delegateResponse
    }

    private fun isFetchable(request: WebResourceRequest): Boolean {
        val scheme = request.url.scheme ?: return false
        return scheme == "https" || scheme == "http"
    }

    private fun fetchAndRewrite(request: WebResourceRequest): WebResourceResponse? {
        val connection = runCatching {
            (URL(request.url.toString()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
            }
        }.getOrNull() ?: return null
        return try {
            val stream = runCatching { connection.inputStream }.getOrNull() ?: return null
            val mimeType = connection.contentType?.substringBefore(';')?.trim().orEmpty()
            if (!mimeType.startsWith("text/html")) return null
            val encoding = connection.contentEncoding ?: "UTF-8"
            rewriteHtml(stream, encoding)
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
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
