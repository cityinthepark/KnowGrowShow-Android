package com.stoneofhope.stoneofhope.data.bible

import com.stoneofhope.stoneofhope.data.bible.SwiftText.DIGIT
import com.stoneofhope.stoneofhope.data.bible.SwiftText.SPACE

/** iOS `BibleRefLevel`. There is deliberately no book-only level. */
enum class BibleRefLevel { Chapter, Verse }

/** iOS `BibleRefConfig`: how complete a screen requires its reference field to be. */
data class BibleRefConfig(
    val requiredLevel: BibleRefLevel,
    val allowsVerseRange: Boolean,
    /** "Genesis 3, 5:1-3" — sermons only. */
    val allowsMultipleChapters: Boolean = false,
) {
    companion object {
        /** Note, prayer, flash card: a verse is required. */
        val FlashCard = BibleRefConfig(BibleRefLevel.Verse, allowsVerseRange = true)

        /** Chapter / sermon / video notes: a chapter is enough. */
        val ChapterAnchored = BibleRefConfig(BibleRefLevel.Chapter, allowsVerseRange = true)

        val Sermon = BibleRefConfig(BibleRefLevel.Chapter, allowsVerseRange = true, allowsMultipleChapters = true)
    }
}

/** iOS `ResolvedBibleRef`. [bookOrd] is 1-based; [verseEnd] is null for a single verse. */
data class ResolvedBibleRef(
    val bookOrd: Int,
    val chapter: Int,
    val verseStart: Int?,
    val verseEnd: Int?,
    /** Chapters after [chapter] (sermons only), in typed order. */
    val extraChapters: List<Int> = emptyList(),
    /** The extra comma-separated segments exactly as typed, e.g. `["5:1-3"]`. */
    val extraDisplaySegments: List<String> = emptyList(),
) {
    val allChapters: List<Int> get() = listOf(chapter) + extraChapters

    val level: BibleRefLevel get() = if (verseStart == null) BibleRefLevel.Chapter else BibleRefLevel.Verse

    /** `"VS:b:c:s-e"` (end = start for one verse) or `"CH:b:c"`. */
    val refKey: String
        get() = if (verseStart == null) "CH:$bookOrd:$chapter" else "VS:$bookOrd:$chapter:$verseStart-${verseEnd ?: verseStart}"

    val chapterKey: String get() = "CH:$bookOrd:$chapter"

    /**
     * iOS `displayText` always uses English book names (it calls `BibleFormatter` with no version);
     * [language] lets the field echo use the reader's language instead.
     */
    fun displayText(language: BibleLanguage = BibleLanguage.English): String {
        val base = BibleFormatter.display(refKey, language) ?: "${BibleBooks.name(bookOrd)} $chapter"
        return if (extraDisplaySegments.isEmpty()) base else (listOf(base) + extraDisplaySegments).joinToString(", ")
    }
}

/** iOS `BibleRefProblem`. */
sealed interface BibleRefProblem {
    data object UnknownBook : BibleRefProblem
    data class ChapterOutOfRange(val bookOrd: Int, val max: Int) : BibleRefProblem
    data class VerseOutOfRange(val bookOrd: Int, val chapter: Int, val max: Int) : BibleRefProblem
    data object ReversedVerseRange : BibleRefProblem
    data object VerseRangeNotAllowed : BibleRefProblem
    data object InvalidChapterList : BibleRefProblem

    /**
     * Wait for a typing pause before showing: "5:3-1" is a prefix of "5:3-12", and an IME can briefly
     * hold raw pinyin. Out-of-range numbers only grow, so they show at once.
     */
    val deservesTypingGrace: Boolean get() = this == ReversedVerseRange || this == UnknownBook
}

/** iOS `BibleRefValidation`. */
sealed interface BibleRefValidation {
    data object Empty : BibleRefValidation
    /** Legal so far, short of the required level: "keep going", not "wrong". */
    data class Incomplete(val missing: BibleRefLevel) : BibleRefValidation
    data class Invalid(val problem: BibleRefProblem) : BibleRefValidation
    data class Valid(val ref: ResolvedBibleRef) : BibleRefValidation

    val resolved: ResolvedBibleRef? get() = (this as? Valid)?.ref
    val isValid: Boolean get() = this is Valid
}

/**
 * iOS `BibleReferenceValidator`: "is this reference text good enough for this screen yet", telling
 * "not done typing" apart from "wrong". [languages] are the selected versions' book-name languages
 * (`BibleVersions.bookMatchingLanguages`); English and both Chinese scripts are always tried too.
 */
object BibleReferenceValidator {

