package com.stoneofhope.stoneofhope.data.bible

import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `BibleBookAbbreviationTests` (E1): 2 Samuel lacked the spaced forms every other numbered book has. */
class BibleBookAbbreviationTest {

    @Test fun secondSamuelHasSpacedAbbreviations() {
        assertEquals(
            listOf("2Sa", "2S", "2Sam", "2 Sa", "2 S", "2 Sam"),
            BibleBooks.book(10)!!.abbreviations(BibleLanguage.English),
        )
    }

    @Test fun noBookRepeatsAnAbbreviationInAnyLanguage() {
        for (book in BibleBooks.all) for (language in BibleLanguage.entries) {
            val abbreviations = book.abbreviations(language)
            assertEquals("${book.fullName(BibleLanguage.English)} repeats one in $language: $abbreviations", abbreviations.size, abbreviations.toSet().size)
        }
    }

    @Test fun spacedSecondSamuelAbbreviationsResolve() {
        for (input in listOf("2 Sam 3:4", "2 Sa 3:4", "2 S 3:4", "1 Sam 3:4")) {
            val ref = BibleReferenceValidator.validate(input, BibleRefConfig.FlashCard).resolved
            assertEquals(input, if (input.startsWith("1")) 9 else 10, ref?.bookOrd)
            assertEquals(input, 3, ref?.chapter)
            assertEquals(input, 4, ref?.verseStart)
        }
    }
}
