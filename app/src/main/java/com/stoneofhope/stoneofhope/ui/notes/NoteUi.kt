package com.stoneofhope.stoneofhope.ui.notes

import android.icu.text.DateIntervalFormat
import android.icu.util.DateInterval
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.stoneofhope.stoneofhope.ui.effects.transformChip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.notes.NoteBadge
import com.stoneofhope.stoneofhope.data.notes.NoteBadgeTitle
import com.stoneofhope.stoneofhope.data.notes.NoteBadges
import com.stoneofhope.stoneofhope.data.notes.NoteFilter
import com.stoneofhope.stoneofhope.data.notes.NoteLabel
import com.stoneofhope.stoneofhope.data.notes.NoteTint
import com.stoneofhope.stoneofhope.ui.theme.AppColors
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Shared pieces of the redesigned note list and details (docs/design/note-*/). Colors only come from
// AppColors (§1); sizes follow §2.

/** iOS system yellow, used for stars. */
val StarYellow = Color(0xFFFFCC00)

/** A `fontSize` + line-height-multiplier text style, as the design specifies them. */
fun textStyle(size: Int, lineHeight: Float = 1.3f, weight: FontWeight = FontWeight.Normal, serif: Boolean = false, tracking: TextUnit = TextUnit.Unspecified) =
    TextStyle(
        fontSize = size.sp, lineHeight = (size * lineHeight).sp, fontWeight = weight, letterSpacing = tracking,
        fontFamily = if (serif) FontFamily.Serif else FontFamily.Default,
    )

/** Uppercase small-caps labels: "✝️ JOHN 3:16", "TODAY · MON, OCT 26". */
fun capsStyle(tracking: Float) = textStyle(12, weight = FontWeight.Bold, tracking = tracking.em)

/** Chip colors per type (design §1.1): the AppColors chip pairs; verses use the 14% purple + `scriptureInk`. */
fun NoteTint.chip(c: AppColors): AppColors.ChipPair = when (this) {
    NoteTint.Notes -> c.notesChip
    NoteTint.Prayer -> c.prayerChip
    NoteTint.BibleVerse -> AppColors.ChipPair(c.noteTypeBibleVerse.copy(alpha = 0.14f), c.scriptureInk)
    NoteTint.Sermon -> c.sermonChip
    NoteTint.Video -> c.videoChip
    NoteTint.Flash -> c.flashChip
    NoteTint.Starred -> c.starredTile
    NoteTint.Default -> AppColors.ChipPair(c.neutral20, c.neutral90)
}

@Composable
fun NoteLabel.text(): String = stringResource(
    when (this) {
        NoteLabel.QuickNote -> R.string.label_quick_note
        NoteLabel.StudyNote -> R.string.tile_study_note
        NoteLabel.Prayers -> R.string.label_prayers
        NoteLabel.SermonNote -> R.string.label_sermon_note
        NoteLabel.YouTubeVideo -> R.string.filter_youtube_video
        NoteLabel.FlashCard -> R.string.label_flash_card
        NoteLabel.PersonalGrowth -> R.string.label_personal_growth
        NoteLabel.Starred -> R.string.tile_starred
        NoteLabel.Image -> R.string.tile_image
    },
)

@Composable
fun NoteBadge.label(): String = when (val t = title) {
    is NoteBadgeTitle.Reference -> t.text
    is NoteBadgeTitle.Label -> t.label.text()
    is NoteBadgeTitle.Chapter -> stringResource(R.string.label_chapter) + " · " + t.ref
}

/** The short labels of the type chips row (design L §1.2). */
@Composable
fun NoteFilter.chipLabel(): String = stringResource(
    when (this) {
        NoteFilter.All -> R.string.filter_all
        NoteFilter.QuickNote -> R.string.chip_notes
        NoteFilter.StudyNote -> R.string.chip_study
        NoteFilter.Prayer -> R.string.chip_prayer
        NoteFilter.AnsweredPrayer -> R.string.chip_answered
        NoteFilter.Pinned -> R.string.chip_pinned
        NoteFilter.Image -> R.string.chip_image
        NoteFilter.Bible -> R.string.chip_bible
        NoteFilter.Sermon -> R.string.chip_sermon
        NoteFilter.YouTubeVideo -> R.string.chip_video
        NoteFilter.Starred -> R.string.chip_starred
    },
)

/** iOS `NoteFilterUI.iconContent` (emoji). */
fun NoteFilter.icon(): String = when (this) {
    NoteFilter.All -> ""
    NoteFilter.QuickNote -> NoteBadges.typeIcon(NoteType.Note)
    NoteFilter.StudyNote -> NoteBadges.typeIcon(NoteType.StudyNote)
    NoteFilter.Prayer -> NoteBadges.typeIcon(NoteType.Prayer)
    NoteFilter.AnsweredPrayer -> "🙌"
    NoteFilter.Pinned -> "📌"
    NoteFilter.Image -> NoteBadges.typeIcon(NoteType.Image)
    NoteFilter.Bible -> NoteBadges.BIBLE_ICON
    NoteFilter.Sermon -> NoteBadges.typeIcon(NoteType.SermonNote)
    NoteFilter.YouTubeVideo -> NoteBadges.typeIcon(NoteType.YouTubeVideo)
    NoteFilter.Starred -> "⭐"
}

