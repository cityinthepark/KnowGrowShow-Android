package com.stoneofhope.stoneofhope.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * iOS `AppColor` design tokens (Utils/Extensions/AppColor.swift), light and dark, plus the
 * note-type colour sets from the iOS asset catalog. Read through `AppTheme.colors`.
 */
@Immutable
data class AppColors(
    val neutral100: Color,
    val neutral90: Color,
    val neutral50: Color,
    val neutral20: Color,
    val surface0: Color,
    val pageBackground: Color,
    val lightGreen100: Color,
    val midGreen100: Color,
    val lightGreen40: Color,
    val blue80: Color,
    val blue20: Color,
    val blue100: Color,
    val yellow40: Color,
    val yellow60: Color,
    val yellow100: Color,
    val salmon40: Color,
    val salmon100: Color,
    val red20: Color,
    val red80: Color,
    val purple40: Color,
    val purple100: Color,
    val readingChipMutedFill: Color,
    val avatarBackground: Color,
    val avatarIcon: Color,
    val noteTypeBibleVerse: Color,
    val noteTypePrayer: Color,
    val noteTypeQuickNote: Color,
    val noteTypeSermon: Color,
    val noteTypeDefault: Color,
    /** Verse references, verse numbers and "Show all" in scripture areas (note redesign §1). */
    val scriptureInk: Color,
    /** "🙌 Answered" text; system green is too light for small text (note redesign §1.1). */
    val answeredInk: Color,
    val calendar: CalendarColors,
) {
    /** iOS `AppColor.ChipPair` (icon handled by the caller). */
    @Immutable
    data class ChipPair(val background: Color, val foreground: Color)

    val notesChip get() = ChipPair(lightGreen40, midGreen100)
    val prayerChip get() = ChipPair(blue20, blue100)
    val flashChip get() = ChipPair(purple40, purple100)
    val sermonChip get() = ChipPair(red20, red80)
    val videoChip get() = ChipPair(neutral20, neutral90)
    val pinnedTile get() = ChipPair(yellow40, yellow100)
    val starredTile get() = ChipPair(yellow60, yellow100)
    val chapterReadTile get() = ChipPair(blue20, blue100)
    val bibleVerseChip get() = ChipPair(noteTypeBibleVerse.copy(alpha = 0.14f), noteTypeBibleVerse)

    /** iOS hard-codes system green at 14% for the "Answered Prayers" tile. */
    val answeredPrayerTile get() = ChipPair(IosSystemGreen.copy(alpha = 0.14f), IosSystemGreen)

    /** iOS `BookReadingTint` palette, cycled per carousel card. */
    fun bookTint(index: Int): ChipPair = listOf(
        ChipPair(blue20, blue100),
        ChipPair(yellow40, yellow100),
        ChipPair(purple40, purple100),
    )[index % 3]

    companion object {
        private val IosSystemGreen = Color(0xFF34C759)

        val Light = AppColors(
            neutral100 = Color(0xFF242F2F), neutral90 = Color(0xFF4A5958), neutral50 = Color(0xFFA7B2B1),
            neutral20 = Color(0xFFDBE2E1), surface0 = Color(0xFFFFFFFF), pageBackground = Color(0xFFF1F3F1),
            lightGreen100 = Color(0xFFA7EDC0), midGreen100 = Color(0xFF004C45), lightGreen40 = Color(0xFFD5EBDB),
            blue80 = Color(0xFF128BA6), blue20 = Color(0xFFD9F2F7), blue100 = Color(0xFF2F5496),
            yellow40 = Color(0xFFF9EEE0), yellow60 = Color(0xFFF6DFA3), yellow100 = Color(0xFFA4580E),
            salmon40 = Color(0xFFFDD5C8), salmon100 = Color(0xFFE85A32), red20 = Color(0xFFEDD5D5),
            red80 = Color(0xFFBB363C), purple40 = Color(0xFFCFD4E4), purple100 = Color(0xFF6B7EA6),
            readingChipMutedFill = Color(0xFFFFFFFF), avatarBackground = Color(0xFFD9F2F7), avatarIcon = Color(0xFF128BA6),
            noteTypeBibleVerse = Color(0xFFA259FF), noteTypePrayer = Color(0xFF10B981), noteTypeQuickNote = Color(0xFF007AFF),
            noteTypeSermon = Color(0xFFFF9500), noteTypeDefault = Color(0xFF8E8E93),
            scriptureInk = Color(0xFF4F3F96), answeredInk = Color(0xFF1E6B33),
            calendar = CalendarColors.Light,
        )

        val Dark = AppColors(
            neutral100 = Color(0xFFF2F5F5), neutral90 = Color(0xFFC8D0CF), neutral50 = Color(0xFF6E7978),
            neutral20 = Color(0xFF3A3F3E), surface0 = Color(0xFF1E1E20), pageBackground = Color(0xFF121214),
            lightGreen100 = Color(0xFF2A5C52), midGreen100 = Color(0xFF7FD4BC), lightGreen40 = Color(0xFF243530),
            blue80 = Color(0xFF5CB8CC), blue20 = Color(0xFF1E3338), blue100 = Color(0xFF7FB0E8),
            yellow40 = Color(0xFF3A3228), yellow60 = Color(0xFF4A4028), yellow100 = Color(0xFFF0C878),
            salmon40 = Color(0xFF3A2A28), salmon100 = Color(0xFFFF9A78), red20 = Color(0xFF3A2828),
            red80 = Color(0xFFFF8A90), purple40 = Color(0xFF2C3040), purple100 = Color(0xFFA8B8D8),
            readingChipMutedFill = Color(0xFF3A3F3E), avatarBackground = Color(0xFF1E3338), avatarIcon = Color(0xFF8AD4E8),
            noteTypeBibleVerse = Color(0xFFD6B2FF), noteTypePrayer = Color(0xFF34D399), noteTypeQuickNote = Color(0xFF60A5FA),
            noteTypeSermon = Color(0xFFFDBA74), noteTypeDefault = Color(0xFFC7C7CC),
            scriptureInk = Color(0xFFCDBFFF), answeredInk = Color(0xFF7BD88F),
            calendar = CalendarColors.Dark,
        )
    }
}

