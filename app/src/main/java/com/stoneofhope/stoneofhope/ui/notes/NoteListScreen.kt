package com.stoneofhope.stoneofhope.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.notes.NoteFilter
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import com.stoneofhope.stoneofhope.ui.today.NoteSheet
import com.stoneofhope.stoneofhope.ui.today.PrayerSheet
import java.time.LocalDate

private sealed interface ListItem {
    val key: String
    val section: NoteSectionUi

    data class Header(override val section: NoteSectionUi) : ListItem {
        override val key get() = if (section.pinned) "pinned" else "day_${section.day}"
    }

    data class Row(override val section: NoteSectionUi, val row: NoteRowUi) : ListItem {
        override val key get() = row.key
    }
}

private enum class Composer { Note, Prayer }

/**
 * Redesigned note list (docs/design/note-list): big day title with the result count, horizontally
 * scrolling type chips, tag button, cards by day (pinned block on top of today's "All"), and a
 * "New note" pill for today's list. Filter, sort and grouping rules are unchanged (NOTES_PLAN §1).
 */
@Composable
fun NoteListScreen(onBack: () -> Unit, onOpenNote: (String) -> Unit, viewModel: NoteListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val expanded by viewModel.expanded.collectAsStateWithLifecycle()
    val prayerVerses by viewModel.prayerVerses.collectAsStateWithLifecycle()
    val actions = rememberNoteActionsState()
    var composer by rememberSaveable { mutableStateOf<Composer?>(null) }
    val listState = rememberLazyListState()
    val c = AppTheme.colors
    val items = remember(state.sections) {
        state.sections.flatMap { s -> listOf<ListItem>(ListItem.Header(s)) + s.rows.map { ListItem.Row(s, it) } }
    }
    // The title follows the day at the top of the list (the pinned block counts as today).
    val titleDay by remember(items) {
        derivedStateOf { items.getOrNull(listState.firstVisibleItemIndex)?.section?.day ?: viewModel.day }
    }
    // A new filter or tag starts from the top (LazyColumn would otherwise keep the old top item).
    LaunchedEffect(state.filter, state.tag) { listState.scrollToItem(0) }
    val prayerMode = state.filter == NoteFilter.Prayer
    val openComposer = { composer = if (prayerMode) Composer.Prayer else Composer.Note }

    Box(Modifier.fillMaxSize().background(c.pageBackground)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Header(titleDay, viewModel.today, viewModel.day, state, prayerMode, onBack, viewModel::setTag)
            TypeChipsRow(state.filter, viewModel::setFilter)
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).testTag("noteList"),
            ) {
                items(items, key = { it.key }) { item ->
                    when (item) {
                        is ListItem.Header -> SectionHeader(item.section, viewModel.today, viewModel.versionsLabel, first = item == items.first())
                        is ListItem.Row -> {
                            val day = item.section.day.takeIf { !item.section.pinned }
                            when (val row = item.row) {
                                is NoteRowUi.Single -> {
                                    val card = row.card
                                    if (card.isAnchor) {
                                        VerseAnchorCard(card, day, onOpen = { onOpenNote(card.note.id) }, onMenu = { actions.open(card.note.id) })
                                    } else {
                                        NoteListCard(
                                            card = card,
                                            sectionDay = day,
                                            maxLines = if (item.section.pinned) 2 else 4,
                                            verseExpanded = card.note.id in expanded,
                                            verse = prayerVerses[card.note.id],
                                            onOpen = { onOpenNote(card.note.id) },
                                            onMenu = { actions.open(card.note.id) },
                                            onToggleVerse = { viewModel.togglePrayerVerse(card.note) },
                                        )
                                    }
                                }
                                is NoteRowUi.VerseGroup -> {
                                    LaunchedEffect(row.key) { viewModel.ensureVerses(row.notes.first().id) }
                                    ReflectionGroupCard(row, day, onOpenNote)
                                }
                            }
                        }
                    }
                }
                if (state.loaded && state.sections.isEmpty()) {
                    item(key = "empty") { EmptyState(viewModel.isToday, prayerMode, openComposer) }
                }
            }
        }
        if (viewModel.isToday) {
            NewNotePill(
                label = stringResource(if (prayerMode) R.string.new_prayer else R.string.new_note),
                onClick = openComposer,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 30.dp),
            )
        }
    }

    NoteActionsHost(
        state = actions,
        target = actions.noteId?.let(state::actionTarget),
        allTags = state.allTags,
        onAction = viewModel::onAction,
    )
    when (composer) {
        Composer.Note -> NoteSheet(
            bibleRefLanguages = viewModel.bibleRefLanguages,
            onDismiss = { composer = null },
            onSave = { content, ref -> viewModel.saveNote(content, ref); composer = null },
        )
        Composer.Prayer -> PrayerSheet(
            tagNames = state.tagNames,
            bibleRefLanguages = viewModel.bibleRefLanguages,
            onDismiss = { composer = null },
            onSave = { content, tag, ref -> viewModel.savePrayer(content, tag, ref); composer = null },
        )
        null -> Unit
    }
}

