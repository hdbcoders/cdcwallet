package com.cdcwallet.addflow

import com.cdcwallet.data.token.VoucherToken
import java.net.URI

/**
 * Format check for pasted/shared voucher links (spec 03 §3.2 step 1).
 *
 * Accepts only `https://voucher.redeem.gov.sg/{token}` (http tolerated, host
 * matched case-insensitively — DNS hosts are case-insensitive). Query params are
 * allowed here and stripped later by the canonical token function. Anything else
 * is rejected immediately, before any network activity or duplicate check.
 */
class VoucherLinkValidator(
    private val allowedHost: String = "voucher.redeem.gov.sg",
) {
    /** True when the input looks like a voucher link worth proceeding on. */
    fun isPlausibleVoucherLink(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
        if (uri.scheme != null && uri.scheme != "http" && uri.scheme != "https") return false
        if (!uri.host.equals(allowedHost, ignoreCase = true)) return false
        // Token segment extraction is the canonical contract (01 §1.4) — never
        // reimplemented here (00 §0.3.2).
        return VoucherToken.tokenFromUrl(trimmed) != null
    }
}
