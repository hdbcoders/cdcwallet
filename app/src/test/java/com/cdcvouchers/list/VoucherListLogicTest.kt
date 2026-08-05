package com.cdcvouchers.list

import com.cdcvouchers.data.model.CategoryBalance
import com.cdcvouchers.data.model.ValidityStatus
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.ui.list.Urgency
import com.cdcvouchers.ui.list.badgeLabel
import com.cdcvouchers.ui.list.badgeState
import com.cdcvouchers.ui.list.formatSgd
import com.cdcvouchers.ui.list.sortActive
import com.cdcvouchers.ui.list.summarizeActive
import com.cdcvouchers.ui.list.summaryHeadline
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
        assertEquals("Couldn't verify, tap to check", badgeLabel(state))
    }

    @Test
    fun notStartedIsNeverUrgentRegardlessOfExpiryDate() {
        val state = badgeState(
            voucher("n", ValidityStatus.NOT_STARTED, today.plusDays(2)),
            today,
        )
        assertEquals("Not started", badgeLabel(state))
    }

    @Test
    fun expiredStatusGetsExpiredBadge() {
        val state = badgeState(voucher("e", ValidityStatus.EXPIRED, today.minusDays(1)), today)
        assertEquals("Expired", badgeLabel(state))
    }

    @Test
    fun activeWithPastDateReadsAsExpired() {
        val state = badgeState(voucher("a", ValidityStatus.ACTIVE, today.minusDays(1)), today)
        assertEquals("Expired", badgeLabel(state))
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

        assertEquals(Urgency.URGENT, (days(0) as com.cdcvouchers.ui.list.BadgeState.Active).urgency)
        assertEquals(Urgency.URGENT, (days(6) as com.cdcvouchers.ui.list.BadgeState.Active).urgency)
        assertEquals(Urgency.SOON, (days(7) as com.cdcvouchers.ui.list.BadgeState.Active).urgency)
        assertEquals(Urgency.SOON, (days(29) as com.cdcvouchers.ui.list.BadgeState.Active).urgency)
        assertEquals(Urgency.FINE, (days(30) as com.cdcvouchers.ui.list.BadgeState.Active).urgency)
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
        assertEquals("Expires in 12 days", badgeLabel(state))
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
        ) as com.cdcvouchers.ui.list.BadgeState.Active
        assertEquals(Urgency.FINE, state.urgency)
        assertEquals("No expiry date", badgeLabel(state))
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
                "Couldn't verify, tap to check",
                "Not started",
                "Expired",
                "Expires in 5 days",
                "Fully used",
            ),
            states.map(::badgeLabel),
        )
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
            com.cdcvouchers.ui.list.BadgeState.NoBalance,
            zero,
        )
        assertEquals("Fully used", badgeLabel(zero))

        // Empty categoryBalances (nothing unused) also means zero balance.
        val empty = badgeState(voucher("e2", ValidityStatus.ACTIVE, today.plusDays(149)), today)
        assertEquals(
            com.cdcvouchers.ui.list.BadgeState.NoBalance,
            empty,
        )
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
        assertEquals(Urgency.FINE, (state as com.cdcvouchers.ui.list.BadgeState.Active).urgency)
        assertEquals("Expires in 149 days", badgeLabel(state))
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
        assertEquals("Expired", badgeLabel(state))
    }

    @Test
    fun nonActiveStatesIgnoreZeroBalance() {
        assertEquals(
            "Not started",
            badgeLabel(badgeState(
                voucher("n", ValidityStatus.NOT_STARTED, today.plusDays(2)),
                today,
            )),
        )
        assertEquals(
            "Expired",
            badgeLabel(badgeState(
                voucher("e", ValidityStatus.EXPIRED, today.minusDays(1)),
                today,
            )),
        )
        assertEquals(
            "Couldn't verify, tap to check",
            badgeLabel(badgeState(voucher("u", ValidityStatus.UNVERIFIED), today)),
        )
    }

    @Test
    fun activeWithoutExpiryAndZeroBalanceIsNoBalance() {
        val state = badgeState(voucher("nz", ValidityStatus.ACTIVE, null), today)
        assertEquals(
            com.cdcvouchers.ui.list.BadgeState.NoBalance,
            state,
        )
    }

    // ---- aggregate summary (04 §4.3) ----

    @Test
    fun summarySumsAcrossEntriesAndCategories() {
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
            voucher("u", ValidityStatus.UNVERIFIED),
        )

        val summary = summarizeActive(vouchers)

        assertEquals(BigDecimal("90.5"), summary.total)
        assertEquals(3, summary.linkCount)
        assertEquals(
            listOf(
                CategoryBalance("groceries", BigDecimal("10")),
                CategoryBalance("heartland", BigDecimal("55")),
                CategoryBalance("supermarket", BigDecimal("25.5")),
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
    fun emptyListSummarizesToZero() {
        val summary = summarizeActive(emptyList())
        assertEquals(BigDecimal.ZERO, summary.total)
        assertEquals(0, summary.linkCount)
    }

    @Test
    fun headlineUsesLinkCountGrammarAndFormatting() {
        assertEquals(
            "$90.50 remaining across 1 link",
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
        assertEquals("$1.00 remaining across 1 link", summaryHeadline(single))
    }

    @Test
    fun formatSgdPinsTwoDecimals() {
        assertEquals("$50.00", formatSgd(BigDecimal("50")))
        assertEquals("$25.50", formatSgd(BigDecimal("25.5")))
        assertEquals("$0.00", formatSgd(BigDecimal.ZERO))
    }
}
