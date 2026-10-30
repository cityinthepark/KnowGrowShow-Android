package com.stoneofhope.stoneofhope.data.tag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagsTest {
    @Test fun presetNamesMatchCaseAndSpacesInEveryLanguage() {
        assertEquals(PresetTag.Trash, PresetTag.of(" TRASH "))
        assertEquals(PresetTag.Trash, PresetTag.of("Trash It"))
        assertEquals(PresetTag.Trash, PresetTag.of("回收站"))
        assertEquals(PresetTag.Milestone, PresetTag.of("milestone"))
        assertEquals(PresetTag.Milestone, PresetTag.of("里程碑"))
        assertNull(PresetTag.of("faith"))
    }

    @Test fun labelsCarryTheMark() {
        assertEquals("🏆 Milestone", PresetTag.label("Milestone"))
        assertEquals("🗑️ Trash", PresetTag.label("Trash"))
        assertEquals("Faith", PresetTag.label("Faith"))
    }

    @Test fun totalAddsTheFourKinds() = assertEquals(10, TagCounts(1, 2, 3, 4).total)
}
