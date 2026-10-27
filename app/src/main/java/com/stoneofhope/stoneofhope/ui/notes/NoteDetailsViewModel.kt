package com.stoneofhope.stoneofhope.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.notes.NoteRelations
import com.stoneofhope.stoneofhope.data.notes.ResolvedVerse
import com.stoneofhope.stoneofhope.data.notes.VerseDisplay
import com.stoneofhope.stoneofhope.data.notes.isAnsweredMarker
import com.stoneofhope.stoneofhope.data.repository.NoteActionRepository
import com.stoneofhope.stoneofhope.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One comment — or, under a verse anchor, one reflection note — in the thread. */
data class ThreadItem(
    val id: String,
    val text: String,
    /** The "Answered" timeline marker (its body is an id; the UI shows a label). */
    val isAnsweredMarker: Boolean,
    val createdAt: Instant,
    val starLevel: Int,
    val tagNames: List<String>,
)

/** One day of the thread, newest first (iOS `NotePinLogBuilder.buildRows`). */
data class ThreadDay(val day: LocalDate, val items: List<ThreadItem>)

data class NoteDetailsState(
    val card: NoteCardUi? = null,
    /** The verse area: an anchor's own verses, or the verse a prayer / note is linked to. */
    val verse: ResolvedVerse? = null,
    /** Comments by day; under a verse anchor, its reflections. */
    val thread: List<ThreadDay> = emptyList(),
    /** Comments without "Answered" markers, or reflections. */
    val threadCount: Int = 0,
    val allTags: List<TagEntity> = emptyList(),
    val loaded: Boolean = false,
    /** Missing or deleted (e.g. right after "Delete"): the screen closes. */
    val gone: Boolean = false,
) {
    fun actionTarget(id: String): NoteActionTarget? = card?.takeIf { it.note.id == id }?.actionTarget()
}

/** iOS `NoteDetailsView`, redesigned (docs/design/note-details). Argument: [ARG_NOTE_ID]. */
@HiltViewModel
class NoteDetailsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val db: AppDatabase,
    private val notes: NoteRepository,
    private val actions: NoteActionRepository,
) : ViewModel() {

    private val noteId: String = checkNotNull(savedState[ARG_NOTE_ID])
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private class Context(
        val allNotes: List<NoteEntity>,
        val tags: List<TagEntity>,
        val sermonIds: Set<String>,
        val verses: Map<String, UserVerseEntity>,
    )

    private val context = combine(
        db.noteDao().observeActive(),
        db.tagDao().observeActive(),
        db.sermonDao().observeActiveIds(),
        db.userVerseDao().observeAll(),
    ) { n, t, s, v -> Context(n, t, s.toSet(), v.associateBy { it.id }) }

    val state: StateFlow<NoteDetailsState> = combine(
        db.noteDao().observeById(noteId),
        db.noteCommentDao().observeActiveForNote(noteId),
        context,
    ) { note, comments, ctx ->
        if (note == null || note.deletedAt != null || note.isDeleted) return@combine NoteDetailsState(loaded = true, gone = true)
        val tagNames = ctx.tags.associate { it.id to it.name }
        val card = NoteCards.card(note, ctx.allNotes, comments, tagNames, ctx.sermonIds, ctx.verses, zone)
        val verse = VerseDisplay.resolve(note, ctx.verses)?.takeIf { note.noteType == NoteType.BibleVerse || it.combinedContent.isNotBlank() }
        val items = if (note.noteType == NoteType.BibleVerse) {
            NoteRelations.reflections(note, ctx.allNotes, zone).map {
                ThreadItem(it.id, it.content, false, it.createdAt, it.starLevel, it.tagIds.mapNotNull(tagNames::get))
            }
        } else {
            comments.map { ThreadItem(it.id, it.content, it.isAnsweredMarker, it.createdAt, it.starLevel, it.tagIds.mapNotNull(tagNames::get)) }
        }
        val days = items.groupBy { it.createdAt.atZone(zone).toLocalDate() }.toSortedMap(compareByDescending { it })
            .map { (day, list) -> ThreadDay(day, list.sortedByDescending { it.createdAt }) }
        NoteDetailsState(card, verse, days, items.count { !it.isAnsweredMarker }, ctx.tags, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteDetailsState())

    init {
        // iOS: refresh today's pin ledger on appear; verse-linked notes fill their verse text.
        viewModelScope.launch {
            actions.refreshTodayPinLog(zone)
            notes.ensureUserVerses(noteId)
        }
    }

    fun onAction(note: NoteEntity, action: NoteAction) {
        viewModelScope.launch { NoteActionDispatcher.dispatch(actions, note, action, unmarkReferenceDay = null, zone = zone) }
    }

    companion object {
        const val ARG_NOTE_ID = "noteId"
    }
}
