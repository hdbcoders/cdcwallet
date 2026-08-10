package com.cdcwallet.extraction

import com.cdcwallet.data.model.CategoryBalance
import com.cdcwallet.data.model.ValidityStatus
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
