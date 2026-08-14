package com.hdbcoders.cdcwallet.extraction

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InjectionScriptTest {

    @Test
    fun captureScriptSubstitutesEveryPlaceholder() {
        val script = captureScriptFor(
            expectedToken = "TestToken1",
            bridgeName = "RedeemBridge7",
            nonce = "nonce-1",
            targetApiHost = "api-cdc.redeem.gov.sg",
            allowedPageOrigin = "https://voucher.redeem.gov.sg",
        )

        // No template placeholder may survive into the installed script.
        // (The wrapper's own `__redeemRequestUrl` JS identifier legitimately
        // contains double underscores - check the substitution markers only.)
        listOf(
            "__ALLOWED_PAGE_ORIGIN__",
            "__EXPECTED_HOST__",
            "__EXPECTED_TOKEN__",
            "__BRIDGE_NAME__",
            "__NONCE__",
        ).forEach { placeholder -> assertFalse(script.contains(placeholder)) }
        assertTrue(script.contains("'TestToken1'"))
        assertTrue(script.contains("'RedeemBridge7'"))
        assertTrue(script.contains("'nonce-1'"))
        assertTrue(script.contains("'api-cdc.redeem.gov.sg'"))
        assertTrue(script.contains("'https://voucher.redeem.gov.sg'"))
    }

    @Test
    fun valuesAreEscapedAsJsStringLiterals() {
        // A token containing a quote must not break out of the JS string
        // literal: it is embedded escaped, so the wrapper stays syntactically
        // valid no matter what the token looks like.
        val script = captureScriptFor(
            expectedToken = "TOK'EN",
            bridgeName = "RedeemBridge1",
            nonce = "n",
            targetApiHost = "api-cdc.redeem.gov.sg",
            allowedPageOrigin = "https://voucher.redeem.gov.sg",
        )
        assertTrue(script.contains("'TOK\\'EN'"))
    }

    @Test
    fun backslashesAreEscapedAsJsStringLiterals() {
        val script = captureScriptFor(
            expectedToken = "TOK\\EN",
            bridgeName = "RedeemBridge1",
            nonce = "n",
            targetApiHost = "api-cdc.redeem.gov.sg",
            allowedPageOrigin = "https://voucher.redeem.gov.sg",
        )
        assertTrue(script.contains("'TOK\\\\EN'"))
    }
}
