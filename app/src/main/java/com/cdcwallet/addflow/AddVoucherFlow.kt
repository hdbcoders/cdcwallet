package com.cdcwallet.addflow

import android.content.Context
import com.cdcwallet.data.VoucherRepository
import com.cdcwallet.data.model.ValidityStatus
import com.cdcwallet.data.model.VoucherGroup
import com.cdcwallet.data.token.VoucherToken
import com.cdcwallet.extraction.ExtractionResult
import com.cdcwallet.extraction.VoucherExtractor
import java.time.Instant
import java.util.UUID

/**
 * Outcome of an add attempt (spec 03 §3.2).
 */
sealed class AddVoucherResult {
    /** Rejected at step 1 — not a plausible voucher link; nothing else ran. */
    data object InvalidFormat : AddVoucherResult()

    /** Rejected at step 2 — token already saved; no fetch was attempted. */
    data class Duplicate(val existing: VoucherGroup) : AddVoucherResult()

    /** Saved at step 4 with real extracted data. */
    data class Added(val voucher: VoucherGroup) : AddVoucherResult()

    /** Saved at step 5 with UNVERIFIED status because the fetch failed. */
    data class AddedUnverified(val voucher: VoucherGroup) : AddVoucherResult()
}

/**
 * Add-time orchestration (spec 03 §3.2) — the order is normative and deliberate:
 * format check → duplicate check → fetch → save (verified or UNVERIFIED).
 *
 * The duplicate check uses Package 1's canonical case-sensitive token comparison
 * and runs strictly before any extraction call, so a re-pasted link never
 * triggers a network request. Cancellation of the calling coroutine propagates
 * naturally through the suspend chain: no row is inserted after the caller has
 * abandoned the add, and Package 2 tears down the hidden WebView (02 §2.7).
 */
class AddVoucherFlow(
    private val repository: VoucherRepository,
    private val extractionEngine: VoucherExtractor,
    private val validator: VoucherLinkValidator = VoucherLinkValidator(),
) {

    suspend fun add(context: Context, rawUrl: String): AddVoucherResult {
        val url = rawUrl.trim()
        if (!validator.isPlausibleVoucherLink(url)) return AddVoucherResult.InvalidFormat

        val token = VoucherToken.tokenFromUrl(url)
            ?: return AddVoucherResult.InvalidFormat
        val existing = repository.findByToken(token)
        if (existing != null) return AddVoucherResult.Duplicate(existing)

        val result = extractionEngine.extractForAdd(context, url)
        val now = Instant.now()
        val base = VoucherGroup(
            id = UUID.randomUUID().toString(),
            token = token,
            url = url,
            campaignName = token,
            validityStatus = ValidityStatus.UNVERIFIED,
            expiryDate = null,
            categoryBalances = emptyList(),
            dateAdded = now,
            lastRefreshedAt = null,
            lastRefreshError = null,
            isArchived = false,
        )
        return when (result) {
            is ExtractionResult.Success -> {
                val voucher = base.copy(
                    campaignName = result.campaignName,
                    validityStatus = result.validityStatus,
                    expiryDate = result.expiryDate,
                    categoryBalances = result.categoryBalances,
                    lastRefreshedAt = now,
                )
                repository.insert(voucher)
                AddVoucherResult.Added(voucher)
            }

            is ExtractionResult.Failure -> {
                val voucher = base.copy(lastRefreshError = result.reason.name)
                repository.insert(voucher)
                AddVoucherResult.AddedUnverified(voucher)
            }
        }
    }
}
