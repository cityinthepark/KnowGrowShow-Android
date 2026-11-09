package com.stoneofhope.stoneofhope.ui.chapter

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.bible.BibleBooks
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.bible.BookChapter
import com.stoneofhope.stoneofhope.data.chapter.ChapterNotesBuilder
import com.stoneofhope.stoneofhope.data.chapter.ChapterSermons
import com.stoneofhope.stoneofhope.data.chapter.ChapterVerse
import com.stoneofhope.stoneofhope.data.chapter.ChapterVerseGroup
import com.stoneofhope.stoneofhope.data.chapter.SermonYear
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.IndexedTextEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.notes.MarkedVerse
import com.stoneofhope.stoneofhope.data.notes.ResolvedVerse
import com.stoneofhope.stoneofhope.data.notes.VerseDisplay
import com.stoneofhope.stoneofhope.data.notes.VerseMarks
import com.stoneofhope.stoneofhope.data.repository.NoteActionRepository
import com.stoneofhope.stoneofhope.data.repository.NoteRepository
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.ui.bible.BibleRefLanguages
import com.stoneofhope.stoneofhope.ui.notes.NoteAction
import com.stoneofhope.stoneofhope.ui.notes.NoteActionDispatcher
import com.stoneofhope.stoneofhope.ui.notes.NoteCardUi
import com.stoneofhope.stoneofhope.ui.notes.NoteCards
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** iOS `ChapterDetailView.Tab`, in the segmented control's order. */
enum class ChapterTab(@param:StringRes val title: Int) {
    Sermons(R.string.chapter_tab_sermons),
    Notes(R.string.chapter_tab_notes),
}

/** A verse group as the Chapter Notes tab draws it. */
data class ChapterVerseUi(
    val verse: ChapterVerse,
    val refLabel: String,
    /** The verse text with its marks; null while no note here has it in `userVerses`. */
    val text: MarkedVerse?,
    val reflectionCount: Int,
    val prayerCount: Int,
    /** A note to fill `userVerses` from while [text] is missing (iOS `VerseNoteGroupCell.task`). */
    val textSourceId: String?,
)

data class ChapterState(
    val sermons: List<SermonYear> = emptyList(),
    /** Notes on the whole chapter, oldest first. */
    val chapterNotes: List<NoteCardUi> = emptyList(),
    val verses: List<ChapterVerseUi> = emptyList(),
    val allTags: List<TagEntity> = emptyList(),
    val loaded: Boolean = false,
) {
    fun actionTarget(id: String) = chapterNotes.firstOrNull { it.note.id == id }?.actionTarget()
}

/** One chapter's notes with everything their cards need, shared by the chapter and verse pages. */
internal class ChapterSource(
    val notes: List<NoteEntity>,
    val comments: List<NoteCommentEntity>,
    val versesById: Map<String, UserVerseEntity>,
    val marks: Map<String, List<IndexedTextEntity>>,
    val tags: List<TagEntity>,
    val sermonIds: Set<String>,
) {
    private val tagNames = tags.associate { it.id to it.name }

    fun card(note: NoteEntity, zone: ZoneId): NoteCardUi =
        NoteCards.card(note, notes, comments, tagNames, sermonIds, versesById, zone, marks)

    fun tagNames(ids: List<String>): List<String> = ids.mapNotNull(tagNames::get)

    /** The first note of [group] whose verses are in `userVerses`, and that text; else the best note to fill them from. */
    fun verseText(group: ChapterVerseGroup): Pair<NoteEntity?, ResolvedVerse?> {
        for (note in group.textSources) {
            val resolved = VerseDisplay.resolve(note, versesById, marks) ?: continue
            if (resolved.combinedContent.isNotBlank()) return note to resolved
        }
        return group.textSources.firstOrNull() to null
    }

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        fun observe(db: AppDatabase, chapter: BookChapter): Flow<ChapterSource> {
            val notes = db.noteDao().observeActiveInChapter(chapter.bookOrd, chapter.chapterOrd)
            val comments = notes.map { list -> list.map { it.id } }.distinctUntilChanged().flatMapLatest { ids ->
                if (ids.isEmpty()) flowOf(emptyList()) else db.noteCommentDao().observeActiveForNotes(ids)
            }
            val labels = combine(db.tagDao().observeActive(), db.sermonDao().observeActiveIds()) { t, s -> t to s.toSet() }
            return combine(
                notes, comments,
                db.userVerseDao().observeInChapter(chapter.bookOrd, chapter.chapterOrd),
                db.indexedTextDao().observeActiveVerseMarks(),
                labels,
            ) { n, c, v, m, (t, s) -> ChapterSource(n, c, v.associateBy { it.id }, VerseMarks.byVerseId(m), t, s) }
        }
    }
}

