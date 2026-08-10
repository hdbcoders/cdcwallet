package com.cdcwallet.addflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherLinkValidatorTest {

    private val validator = VoucherLinkValidator()

    @Test
    fun validRedeemSgLinkAccepted() {
        assertTrue(validator.isPlausibleVoucherLink("https://voucher.redeem.gov.sg/ABC123"))
    }

    @Test
    fun trailingSlashStillYieldsATokenSegment() {
        // The canonical tokenizer trims slashes, so this normalizes to the same
        // token as the slash-less form — not junk.
        assertTrue(validator.isPlausibleVoucherLink("https://voucher.redeem.gov.sg/ABC123/"))
    }

    @Test
    fun queryParamsAreAllowed() {
        assertTrue(validator.isPlausibleVoucherLink("https://voucher.redeem.gov.sg/ABC123?utm_source=sms"))
    }

    @Test
    fun httpSchemeTolerated() {
        assertTrue(validator.isPlausibleVoucherLink("http://voucher.redeem.gov.sg/ABC123"))
    }

    @Test
    fun hostIsMatchedCaseInsensitively() {
        assertTrue(validator.isPlausibleVoucherLink("https://VOUCHER.REDEEM.GOV.SG/ABC123"))
    }

    @Test
    fun wrongHostRejected() {
        assertFalse(validator.isPlausibleVoucherLink("https://evil.example.com/ABC123"))
        assertFalse(validator.isPlausibleVoucherLink("https://voucher.redeem.gov.sg.evil.com/ABC123"))
        assertFalse(validator.isPlausibleVoucherLink("https://api-cdc.redeem.gov.sg/ABC123"))
    }

    @Test
    fun junkInputRejected() {
        assertFalse(validator.isPlausibleVoucherLink(""))
        assertFalse(validator.isPlausibleVoucherLink("   "))
        assertFalse(validator.isPlausibleVoucherLink("hello world"))
        assertFalse(validator.isPlausibleVoucherLink("12345"))
        assertFalse(validator.isPlausibleVoucherLink("https://voucher.redeem.gov.sg"))
        assertFalse(validator.isPlausibleVoucherLink("https://voucher.redeem.gov.sg/"))
    }

    @Test
    fun unsupportedSchemeRejected() {
        assertFalse(validator.isPlausibleVoucherLink("ftp://voucher.redeem.gov.sg/ABC123"))
        assertFalse(validator.isPlausibleVoucherLink("javascript:voucher.redeem.gov.sg/ABC123"))
    }

    @Test
    fun whitespaceSurroundingInputIsIgnored() {
        assertTrue(validator.isPlausibleVoucherLink("  https://voucher.redeem.gov.sg/ABC123  "))
    }

    @Test
    fun queryParamsAndFragmentAreStrippedByCanonicalTokenFunction() {
        assertTrue(
            validator.isPlausibleVoucherLink("https://voucher.redeem.gov.sg/TokenABC?a=1#frag"),
        )
    }

    @Test
    fun trailingSlashUrlIsValidViaCanonicalTokenFunction() {
        assertTrue(validator.isPlausibleVoucherLink("https://voucher.redeem.gov.sg/TokenABC/"))
    }

    @Test
    fun hostCaseVariantsAcceptedButWrongHostAndEmptySegmentRejected() {
        assertTrue(validator.isPlausibleVoucherLink("https://Voucher.Redeem.Gov.Sg/TokenABC"))
        assertFalse(validator.isPlausibleVoucherLink("https://wronghost.redeem.gov.sg/TokenABC"))
        assertFalse(validator.isPlausibleVoucherLink("https://voucher.redeem.gov.sg/"))
    }
}
