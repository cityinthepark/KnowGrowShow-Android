package com.stoneofhope.stoneofhope.ui.sermon

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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.notes.NoteBadges
import com.stoneofhope.stoneofhope.data.notes.NoteRelations
import com.stoneofhope.stoneofhope.data.notes.VerseDisplay
import com.stoneofhope.stoneofhope.data.notes.anchoredVerseRef
import com.stoneofhope.stoneofhope.data.notes.bibleVerseRef
import com.stoneofhope.stoneofhope.data.notes.isAnsweredMarker
import com.stoneofhope.stoneofhope.data.sermon.SermonRules
import com.stoneofhope.stoneofhope.ui.notes.StarIcons
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** One block of the reader: a note, then its reflections and comments in time order. */
data class ReaderBlock(
    val note: NoteEntity,
    /** "✝️ John 3:16" for an anchor. */
    val reference: String?,
    /** The verse text for an anchor (iOS shows the "·" placeholder: IOS_ISSUES K15), else the note. */
    val body: String,
    val lines: List<String>,
)

data class ReaderState(val sermon: SermonEntity? = null, val blocks: List<ReaderBlock> = emptyList(), val loaded: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SermonReaderViewModel @Inject constructor(
    savedState: SavedStateHandle,
    db: AppDatabase,
    private val audio: SermonAudioPlayer,
) : ViewModel() {
    val sermonId: String = checkNotNull(savedState[SermonViewModel.ARG_ID])
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val notes = db.noteDao().observeForSermons(listOf(sermonId))
    private val comments = notes.flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyList()) else db.noteCommentDao().observeActiveForNotes(list.map { it.id })
    }

    val state: StateFlow<ReaderState> = combine(db.sermonDao().observeById(sermonId), notes, comments, db.userVerseDao().observeAll()) { s, list, c, verses ->
        val versesById = verses.associateBy { it.id }
        val blocks = list.filter { !NoteRelations.isHiddenFromTopLevel(it, list, zone) }.sortedBy { it.createdAt }.map { note ->
            val isAnchor = note.noteType == NoteType.BibleVerse
            // Reflections (anchors) and comments, oldest first, like the rest of the reader.
            val reflections = NoteRelations.reflections(note, list, zone).map { it.createdAt to it.content }
            val noteComments = c.filter { it.noteId == note.id && !it.isAnsweredMarker }.map { it.createdAt to it.content }
            ReaderBlock(
                note = note,
                reference = if (isAnchor) (note.bibleVerseRef ?: note.anchoredVerseRef) else null,
                body = VerseDisplay.displayContent(note, versesById),
                lines = (reflections + noteComments).sortedBy { it.first }.map { it.second },
            )
        }
        ReaderState(s?.takeIf { it.deletedAt == null }, blocks, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderState())

    fun playAudio(s: SermonEntity) {
        val url = s.audioUrl?.trim()?.ifEmpty { null } ?: return
        audio.play(url, s.title, s.chapterRange?.trim()?.ifEmpty { null }, s.preacher, s.id)
    }
}

/**
 * iOS `NoteReaderSermonView`: a read-only page of a past sermon's notes (Today's "Notes" on another
 * day). Listen / Watch go to the sermon page like everywhere else; "Edit Notes" opens it too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SermonReaderScreen(
    onBack: () -> Unit,
    onOpenSermon: (sermonId: String, entry: VideoEntry) -> Unit,
    viewModel: SermonReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val context = LocalContext.current
    val s = state.sermon
    if (s == null) {
        MissingPage(stringResource(R.string.sermon_not_found), loaded = state.loaded, onBack = onBack)
        return
    }
    Column(Modifier.fillMaxSize().background(c.pageBackground).safeDrawingPadding()) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.reader_title), fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            actions = { TextButton(onClick = { onOpenSermon(s.id, VideoEntry.Default) }) { Text(stringResource(R.string.reader_edit_notes)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = c.pageBackground),
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.title, style = textStyle(20, weight = FontWeight.Bold), color = c.neutral100, modifier = Modifier.weight(1f))
                if (!s.audioUrl.isNullOrBlank()) {
                    IconButton(onClick = { viewModel.playAudio(s); onOpenSermon(s.id, VideoEntry.Suppressed) }) {
                        Icon(Icons.Outlined.Headphones, stringResource(R.string.sermon_listen), tint = c.blue80)
                    }
                }
                if (SermonRules.youTubeID(s) != null) {
                    IconButton(onClick = { onOpenSermon(s.id, VideoEntry.Autoplay) }) {
                        Icon(Icons.Outlined.SmartDisplay, stringResource(R.string.sermon_watch), tint = c.red80)
                    }
                }
                s.pdfUrl?.trim()?.ifEmpty { null }?.let { pdf ->
                    IconButton(onClick = { openUrl(context, pdf) }) { Icon(Icons.Outlined.Description, stringResource(R.string.sermon_read), tint = c.blue80) }
                }
            }
            s.chapterRange?.trim()?.ifEmpty { null }?.let { ReferenceChip(it) }
        }
        HorizontalDivider(color = c.neutral20)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f).testTag("readerList"),
        ) {
            items(state.blocks, key = { it.note.id }) { block -> ReaderBlockCard(block) }
            if (state.blocks.isEmpty()) {
                item { Text(stringResource(R.string.notes_empty), color = c.neutral90) }
            }
        }
    }
}

@Composable
private fun ReaderBlockCard(block: ReaderBlock) {
    val c = AppTheme.colors
    val note = block.note
    val fill = when {
        note.noteType == NoteType.BibleVerse -> c.noteTypeBibleVerse.copy(alpha = 0.08f)
        note.starLevel > 0 -> c.yellow40
        else -> c.surface0
    }
    Surface(shape = RoundedCornerShape(20.dp), color = fill, border = BorderStroke(1.dp, c.neutral20), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (note.starLevel > 0) StarIcons(note.starLevel)
            block.reference?.let { Text("✝️ $it", style = textStyle(13, weight = FontWeight.SemiBold), color = c.noteTypeBibleVerse) }
            Row {
                if (note.noteType != NoteType.BibleVerse) {
                    Text(NoteBadges.typeIcon(note.noteType), style = textStyle(15))
                    Spacer(Modifier.width(6.dp))
                }
                Text(block.body, style = textStyle(15, 1.5f, serif = note.noteType == NoteType.BibleVerse), color = c.neutral100)
            }
            block.lines.forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 3.dp)) { Icon(Icons.Outlined.ChatBubbleOutline, null, tint = c.neutral90, modifier = Modifier.size(14.dp)) }
                    Spacer(Modifier.width(6.dp))
                    Text(line, style = textStyle(14, 1.4f), color = c.neutral100)
                }
            }
        }
    }
}
