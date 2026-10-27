package com.stoneofhope.stoneofhope.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.notes.NoteRelations
import com.stoneofhope.stoneofhope.data.notes.NoteReview
import com.stoneofhope.stoneofhope.data.notes.ResolvedVerse
import com.stoneofhope.stoneofhope.data.notes.ThreadItem
import com.stoneofhope.stoneofhope.data.notes.VerseDisplay
import com.stoneofhope.stoneofhope.data.notes.VerseMarks
import com.stoneofhope.stoneofhope.data.notes.isAnsweredMarker
import com.stoneofhope.stoneofhope.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One page of the review. */
data class ReviewPage(
    val card: NoteCardUi,
    /** An anchor's verses, or the verse a prayer is linked to (shown as its own card). */
    val verse: ResolvedVerse?,
    /** Reflections (newest first), then comments (oldest first), as iOS lists them. */
    val items: List<ThreadItem>,
    /** Without "Answered" markers (iOS counts them; IOS_ISSUES G8). */
    val count: Int,
)

data class NotesReviewState(val pages: List<ReviewPage> = emptyList(), val loaded: Boolean = false)

/** iOS `NotesReviewView` (Today's "Review"): one day's notes as pages. Argument: [ARG_DAY] (epoch day). */
@HiltViewModel
class NotesReviewViewModel @Inject constructor(
    savedState: SavedStateHandle,
    db: AppDatabase,
    private val notes: NoteRepository,
) : ViewModel() {

    private val day: LocalDate = LocalDate.ofEpochDay(checkNotNull(savedState.get<Long>(ARG_DAY)))
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val versesEnsured = mutableSetOf<String>()

    private val verseData = combine(db.userVerseDao().observeAll(), db.indexedTextDao().observeActiveVerseMarks()) { v, m ->
        v.associateBy { it.id } to VerseMarks.byVerseId(m)
    }

    val state: StateFlow<NotesReviewState> = combine(
        db.noteDao().observeActive(),
        db.noteCommentDao().observeActive(),
        db.tagDao().observeActive(),
        db.sermonDao().observeActiveIds(),
        verseData,
    ) { allNotes, comments, tags, sermonIds, (versesById, marks) ->
        val tagNames = tags.associate { it.id to it.name }
        val commentsByNote = comments.groupBy { it.noteId }
        val pages = NoteReview.notes(day, LocalDate.now(zone), allNotes, comments, zone).map { note ->
            val own = commentsByNote[note.id].orEmpty()
            val reflections = if (note.noteType == NoteType.BibleVerse) NoteRelations.reflections(note, allNotes, zone) else emptyList()
            val items = reflections.map {
                ThreadItem(it.id, it.content, false, it.createdAt, it.starLevel, it.tagIds, it.tagIds.mapNotNull(tagNames::get), isReflection = true)
            } + own.sortedBy { it.createdAt }.map {
                ThreadItem(it.id, it.content, it.isAnsweredMarker, it.createdAt, it.starLevel, it.tagIds, it.tagIds.mapNotNull(tagNames::get))
            }
            ReviewPage(
                card = NoteCards.card(note, allNotes, comments, tagNames, sermonIds.toSet(), versesById, zone, marks),
                verse = VerseDisplay.resolve(note, versesById, marks)
                    ?.takeIf { note.noteType == NoteType.BibleVerse || (note.noteType == NoteType.Prayer && it.combinedContent.isNotBlank()) },
                items = items,
                count = items.count { !it.isAnsweredMarker },
            )
        }
        NotesReviewState(pages, loaded = true)
    }.onEach { s ->
        // iOS: a verse-linked prayer fills its verse text when its page appears.
        s.pages.map { it.card.note }.filter { it.noteType == NoteType.Prayer && it.verseStart != null && versesEnsured.add(it.id) }
            .forEach { viewModelScope.launch { notes.ensureUserVerses(it.id) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesReviewState())

    companion object {
        const val ARG_DAY = "day"
    }
}
