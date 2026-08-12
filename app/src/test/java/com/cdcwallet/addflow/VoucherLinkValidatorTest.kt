package com.cdcwallet.addflow

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
        // token as the slash-less form - not junk.
        assertTrue(validator.isPlausibleVoucherLink("https://voucher.redeem.gov.sg/ABC123/"))
    }

    @Test
    fun queryParamsAreAllowed() {
        assertTrue(validator.isPlausibleVoucherLink("https://voucher.redeem.gov.sg/ABC123?utm_source=sms"))
    }

    @Test
    fun httpSchemeRejected() {
        // Refactor H2: the confirmed production policy is HTTPS only. The link
        // is a bearer credential; an http URL could leak it or serve a
        // tampered page.
        assertFalse(validator.isPlausibleVoucherLink("http://voucher.redeem.gov.sg/ABC123"))
    }

    @Test
    fun schemeRelativeUrlRejected() {
        // "//host/token" has no scheme: it would resolve against whatever the
        // surrounding context is, which is not a self-contained voucher link.
        assertFalse(validator.isPlausibleVoucherLink("//voucher.redeem.gov.sg/ABC123"))
    }

    @Test
    fun userInfoRejected() {
        assertFalse(validator.isPlausibleVoucherLink("https://user@voucher.redeem.gov.sg/ABC123"))
    }

    @Test
    fun unexpectedPortRejected() {
        assertFalse(validator.isPlausibleVoucherLink("https://voucher.redeem.gov.sg:8443/ABC123"))
        // Even the "correct" port written explicitly is not the official shape.
        assertFalse(validator.isPlausibleVoucherLink("https://voucher.redeem.gov.sg:443/ABC123"))
    }

    @Test
    fun multiSegmentPathRejected() {
        // The official voucher path is exactly /{token}; deeper paths are not
        // voucher links and could smuggle a token that the stored URL then
        // disagrees with.
        assertFalse(validator.isPlausibleVoucherLink("https://voucher.redeem.gov.sg/a/b/ABC123"))
        assertFalse(validator.isPlausibleVoucherLink("https://voucher.redeem.gov.sg/groups/ABC123"))
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

    @Test
    fun customTestHostAcceptedThroughTheConstructorSeam() {
        // Test-only custom hosts must remain available through injected
        // validators, never through the production policy.
        val fixtureValidator = VoucherLinkValidator(allowedHost = "appassets.androidplatform.net")
        assertTrue(fixtureValidator.isPlausibleVoucherLink("https://appassets.androidplatform.net/TestToken1"))
        assertFalse(validator.isPlausibleVoucherLink("https://appassets.androidplatform.net/TestToken1"))
    }
}