    fun validate(raw: String, config: BibleRefConfig, languages: List<BibleLanguage> = emptyList()): BibleRefValidation {
        val text = normalize(raw)
        if (text.isEmpty()) return BibleRefValidation.Empty
        if (config.allowsMultipleChapters && text.contains(",")) return validateMultipleChapters(text, config, languages)

        val scan = scan(text) ?: return bookTokenOnly(text, languages)
        val book = resolveBook(scan.bookToken, languages) ?: return BibleRefValidation.Invalid(BibleRefProblem.UnknownBook)

        // A one-chapter book is written "Jude 5" to mean verse 5.
        if (book.chapterCount == 1 && scan.verseStart == null && scan.chapter != null) {
            return finish(book, chapter = 1, verseStart = scan.chapter, verseEnd = null, config = config)
        }
        val chapter = scan.chapter ?: return BibleRefValidation.Incomplete(BibleRefLevel.Chapter)
        return finish(book, chapter, scan.verseStart, scan.verseEnd, config)
    }

    /** Full-width punctuation from a Chinese keyboard, then Swift-style trim. */
    fun normalize(raw: String): String = SwiftText.trim(normalizePunctuation(raw))

    /** The 1:1 character replacements of iOS `normalize`, without trimming (indices stay aligned). */
    internal fun normalizePunctuation(raw: String): String {
        val chars = raw.toCharArray()
        for (i in chars.indices) {
            chars[i] = when (chars[i]) {
                '：' -> ':'
                '－', '–', '—', '−' -> '-'
                '　' -> ' '
                else -> chars[i]
            }
        }
        return String(chars)
    }

    // MARK: Multiple chapters (sermons only)

    private fun validateMultipleChapters(text: String, config: BibleRefConfig, languages: List<BibleLanguage>): BibleRefValidation {
        val segments = text.split(",").map(SwiftText::trimWhitespaces)
        val firstRaw = segments.first()
        if (firstRaw.isEmpty()) return BibleRefValidation.Empty

        val first = validate(firstRaw, config.copy(allowsMultipleChapters = false), languages)
        val firstResolved = (first as? BibleRefValidation.Valid)?.ref ?: return first
        val book = BibleBooks.book(firstResolved.bookOrd) ?: return BibleRefValidation.Invalid(BibleRefProblem.UnknownBook)

        val chapters = mutableListOf<Int>()
        val displaySegments = mutableListOf<String>()
        val rest = segments.drop(1)
        for ((index, raw) in rest.withIndex()) {
            val isLast = index == rest.lastIndex
            if (raw.isEmpty()) {
                // A trailing comma while typing ("Genesis 3, ") is quietly incomplete.
                if (isLast) break else continue
            }
            val scanned = scanChapterOnly(raw)
            if (scanned == null) {
                if (isLast) break
                return BibleRefValidation.Invalid(BibleRefProblem.InvalidChapterList)
            }
            if (!book.isValidChapter(scanned.first)) {
                return BibleRefValidation.Invalid(BibleRefProblem.ChapterOutOfRange(firstResolved.bookOrd, book.chapterCount))
            }
            val verseStart = scanned.second
            if (verseStart != null && !book.isValidVerse(verseStart, scanned.first)) {
                return BibleRefValidation.Invalid(
                    BibleRefProblem.VerseOutOfRange(firstResolved.bookOrd, scanned.first, book.versesPerChapter[scanned.first - 1]),
                )
            }
            if (scanned.first != firstResolved.chapter && scanned.first !in chapters) chapters += scanned.first
            displaySegments += raw
        }
        return BibleRefValidation.Valid(firstResolved.copy(extraChapters = chapters, extraDisplaySegments = displaySegments))
    }

    private val chapterOnlyPattern = Regex("^($DIGIT{1,3})(?:$SPACE*:$SPACE*($DIGIT{1,3})(?:$SPACE*-$SPACE*($DIGIT{1,3})?)?)?$")

    /** (chapter, verseStart) for a later segment: "5", "5:3", "5:3-7" — never a book name. */
    private fun scanChapterOnly(text: String): Pair<Int, Int?>? {
        val m = chapterOnlyPattern.find(text) ?: return null
        val chapter = SwiftText.asciiInt(m.groups[1]?.value) ?: return null
        return chapter to SwiftText.asciiInt(m.groups[2]?.value)
    }

    // MARK: Canon checks

