package com.hdbcoders.cdcwallet.data.token

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherTokenTest {

    @Test
    fun `tokens differing only in case are distinct`() {
        assertFalse(VoucherToken.isDuplicate("AbC123xyz", "abc123XYZ"))
    }

    @Test
    fun `identical tokens are duplicates`() {
        assertTrue(VoucherToken.isDuplicate("AbC123xyz", "AbC123xyz"))
    }

    @Test
    fun `normalize strips query params`() {
        assertEquals(
            "https://voucher.redeem.gov.sg/AbC123",
            VoucherToken.normalize("https://voucher.redeem.gov.sg/AbC123?lang=en-US"),
        )
    }

    @Test
    fun `normalize strips fragment too`() {
        assertEquals(
            "https://voucher.redeem.gov.sg/AbC123",
            VoucherToken.normalize("https://voucher.redeem.gov.sg/AbC123#section"),
        )
    }

    @Test
    fun `token extracted from url path`() {
        assertEquals(
            "AbC123",
            VoucherToken.tokenFromUrl("https://voucher.redeem.gov.sg/AbC123?lang=en-US"),
        )
    }

    @Test
    fun `token with trailing slash`() {
        assertEquals(
            "AbC123",
            VoucherToken.tokenFromUrl("https://voucher.redeem.gov.sg/AbC123/"),
        )
    }

    @Test
    fun `invalid url yields null token`() {
        assertNull(VoucherToken.tokenFromUrl("not a url at all"))
    }

    @Test
    fun `url with no path segment yields null token`() {
        assertNull(VoucherToken.tokenFromUrl("https://voucher.redeem.gov.sg/"))
    }

    @Test
    fun `case preserved through normalization`() {
        val normalized = VoucherToken.normalize("https://voucher.redeem.gov.sg/TokenAB1")
        assertTrue(normalized.endsWith("TokenAB1"))
        assertFalse(normalized.lowercase().endsWith("TokenAB1"))
    }
}
