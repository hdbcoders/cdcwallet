package com.cdcwallet.ui.theme

/**
 * Display-time localization for scraped names (campaign names and category
 * names). The real RedeemSG page is the source of truth: these raw strings
 * are stored as-is (whitelisted `campaign.name`, `voucher.type` per
 * `02 §2.6`) and are translated only when rendered, so glossary updates
 * re-localize existing rows for free without touching stored data.
 *
 * One [Entry] carries all four app languages, so a missing locale is a
 * compile error rather than a silent English fallback.
 */
data class Entry(val en: String, val zh: String, val ms: String, val ta: String) {
    fun forLanguage(language: AppLanguage): String = when (language) {
        AppLanguage.EN -> en
        AppLanguage.ZH -> zh
        AppLanguage.MS -> ms
        AppLanguage.TA -> ta
    }
}

/**
 * The glossary. `categories` is keyed by the lower-cased raw category name
 * (matching [categoryVisuals]); `campaignTokens` is keyed by the raw token
 * as it appears inside a campaign name. Both are deliberately extensible:
 * adding a word or category is one map entry per language.
 *
 * Seeded with a single category (`supermarket`) — the remaining categories
 * and all campaign tokens are added as their translations are provided.
 */
object CampaignGlossary {

    val categories: Map<String, Entry> = mapOf(
        "supermarket" to Entry("Supermarket", "超级市场", "Pasar Raya", "சூப்பர்மார்க்கெட்"),
    )

    val campaignTokens: Map<String, Entry> = emptyMap()
}

/**
 * Localizes a scraped category name for display. Case-insensitive exact
 * match against the glossary; unknown categories pass through raw (the
 * real page's name stays the source of truth).
 */
fun localizeCategory(raw: String, language: AppLanguage): String =
    CampaignGlossary.categories[raw.trim().lowercase()]?.forLanguage(language) ?: raw

/**
 * Localizes a scraped campaign name for display. Tokenizes the raw name,
 * mapping known tokens through the campaign glossary with
 * **longest-match-first** priority: at each position the longest glossary
 * key that matches (case-insensitively, whole-word) is consumed as one
 * token, so a phrase like "Climate Vouchers" beats its component word
 * "Vouchers"; shorter keys and unmatched words pass through raw.
 *
 * The campaign token map is currently empty, so this is a pass-through
 * until translations are added — the tokenizer is what future entries
 * plug into.
 */
fun localizeCampaignName(raw: String, language: AppLanguage): String =
    tokenizeWithGlossary(raw, language, CampaignGlossary.campaignTokens)

/**
 * Maximal-munch tokenizer: walks the whitespace-separated words and, at
 * each position, tries the longest [glossary] key first (whole-word,
 * case-insensitive). A matching key consumes its word count and emits the
 * translation; otherwise one raw word passes through. Phrase keys always
 * beat their component words because they are tried first.
 */
internal fun tokenizeWithGlossary(
    raw: String,
    language: AppLanguage,
    glossary: Map<String, Entry>,
): String {
    if (glossary.isEmpty()) return raw
    val words = raw.split(' ')
    val keys = glossary.keys.sortedByDescending { it.length }
    val out = mutableListOf<String>()
    var i = 0
    while (i < words.size) {
        val key = keys.firstOrNull { candidate ->
            val keyWords = candidate.split(' ')
            keyWords.size <= words.size - i &&
                keyWords.indices.all { j -> words[i + j].equals(keyWords[j], ignoreCase = true) }
        }
        if (key != null) {
            out += glossary.getValue(key).forLanguage(language)
            i += key.split(' ').size
        } else {
            out += words[i]
            i++
        }
    }
    return out.joinToString(" ")
}
