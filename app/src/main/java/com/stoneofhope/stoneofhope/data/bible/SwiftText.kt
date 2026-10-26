package com.stoneofhope.stoneofhope.data.bible

/**
 * Foundation string behaviour the Bible-reference code depends on, reproduced exactly so a reference
 * that parses on iOS parses the same way here (Kotlin's `trim()`, `toInt()` and regex shorthands differ
 * at the edges: NEL, full-width digits, NBSP).
 */
internal object SwiftText {

    /** `CharacterSet.whitespacesAndNewlines`: Unicode Z*, U+0009–U+000D, U+0085. */
    fun isWhitespaceOrNewline(c: Char): Boolean = c in '\u0009'..'\u000D' || c == '\u0085' || isSeparator(c)

    /** `CharacterSet.whitespaces`: Unicode Zs and U+0009. */
    fun isWhitespace(c: Char): Boolean = c == '\t' || Character.getType(c) == Character.SPACE_SEPARATOR.toInt()

    /** `CharacterSet.punctuationCharacters`: Unicode P*. */
    fun isPunctuation(c: Char): Boolean = when (Character.getType(c).toByte()) {
        Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
        Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION -> true
        else -> false
    }

    private fun isSeparator(c: Char): Boolean = when (Character.getType(c).toByte()) {
        Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true
        else -> false
    }

    /** `trimmingCharacters(in: .whitespacesAndNewlines)`. */
    fun trim(s: String): String = s.trim(::isWhitespaceOrNewline)

    /** `trimmingCharacters(in: .whitespaces)`. */
    fun trimWhitespaces(s: String): String = s.trim(::isWhitespace)

    /** `trimmingCharacters(in: .whitespacesAndNewlines.union(.punctuationCharacters))`. */
    fun trimWhitespaceAndPunctuation(s: String): String = s.trim { isWhitespaceOrNewline(it) || isPunctuation(it) }

    /** `Int(String)`: ASCII digits only (Kotlin's `toInt()` also accepts e.g. full-width digits). */
    fun asciiInt(s: String?): Int? {
        if (s.isNullOrEmpty() || s.length > 9 || s.any { it !in '0'..'9' }) return null
        return s.toInt()
    }

    /** `capitalizedFirstLetter()`: first character uppercased, the rest untouched. */
    fun capitalizedFirstLetter(s: String): String {
        if (s.isEmpty()) return s
        val firstLength = Character.charCount(s.codePointAt(0))
        return s.substring(0, firstLength).uppercase() + s.substring(firstLength)
    }

    // ICU (NSRegularExpression, and Android's own regex engine) gives `\d` `\s` `\w` Unicode meanings;
    // the JVM used by unit tests does not. These classes spell out the ICU meaning so both agree.

    /** ICU `\d`. */
    const val DIGIT = "\\p{Nd}"

    /** ICU `\s`: `[\t\n\f\r\p{Z}]`. */
    const val SPACE = "[\\t\\n\\f\\r\\p{Z}]"

    /** ICU `\w` (word character for `\b`). */
    const val WORD = "[\\p{L}\\p{M}\\p{Nd}\\p{Pc}\\u200C\\u200D]"
}
