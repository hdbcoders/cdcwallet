package com.hdbcoders.cdcwallet.extraction

import com.hdbcoders.cdcwallet.data.model.CategoryBalance
import com.hdbcoders.cdcwallet.data.model.ValidityStatus
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
 * Only the six whitelisted fields (spec 02 §2.6) exist in the DTOs below -
 * there is structurally nowhere for address or merchant data to land.
 *
 * Strictness (refactor H7): a malformed payload is a parse failure, never a
 * silently-partial success. Invalid values skipped in the past produced
 * successful results with undercounted balances - the worst failure mode for
 * a money tracker. Malformed required values, unknown voucher states,
 * negative amounts, and malformed nonblank dates now all fail the parse; the
 * caller then preserves the previous cache (refresh) or saves UNVERIFIED
 * (add).
 */
internal object VoucherPayloadParser {

    private val json = Json { ignoreUnknownKeys = true }
    private val singaporeZone: ZoneId = ZoneId.of("Asia/Singapore")

    /** The only voucher states the API defines (spec 02 §2.2). */
    private val knownVoucherStates = setOf("unused", "redeemed", "voided")

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

        // A nonblank expiry that does not parse means the payload is corrupt
        // (refactor H7) - silently treating it as "no expiry" would hide a
        // schema drift or a truncated response.
        val expiry = campaign.validity_end
            ?.takeIf { it.isNotBlank() }
            ?.let { parseExpiry(it) ?: return null }

        // Unknown voucher states mean the response shape moved under us:
        // ignoring them would undercount the balance. A missing state is
        // equally malformed - every voucher carries one (spec 02 §2.2).
        if (payload.vouchers.any { it.state !in knownVoucherStates }) return null

        val balances = payload.vouchers
            .filter { it.state == "unused" }
            .groupBy { voucher ->
                // Climate/undifferentiated vouchers carry no `type` - bucket
                // them under the campaign's first word (e.g. "Climate" from
                // "Climate Vouchers ($100)") so their value still counts
                // (spec 02 §2.2: `type` is nullable for non-CDC schemes).
                voucher.type?.takeIf { it.isNotBlank() } ?: name.substringBefore(' ').ifBlank { name }
            }
            .map { (category, vouchers) ->
                CategoryBalance(category, sumValues(vouchers) ?: return null)
            }

        return ExtractionResult.Success(
            campaignName = name,
            validityStatus = status,
            expiryDate = expiry,
            categoryBalances = balances,
        )
    }

    /**
     * Strict sum of unused-voucher values (refactor H7): a missing or
     * unparseable value, or a negative amount, is a malformed payload and
     * yields null - the caller turns it into a parse failure instead of
     * reporting an undercounted balance as success.
     */
    private fun sumValues(vouchers: List<WhitelistedVoucher>): BigDecimal? {
        var total = BigDecimal.ZERO
        for (voucher in vouchers) {
            val raw = voucher.voucher_value?.content ?: return null
            val value = runCatching { BigDecimal(raw) }.getOrNull() ?: return null
            if (value.signum() < 0) return null
            total += value
        }
        return total
    }

    private fun parseExpiry(value: String): LocalDate? =
        runCatching { LocalDate.parse(value) }
            .recoverCatching { OffsetDateTime.parse(value).atZoneSameInstant(singaporeZone).toLocalDate() }
            .recoverCatching { Instant.parse(value).atZone(singaporeZone).toLocalDate() }
            .getOrNull()

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