/** The type chip: emoji + name on the type's chip colors, fully rounded (design §1.1). */
@Composable
fun TypeChip(badge: NoteBadge, modifier: Modifier = Modifier, size: Int = 12) {
    val pair = badge.tint.chip(AppTheme.colors)
    Pill(pair.background, modifier) {
        Text("${badge.icon} ${badge.label()}", style = textStyle(size, weight = FontWeight.Bold), color = pair.foreground, maxLines = 1)
        if (badge.sermonMark) Text(" " + NoteBadges.typeIcon(NoteType.SermonNote), style = textStyle(size))
    }
}

/** "🙌 Answered" on the answered tile color with the darker answered ink. */
@Composable
fun AnsweredChip(size: Int = 12) {
    Pill(AppTheme.colors.answeredPrayerTile.background) {
        Text("🙌 " + stringResource(R.string.answered), style = textStyle(size, weight = FontWeight.Bold), color = AppTheme.colors.answeredInk, maxLines = 1)
    }
}

@Composable
fun Pill(color: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = color, modifier = modifier) {
        Row(Modifier.padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** A tag pill: the name on the page background. [ownerId] is the note or comment it labels. */
@Composable
fun TagPill(name: String, compact: Boolean = false, ownerId: String? = null) {
    // iOS: a preset chip is a button that plays its effect again (only Transform has one here).
    val replays = com.stoneofhope.stoneofhope.data.tag.PresetTag.of(name) == com.stoneofhope.stoneofhope.data.tag.PresetTag.Transform
    Surface(shape = RoundedCornerShape(50), color = AppTheme.colors.pageBackground, modifier = Modifier.transformChip(ownerId, replays)) {
        Text(
            com.stoneofhope.stoneofhope.data.tag.PresetTag.label(name),
            style = textStyle(12),
            color = AppTheme.colors.neutral90,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = if (compact) 7.dp else 9.dp, vertical = if (compact) 1.dp else 3.dp),
        )
    }
}

/** 44 round button, white with a `neutral20` hairline (design §3.1 back / "⋯"). */
@Composable
fun RoundButton(onClick: () -> Unit, description: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = AppTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = c.surface0,
        contentColor = c.neutral100,
        border = BorderStroke(1.dp, c.neutral20),
        modifier = modifier.size(44.dp).semantics { contentDescription = description },
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

/** iOS `StarIconView`: 1–3 filled stars, or one outline star for none. */
@Composable
fun StarIcons(level: Int, modifier: Modifier = Modifier, size: Dp = 13.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        if (level <= 0) {
            Icon(Icons.Outlined.StarOutline, null, tint = AppTheme.colors.neutral90, modifier = Modifier.size(size))
        } else {
            repeat(level.coerceAtMost(3)) { Icon(Icons.Filled.Star, null, tint = AppTheme.colors.starredTile.foreground, modifier = Modifier.size(size)) }
        }
    }
}

@Composable
fun threadLabel(isAnchor: Boolean, count: Int): String = when {
    isAnchor && count == 0 -> stringResource(R.string.no_reflections_short)
    isAnchor -> pluralStringResource(R.plurals.reflections_count, count, count)
    count == 0 -> stringResource(R.string.no_comments_short)
    else -> pluralStringResource(R.plurals.comments_count, count, count)
}

// MARK: - Dates (localized skeletons, so Chinese reads "10月26日 周一")

private fun pattern(skeleton: String): DateTimeFormatter {
    val locale = Locale.getDefault()
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

/** "Mon, Oct 26". */
fun weekdayMonthDay(day: LocalDate): String = pattern("EEEMMMd").format(day)

/** "Oct 12". */
fun monthDay(day: LocalDate): String = pattern("MMMd").format(day)

/** "Mon" (the comments date column). */
fun weekdayShort(day: LocalDate): String = pattern("EEE").format(day)

/** "Oct 20 – 26", "Sep 28 – Oct 3"; with years when the run is not all in this year. */
fun dayRange(start: LocalDate, end: LocalDate, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val skeleton = if (start.year == today.year && end.year == today.year) "MMMd" else "yMMMd"
    val millis = { d: LocalDate -> d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli() }
    return DateIntervalFormat.getInstance(skeleton, Locale.getDefault()).format(DateInterval(millis(start), millis(end)))
}

/** "Nov" (iOS `.dateTime.month(.abbreviated)`). */
fun monthShort(day: LocalDate): String = pattern("MMM").format(day)

/** iOS `monthYearTitle`: "October 2026", "2026年10月". */
fun monthYearTitle(month: YearMonth): String = pattern("yMMMM").format(month.atDay(1))


/** "7:15 AM". */
fun clockTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone).format(instant)

/** "Mon, Oct 12, 2026 · 7:15 AM" (details app bar). */
fun fullDateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    pattern("EEEMMMdy").format(instant.atZone(zone)) + " · " + clockTime(instant, zone)

/** A card's time: the clock on its own day, "Oct 12" when shown under another day (pinned / commented). */
fun cardTime(instant: Instant, sectionDay: LocalDate?, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = instant.atZone(zone).toLocalDate()
    return if (sectionDay == null || day == sectionDay) clockTime(instant, zone) else monthDay(day)
}

/** iOS `relativeOrTimestampDisplayString`: relative within 24 hours, else short date + time. */
@Composable
fun relativeTime(instant: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val elapsed = Duration.between(instant, now)
    if (elapsed.isNegative || elapsed >= Duration.ofHours(24)) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(zone).format(instant)
    }
    val hours = elapsed.toHours().toInt()
    if (hours >= 1) return pluralStringResource(R.plurals.hours_ago, hours, hours)
    val minutes = elapsed.toMinutes().toInt()
    if (minutes >= 1) return pluralStringResource(R.plurals.minutes_ago, minutes, minutes)
    return stringResource(R.string.time_just_now)
}
