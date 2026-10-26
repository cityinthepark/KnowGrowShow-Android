package com.stoneofhope.stoneofhope.data.bible

import com.stoneofhope.stoneofhope.data.bible.BibleRefValidation.Empty
import com.stoneofhope.stoneofhope.data.bible.BibleRefValidation.Incomplete
import com.stoneofhope.stoneofhope.data.bible.BibleRefValidation.Invalid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Port of iOS `BibleReferenceValidatorTests` (same inputs, same expected states). */
class BibleReferenceValidatorTest {

    private val matthew = 40
    private val john = 43
    private val jude = 65
    private val revelation = 66

    private fun validate(input: String, config: BibleRefConfig = BibleRefConfig.FlashCard) =
        BibleReferenceValidator.validate(input, config)

    private fun resolved(input: String, config: BibleRefConfig = BibleRefConfig.FlashCard): ResolvedBibleRef =
        validate(input, config).resolved ?: throw AssertionError("\"$input\" rejected: ${validate(input, config)}")

    // Typing a reference from scratch, one keystroke at a time

    @Test fun typingAValidReferenceNeverPassesThroughInvalid() {
        for (stroke in listOf("M", "Ma", "Mat", "Matt", "Matthew", "Matthew ", "Matthew 5", "Matthew 5:", "Matthew 5:3")) {
            val v = validate(stroke)
            if (v is Invalid) fail("\"$stroke\" reported ${v.problem} while still being typed")
        }
        assertTrue(validate("Matthew 5:3").isValid)
    }

    @Test fun chineseTypingNeverPassesThroughInvalid() {
        for (stroke in listOf("马", "马太", "马太福", "马太福音", "马太福音 5", "马太福音 5:3")) {
            val v = validate(stroke)
            if (v is Invalid) fail("\"$stroke\" reported ${v.problem} while still being typed")
        }
    }

    @Test fun bookPrefixIsIncompleteRatherThanUnknown() {
        assertEquals(Incomplete(BibleRefLevel.Chapter), validate("马"))
        assertEquals(Incomplete(BibleRefLevel.Chapter), validate("Mat"))
        assertEquals(Incomplete(BibleRefLevel.Chapter), validate("Matthew"))
    }

    @Test fun stringThatCannotBecomeABookIsUnknown() {
        assertEquals(Invalid(BibleRefProblem.UnknownBook), validate("鸡"))
        assertEquals(Invalid(BibleRefProblem.UnknownBook), validate("zzz"))
    }

    @Test fun emptyInputIsEmptyNotInvalid() {
        assertEquals(Empty, validate(""))
        assertEquals(Empty, validate("   "))
    }

    // requiredLevel

    @Test fun chapterOnlyIsIncompleteForFlashCardsAndValidForChapterAnchored() {
        assertEquals(Incomplete(BibleRefLevel.Verse), validate("Matthew 5", BibleRefConfig.FlashCard))
        val ref = resolved("Matthew 5", BibleRefConfig.ChapterAnchored)
        assertEquals(matthew, ref.bookOrd)
        assertEquals(5, ref.chapter)
        assertNull(ref.verseStart)
        assertEquals(BibleRefLevel.Chapter, ref.level)
    }

    @Test fun trailingColonKeepsChapterAnchoredValid() {
        assertTrue(validate("Matthew 5:", BibleRefConfig.ChapterAnchored).isValid)
        assertEquals(Incomplete(BibleRefLevel.Verse), validate("Matthew 5:", BibleRefConfig.FlashCard))
    }

    // Canon bounds

    @Test fun chapterBeyondBookIsInvalidImmediately() {
        assertEquals(Invalid(BibleRefProblem.ChapterOutOfRange(matthew, 28)), validate("Matthew 29:1"))
    }

    @Test fun verseBeyondChapterIsInvalidImmediately() {
        assertEquals(Invalid(BibleRefProblem.VerseOutOfRange(matthew, 5, 48)), validate("Matthew 5:49"))
        assertEquals(Invalid(BibleRefProblem.VerseOutOfRange(john, 3, 36)), validate("John 3:37"))
    }

    @Test fun lastVerseOfAChapterIsAccepted() {
        assertTrue(validate("Matthew 5:48").isValid)
        assertTrue(validate("John 3:36").isValid)
    }

    @Test fun revelationIsAccepted() {
        assertEquals(revelation, resolved("Revelation 1:1").bookOrd)
        assertTrue(validate("启 22:21").isValid)
        assertEquals(Invalid(BibleRefProblem.ChapterOutOfRange(revelation, 22)), validate("Revelation 23:1"))
    }

    // Verse ranges

    @Test fun verseRangeIsResolved() {
        val ref = resolved("Matthew 5:3-12")
        assertEquals(3, ref.verseStart)
        assertEquals(12, ref.verseEnd)
    }

    @Test fun rangeCollapsingToOneVerseDropsTheEnd() {
        assertNull(resolved("Matthew 5:3-3").verseEnd)
        assertEquals(3, resolved("Matthew 5:3-3").verseStart)
    }

