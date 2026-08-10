package com.cdcwallet.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Core entity — one row per saved voucher link. Canonical definition lives here
 * (spec 01 §1.2); every other package imports these types as-is.
 */
@Serializable
@Entity(
    tableName = "voucher_groups",
    indices = [Index(value = ["token"], unique = true)],
)
data class VoucherGroup(
    @PrimaryKey val id: String,
    val token: String,
    val url: String,
    val campaignName: String,
    val validityStatus: ValidityStatus,
    @Serializable(with = LocalDateAsIsoSerializer::class)
    val expiryDate: LocalDate?,
    val categoryBalances: List<CategoryBalance>,
    @Serializable(with = InstantAsEpochMillisSerializer::class)
    val dateAdded: Instant,
    @Serializable(with = InstantAsEpochMillisSerializer::class)
    val lastRefreshedAt: Instant?,
    val lastRefreshError: String?,
    val isArchived: Boolean = false,
)

@Serializable
enum class ValidityStatus {
    UNVERIFIED,
    NOT_STARTED,
    ACTIVE,
    EXPIRED,
}

@Serializable
data class CategoryBalance(
    val category: String,
    @Serializable(with = BigDecimalAsStringSerializer::class)
    val remainingValue: BigDecimal,
)

/**
 * Envelope for the encrypted export/import file (Package 6). Never persisted as a
 * local table; assembled/disassembled at export/import time.
 */
@Serializable
data class VoucherBackupPayload(
    val formatVersion: Int = 1,
    @Serializable(with = InstantAsEpochMillisSerializer::class)
    val createdAt: Instant,
    val vouchers: List<VoucherGroup>,
)

/** Snapshot written to a row by a successful refresh (spec 01 §1.3). */
data class VoucherRefreshData(
    val campaignName: String,
    val validityStatus: ValidityStatus,
    val expiryDate: LocalDate?,
    val categoryBalances: List<CategoryBalance>,
    val lastRefreshedAt: Instant,
)

object BigDecimalAsStringSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimalAsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder.encodeString(value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): BigDecimal =
        BigDecimal(decoder.decodeString())
}

object InstantAsEpochMillisSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("InstantAsEpochMillis", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.toEpochMilli())
    }

    override fun deserialize(decoder: Decoder): Instant =
        Instant.ofEpochMilli(decoder.decodeLong())
}

object LocalDateAsIsoSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDateAsIso", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}
