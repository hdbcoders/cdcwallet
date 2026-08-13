package com.cdcwallet.backup

import com.cdcwallet.data.FakeVoucherRepository
import com.cdcwallet.data.backup.BackupFlow
import com.cdcwallet.data.backup.BackupService
import com.cdcwallet.data.backup.InvalidBackupPayloadException
import com.cdcwallet.data.model.CategoryBalance
import com.cdcwallet.data.model.ValidityStatus
import com.cdcwallet.data.model.VoucherBackupPayload
import com.cdcwallet.data.model.VoucherGroup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class BackupFlowTest {

    private fun voucher(token: String, id: String = "id-$token") = VoucherGroup(
        id = id,
        token = token,
        url = "https://voucher.redeem.gov.sg/$token",
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

        val imported = backupFlow.importMerge(payload(voucher("A"), voucher("B"), voucher("C")))

        assertEquals(2, imported)
        assertEquals(listOf("A", "B", "C"), repo.snapshot().map { it.token })
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

        // In-payload duplicate TOKENS are rejected by validation (refactor H4)
        // before the dedupe layer runs - the payload is all-or-nothing.
        var rejected = false
        try {
            backupFlow.importReplace(payload(voucher("A"), voucher("A", id = "id-A-2")))
        } catch (e: InvalidBackupPayloadException) {
            rejected = true
        }
        assertTrue("duplicate tokens in payload must be rejected", rejected)
        // The existing rows were NOT touched by the rejected replace.
        assertEquals(listOf("OLD"), repo.snapshot().map { it.token })
    }

    @Test
    fun importMergeMatchesOnlyOnTokenNotId() = runTest {
        val repo = FakeVoucherRepository()
        repo.bulkInsert(listOf(voucher("X", id = "local-id")))
        val backupFlow = flow(repo)

        // Same token, different id/url/name - the duplicate rule is token-based
        // (01 §1.4), so the incoming row must NOT be imported. The differing
        // URL is still a valid official-shaped link for the same token.
        val imported = backupFlow.importMerge(
            payload(voucher("X", id = "backup-id").copy(url = "https://voucher.redeem.gov.sg/X?lang=en-US")),
        )

        assertEquals(0, imported)
        assertEquals(listOf("local-id"), repo.snapshot().map { it.id })
    }

    // --- Refactor H4: every imported row is validated before any database
    // mutation; an invalid payload is rejected wholesale. ---

    @Test
    fun nonOfficialUrlRejectsWholePayloadBeforeAnyMutation() = runTest {
        val repo = FakeVoucherRepository()
        repo.bulkInsert(listOf(voucher("OLD")))
        val backupFlow = flow(repo)

        var rejected = false
        try {
            backupFlow.importMerge(payload(voucher("A"), voucher("B").copy(url = "https://evil.example.com/B")))
        } catch (e: InvalidBackupPayloadException) {
            rejected = true
        }

        assertTrue(rejected)
        assertEquals(listOf("OLD"), repo.snapshot().map { it.token })
    }

    @Test
    fun tokenUrlMismatchRejectsPayload() = runTest {
        val repo = FakeVoucherRepository()
        val backupFlow = flow(repo)

        var rejected = false
        try {
            // The row opens XYZ789 but identifies as ABC123 - the app must
            // never import such a row (refactor H4).
            backupFlow.importMerge(
                payload(voucher("ABC123").copy(url = "https://voucher.redeem.gov.sg/XYZ789")),
            )
        } catch (e: InvalidBackupPayloadException) {
            rejected = true
        }

        assertTrue(rejected)
        assertTrue(repo.snapshot().isEmpty())
    }

    @Test
    fun routeUnsafeIdRejectsPayload() = runTest {
        val repo = FakeVoucherRepository()
        val backupFlow = flow(repo)

        var rejected = false
        try {
            backupFlow.importMerge(payload(voucher("A", id = "bad/id?x")))
        } catch (e: InvalidBackupPayloadException) {
            rejected = true
        }

        assertTrue(rejected)
        assertTrue(repo.snapshot().isEmpty())
    }

    @Test
    fun duplicateImportedIdsRejectPayloadBeforeAnyMutation() = runTest {
        // Refactor D12: two rows sharing one id in the payload must be
        // rejected wholesale - the merge must never half-apply.
        val repo = FakeVoucherRepository()
        val backupFlow = flow(repo)

        var rejected = false
        try {
            backupFlow.importMerge(
                payload(
                    voucher("A", id = "shared-id"),
                    voucher("B", id = "shared-id"),
                ),
            )
        } catch (e: InvalidBackupPayloadException) {
            rejected = true
        }

        assertTrue("duplicate imported ids must reject the payload", rejected)
        assertTrue(repo.snapshot().isEmpty())
    }

    @Test
    fun negativeBalanceRejectsPayload() = runTest {
        val repo = FakeVoucherRepository()
        val backupFlow = flow(repo)

        var rejected = false
        try {
            backupFlow.importMerge(
                payload(voucher("A").copy(categoryBalances = listOf(CategoryBalance("heartland", BigDecimal("-5"))))),
            )
        } catch (e: InvalidBackupPayloadException) {
            rejected = true
        }

        assertTrue(rejected)
        assertTrue(repo.snapshot().isEmpty())
    }

    @Test
    fun oversizedFieldRejectsPayload() = runTest {
        val repo = FakeVoucherRepository()
        val backupFlow = flow(repo)

        var rejected = false
        try {
            backupFlow.importMerge(payload(voucher("A").copy(campaignName = "x".repeat(400))))
        } catch (e: InvalidBackupPayloadException) {
            rejected = true
        }

        assertTrue(rejected)
        assertTrue(repo.snapshot().isEmpty())
    }

    @Test
    fun replaceModeRejectsInvalidPayloadWithoutDeletingExistingRows() = runTest {
        val repo = FakeVoucherRepository()
        repo.bulkInsert(listOf(voucher("KEEP")))
        val backupFlow = flow(repo)

        var rejected = false
        try {
            backupFlow.importReplace(
                payload(voucher("NEW").copy(url = "https://voucher.redeem.gov.sg/groups/NEW")),
            )
        } catch (e: InvalidBackupPayloadException) {
            rejected = true
        }

        // The most destructive operation in the app must never wipe rows for
        // a backup that would not import (refactor H4).
        assertTrue(rejected)
        assertEquals(listOf("KEEP"), repo.snapshot().map { it.token })
    }

    @Test
    fun replaceModeAcceptsValidOfficialShapedPayload() = runTest {
        val repo = FakeVoucherRepository()
        repo.bulkInsert(listOf(voucher("OLD")))
        val backupFlow = flow(repo)

        backupFlow.importReplace(payload(voucher("NEW"), voucher("KEPT")))

        assertEquals(setOf("NEW", "KEPT"), repo.snapshot().map { it.token }.toSet())
    }
}
