package com.stoneofhope.stoneofhope.ui.moments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.notes.MomentLine
import com.stoneofhope.stoneofhope.data.notes.MomentsRange
import com.stoneofhope.stoneofhope.data.notes.NoteBadges
import com.stoneofhope.stoneofhope.data.notes.NoteFilter
import com.stoneofhope.stoneofhope.ui.notes.chipLabel
import com.stoneofhope.stoneofhope.ui.notes.dayRange
import com.stoneofhope.stoneofhope.ui.notes.icon
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * iOS `MomentsTabView`: a feed of the notes in a date window (last 30 days by default), by day, with
 * type / tag / date menus on top and "View Earlier Notes" at the end. Pull to refresh syncs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsScreen(onOpenNote: (noteId: String) -> Unit, viewModel: MomentsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    var rangeSheet by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val c = AppTheme.colors

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDay()
        onPauseOrDispose { }
    }
    // A new filter, tag or window starts from the top.
    LaunchedEffect(state.filter, state.tag, state.range) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize().background(c.pageBackground)) {
        Header(state, onFilter = viewModel::setFilter, onTag = viewModel::setTag, onRange = { rangeSheet = true })
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = viewModel::refresh, modifier = Modifier.weight(1f)) {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize().testTag("moments")) {
                // Nothing until loaded: a lone footer would otherwise stay in view as the notes arrive above it.
                if (!state.loaded) return@LazyColumn
                if (state.sections.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.moments_empty),
                            style = textStyle(15),
                            color = c.neutral90,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp).testTag("momentsEmpty"),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                state.sections.forEachIndexed { index, section ->
                    item(key = "day_${section.day}") { DayHeader(section.day, first = index == 0) }
                    items(section.rows, key = { it.noteId }) { row -> MomentRow(row, onClick = { onOpenNote(row.noteId) }) }
                }
                item(key = "earlier") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { rangeSheet = true }, modifier = Modifier.testTag("momentsEarlier")) {
                            Text(stringResource(R.string.moments_view_earlier), style = textStyle(15, weight = FontWeight.Medium))
                        }
                    }
                }
            }
        }
    }

    if (rangeSheet) {
        DateRangeSheet(
            current = state.range,
            onDismiss = { rangeSheet = false },
            onApply = {
                viewModel.setRange(it)
                rangeSheet = false
            },
        )
    }
}

/** "Moments", then the type, tag and date menus (iOS puts them on one line; two fit a phone better). */
@Composable
private fun Header(state: MomentsState, onFilter: (NoteFilter) -> Unit, onTag: (String?) -> Unit, onRange: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        Text(
            stringResource(R.string.tab_moments),
            style = textStyle(22, 1.2f, FontWeight.Bold),
            color = c.neutral100,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TypeMenu(state.filter, onFilter)
            TagMenu(state.tag, state.tagNames, onTag)
            MenuPill(
                label = rangeLabel(state.range),
                leading = { Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) },
                onClick = onRange,
                tag = "momentsRange",
            )
        }
    }
}

@Composable
private fun TypeMenu(selected: NoteFilter, onSelect: (NoteFilter) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        MenuPill(
            label = selected.chipLabel(),
            leading = selected.icon().takeIf { it.isNotEmpty() }?.let { emoji -> { Text(emoji, fontSize = 14.sp) } },
            onClick = { open = true },
            tag = "momentsType",
            dropdown = true,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            NoteFilter.entries.forEach { f ->
                DropdownMenuItem(
                    text = { Text(listOf(f.icon(), f.chipLabel()).filter { it.isNotEmpty() }.joinToString("  ")) },
                    leadingIcon = { CheckSlot(f == selected) },
                    onClick = { onSelect(f); open = false },
                    modifier = Modifier.testTag("momentsType_${f.name}"),
                )
            }
        }
    }
}

@Composable
private fun TagMenu(tag: String?, tagNames: List<String>, onTag: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        MenuPill(
            label = tag ?: stringResource(R.string.filter_all),
            leading = { Icon(Icons.Outlined.Sell, null, tint = AppTheme.colors.neutral90, modifier = Modifier.size(16.dp)) },
            onClick = { open = true },
            tag = "momentsTag",
            dropdown = true,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.filter_all)) }, leadingIcon = { CheckSlot(tag == null) }, onClick = { onTag(null); open = false })
            tagNames.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, leadingIcon = { CheckSlot(name == tag) }, onClick = { onTag(name); open = false })
            }
        }
    }
}

@Composable
private fun CheckSlot(checked: Boolean) {
    if (checked) Icon(Icons.Filled.Check, null) else Spacer(Modifier.width(24.dp))
}

