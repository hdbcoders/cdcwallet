package com.cdcvouchers.backup

import com.cdcvouchers.data.FakeVoucherRepository
import com.cdcvouchers.data.backup.BackupFlow
import com.cdcvouchers.data.backup.BackupService
import com.cdcvouchers.data.model.CategoryBalance
import com.cdcvouchers.data.model.ValidityStatus
import com.cdcvouchers.data.model.VoucherBackupPayload
import com.cdcvouchers.data.model.VoucherGroup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class BackupFlowTest {

    private fun voucher(token: String, id: String = "id-$token") = VoucherGroup(
        id = id,
        token = token,
        url = "https://voucher.redeem.gov.sg/groups/$token",
        campaignName = token,
        validityStatus = ValidityStatus.ACTIVE,
        expiryDate = null,
        categoryBalances = listOf(CategoryBalance("heartland", BigDecimal("50"))),
        dateAdded = Instant.now(),
        lastRefreshedAt = null,
        lastRefreshError = null,
    )

    private fun payload(vararg vouchers: VoucherGroup) = VoucherBackupPayload(
        createdAt = Instant.now(),
        vouchers = vouchers.toList(),
    )

    private fun flow(repository: FakeVoucherRepository) =
        BackupFlow(repository = repository, service = BackupService())

    @Test
    fun importMergeDedupesAndBulkInserts() = runTest {
        val repo = FakeVoucherRepository()
        repo.bulkInsert(listOf(voucher("A")))
        val backupFlow = flow(repo)

        val imported = backupFlow.importMerge(payload(voucher("A"), voucher("B"), voucher("B")))

        assertEquals(1, imported)
        assertEquals(listOf("A", "B"), repo.snapshot().map { it.token })
    }

    @Test
    fun importMergeWithNothingNewReturnsZero() = runTest {
        val repo = FakeVoucherRepository()
        repo.bulkInsert(listOf(voucher("A"), voucher("B")))
        val backupFlow = flow(repo)

        val imported = backupFlow.importMerge(payload(voucher("A"), voucher("B")))

        assertEquals(0, imported)
        assertEquals(2, repo.snapshot().size)
    }

    @Test
    fun importReplaceWithDuplicateTokensInPayloadDoesNotThrow() = runTest {
        val repo = FakeVoucherRepository()
        repo.bulkInsert(listOf(voucher("OLD")))
        val backupFlow = flow(repo)

        backupFlow.importReplace(payload(voucher("A"), voucher("A", id = "id-A-2")))

        val rows = repo.snapshot()
        assertEquals(listOf("A"), rows.map { it.token })
        assertEquals(1, rows.size)
    }

    @Test
    fun importMergeMatchesOnlyOnTokenNotId() = runTest {
        val repo = FakeVoucherRepository()
        repo.bulkInsert(listOf(voucher("X", id = "local-id")))
        val backupFlow = flow(repo)

        // Same token, different id/url/name — the duplicate rule is token-based
        // (01 §1.4), so the incoming row must NOT be imported.
        val imported = backupFlow.importMerge(
            payload(voucher("X", id = "backup-id").copy(url = "https://example.com/other")),
        )

        assertEquals(0, imported)
        assertEquals(listOf("local-id"), repo.snapshot().map { it.id })
    }
}
