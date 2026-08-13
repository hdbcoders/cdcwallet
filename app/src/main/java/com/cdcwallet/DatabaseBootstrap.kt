package com.cdcwallet

import android.content.Context
import androidx.room.Room
import com.cdcwallet.data.db.AppDatabase
import com.cdcwallet.data.db.SqlCipherNative
import com.cdcwallet.data.db.SqlCipherPassphraseStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Splash/readiness state of the SQLCipher database bootstrap (refactor M2).
 */
sealed interface DatabaseBootstrapState {
    data object Initializing : DatabaseBootstrapState

    /** The database is open and safe to use. */
    data class Ready(val database: AppDatabase?) : DatabaseBootstrapState

    /** Initialization failed - the UI must surface this, never hang on it. */
    data class Failed(val cause: Throwable) : DatabaseBootstrapState
}

/**
 * Owns the expensive SQLCipher initialization and moves it OFF the main thread
 * (refactor M2): native library load, Keystore key generation/unwrap, wrapped
 * passphrase persistence, and the Room build all run on an IO-backed scope at
 * app start. The UI gates on [state] (splash) and never touches [database]
 * before `Ready` - and a `Failed` state releases the splash into an explicit
 * error screen instead of hanging.
 *
 * The wrapped passphrase is persisted SYNCHRONOUSLY inside
 * [SqlCipherPassphraseStore] (refactor M1): it is durable on disk before the
 * database is ever opened, so a process kill can never leave the database
 * without a recoverable key.
 *
 * The initializer is injectable so the failure path is unit-testable without
 * an Android runtime; production always uses [create].
 */
class DatabaseBootstrap internal constructor(
    private val initializer: suspend () -> AppDatabase?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<DatabaseBootstrapState>(DatabaseBootstrapState.Initializing)
    val state: StateFlow<DatabaseBootstrapState> = _state.asStateFlow()

    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Start the bootstrap exactly once; subsequent calls are no-ops. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val result = runCatching { initializer() }
            _state.value = result.fold(
                onSuccess = { DatabaseBootstrapState.Ready(it) },
                onFailure = { DatabaseBootstrapState.Failed(it) },
            )
        }
    }

    /** The opened database; null until `Ready` - UI must gate on [state]. */
    val database: AppDatabase?
        get() = (state.value as? DatabaseBootstrapState.Ready)?.database

    /** Suspends until the bootstrap settles and returns the open database;
     *  throws the original failure when initialization failed. */
    suspend fun awaitReady(): AppDatabase? =
        when (val settled = state.first { it !is DatabaseBootstrapState.Initializing }) {
            is DatabaseBootstrapState.Ready -> settled.database
            is DatabaseBootstrapState.Failed -> throw settled.cause
            is DatabaseBootstrapState.Initializing -> error("unreachable")
        }

    companion object {
        const val DB_NAME = "voucher.db"

        /** Production initializer: the real SQLCipher/Keystore/Room chain. */
        fun create(appContext: Context, passphraseStore: SqlCipherPassphraseStore): DatabaseBootstrap =
            DatabaseBootstrap {
                SqlCipherNative.load()
                val passphrase = passphraseStore.obtainPassphrase()
                Room.databaseBuilder(appContext, AppDatabase::class.java, DB_NAME)
                    .openHelperFactory(SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8)))
                    .build()
            }
    }
}