    @Test fun trailingDashIsTreatedAsASingleVerse() {
        val ref = resolved("Matthew 5:3-")
        assertEquals(3, ref.verseStart)
        assertNull(ref.verseEnd)
    }

    @Test fun reversedRangeIsInvalidButGraced() {
        assertEquals(Invalid(BibleRefProblem.ReversedVerseRange), validate("Matthew 5:3-1"))
        assertTrue(BibleRefProblem.ReversedVerseRange.deservesTypingGrace)
    }

    @Test fun rangeEndBeyondChapterReportsTheBound() {
        assertEquals(Invalid(BibleRefProblem.VerseOutOfRange(matthew, 5, 48)), validate("Matthew 5:3-99"))
    }

    @Test fun rangeRejectedWhenConfigDisallowsIt() {
        val singleVerseOnly = BibleRefConfig(BibleRefLevel.Verse, allowsVerseRange = false)
        assertEquals(Invalid(BibleRefProblem.VerseRangeNotAllowed), validate("Matthew 5:3-12", singleVerseOnly))
        assertTrue(validate("Matthew 5:3", singleVerseOnly).isValid)
    }

    @Test fun gracePolicy() {
        assertFalse(BibleRefProblem.ChapterOutOfRange(40, 28).deservesTypingGrace)
        assertFalse(BibleRefProblem.VerseOutOfRange(40, 5, 48).deservesTypingGrace)
        assertFalse(BibleRefProblem.VerseRangeNotAllowed.deservesTypingGrace)
        assertTrue(BibleRefProblem.UnknownBook.deservesTypingGrace)
        assertTrue(BibleRefProblem.ReversedVerseRange.deservesTypingGrace)
    }

    // Input shapes

    @Test fun bookNameStartingWithANumeral() {
        val ref = resolved("1 Peter 2:1")
        assertEquals(60, ref.bookOrd)
        assertEquals(2, ref.chapter)
        assertEquals(1, ref.verseStart)
    }

    @Test fun reversedOrderMatchesExistingParserBehaviour() {
        val ref = resolved("3:16 John")
        assertEquals(john, ref.bookOrd)
        assertEquals(3, ref.chapter)
        assertEquals(16, ref.verseStart)
    }

    @Test fun fullWidthPunctuationIsNormalised() {
        assertTrue(validate("马太福音 5：3").isValid)
        assertTrue(validate("马太福音 5：3－12").isValid)
        assertEquals(12, resolved("马太福音 5：3－12").verseEnd)
    }

    @Test fun noSpaceBetweenChineseBookAndNumber() {
        assertTrue(validate("诗150", BibleRefConfig.ChapterAnchored).isValid)
        assertEquals(150, resolved("诗150", BibleRefConfig.ChapterAnchored).chapter)
    }

    @Test fun trailingPeriodInAbbreviation() {
        assertTrue(validate("Matt. 5:3").isValid)
    }

    @Test fun oneChapterBookTreatsBareNumberAsVerse() {
        val ref = resolved("Jude 5")
        assertEquals(jude, ref.bookOrd)
        assertEquals(1, ref.chapter)
        assertEquals(5, ref.verseStart)
        assertEquals(Invalid(BibleRefProblem.VerseOutOfRange(jude, 1, 25)), validate("Jude 26"))
    }

    // Keys handed to the rest of the app

    @Test fun refKeyMatchesTheShapeBibleFormatterParses() {
        assertEquals("VS:40:5:3-12", resolved("Matthew 5:3-12").refKey)
        assertEquals("VS:40:5:3-3", resolved("Matthew 5:3").refKey)
        assertEquals("CH:40:5", resolved("Matthew 5", BibleRefConfig.ChapterAnchored).refKey)
        assertEquals("CH:40:5", resolved("Matthew 5:3").chapterKey)
    }

    @Test fun displayTextRoundTripsThroughBibleFormatter() {
        assertEquals("John 3:16", resolved("Jn 3:16").displayText())
        // Android addition: the field echo can use the reader's language (iOS always shows English).
        assertEquals("約翰福音 3:16", resolved("约 3:16").displayText(BibleLanguage.ChineseTraditional))
    }

    // Agreement with the logic this replaced

    @Test fun agreesWithPreviousFlashCardGate() {
        val inputs = listOf(
            "John 3:16", "Jn 3:16", "Matthew 5:3-12", "3:16 John", "Matt. 5:3",
            "Revelation 1:1", "启 22:21", "1 Peter 2:1",
            "Matthew 29:1", "Matthew 5:49", "John 3:37", "Revelation 23:1",
            "", "   ", "Matthew", "鸡", "zzz", "Matthew 5",
        )
        for (input in inputs) {
            assertEquals("disagreement on \"$input\"", previousFlashCardGate(input), validate(input).isValid)
        }
    }

