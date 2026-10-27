package com.stoneofhope.stoneofhope.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.notes.CompactSummary
import com.stoneofhope.stoneofhope.data.notes.NoteBadge
import com.stoneofhope.stoneofhope.data.notes.NoteBadges
import com.stoneofhope.stoneofhope.data.notes.NoteFilter
import com.stoneofhope.stoneofhope.data.notes.NoteListBuilder
import com.stoneofhope.stoneofhope.data.notes.NoteListData
import com.stoneofhope.stoneofhope.data.notes.NoteRelations
import com.stoneofhope.stoneofhope.data.notes.ResolvedVerse
import com.stoneofhope.stoneofhope.data.notes.VerseDisplay
import com.stoneofhope.stoneofhope.data.notes.anchoredVerseRef
import com.stoneofhope.stoneofhope.data.notes.bibleVerseRef
import com.stoneofhope.stoneofhope.data.notes.chapterRef
import com.stoneofhope.stoneofhope.data.notes.showsBibleRefInHeaderBadge
import com.stoneofhope.stoneofhope.data.repository.NoteActionRepository
import com.stoneofhope.stoneofhope.data.repository.NoteRepository
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.ui.bible.BibleRefLanguages
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One note as the redesigned cards draw it. */
data class NoteCardUi(
    val note: NoteEntity,
    val badge: NoteBadge,
    val displayContent: String,
    val showsBody: Boolean,
    /** Reflections under an anchor, comments (without "Answered" markers) otherwise — zero included. */
    val thread: CompactSummary,
    val tagNames: List<String>,
    /** A verse-linked prayer's reference chip; tapping reveals the verse in the card. */
    val prayerRef: String?,
    /** The reference shown at the top of the "⋯" sheet. */
    val verseRef: String?,
) {
    val isAnchor: Boolean get() = note.noteType == NoteType.BibleVerse
    val threadCount: Int get() = when (val t = thread) { is CompactSummary.Notes -> t.count; is CompactSummary.Comments -> t.count }

    fun actionTarget() = NoteActionTarget(note, displayContent, verseRef, tagNames)
}

sealed interface NoteRowUi {
    val key: String

    data class Single(val card: NoteCardUi) : NoteRowUi {
        override val key get() = card.note.id
    }

    /** iOS `VerseNoteGroupCell`: loose notes on one verse, verse text once. */
    data class VerseGroup(val header: NoteCardUi, val verseText: String?, val notes: List<NoteEntity>) : NoteRowUi {
        override val key get() = "group_" + notes.first().id
    }
}

/** A day of the list, or the "📌 PINNED" block on top of today's "All" list (design L §2.1). */
data class NoteSectionUi(val day: LocalDate, val rows: List<NoteRowUi>, val pinned: Boolean = false)

data class NoteListState(
    val filter: NoteFilter,
    val tag: String?,
    /** Tag menu entries (iOS `getAllTagNames()`, oldest first). */
    val tagNames: List<String> = emptyList(),
    val allTags: List<TagEntity> = emptyList(),
    val sections: List<NoteSectionUi> = emptyList(),
    /** Notes in the current results, for the header ("6 notes"). */
    val total: Int = 0,
    val loaded: Boolean = false,
) {
    /** The card behind the "⋯" sheet. */
    fun actionTarget(noteId: String): NoteActionTarget? = sections.asSequence()
        .flatMap { it.rows }
        .mapNotNull { (it as? NoteRowUi.Single)?.card }
        .firstOrNull { it.note.id == noteId }?.actionTarget()
}

