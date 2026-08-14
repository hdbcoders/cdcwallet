package com.hdbcoders.cdcwallet.ui.list

import com.hdbcoders.cdcwallet.data.FakeVoucherRepository
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import com.hdbcoders.cdcwallet.data.model.VoucherGroup
import com.hdbcoders.cdcwallet.extraction.ExtractionCoordinator
import com.hdbcoders.cdcwallet.extraction.ExtractionEngine
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class VoucherListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun voucher(id: String, archived: Boolean = false) = VoucherGroup(
        id = id,
        token = id,
        url = "https://example.com/$id",
        campaignName = "Name $id",
        validityStatus = ValidityStatus.UNVERIFIED,
        expiryDate = null,
        categoryBalances = emptyList(),
        dateAdded = Instant.EPOCH,
        lastRefreshedAt = null,
        lastRefreshError = null,
        isArchived = archived,
    )

    private fun coordinator(repo: FakeVoucherRepository) =
        ExtractionCoordinator(repo, ExtractionEngine())

    @Test
    fun archivePersistsRow() = runTest(dispatcher) {
        val repo = FakeVoucherRepository()
        repo.bulkInsert(listOf(voucher("v1")))
        val vm = VoucherListViewModel(repo, coordinator(repo))

        vm.archive(voucher("v1"))
        runCurrent()

        assertTrue(repo.snapshot().single { it.id == "v1" }.isArchived)
    }

    @Test
    fun deleteAndRestoreCallThrough() = runTest(dispatcher) {
        val repo = FakeVoucherRepository()
        repo.bulkInsert(listOf(voucher("a"), voucher("b")))
        val vm = VoucherListViewModel(repo, coordinator(repo))

        vm.archive(voucher("a"))
        runCurrent()
        assertTrue(repo.snapshot().single { it.id == "a" }.isArchived)

        vm.restore("a")
        runCurrent()
        assertTrue(repo.snapshot().single { it.id == "a" }.isArchived.not())

        vm.delete("b")
        runCurrent()
        assertEquals(listOf("a"), repo.snapshot().map { it.id })
    }

    @Test
    fun requestDeleteAndDismissTogglePendingDelete() = runTest(dispatcher) {
        val vm = VoucherListViewModel(FakeVoucherRepository(), coordinator(FakeVoucherRepository()))
        val v = voucher("v1")

        vm.setMenu("v1")
        assertEquals("v1", vm.menuForId)
        vm.setMenu(null)
        assertNull(vm.menuForId)

        vm.requestDelete(v)
        assertEquals(v, vm.pendingDelete)
        vm.dismissDelete()
        assertNull(vm.pendingDelete)
    }

    @Test
    fun vouchersAndArchivedCountReflectRepository() = runTest(dispatcher) {
        val repo = FakeVoucherRepository()
        val vm = VoucherListViewModel(repo, coordinator(repo))
        backgroundScope.launch { vm.vouchers.collect {} }
        backgroundScope.launch { vm.archivedCount.collect {} }
        runCurrent()

        repo.bulkInsert(listOf(voucher("active"), voucher("gone", archived = true)))
        runCurrent()

        assertEquals(listOf("active"), vm.vouchers.value.map { it.id })
        assertEquals(1, vm.archivedCount.value)

        repo.archive("active")
        runCurrent()

        assertTrue(vm.vouchers.value.isEmpty())
        assertEquals(2, vm.archivedCount.value)
    }
}