/** iOS `Color.colorCalendar*` (Utils/Extensions/Color.swift): the calendar's day bars and day styles. */
@Immutable
data class CalendarColors(
    val quickNoteBar: Color,
    val studyNoteBar: Color,
    val starredBar: Color,
    val prayerBar: Color,
    val answeredBar: Color,
    val pinnedBar: Color,
    val imageBar: Color,
    val bibleBar: Color,
    val selectedDay: Color,
    val todayRing: Color,
    val todayRingFill: Color,
    val todayButton: Color,
    val todayButtonText: Color,
    /** Sermon / video day tints (iOS `calendarSermonFill` / `calendarVideoFill`): the plain chip tints vanish on the dark card. */
    val sermonFill: Color,
    val videoFill: Color,
    val gridBackground: Color,
) {
    companion object {
        private val SkyBlueText = Color(0xFF3378A1)
        private val SkyBlueLight = Color(0xFFDBF2FA)
        private val NightMutedBlue = Color(0xFF7DADE0)
        private val ButterBar = Color(0xFFEDD487)
        private val PinkBar = Color(0xFFFCB5D4)

        val Light = CalendarColors(
            quickNoteBar = SkyBlueText.copy(alpha = 0.55f), studyNoteBar = Color(0xFFFF9500).copy(alpha = 0.65f),
            starredBar = ButterBar, prayerBar = Color(0xFFF5EED8), answeredBar = Color(0xFF73C785),
            pinnedBar = Color(0xFF5856D6).copy(alpha = 0.55f), imageBar = Color(0xFFDBE8FF), bibleBar = PinkBar,
            selectedDay = SkyBlueText, todayRing = SkyBlueText.copy(alpha = 0.55f), todayRingFill = SkyBlueLight.copy(alpha = 0.35f),
            todayButton = SkyBlueLight, todayButtonText = SkyBlueText,
            sermonFill = Color(0xFFF9EEE0), videoFill = Color(0xFFD9F2F7), gridBackground = Color(0xFFF1F3F1),
        )

        val Dark = CalendarColors(
            quickNoteBar = NightMutedBlue, studyNoteBar = Color(0xFFFF9F0A).copy(alpha = 0.55f),
            starredBar = ButterBar, prayerBar = NightMutedBlue, answeredBar = Color(0xFF59A66B),
            pinnedBar = Color(0xFF5E5CE6).copy(alpha = 0.72f), imageBar = Color(0xFF5C4A69), bibleBar = PinkBar,
            selectedDay = NightMutedBlue, todayRing = NightMutedBlue, todayRingFill = Color(0xFF3B4A5C).copy(alpha = 0.35f),
            todayButton = Color(0xFF3D3D42), todayButtonText = Color(0xFFF2F5F5),
            sermonFill = Color(0xFF6B5130), videoFill = Color(0xFF2F6170), gridBackground = Color(0xFF2E2E33),
        )
    }
}

val LocalAppColors = staticCompositionLocalOf { AppColors.Light }
