package com.hdbcoders.cdcwallet.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hdbcoders.cdcwallet.data.model.VoucherGroup

@Database(
    entities = [VoucherGroup::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(VoucherConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voucherDao(): VoucherDao
}
