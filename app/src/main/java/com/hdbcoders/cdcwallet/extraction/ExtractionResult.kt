package com.hdbcoders.cdcwallet.extraction

import com.hdbcoders.cdcwallet.data.model.CategoryBalance
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import java.time.LocalDate

/**
 * Public interface of the extraction engine (spec 02 §2.9). Packages 3 and 4 call
 * into this package without knowing its internals.
 */
sealed class ExtractionResult {
    data class Success(
        val campaignName: String,
        val validityStatus: ValidityStatus,
        val expiryDate: LocalDate?,
        val categoryBalances: List<CategoryBalance>,
    ) : ExtractionResult()

    data class Failure(val reason: FailureReason) : ExtractionResult()

    enum class FailureReason { PARSE_ERROR, NETWORK_ERROR, TIMEOUT }
}

/**
 * The persisted failure classification (spec 02 §2.7, refactor M5): a 10s
 * timeout with no interception is treated as a parse error - the page yielded
 * no parseable data. BOTH call sites (add-time save and tap-refresh failure
 * recording) persist this SAME code, so the stored value never differs by
 * path.
 */
val ExtractionResult.FailureReason.persistedName: String
    get() = if (this == ExtractionResult.FailureReason.TIMEOUT) {
        ExtractionResult.FailureReason.PARSE_ERROR.name
    } else {
        name
    }
