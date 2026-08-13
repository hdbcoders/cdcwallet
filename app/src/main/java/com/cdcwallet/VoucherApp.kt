package com.cdcwallet

import android.app.Application
import android.content.Context
import android.webkit.WebView
import com.cdcwallet.data.RoomVoucherRepository
import com.cdcwallet.data.VoucherRepository
import com.cdcwallet.data.backup.BackupFlow
import com.cdcwallet.data.db.AppDatabase
import com.cdcwallet.data.db.SqlCipherPassphraseStore
import com.cdcwallet.extraction.ExtractionCoordinator
import com.cdcwallet.extraction.ExtractionEngine
import com.cdcwallet.ui.components.HeroCollapseStore
import com.cdcwallet.ui.theme.FontScaleStore
import com.cdcwallet.ui.theme.LanguageStore
import com.cdcwallet.ui.theme.ThemeModeStore

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
        // the first tap, so taps feel like a phone browser. Loads no URL - no
        // network traffic. Failures here degrade to first-tap creation.
        container.extractionEngine.warmUp(this)
    }
}

/** Hand-rolled composition root - no DI framework for a sideload app of this size. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val passphraseStore: SqlCipherPassphraseStore by lazy {
        SqlCipherPassphraseStore(appContext)
    }

    val themeModeStore: ThemeModeStore by lazy {
        ThemeModeStore(appContext)
    }

    val fontScaleStore: FontScaleStore by lazy {
        FontScaleStore(appContext)
    }

    val languageStore: LanguageStore by lazy {
        LanguageStore(appContext)
    }

    val heroCollapseStore: HeroCollapseStore by lazy {
        HeroCollapseStore(appContext)
    }

    val databaseBootstrap: DatabaseBootstrap by lazy {
        DatabaseBootstrap.create(appContext, SqlCipherPassphraseStore(appContext)).also { it.start() }
    }

    val repository: VoucherRepository by lazy {
        RoomVoucherRepository(
            checkNotNull(databaseBootstrap.database) {
                "repository accessed before database bootstrap completed"
            },
        )
    }

    /** Debug tooling only (SeedDevDataReceiver); same readiness contract as [repository]. */
    val database: AppDatabase
        get() = checkNotNull(databaseBootstrap.database) {
            "database accessed before bootstrap completed"
        }

    val extractionEngine: ExtractionEngine by lazy {
        ExtractionEngine()
    }

    val extractionCoordinator: ExtractionCoordinator by lazy {
        ExtractionCoordinator(repository, extractionEngine)
    }

    val backupFlow: BackupFlow by lazy {
        BackupFlow(repository)
    }
}
