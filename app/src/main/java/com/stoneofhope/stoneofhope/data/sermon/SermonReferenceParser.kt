package com.stoneofhope.stoneofhope.data.sermon

import com.stoneofhope.stoneofhope.data.bible.BibleBookMatcher
import com.stoneofhope.stoneofhope.data.bible.BibleBooks
import com.stoneofhope.stoneofhope.data.bible.BibleLanguage
import com.stoneofhope.stoneofhope.data.bible.SwiftText
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity

/** iOS `ParsedResult`. [chapterRange] is the display text written to `Sermon.chapterRange`. */
data class ParsedSermonRef(
    val bookOrd: Int?,
    val chapters: List<Int>,
    val chapterRange: String,
    val isValid: Boolean,
)

/**
 * iOS `SermonReferenceParser`: one book and any number of chapters ("Romans 3, 5:1-3"), the format of
 * `Sermon.chapters` / `chapterRange` / `bookOrd`.
 *
 * iOS takes only the first word as the book, so "1 John 3", "Song of Solomon 2" and "约翰一书3" never
 * parse, although the reference field accepts them and the book picker writes "1 John " itself
 * (IOS_ISSUES K21). Android tries iOS's first word, then the longest run of leading words, then the
 * text before the first digit. Every reference iOS parses still parses to the same result.
 */
object SermonReferenceParser {

    fun parse(input: String, languages: List<BibleLanguage> = emptyList()): ParsedSermonRef {
        val raw = SwiftText.trim(input)
        if (raw.isEmpty()) return ParsedSermonRef(null, emptyList(), "", isValid = false)

        // Only the first book reference (before ";"), and nothing after an en dash.
        val mainSegment = raw.split(";").first()
        val clean = SwiftText.trimWhitespaces(mainSegment.split("–").first())
        val (bookOrd, token) = bookPrefix(clean, languages)
            ?: return ParsedSermonRef(null, emptyList(), raw, isValid = false)

        val remainder = SwiftText.trimWhitespaces(clean.substring(token.length))
        val chapters = extractValidChapters(remainder, bookOrd)
        val isValid = chapters.isNotEmpty()
        // iOS `getBookName(for:)`: the full name in the interface language.
        val display = if (isValid) BibleBooks.name(bookOrd) + if (remainder.isEmpty()) "" else " $remainder" else raw
        return ParsedSermonRef(bookOrd, chapters, display, isValid)
    }

    /** iOS `SermonReferenceParser.applyTo(sermon:from:)`: `book` is left alone, as on iOS. */
    fun applyTo(sermon: SermonEntity, input: String, languages: List<BibleLanguage> = emptyList()): SermonEntity {
        val result = parse(input, languages)
        return sermon.copy(chapterRange = result.chapterRange, bookOrd = result.bookOrd, chapters = result.chapters)
    }

    /** The book and the exact leading text it was read from. */
    private fun bookPrefix(clean: String, languages: List<BibleLanguage>): Pair<Int, String>? {
        // iOS: `components(separatedBy: .whitespaces).first`.
        val firstWord = clean.split(Regex("[\\t\\p{Zs}]")).first()
        BibleBookMatcher.bookOrd(firstWord, languages)?.let { return it to firstWord }

        // Android: "1 John", "Song of Solomon" — the longest run of words that names a book.
        val words = Regex("[^\\t\\p{Zs}]+").findAll(clean).toList()
        for (count in minOf(words.size, 4) downTo 2) {
            val token = clean.substring(0, words[count - 1].range.last + 1)
            BibleBookMatcher.bookOrd(token.trimEnd('.'), languages)?.let { return it to token }
        }
        // Android: "约翰一书3", "Gen3" — the text before the first digit.
        val digit = clean.indexOfFirst { it in '0'..'9' }
        if (digit > 0) {
            val token = clean.substring(0, digit)
            BibleBookMatcher.bookOrd(SwiftText.trimWhitespaces(token).trimEnd('.'), languages)?.let { return it to token }
        }
        return null
    }

    private val chapterRangePattern = Regex("(\\d+)(?:\\s*-\\s*(\\d+))?")

    /**
     * iOS `BibleBookHelper.extractValidChapters(from:bookOrd:)`, sorted and de-duplicated. iOS builds
     * `start...end` for "5-3" and crashes (Swift traps on a reversed range: IOS_ISSUES K22); here a
     * reversed range covers the same chapters as "3-5".
     */
    fun extractValidChapters(ref: String, bookOrd: Int): List<Int> {
        val book = BibleBooks.book(bookOrd) ?: return emptyList()
        val chapters = sortedSetOf<Int>()
        val isOneChapterBook = book.chapterCount == 1
        for (part in ref.split(",")) {
            val trimmed = SwiftText.trimWhitespaces(part)
            when {
                trimmed.contains(":") -> {
                    // The chapter before ":".
                    val chapter = SwiftText.asciiInt(trimmed.split(":").first())
                    if (chapter != null && book.isValidChapter(chapter)) chapters += chapter
                }
                // A one-chapter book: every number is a verse.
                isOneChapterBook -> chapters += 1
                else -> for (m in chapterRangePattern.findAll(trimmed)) {
                    val start = SwiftText.asciiInt(m.groupValues[1]) ?: continue
                    if (!book.isValidChapter(start)) continue
                    val end = SwiftText.asciiInt(m.groups[2]?.value)
                    if (end == null) {
                        chapters += start
                    } else {
                        for (c in minOf(start, end)..maxOf(start, end)) if (book.isValidChapter(c)) chapters += c
                    }
                }
            }
        }
        return chapters.toList()
    }
}
