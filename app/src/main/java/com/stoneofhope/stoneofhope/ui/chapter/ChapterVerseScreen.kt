package com.stoneofhope.stoneofhope.ui.chapter

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.notes.NoteBadges
import com.stoneofhope.stoneofhope.ui.notes.CommentActionsHost
import com.stoneofhope.stoneofhope.ui.notes.Composer
import com.stoneofhope.stoneofhope.ui.notes.NoteActionsHost
import com.stoneofhope.stoneofhope.ui.notes.NoteCardUi
import com.stoneofhope.stoneofhope.ui.notes.Pill
import com.stoneofhope.stoneofhope.ui.notes.RoundButton
import com.stoneofhope.stoneofhope.ui.notes.Thread
import com.stoneofhope.stoneofhope.ui.notes.VerseArea
import com.stoneofhope.stoneofhope.ui.notes.capsStyle
import com.stoneofhope.stoneofhope.ui.notes.rememberCommentActionsState
import com.stoneofhope.stoneofhope.ui.notes.rememberNoteActionsState
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.theme.AppTheme

/**
 * iOS `ChapterNoteDetailsView` in the note details style: the verse, its reflections by day (from
 * every anchor on it: H1) and its prayers; the composer writes a reflection or a prayer on this verse.
 */
@Composable
fun ChapterVerseScreen(onBack: () -> Unit, onOpenNote: (noteId: String) -> Unit, viewModel: ChapterVerseViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = rememberNoteActionsState()
    val commentActions = rememberCommentActionsState()
    val composerFocus = remember { FocusRequester() }
    var mode by rememberSaveable { mutableStateOf(VerseEntryMode.Reflection) }
    val c = AppTheme.colors
    state.textSourceId?.let { id -> LaunchedEffect(id) { viewModel.ensureVerses(id) } }

    Box(Modifier.fillMaxSize().background(c.pageBackground)) {
        // Android 12+ blurs the page behind a lifted reflection (design C4); older versions only dim it.
        Column(Modifier.fillMaxSize().statusBarsPadding().then(if (commentActions.isLifted) Modifier.blur(3.dp) else Modifier)) {
            TopBar(state.refLabel, showMenu = state.anchor != null, onBack = onBack, onMenu = { state.anchor?.let { actions.open(it.note.id) } })
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
                modifier = Modifier.weight(1f).testTag("chapterVersePage"),
            ) {
                item(key = "verse") { VerseCard(state) }
                if (state.reflectionCount > 0) {
                    item(key = "reflections") {
                        Thread(isAnchor = true, days = state.thread, count = state.reflectionCount, actions = commentActions, onAction = viewModel::onThreadAction)
                    }
                }
                if (state.prayers.isNotEmpty()) {
                    item(key = "prayers") { Prayers(state.prayers, onOpenNote, onMenu = actions::open) }
                }
                if (state.loaded && state.reflectionCount == 0 && state.prayers.isEmpty()) {
                    item(key = "empty") {
                        Text(stringResource(R.string.verse_page_empty), style = textStyle(14), color = c.neutral90, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
            Composer(
                hint = stringResource(mode.hint),
                canMarkAnswered = false,
                focusRequester = composerFocus,
                onSend = { text, _ -> viewModel.send(text, mode) },
                top = { ModeSwitch(mode) { mode = it } },
            )
        }
        CommentActionsHost(
            state = commentActions,
            item = commentActions.itemId?.let(state::threadItem),
            allTags = state.allTags,
            onAction = viewModel::onThreadAction,
        )
    }

    NoteActionsHost(
        state = actions,
        target = actions.noteId?.let(state::actionTarget),
        allTags = state.allTags,
        onAction = viewModel::onAction,
    )
}

/** Back, the verse chip, and "⋯" for the newest anchor (iOS: the verse note's actions). */
@Composable
private fun TopBar(refLabel: String, showMenu: Boolean, onBack: () -> Unit, onMenu: () -> Unit) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(onClick = onBack, description = stringResource(R.string.back)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (refLabel.isNotEmpty()) {
                Pill(c.noteTypeBibleVerse.copy(alpha = 0.14f)) {
                    Text("${NoteBadges.BIBLE_ICON} $refLabel", style = textStyle(13, weight = FontWeight.Bold), color = c.scriptureInk, maxLines = 1)
                }
            }
        }
        if (showMenu) {
            RoundButton(onClick = onMenu, description = stringResource(R.string.action_more), modifier = Modifier.testTag("verseMenu")) {
                Icon(Icons.Filled.MoreHoriz, null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** The verse in a card; just its reference while the text is not known. */
@Composable
private fun VerseCard(state: ChapterVerseState) {
    val c = AppTheme.colors
    Surface(shape = RoundedCornerShape(24.dp), color = c.surface0, border = BorderStroke(1.dp, c.neutral20), modifier = Modifier.fillMaxWidth()) {
        val verse = state.verse
        if (verse != null) {
            Column { VerseArea(verse, foldable = false, divider = false, moreVersions = state.moreVersions) }
        } else {
            Column(Modifier.fillMaxWidth().background(c.noteTypeBibleVerse.copy(alpha = 0.06f)).padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("${NoteBadges.BIBLE_ICON} " + state.refLabel.uppercase(), style = capsStyle(0.08f), color = c.scriptureInk)
            }
        }
    }
}

/** iOS "Prayers (N)": newest first; tap opens the prayer, "⋯" its actions. */
@Composable
private fun Prayers(prayers: List<NoteCardUi>, onOpenNote: (String) -> Unit, onMenu: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.prayers_title), prayers.size)
        prayers.forEach { card -> DatedNoteCard(card, onOpen = { onOpenNote(card.note.id) }, onMenu = { onMenu(card.note.id) }) }
    }
}

/** iOS `ChapterNoteEntrySheet`'s segmented control, here above the composer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitch(mode: VerseEntryMode, onChange: (VerseEntryMode) -> Unit) {
    val modes = VerseEntryMode.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
        modes.forEachIndexed { i, m ->
            SegmentedButton(
                selected = m == mode,
                onClick = { onChange(m) },
                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                modifier = Modifier.testTag("mode_${m.name}"),
            ) { Text(stringResource(m.title)) }
        }
    }
}
