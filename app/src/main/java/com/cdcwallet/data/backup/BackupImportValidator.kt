package com.cdcwallet.data.backup

import com.cdcwallet.addflow.VoucherLinkValidator
import com.cdcwallet.data.model.VoucherBackupPayload
import com.cdcwallet.data.model.VoucherGroup
import com.cdcwallet.data.token.VoucherToken

/** Thrown when a decrypted backup payload contains invalid rows (refactor H4).
 *  Distinct from [BackupException]: the file opened and decrypted fine, but
 *  its contents must not be imported. */
class InvalidBackupPayloadException(message: String) : Exception(message)

/**
 * Validates every row of a decrypted backup payload BEFORE any database
 * mutation (refactor H4). Backups were previously imported on password + JSON
 * shape alone, so a crafted file could smuggle a non-RedeemSG URL into a row
 * (later opened in the WebView) or a `token` disagreeing with the stored URL
 * (opening one voucher while identifying as another), or an `id` that corrupts
 * the detail navigation route.
 *
 * A payload is all-or-nothing: any invalid row rejects the whole payload, so
 * replace-mode never deletes existing rows for a backup that would not import.
 *
 * The URL policy is the SAME strict policy as the add flow
 * ([VoucherLinkValidator] - https, exact host, official path shape), and the
 * token must equal `VoucherToken.tokenFromUrl(url)` case-sensitively, so the
 * row's identity and the page it opens can never disagree. Synthetic test
 * hosts go through the validator seam, never through the production policy.
 */
class BackupImportValidator(
    private val linkValidator: VoucherLinkValidator = VoucherLinkValidator(),
) {

    fun validate(payload: VoucherBackupPayload): List<VoucherGroup> {
        val ids = HashSet<String>()
        val tokens = HashSet<String>()
        for (row in payload.vouchers) {
            // ID: the detail screen interpolates it into the navigation route
            // ("detail/{id}"), so it must be a short, route-safe token.
            if (!ID_PATTERN.matches(row.id)) {
                throw InvalidBackupPayloadException("invalid voucher id")
            }
            if (!ids.add(row.id)) {
                throw InvalidBackupPayloadException("duplicate voucher id ${row.id}")
            }
            if (row.token.isBlank() || row.token.length > TOKEN_MAX_LENGTH) {
                throw InvalidBackupPayloadException("invalid voucher token")
            }
            if (!tokens.add(row.token)) {
                throw InvalidBackupPayloadException("duplicate voucher token ${row.token}")
            }
            if (row.url.length > URL_MAX_LENGTH) {
                throw InvalidBackupPayloadException("voucher url too long")
            }
            // The strict add-flow policy: https, exact host, official path shape.
            if (!linkValidator.validate(row.url).isValid) {
                throw InvalidBackupPayloadException("invalid voucher url")
            }
            // Identity consistency: the row's token must be the token of its
            // own URL, case-sensitively (REQ-7) - never a second token source.
            if (row.token != VoucherToken.tokenFromUrl(row.url)) {
                throw InvalidBackupPayloadException("token does not match url")
            }
            if (row.campaignName.length > CAMPAIGN_NAME_MAX_LENGTH) {
                throw InvalidBackupPayloadException("campaign name too long")
            }
            if ((row.lastRefreshError?.length ?: 0) > LAST_REFRESH_ERROR_MAX_LENGTH) {
                throw InvalidBackupPayloadException("refresh error too long")
            }
            if (row.categoryBalances.size > CATEGORY_BALANCES_MAX) {
                throw InvalidBackupPayloadException("too many category balances")
            }
            for (balance in row.categoryBalances) {
                if (balance.category.isBlank() || balance.category.length > CATEGORY_NAME_MAX_LENGTH) {
                    throw InvalidBackupPayloadException("invalid category name")
                }
                if (balance.remainingValue.signum() < 0) {
                    throw InvalidBackupPayloadException("negative balance")
                }
            }
            // validityStatus and the dates are structurally validated during
            // JSON decoding (kotlinx.serialization rejects unknown enum values
            // and unparseable dates before this point), so a payload that
            // reaches here already carries only real statuses and dates.
        }
        return payload.vouchers
    }

    private companion object {
        /** Route-safe ids: the detail route interpolates the id unencoded. */
        val ID_PATTERN: Regex = Regex("^[A-Za-z0-9._-]{1,64}$")

        const val TOKEN_MAX_LENGTH = 256
        const val URL_MAX_LENGTH = 4096
        const val CAMPAIGN_NAME_MAX_LENGTH = 300
        const val LAST_REFRESH_ERROR_MAX_LENGTH = 200
        const val CATEGORY_NAME_MAX_LENGTH = 200
        const val CATEGORY_BALANCES_MAX = 100
    }
}