/** Back, the big day title with "Mon, Oct 26 · 6 notes", and the tag filter (design L §1.1). */
@Composable
private fun Header(
    titleDay: LocalDate?,
    today: LocalDate,
    listDay: LocalDate?,
    state: NoteListState,
    prayerMode: Boolean,
    onBack: () -> Unit,
    onTag: (String?) -> Unit,
) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(onClick = onBack, description = stringResource(R.string.back)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val title = when (titleDay) {
                null -> stringResource(R.string.action_notes)
                today -> stringResource(R.string.notes_title_today)
                else -> weekdayMonthDay(titleDay)
            }
            Text(title, style = textStyle(22, 1.2f, FontWeight.Bold), color = c.neutral100, maxLines = 1, modifier = Modifier.testTag("listTitle"))
            val count = if (prayerMode) pluralStringResource(R.plurals.prayers_count, state.total, state.total)
            else pluralStringResource(R.plurals.notes_count, state.total, state.total)
            val dayPart = (listDay ?: titleDay)?.let { weekdayMonthDay(it) + " · " }.orEmpty()
            Text(dayPart + count, style = textStyle(12), color = c.neutral90, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
        TagFilterButton(state.tag, state.tagNames, onTag)
    }
}

/** A 44 round tag button, or a "#tag" pill once a tag is chosen; opens the tag menu. */
@Composable
private fun TagFilterButton(tag: String?, tagNames: List<String>, onTag: (String?) -> Unit) {
    val c = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        if (tag == null) {
            RoundButton(onClick = { open = true }, description = stringResource(R.string.tag_filter), modifier = Modifier.testTag("tagFilter")) {
                Icon(Icons.Outlined.Sell, null, modifier = Modifier.size(20.dp))
            }
        } else {
            Surface(
                onClick = { open = true },
                shape = RoundedCornerShape(50),
                color = c.prayerChip.background,
                border = BorderStroke(1.dp, c.blue100),
                modifier = Modifier.height(44.dp).testTag("tagFilter"),
            ) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Sell, null, tint = c.blue100, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("#$tag", style = textStyle(15, weight = FontWeight.SemiBold), color = c.blue100, maxLines = 1)
                }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_all)) },
                leadingIcon = { if (tag == null) Icon(Icons.Filled.Check, null) else Spacer(Modifier.width(24.dp)) },
                onClick = { onTag(null); open = false },
            )
            tagNames.forEach { name ->
                DropdownMenuItem(
                    text = { Text("#$name") },
                    leadingIcon = { if (name == tag) Icon(Icons.Filled.Check, null) else Spacer(Modifier.width(24.dp)) },
                    onClick = { onTag(name); open = false },
                )
            }
        }
    }
}

/** Horizontally scrolling type chips (design L §1.2); the selected one scrolls into view. */
@Composable
private fun TypeChipsRow(selected: NoteFilter, onSelect: (NoteFilter) -> Unit) {
    val c = AppTheme.colors
    val rowState = rememberLazyListState()
    // Only scroll when the chosen chip is (partly) off screen, keeping its left neighbour in view.
    LaunchedEffect(selected) {
        val info = rowState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == selected.ordinal }
        if (item == null || item.offset < info.viewportStartOffset || item.offset + item.size > info.viewportEndOffset) {
            rowState.animateScrollToItem((selected.ordinal - 1).coerceAtLeast(0))
        }
    }
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp).testTag("typeChips"),
    ) {
        items(NoteFilter.entries, key = { it.name }) { f ->
            val isSelected = f == selected
            Surface(
                onClick = { onSelect(f) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) c.neutral100 else c.surface0,
                border = if (isSelected) null else BorderStroke(1.dp, c.neutral20),
                modifier = Modifier.height(36.dp).testTag("filter_${f.name}"),
            ) {
                Box(Modifier.padding(horizontal = if (f == NoteFilter.All) 14.dp else 12.dp), contentAlignment = Alignment.Center) {
                    val label = listOf(f.icon(), f.chipLabel()).filter { it.isNotEmpty() }.joinToString(" ")
                    Text(
                        label,
                        style = textStyle(14, weight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (isSelected) c.surface0 else c.neutral100,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

/** "TODAY · MON, OCT 26" + versions, or "📌 PINNED" (design L §2.1). */
@Composable
private fun SectionHeader(section: NoteSectionUi, today: LocalDate, versions: String, first: Boolean) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = if (first) 0.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
        if (section.pinned) {
            Text("📌 " + stringResource(R.string.pinned_section).uppercase(), style = capsStyle(0.06f), color = c.pinnedTile.foreground)
        } else {
            val day = weekdayMonthDay(section.day).uppercase()
            val text = if (section.day == today) stringResource(R.string.notes_title_today).uppercase() + " · " + day else day
            Text(text, style = capsStyle(0.06f), color = c.neutral90, modifier = Modifier.weight(1f))
            // iOS opens the version settings from here; Android has no such page yet.
            Text(versions, style = textStyle(12, weight = FontWeight.SemiBold), color = c.neutral90)
        }
    }
}

@Composable
private fun EmptyState(isToday: Boolean, prayerMode: Boolean, onNew: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val text = when {
            !isToday -> stringResource(R.string.notes_empty)
            prayerMode -> stringResource(R.string.empty_prayers_today)
            else -> stringResource(R.string.empty_notes_today)
        }
        Text(text, style = textStyle(15), color = c.neutral90)
        if (isToday) {
            OutlinedButton(onClick = onNew) {
                Text("＋ " + stringResource(if (prayerMode) R.string.new_prayer else R.string.new_note))
            }
        }
    }
}

/** The bottom-centre "＋ New note" pill (design L §3). */
@Composable
private fun NewNotePill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(26.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = c.blue80,
        contentColor = Color.White,
        modifier = modifier.shadow(12.dp, shape, ambientColor = c.blue80.copy(alpha = 0.3f), spotColor = c.blue80.copy(alpha = 0.3f)).height(52.dp).testTag("addNote"),
    ) {
        Row(Modifier.padding(start = 18.dp, end = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(label, style = textStyle(16, weight = FontWeight.SemiBold))
        }
    }
}