/** A rounded menu button (iOS `ToolbarMenuButton`). */
@Composable
private fun MenuPill(label: String, leading: (@Composable () -> Unit)?, onClick: () -> Unit, tag: String, dropdown: Boolean = false) {
    val c = AppTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = c.surface0,
        border = BorderStroke(1.dp, c.neutral20),
        modifier = Modifier.height(36.dp).testTag(tag),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            leading?.invoke()
            Text(label, style = textStyle(14, weight = FontWeight.Medium), color = c.neutral100, maxLines = 1)
            if (dropdown) Icon(Icons.Filled.KeyboardArrowDown, null, tint = c.neutral90, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun rangeLabel(range: MomentsRange): String = when (range) {
    MomentsRange.Last.Days30 -> stringResource(R.string.moments_last_30_days)
    MomentsRange.Last.Days7 -> stringResource(R.string.moments_last_7_days)
    MomentsRange.Last.Months3 -> stringResource(R.string.moments_last_3_months)
    MomentsRange.Last.Months12 -> stringResource(R.string.moments_last_12_months)
    is MomentsRange.Custom -> dayRange(minOf(range.start, range.end), maxOf(range.start, range.end))
}

/** iOS `sectionHeader(for:)`: the date in the accent colour over a hairline. */
@Composable
private fun DayHeader(day: LocalDate, first: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = if (first) 8.dp else 28.dp, bottom = 6.dp)) {
        Text(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(day),
            style = textStyle(17, weight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )
        HorizontalDivider(color = AppTheme.colors.neutral20)
    }
}

/** iOS `MomentsNoteRow`: the type icon, then up to four short lines; tap opens the note. */
@Composable
private fun MomentRow(row: MomentRowUi, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp).testTag("moment_${row.noteId}"),
    ) {
        Text(row.icon, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            row.lines.forEach { line ->
                when (line) {
                    is MomentLine.Reference -> {
                        val mark = if (line.sermonMark) "  " + NoteBadges.typeIcon(NoteType.SermonNote) else ""
                        Text(line.text + mark, style = textStyle(15, 1.35f), color = c.neutral100)
                    }
                    is MomentLine.Body -> Text(line.text, style = textStyle(15, 1.35f), color = c.neutral100)
                }
            }
        }
    }
}

/**
 * iOS `MomentsDateRangeSheet`: "Recent 30 Days", the longer presets (named for what they cover:
 * IOS_ISSUES I3), and a custom range that ends today at the latest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeSheet(current: MomentsRange, onDismiss: () -> Unit, onApply: (MomentsRange) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.date_range_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            RangeRow(stringResource(R.string.moments_last_30_days), current == MomentsRange.Last.Days30, "range_Days30") { onApply(MomentsRange.Last.Days30) }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            listOf(MomentsRange.Last.Days7, MomentsRange.Last.Months3, MomentsRange.Last.Months12).forEach { preset ->
                RangeRow(rangeLabel(preset), current == preset, "range_${preset.name}") { onApply(preset) }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val custom = current as? MomentsRange.Custom
            RangeRow(
                stringResource(R.string.date_range_custom),
                custom != null,
                "range_custom",
                supporting = custom?.let { rangeLabel(it) },
                icon = Icons.Outlined.CalendarMonth,
            ) { picking = true }
        }
    }
    if (picking) {
        CustomRangeDialog(
            initial = current as? MomentsRange.Custom,
            onDismiss = { picking = false },
            onDone = {
                picking = false
                onApply(it)
            },
        )
    }
}

@Composable
private fun RangeRow(
    title: String,
    selected: Boolean,
    tag: String,
    supporting: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            when {
                selected -> Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                icon != null -> Icon(icon, null)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp).testTag(tag),
    )
}

/** Material's date range picker; days after today can't be picked. Dates travel as UTC midnights. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(initial: MomentsRange.Custom?, onDismiss: () -> Unit, onDone: (MomentsRange.Custom) -> Unit) {
    val today = remember { LocalDate.now() }
    fun LocalDate.utcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.utcDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = (initial?.start ?: today.minusDays(30)).utcMillis(),
        initialSelectedEndDateMillis = (initial?.end ?: today).utcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = !utcTimeMillis.utcDate().isAfter(today)
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (start != null && end != null) onDone(MomentsRange.Custom(start.utcDate(), end.utcDate())) },
                enabled = start != null && end != null,
                modifier = Modifier.testTag("rangeDone"),
            ) { Text(stringResource(R.string.date_range_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}