/**
 * iOS `ChapterDetailView`: the chapter's sermons and notes. Arguments: [ARG_BOOK_ORD], [ARG_CHAPTER_ORD]
 * and [ARG_TAB] (a [ChapterTab] ordinal).
 */
@HiltViewModel
class ChapterViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val db: AppDatabase,
    private val notes: NoteRepository,
    private val actions: NoteActionRepository,
    bibleVersions: BibleVersionPreferences,
) : ViewModel() {

    val chapter = BookChapter(checkNotNull(savedState[ARG_BOOK_ORD]), checkNotNull(savedState[ARG_CHAPTER_ORD]))
    val initialTab: ChapterTab = ChapterTab.entries.getOrElse(savedState.get<Int>(ARG_TAB) ?: 0) { ChapterTab.Sermons }

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val selectedVersions = bibleVersions.selectedVersions()
    private val primaryVersion = BibleVersions.primary(selectedVersions)

    val bibleRefLanguages = BibleRefLanguages(
        matching = BibleVersions.bookMatchingLanguages(selectedVersions),
        display = BibleVersions.primaryBookDisplayLanguage(selectedVersions),
        secondary = BibleVersions.secondaryBookDisplayLanguage(selectedVersions),
    )

    /** iOS `NoteInputSheetView(defaultBookChapter:)` pre-fills "Genesis 2", in a language the field reads. */
    val defaultReference: String
        get() {
            val language = bibleRefLanguages.display.takeIf { it in bibleRefLanguages.matching }
                ?: bibleRefLanguages.matching.firstOrNull() ?: bibleRefLanguages.display
            return "${BibleBooks.name(chapter.bookOrd, language)} ${chapter.chapterOrd}"
        }

    private val sermons = db.sermonDao().observeActiveForBook(chapter.bookOrd)
        .map { ChapterSermons.byYear(ChapterSermons.forChapter(it, chapter.chapterOrd)) }

    val state: StateFlow<ChapterState> = combine(ChapterSource.observe(db, chapter), sermons) { src, sermonYears ->
        val built = ChapterNotesBuilder.build(src.notes, src.comments, chapter)
        val verses = built.verses.map { group ->
            val (source, resolved) = src.verseText(group)
            ChapterVerseUi(
                verse = group.verse,
                refLabel = group.verse.refLabel(source?.versionAbbr ?: primaryVersion),
                text = resolved?.combined?.trimmed(),
                reflectionCount = group.reflectionCount,
                prayerCount = group.prayers.size,
                textSourceId = if (resolved == null) source?.id else null,
            )
        }
        ChapterState(sermonYears, built.chapterNotes.map { src.card(it, zone) }, verses, src.tags, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChapterState())

    fun onAction(note: NoteEntity, action: NoteAction) {
        viewModelScope.launch { NoteActionDispatcher.dispatch(actions, note, action, zone = zone) }
    }

    /** iOS `ChapterNoteView` "+": `addNote(_:to:bibleRefText:separatedReference: true)`. */
    fun saveNote(content: String, bibleRefText: String?) {
        viewModelScope.launch { notes.saveNoteFromContent(content, bibleRefText, anchorChapter = chapter, zone = zone) }
    }

    fun ensureVerses(noteId: String) {
        viewModelScope.launch { notes.ensureUserVerses(noteId) }
    }

    companion object {
        const val ARG_BOOK_ORD = "bookOrd"
        const val ARG_CHAPTER_ORD = "chapterOrd"
        const val ARG_TAB = "tab"
    }
}
