package com.stoneofhope.stoneofhope.data.bible

/**
 * Book name / abbreviation → `bookOrd`. Ports iOS `BibleBook.matches(input:language:)`,
 * `BibleBook.aliases(for:)`, `BibleBook.allPossibleNames`, `BibleBookHelper.getBookOrdSmart` and
 * `BibleReferenceValidator.isBookNamePrefix`.
 */
object BibleBookMatcher {

    /** iOS `aliases(for:)`, keyed by the lowercased full name in that language. */
    private val aliases: Map<BibleLanguage, Map<String, List<String>>> = mapOf(
        BibleLanguage.English to mapOf("psalms" to listOf("psalm"), "proverbs" to listOf("proverb")),
        BibleLanguage.ChineseSimplified to mapOf("创世记" to listOf("创世纪")),
        BibleLanguage.ChineseTraditional to mapOf("創世記" to listOf("創世紀")),
    )

    /** iOS `BibleBook.matches`: case-insensitive full name, abbreviation or alias in one language. */
    fun matches(book: BibleBook, input: String, language: BibleLanguage): Boolean {
        val lowered = SwiftText.trim(input.lowercase())
        val fullName = book.fullName(language).lowercase()
        if (fullName == lowered) return true
        if (book.abbreviations(language).any { it.lowercase() == lowered }) return true
        return aliases.getValue(language)[fullName]?.contains(lowered) == true
    }

    /** iOS `allPossibleNames`: every name, abbreviation and alias in every language, lowercased. */
    fun allPossibleNames(book: BibleBook): Set<String> = namesByOrd.getValue(book.ord)

    private val namesByOrd: Map<Int, Set<String>> = BibleBooks.all.associate { book ->
        val names = BibleLanguage.entries.flatMap { lang ->
            listOf(book.fullName(lang)) + book.abbreviations(lang) +
                aliases.getValue(lang)[book.fullName(lang).lowercase()].orEmpty()
        }
        book.ord to names.map { SwiftText.trimWhitespaces(it.lowercase()) }.toSet()
    }

    /**
     * iOS `getBookOrdSmart(for:languages:)`: tries [languages] (the selected Bible versions' languages)
     * first, then always English, Simplified and Traditional Chinese, so "1p", "彼前" and "1 Pe" all
     * resolve whatever version is selected.
     */
    fun bookOrd(input: String, languages: List<BibleLanguage> = emptyList()): Int? {
        val trimmed = SwiftText.trim(input)
        val langs = languages.ifEmpty { listOf(BibleLanguage.current()) }.toMutableList()
        for (extra in listOf(BibleLanguage.English, BibleLanguage.ChineseSimplified, BibleLanguage.ChineseTraditional)) {
            if (extra !in langs) langs += extra
        }
        for (lang in langs) {
            BibleBooks.all.firstOrNull { matches(it, trimmed, lang) }?.let { return it.ord }
        }
        return null
    }

    /**
     * iOS `isBookNamePrefix`: `马` is not a book but starts 马太福音 and 马可福音, so a field holding it is
     * mid-word rather than wrong.
     */
    fun isBookNamePrefix(token: String): Boolean {
        val needle = SwiftText.trimWhitespaces(token.lowercase())
        if (needle.isEmpty()) return true
        return namesByOrd.values.any { names -> names.any { it.startsWith(needle) } }
    }
}
