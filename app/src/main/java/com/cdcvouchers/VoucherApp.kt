package com.cdcvouchers

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.cdcvouchers.data.RoomVoucherRepository
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.backup.BackupFlow
import com.cdcvouchers.data.db.AppDatabase
import com.cdcvouchers.data.db.SqlCipherNative
import com.cdcvouchers.data.db.SqlCipherPassphraseStore
import com.cdcvouchers.extraction.ExtractionEngine
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class VoucherApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-rolled composition root — no DI framework for a sideload app of this size. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val passphraseStore: SqlCipherPassphraseStore by lazy {
        SqlCipherPassphraseStore(appContext)
    }

    val database: AppDatabase by lazy {
        SqlCipherNative.load()
        val passphrase = passphraseStore.obtainPassphrase()
        Room.databaseBuilder(appContext, AppDatabase::class.java, DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8)))
            .build()
    }

    val repository: VoucherRepository by lazy {
        RoomVoucherRepository(database)
    }

    val extractionEngine: ExtractionEngine by lazy {
        ExtractionEngine()
    }

    val backupFlow: BackupFlow by lazy {
        BackupFlow(repository)
    }

    companion object {
        const val DB_NAME = "voucher.db"
    }
}
