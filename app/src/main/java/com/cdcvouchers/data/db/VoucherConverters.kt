package com.cdcvouchers.data.db

import androidx.room.TypeConverter
import com.cdcvouchers.data.model.CategoryBalance
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Room TypeConverters. categoryBalances is a small list (typically 1-3 entries),
 * stored as a serialized JSON column rather than a normalized child table
 * (spec 01 §1.1).
 */
class VoucherConverters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun categoryBalancesToJson(value: List<CategoryBalance>): String =
        json.encodeToString(ListSerializer(CategoryBalance.serializer()), value)

    @TypeConverter
    fun jsonToCategoryBalances(value: String): List<CategoryBalance> =
        json.decodeFromString(ListSerializer(CategoryBalance.serializer()), value)

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}
