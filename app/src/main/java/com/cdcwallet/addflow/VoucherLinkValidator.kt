package com.cdcwallet.addflow

import com.cdcwallet.data.token.VoucherToken
import java.net.URI

/**
 * Format check for pasted/shared voucher links (spec 03 §3.2 step 1).
 *
 * Accepts only the official link shape (refactor H2 - confirmed production
 * policy):
 *  - `https` scheme exactly (no http, no scheme-relative URLs);
 *  - the exact host (matched case-insensitively - DNS hosts are
 *    case-insensitive), with no user-info and no explicit port;
 *  - the official voucher path shape: exactly one token segment
 *    (`/{token}`). Multi-segment paths are not voucher links.
 *
 * Query parameters are allowed and preserved in the stored URL; the token is
 * derived from the normalized path by the canonical token function
 * (01 §1.4) - never re-derived here (00 §0.3.2). Anything else is rejected
 * immediately, before any network activity or duplicate check.
 *
 * Test-only custom hosts go through the constructor seam (tests pass their
 * fixture host in), never through the production policy.
 */
class VoucherLinkValidator(
    private val allowedHost: String = "voucher.redeem.gov.sg",
) {
    /** True when the input looks like a voucher link worth proceeding on. */
    fun isPlausibleVoucherLink(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
        // https only: http, scheme-relative ("//host/..."), and every other
        // scheme are rejected. The voucher link is a bearer credential; only
        // the encrypted channel may ever carry it.
        if (uri.scheme != "https") return false
        if (!uri.host.equals(allowedHost, ignoreCase = true)) return false
        if (uri.userInfo != null) return false
        if (uri.port != -1) return false
        // Official path shape: exactly one token segment. The token segment
        // itself comes from the canonical function below, not from this check.
        val path = uri.path ?: return false
        val segments = path.trim('/').split('/')
        if (segments.size != 1 || segments[0].isBlank()) return false
        // Token segment extraction is the canonical contract (01 §1.4) - never
        // reimplemented here (00 §0.3.2).
        return VoucherToken.tokenFromUrl(trimmed) != null
    }
}