/** Builds the card for any note (list rows, verse-group headers, the details screen). */
internal object NoteCards {
    fun card(
        note: NoteEntity,
        notes: List<NoteEntity>,
        comments: List<NoteCommentEntity>,
        tagNamesById: Map<String, String>,
        activeSermonIds: Set<String>,
        verses: Map<String, UserVerseEntity>,
        zone: ZoneId,
    ): NoteCardUi {
        val content = VerseDisplay.displayContent(note, verses)
        val prayerRef = if (note.noteType == NoteType.Prayer && !note.showsBibleRefInHeaderBadge) {
            (note.bibleVerseRef ?: note.chapterRef)?.takeIf { it.isNotEmpty() }
        } else null
        return NoteCardUi(
            note = note,
            badge = NoteBadges.badge(note, hasLinkedSermon = note.sermonId != null && note.sermonId in activeSermonIds),
            displayContent = content,
            showsBody = VerseDisplay.showsBody(note, content),
            thread = NoteRelations.thread(note, notes, comments, zone),
            tagNames = note.tagIds.mapNotNull(tagNamesById::get),
            prayerRef = prayerRef,
            verseRef = (note.bibleVerseRef ?: note.anchoredVerseRef)?.takeIf { it.isNotEmpty() },
        )
    }
}

/**
 * iOS `NoteCreationView`: the notes of one day (or every day) filtered by type and tag, opened from a
 * Today tile. Arguments: [ARG_FILTER] (a [NoteFilter] ordinal) and [ARG_DAY] (epoch day or [NO_DAY]).
 */
