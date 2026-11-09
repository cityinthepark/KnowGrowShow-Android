package com.stoneofhope.stoneofhope.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.IndexedTextEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.notes.NoteRelations
import com.stoneofhope.stoneofhope.data.notes.NoteThread
import com.stoneofhope.stoneofhope.data.notes.ResolvedVerse
import com.stoneofhope.stoneofhope.data.notes.ThreadDay
import com.stoneofhope.stoneofhope.data.notes.ThreadItem
import com.stoneofhope.stoneofhope.data.notes.VerseDisplay
import com.stoneofhope.stoneofhope.data.notes.VerseMarks
import com.stoneofhope.stoneofhope.data.notes.threadItem
import com.stoneofhope.stoneofhope.data.repository.NoteActionRepository
import com.stoneofhope.stoneofhope.data.repository.NoteRepository
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NoteDetailsState(
    val card: NoteCardUi? = null,
    /** The verse area: an anchor's own verses, or the verse a prayer / note is linked to. */
    val verse: ResolvedVerse? = null,
    /** An anchor's verses in the other selected versions (iOS shows every selected version). */
    val moreVersions: List<ResolvedVerse> = emptyList(),
    /** Comments by day with pinned runs and the answered day; under a verse anchor, its reflections. */
    val thread: List<ThreadDay> = emptyList(),
    /** Comments and reflections, iOS `threadItemCount` ([NoteThread.count]). */
    val threadCount: Int = 0,
    val allTags: List<TagEntity> = emptyList(),
    val loaded: Boolean = false,
    /** Missing or deleted (e.g. right after "Delete"): the screen closes. */
    val gone: Boolean = false,
) {
    fun actionTarget(id: String): NoteActionTarget? = card?.takeIf { it.note.id == id }?.actionTarget()

    fun threadItem(id: String): ThreadItem? = thread.firstNotNullOfOrNull { day -> day.items.firstOrNull { it.id == id } }
}

/** iOS `NoteDetailsView`, redesigned (docs/design/note-details). Argument: [ARG_NOTE_ID]. */
@HiltViewModel
class NoteDetailsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val db: AppDatabase,
    private val notes: NoteRepository,
    private val actions: NoteActionRepository,
    bibleVersions: BibleVersionPreferences,
) : ViewModel() {

    private val noteId: String = checkNotNull(savedState[ARG_NOTE_ID])
    private val selectedVersions = bibleVersions.selectedVersions()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private class Context(
        val allNotes: List<NoteEntity>,
        val tags: List<TagEntity>,
        val sermonIds: Set<String>,
        val verses: Map<String, UserVerseEntity>,
        val marks: Map<String, List<IndexedTextEntity>>,
    )

    private val context = combine(
        db.noteDao().observeActive(),
        db.tagDao().observeActive(),
        db.sermonDao().observeActiveIds(),
        db.userVerseDao().observeAll(),
        db.indexedTextDao().observeActiveVerseMarks(),
    ) { n, t, s, v, m -> Context(n, t, s.toSet(), v.associateBy { it.id }, VerseMarks.byVerseId(m)) }

    val state: StateFlow<NoteDetailsState> = combine(
        db.noteDao().observeById(noteId),
        db.noteCommentDao().observeActiveForNote(noteId),
        db.pinLogDao().observeByNoteId(noteId),
        context,
    ) { note, comments, pinLog, ctx ->
        if (note == null || note.deletedAt != null || note.isDeleted) return@combine NoteDetailsState(loaded = true, gone = true)
        val tagNames = ctx.tags.associate { it.id to it.name }
        val card = NoteCards.card(note, ctx.allNotes, comments, tagNames, ctx.sermonIds, ctx.verses, zone, ctx.marks, selectedVersions)
        val blocks = VerseDisplay.versionBlocks(note, selectedVersions, ctx.verses, ctx.marks)
        val verse = blocks.firstOrNull()?.resolved
            ?: VerseDisplay.resolve(note, ctx.verses, ctx.marks)?.takeIf { note.noteType == NoteType.BibleVerse || it.combinedContent.isNotBlank() }
        val items = if (note.noteType == NoteType.BibleVerse) {
            NoteRelations.reflections(note, ctx.allNotes, zone).map {
                ThreadItem(it.id, it.content, false, it.createdAt, it.starLevel, it.tagIds, it.tagIds.mapNotNull(tagNames::get), isReflection = true)
            }
        } else {
            comments.map { it.threadItem(tagNames) }
        }
        // iOS `noteTimelineRows`: the answered day is `answeredAt ?? updatedAt` while answered.
        val answeredAt = if (note.isAnswered == true) note.answeredAt ?: note.updatedAt else null
        val days = NoteThread.build(items, pinLog?.pinnedDays.orEmpty(), answeredAt, zone)
        NoteDetailsState(card, verse, blocks.drop(1).map { it.resolved }, days, NoteThread.count(items), ctx.tags, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteDetailsState())

    init {
        // iOS: refresh today's pin ledger on appear; verse-linked notes fill their verse text.
        viewModelScope.launch {
            actions.refreshTodayPinLog(zone)
            notes.ensureUserVerses(noteId)
        }
    }

    fun onAction(note: NoteEntity, action: NoteAction) {
        viewModelScope.launch { NoteActionDispatcher.dispatch(actions, note, action, zone = zone) }
    }

    /** Comment / reflection actions (design C4). */
    fun onThreadAction(item: ThreadItem, action: NoteAction) {
        viewModelScope.launch { NoteActionDispatcher.dispatchThread(actions, db, item, action, zone) }
    }

    companion object {
        const val ARG_NOTE_ID = "noteId"
    }
}
