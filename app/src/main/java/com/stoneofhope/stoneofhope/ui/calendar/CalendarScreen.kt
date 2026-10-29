package com.stoneofhope.stoneofhope.ui.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.notes.NoteFilter
import com.stoneofhope.stoneofhope.data.today.CalendarGrid
import com.stoneofhope.stoneofhope.data.today.DayMarks
import com.stoneofhope.stoneofhope.data.today.TodayActivity.TileKind
import com.stoneofhope.stoneofhope.ui.chapter.ChapterTab
import com.stoneofhope.stoneofhope.ui.sermon.SermonNavigation
import com.stoneofhope.stoneofhope.ui.notes.chipLabel
import com.stoneofhope.stoneofhope.ui.notes.icon
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import com.stoneofhope.stoneofhope.ui.theme.CalendarColors
import com.stoneofhope.stoneofhope.ui.today.TodayActivitySection
import com.stoneofhope.stoneofhope.ui.today.noteFilter
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * iOS `CalendarSummaryView`: a month with each day's bars (and sermon / video tints) on top; below it
 * "Summary for …" with Review and that day's Today activity, narrowed by the type menu.
 */
@Composable
fun CalendarScreen(
    nav: SermonNavigation,
    onOpenChapter: (bookOrd: Int, chapter: Int, tab: ChapterTab) -> Unit,
    onOpenNotes: (NoteFilter, LocalDate) -> Unit,
    onOpenReview: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val today by viewModel.today.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    val c = AppTheme.colors

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDay()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().background(c.pageBackground)) {
        MonthHeader(
            month = month,
            filter = filter,
            canGoNext = month < YearMonth.from(today),
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            onToday = viewModel::goToToday,
            onFilter = viewModel::setFilter,
        )
        MonthGrid(month, today, selected, filter, marks, onSelect = viewModel::select)
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = c.neutral20)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.calendar_summary_for, DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(selected)),
                style = MaterialTheme.typography.titleMedium,
                color = c.neutral100,
                modifier = Modifier.weight(1f).clickable(onClick = viewModel::showSelectedMonth).testTag("calendarSummaryTitle"),
            )
            if (activity.showReview) {
                AssistChip(
                    onClick = { onOpenReview(selected) },
                    label = { Text("▶ " + stringResource(R.string.today_review)) },
                    modifier = Modifier.testTag("calendarReview"),
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 24.dp)) {
            // Only when the day really is empty: under a type filter it may just hold other kinds.
            if (activity.isEmpty && filter == NoteFilter.All) {
                Text(
                    stringResource(R.string.calendar_day_empty),
                    style = textStyle(15),
                    color = c.neutral90,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp).testTag("calendarDayEmpty"),
                )
            }
            TodayActivitySection(
                activity = activity,
                nav = nav,
                onOpenChapter = onOpenChapter,
                onOpenTile = { kind -> onOpenNotes(kind.noteFilter, selected) },
                onReview = { onOpenReview(selected) },
                onUnread = viewModel::toggleChapterRead,
                showsHeader = false,
                isToday = selected == today,
            )
        }
    }
}

/** ‹  [Today]  Oct 2026  [type ▾]  › (iOS `CustomCalendarView` header). */
@Composable
private fun MonthHeader(
    month: YearMonth,
    filter: NoteFilter,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onFilter: (NoteFilter) -> Unit,
) {
    val cal = AppTheme.colors.calendar
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.testTag("calendarPrevious")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.calendar_previous_month))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onToday, shape = RoundedCornerShape(50), color = cal.todayButton, modifier = Modifier.testTag("calendarToday")) {
                Text(
                    stringResource(R.string.notes_title_today),
                    style = textStyle(14, weight = FontWeight.SemiBold),
                    color = cal.todayButtonText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Text(monthTitle(month), style = textStyle(17, weight = FontWeight.SemiBold), color = AppTheme.colors.neutral100, maxLines = 1, modifier = Modifier.testTag("calendarMonth"))
            FilterMenu(filter, onFilter)
        }
        IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.testTag("calendarNext")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.calendar_next_month))
        }
    }
}

/** "Oct 2026" / "2026年10月". */
private fun monthTitle(month: YearMonth): String {
    val locale = Locale.getDefault()
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMM"), locale).format(month.atDay(1))
}

