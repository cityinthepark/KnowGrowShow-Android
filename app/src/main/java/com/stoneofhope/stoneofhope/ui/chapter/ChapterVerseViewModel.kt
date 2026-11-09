package com.stoneofhope.stoneofhope.ui.chapter

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.chapter.ChapterNotesBuilder
import com.stoneofhope.stoneofhope.data.chapter.ChapterVerse
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.notes.NoteThread
import com.stoneofhope.stoneofhope.data.notes.ResolvedVerse
import com.stoneofhope.stoneofhope.data.notes.ThreadDay
import com.stoneofhope.stoneofhope.data.notes.ThreadItem
import com.stoneofhope.stoneofhope.data.repository.NoteActionRepository
import com.stoneofhope.stoneofhope.data.repository.NoteRepository
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.ui.notes.NoteAction
import com.stoneofhope.stoneofhope.ui.notes.NoteActionDispatcher
import com.stoneofhope.stoneofhope.ui.notes.NoteActionTarget
import com.stoneofhope.stoneofhope.ui.notes.NoteCardUi
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the verse page's composer writes (iOS `ChapterNoteEntryKind`; "Comment" there is a reflection). */
enum class VerseEntryMode(@param:StringRes val title: Int, @param:StringRes val hint: Int) {
    Reflection(R.string.mode_reflection, R.string.add_reflection_hint),
    Prayer(R.string.chip_prayer, R.string.add_prayer_hint),
}

data class ChapterVerseState(
    val refLabel: String = "",
    /** The verse text in its version, with marks; null while unknown. */
    val verse: ResolvedVerse? = null,
    /** The newest anchor here: the target of the top "⋯". */
    val anchor: NoteCardUi? = null,
    /** Reflections and the anchors' own comments, by day. */
    val thread: List<ThreadDay> = emptyList(),
    val reflectionCount: Int = 0,
    val prayers: List<NoteCardUi> = emptyList(),
    val allTags: List<TagEntity> = emptyList(),
    /** A note to fill `userVerses` from while [verse] is unknown. */
    val textSourceId: String? = null,
    val loaded: Boolean = false,
) {
    fun actionTarget(id: String): NoteActionTarget? =
        (listOfNotNull(anchor) + prayers).firstOrNull { it.note.id == id }?.actionTarget()

    fun threadItem(id: String): ThreadItem? = thread.firstNotNullOfOrNull { day -> day.items.firstOrNull { it.id == id } }
}

/**
 * iOS `ChapterNoteDetailsView`: one verse range of a chapter — its text, every reflection on it (all
 * anchors, all days: IOS_ISSUES H1) and its prayers. Arguments: [ChapterViewModel.ARG_BOOK_ORD],
 * [ChapterViewModel.ARG_CHAPTER_ORD], [ARG_VERSE_START], [ARG_VERSE_END].
 */
@HiltViewModel
class ChapterVerseViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val db: AppDatabase,
    private val notes: NoteRepository,
    private val actions: NoteActionRepository,
    bibleVersions: BibleVersionPreferences,
) : ViewModel() {

    val verse = ChapterVerse(
        checkNotNull(savedState[ChapterViewModel.ARG_BOOK_ORD]),
        checkNotNull(savedState[ChapterViewModel.ARG_CHAPTER_ORD]),
        checkNotNull(savedState[ARG_VERSE_START]),
        checkNotNull(savedState[ARG_VERSE_END]),
    )

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val primaryVersion = BibleVersions.primary(bibleVersions.selectedVersions())

    val state: StateFlow<ChapterVerseState> = ChapterSource.observe(db, verse.chapter).map { src ->
        val group = ChapterNotesBuilder.verse(src.notes, src.comments, verse)
        val (source, resolved) = src.verseText(group)
        val items = group.reflections.map {
            ThreadItem(it.id, it.content, false, it.createdAt, it.starLevel, it.tagIds, src.tagNames(it.tagIds), isReflection = true)
        } + group.anchorComments.map {
            ThreadItem(it.id, it.content, false, it.createdAt, it.starLevel, it.tagIds, src.tagNames(it.tagIds))
        }
        ChapterVerseState(
            refLabel = verse.refLabel(source?.versionAbbr ?: primaryVersion),
            verse = resolved,
            anchor = group.anchors.firstOrNull()?.let { src.card(it, zone) },
            thread = NoteThread.build(items, pinDayKeys = emptyList(), answeredAt = null, zone = zone),
            reflectionCount = items.size,
            // The page is the verse, so the prayers' own verse chip would only repeat it.
            prayers = group.prayers.map { src.card(it, zone).copy(prayerRef = null) },
            allTags = src.tags,
            textSourceId = if (resolved == null) source?.id else null,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChapterVerseState())

    fun send(text: String, mode: VerseEntryMode) {
        viewModelScope.launch {
            when (mode) {
                VerseEntryMode.Reflection -> notes.saveVerseReflection(verse.bookOrd, verse.chapterOrd, verse.verseStart, verse.verseEnd, text, zone)
                VerseEntryMode.Prayer -> notes.savePrayerOnVerses(text, verse.bookOrd, verse.chapterOrd, verse.verseStart, verse.verseEnd)
            }
        }
    }

    fun onAction(note: NoteEntity, action: NoteAction) {
        viewModelScope.launch { NoteActionDispatcher.dispatch(actions, note, action, zone = zone) }
    }

    fun onThreadAction(item: ThreadItem, action: NoteAction) {
        viewModelScope.launch { NoteActionDispatcher.dispatchThread(actions, db, item, action, zone) }
    }

    fun ensureVerses(noteId: String) {
        viewModelScope.launch { notes.ensureUserVerses(noteId) }
    }

    companion object {
        const val ARG_VERSE_START = "verseStart"
        const val ARG_VERSE_END = "verseEnd"
    }
}
