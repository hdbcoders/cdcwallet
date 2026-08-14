package com.hdbcoders.cdcwallet

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bootstrap state-machine tests (refactor M2): the splash gate keys on the
 * state leaving `Initializing` - both outcomes must complete the gate, and a
 * failure must carry its cause through `awaitReady()`.
 */
class DatabaseBootstrapTest {

    @Test
    fun `failure completes readiness with a Failed state`() = runTest {
        val boom = IllegalStateException("init failed")
        val bootstrap = DatabaseBootstrap { throw boom }
        bootstrap.start()

        // The splash gate predicate is "not Initializing" - a failure must
        // satisfy it, never hang it.
        val settled = bootstrap.state.first { it !is DatabaseBootstrapState.Initializing }
        assertTrue("expected Failed, got $settled", settled is DatabaseBootstrapState.Failed)
        assertTrue((settled as DatabaseBootstrapState.Failed).cause === boom)
        assertTrue(bootstrap.database == null)

        val thrown = runCatching { bootstrap.awaitReady() }.exceptionOrNull()
        assertTrue("awaitReady must rethrow the original cause", thrown === boom)
    }

    @Test
    fun `success completes readiness with a Ready state`() = runTest {
        val bootstrap = DatabaseBootstrap { fakeDatabase() }
        bootstrap.start()

        val settled = bootstrap.state.first { it !is DatabaseBootstrapState.Initializing }
        assertTrue("expected Ready, got $settled", settled is DatabaseBootstrapState.Ready)
        // (The synthetic sentinel database is null-typed; the state machine
        // only cares that the initializer completed.)
    }

    @Test
    fun `start is idempotent`() = runTest {
        var calls = 0
        val bootstrap = DatabaseBootstrap {
            calls++
            fakeDatabase()
        }
        bootstrap.start()
        bootstrap.start()
        bootstrap.state.first { it !is DatabaseBootstrapState.Initializing }
        assertTrue("initializer must run exactly once, ran $calls", calls == 1)
    }

    private fun fakeDatabase(): com.hdbcoders.cdcwallet.data.db.AppDatabase? {
        // The state machine never opens the instance; a null sentinel proves
        // the Ready transition regardless of the concrete database.
        return null
    }
}
