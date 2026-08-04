package com.cdcvouchers.extraction

import com.cdcvouchers.data.model.CategoryBalance
import com.cdcvouchers.data.model.ValidityStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Parses the whitelisted bridge payload into an ExtractionResult.Success.
 * Only the six whitelisted fields (spec 02 §2.6) exist in the DTOs below —
 * there is structurally nowhere for address or merchant data to land.
 */
internal object VoucherPayloadParser {

    private val json = Json { ignoreUnknownKeys = true }
    private val singaporeZone: ZoneId = ZoneId.of("Asia/Singapore")

    fun parse(payloadJson: String): ExtractionResult.Success? {
        val payload = runCatching {
            json.decodeFromString(WhitelistedPayload.serializer(), payloadJson)
        }.getOrNull() ?: return null

        val campaign = payload.campaign ?: return null
        val name = campaign.name?.takeIf { it.isNotBlank() } ?: return null
        val status = when (campaign.validity) {
            "campaign_valid" -> ValidityStatus.ACTIVE
            "campaign_not_started" -> ValidityStatus.NOT_STARTED
            "campaign_ended" -> ValidityStatus.EXPIRED
            else -> return null
        }

        val balances = payload.vouchers
            .filter { it.state == "unused" }
            .groupBy { voucher ->
                // Climate/undifferentiated vouchers carry no `type` — bucket
                // them under the campaign's first word (e.g. "Climate" from
                // "Climate Vouchers ($100)") so their value still counts
                // (spec 02 §2.2: `type` is nullable for non-CDC schemes).
                voucher.type?.takeIf { it.isNotBlank() } ?: name.substringBefore(' ').ifBlank { name }
            }
            .map { (category, vouchers) -> CategoryBalance(category, sumValues(vouchers)) }

        return ExtractionResult.Success(
            campaignName = name,
            validityStatus = status,
            expiryDate = parseExpiry(campaign.validity_end),
            categoryBalances = balances,
        )
    }

    private fun sumValues(vouchers: List<WhitelistedVoucher>): BigDecimal =
        vouchers.fold(BigDecimal.ZERO) { acc, v ->
            val value = v.voucher_value?.content
                ?.let { runCatching { BigDecimal(it) }.getOrNull() }
            if (value == null) acc else acc + value
        }

    private fun parseExpiry(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(value) }
            .recoverCatching { OffsetDateTime.parse(value).atZoneSameInstant(singaporeZone).toLocalDate() }
            .recoverCatching { Instant.parse(value).atZone(singaporeZone).toLocalDate() }
            .getOrNull()
    }

    @Serializable
    internal data class WhitelistedPayload(
        val campaign: WhitelistedCampaign? = null,
        val vouchers: List<WhitelistedVoucher> = emptyList(),
    )

    @Serializable
    internal data class WhitelistedCampaign(
        val name: String? = null,
        val validity: String? = null,
        val validity_end: String? = null,
    )

    @Serializable
    internal data class WhitelistedVoucher(
        val state: String? = null,
        val voucher_value: JsonPrimitive? = null,
        val type: String? = null,
    )
}
