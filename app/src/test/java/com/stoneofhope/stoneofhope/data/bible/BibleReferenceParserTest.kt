package com.stoneofhope.stoneofhope.data.bible

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ports iOS `BibleReferenceHelperTests`, `BibleReferenceParsingCoverageTests` and
 * `BibleCanonBoundsTests`, plus the `vN` shortcut (`detectSermonVerseShortcut`).
 */
class BibleReferenceParserTest {

    private fun ord(name: String) = BibleBookMatcher.bookOrd(name)!!
    private val matthew get() = ord("Matthew")
    private val john get() = ord("John")
    private val psalms get() = ord("Psalms")
    private val jude get() = ord("Jude")
    private val mark get() = ord("Mark")

    private fun langs(vararg selected: String) = BibleVersions.bookMatchingLanguages(selected.toList())

    private fun assertParsesCanon(
        input: String, book: Int, ch: Int, s: Int, e: Int?,
        languages: List<BibleLanguage> = emptyList(),
    ) {
        val r = BibleReferenceParser.parseFlexible(input, languages)
        assertNotNull("Expected parse: $input", r)
        assertEquals(ParsedBibleRef(book, ch, s, e), r)
        assertTrue("Expected within canon: $input", BibleReferenceParser.isWithinCanon(book, ch, s, e))
    }

    // BibleReferenceHelperTests

    @Test fun parsesStandardReference() = assertParsesCanon("John 3:16", john, 3, 16, null)

    @Test fun parsesReversedReference() = assertParsesCanon("3:16 John", john, 3, 16, null)

    // Standard "Book chapter:verse"

    @Test fun standardForms() {
        assertParsesCanon("Matt 1:1", matthew, 1, 1, null)
        assertParsesCanon("Matthew 1:1", matthew, 1, 1, null)
        assertParsesCanon("Mt 1:1", matthew, 1, 1, null)
        assertParsesCanon("Matt 1:1-4", matthew, 1, 1, 4)
        assertParsesCanon("Matt. 1:1", matthew, 1, 1, null)
        assertParsesCanon("  John 3:16  ", john, 3, 16, null)
        assertParsesCanon("john 3:16", john, 3, 16, null)
    }

    @Test fun reversedForms() {
        assertParsesCanon("1:1 Matt", matthew, 1, 1, null)
        assertParsesCanon("1:1-3 Mark", mark, 1, 1, 3)
        assertParsesCanon("3:16 John", john, 3, 16, null)
    }

    @Test fun reversedVerseSpanStillParses() {
        val r = BibleReferenceParser.parseFlexible("Matt 1:4-1")
        assertEquals(4, r?.startVerse)
        assertEquals(1, r?.endVerse)
        assertTrue(BibleReferenceParser.isWithinCanon(matthew, 1, 4, 1))
    }

    @Test fun parseChaptersFlexible() {
        assertEquals(ParsedBibleRef(matthew, 1, null, null), BibleReferenceParser.parseChaptersFlexible("Matthew 1"))
        assertEquals(ParsedBibleRef(john, 3, 16, 18), BibleReferenceParser.parseChaptersFlexible("John 3:16-18"))
        assertEquals(ParsedBibleRef(62, 3, null, null), BibleReferenceParser.parseChaptersFlexible("1 John 3"))
    }

    @Test fun singleChapterBooks() {
        assertParsesCanon("Jude 1:1", jude, 1, 1, null)
        assertParsesCanon("Obadiah 1:10", ord("Obadiah"), 1, 10, null)
    }

    // Canon violations (parse succeeds, isWithinCanon fails)

    @Test fun canonViolations() {
        assertEquals(ParsedBibleRef(psalms, 151, 1, null), BibleReferenceParser.parseFlexible("Psalms 151:1"))
        assertFalse(BibleReferenceParser.isWithinCanon(psalms, 151, 1, null))
        assertNotNull(BibleReferenceParser.parseFlexible("John 3:999"))
        assertFalse(BibleReferenceParser.isWithinCanon(john, 3, 999, null))
        assertNotNull(BibleReferenceParser.parseFlexible("Matt 1:0"))
        assertFalse(BibleReferenceParser.isWithinCanon(matthew, 1, 0, null))
        assertNotNull(BibleReferenceParser.parseFlexible("Matt 0:1"))
        assertFalse(BibleReferenceParser.isWithinCanon(matthew, 0, 1, null))
        assertNotNull(BibleReferenceParser.parseFlexible("John 3:1-999"))
        assertFalse(BibleReferenceParser.isWithinCanon(john, 3, 1, 999))
        assertNotNull(BibleReferenceParser.parseFlexible("Jude 2:1"))
        assertFalse(BibleReferenceParser.isWithinCanon(jude, 2, 1, null))
    }

    @Test fun malformedInput() {
        assertNull(BibleReferenceParser.parseFlexible("3:16"))
        assertNull(BibleReferenceParser.parseFlexible("Xyzzy 1:1"))
        assertNull(BibleReferenceParser.parseFlexible("Matthew"))
    }

    /** iOS parses only the first c:v and only with the book at the very start or end. */
    @Test fun bodyTextShapes() {
        assertEquals(ParsedBibleRef(52, 4, 13, null), BibleReferenceParser.parseFlexible("1Th 4:13 grateful for hope"))
        assertNull(BibleReferenceParser.parseFlexible("Today I read John 3:16"))
        assertNull(BibleReferenceParser.parseFlexible("约翰福音3：16 神爱世人")) // full-width colon: iOS misses it
    }

