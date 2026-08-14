package com.hdbcoders.cdcwallet.list

import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.data.model.CategoryBalance
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.ui.list.BadgeGlyph
import com.hdbcoders.cdcwallet.ui.list.BadgeTone
import com.hdbcoders.cdcwallet.ui.list.Urgency
import com.hdbcoders.cdcwallet.ui.list.badgePresentation
import com.hdbcoders.cdcwallet.ui.list.badgeState
import com.hdbcoders.cdcwallet.ui.list.formatSgd
import com.hdbcoders.cdcwallet.ui.list.sortActive
import com.hdbcoders.cdcwallet.ui.list.summarizeActive
import com.hdbcoders.cdcwallet.ui.list.summaryHeadline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class VoucherListLogicTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 1)

    private fun voucher(
        id: String,
        status: ValidityStatus,
        expiry: LocalDate? = null,
        balances: List<CategoryBalance> = emptyList(),
        name: String = "Name $id",
    ) = VoucherGroup(
        id = id,
        token = id,
        url = "https://example.com/$id",
        campaignName = name,
        validityStatus = status,
        expiryDate = expiry,
        categoryBalances = balances,
        dateAdded = Instant.EPOCH,
        lastRefreshedAt = null,
        lastRefreshError = null,
    )

    // ---- sorting (04 §4.1) ----

    @Test
    fun unverifiedEntriesArePinnedAboveAllOthers() {
        val unverified = voucher("u", ValidityStatus.UNVERIFIED)
        val farExpiry = voucher("far", ValidityStatus.ACTIVE, today.plusDays(90))
        val nearExpiry = voucher("near", ValidityStatus.ACTIVE, today.plusDays(3))

        val sorted = sortActive(listOf(farExpiry, nearExpiry, unverified))

        assertEquals(listOf("u", "near", "far"), sorted.map { it.id })
    }

    @Test
    fun sortsBySoonestExpiry() {
        val a = voucher("a", ValidityStatus.ACTIVE, today.plusDays(10))
        val b = voucher("b", ValidityStatus.ACTIVE, today.plusDays(2))
        val c = voucher("c", ValidityStatus.ACTIVE, today.plusDays(40))

        assertEquals(listOf("b", "a", "c"), sortActive(listOf(a, c, b)).map { it.id })
    }

    @Test
    fun entriesWithoutExpiryDateSortLastAmongRealEntries() {
        val noExpiry = voucher("x", ValidityStatus.ACTIVE, null)
        val expired = voucher("e", ValidityStatus.EXPIRED, today.minusDays(5))
        val soon = voucher("s", ValidityStatus.ACTIVE, today.plusDays(5))

        assertEquals(listOf("e", "s", "x"), sortActive(listOf(noExpiry, expired, soon)).map { it.id })
    }

    @Test
    fun vouchersWithBalanceSortBeforeFullyUsedOnesRegardlessOfExpiry() {
        val hasBalance = voucher(
            "hb", ValidityStatus.ACTIVE, today.plusDays(100),
            listOf(CategoryBalance("heartland", BigDecimal("5"))),
        )
        val fullyUsedSoon = voucher("fu", ValidityStatus.ACTIVE, today.plusDays(2))
        val fullyUsedFar = voucher("ff", ValidityStatus.ACTIVE, today.plusDays(50))

        // hasBalance (even though it expires far later) must come before both
        // fully-used vouchers; those two order by expiry.
        assertEquals(
            listOf("hb", "fu", "ff"),
            sortActive(listOf(fullyUsedFar, hasBalance, fullyUsedSoon)).map { it.id },
        )
    }

    @Test
    fun balanceGroupOrdersByExpiryWithinEachPriority() {
        val hasA = voucher(
            "ha", ValidityStatus.ACTIVE, today.plusDays(40),
            listOf(CategoryBalance("heartland", BigDecimal("5"))),
        )
        val hasB = voucher(
            "hb", ValidityStatus.ACTIVE, today.plusDays(3),
            listOf(CategoryBalance("supermarket", BigDecimal("10"))),
        )
        val usedA = voucher("ua", ValidityStatus.ACTIVE, today.plusDays(60))
        val usedB = voucher("ub", ValidityStatus.ACTIVE, today.plusDays(1))

        assertEquals(
            listOf("hb", "ha", "ub", "ua"),
            sortActive(listOf(hasA, usedA, hasB, usedB)).map { it.id },
        )
    }

    @Test
    fun zeroSumBalancesSortAsFullyUsedNotAsHasBalance() {
        // Banana-style fixture: a non-empty balance list whose values sum to
        // zero. It must sort into the "no balance" tier (below any voucher
        // with a positive sum), not be treated as having balance simply because
        // the list is non-empty.
        val zeroCat = voucher(
            "banana", ValidityStatus.ACTIVE,
            LocalDate.of(2027, 12, 31),
            listOf(CategoryBalance("Climate", BigDecimal("0"))),
        )
        val onePositive = voucher(
            "lychee", ValidityStatus.ACTIVE,
            LocalDate.of(2027, 12, 31),
            listOf(
                CategoryBalance("Heartland", BigDecimal("0")),
                CategoryBalance("Supermarket", BigDecimal("150")),
            ),
        )
        val otherPositive = voucher(
            "rambutan", ValidityStatus.ACTIVE,
            LocalDate.of(2027, 12, 31),
            listOf(
                CategoryBalance("Heartland", BigDecimal("100")),
                CategoryBalance("Supermarket", BigDecimal("0")),
            ),
        )
        val alsoZero = voucher("durian", ValidityStatus.ACTIVE, LocalDate.of(2026, 12, 31))

        // Both positive-sum vouchers ahead of the zero-sum one regardless of
        // expiry; among the zero-sum ones, expiry (then id) decides.
        assertEquals(
            listOf("lychee", "rambutan", "durian", "banana"),
            sortActive(listOf(zeroCat, alsoZero, onePositive, otherPositive)).map { it.id },
        )
    }

    // ---- badges (04 §4.2) ----

    @Test
    fun unverifiedGetsDistinctNeutralBadge() {
        val state = badgeState(voucher("u", ValidityStatus.UNVERIFIED), today)
        val p = badgePresentation(state)
        assertEquals(R.string.badge_unverified, p.labelResId)
        assertEquals(BadgeTone.NEUTRAL, p.tone)
        assertEquals(BadgeGlyph.NONE, p.glyph)
        assertEquals(R.string.unverified_footer, p.bannerResId)
        assertTrue(p.bannerNeutral)
    }

    @Test
    fun notStartedIsNeverUrgentRegardlessOfExpiryDate() {
        val state = badgeState(
            voucher("n", ValidityStatus.NOT_STARTED, today.plusDays(2)),
            today,
        )
        val p = badgePresentation(state)
        assertEquals(R.string.badge_not_started, p.labelResId)
        assertEquals(BadgeTone.NEUTRAL, p.tone)
        assertEquals(BadgeGlyph.NONE, p.glyph)
        assertEquals(null, p.bannerResId)
    }

    @Test
    fun expiredStatusGetsExpiredBadge() {
        val state = badgeState(voucher("e", ValidityStatus.EXPIRED, today.minusDays(1)), today)
        val p = badgePresentation(state)
        assertEquals(R.string.badge_expired, p.labelResId)
        assertEquals(BadgeTone.DANGER, p.tone)
        assertEquals(BadgeGlyph.WARNING, p.glyph)
        assertEquals(R.string.expired_footer, p.bannerResId)
        assertTrue(!p.bannerNeutral)
    }

    @Test
    fun activeWithPastDateReadsAsExpired() {
        val state = badgeState(voucher("a", ValidityStatus.ACTIVE, today.minusDays(1)), today)
        assertEquals(R.string.badge_expired, badgePresentation(state).labelResId)
    }

    @Test
    fun activeUrgencyBoundaries() {
        fun days(n: Long) =
            badgeState(
                voucher(
                    "a",
                    ValidityStatus.ACTIVE,
                    today.plusDays(n),
                    listOf(CategoryBalance("heartland", BigDecimal("5"))),
                ),
                today,
            )

        assertEquals(Urgency.URGENT, (days(0) as com.hdbcoders.cdcwallet.ui.list.BadgeState.Active).urgency)
        assertEquals(Urgency.URGENT, (days(6) as com.hdbcoders.cdcwallet.ui.list.BadgeState.Active).urgency)
        assertEquals(Urgency.SOON, (days(7) as com.hdbcoders.cdcwallet.ui.list.BadgeState.Active).urgency)
        assertEquals(Urgency.SOON, (days(29) as com.hdbcoders.cdcwallet.ui.list.BadgeState.Active).urgency)
        assertEquals(Urgency.FINE, (days(30) as com.hdbcoders.cdcwallet.ui.list.BadgeState.Active).urgency)
    }

    @Test
    fun activeBadgeAnnouncesDaysRemaining() {
        val state = badgeState(
            voucher(
                "a",
                ValidityStatus.ACTIVE,
                today.plusDays(12),
                listOf(CategoryBalance("heartland", BigDecimal("5"))),
            ),
            today,
        )
        val label = badgePresentation(state)
        assertEquals(R.plurals.badge_days_left, label.labelResId)
        assertEquals(12L, label.pluralCount)
        // 12 days to expiry is SOON territory.
        assertEquals(BadgeTone.WARNING, label.tone)
        assertEquals(BadgeGlyph.WARNING, label.glyph)
    }

    @Test
    fun activeWithoutExpiryDateIsFineAndLabeled() {
        val state = badgeState(
            voucher(
                "a",
                ValidityStatus.ACTIVE,
                null,
                listOf(CategoryBalance("heartland", BigDecimal("5"))),
            ),
            today,
        ) as com.hdbcoders.cdcwallet.ui.list.BadgeState.Active
        assertEquals(Urgency.FINE, state.urgency)
        val p = badgePresentation(state)
        assertEquals(R.string.badge_no_expiry, p.labelResId)
        assertEquals(BadgeTone.OK, p.tone)
        assertEquals(BadgeGlyph.CHECK, p.glyph)
    }

    @Test
    fun allFiveTalkBackStringsCovered() {
        val states = listOf(
            badgeState(voucher("u", ValidityStatus.UNVERIFIED), today),
            badgeState(voucher("n", ValidityStatus.NOT_STARTED, today.plusDays(1)), today),
            badgeState(voucher("e", ValidityStatus.EXPIRED), today),
            badgeState(
                voucher(
                    "a",
                    ValidityStatus.ACTIVE,
                    today.plusDays(5),
                    listOf(CategoryBalance("heartland", BigDecimal("5"))),
                ),
                today,
            ),
            badgeState(
                voucher(
                    "z",
                    ValidityStatus.ACTIVE,
                    today.plusDays(5),
                    listOf(CategoryBalance("heartland", BigDecimal.ZERO)),
                ),
                today,
            ),
        )
        assertEquals(
            listOf(
                R.string.badge_unverified,
                R.string.badge_not_started,
                R.string.badge_expired,
                R.plurals.badge_days_left,
                R.string.badge_fully_used,
            ),
            states.map { badgePresentation(it).labelResId },
        )
        // The days-left label resolves the plural count the UI renders with.
        assertEquals(5L, badgePresentation(states[3]).pluralCount)
    }

    @Test
    fun activeWithZeroBalanceGetsNoBalanceBadge() {
        // Explicit zero entries sum to zero.
        val zero = badgeState(
            voucher(
                "z",
                ValidityStatus.ACTIVE,
                today.plusDays(149),
                listOf(
                    CategoryBalance("heartland", BigDecimal.ZERO),
                    CategoryBalance("supermarket", BigDecimal.ZERO),
                ),
            ),
            today,
        )
        assertEquals(
            com.hdbcoders.cdcwallet.ui.list.BadgeState.NoBalance,
            zero,
        )
        val p = badgePresentation(zero)
        assertEquals(R.string.badge_fully_used, p.labelResId)
        assertEquals(BadgeTone.DANGER, p.tone)
        assertEquals(BadgeGlyph.WARNING, p.glyph)
        assertEquals(R.string.no_balance_banner, p.bannerResId)
        assertTrue(!p.bannerNeutral)

        // Empty categoryBalances (nothing unused) also means zero balance.
        val empty = badgeState(voucher("e2", ValidityStatus.ACTIVE, today.plusDays(149)), today)
        assertEquals(
            com.hdbcoders.cdcwallet.ui.list.BadgeState.NoBalance,
            empty,
        )
    }

    @Test
    fun scaledZeroValuesAlsoReadAsNoBalance() {
        // Refactor M9: BigDecimal equality is scale-sensitive - "0.00" is NOT
        // == BigDecimal.ZERO, but its value IS zero. All scaled-zero forms
        // must read as NoBalance, never as expiry urgency.
        val doubleZero = badgeState(
            voucher(
                "sz1",
                ValidityStatus.ACTIVE,
                today.plusDays(30),
                listOf(CategoryBalance("heartland", BigDecimal("0.00"))),
            ),
            today,
        )
        assertEquals(com.hdbcoders.cdcwallet.ui.list.BadgeState.NoBalance, doubleZero)

        val mixedScaled = badgeState(
            voucher(
                "sz2",
                ValidityStatus.ACTIVE,
                today.plusDays(30),
                listOf(
                    CategoryBalance("heartland", BigDecimal("5.00")),
                    CategoryBalance("supermarket", BigDecimal("-5.0")),
                ),
            ),
            today,
        )
        assertEquals(com.hdbcoders.cdcwallet.ui.list.BadgeState.NoBalance, mixedScaled)

        // A scaled zero in one category plus real value elsewhere is NOT
        // zero-balance: urgency must win.
        val withValue = badgeState(
            voucher(
                "sz3",
                ValidityStatus.ACTIVE,
                today.plusDays(30),
                listOf(
                    CategoryBalance("heartland", BigDecimal("0.00")),
                    CategoryBalance("supermarket", BigDecimal("1")),
                ),
            ),
            today,
        )
        assertTrue(withValue is com.hdbcoders.cdcwallet.ui.list.BadgeState.Active)
    }

    @Test
    fun activeWithPositiveBalanceKeepsExpiryUrgency() {
        val state = badgeState(
            voucher(
                "a",
                ValidityStatus.ACTIVE,
                today.plusDays(149),
                listOf(CategoryBalance("heartland", BigDecimal("5"))),
            ),
            today,
        )
        assertEquals(Urgency.FINE, (state as com.hdbcoders.cdcwallet.ui.list.BadgeState.Active).urgency)
        val label = badgePresentation(state)
        assertEquals(R.plurals.badge_days_left, label.labelResId)
        assertEquals(149L, label.pluralCount)
    }

    @Test
    fun activePastDateWithZeroBalanceStillReadsAsExpired() {
        val state = badgeState(
            voucher(
                "p",
                ValidityStatus.ACTIVE,
                today.minusDays(1),
                listOf(CategoryBalance("heartland", BigDecimal.ZERO)),
            ),
            today,
        )
        assertEquals(R.string.badge_expired, badgePresentation(state).labelResId)
    }

    @Test
    fun nonActiveStatesIgnoreZeroBalance() {
        assertEquals(
            R.string.badge_not_started,
            badgePresentation(badgeState(
                voucher("n", ValidityStatus.NOT_STARTED, today.plusDays(2)),
                today,
            )).labelResId,
        )
        assertEquals(
            R.string.badge_expired,
            badgePresentation(badgeState(
                voucher("e", ValidityStatus.EXPIRED, today.minusDays(1)),
                today,
            )).labelResId,
        )
        assertEquals(
            R.string.badge_unverified,
            badgePresentation(badgeState(voucher("u", ValidityStatus.UNVERIFIED), today)).labelResId,
        )
    }

    @Test
    fun activeWithoutExpiryAndZeroBalanceIsNoBalance() {
        val state = badgeState(voucher("nz", ValidityStatus.ACTIVE, null), today)
        assertEquals(
            com.hdbcoders.cdcwallet.ui.list.BadgeState.NoBalance,
            state,
        )
    }

    // ---- aggregate summary (04 §4.3) ----

    @Test
    fun summaryCountsOnlyActiveEntriesAndSumsCategories() {
        val vouchers = listOf(
            voucher(
                "a",
                ValidityStatus.ACTIVE,
                today.plusDays(10),
                listOf(
                    CategoryBalance("heartland", BigDecimal("50")),
                    CategoryBalance("supermarket", BigDecimal("25.5")),
                ),
            ),
            voucher(
                "b",
                ValidityStatus.ACTIVE,
                today.plusDays(40),
                listOf(CategoryBalance("groceries", BigDecimal("10"))),
            ),
            voucher(
                "c",
                ValidityStatus.NOT_STARTED,
                today.plusDays(2),
                listOf(CategoryBalance("heartland", BigDecimal("5"))),
            ),
            voucher(
                "e",
                ValidityStatus.EXPIRED,
                today.minusDays(1),
                listOf(CategoryBalance("heartland", BigDecimal("2"))),
            ),
            voucher("u", ValidityStatus.UNVERIFIED),
        )

        val summary = summarizeActive(vouchers)

        // Refactor H9: only ACTIVE balances count. NOT_STARTED/EXPIRED
        // balances are not spendable value, and UNVERIFIED rows carry none.
        assertEquals(BigDecimal("85.5"), summary.total)
        assertEquals(2, summary.linkCount)
        assertEquals(
            listOf(
                CategoryBalance("Groceries", BigDecimal("10")),
                CategoryBalance("Heartland", BigDecimal("50")),
                CategoryBalance("Supermarket", BigDecimal("25.5")),
            ),
            summary.categoryTotals,
        )
    }

    @Test
    fun unverifiedContributesNothingToSummary() {
        val summary = summarizeActive(listOf(voucher("u", ValidityStatus.UNVERIFIED)))
        assertEquals(BigDecimal.ZERO, summary.total)
        assertEquals(0, summary.linkCount)
        assertTrue(summary.categoryTotals.isEmpty())
    }

    @Test
    fun mixedCaseCategoriesAggregateIntoOneCanonicalRow() {
        // Refactor M8: raw stored values keep their exact spelling, but the
        // summary groups by a display-only canonical key - "heartland" and
        // "Heartland" must sum into ONE row emitted in canonical form.
        val summary = summarizeActive(
            listOf(
                voucher(
                    "a",
                    ValidityStatus.ACTIVE,
                    today.plusDays(10),
                    listOf(CategoryBalance("heartland", BigDecimal("50"))),
                ),
                voucher(
                    "b",
                    ValidityStatus.ACTIVE,
                    today.plusDays(20),
                    listOf(CategoryBalance("Heartland", BigDecimal("10"))),
                ),
            ),
        )
        assertEquals(BigDecimal("60"), summary.total)
        assertEquals(
            listOf(CategoryBalance("Heartland", BigDecimal("60"))),
            summary.categoryTotals,
        )
    }

    @Test
    fun zeroBalanceCategoriesAreFilteredOutOfSummary() {
        // A category that sums to $0 (e.g. an "unused" voucher group with
        // zero value) must not appear in the hero's category rows - the
        // total stays correct, the category list drops the $0 entry.
        val summary = summarizeActive(
            listOf(
                voucher(
                    "a",
                    ValidityStatus.ACTIVE,
                    today.plusDays(1),
                    listOf(
                        CategoryBalance("heartland", BigDecimal("50")),
                        CategoryBalance("groceries", BigDecimal("0.00")),
                    ),
                ),
                voucher(
                    "b",
                    ValidityStatus.ACTIVE,
                    today.plusDays(2),
                    listOf(CategoryBalance("groceries", BigDecimal("0.00"))),
                ),
            ),
        )
        assertEquals(BigDecimal("50.00"), summary.total)
        assertEquals(
            listOf(CategoryBalance("Heartland", BigDecimal("50"))),
            summary.categoryTotals,
        )
    }

    @Test
    fun emptyListSummarizesToZero() {
        val summary = summarizeActive(emptyList())
        assertEquals(BigDecimal.ZERO, summary.total)
        assertEquals(0, summary.linkCount)
    }

    @Test
    fun headlineUsesLinkCountGrammarAndFormatting() {
        assertEquals(
            "$90.5 remaining across 1 link",
            summaryHeadline(
                summarizeActive(
                    listOf(
                        voucher(
                            "a",
                            ValidityStatus.ACTIVE,
                            today.plusDays(1),
                            listOf(CategoryBalance("heartland", BigDecimal("90.5"))),
                        ),
                        voucher("u", ValidityStatus.UNVERIFIED),
                    ),
                ),
            ),
        )
        val single = summarizeActive(
            listOf(voucher("a", ValidityStatus.ACTIVE, null, listOf(CategoryBalance("x", BigDecimal("1"))))),
        )
        assertEquals("$1 remaining across 1 link", summaryHeadline(single))
    }

    @Test
    fun formatSgdStripsTrailingZeros() {
        assertEquals("$50", formatSgd(BigDecimal("50")))
        assertEquals("$25.5", formatSgd(BigDecimal("25.5")))
        assertEquals("$0", formatSgd(BigDecimal.ZERO))
        assertEquals("$100", formatSgd(BigDecimal("100")))
        assertEquals("$0.5", formatSgd(BigDecimal("0.50")))
    }

    @Test
    fun formatSgdGroupsThousandsOnIntegerPartOnly() {
        assertEquals("$1,030", formatSgd(BigDecimal("1030")))
        assertEquals("$1,030.5", formatSgd(BigDecimal("1030.5")))
        assertEquals("$12,345.67", formatSgd(BigDecimal("12345.67")))
        assertEquals("$1,000,000", formatSgd(BigDecimal("1000000")))
    }
}
