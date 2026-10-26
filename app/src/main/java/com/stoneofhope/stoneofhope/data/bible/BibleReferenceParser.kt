package com.stoneofhope.stoneofhope.data.bible

import com.stoneofhope.stoneofhope.data.bible.SwiftText.DIGIT
import com.stoneofhope.stoneofhope.data.bible.SwiftText.SPACE
import com.stoneofhope.stoneofhope.data.bible.SwiftText.WORD

/** iOS `parseFlexible` result. */
data class ParsedBibleRef(val bookOrd: Int, val chapter: Int, val startVerse: Int?, val endVerse: Int?)

/**
 * iOS `BibleReferenceHelper`: the stricter parser behind the note-body shortcut ("1Th 4:13 grateful"),
 * not the reference field (that is [BibleReferenceValidator]). Only a full `chapter:verse` parses.
 */
object BibleReferenceParser {

    /** iOS regex `\d+:\d+(-\d+)?`: the first chapter:verse[-verse] anywhere in the text. */
    internal val chapterVersePattern = Regex("$DIGIT+:$DIGIT+(?:-$DIGIT+)?")

    /** iOS `parseFlexible`: "Book c:v[-v]" (book before the numbers) or "c:v[-v] Book". */
    fun parseFlexible(content: String, languages: List<BibleLanguage> = emptyList()): ParsedBibleRef? {
        val trimmed = SwiftText.trim(content)
        val match = chapterVersePattern.find(trimmed) ?: return null
        return parse(match.value, trimmed.substring(0, match.range.first), languages)
            ?: parse(match.value, trimmed.substring(match.range.last + 1), languages)
    }

    private fun parse(chapterVerse: String, bookPart: String, languages: List<BibleLanguage>): ParsedBibleRef? {
        val parts = chapterVerse.split(":")
        if (parts.size != 2) return null
        val chapter = SwiftText.asciiInt(parts[0]) ?: return null
        val verseParts = parts[1].split("-")
        val start = SwiftText.asciiInt(verseParts[0]) ?: return null
        val end = if (verseParts.size > 1) SwiftText.asciiInt(verseParts[1]) else null
        val bookOrd = BibleBookMatcher.bookOrd(normalizeBookNameToken(bookPart), languages) ?: return null
        return ParsedBibleRef(bookOrd, chapter, start, end)
    }

    private val trailingChapterPattern = Regex("$SPACE+$DIGIT+(?::$DIGIT+(?:-$DIGIT+)?)?$")

    /** iOS `parseChaptersFlexible`: "Book c" or "Book c:v[-v]" (sermons). */
    fun parseChaptersFlexible(content: String, languages: List<BibleLanguage> = emptyList()): ParsedBibleRef? {
        val trimmed = SwiftText.trim(content)
        val match = trailingChapterPattern.find(trimmed) ?: return null
        val bookOrd = BibleBookMatcher.bookOrd(normalizeBookNameToken(trimmed.substring(0, match.range.first)), languages)
            ?: return null
        val parts = SwiftText.trimWhitespaces(match.value).split(":")
        val chapter = SwiftText.asciiInt(parts[0]) ?: return null
        var start: Int? = null
        var end: Int? = null
        if (parts.size > 1) {
            val verseParts = parts[1].split("-")
            start = SwiftText.asciiInt(verseParts[0])
            if (verseParts.size > 1) end = SwiftText.asciiInt(verseParts[1])
        }
        return ParsedBibleRef(bookOrd, chapter, start, end)
    }

    /** iOS `isWithinCanon`: the chapter and every verse of `min...max` exist in that book. */
    fun isWithinCanon(bookOrd: Int, chapter: Int, startVerse: Int, endVerse: Int?): Boolean {
        val book = BibleBooks.book(bookOrd) ?: return false
        if (!book.isValidChapter(chapter)) return false
        val lo = minOf(startVerse, endVerse ?: startVerse)
        val hi = maxOf(startVerse, endVerse ?: startVerse)
        return (lo..hi).all { book.isValidVerse(it, chapter) }
    }

    /** Trims whitespace and trailing periods so "Matt." / "1 Pet." still resolve. */
    private fun normalizeBookNameToken(raw: String): String = SwiftText.trim(SwiftText.trim(raw).trimEnd('.'))
}

/** iOS `detectSermonVerseShortcut` result: "v16" / "v3-5" plus the surrounding text. */
data class VerseShortcut(val start: Int, val end: Int, val remainder: String?)

object VerseShortcutDetector {

    // iOS `(?i)\bv\s*(\d+)(?:\s*-\s*(\d+))?\b`, with ICU's \b spelled out as look-arounds.
    private val pattern = Regex("(?<!$WORD)[vV]$SPACE*($DIGIT+)(?:$SPACE*-$SPACE*($DIGIT+))?(?!$WORD)")

    /** iOS `detectSermonVerseShortcut(in:)`: the first `vN[-M]`; the text around it, first letter capitalized. */
    fun detect(content: String): VerseShortcut? {
        val match = pattern.find(content) ?: return null
        val start = SwiftText.asciiInt(match.groups[1]?.value) ?: return null
        val end = match.groups[2]?.value?.let(SwiftText::asciiInt) ?: start
        val prefix = SwiftText.trim(content.substring(0, match.range.first))
        val suffix = SwiftText.trim(content.substring(match.range.last + 1))
        val extra = SwiftText.capitalizedFirstLetter(SwiftText.trim("$prefix $suffix"))
        return VerseShortcut(start, end, extra.ifEmpty { null })
    }
}
