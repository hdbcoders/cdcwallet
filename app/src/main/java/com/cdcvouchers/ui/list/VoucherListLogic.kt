package com.cdcvouchers.ui.list

import com.cdcvouchers.data.model.CategoryBalance
import com.cdcvouchers.data.model.ValidityStatus
import com.cdcvouchers.data.model.VoucherGroup
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Comparator

/**
 * Pure list logic for the main voucher list (spec 04). Kept free of Compose so
 * it is unit-testable: sorting, badge derivation, aggregate summary.
 */

/** Badge state derived from validityStatus + expiryDate together (spec 04 §4.2). */
sealed interface BadgeState {
    data object Unverified : BadgeState
    data object NotStarted : BadgeState
    data object Expired : BadgeState

    /**
     * ACTIVE entry whose remaining value is zero — nothing left to spend.
     * Rendered red with the same warning icon as Expired.
     */
    data object NoBalance : BadgeState

    /** ACTIVE entries only; daysRemaining is null when the API omitted an end date. */
    data class Active(val daysRemaining: Long?, val urgency: Urgency) : BadgeState
}

enum class Urgency { URGENT, SOON, FINE }

/**
 * Spec 04 §4.1: sort priority —
 *   1. UNVERIFIED pinned above all others (no expiry/balance to sort by),
 *   2. vouchers WITH balance remaining come before those with none,
 *   3. within each, soonest expiry first.
 */
fun sortActive(vouchers: List<VoucherGroup>): List<VoucherGroup> =
    vouchers.sortedWith(
        compareByDescending<VoucherGroup> { it.validityStatus == ValidityStatus.UNVERIFIED }
            .thenByDescending { voucher -> totalRemaining(voucher) > BigDecimal.ZERO }
            .thenBy(nullsLast()) { it.expiryDate }
            .thenBy { it.id },
    )

/**
 * Spec 04 §4.2: urgency driven by validityStatus + expiryDate together. A
 * NOT_STARTED entry is never styled urgent regardless of its expiryDate; an
 * ACTIVE entry whose date has passed reads as Expired (stale cache signal).
 */
fun badgeState(voucher: VoucherGroup, today: LocalDate = LocalDate.now()): BadgeState =
    when (voucher.validityStatus) {
        ValidityStatus.UNVERIFIED -> BadgeState.Unverified
        ValidityStatus.NOT_STARTED -> BadgeState.NotStarted
        ValidityStatus.EXPIRED -> BadgeState.Expired
        ValidityStatus.ACTIVE -> {
            val expiry = voucher.expiryDate
            if (expiry == null) {
                // A spent voucher is terminal regardless of a missing end
                // date; only fall through to urgency when value remains.
                if (totalRemaining(voucher) == BigDecimal.ZERO) {
                    BadgeState.NoBalance
                } else {
                    BadgeState.Active(daysRemaining = null, urgency = Urgency.FINE)
                }
            } else {
                val days = ChronoUnit.DAYS.between(today, expiry)
                if (days < 0) {
                    BadgeState.Expired
                } else if (totalRemaining(voucher) == BigDecimal.ZERO) {
                    // Nothing left to spend — urgency to spend before expiry
                    // is moot; the real page reports the balance as zero.
                    BadgeState.NoBalance
                } else {
                    val urgency = when {
                        days < 7 -> Urgency.URGENT
                        days < 30 -> Urgency.SOON
                        else -> Urgency.FINE
                    }
                    BadgeState.Active(daysRemaining = days, urgency = urgency)
                }
            }
        }
    }

/**
 * Sum of unused-only remaining value (spec 04 §4.3 semantics): zero (or
 * empty — nothing unused) means the voucher has no balance left.
 */
private fun totalRemaining(voucher: VoucherGroup): BigDecimal =
    voucher.categoryBalances.fold(BigDecimal.ZERO) { acc, b -> acc + b.remainingValue }

/**
 * Spec 04 §4.2 accessibility: exactly one of four strings per badge — no
 * fall-through (UNVERIFIED must never announce blank/default). The badge
 * renders this same string, so what is on screen is what TalkBack announces.
 */
fun badgeLabel(state: BadgeState): String = when (state) {
    is BadgeState.Active ->
        state.daysRemaining?.let { "$it days left" } ?: "No expiry date"
    BadgeState.Expired -> "Expired"
    BadgeState.NoBalance -> "Fully used"
    BadgeState.NotStarted -> "Not started"
    BadgeState.Unverified -> "Couldn't verify, tap to check"
}

/**
 * Spec 04 §4.3: total remaining value across active entries, broken down by
 * category. UNVERIFIED entries contribute nothing to the value and are not
 * counted toward "N links" (they have no verified data yet).
 */
data class ListSummary(
    val total: BigDecimal,
    val categoryTotals: List<CategoryBalance>,
    val linkCount: Int,
)

fun summarizeActive(vouchers: List<VoucherGroup>): ListSummary {
    val known = vouchers.filter { it.validityStatus != ValidityStatus.UNVERIFIED }
    val byCategory = linkedMapOf<String, BigDecimal>()
    known.forEach { voucher ->
        voucher.categoryBalances.forEach { balance ->
            byCategory.merge(balance.category, balance.remainingValue, BigDecimal::add)
        }
    }
    val total = known.fold(BigDecimal.ZERO) { acc, voucher ->
        acc + voucher.categoryBalances.fold(BigDecimal.ZERO) { a, b -> a + b.remainingValue }
    }
    return ListSummary(
        total = total,
        categoryTotals = byCategory.map { (category, value) -> CategoryBalance(category, value) }
            .sortedBy { it.category },
        linkCount = known.size,
    )
}

fun formatSgd(value: BigDecimal): String =
    // Currency is implicitly Singapore dollars throughout the app. Strip
    // trailing zeros so whole-dollar amounts read "$50" (not "$50.00"), while
    // fractional cents like "$25.50" keep their decimals.
    "$" + value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

fun summaryHeadline(summary: ListSummary): String =
    "${formatSgd(summary.total)} remaining across ${summary.linkCount} " +
        if (summary.linkCount == 1) "link" else "links"
