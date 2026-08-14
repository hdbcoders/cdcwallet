package com.hdbcoders.cdcwallet.addflow

import com.hdbcoders.cdcwallet.data.token.VoucherToken
import java.net.URI

/**
 * Result of the voucher-link format check: whether the link is plausible, and
 * - when it is - the canonical token already derived from it (01 §1.4), so
 * callers never parse the URL a second time (refactor L10).
 */
data class VoucherLinkValidation(val isValid: Boolean, val token: String?)

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
    /** Validates the input and returns the derived canonical token with it. */
    fun validate(input: String): VoucherLinkValidation {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return VoucherLinkValidation(false, null)
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return VoucherLinkValidation(false, null)
        // https only: http, scheme-relative ("//host/..."), and every other
        // scheme are rejected. The voucher link is a bearer credential; only
        // the encrypted channel may ever carry it.
        if (uri.scheme != "https") return VoucherLinkValidation(false, null)
        if (!uri.host.equals(allowedHost, ignoreCase = true)) return VoucherLinkValidation(false, null)
        if (uri.userInfo != null) return VoucherLinkValidation(false, null)
        if (uri.port != -1) return VoucherLinkValidation(false, null)
        // Official path shape: exactly one token segment. The token segment
        // itself comes from the canonical function below, not from this check.
        val path = uri.path ?: return VoucherLinkValidation(false, null)
        val segments = path.trim('/').split('/')
        if (segments.size != 1 || segments[0].isBlank()) return VoucherLinkValidation(false, null)
        // Token segment extraction is the canonical contract (01 §1.4) - never
        // reimplemented here (00 §0.3.2).
        val token = VoucherToken.tokenFromUrl(trimmed)
            ?: return VoucherLinkValidation(false, null)
        return VoucherLinkValidation(true, token)
    }
}
