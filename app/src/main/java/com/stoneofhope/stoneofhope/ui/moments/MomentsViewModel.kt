package com.stoneofhope.stoneofhope.ui.moments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.notes.MomentLine
import com.stoneofhope.stoneofhope.data.notes.MomentsBuilder
import com.stoneofhope.stoneofhope.data.notes.MomentsRange
import com.stoneofhope.stoneofhope.data.notes.NoteBadges
import com.stoneofhope.stoneofhope.data.notes.NoteFilter
import com.stoneofhope.stoneofhope.data.notes.NoteListData
import com.stoneofhope.stoneofhope.data.notes.VerseDisplay
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.data.sync.CloudSync
import com.stoneofhope.stoneofhope.data.sync.SyncIdentity
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
import kotlinx.coroutines.launch

/** One Moments row (iOS `MomentsNoteRow`). */
data class MomentRowUi(val noteId: String, val icon: String, val lines: List<MomentLine>)

data class MomentsSectionUi(val day: LocalDate, val rows: List<MomentRowUi>)

data class MomentsState(
    val filter: NoteFilter = NoteFilter.All,
    val tag: String? = null,
    /** Tag menu entries (iOS `getAllTagNames()`, oldest first). */
    val tagNames: List<String> = emptyList(),
    val range: MomentsRange = MomentsRange.Last.Days30,
    val sections: List<MomentsSectionUi> = emptyList(),
    val loaded: Boolean = false,
)

/** iOS `MomentsTabView`: the notes of a date window, newest first, by type and tag. */
@HiltViewModel
class MomentsViewModel @Inject constructor(
    db: AppDatabase,
    private val clock: Clock,
    private val identity: SyncIdentity,
    private val cloudSync: CloudSync,
    bibleVersions: BibleVersionPreferences,
) : ViewModel() {

    private val selectedVersions = bibleVersions.selectedVersions()

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val today = MutableStateFlow(currentDay())
    private val filter = MutableStateFlow(NoteFilter.All)
    private val tag = MutableStateFlow<String?>(null)
    private val range = MutableStateFlow<MomentsRange>(MomentsRange.Last.Days30)
    private val _refreshing = MutableStateFlow(false)

    /** Pull to refresh is running. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private class Source(val data: NoteListData, val tagsByCreation: List<TagEntity>)

    private class Selection(val filter: NoteFilter, val tag: String?, val range: MomentsRange, val today: LocalDate)

    private val source = combine(
        db.noteDao().observeActive(),
        db.noteCommentDao().observeActive(),
        db.tagDao().observeActiveByCreation(),
        db.sermonDao().observeActiveIds(),
        db.youTubeVideoDao().observeActiveIds(),
    ) { n, c, t, s, v -> Source(NoteListData(n, c, t.associate { it.id to it.name }, s.toSet(), v.toSet()), t) }

    private val selection = combine(filter, tag, range, today, ::Selection)

    val state: StateFlow<MomentsState> = combine(source, db.userVerseDao().observeAll(), selection) { src, verses, sel ->
        val versesById = verses.associateBy { it.id }
        val data = src.data
        val sections = MomentsBuilder.build(data, sel.filter, sel.tag, sel.range.days(sel.today), zone).map { section ->
            MomentsSectionUi(section.day, section.notes.map { note ->
                val linkedSermon = note.sermonId != null && note.sermonId in data.activeSermonIds
                MomentRowUi(
                    noteId = note.id,
                    icon = NoteBadges.badge(note, linkedSermon).icon,
                    lines = MomentsBuilder.lines(
                        note, VerseDisplay.displayContent(note, selectedVersions, versesById),
                        sermonMark = linkedSermon && note.noteType == NoteType.BibleVerse,
                    ),
                )
            })
        }
        MomentsState(sel.filter, sel.tag, src.tagsByCreation.map { it.name }, sel.range, sections, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MomentsState())

    fun setFilter(value: NoteFilter) { filter.value = value }

    fun setTag(value: String?) { tag.value = value }

    fun setRange(value: MomentsRange) { range.value = value }

    /** "Recent 30 Days" and the presets end today: move them on at midnight / back in the foreground (I4). */
    fun refreshDay() { today.value = currentDay() }

    /** iOS pulls a full sync for a signed-in user; local-only use just ends the gesture. */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                identity.currentAuthUid?.let { cloudSync.fullSync(it) }
            } finally {
                _refreshing.value = false
            }
        }
    }

    private var lastAutoSyncAt: java.time.Instant? = null

    /**
     * Opening the tab: pull only if another device changed something (cheap check), then push. Quiet, and at
     * most every [AUTO_SYNC_MIN_INTERVAL_SECONDS], so switching tabs back and forth costs nothing.
     */
    fun autoSyncOnAppear() {
        val uid = identity.currentAuthUid ?: return
        val now = clock.instant()
        if (lastAutoSyncAt?.let { java.time.Duration.between(it, now).seconds < AUTO_SYNC_MIN_INTERVAL_SECONDS } == true) return
        lastAutoSyncAt = now
        viewModelScope.launch { cloudSync.autoSync(uid) }
    }

    private companion object {
        const val AUTO_SYNC_MIN_INTERVAL_SECONDS = 30L
    }

    private fun currentDay(): LocalDate = clock.instant().atZone(zone).toLocalDate()
}
