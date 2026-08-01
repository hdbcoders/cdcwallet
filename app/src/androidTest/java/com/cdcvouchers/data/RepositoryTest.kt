package com.cdcvouchers.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cdcvouchers.data.db.AppDatabase
import com.cdcvouchers.data.db.SqlCipherNative
import com.cdcvouchers.data.model.CategoryBalance
import com.cdcvouchers.data.model.ValidityStatus
import com.cdcvouchers.data.model.VoucherGroup
import com.cdcvouchers.data.model.VoucherRefreshData
import kotlinx.coroutines.flow.first
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private lateinit var repository: VoucherRepository

    @Before
    fun setUp() {
        SqlCipherNative.load()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .openHelperFactory(SupportOpenHelperFactory("test-passphrase".toByteArray()))
            .allowMainThreadQueries()
            .build()
        repository = RoomVoucherRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun voucher(
        token: String = "TokenA",
        status: ValidityStatus = ValidityStatus.ACTIVE,
        archived: Boolean = false,
    ) = VoucherGroup(
        id = "id-$token",
        token = token,
        url = "https://voucher.redeem.gov.sg/$token?lang=en-US",
        campaignName = "Campaign $token",
        validityStatus = status,
        expiryDate = LocalDate.of(2026, 12, 31),
        categoryBalances = listOf(CategoryBalance("heartland", BigDecimal("10"))),
        dateAdded = Instant.parse("2026-07-01T08:00:00Z"),
        lastRefreshedAt = null,
        lastRefreshError = null,
        isArchived = archived,
    )

    @Test
    fun insertThenFindByToken() = runTest {
        repository.insert(voucher("TokenA"))
        val found = repository.findByToken("TokenA")
        assertNotNull(found)
        assertEquals("TokenA", found?.token)
        assertEquals("Campaign TokenA", found?.campaignName)
        assertEquals(ValidityStatus.ACTIVE, found?.validityStatus)
        assertEquals(LocalDate.of(2026, 12, 31), found?.expiryDate)
        assertEquals(listOf(CategoryBalance("heartland", BigDecimal("10"))), found?.categoryBalances)
        assertNull(found?.lastRefreshedAt)
        assertFalse(found?.isArchived ?: true)
    }

    @Test
    fun duplicateTokenInsertReturnsFalse() = runTest {
        repository.insert(voucher("TokenA"))
        val second = repository.insert(voucher("TokenA", status = ValidityStatus.ACTIVE))
        assertFalse(second)
        assertEquals(1, repository.observeActive().first().size)
    }

    @Test
    fun tokensDifferingOnlyInCaseCoexist() = runTest {
        assertTrue(repository.insert(voucher("TokenA")))
        assertTrue(repository.insert(voucher("tokena")))
        assertNotNull(repository.findByToken("TokenA"))
        assertNotNull(repository.findByToken("tokena"))
        assertEquals(2, repository.observeActive().first().size)
    }

    @Test
    fun updateFromRefreshWritesAllFieldsAndClearsError() = runTest {
        repository.insert(voucher("TokenA"))
        val existing = repository.findByToken("TokenA")!!
        repository.recordRefreshFailure(existing.id, "Website data failed to parse")

        val refreshed = VoucherRefreshData(
            campaignName = "SG60 Vouchers",
            validityStatus = ValidityStatus.NOT_STARTED,
            expiryDate = LocalDate.of(2027, 3, 1),
            categoryBalances = listOf(
                CategoryBalance("heartland", BigDecimal("100")),
                CategoryBalance("supermarket", BigDecimal("200.50")),
            ),
            lastRefreshedAt = Instant.parse("2026-08-01T10:00:00Z"),
        )
        repository.updateFromRefresh(existing.id, refreshed)

        val updated = repository.findByToken("TokenA")!!
        assertEquals("SG60 Vouchers", updated.campaignName)
        assertEquals(ValidityStatus.NOT_STARTED, updated.validityStatus)
        assertEquals(LocalDate.of(2027, 3, 1), updated.expiryDate)
        assertEquals(
            listOf(
                CategoryBalance("heartland", BigDecimal("100")),
                CategoryBalance("supermarket", BigDecimal("200.50")),
            ),
            updated.categoryBalances,
        )
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), updated.lastRefreshedAt)
        assertNull(updated.lastRefreshError)
    }

    @Test
    fun updateFromRefreshOnMissingRowIsSafeNoOp() = runTest {
        val result = repository.updateFromRefresh(
            id = "does-not-exist",
            data = VoucherRefreshData(
                campaignName = "X",
                validityStatus = ValidityStatus.EXPIRED,
                expiryDate = null,
                categoryBalances = emptyList(),
                lastRefreshedAt = Instant.now(),
            ),
        )
        // No crash, no row created.
        assertEquals(0, repository.observeActive().first().size)
        assertNull(repository.findByToken("X"))
    }

    @Test
    fun recordRefreshFailureKeepsCachedFields() = runTest {
        repository.insert(voucher("TokenA"))
        val existing = repository.findByToken("TokenA")!!
        repository.updateFromRefresh(
            existing.id,
            VoucherRefreshData(
                campaignName = "Fresh",
                validityStatus = ValidityStatus.ACTIVE,
                expiryDate = LocalDate.of(2026, 12, 31),
                categoryBalances = listOf(CategoryBalance("heartland", BigDecimal("42"))),
                lastRefreshedAt = Instant.parse("2026-08-01T10:00:00Z"),
            ),
        )

        repository.recordRefreshFailure(existing.id, "Unable to load website")

        val after = repository.findByToken("TokenA")!!
        assertEquals("Unable to load website", after.lastRefreshError)
        assertEquals("Fresh", after.campaignName)
        assertEquals(listOf(CategoryBalance("heartland", BigDecimal("42"))), after.categoryBalances)
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), after.lastRefreshedAt)
    }

    @Test
    fun archiveRestoreMoveBetweenLists() = runTest {
        repository.insert(voucher("TokenA"))
        repository.insert(voucher("TokenB"))
        val a = repository.findByToken("TokenA")!!

        repository.archive(a.id)
        assertEquals(listOf("TokenB"), repository.observeActive().first().map { it.token })
        assertEquals(listOf("TokenA"), repository.observeArchived().first().map { it.token })

        repository.restore(a.id)
        assertEquals(2, repository.observeActive().first().size)
        assertEquals(0, repository.observeArchived().first().size)
    }

    @Test
    fun deleteRemovesRowPermanently() = runTest {
        repository.insert(voucher("TokenA"))
        val a = repository.findByToken("TokenA")!!
        repository.delete(a.id)
        assertNull(repository.findByToken("TokenA"))
        assertEquals(0, repository.observeActive().first().size)
    }

    @Test
    fun replaceAllWipesAndReinserts() = runTest {
        repository.insert(voucher("TokenA"))
        repository.replaceAll(listOf(voucher("TokenB"), voucher("TokenC")))
        val active = repository.observeActive().first()
        assertEquals(listOf("TokenB", "TokenC"), active.map { it.token }.sorted())
        assertNull(repository.findByToken("TokenA"))
    }

    @Test
    fun bulkInsertAddsAll() = runTest {
        repository.bulkInsert(listOf(voucher("TokenA"), voucher("TokenB")))
        assertEquals(2, repository.observeActive().first().size)
    }

    @Test
    fun unverifiedRowRoundTrips() = runTest {
        val unverified = voucher("TokUnv", status = ValidityStatus.UNVERIFIED).copy(
            campaignName = "TokUnv",
            expiryDate = null,
            categoryBalances = emptyList(),
            lastRefreshedAt = null,
        )
        repository.insert(unverified)
        val found = repository.findByToken("TokUnv")!!
        assertEquals(ValidityStatus.UNVERIFIED, found.validityStatus)
        assertNull(found.expiryDate)
        assertNull(found.lastRefreshedAt)
        assertTrue(found.categoryBalances.isEmpty())
    }

    private fun runTest(block: suspend () -> Unit) {
        kotlinx.coroutines.test.runTest { block() }
    }
}
