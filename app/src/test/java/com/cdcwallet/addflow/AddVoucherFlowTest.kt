package com.cdcwallet.addflow

import android.content.Context
import com.cdcwallet.data.FakeVoucherRepository
import com.cdcwallet.data.model.CategoryBalance
import com.cdcwallet.data.model.ValidityStatus
import com.cdcwallet.data.model.VoucherGroup
import com.cdcwallet.extraction.ExtractionResult
import com.cdcwallet.extraction.VoucherExtractor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

private class FakeExtractor(private val result: ExtractionResult) : VoucherExtractor {
    val calls = AtomicInteger(0)

    override suspend fun extractForAdd(context: Context, url: String): ExtractionResult {
        calls.incrementAndGet()
        return result
    }
}

private class SuspendingExtractor : VoucherExtractor {
    val started = CompletableDeferred<Unit>()
    private val gate = CompletableDeferred<Unit>()

    override suspend fun extractForAdd(context: Context, url: String): ExtractionResult {
        started.complete(Unit)
        gate.await()
        return ExtractionResult.Failure(ExtractionResult.FailureReason.TIMEOUT)
    }
}

/** Extraction completes only when the test releases it (refactor M4). */
private class GateExtractor : VoucherExtractor {
    val started = CompletableDeferred<Unit>()
    private val gate = CompletableDeferred<ExtractionResult>()

    override suspend fun extractForAdd(context: Context, url: String): ExtractionResult {
        started.complete(Unit)
        return gate.await()
    }

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
        val repo = FakeVoucherRepository()
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
        assertEquals(1, repo.snapshot().size)
        assertEquals(1, extractor.calls.get())
    }

    @Test
    fun invalidFormatIsRejectedWithoutAnyExtractionOrInsert() = runTest {
        val repo = FakeVoucherRepository()
        val extractor = FakeExtractor(success)
        val flow = AddVoucherFlow(repo, extractor)

        val result = flow.add(context, "https://evil.example.com/TokenABC")

        assertEquals(AddVoucherResult.InvalidFormat, result)
        assertEquals(0, extractor.calls.get())
        assertEquals(0, repo.insertCalls.get())
        assertEquals(0, repo.snapshot().size)
    }

    @Test
    fun duplicateIsRejectedBeforeAnyExtraction() = runTest {
        val repo = FakeVoucherRepository()
        repo.insert(voucher("TokenABC"))
        val extractor = FakeExtractor(success)
        val flow = AddVoucherFlow(repo, extractor)

        val result = flow.add(context, "https://voucher.redeem.gov.sg/TokenABC")

        assertTrue(result is AddVoucherResult.Duplicate)
        assertEquals("TokenABC", (result as AddVoucherResult.Duplicate).existing.token)
        // the test that proves duplicate check runs before the fetch:
        assertEquals(0, extractor.calls.get())
        assertEquals(1, repo.snapshot().size)
    }

    @Test
    fun tokenDifferingOnlyInCaseIsANewEntry() = runTest {
        val repo = FakeVoucherRepository()
        repo.insert(voucher("TokenABC"))
        val extractor = FakeExtractor(success)
        val flow = AddVoucherFlow(repo, extractor)

        val result = flow.add(context, "https://voucher.redeem.gov.sg/tokenabc")

        assertTrue(result is AddVoucherResult.Added)
        assertEquals(2, repo.snapshot().size)
        assertEquals(1, extractor.calls.get())
    }

    @Test
    fun fetchFailureStillSavesRowAsUnverified() = runTest {
        val repo = FakeVoucherRepository()
        val extractor = FakeExtractor(ExtractionResult.Failure(ExtractionResult.FailureReason.NETWORK_ERROR))
        val flow = AddVoucherFlow(repo, extractor)

        val result = flow.add(context, "https://voucher.redeem.gov.sg/TokenXYZ")

        assertTrue(result is AddVoucherResult.AddedUnverified)
        val voucher = (result as AddVoucherResult.AddedUnverified).voucher
        assertEquals(ValidityStatus.UNVERIFIED, voucher.validityStatus)
        assertNull(voucher.expiryDate)
        assertEquals("TokenXYZ", voucher.campaignName)
        assertEquals("NETWORK_ERROR", voucher.lastRefreshError)
        assertEquals(1, repo.snapshot().size)
    }

    @Test
    fun cancelledMidFetchInsertsNothing() = runTest {
        val repo = FakeVoucherRepository()
        val extractor = SuspendingExtractor()
        val flow = AddVoucherFlow(repo, extractor)
        var outcome: Any? = "not-run"
        val job = launch {
            outcome = flow.add(context, "https://voucher.redeem.gov.sg/TokenZZZ")
        }
        // Refactor D10: deterministic gate - wait until the extraction was
        // actually entered before cancelling (replaces Thread.sleep).
        extractor.started.await()
        job.cancel()
        job.join()
        assertTrue("flow must not return a result after cancellation", outcome == "not-run")
        assertEquals(0, repo.snapshot().size)
        assertEquals(0, repo.insertCalls.get())
    }

    @Test
    fun rejectedInsertReportsDuplicateInsteadOfSilentSuccess() = runTest {
        // Refactor M3: the unique-token backstop can reject the row after the
        // duplicate pre-check (concurrent add won the race). The add must
        // report a Duplicate - never a success with no saved row.
        val repo = FakeVoucherRepository().apply { failInsert = true }
        val flow = AddVoucherFlow(repo, FakeExtractor(success))

        val result = flow.add(context, "https://voucher.redeem.gov.sg/TokenABC")

        assertTrue("rejected insert must surface as Duplicate, got $result", result is AddVoucherResult.Duplicate)
        assertEquals(1, repo.insertCalls.get())
        assertEquals(0, repo.snapshot().size)
    }

    @Test
    fun cancelledBetweenExtractionAndInsertSavesNothing() = runTest {
        // Refactor M4: cancellation that lands while the extraction is still
        // resolving must be caught by the pre-insert ensureActive check - the
        // row must never be saved from an abandoned add.
        val repo = FakeVoucherRepository()
        val extractor = GateExtractor()
        val flow = AddVoucherFlow(repo, extractor)
        var outcome: Any? = "not-run"
        val job = launch {
            outcome = flow.add(context, "https://voucher.redeem.gov.sg/TokenZZZ")
        }
        // Refactor D10: deterministic gate instead of a fixed sleep.
        extractor.started.await()
        job.cancel()
        // The extraction completes only AFTER the cancellation - exactly the
        // window the ensureActive check closes.
        extractor.release(success)
        job.join()
        assertTrue("flow must not return a result after cancellation", outcome == "not-run")
        assertEquals(0, repo.insertCalls.get())
        assertEquals(0, repo.snapshot().size)
    }
}
