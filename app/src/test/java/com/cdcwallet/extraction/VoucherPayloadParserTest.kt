package com.cdcwallet.extraction

import com.cdcwallet.data.model.ValidityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class VoucherPayloadParserTest {

    private fun payloadJson(
        validity: String = "campaign_valid",
        validityEnd: String = "2026-12-31T23:59:59+08:00",
        vouchers: String = """[
            {"id":"v1","state":"unused","voucher_value":50,"type":"heartland"},
            {"id":"v2","state":"unused","voucher_value":25.5,"type":"supermarket"}
        ]""",
    ) = """{"campaign":{"name":"CDC Vouchers 2026","validity":"$validity","validity_end":"$validityEnd"},"vouchers":$vouchers}"""

    @Test
    fun `confirmed shape parses to success`() {
        val result = VoucherPayloadParser.parse(payloadJson())
        assertEquals("CDC Vouchers 2026", result?.campaignName)
        assertEquals(ValidityStatus.ACTIVE, result?.validityStatus)
        assertEquals(LocalDate.of(2026, 12, 31), result?.expiryDate)
        assertEquals(
            listOf(
                com.cdcwallet.data.model.CategoryBalance("heartland", BigDecimal("50")),
                com.cdcwallet.data.model.CategoryBalance("supermarket", BigDecimal("25.5")),
            ),
            result?.categoryBalances,
        )
    }

    @Test
    fun `only unused vouchers count toward balances`() {
        val json = payloadJson(vouchers = """[
            {"id":"v1","state":"unused","voucher_value":50,"type":"heartland"},
            {"id":"v2","state":"redeemed","voucher_value":999,"type":"heartland"},
            {"id":"v3","state":"voided","voucher_value":888,"type":"supermarket"},
            {"id":"v4","state":"unused","voucher_value":10,"type":"supermarket"}
        ]""")
        val result = VoucherPayloadParser.parse(json)
        assertEquals(
            listOf(
                com.cdcwallet.data.model.CategoryBalance("heartland", BigDecimal("50")),
                com.cdcwallet.data.model.CategoryBalance("supermarket", BigDecimal("10")),
            ),
            result?.categoryBalances,
        )
    }

    @Test
    fun `unused values in same category are summed`() {
        val json = payloadJson(vouchers = """[
            {"id":"v1","state":"unused","voucher_value":10,"type":"heartland"},
            {"id":"v2","state":"unused","voucher_value":20,"type":"heartland"},
            {"id":"v3","state":"unused","voucher_value":5.5,"type":"heartland"}
        ]""")
        val result = VoucherPayloadParser.parse(json)
        assertEquals(
            listOf(com.cdcwallet.data.model.CategoryBalance("heartland", BigDecimal("35.5"))),
            result?.categoryBalances,
        )
    }

    @Test
    fun `not started and ended statuses map correctly`() {
        assertEquals(
            ValidityStatus.NOT_STARTED,
            VoucherPayloadParser.parse(payloadJson(validity = "campaign_not_started"))?.validityStatus,
        )
        assertEquals(
            ValidityStatus.EXPIRED,
            VoucherPayloadParser.parse(payloadJson(validity = "campaign_ended"))?.validityStatus,
        )
    }

    @Test
    fun `unknown validity is a parse failure`() {
        assertNull(VoucherPayloadParser.parse(payloadJson(validity = "campaign_something_else")))
    }

    @Test
    fun `missing campaign name is a parse failure`() {
        val json = """{"campaign":{"validity":"campaign_valid"},"vouchers":[]}"""
        assertNull(VoucherPayloadParser.parse(json))
    }

    @Test
    fun `missing campaign object is a parse failure`() {
        assertNull(VoucherPayloadParser.parse("""{"vouchers":[]}"""))
    }

    @Test
    fun `non-json input is a parse failure`() {
        assertNull(VoucherPayloadParser.parse("not json at all"))
    }

    @Test
    fun `empty vouchers is valid with empty balances`() {
        val result = VoucherPayloadParser.parse(payloadJson(vouchers = "[]"))
        assertEquals(emptyList<com.cdcwallet.data.model.CategoryBalance>(), result?.categoryBalances)
    }

    @Test
    fun `expiry date parses from plain date`() {
        val result = VoucherPayloadParser.parse(payloadJson(validityEnd = "2026-12-31"))
        assertEquals(LocalDate.of(2026, 12, 31), result?.expiryDate)
    }

    @Test
    fun `expiry date parses from zulu timestamp`() {
        val result = VoucherPayloadParser.parse(payloadJson(validityEnd = "2026-12-30T16:00:00Z"))
        assertEquals(LocalDate.of(2026, 12, 31), result?.expiryDate)
    }

    @Test
    fun `unparseable nonblank expiry is a parse failure`() {
        // Refactor H7: a nonblank validity_end that does not parse means the
        // payload is corrupt - silently reading it as "no expiry" would hide
        // schema drift behind a plausible-looking row.
        assertNull(VoucherPayloadParser.parse(payloadJson(validityEnd = "not-a-date")))
    }

    @Test
    fun `missing expiry yields null`() {
        val json = """{"campaign":{"name":"X","validity":"campaign_valid"},"vouchers":[]}"""
        assertNull(VoucherPayloadParser.parse(json)?.expiryDate)
    }

    @Test
    fun `string voucher values are accepted`() {
        val json = payloadJson(vouchers = """[
            {"id":"v1","state":"unused","voucher_value":"45.50","type":"heartland"}
        ]""")
        val result = VoucherPayloadParser.parse(json)
        assertEquals(
            listOf(com.cdcwallet.data.model.CategoryBalance("heartland", BigDecimal("45.50"))),
            result?.categoryBalances,
        )
    }

    @Test
    fun `blank type vouchers bucket under campaign name`() {
        val json = payloadJson(vouchers = """[
            {"id":"v1","state":"unused","voucher_value":10,"type":"heartland"},
            {"id":"v2","state":"unused","voucher_value":2},
            {"state":"unused","voucher_value":5}
        ]""")
        val result = VoucherPayloadParser.parse(json)
        // v1 keeps its type; v2 and v3 (blank type, values 2 and 5) fall back
        // to the campaign's first word ("CDC" from "CDC Vouchers 2026").
        assertEquals(
            listOf(
                com.cdcwallet.data.model.CategoryBalance("heartland", BigDecimal("10")),
                com.cdcwallet.data.model.CategoryBalance("CDC", BigDecimal("7")),
            ),
            result?.categoryBalances,
        )
    }

    @Test
    fun `unknown voucher state is a parse failure`() {
        // Refactor H7: a state outside unused/redeemed/voided means the
        // response shape moved under us - ignoring it would silently
        // undercount the balance.
        val json = payloadJson(vouchers = """[
            {"id":"v1","state":"unused","voucher_value":50,"type":"heartland"},
            {"id":"v2","state":"spent_elsewhere","voucher_value":20,"type":"heartland"}
        ]""")
        assertNull(VoucherPayloadParser.parse(json))
    }

    @Test
    fun `missing voucher state is a parse failure`() {
        val json = payloadJson(vouchers = """[
            {"id":"v1","state":"unused","voucher_value":50,"type":"heartland"},
            {"id":"v2","voucher_value":20,"type":"heartland"}
        ]""")
        assertNull(VoucherPayloadParser.parse(json))
    }

    @Test
    fun `unparseable voucher value is a parse failure`() {
        val json = payloadJson(vouchers = """[
            {"id":"v1","state":"unused","voucher_value":"$45","type":"heartland"}
        ]""")
        assertNull(VoucherPayloadParser.parse(json))
    }

    @Test
    fun `missing voucher value on unused voucher is a parse failure`() {
        val json = payloadJson(vouchers = """[
            {"id":"v1","state":"unused","type":"heartland"}
        ]""")
        assertNull(VoucherPayloadParser.parse(json))
    }

    @Test
    fun `negative voucher value is a parse failure`() {
        val json = payloadJson(vouchers = """[
            {"id":"v1","state":"unused","voucher_value":-5,"type":"heartland"}
        ]""")
        assertNull(VoucherPayloadParser.parse(json))
    }
}
