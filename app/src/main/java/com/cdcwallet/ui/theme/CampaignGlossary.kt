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
 * Seeded with a single category (`supermarket`) and the twelve calendar
 * months as campaign tokens — the remaining categories and campaign tokens
 * are added as their translations are provided.
 */
object CampaignGlossary {

    val categories: Map<String, Entry> = mapOf(
        "supermarket" to Entry("Supermarket", "超级市场", "Pasar Raya", "சூப்பர்மார்க்கெட்"),
        "heartland" to Entry("Heartland", "邻里商店", "Kedai Kejiranan", "அண்மைக் கடைகள்"),
        "climate" to Entry("Climate", "气候", "Iklim", "காலநிலை"),
    )

    /**
     * The twelve calendar months — campaign names carry them parenthesized
     * (e.g. "CDC Vouchers 2026 (June)"), which the tokenizer matches
     * inside the parens and re-wraps on output. Keys are the English
     * month names, lower-case (matching is case-insensitive).
     *
     * Voucher terms are keyed in both singular and plural (option A: each
     * surface form is an explicit key pointing at the same [Entry]), so
     * "Voucher" and "Vouchers" both localize to the same word, and the
     * "Climate voucher(s)" phrase beats its component words via
     * longest-match.
     */
    private val voucher = Entry("Voucher", "消费券", "Baucar", "வவச்சர்")
    private val climateVoucher = Entry("Climate voucher", "气候优惠券", "Baucar Iklim", "காலநிலை வவச்சர்")

    val campaignTokens: Map<String, Entry> = mapOf(
        "january" to Entry("January", "一月", "Januari", "ஜனவரி"),
        "february" to Entry("February", "二月", "Februari", "பிப்ரவரி"),
        "march" to Entry("March", "三月", "Mac", "மார்ச்"),
        "april" to Entry("April", "四月", "April", "ஏப்ரல்"),
        "may" to Entry("May", "五月", "Mei", "மே"),
        "june" to Entry("June", "六月", "Jun", "ஜூன்"),
        "july" to Entry("July", "七月", "Julai", "ஜூலை"),
        "august" to Entry("August", "八月", "Ogos", "ஆகஸ்ட்"),
        "september" to Entry("September", "九月", "September", "செப்டம்பர்"),
        "october" to Entry("October", "十月", "Oktober", "அக்டோபர்"),
        "november" to Entry("November", "十一月", "November", "நவம்பர்"),
        "december" to Entry("December", "十二月", "Disember", "டிசம்பர்"),
        "voucher" to voucher,
        "vouchers" to voucher,
        "climate voucher" to climateVoucher,
        "climate vouchers" to climateVoucher,
        "adult" to Entry("Adult", "成人", "Dewasa", "பெரியவர்கள்"),
        "adults" to Entry("Adults", "成人", "Dewasa", "பெரியவர்கள்"),
        "senior" to Entry("Senior", "乐龄人士", "Warga Emas", "மூத்தவர்கள்"),
        "seniors" to Entry("Seniors", "乐龄人士", "Warga Emas", "மூத்தவர்கள்"),
    )
}

/**
 * Localizes a scraped category name for display. Case-insensitive exact
 * match against the glossary; unknown categories pass through raw (the
 * real page's name stays the source of truth). English is the source
 * language — it is returned unchanged.
 */
fun localizeCategory(raw: String, language: AppLanguage): String =
    if (language == AppLanguage.EN) raw
    else CampaignGlossary.categories[raw.trim().lowercase()]?.forLanguage(language) ?: raw

/**
 * Localizes a scraped campaign name for display. Tokenizes the raw name,
 * mapping known tokens through the campaign glossary with
 * **longest-match-first** priority: at each position the longest glossary
 * key that matches (case-insensitively, whole-word, paren-aware) is
 * consumed as one token, so a phrase like "Climate Vouchers" beats its
 * component word "Vouchers"; shorter keys and unmatched words pass
 * through raw.
 *
 * The campaign token map currently holds the twelve months; the remaining
 * campaign tokens are added as their translations are provided.
 */
fun localizeCampaignName(raw: String, language: AppLanguage): String =
    if (language == AppLanguage.EN) raw
    else tokenizeWithGlossary(raw, language, CampaignGlossary.campaignTokens)

/**
 * Maximal-munch tokenizer: walks the whitespace-separated words and, at
 * each position, tries the longest [glossary] key first (whole-word,
 * case-insensitive). A matching key consumes its word count and emits the
 * translation; otherwise one raw word passes through. Phrase keys always
 * beat their component words because they are tried first.
 *
 * Matching is paren-aware: a word wrapped in parentheses (e.g. "(June)")
 * is matched against the bare key ("june") and the translation is
 * re-wrapped in the same parens on output, so campaign names like
 * "CDC Vouchers 2026 (June)" localize the month without losing the
 * parentheses.
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
                keyWords.indices.all { j -> words[i + j].trimParens().equals(keyWords[j], ignoreCase = true) }
        }
        if (key != null) {
            val keyWordCount = key.split(' ').size
            // If the matched span's first word was parenthesized, wrap the
            // translation so "(June)" stays "(六月)".
            val wrapped = words[i].isParenthesized()
            val translation = glossary.getValue(key).forLanguage(language)
            out += if (wrapped && keyWordCount == 1) "($translation)" else translation
            i += keyWordCount
        } else {
            out += words[i]
            i++
        }
    }
    return out.joinToString(" ")
}

/** Strips enclosing parentheses from a word for matching: "(June)" → "June". */
private fun String.trimParens(): String {
    var s = this
    while (s.startsWith("(") && s.endsWith(")") && s.length >= 2) {
        s = s.substring(1, s.length - 1)
    }
    return s
}

/** True when the word is fully enclosed in parentheses, e.g. "(June)". */
private fun String.isParenthesized(): Boolean = length >= 2 && startsWith("(") && endsWith(")")
