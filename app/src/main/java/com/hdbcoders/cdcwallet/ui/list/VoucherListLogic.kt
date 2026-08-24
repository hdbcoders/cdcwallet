package com.hdbcoders.cdcwallet.ui.list

import com.hdbcoders.cdcwallet.data.model.CategoryBalance
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.ui.theme.canonicalizeCategoryForDisplay
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
     * ACTIVE entry whose remaining value is zero - nothing left to spend.
     * Rendered red with the same warning icon as Expired.
     */
    data object NoBalance : BadgeState

    /** ACTIVE entries only; daysRemaining is null when the API omitted an end date. */
    data class Active(val daysRemaining: Long?, val urgency: Urgency) : BadgeState
}

enum class Urgency { URGENT, SOON, FINE }

/**
 * Spec 04 §4.1: sort priority -
 *   1. UNVERIFIED pinned above all others (no expiry/balance to sort by),
 *   2. vouchers WITH balance remaining come before those with none,
 *   3. within each, soonest expiry first.
 */
fun sortActive(vouchers: List<VoucherGroup>): List<VoucherGroup> =
    vouchers.sortedWith(
        compareByDescending<VoucherGroup> { it.isPinned }
            .thenByDescending { it.validityStatus == ValidityStatus.UNVERIFIED }
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
                if (totalRemaining(voucher).compareTo(BigDecimal.ZERO) == 0) {
                    BadgeState.NoBalance
                } else {
                    BadgeState.Active(daysRemaining = null, urgency = Urgency.FINE)
                }
            } else {
                val days = ChronoUnit.DAYS.between(today, expiry)
                if (days < 0) {
                    BadgeState.Expired
                } else if (totalRemaining(voucher).compareTo(BigDecimal.ZERO) == 0) {
                    // Nothing left to spend - urgency to spend before expiry
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
 * empty - nothing unused) means the voucher has no balance left.
 */
private fun totalRemaining(voucher: VoucherGroup): BigDecimal =
    voucher.categoryBalances.fold(BigDecimal.ZERO) { acc, b -> acc + b.remainingValue }

/** Semantic tone of a badge state; the UI maps it to theme colors. */
enum class BadgeTone { DANGER, WARNING, OK, NEUTRAL }

/** Leading glyph rendered for a badge state's expiry row. */
enum class BadgeGlyph { CHECK, WARNING, NONE }

/**
 * Spec 04 §4.2: exactly one label per badge state - no fall-through
 * (UNVERIFIED must never announce blank/default). The label is a
 * string-resource reference (localized); the days-left variant carries its
 * count so the caller can resolve the plural form.
 *
 * This is the SINGLE presentation source for badge states (refactor L3):
 * components render from this model only - tone, glyph, and body banner are
 * all derived here, never re-derived from [BadgeState] in the UI.
 */
data class BadgePresentation(
    val labelResId: Int,
    val pluralCount: Long? = null,
    val tone: BadgeTone,
    val glyph: BadgeGlyph,
    /** Body banner text; null means the card body shows category pills. */
    val bannerResId: Int? = null,
    /** Banner renders on the neutral raised surface instead of the danger tint. */
    val bannerNeutral: Boolean = false,
)

fun badgePresentation(state: BadgeState): BadgePresentation = when (state) {
    is BadgeState.Active -> {
        val (resId, count) = if (state.daysRemaining != null) {
            R.plurals.badge_days_left to state.daysRemaining
        } else {
            R.string.badge_no_expiry to null
        }
        when (state.urgency) {
            Urgency.URGENT -> BadgePresentation(resId, count, BadgeTone.DANGER, BadgeGlyph.WARNING)
            Urgency.SOON -> BadgePresentation(resId, count, BadgeTone.WARNING, BadgeGlyph.WARNING)
            Urgency.FINE -> BadgePresentation(resId, count, BadgeTone.OK, BadgeGlyph.CHECK)
        }
    }
    BadgeState.Expired -> BadgePresentation(
        R.string.badge_expired,
        tone = BadgeTone.DANGER,
        glyph = BadgeGlyph.WARNING,
        bannerResId = R.string.expired_footer,
    )
    BadgeState.NoBalance -> BadgePresentation(
        R.string.badge_fully_used,
        tone = BadgeTone.DANGER,
        glyph = BadgeGlyph.WARNING,
        bannerResId = R.string.no_balance_banner,
    )
    BadgeState.NotStarted -> BadgePresentation(
        R.string.badge_not_started,
        tone = BadgeTone.NEUTRAL,
        glyph = BadgeGlyph.NONE,
    )
    BadgeState.Unverified -> BadgePresentation(
        R.string.badge_unverified,
        tone = BadgeTone.NEUTRAL,
        glyph = BadgeGlyph.NONE,
        bannerResId = R.string.unverified_footer,
        bannerNeutral = true,
    )
}

/**
 * Spec 04 §4.3: total remaining value across entries, broken down by
 * category. Only `ValidityStatus.ACTIVE` entries contribute value and count
 * toward "N links" (refactor H9 - confirmed product rule): `UNVERIFIED` rows
 * carry no extracted data yet, and `NOT_STARTED`/`EXPIRED` balances are not
 * spendable value, so they must not inflate the hero.
 */
data class ListSummary(
    val total: BigDecimal,
    val categoryTotals: List<CategoryBalance>,
    val linkCount: Int,
)

fun summarizeActive(vouchers: List<VoucherGroup>): ListSummary {
    val active = vouchers.filter { it.validityStatus == ValidityStatus.ACTIVE }
    val byCategory = linkedMapOf<String, BigDecimal>()
    active.forEach { voucher ->
        voucher.categoryBalances.forEach { balance ->
            // Display-only canonical key (refactor M8): aggregation groups
            // case-insensitively so "heartland"/"Heartland" sum into ONE row;
            // the stored raw value is never mutated. The emitted category is
            // the display-canonical form, so every renderer shows capitalized
            // first letters regardless of the raw spelling.
            byCategory.merge(
                canonicalizeCategoryForDisplay(balance.category).lowercase(),
                balance.remainingValue,
                BigDecimal::add,
            )
        }
    }
    val total = active.fold(BigDecimal.ZERO) { acc, voucher ->
        acc + voucher.categoryBalances.fold(BigDecimal.ZERO) { a, b -> a + b.remainingValue }
    }
    return ListSummary(
        total = total,
        categoryTotals = byCategory.map { (category, value) ->
            CategoryBalance(canonicalizeCategoryForDisplay(category), value)
        }
            .filter { it.remainingValue.signum() != 0 }
            .sortedBy { it.category },
        linkCount = active.size,
    )
}

fun formatSgd(value: BigDecimal): String {
    // Currency is implicitly Singapore dollars throughout the app. Strip
    // trailing zeros so whole-dollar amounts read "$50" (not "$50.00"), while
    // fractional cents like "$25.50" keep their decimals. Group thousands on
    // the integer part only ("$1,030", "$1,030.5") to match the mockup's
    // hero amount.
    val plain = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    val parts = plain.split(".", limit = 2)
    val groupedInt = parts[0].replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")
    return "$" + if (parts.size == 2) "$groupedInt.${parts[1]}" else groupedInt
}

fun summaryHeadline(summary: ListSummary): String =
    "${formatSgd(summary.total)} remaining across ${summary.linkCount} " +
        if (summary.linkCount == 1) "link" else "links"
