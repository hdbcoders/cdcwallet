package com.cdcwallet.addflow

import android.content.Context
import com.cdcwallet.data.VoucherRepository
import com.cdcwallet.data.model.CategoryBalance
import com.cdcwallet.data.model.ValidityStatus
import com.cdcwallet.data.model.VoucherGroup
import com.cdcwallet.extraction.ExtractionResult
import com.cdcwallet.extraction.VoucherExtractor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

private class FakeRepository : VoucherRepository {
    val rows = mutableListOf<VoucherGroup>()
    val findCalls = AtomicInteger(0)
    val insertCalls = AtomicInteger(0)

    /** Simulates the unique-token backstop rejecting a row (refactor M3). */
    var failInsert = false

    override fun observeActive(): Flow<List<VoucherGroup>> = flowOf(rows.filterNot { it.isArchived })
    override fun observeArchived(): Flow<List<VoucherGroup>> = flowOf(rows.filter { it.isArchived })
    override fun observeActiveCount(): Flow<Int> = flowOf(rows.count { !it.isArchived })
    override fun observeArchivedCount(): Flow<Int> = flowOf(rows.count { it.isArchived })
    override fun observeById(id: String): Flow<VoucherGroup?> = flowOf(rows.firstOrNull { it.id == id })

    override suspend fun insert(voucher: VoucherGroup): Boolean {
        insertCalls.incrementAndGet()
        if (failInsert) return false
        if (rows.any { it.token == voucher.token }) return false
        rows.add(voucher)
        return true
    }

    override suspend fun updateFromRefresh(id: String, data: com.cdcwallet.data.model.VoucherRefreshData) {}
    override suspend fun recordRefreshFailure(id: String, error: String) {}
    override suspend fun archive(id: String) {}
    override suspend fun restore(id: String) {}
    override suspend fun delete(id: String) {}
    override suspend fun findByToken(token: String): VoucherGroup? {
        findCalls.incrementAndGet()
        return rows.firstOrNull { it.token == token }
    }

    override suspend fun findAll(): List<VoucherGroup> = rows.toList()

    override suspend fun replaceAll(vouchers: List<VoucherGroup>) {
        rows.clear()
        rows.addAll(vouchers)
    }

    override suspend fun bulkInsert(vouchers: List<VoucherGroup>): Int {
        rows.addAll(vouchers)
        return vouchers.size
    }
}

private class FakeExtractor(private val result: ExtractionResult) : VoucherExtractor {
    val calls = AtomicInteger(0)

    override suspend fun extractForAdd(context: Context, url: String): ExtractionResult {
        calls.incrementAndGet()
        return result
    }
}

private class SuspendingExtractor : VoucherExtractor {
    private val gate = CompletableDeferred<Unit>()

    override suspend fun extractForAdd(context: Context, url: String): ExtractionResult {
        gate.await()
        return ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT)
    }
}

/** Extraction completes only when the test releases it (refactor M4). */
private class GateExtractor : VoucherExtractor {
    private val gate = CompletableDeferred<ExtractionResult>()

    override suspend fun extractForAdd(context: Context, url: String): ExtractionResult =
        gate.await()

    fun release(result: ExtractionResult) {
        gate.complete(result)
    }
}

class AddVoucherFlowTest {

    private val context = android.app.Application()

    private fun voucher(token: String) = VoucherGroup(
        id = "id-$token",
        token = token,
        url = "https://voucher.redeem.gov.sg/$token",
        campaignName = token,
        validityStatus = ValidityStatus.UNVERIFIED,
        expiryDate = null,
        categoryBalances = emptyList(),
        dateAdded = Instant.now(),
        lastRefreshedAt = null,
        lastRefreshError = null,
        isArchived = false,
    )

    private val success = ExtractionResult.Success(
        campaignName = "CDC Vouchers 2026",
        validityStatus = ValidityStatus.ACTIVE,
        expiryDate = LocalDate.of(2026, 12, 31),
        categoryBalances = listOf(CategoryBalance("heartland", BigDecimal("50"))),
    )

    @Test
    fun happyPathAddsWithRealData() = runTest {
        val repo = FakeRepository()
        val extractor = FakeExtractor(success)
        val flow = AddVoucherFlow(repo, extractor)

        val result = flow.add(context, "https://voucher.redeem.gov.sg/TokenABC?utm_source=sms")

        assertTrue(result is AddVoucherResult.Added)
        val voucher = (result as AddVoucherResult.Added).voucher
        assertEquals("CDC Vouchers 2026", voucher.campaignName)
        assertEquals(ValidityStatus.ACTIVE, voucher.validityStatus)
        assertEquals(LocalDate.of(2026, 12, 31), voucher.expiryDate)
        assertEquals(listOf(CategoryBalance("heartland", BigDecimal("50"))), voucher.categoryBalances)
        assertTrue(voucher.lastRefreshedAt != null)
        // token stored without the query params
        assertEquals("TokenABC", voucher.token)
        assertEquals(1, repo.rows.size)
        assertEquals(1, extractor.calls.get())
    }

