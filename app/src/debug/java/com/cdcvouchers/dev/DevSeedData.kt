package com.cdcvouchers.dev

import com.cdcvouchers.data.model.CategoryBalance
import com.cdcvouchers.data.model.ValidityStatus
import com.cdcvouchers.data.model.VoucherGroup
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Debug-only test fixtures (com.cdcvouchers.dev lives under `src/debug`, so
 * this never ships in a release build). Each row in the plan maps to one
 * VoucherGroup; campaign names are arbitrary fruits. Tokens/urls are unique so
 * reseeding can delete-and-reinsert without colliding with the caller's real
 * vouchers.
 */
object DevSeedData {

    private fun voucher(
        id: String,
        campaignName: String,
        status: ValidityStatus,
        expiry: LocalDate?,
        balances: List<CategoryBalance>,
        url: String = "https://test.local/$id",
        token: String = id,
    ) = VoucherGroup(
        id = id,
        token = token,
        url = url,
        campaignName = campaignName,
        validityStatus = status,
        expiryDate = expiry,
        categoryBalances = balances,
        dateAdded = Instant.now(),
        lastRefreshedAt = null,
        lastRefreshError = null,
    )

    /** The 6 fixed rows. Stable ids/tokens so reseed is idempotent. */
    val vouchers: List<VoucherGroup> = listOf(
        // 1. Expired 31 Dec 2025, no balance.
        voucher(
            "dev-expired",
            "Papaya",
            ValidityStatus.EXPIRED,
            LocalDate.of(2025, 12, 31),
            emptyList(),
        ),
        // 2. Climate $0, exp 2027-12-31.
        voucher(
            "dev-climate-zero",
            "Banana",
            ValidityStatus.ACTIVE,
            LocalDate.of(2027, 12, 31),
            listOf(CategoryBalance("Climate", BigDecimal("0"))),
        ),
        // 3. Climate $80, exp 2027-12-31.
        voucher(
            "dev-climate-80",
            "Mango",
            ValidityStatus.ACTIVE,
            LocalDate.of(2027, 12, 31),
            listOf(CategoryBalance("Climate", BigDecimal("80"))),
        ),
        // 4. Heartland $100 + Supermarket $150, exp 2027-12-31.
        voucher(
            "dev-hs-100-150",
            "Durian",
            ValidityStatus.ACTIVE,
            LocalDate.of(2026, 8, 31),
            listOf(
                CategoryBalance("Heartland", BigDecimal("100")),
                CategoryBalance("Supermarket", BigDecimal("150")),
            ),
        ),
        // 5. Heartland $100 + Supermarket $0, exp 2027-12-31.
        voucher(
            "dev-hs-100-0",
            "Rambutan",
            ValidityStatus.ACTIVE,
            LocalDate.of(2027, 12, 31),
            listOf(
                CategoryBalance("Heartland", BigDecimal("100")),
                CategoryBalance("Supermarket", BigDecimal("0")),
            ),
        ),
        // 6. Heartland $0 + Supermarket $150, exp 2027-12-31.
        voucher(
            "dev-hs-0-150",
            "Lychee",
            ValidityStatus.ACTIVE,
            LocalDate.of(2027, 12, 31),
            listOf(
                CategoryBalance("Heartland", BigDecimal("0")),
                CategoryBalance("Supermarket", BigDecimal("150")),
            ),
        ),
        // 7. REAL zero-balance RedeemSG test link — URL/token intentionally NOT
        // stored in git (personal link, see local note in docs/ or AGENTS.md).
        // Restore the real URL locally if needed for tests:
        //   url   = "https://voucher.redeem.gov.sg/<TOKEN>?lang=en-US"
        //   token = "<TOKEN>"
        voucher(
            "dev-redeem-zero",
            "Zero-Balance Test",
            ValidityStatus.ACTIVE,
            null,
            emptyList(),
            url = "https://test.local/dev-redeem-zero",
            token = "dev-redeem-zero",
        ),
    )
}