package com.cdcvouchers

import android.app.Application
import android.content.Context
import android.webkit.WebView
import androidx.room.Room
import com.cdcvouchers.data.RoomVoucherRepository
import com.cdcvouchers.data.VoucherRepository
import com.cdcvouchers.data.backup.BackupFlow
import com.cdcvouchers.data.db.AppDatabase
import com.cdcvouchers.data.db.SqlCipherNative
import com.cdcvouchers.data.db.SqlCipherPassphraseStore
import com.cdcvouchers.extraction.ExtractionEngine
import com.cdcvouchers.ui.theme.ThemeModeStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

open class VoucherApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Debug-only: allow CDP inspection of the WebViews (chrome://inspect).
        // Release builds never enable this.
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        container = AppContainer(this)
        // Eager warm-up of the long-lived visible WebView (spec 02 §2.4
        // revision 2026-08-03): engine init, renderer, and DNS are warm before
        // the first tap, so taps feel like a phone browser. Loads no URL — no
        // network traffic. Failures here degrade to first-tap creation.
        container.extractionEngine.warmUp(this)
    }
}

/** Hand-rolled composition root — no DI framework for a sideload app of this size. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val passphraseStore: SqlCipherPassphraseStore by lazy {
        SqlCipherPassphraseStore(appContext)
    }

    val themeModeStore: ThemeModeStore by lazy {
        ThemeModeStore(appContext)
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