    @Test
    fun invalidFormatIsRejectedWithoutAnyExtractionOrInsert() = runTest {
        val repo = FakeRepository()
        val extractor = FakeExtractor(success)
        val flow = AddVoucherFlow(repo, extractor)

        val result = flow.add(context, "https://evil.example.com/TokenABC")

        assertEquals(AddVoucherResult.InvalidFormat, result)
        assertEquals(0, extractor.calls.get())
        assertEquals(0, repo.insertCalls.get())
        assertEquals(0, repo.rows.size)
    }

    @Test
    fun duplicateIsRejectedBeforeAnyExtraction() = runTest {
        val repo = FakeRepository()
        repo.rows.add(voucher("TokenABC"))
        val extractor = FakeExtractor(success)
        val flow = AddVoucherFlow(repo, extractor)

        val result = flow.add(context, "https://voucher.redeem.gov.sg/TokenABC")

        assertTrue(result is AddVoucherResult.Duplicate)
        assertEquals("TokenABC", (result as AddVoucherResult.Duplicate).existing.token)
        // the test that proves duplicate check runs before the fetch:
        assertEquals(0, extractor.calls.get())
        assertEquals(1, repo.rows.size)
    }

    @Test
    fun tokenDifferingOnlyInCaseIsANewEntry() = runTest {
        val repo = FakeRepository()
        repo.rows.add(voucher("TokenABC"))
        val extractor = FakeExtractor(success)
        val flow = AddVoucherFlow(repo, extractor)

        val result = flow.add(context, "https://voucher.redeem.gov.sg/tokenabc")

        assertTrue(result is AddVoucherResult.Added)
        assertEquals(2, repo.rows.size)
        assertEquals(1, extractor.calls.get())
    }

    @Test
    fun fetchFailureStillSavesRowAsUnverified() = runTest {
        val repo = FakeRepository()
        val extractor = FakeExtractor(ExtractionResult.Failure(ExtractionResult.FailureReason.NETWORK_ERROR))
        val flow = AddVoucherFlow(repo, extractor)

        val result = flow.add(context, "https://voucher.redeem.gov.sg/TokenXYZ")

        assertTrue(result is AddVoucherResult.AddedUnverified)
        val voucher = (result as AddVoucherResult.AddedUnverified).voucher
        assertEquals(ValidityStatus.UNVERIFIED, voucher.validityStatus)
        assertNull(voucher.expiryDate)
        assertEquals("TokenXYZ", voucher.campaignName)
        assertEquals("NETWORK_ERROR", voucher.lastRefreshError)
        assertEquals(1, repo.rows.size)
    }

    @Test
    fun cancelledMidFetchInsertsNothing() = runTest {
        val repo = FakeRepository()
        val flow = AddVoucherFlow(repo, SuspendingExtractor())
        val scope = CoroutineScope(Dispatchers.Default + Job())
        var outcome: Any? = "not-run"
        val job = scope.launch {
            outcome = flow.add(context, "https://voucher.redeem.gov.sg/TokenZZZ")
        }
        withContext(Dispatchers.Default) { Thread.sleep(100) }
        job.cancel()
        job.join()
        assertTrue("flow must not return a result after cancellation", outcome == "not-run")
        assertEquals(0, repo.rows.size)
        assertEquals(0, repo.insertCalls.get())
        scope.cancel()
    }

    @Test
    fun rejectedInsertReportsDuplicateInsteadOfSilentSuccess() = runTest {
        // Refactor M3: the unique-token backstop can reject the row after the
        // duplicate pre-check (concurrent add won the race). The add must
        // report a Duplicate - never a success with no saved row.
        val repo = FakeRepository().apply { failInsert = true }
        val flow = AddVoucherFlow(repo, FakeExtractor(success))

        val result = flow.add(context, "https://voucher.redeem.gov.sg/TokenABC")

        assertTrue("rejected insert must surface as Duplicate, got $result", result is AddVoucherResult.Duplicate)
        assertEquals(1, repo.insertCalls.get())
        assertEquals(0, repo.rows.size)
    }

    @Test
    fun cancelledBetweenExtractionAndInsertSavesNothing() = runTest {
        // Refactor M4: cancellation that lands while the extraction is still
        // resolving must be caught by the pre-insert ensureActive check - the
        // row must never be saved from an abandoned add.
        val repo = FakeRepository()
        val extractor = GateExtractor()
        val flow = AddVoucherFlow(repo, extractor)
        val scope = CoroutineScope(Dispatchers.Default + Job())
        var outcome: Any? = "not-run"
        val job = scope.launch {
            outcome = flow.add(context, "https://voucher.redeem.gov.sg/TokenZZZ")
        }
        withContext(Dispatchers.Default) { Thread.sleep(100) }
        job.cancel()
        // The extraction completes only AFTER the cancellation - exactly the
        // window the ensureActive check closes.
        extractor.release(success)
        job.join()
        assertTrue("flow must not return a result after cancellation", outcome == "not-run")
        assertEquals(0, repo.insertCalls.get())
        assertEquals(0, repo.rows.size)
        scope.cancel()
    }
}
