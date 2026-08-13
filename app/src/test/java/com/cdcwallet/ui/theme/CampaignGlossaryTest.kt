package com.cdcwallet.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Display-time glossary localization: seeded categories (supermarket,
 * heartland, climate) and campaign tokens (months, voucher terms, adults,
 * seniors) localize per language; unknown terms pass through raw. English
 * is the source language and is always returned unchanged.
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
    fun unknownCategoriesFallBackToDisplayCanonicalForm() {
        // Refactor M8: unknown raw strings pass through in their DISPLAY
        // canonical form - trimmed, first letter capitalized.
        assertEquals("Other", localizeCategory("Other", AppLanguage.ZH))
        assertEquals("Dining", localizeCategory("dining", AppLanguage.MS))
        assertEquals("Community Development Council", localizeCategory("Community Development Council", AppLanguage.TA))
        assertEquals("Dining", localizeCategory("  dining ", AppLanguage.EN))
    }

    @Test
    fun heartlandAndClimateLocalizeAsCategories() {
        assertEquals("邻里商店", localizeCategory("Heartland", AppLanguage.ZH))
        assertEquals("Kedai Kejiranan", localizeCategory("Heartland", AppLanguage.MS))
        assertEquals("அண்மைக் கடைகள்", localizeCategory("Heartland", AppLanguage.TA))
        assertEquals("气候", localizeCategory("Climate", AppLanguage.ZH))
        assertEquals("Iklim", localizeCategory("Climate", AppLanguage.MS))
        assertEquals("காலநிலை", localizeCategory("Climate", AppLanguage.TA))
    }

    @Test
    fun campaignNamesLocalizeKnownTermsAndKeepTheRestRaw() {
        // "Climate Vouchers" is a phrase key; "($100)" passes through.
        assertEquals("气候优惠券 ($100)", localizeCampaignName("Climate Vouchers ($100)", AppLanguage.ZH))
        // "Vouchers" translates, "SG60" and "(Adults)" is a key too:
        assertEquals("SG60 Baucar (Dewasa)", localizeCampaignName("SG60 Vouchers (Adults)", AppLanguage.MS))
        assertEquals("SG60 消费券 (成人)", localizeCampaignName("SG60 Vouchers (Adults)", AppLanguage.ZH))
    }

    @Test
    fun parenthesizedMonthTranslatesAndKeepsParens() {
        // "CDC Vouchers 2026 (June)" - "Vouchers" and the parenthesized
        // month both translate; the parens around the month are preserved.
        assertEquals(
            "CDC 消费券 2026 (六月)",
            localizeCampaignName("CDC Vouchers 2026 (June)", AppLanguage.ZH),
        )
        assertEquals(
            "CDC Baucar 2026 (Jun)",
            localizeCampaignName("CDC Vouchers 2026 (June)", AppLanguage.MS),
        )
        assertEquals(
            "CDC வவச்சர் 2026 (ஜூன்)",
            localizeCampaignName("CDC Vouchers 2026 (June)", AppLanguage.TA),
        )
        // English is the source language - the name is returned unchanged.
        assertEquals(
            "CDC Vouchers 2026 (June)",
            localizeCampaignName("CDC Vouchers 2026 (June)", AppLanguage.EN),
        )
    }

    @Test
    fun englishIsSourceLanguageAndStaysRaw() {
        // EN never rewrites anything, even with glossary matches available.
        assertEquals("Vouchers", localizeCampaignName("Vouchers", AppLanguage.EN))
        assertEquals("Climate Vouchers ($100)", localizeCampaignName("Climate Vouchers ($100)", AppLanguage.EN))
        assertEquals("Supermarket", localizeCategory("Supermarket", AppLanguage.EN))
    }

    @Test
    fun allTwelveMonthsTranslateInEveryLanguage() {
        val months = mapOf(
            "January" to listOf("一月", "Januari", "ஜனவரி"),
            "February" to listOf("二月", "Februari", "பிப்ரவரி"),
            "March" to listOf("三月", "Mac", "மார்ச்"),
            "April" to listOf("四月", "April", "ஏப்ரல்"),
            "May" to listOf("五月", "Mei", "மே"),
            "June" to listOf("六月", "Jun", "ஜூன்"),
            "July" to listOf("七月", "Julai", "ஜூலை"),
            "August" to listOf("八月", "Ogos", "ஆகஸ்ட்"),
            "September" to listOf("九月", "September", "செப்டம்பர்"),
            "October" to listOf("十月", "Oktober", "அக்டோபர்"),
            "November" to listOf("十一月", "November", "நவம்பர்"),
            "December" to listOf("十二月", "Disember", "டிசம்பர்"),
        )
        val languages = listOf(
            AppLanguage.ZH to 0,
            AppLanguage.MS to 1,
            AppLanguage.TA to 2,
        )
        months.forEach { (en, expected) ->
            languages.forEach { (language, index) ->
                assertEquals(
                    "month $en in $language",
                    "(${expected[index]})",
                    localizeCampaignName("($en)", language),
                )
            }
        }
    }

    @Test
    fun voucherSingularAndPluralMapToSameTranslation() {
        // Option A: both surface forms are explicit keys sharing the Entry.
        assertEquals("消费券", localizeCampaignName("Voucher", AppLanguage.ZH))
        assertEquals("消费券", localizeCampaignName("Vouchers", AppLanguage.ZH))
        assertEquals("Baucar", localizeCampaignName("Voucher", AppLanguage.MS))
        assertEquals("Baucar", localizeCampaignName("Vouchers", AppLanguage.MS))
        assertEquals("வவச்சர்", localizeCampaignName("Voucher", AppLanguage.TA))
        assertEquals("வவச்சர்", localizeCampaignName("Vouchers", AppLanguage.TA))
    }

    @Test
    fun climateVoucherPhraseBeatsVoucherWord() {
        // The phrase key "climate vouchers" wins over the component
        // "vouchers" key (longest-match-first).
        assertEquals(
            "气候优惠券",
            localizeCampaignName("Climate Vouchers", AppLanguage.ZH),
        )
        assertEquals(
            "Baucar Iklim",
            localizeCampaignName("Climate Vouchers", AppLanguage.MS),
        )
    }

    @Test
    fun adultsAndSeniorsTranslateInParens() {
        assertEquals("SG60 消费券 (成人)", localizeCampaignName("SG60 Vouchers (Adults)", AppLanguage.ZH))
        assertEquals("SG60 消费券 (乐龄人士)", localizeCampaignName("SG60 Vouchers (Seniors)", AppLanguage.ZH))
        assertEquals("SG60 Baucar (Warga Emas)", localizeCampaignName("SG60 Vouchers (Seniors)", AppLanguage.MS))
        assertEquals("SG60 வவச்சர் (மூத்தவர்கள்)", localizeCampaignName("SG60 Vouchers (Seniors)", AppLanguage.TA))
    }

    @Test
    fun adultAndSeniorSingularFormsMapToo() {
        // Option A: singular and plural are explicit keys sharing the Entry.
        assertEquals("成人", localizeCampaignName("Adult", AppLanguage.ZH))
        assertEquals("成人", localizeCampaignName("Adults", AppLanguage.ZH))
        assertEquals("Dewasa", localizeCampaignName("Adult", AppLanguage.MS))
        assertEquals("Dewasa", localizeCampaignName("Adults", AppLanguage.MS))
        assertEquals("பெரியவர்கள்", localizeCampaignName("Adult", AppLanguage.TA))
        assertEquals("பெரியவர்கள்", localizeCampaignName("Adults", AppLanguage.TA))
        assertEquals("乐龄人士", localizeCampaignName("Senior", AppLanguage.ZH))
        assertEquals("乐龄人士", localizeCampaignName("Seniors", AppLanguage.ZH))
        assertEquals("Warga Emas", localizeCampaignName("Senior", AppLanguage.MS))
        assertEquals("Warga Emas", localizeCampaignName("Seniors", AppLanguage.MS))
        assertEquals("மூத்தவர்கள்", localizeCampaignName("Senior", AppLanguage.TA))
        assertEquals("மூத்தவர்கள்", localizeCampaignName("Seniors", AppLanguage.TA))
    }

    // --- Longest-match-first tokenization (the phrase-vs-word rule) ---
    // Exercises the internal tokenizer with a synthetic glossary (the
    // production campaignTokens map holds only months). The scenario: both
    // "Climate Vouchers" (a phrase) and "Vouchers" (a single word) are terms.

    private val phrasePlusWordGlossary = mapOf(
        "climate vouchers" to Entry("Climate Vouchers", "气候券", "Baucar Iklim", "காலநிலை வவச்சர்கள்"),
        "vouchers" to Entry("Vouchers", "券", "Baucar", "வவச்சர்கள்"),
    )

    @Test
    fun phraseBeatsItsComponentWord() {
        // The full phrase "Climate Vouchers" matches as one token - the
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
        // single-word key - the phrase key simply doesn't match here.
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
        // No glossary key matches any word - the whole name stays raw.
        assertEquals(
            "ABC Programme (Bonus)",
            tokenizeWithGlossary("ABC Programme (Bonus)", AppLanguage.ZH, phrasePlusWordGlossary),
        )
    }
}
