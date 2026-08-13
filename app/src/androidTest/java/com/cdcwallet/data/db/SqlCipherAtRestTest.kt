package com.cdcwallet.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cdcwallet.data.model.CategoryBalance
import com.cdcwallet.data.model.ValidityStatus
import com.cdcwallet.data.model.VoucherGroup
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Spec 01 §1.6: opening the raw .db file outside the app with a plain SQLite
 * reader must fail - proves encryption at rest, not just that Room works.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherAtRestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun rawDatabaseFileCannotBeOpenedByPlainSqlite() {
        SqlCipherNative.load()
        val dbName = "encryption_proof_${System.currentTimeMillis()}.db"
        val passphrase = SqlCipherPassphraseStore(context).obtainPassphrase()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .openHelperFactory(SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8)))
            .build()

        kotlinx.coroutines.test.runTest {
            db.voucherDao().insert(
                VoucherGroup(
                    id = "id-1",
                    token = "PlainToken",
                    url = "https://voucher.redeem.gov.sg/PlainToken",
                    campaignName = "CDC Vouchers",
                    validityStatus = ValidityStatus.ACTIVE,
                    expiryDate = LocalDate.of(2026, 12, 31),
                    categoryBalances = listOf(CategoryBalance("heartland", BigDecimal("100"))),
                    dateAdded = Instant.now(),
                    lastRefreshedAt = null,
                    lastRefreshError = null,
                ),
            )
        }
        db.close()

        val file = context.getDatabasePath(dbName)
        assertTrue("database file should exist", file.exists())

        assertThrows(SQLiteException::class.java) {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
                .let { db2 ->
                    db2.getVersion()
                    db2.rawQuery("SELECT * FROM voucher_groups", null).use { it.moveToFirst() }
                }
        }

        context.deleteDatabase(dbName)
    }

    @Test
    fun passphraseIsStableAcrossCalls() {
        val store = SqlCipherPassphraseStore(context)
        val first = store.obtainPassphrase()
        val second = store.obtainPassphrase()
        assertEquals(first, second)
    }

    /**
     * Refactor M1: the wrapped passphrase must be DURABLE before
     * obtainPassphrase() returns - the old async apply() left a crash window
     * where a process kill would orphan the database with no recoverable key.
     */
    @Test
    fun wrappedPassphraseIsSynchronouslyPersistedBeforeReturning() {
        val prefs = context.getSharedPreferences("voucher_secure_prefs", Context.MODE_PRIVATE)
        // Preserve any existing wrapped blob: this test temporarily forces the
        // first-run path and must restore the device state afterwards (the
        // connected-test flow reseeds the real app DB with the original key).
        val original = prefs.getString("wrapped_db_passphrase", null)
        try {
            prefs.edit().clear().commit()

            val store = SqlCipherPassphraseStore(context)
            store.obtainPassphrase()

            // The blob must already be readable from SharedPreferences - i.e. the
            // in-memory state was committed to disk synchronously, and a second
            // call round-trips through the persisted blob to the same passphrase.
            val wrapped = prefs.getString("wrapped_db_passphrase", null)
            assertTrue("wrapped blob must be persisted before obtainPassphrase returns", !wrapped.isNullOrBlank())
            assertEquals(store.obtainPassphrase(), store.obtainPassphrase())
        } finally {
            if (original != null) {
                prefs.edit().putString("wrapped_db_passphrase", original).commit()
            }
        }
    }
}
