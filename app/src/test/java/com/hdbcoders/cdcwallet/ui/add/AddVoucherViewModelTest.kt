package com.hdbcoders.cdcwallet.ui.add

import android.content.Context
import com.hdbcoders.cdcwallet.R
import com.hdbcoders.cdcwallet.addflow.AddVoucherFlow
import com.hdbcoders.cdcwallet.data.FakeVoucherRepository
import com.hdbcoders.cdcwallet.data.model.CategoryBalance
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
import com.hdbcoders.cdcwallet.extraction.ExtractionResult
import com.hdbcoders.cdcwallet.extraction.VoucherExtractor
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AddVoucherViewModel terminal-state guarantees (spec 03 §3.3, refactor M3):
 * every add reaches a terminal UI state - a failed insert (the unique-token
 * backstop rejecting a race duplicate) surfaces as the DUPLICATE state, any
 * non-cancellation exception converts to the explicit generic error state
 * (never stranded in Working), and cancellation is re-thrown, not swallowed
 * into an error message. Previously only exercised incidentally at flow level.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddVoucherViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context = android.app.Application()

    private val url = "https://voucher.redeem.gov.sg/TokenABC"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val success = ExtractionResult.Success(
        campaignName = "CDC Vouchers 2026",
        validityStatus = ValidityStatus.ACTIVE,
        expiryDate = LocalDate.of(2026, 12, 31),
        categoryBalances = listOf(CategoryBalance("heartland", BigDecimal("50"))),
    )

    private fun vm(repo: FakeVoucherRepository, extractor: VoucherExtractor) =
        AddVoucherViewModel(AddVoucherFlow(repo, extractor), context)

    @Test
    fun failedInsertSurfacesAsDuplicateState() = runTest(dispatcher) {
        // Refactor M3: the unique-token backstop rejects the inserted row (a
        // concurrent add won the race) - the screen must show the DUPLICATE
        // state, never silent success and never a stranded Working.
        val repo = FakeVoucherRepository()
        repo.failInsert = true
        val viewModel = vm(repo, object : VoucherExtractor {
            override suspend fun extractForAdd(context: Context, url: String) = success
        })

        viewModel.onUrlArrived(url)
        advanceUntilIdle()

        val status = viewModel.status
        assertTrue("expected a Message state, was $status", status is AddUiStatus.Message)
        status as AddUiStatus.Message
        assertEquals("failed insert must map to the duplicate message", R.string.add_duplicate, status.resId)
        assertEquals(false, status.isError)
        // Nothing was persisted.
        assertTrue("no row may be saved when the insert was rejected", repo.snapshot().isEmpty())
    }

    @Test
    fun extractionExceptionReachesGenericErrorState() = runTest(dispatcher) {
        // Refactor M3: ANY non-cancellation exception in the pipeline converts
        // to an explicit error state - the screen is never stranded in Working.
        val viewModel = vm(FakeVoucherRepository(), object : VoucherExtractor {
            override suspend fun extractForAdd(context: Context, url: String): ExtractionResult {
                throw IllegalStateException("storage exploded")
            }
        })

        viewModel.onUrlArrived(url)
        advanceUntilIdle()

        val status = viewModel.status
        assertTrue("expected a Message state, was $status", status is AddUiStatus.Message)
        status as AddUiStatus.Message
        assertEquals(R.string.add_failed_generic, status.resId)
        assertEquals(true, status.isError)
    }

    @Test
    fun cancellationIsRethrownNotSwallowedIntoErrorState() = runTest(dispatcher) {
        // Spec 03 §3.3: a CancellationException (screen abandonment) must
        // propagate - it is NOT an error, so the status must stay Working
        // (never converted into a generic-failure message).
        val repo = FakeVoucherRepository()
        val viewModel = vm(repo, object : VoucherExtractor {
            override suspend fun extractForAdd(context: Context, url: String): ExtractionResult {
                throw CancellationException("abandoned")
            }
        })

        viewModel.onUrlArrived(url)
        advanceUntilIdle()

        assertEquals(AddUiStatus.Working, viewModel.status)
        assertTrue("no row may be saved by an abandoned add", repo.snapshot().isEmpty())
    }
}