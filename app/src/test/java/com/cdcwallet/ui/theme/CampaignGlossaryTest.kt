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
}
