package com.cdcwallet.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Display-time glossary localization: the single seeded category
 * (`supermarket`) must localize per language; everything else passes
 * through raw — unknown categories and all campaign names (the campaign
 * token map is empty until translations land).
 */
class CampaignGlossaryTest {

    @Test
    fun supermarketLocalizesInAllFourLanguages() {
        assertEquals("Supermarket", localizeCategory("Supermarket", AppLanguage.EN))
        assertEquals("超级市场", localizeCategory("Supermarket", AppLanguage.ZH))
        assertEquals("Pasar Raya", localizeCategory("Supermarket", AppLanguage.MS))
        assertEquals("சூப்பர்மார்க்கெட்", localizeCategory("Supermarket", AppLanguage.TA))
    }

    @Test
    fun categoryMatchingIsCaseInsensitive() {
        assertEquals("超级市场", localizeCategory("supermarket", AppLanguage.ZH))
        assertEquals("Pasar Raya", localizeCategory("SUPERMARKET", AppLanguage.MS))
        assertEquals("சூப்பர்மார்க்கெட்", localizeCategory("  Supermarket  ", AppLanguage.TA))
    }

    @Test
    fun unknownCategoriesFallBackToRaw() {
        // The raw string passes through unchanged (case, spacing, everything).
        assertEquals("Heartland", localizeCategory("Heartland", AppLanguage.ZH))
        assertEquals("climate", localizeCategory("climate", AppLanguage.MS))
        assertEquals("Community Development Council", localizeCategory("Community Development Council", AppLanguage.TA))
    }

    @Test
    fun campaignNamesPassThroughUntilTokensAreAdded() {
        // The campaign token map is deliberately empty — every name stays
        // raw in every language until translations are provided.
        assertEquals("Climate Vouchers ($100)", localizeCampaignName("Climate Vouchers ($100)", AppLanguage.ZH))
        assertEquals("SG60 Vouchers (Adults)", localizeCampaignName("SG60 Vouchers (Adults)", AppLanguage.MS))
        assertEquals("CDC Vouchers 2026 (June)", localizeCampaignName("CDC Vouchers 2026 (June)", AppLanguage.TA))
        assertEquals("CDC Vouchers 2026 (June)", localizeCampaignName("CDC Vouchers 2026 (June)", AppLanguage.EN))
    }

    // --- Longest-match-first tokenization (the phrase-vs-word rule) ---
    // Exercises the internal tokenizer with a synthetic glossary because the
    // production campaignTokens map is still empty. The scenario: both
    // "Climate Vouchers" (a phrase) and "Vouchers" (a single word) are terms.

    private val phrasePlusWordGlossary = mapOf(
        "climate vouchers" to Entry("Climate Vouchers", "气候券", "Baucar Iklim", "காலநிலை வவச்சர்கள்"),
        "vouchers" to Entry("Vouchers", "券", "Baucar", "வவச்சர்கள்"),
    )

    @Test
    fun phraseBeatsItsComponentWord() {
        // The full phrase "Climate Vouchers" matches as one token — the
        // shorter "Vouchers" key must NOT fire inside it.
        assertEquals(
            "气候券 ($100)",
            tokenizeWithGlossary("Climate Vouchers ($100)", AppLanguage.ZH, phrasePlusWordGlossary),
        )
        assertEquals(
            "Baucar Iklim ($100)",
            tokenizeWithGlossary("Climate Vouchers ($100)", AppLanguage.MS, phrasePlusWordGlossary),
        )
    }

    @Test
    fun componentWordStillMatchesWhenPhraseDoesNotApply() {
        // "Vouchers" alone (no "Climate" prefix) still translates via the
        // single-word key — the phrase key simply doesn't match here.
        assertEquals(
            "券",
            tokenizeWithGlossary("Vouchers", AppLanguage.ZH, phrasePlusWordGlossary),
        )
        assertEquals(
            "Baucar",
            tokenizeWithGlossary("Vouchers", AppLanguage.MS, phrasePlusWordGlossary),
        )
    }

    @Test
    fun phraseMatchingIsCaseInsensitive() {
        assertEquals(
            "气候券 ($100)",
            tokenizeWithGlossary("climate VOUCHERS ($100)", AppLanguage.ZH, phrasePlusWordGlossary),
        )
        assertEquals(
            "气候券 ($100)",
            tokenizeWithGlossary("CLIMATE VOUCHERS ($100)", AppLanguage.ZH, phrasePlusWordGlossary),
        )
    }

    @Test
    fun unmatchedPartsPassThroughRaw() {
        // The phrase matches and translates; the unmatched fragment stays
        // exactly as authored.
        assertEquals(
            "气候券 (Extra)",
            tokenizeWithGlossary("Climate Vouchers (Extra)", AppLanguage.ZH, phrasePlusWordGlossary),
        )
    }

    @Test
    fun nameWithNoKnownTokensPassesThroughEntirely() {
        // No glossary key matches any word — the whole name stays raw.
        assertEquals(
            "ABC Programme (Bonus)",
            tokenizeWithGlossary("ABC Programme (Bonus)", AppLanguage.ZH, phrasePlusWordGlossary),
        )
    }
}
