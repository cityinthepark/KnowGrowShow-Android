package com.stoneofhope.stoneofhope.data.bible

import com.stoneofhope.stoneofhope.data.settings.FontSizePreference
import com.stoneofhope.stoneofhope.data.settings.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class BibleVersionSelectionTest {
    private val en = BibleLanguage.English

    @Test fun addsASecondVersionAfterTheFirst() =
        assertEquals(VersionToggle.Changed(listOf("KJV", "CUV")), BibleVersionSelection.toggle(listOf("KJV"), "CUV", en))

    @Test fun apiOnlyVersionsCannotBeChosenYet() {
        assertEquals(VersionToggle.Unavailable, BibleVersionSelection.toggle(listOf("KJV"), "NIV", en))
        assertEquals(VersionToggle.Unavailable, BibleVersionSelection.toggle(listOf("KJV"), "nkjv", en))
    }

    @Test fun deselectingKeepsTheOtherAsPrimary() {
        assertEquals(VersionToggle.Changed(listOf("CUV")), BibleVersionSelection.toggle(listOf("KJV", "CUV"), "kjv", en))
    }

    @Test fun deselectingTheLastBringsBackTheDefault() {
        assertEquals(VersionToggle.Changed(listOf("KJV")), BibleVersionSelection.toggle(listOf("KJV"), "KJV", en))
        assertEquals(VersionToggle.Changed(listOf("CUV")), BibleVersionSelection.toggle(listOf("CUV"), "CUV", BibleLanguage.ChineseSimplified))
    }

    @Test fun unknownAppearanceValuesFallBackToTheDefaults() {
        assertEquals(ThemePreference.System, ThemePreference.of("blue"))
        assertEquals(ThemePreference.Dark, ThemePreference.of("dark"))
        assertEquals(FontSizePreference.Medium, FontSizePreference.of(null))
        assertEquals(1.3f, FontSizePreference.of("extraLarge").scale, 0f)
    }
}