    private fun finish(book: BibleBook, chapter: Int, verseStart: Int?, verseEnd: Int?, config: BibleRefConfig): BibleRefValidation {
        if (!book.isValidChapter(chapter)) {
            return BibleRefValidation.Invalid(BibleRefProblem.ChapterOutOfRange(book.ord, book.chapterCount))
        }
        if (verseStart == null) {
            return if (config.requiredLevel == BibleRefLevel.Chapter) {
                BibleRefValidation.Valid(ResolvedBibleRef(book.ord, chapter, null, null))
            } else {
                BibleRefValidation.Incomplete(BibleRefLevel.Verse)
            }
        }
        val maxVerse = book.versesPerChapter[chapter - 1]
        if (!book.isValidVerse(verseStart, chapter)) {
            return BibleRefValidation.Invalid(BibleRefProblem.VerseOutOfRange(book.ord, chapter, maxVerse))
        }
        if (verseEnd != null) {
            if (!config.allowsVerseRange) return BibleRefValidation.Invalid(BibleRefProblem.VerseRangeNotAllowed)
            if (!book.isValidVerse(verseEnd, chapter)) {
                return BibleRefValidation.Invalid(BibleRefProblem.VerseOutOfRange(book.ord, chapter, maxVerse))
            }
            // After the bounds check, so "5:3-999" reports the bound, the more actionable message.
            if (verseEnd < verseStart) return BibleRefValidation.Invalid(BibleRefProblem.ReversedVerseRange)
        }
        return BibleRefValidation.Valid(
            ResolvedBibleRef(book.ord, chapter, verseStart, if (verseEnd == verseStart) null else verseEnd),
        )
    }

    // MARK: Book token

    private fun resolveBook(token: String, languages: List<BibleLanguage>): BibleBook? =
        BibleBookMatcher.bookOrd(token, languages)?.let(BibleBooks::book)

    /** No digits typed yet: a known book or the start of one is incomplete; anything else is unknown. */
    private fun bookTokenOnly(token: String, languages: List<BibleLanguage>): BibleRefValidation =
        if (resolveBook(token, languages) != null || BibleBookMatcher.isBookNamePrefix(token)) {
            BibleRefValidation.Incomplete(BibleRefLevel.Chapter)
        } else {
            BibleRefValidation.Invalid(BibleRefProblem.UnknownBook)
        }

    // MARK: Text scanning

    private data class Scan(val bookToken: String, val chapter: Int?, val verseStart: Int?, val verseEnd: Int?)

    /** "Matt." / "1 Pet." still resolve. */
    private fun trimBookToken(raw: String): String = SwiftText.trim(SwiftText.trim(raw).trimEnd('.'))

    private val notDigitOrSpace = "[^\\p{Nd}\\t\\n\\f\\r\\p{Z}]"

    /** Ordered attempts, each anchored so the leading numeral of "1 Peter 2:1" stays in the book token. */
    private val bookChapterVerse = Regex("^(.*?)($DIGIT{1,3})$SPACE*:$SPACE*($DIGIT{1,3})(?:$SPACE*-$SPACE*($DIGIT{1,3})?)?$SPACE*$")
    private val chapterVerseBook = Regex("^($DIGIT{1,3})$SPACE*:$SPACE*($DIGIT{1,3})(?:$SPACE*-$SPACE*($DIGIT{1,3})?)?$SPACE*(.+)$")
    private val bookChapterColon = Regex("^(.*?)($DIGIT{1,3})$SPACE*:$SPACE*$")
    private val bookChapter = Regex("^(.*?$notDigitOrSpace)$SPACE*($DIGIT{1,3})$SPACE*$")

    private fun scan(text: String): Scan? {
        // "Book 5:3", "Book 5:3-12", "Book 5:3-" (range still being typed)
        bookChapterVerse.find(text)?.let { m ->
            return Scan(trimBookToken(m.groups[1]?.value.orEmpty()), m.int(2), m.int(3), m.int(4))
        }
        // "5:3 Book"
        chapterVerseBook.find(text)?.let { m ->
            return Scan(trimBookToken(m.groups[4]?.value.orEmpty()), m.int(1), m.int(2), m.int(3))
        }
        // "Book 5:" — colon typed, verse pending
        bookChapterColon.find(text)?.let { m ->
            return Scan(trimBookToken(m.groups[1]?.value.orEmpty()), m.int(2), null, null)
        }
        // "Book 5" / "诗150" — a non-digit before the number, so a bare "150" is not a chapter
        bookChapter.find(text)?.let { m ->
            return Scan(trimBookToken(m.groups[1]?.value.orEmpty()), m.int(2), null, null)
        }
        return null
    }

    private fun MatchResult.int(group: Int): Int? = SwiftText.asciiInt(groups[group]?.value)
}
