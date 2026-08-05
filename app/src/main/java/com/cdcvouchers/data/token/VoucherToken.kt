package com.cdcvouchers.data.token

import java.net.URI

/**
 * Canonical token-comparison implementation (spec 01 §1.4). Single shared contract
 * used by Package 3 (add-time duplicate check) and Package 6 (import merge dedup).
 * Never lowercase, never reimplemented elsewhere.
 */
object VoucherToken {

    /**
     * Strip query parameters and fragment (host + path only, per 01 §1.4).
     * The fragment is intentionally dropped too: it is never sent to the
     * server, so two URLs differing only in a fragment identify the same
     * voucher and must compare as equal.
     */
    fun normalize(url: String): String =
        url.substringBefore('?').substringBefore('#')

    /**
     * The token segment of the normalized path — the last non-blank path segment.
     * Returns null for URLs that don't parse or have no token segment.
     */
    fun tokenFromUrl(url: String): String? {
        val path = runCatching { URI(normalize(url)).path }.getOrNull() ?: return null
        val segment = path.trim('/').substringAfterLast('/').trim()
        return segment.takeIf { it.isNotEmpty() }
    }

    /** Case-sensitive exact match (REQ-7). */
    fun isDuplicate(newToken: String, existingToken: String): Boolean =
        newToken == existingToken
}