    @Test fun intendedDivergencesFromPreviousGate() {
        assertTrue(previousFlashCardGate("Matthew 5:3-1"))
        assertFalse(validate("Matthew 5:3-1").isValid)
        for (nowAccepted in listOf("马太福音 5：3", "Jude 5")) {
            assertFalse("$nowAccepted unexpectedly parsed before", previousFlashCardGate(nowAccepted))
            assertTrue("$nowAccepted should now be accepted", validate(nowAccepted).isValid)
        }
    }

    // Multiple chapters (sermon only)

    @Test fun sermonConfigAcceptsChapterThenVerseRangeSegment() {
        val ref = resolved("Genesis 3, 5:1-3", BibleRefConfig.Sermon)
        assertEquals(3, ref.chapter)
        assertNull(ref.verseStart)
        assertEquals(listOf(5), ref.extraChapters)
        assertEquals(listOf(3, 5), ref.allChapters)
        assertEquals("Genesis 3, 5:1-3", ref.displayText())
    }

    @Test fun sermonConfigAcceptsVerseRangeThenBareChapters() {
        val ref = resolved("Genesis 1:2-6, 3:5, 4", BibleRefConfig.Sermon)
        assertEquals(1, ref.chapter)
        assertEquals(2, ref.verseStart)
        assertEquals(6, ref.verseEnd)
        assertEquals(listOf(3, 4), ref.extraChapters)
        assertEquals(listOf(1, 3, 4), ref.allChapters)
        assertEquals("Genesis 1:2-6, 3:5, 4", ref.displayText())
    }

    @Test fun sermonConfigWithoutCommaBehavesLikeChapterAnchored() {
        val ref = resolved("Genesis 6", BibleRefConfig.Sermon)
        assertEquals(6, ref.chapter)
        assertEquals(emptyList<Int>(), ref.extraChapters)
        assertEquals(listOf(6), ref.allChapters)
    }

    @Test fun sermonConfigTrailingCommaIsQuietlyIncompleteNotInvalid() {
        val ref = resolved("Genesis 3, ", BibleRefConfig.Sermon)
        assertEquals(3, ref.chapter)
        assertEquals(emptyList<Int>(), ref.extraChapters)
    }

    @Test fun sermonConfigRejectsExtraChapterOutOfCanonRange() {
        assertEquals(Invalid(BibleRefProblem.ChapterOutOfRange(1, 50)), validate("Genesis 3, 51", BibleRefConfig.Sermon))
    }

    @Test fun sermonConfigRejectsGarbageMiddleSegment() {
        assertEquals(Invalid(BibleRefProblem.InvalidChapterList), validate("Genesis 3, abc, 5", BibleRefConfig.Sermon))
    }

    @Test fun multipleChaptersRejectedForConfigsThatDontAllowThem() {
        assertFalse(validate("Genesis 3, 5:1-3", BibleRefConfig.ChapterAnchored).isValid)
        assertFalse(validate("Genesis 3, 5:1-3", BibleRefConfig.FlashCard).isValid)
    }

    @Test fun chapterOnlyRefKeyDisplaysThroughBibleFormatter() {
        assertEquals("CH:40:5", resolved("Matthew 5", BibleRefConfig.ChapterAnchored).refKey)
        assertEquals("Matthew 5", BibleFormatter.display("CH:40:5", versionAbbr = null, showVersion = false))
    }

    // Android-only: ICU vs JVM regex differences must not change results.

    @Test fun fullWidthDigitsAreNotNumbersLikeSwiftInt() {
        // NSRegularExpression's \d matches "３" but Swift Int("３") is nil, so iOS waits for a chapter.
        assertEquals(Incomplete(BibleRefLevel.Chapter), validate("John ３:１６"))
    }

    @Test fun ideographicAndNoBreakSpacesCountAsSpace() {
        assertTrue(validate("约翰福音　3:16").isValid)
        assertTrue(validate("John 3:16").isValid)
    }

    private fun previousFlashCardGate(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        val parsed = BibleReferenceParser.parseFlexible(trimmed) ?: return false
        val start = parsed.startVerse ?: return false
        return BibleReferenceParser.isWithinCanon(parsed.bookOrd, parsed.chapter, start, parsed.endVerse ?: start)
    }

    @Test fun bookMatchingIsLanguageOrderIndependent() {
        // getBookOrdSmart tries the selected versions' languages first; no name may mean a different
        // book in another language, or the selected version would change which book is found.
        for (book in BibleBooks.all) {
            for (name in BibleBookMatcher.allPossibleNames(book)) {
                val orders = listOf(
                    listOf(BibleLanguage.English),
                    listOf(BibleLanguage.ChineseSimplified),
                    listOf(BibleLanguage.ChineseTraditional),
                )
                val results = orders.map { BibleBookMatcher.bookOrd(name, it) }.toSet()
                assertEquals("\"$name\" resolves differently by language order", 1, results.size)
                assertNotNull(results.single())
            }
        }
    }
}