    // Chinese (via selected Bible version languages)

    @Test fun chinese_simplifiedAbbreviationJohn_whenCUVSelected() =
        assertParsesCanon("约 3:16", john, 3, 16, null, langs("CUV"))

    @Test fun chinese_fullNameGenesis_whenCUVSelected() {
        val genesis = BibleBookMatcher.bookOrd("创世记", langs("CUV"))!!
        assertParsesCanon("创世记 1:1", genesis, 1, 1, null, langs("CUV"))
        assertParsesCanon("创世纪 1:26-30", genesis, 1, 26, 30, langs("CUV"))
    }

    @Test fun chinese_andEnglish_whenKJVAndCUVSelected() {
        val l = langs("KJV", "CUV")
        assertParsesCanon("John 3:16", john, 3, 16, null, l)
        assertParsesCanon("约 3:16", john, 3, 16, null, l)
        assertParsesCanon("3:16 约", john, 3, 16, null, l)
    }

    @Test fun chinese_parses_whenOnlyKJVSelected() = assertParsesCanon("约 3:16", john, 3, 16, null, langs("KJV"))

    @Test fun englishAbbreviation_parses_whenOnlyCUVSelected() {
        val peter = BibleBooks.all.indexOfFirst { it.fullName(BibleLanguage.English) == "1 Peter" } + 1
        assertParsesCanon("1p 2:1-3", peter, 2, 1, 3, langs("CUV"))
        assertParsesCanon("彼前 2:1-3", peter, 2, 1, 3, langs("CUV"))
    }

    @Test fun bookMatchingLanguages_expandsChineseVariants() {
        val l = langs("CUV")
        assertTrue(BibleLanguage.ChineseTraditional in l)
        assertTrue(BibleLanguage.ChineseSimplified in l)
        assertEquals(listOf(BibleLanguage.English), langs("KJV", "NIV"))
    }

    @Test fun aliasesAndCase() {
        assertEquals(19, BibleBookMatcher.bookOrd("psalm"))
        assertEquals(20, BibleBookMatcher.bookOrd("PROVERB"))
        assertEquals(1, BibleBookMatcher.bookOrd("創世紀"))
        assertNull(BibleBookMatcher.bookOrd(""))
    }

    // BibleCanonBoundsTests

    @Test fun revelationBounds() {
        val revelation = BibleBooks.book(66)!!
        assertEquals(66, BibleBooks.all.size)
        for (chapter in 1..revelation.chapterCount) assertTrue(revelation.isValidChapter(chapter))
        assertTrue(revelation.isValidVerse(21, 22))
        assertFalse(revelation.isValidVerse(22, 22))
        assertTrue(BibleReferenceParser.isWithinCanon(66, 1, 1, null))
        assertEquals("Revelation", BibleBooks.name(66, BibleLanguage.English))
    }

    @Test fun everyChapterAcceptsLastVerseAndRejectsOneBeyond() {
        for (book in BibleBooks.all) {
            assertTrue(book.isValidChapter(book.chapterCount))
            assertFalse(book.isValidChapter(book.chapterCount + 1))
            assertFalse(book.isValidChapter(0))
            for (chapter in 1..book.chapterCount) {
                val last = book.versesPerChapter[chapter - 1]
                assertTrue(book.isValidVerse(last, chapter))
                assertFalse(book.isValidVerse(last + 1, chapter))
                assertFalse(book.isValidVerse(0, chapter))
            }
        }
    }

    @Test fun outOfRangeBookOrdsAreRejected() {
        for (ord in listOf(Int.MIN_VALUE, -1, 0, 67, 100, Int.MAX_VALUE)) {
            assertNull(BibleBooks.book(ord))
            assertFalse(BibleReferenceParser.isWithinCanon(ord, 1, 1, null))
            assertEquals("?", BibleBooks.name(ord))
        }
    }

    @Test fun canonTotals() {
        assertEquals(66, BibleBooks.all.size)
        assertEquals(1189, BibleBooks.all.sumOf { it.chapterCount })
        assertEquals(31_102, BibleBooks.all.sumOf { it.versesPerChapter.sum() })
    }

    // vN shortcut (iOS detectSermonVerseShortcut)

    @Test fun verseShortcut() {
        assertEquals(VerseShortcut(16, 16, "For God so loved"), VerseShortcutDetector.detect("v16 for God so loved"))
        assertEquals(VerseShortcut(3, 5, null), VerseShortcutDetector.detect("V 3 - 5"))
        assertEquals(VerseShortcut(2, 2, "I love of this"), VerseShortcutDetector.detect("I love v2 of this"))
        assertNull(VerseShortcutDetector.detect("v12abc"))
        assertNull(VerseShortcutDetector.detect("nov3"))
        assertNull(VerseShortcutDetector.detect("读v3")) // CJK letters are word characters for ICU \b
        assertEquals(VerseShortcut(3, 3, "-5x"), VerseShortcutDetector.detect("v3-5x"))
        assertEquals(VerseShortcut(7, 7, "，读经"), VerseShortcutDetector.detect("v7，读经"))
    }
}
