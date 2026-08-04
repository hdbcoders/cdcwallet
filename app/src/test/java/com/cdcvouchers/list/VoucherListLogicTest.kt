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
            badgeState(voucher("a", ValidityStatus.ACTIVE, today.plusDays(n)), today)

        assertEquals(Urgency.URGENT, (days(0) as com.cdcvouchers.ui.list.BadgeState.Active).urgency)
        assertEquals(Urgency.URGENT, (days(6) as com.cdcvouchers.ui.list.BadgeState.Active).urgency)
        assertEquals(Urgency.SOON, (days(7) as com.cdcvouchers.ui.list.BadgeState.Active).urgency)
        assertEquals(Urgency.SOON, (days(29) as com.cdcvouchers.ui.list.BadgeState.Active).urgency)
        assertEquals(Urgency.FINE, (days(30) as com.cdcvouchers.ui.list.BadgeState.Active).urgency)
    }

    @Test
    fun activeBadgeAnnouncesDaysRemaining() {
        val state = badgeState(voucher("a", ValidityStatus.ACTIVE, today.plusDays(12)), today)
        assertEquals("Expires in 12 days", badgeLabel(state))
    }

    @Test
    fun activeWithoutExpiryDateIsFineAndLabeled() {
        val state = badgeState(voucher("a", ValidityStatus.ACTIVE, null), today)
            as com.cdcvouchers.ui.list.BadgeState.Active
        assertEquals(Urgency.FINE, state.urgency)
        assertEquals("No expiry date", badgeLabel(state))
    }

    @Test
    fun allFourTalkBackStringsCovered() {
        val states = listOf(
            badgeState(voucher("u", ValidityStatus.UNVERIFIED), today),
            badgeState(voucher("n", ValidityStatus.NOT_STARTED, today.plusDays(1)), today),
            badgeState(voucher("e", ValidityStatus.EXPIRED), today),
            badgeState(voucher("a", ValidityStatus.ACTIVE, today.plusDays(5)), today),
        )
        assertEquals(
            listOf(
                "Couldn't verify, tap to check",
                "Not started",
                "Expired",
                "Expires in 5 days",
            ),
            states.map(::badgeLabel),
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