/** iOS `CalendarTypeFilterMenu`: every type but Sermon (sermon days are tinted instead). */
@Composable
private fun FilterMenu(selected: NoteFilter, onSelect: (NoteFilter) -> Unit) {
    val cal = AppTheme.colors.calendar
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { open = true }, shape = RoundedCornerShape(50), color = cal.todayButton.copy(alpha = 0.65f), modifier = Modifier.testTag("calendarFilter")) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (selected != NoteFilter.All) Text(selected.icon(), fontSize = 12.sp)
                Text(selected.chipLabel(), style = textStyle(12, weight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary, maxLines = 1)
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            NoteFilter.entries.filter { it != NoteFilter.Sermon }.forEach { f ->
                DropdownMenuItem(
                    text = { Text(listOf(f.icon(), f.chipLabel()).filter { it.isNotEmpty() }.joinToString("  ")) },
                    leadingIcon = { if (f == selected) Icon(Icons.Filled.Check, null) else Spacer(Modifier.width(24.dp)) },
                    onClick = { onSelect(f); open = false },
                    modifier = Modifier.testTag("calendarFilter_${f.name}"),
                )
            }
        }
    }
}

/** Weekday names, then the weeks; the week starts where the device's locale starts it. */
@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    filter: NoteFilter,
    marks: Map<LocalDate, DayMarks>,
    onSelect: (LocalDate) -> Unit,
) {
    val c = AppTheme.colors
    val locale = Locale.getDefault()
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val weeks = remember(month, firstDay) { CalendarGrid.weeks(month, firstDay) }
    Column(Modifier.fillMaxWidth().background(c.calendar.gridBackground).padding(bottom = 4.dp).testTag("calendarGrid")) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            repeat(7) { i ->
                Text(
                    firstDay.plus(i.toLong()).getDisplayName(TextStyle.SHORT, locale),
                    style = textStyle(12),
                    color = c.neutral100.copy(alpha = 0.42f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.padding(horizontal = 4.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).heightIn(min = 44.dp))
                    } else {
                        DayCell(
                            date = date,
                            marks = marks[date],
                            isSelected = date == selected,
                            isToday = date == today,
                            isFuture = date.isAfter(today),
                            filter = filter,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** iOS `CalendarDayView`: the number (filled when selected, ringed on today) over up to three bars and "+N". */
@Composable
private fun DayCell(
    date: LocalDate,
    marks: DayMarks?,
    isSelected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    filter: NoteFilter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val bars = marks?.bars(filter).orEmpty()
    val sermon = marks?.showsSermon(filter) == true
    val video = marks?.showsVideo(filter) == true
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .heightIn(min = 44.dp)
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .clip(shape)
            .dayTint(sermon, video, c.yellow40, c.blue20)
            .then(if (isFuture) Modifier else Modifier.clickable(onClick = onClick))
            .alpha(if (isFuture) 0.5f else 1f)
            .padding(vertical = 3.dp)
            .testTag("day_$date"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) {
            when {
                isSelected -> Box(Modifier.size(22.dp).background(c.calendar.selectedDay, CircleShape))
                isToday -> Box(Modifier.size(22.dp).background(c.calendar.todayRingFill, CircleShape).border(1.5.dp, c.calendar.todayRing, CircleShape))
            }
            Text(
                "${date.dayOfMonth}",
                style = textStyle(if (isSelected) 12 else 13, weight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                color = when {
                    isSelected -> Color.White
                    isFuture -> c.neutral90
                    else -> c.neutral100
                },
            )
        }
        if (bars.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                bars.take(3).forEach { kind ->
                    Box(Modifier.fillMaxWidth().height(2.dp).background(c.calendar.bar(kind), RoundedCornerShape(1.dp)))
                }
                if (bars.size > 3) Text("+${bars.size - 3}", fontSize = 8.sp, fontWeight = FontWeight.Medium, color = c.neutral90, lineHeight = 9.sp)
            }
        }
    }
}

/** Sermon days yellow, video days blue, both: sermon on the top-left triangle (iOS `CalendarDiagonalSplitCellBackground`). */
private fun Modifier.dayTint(sermon: Boolean, video: Boolean, sermonColor: Color, videoColor: Color): Modifier = when {
    sermon && video -> drawBehind {
        drawRect(videoColor)
        drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(0f, size.height); close() }, sermonColor)
    }
    sermon -> background(sermonColor)
    video -> background(videoColor)
    else -> this
}

private fun CalendarColors.bar(kind: TileKind): Color = when (kind) {
    TileKind.QuickNote -> quickNoteBar
    TileKind.StudyNote -> studyNoteBar
    TileKind.Starred -> starredBar
    TileKind.Prayer -> prayerBar
    TileKind.AnsweredPrayer -> answeredBar
    TileKind.Pinned -> pinnedBar
    TileKind.Image -> imageBar
    TileKind.Bible -> bibleBar
}
