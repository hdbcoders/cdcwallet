package com.cdcwallet.addflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherLinkValidatorTest {

    private val validator = VoucherLinkValidator()

    @Test
    fun validRedeemSgLinkAccepted() {
        val result = validator.validate("https://voucher.redeem.gov.sg/ABC123")
        assertTrue(result.isValid)
        assertEquals("ABC123", result.token)
    }

    @Test
    fun trailingSlashStillYieldsATokenSegment() {
        // The canonical tokenizer trims slashes, so this normalizes to the same
        // token as the slash-less form - not junk.
        val result = validator.validate("https://voucher.redeem.gov.sg/ABC123/")
        assertTrue(result.isValid)
        assertEquals("ABC123", result.token)
    }

    @Test
    fun queryParamsAreAllowed() {
        val result = validator.validate("https://voucher.redeem.gov.sg/ABC123?utm_source=sms")
        assertTrue(result.isValid)
        assertEquals("ABC123", result.token)
    }

    @Test
    fun httpSchemeRejected() {
        // Refactor H2: the confirmed production policy is HTTPS only. The link
        // is a bearer credential; an http URL could leak it or serve a
        // tampered page.
        assertFalse(validator.validate("http://voucher.redeem.gov.sg/ABC123").isValid)
    }

    @Test
    fun schemeRelativeUrlRejected() {
        // "//host/token" has no scheme: it would resolve against whatever the
        // surrounding context is, which is not a self-contained voucher link.
        assertFalse(validator.validate("//voucher.redeem.gov.sg/ABC123").isValid)
    }

    @Test
    fun userInfoRejected() {
        assertFalse(validator.validate("https://user@voucher.redeem.gov.sg/ABC123").isValid)
    }

    @Test
    fun unexpectedPortRejected() {
        assertFalse(validator.validate("https://voucher.redeem.gov.sg:8443/ABC123").isValid)
        // Even the "correct" port written explicitly is not the official shape.
        assertFalse(validator.validate("https://voucher.redeem.gov.sg:443/ABC123").isValid)
    }

    @Test
    fun multiSegmentPathRejected() {
        // The official voucher path is exactly /{token}; deeper paths are not
        // voucher links and could smuggle a token that the stored URL then
        // disagrees with.
        assertFalse(validator.validate("https://voucher.redeem.gov.sg/a/b/ABC123").isValid)
        assertFalse(validator.validate("https://voucher.redeem.gov.sg/groups/ABC123").isValid)
    }

    @Test
    fun hostIsMatchedCaseInsensitively() {
        assertTrue(validator.validate("https://VOUCHER.REDEEM.GOV.SG/ABC123").isValid)
    }

    @Test
    fun wrongHostRejected() {
        assertFalse(validator.validate("https://evil.example.com/ABC123").isValid)
        assertFalse(validator.validate("https://voucher.redeem.gov.sg.evil.com/ABC123").isValid)
        assertFalse(validator.validate("https://api-cdc.redeem.gov.sg/ABC123").isValid)
    }

    @Test
    fun junkInputRejected() {
        assertFalse(validator.validate("").isValid)
        assertFalse(validator.validate("   ").isValid)
        assertFalse(validator.validate("hello world").isValid)
        assertFalse(validator.validate("12345").isValid)
        assertFalse(validator.validate("https://voucher.redeem.gov.sg").isValid)
        assertFalse(validator.validate("https://voucher.redeem.gov.sg/").isValid)
    }

    @Test
    fun unsupportedSchemeRejected() {
        assertFalse(validator.validate("ftp://voucher.redeem.gov.sg/ABC123").isValid)
        assertFalse(validator.validate("javascript:voucher.redeem.gov.sg/ABC123").isValid)
    }

    @Test
    fun whitespaceSurroundingInputIsIgnored() {
        val result = validator.validate("  https://voucher.redeem.gov.sg/ABC123  ")
        assertTrue(result.isValid)
        assertEquals("ABC123", result.token)
    }

    @Test
    fun queryParamsAndFragmentAreStrippedByCanonicalTokenFunction() {
        // Refactor L10: the fragment is stripped by the canonical token
        // function (01 §1.4) - a URL differing only in its fragment is the
        // same voucher.
        val result = validator.validate("https://voucher.redeem.gov.sg/TokenABC?a=1#frag")
        assertTrue(result.isValid)
        assertEquals("TokenABC", result.token)
    }

    @Test
    fun trailingSlashUrlIsValidViaCanonicalTokenFunction() {
        assertTrue(validator.validate("https://voucher.redeem.gov.sg/TokenABC/").isValid)
    }

    @Test
    fun hostCaseVariantsAcceptedButWrongHostAndEmptySegmentRejected() {
        assertTrue(validator.validate("https://Voucher.Redeem.Gov.Sg/TokenABC").isValid)
        assertFalse(validator.validate("https://wronghost.redeem.gov.sg/TokenABC").isValid)
        assertFalse(validator.validate("https://voucher.redeem.gov.sg/").isValid)
    }

    @Test
    fun customTestHostAcceptedThroughTheConstructorSeam() {
        // Test-only custom hosts must remain available through injected
        // validators, never through the production policy.
        val fixtureValidator = VoucherLinkValidator(allowedHost = "appassets.androidplatform.net")
        assertTrue(fixtureValidator.validate("https://appassets.androidplatform.net/TestToken1").isValid)
        assertFalse(validator.validate("https://appassets.androidplatform.net/TestToken1").isValid)
    }

    @Test
    fun rejectedInputsCarryNoToken() {
        assertNull(validator.validate("https://voucher.redeem.gov.sg/").token)
        assertNull(validator.validate("https://evil.example.com/ABC123").token)
        assertNull(validator.validate("").token)
    }
}
