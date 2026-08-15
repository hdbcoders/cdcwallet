package com.hdbcoders.cdcwallet.update

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REQ-13 update-check logic: the 24h throttle, the silent vs user entry
 * points, flag persistence through the store, stale-flag clearing on app
 * update, fail-soft behavior, and the Play Store URI builders. Pure JVM
 * tests - the store/source/clock are injected fakes.
 */
class UpdateCheckerTest {

    private class FakeStore : UpdateCheckStore {
        var lastCheck = 0L
        var flagged = false
        var flagVersion = -1
        override fun lastCheckMs(): Long = lastCheck
        override fun stampLastCheck(now: Long) {
            lastCheck = now
        }

        override fun isFlagged(): Boolean = flagged
        override fun flagVersionCode(): Int = flagVersion
        override fun setFlagged(installedVersionCode: Int) {
            flagged = true
            flagVersion = installedVersionCode
        }

        override fun clearFlagged() {
            flagged = false
            flagVersion = -1
        }
    }

    private class FakeSource(
        var result: UpdateCheckResult = UpdateCheckResult.NotAvailable,
        var throwOnCheck: Boolean = false,
    ) : UpdateAvailabilitySource {
        var calls = 0
        override suspend fun check(): UpdateCheckResult {
            calls++
            if (throwOnCheck) throw RuntimeException("play unavailable")
            return result
        }
    }

    private val now = 1_000_000_000L

    private fun checker(
        store: UpdateCheckStore = FakeStore(),
        source: FakeSource = FakeSource(),
        installedVersion: () -> Int = { 5 },
    ) = UpdateChecker(
        store = store,
        source = source,
        clock = { now },
        installedVersionCode = installedVersion,
    )

    // --- Pure throttle decision -------------------------------------------------

    @Test
    fun `never checked is due`() {
        assertTrue(isCheckDue(lastCheckMs = 0L, now = now, intervalMs = UpdateChecker.CHECK_INTERVAL_MS))
    }

    @Test
    fun `checked 23h ago is not due`() {
        assertFalse(
            isCheckDue(
                lastCheckMs = now - (UpdateChecker.CHECK_INTERVAL_MS - 3_600_000L),
                now = now,
                intervalMs = UpdateChecker.CHECK_INTERVAL_MS,
            ),
        )
    }

    @Test
    fun `checked 25h ago is due`() {
        assertTrue(
            isCheckDue(
                lastCheckMs = now - (UpdateChecker.CHECK_INTERVAL_MS + 3_600_000L),
                now = now,
                intervalMs = UpdateChecker.CHECK_INTERVAL_MS,
            ),
        )
    }

    // --- Silent check -----------------------------------------------------------

    @Test
    fun `silent check runs when never checked and sets flag on available`() = runTest {
        val store = FakeStore()
        val source = FakeSource(UpdateCheckResult.Available)
        val c = checker(store, source)

        c.silentCheckIfDue()

        assertEquals(1, source.calls)
        assertTrue(c.updateAvailable)
        assertTrue(store.flagged)
        assertEquals(5, store.flagVersion)
        assertEquals(now, store.lastCheck)
    }

    @Test
    fun `silent check skips within 24h of the last check`() = runTest {
        val store = FakeStore().apply { lastCheck = now - 3_600_000L } // 1h ago
        val source = FakeSource(UpdateCheckResult.Available)
        val c = checker(store, source)

        c.silentCheckIfDue()

        assertEquals(0, source.calls)
        assertFalse(c.updateAvailable)
    }

    @Test
    fun `silent check runs again after 24h`() = runTest {
        val store = FakeStore().apply { lastCheck = now - (UpdateChecker.CHECK_INTERVAL_MS + 1) }
        val source = FakeSource(UpdateCheckResult.Available)
        val c = checker(store, source)

        c.silentCheckIfDue()

        assertEquals(1, source.calls)
        assertTrue(c.updateAvailable)
    }

    @Test
    fun `silent check never queries while the flag is already set`() = runTest {
        val store = FakeStore().apply {
            flagged = true
            flagVersion = 5
        }
        val source = FakeSource(UpdateCheckResult.Available)
        val c = checker(store, source)

        c.silentCheckIfDue()

        assertEquals(0, source.calls)
        assertTrue(c.updateAvailable)
    }

    @Test
    fun `failed silent check stamps the timestamp and leaves the flag untouched`() = runTest {
        val store = FakeStore()
        val source = FakeSource(throwOnCheck = true)
        val c = checker(store, source)

        c.silentCheckIfDue()

        assertEquals(now, store.lastCheck) // no retry-hammer for 24h
        assertFalse(c.updateAvailable)
        assertFalse(store.flagged)
    }

    // --- User check -------------------------------------------------------------

    @Test
    fun `user check bypasses the throttle`() = runTest {
        val store = FakeStore().apply { lastCheck = now - 3_600_000L } // 1h ago
        val source = FakeSource(UpdateCheckResult.Available)
        val c = checker(store, source)

        val result = c.userCheck()

        assertEquals(1, source.calls)
        assertEquals(UpdateCheckResult.Available, result)
        assertTrue(c.updateAvailable)
    }

    @Test
    fun `user check not-available leaves the flag off`() = runTest {
        val source = FakeSource(UpdateCheckResult.NotAvailable)
        val c = checker(FakeStore(), source)

        val result = c.userCheck()

        assertEquals(UpdateCheckResult.NotAvailable, result)
        assertFalse(c.updateAvailable)
    }

    @Test
    fun `user check failure maps to Failed and leaves the flag off`() = runTest {
        val source = FakeSource(throwOnCheck = true)
        val c = checker(FakeStore(), source)

        val result = c.userCheck()

        assertEquals(UpdateCheckResult.Failed, result)
        assertFalse(c.updateAvailable)
    }

    @Test
    fun `user check while already flagged returns Available without querying`() = runTest {
        val store = FakeStore().apply {
            flagged = true
            flagVersion = 5
        }
        val source = FakeSource(UpdateCheckResult.NotAvailable)
        val c = checker(store, source)

        val result = c.userCheck()

        assertEquals(UpdateCheckResult.Available, result)
        assertEquals(0, source.calls)
    }

    // --- Stale flag -------------------------------------------------------------

    @Test
    fun `stale flag is cleared when the installed version changed`() {
        val store = FakeStore().apply {
            flagged = true
            flagVersion = 4 // flag set under the previous version
        }
        val c = checker(store, installedVersion = { 5 })

        assertFalse(c.updateAvailable)
        assertFalse(store.flagged)
    }

    @Test
    fun `flag survives when the installed version is unchanged`() {
        val store = FakeStore().apply {
            flagged = true
            flagVersion = 5
        }
        val c = checker(store, installedVersion = { 5 })

        assertTrue(c.updateAvailable)
        assertTrue(store.flagged)
    }

    // --- Play Store URI ---------------------------------------------------------

    @Test
    fun `play store URIs target the package`() {
        assertEquals(
            "market://details?id=com.hdbcoders.cdcwallet",
            playStoreMarketUri("com.hdbcoders.cdcwallet"),
        )
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.hdbcoders.cdcwallet",
            playStoreWebUri("com.hdbcoders.cdcwallet"),
        )
    }
}
