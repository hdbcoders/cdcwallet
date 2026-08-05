package com.cdcvouchers.ui.list

import com.cdcvouchers.data.FakeVoucherRepository
import com.cdcvouchers.data.model.ValidityStatus
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.extraction.ExtractionCoordinator
import com.cdcvouchers.extraction.ExtractionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ArchivedVoucherViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val today: LocalDate = LocalDate.of(2026, 8, 1)

    private fun voucher(id: String, status: ValidityStatus, expiry: LocalDate? = null) = VoucherGroup(
        id = id,
        token = id,
        url = "https://example.com/$id",
        campaignName = "Name $id",
        validityStatus = status,
        expiryDate = expiry,
        categoryBalances = emptyList(),
        dateAdded = Instant.EPOCH,
        lastRefreshedAt = null,
        lastRefreshError = null,
        isArchived = true,
    )

    private fun coordinator(repo: FakeVoucherRepository) =
        ExtractionCoordinator(repo, ExtractionEngine())

    @Test
    fun vouchersAreSortedBySortActive() = runTest(dispatcher) {
        val repo = FakeVoucherRepository()
        repo.bulkInsert(
            listOf(
                voucher("far", ValidityStatus.ACTIVE, today.plusDays(90)),
                voucher("near", ValidityStatus.ACTIVE, today.plusDays(3)),
                voucher("u", ValidityStatus.UNVERIFIED, null),
            ),
        )
        val vm = ArchivedVoucherViewModel(repo, coordinator(repo))
        backgroundScope.launch { vm.vouchers.collect {} }
        runCurrent()

        assertEquals(listOf("u", "near", "far"), vm.vouchers.value.map { it.id })
    }

    @Test
    fun restoreAndDeleteCallThrough() = runTest(dispatcher) {
        val repo = FakeVoucherRepository()
        repo.bulkInsert(listOf(voucher("a", ValidityStatus.ACTIVE), voucher("b", ValidityStatus.ACTIVE)))
        val vm = ArchivedVoucherViewModel(repo, coordinator(repo))

        vm.restore("a")
        runCurrent()
        assertTrue(repo.snapshot().single { it.id == "a" }.isArchived.not())

        vm.delete("b")
        runCurrent()
        assertEquals(listOf("a"), repo.snapshot().map { it.id })
    }
}
