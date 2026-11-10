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

    @Test fun transformMatchesTheNameSetSharedWithIosAndWeb() {
        for (name in listOf("transform", "Transformed", " TRANSFORMATION ", "转化", "轉化", "蜕变", "蛻變")) {
            assertEquals(name, PresetTag.Transform, PresetTag.of(name))
        }
        assertNull(PresetTag.of("transforming"))
        assertNull(PresetTag.of("变化"))
    }

    @Test fun labelsCarryTheMark() {
        assertEquals("🦋 Transform", PresetTag.label("Transform"))
        assertEquals("🦋 轉化", PresetTag.label("轉化"))
        assertEquals("🏆 Milestone", PresetTag.label("Milestone"))
        assertEquals("🗑️ Trash", PresetTag.label("Trash"))
        assertEquals("Faith", PresetTag.label("Faith"))
    }

    @Test fun totalAddsTheFourKinds() = assertEquals(10, TagCounts(1, 2, 3, 4).total)
}