@HiltViewModel
class NoteListViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val db: AppDatabase,
    private val notes: NoteRepository,
    private val actions: NoteActionRepository,
    bibleVersions: BibleVersionPreferences,
    private val clock: Clock,
) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    val day: LocalDate? = savedState.get<Long>(ARG_DAY)?.takeIf { it != NO_DAY }?.let(LocalDate::ofEpochDay)

    val today: LocalDate get() = clock.instant().atZone(zone).toLocalDate()

    val isToday: Boolean get() = day == today

    private val filter = MutableStateFlow(NoteFilter.entries[savedState.get<Int>(ARG_FILTER) ?: 0])
    private val tag = MutableStateFlow<String?>(null)

    private val selectedVersions = bibleVersions.selectedVersions()

    /** iOS `getSelectedVersionsDisplayString()`, shown next to each day header. */
    val versionsLabel: String = selectedVersions.joinToString(", ") { BibleVersions.toDisplay(it) }

    val bibleRefLanguages = BibleRefLanguages(
        matching = BibleVersions.bookMatchingLanguages(selectedVersions),
        display = BibleVersions.primaryBookDisplayLanguage(selectedVersions),
        secondary = BibleVersions.secondaryBookDisplayLanguage(selectedVersions),
    )

    private class Source(val data: NoteListData, val tagsByCreation: List<TagEntity>)

    private val source = combine(
        db.noteDao().observeActive(),
        db.noteCommentDao().observeActive(),
        db.tagDao().observeActiveByCreation(),
        db.sermonDao().observeActiveIds(),
        db.youTubeVideoDao().observeActiveIds(),
    ) { n, c, t, s, v -> Source(NoteListData(n, c, t.associate { it.id to it.name }, s.toSet(), v.toSet()), t) }

    val state: StateFlow<NoteListState> = combine(source, db.userVerseDao().observeAll(), filter, tag) { src, verses, f, tg ->
        val versesById = verses.associateBy { it.id }
        val d = src.data
        fun card(n: NoteEntity) = NoteCards.card(n, d.notes, d.comments, d.tagNamesById, d.activeSermonIds, versesById, zone)
        var sections = NoteListBuilder.build(d, f, tg, day, today, zone).map { section ->
            NoteSectionUi(section.day, section.rows.map { row ->
                if (row.isVerseGroup) {
                    val first = row.notes.first()
                    val verse = VerseDisplay.resolve(first, versesById)?.combinedContent?.trim()?.ifEmpty { null }
                    NoteRowUi.VerseGroup(card(first), verse, row.notes)
                } else {
                    NoteRowUi.Single(card(row.note))
                }
            })
        }
        // "All" on today: pinned notes get their own block on top (the rules already put them first in today).
        if (f == NoteFilter.All && day == today) {
            val pinnedRows = sections.flatMap { s -> s.rows.filter { it is NoteRowUi.Single && it.card.note.isPinned == true } }
            if (pinnedRows.isNotEmpty()) {
                sections = listOf(NoteSectionUi(today, pinnedRows, pinned = true)) +
                    sections.map { s -> s.copy(rows = s.rows - pinnedRows.toSet()) }.filter { it.rows.isNotEmpty() }
            }
        }
        val total = sections.sumOf { s -> s.rows.sumOf { r -> if (r is NoteRowUi.VerseGroup) r.notes.size else 1 } }
        NoteListState(f, tg, src.tagsByCreation.map { it.name }, src.tagsByCreation, sections, total, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteListState(filter.value, null))

    private val _expanded = MutableStateFlow<Set<String>>(emptySet())
    private val _prayerVerses = MutableStateFlow<Map<String, ResolvedVerse?>>(emptyMap())

    /** Prayers whose verse is unfolded inside the card (design L2); kept while this page lives. */
    val expanded: StateFlow<Set<String>> = _expanded.asStateFlow()
    val prayerVerses: StateFlow<Map<String, ResolvedVerse?>> = _prayerVerses.asStateFlow()

    fun setFilter(value: NoteFilter) { filter.value = value }

    fun setTag(value: String?) { tag.value = value }

    fun onAction(note: NoteEntity, action: NoteAction) {
        viewModelScope.launch { NoteActionDispatcher.dispatch(actions, note, action, unmarkReferenceDay = day, zone = zone) }
    }

    /** The list's "New note" (today only), then back to "All" like iOS. */
    fun saveNote(content: String, bibleRefText: String?) {
        viewModelScope.launch {
            notes.saveNoteFromContent(content, bibleRefText, zone = zone)
            filter.value = NoteFilter.All
        }
    }

    /** "New prayer" while the Prayer filter is on. */
    fun savePrayer(content: String, tagName: String?, bibleRefText: String?) {
        viewModelScope.launch { notes.savePrayerNote(content, tagName, bibleRefText) }
    }

    /** iOS `VerseNoteGroupCell.task`: fill the pool so the group's verse text can show. */
    fun ensureVerses(noteId: String) {
        viewModelScope.launch { notes.ensureUserVerses(noteId) }
    }

    /** Unfold / fold a prayer's verse (iOS `PrayerVerseRevealButton`, shown in place here). */
    fun togglePrayerVerse(note: NoteEntity) {
        if (note.id in _expanded.value) {
            _expanded.update { it - note.id }
            return
        }
        _expanded.update { it + note.id }
        if (note.id !in _prayerVerses.value) {
            viewModelScope.launch {
                notes.ensureUserVerses(note.id)
                val verses = VerseDisplay.userVerseIds(note).mapNotNull { db.userVerseDao().getById(it) }.associateBy { it.id }
                val resolved = VerseDisplay.resolve(note, verses)
                _prayerVerses.update { it + (note.id to resolved) }
            }
        }
    }

    companion object {
        const val ARG_FILTER = "filter"
        const val ARG_DAY = "day"
        const val NO_DAY = Long.MIN_VALUE
    }
}

/** Shared by list and details: one [NoteAction] → [NoteActionRepository]. */
internal object NoteActionDispatcher {
    suspend fun dispatch(actions: NoteActionRepository, note: NoteEntity, action: NoteAction, unmarkReferenceDay: LocalDate?, zone: ZoneId) {
        when (action) {
            NoteAction.TogglePinned -> actions.setPinned(note.id, note.isPinned != true, unmarkReferenceDay, zone)
            NoteAction.ToggleAnswered -> actions.setAnswered(note.id, note.isAnswered != true, zone = zone)
            is NoteAction.Star -> actions.setStarLevel(note.id, action.level)
            is NoteAction.AddTag -> actions.addTag(note.id, action.name)
            is NoteAction.RemoveTag -> actions.removeTag(note.id, action.tagId)
            is NoteAction.Edit -> actions.updateContent(note.id, action.text)
            NoteAction.Delete -> actions.delete(note.id)
            is NoteAction.Comment ->
                if (action.answered) actions.addAnsweredComment(note.id, action.text.ifEmpty { null })
                else actions.addComment(note.id, action.text, zone)
        }
    }
}
