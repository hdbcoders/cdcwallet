package com.hdbcoders.cdcwallet.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hdbcoders.cdcwallet.data.model.VoucherGroup

@Database(
    entities = [VoucherGroup::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(VoucherConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voucherDao(): VoucherDao

    companion object {
        /**
         * v1 -> v2: adds the device-local pin flag (one pinned row max,
         * enforced by the repository, not the schema). Pure column addition
         * with a constant default - existing rows read back unpinned.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE voucher_groups ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }
}
