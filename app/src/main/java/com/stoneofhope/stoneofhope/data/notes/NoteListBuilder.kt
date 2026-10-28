package com.stoneofhope.stoneofhope.data.notes

import com.stoneofhope.stoneofhope.data.bible.BibleLinkBucket
import com.stoneofhope.stoneofhope.data.bible.SwiftText
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.NoteVerseAnchor
import com.stoneofhope.stoneofhope.data.model.isChapterNote
import com.stoneofhope.stoneofhope.data.model.isVerseAnchoredQuickNote
import java.time.LocalDate
import java.time.ZoneId

/**
 * Verse anchors and their reflections (iOS `linkedReflectionNotes`, `parentBibleVerseAnchor`,
 * `shouldHideFromTopLevelList`). A reflection belongs to the active `.bibleVerse` anchor with the same
 * verses, version, link bucket and local creation day. iOS compares `verseEnd` exactly; an unset
 * `verseEnd` reads as `verseStart` here (IOS_ISSUES F3).
 */
object NoteRelations {

    private fun NoteEntity.end() = verseEnd ?: verseStart

    private fun version(v: String?) = SwiftText.trim(v.orEmpty()).uppercase()

    private fun sameAnchor(a: NoteEntity, b: NoteEntity, zone: ZoneId) =
        a.bookOrd == b.bookOrd && a.chapterOrd == b.chapterOrd && a.verseStart == b.verseStart && a.end() == b.end() &&
            version(a.versionAbbr) == version(b.versionAbbr) && BibleLinkBucket.from(a) == BibleLinkBucket.from(b) &&
            a.createdAt.atZone(zone).toLocalDate() == b.createdAt.atZone(zone).toLocalDate()

    private val NoteEntity.isActive get() = deletedAt == null && !isDeleted

    /** iOS `parentBibleVerseAnchor(for:)`. */
    fun parentAnchor(note: NoteEntity, notes: List<NoteEntity>, zone: ZoneId): NoteEntity? {
        if (!note.isVerseAnchoredQuickNote) return null
        return notes.firstOrNull { it.noteType == NoteType.BibleVerse && it.isActive && sameAnchor(it, note, zone) }
    }

    /** iOS `shouldHideFromTopLevelList`: reflections sit under their anchor, not as list rows. */
    fun isHiddenFromTopLevel(note: NoteEntity, notes: List<NoteEntity>, zone: ZoneId): Boolean =
        note.isVerseAnchoredQuickNote && parentAnchor(note, notes, zone) != null

    /** iOS `linkedReflectionNotes(for:)`: newest first; "·" / "." bodies are not reflections. */
    fun reflections(anchor: NoteEntity, notes: List<NoteEntity>, zone: ZoneId): List<NoteEntity> {
        if (anchor.noteType != NoteType.BibleVerse || anchor.bookOrd == null || anchor.chapterOrd == null || anchor.verseStart == null) {
            return emptyList()
        }
        return notes.filter {
            (it.noteType == NoteType.Note || it.noteType == NoteType.StudyNote) && it.id != anchor.id && it.isActive &&
                sameAnchor(it, anchor, zone) && !NoteVerseAnchor.isPlaceholderContent(it.content)
        }.sortedByDescending { it.createdAt }
    }

    /** iOS `compactTimelineSummary(for:)`: reflections under an anchor, comments (minus "Answered" markers) otherwise. */
    fun summary(note: NoteEntity, notes: List<NoteEntity>, comments: List<NoteCommentEntity>, zone: ZoneId): CompactSummary? =
        when (val s = thread(note, notes, comments, zone)) {
            is CompactSummary.Notes -> s.takeIf { it.count > 0 }
            is CompactSummary.Comments -> s.takeIf { it.count > 0 }
        }

    /** Like [summary], but also reports zero (the redesigned cards say "No comments"). */
    fun thread(note: NoteEntity, notes: List<NoteEntity>, comments: List<NoteCommentEntity>, zone: ZoneId): CompactSummary =
        if (note.noteType == NoteType.BibleVerse) CompactSummary.Notes(reflections(note, notes, zone).size)
        else CompactSummary.Comments(comments.count { it.noteId == note.id && !it.isAnsweredMarker })
}

/** iOS `CompactTimelineSummary`: the "N notes" / "N comments" caption of a list row. */
sealed interface CompactSummary {
    data class Notes(val count: Int) : CompactSummary
    data class Comments(val count: Int) : CompactSummary
}

/** iOS `NoteFilterUI`, in the type menu's order. */
enum class NoteFilter { All, QuickNote, StudyNote, Prayer, AnsweredPrayer, Pinned, Image, Bible, Sermon, YouTubeVideo, Starred }

/** One list row: a single note, or (`isVerseGroup`) loose notes on the same verse shown together. */
data class NoteListRow(val notes: List<NoteEntity>, val isVerseGroup: Boolean) {
    val note: NoteEntity get() = notes.first()
}

/** A day header and its rows (iOS `DatedNoteListRow` grouped by day). */
data class NoteListSection(val day: LocalDate, val rows: List<NoteListRow>)

/** Everything the list rules read; active rows only. */
data class NoteListData(
    val notes: List<NoteEntity>,
    val comments: List<NoteCommentEntity>,
    val tagNamesById: Map<String, String>,
    val activeSermonIds: Set<String>,
    val activeVideoIds: Set<String>,
)

/**
 * The type and tag menus' rules, shared by the note list and Moments. iOS runs Moments through
 * `applyMomentsTypeFilter`, whose rules differ slightly (IOS_ISSUES I5); Android uses these for both.
 */
class NoteFilterRules(private val data: NoteListData) {
    private val starredByComment = data.comments.filter { it.starLevel > 0 }.mapNotNull { it.noteId }.toSet()
    private val commentTagNames = data.comments.groupBy { it.noteId }
        .mapValues { (_, cs) -> cs.flatMap { c -> c.tagIds.mapNotNull(data.tagNamesById::get) }.toSet() }

    fun typeMatches(n: NoteEntity, filter: NoteFilter): Boolean = when (filter) {
        NoteFilter.All -> true
        NoteFilter.Starred -> n.starLevel > 0 || n.id in starredByComment
        NoteFilter.Sermon -> n.sermonId != null && n.sermonId in data.activeSermonIds
        NoteFilter.YouTubeVideo -> (!n.youtubeID.isNullOrEmpty() && n.youtubeID in data.activeVideoIds) || n.noteType == NoteType.YouTubeVideo
        NoteFilter.AnsweredPrayer -> n.isAnswered == true
        NoteFilter.Pinned -> n.isPinned == true
        NoteFilter.Prayer -> n.noteType == NoteType.Prayer
        NoteFilter.QuickNote -> n.noteType == NoteType.Note && !n.isChapterNote && !n.isVerseAnchoredQuickNote
        NoteFilter.StudyNote -> n.noteType == NoteType.StudyNote
        // "Bible" folds in chapter-anchored quick notes too.
        NoteFilter.Bible -> n.noteType == NoteType.BibleVerse || n.isChapterNote
        NoteFilter.Image -> n.noteType == NoteType.Image
    }

    /** The note's own tags or any of its comments' tags. */
    fun tagMatches(n: NoteEntity, tagName: String?): Boolean = tagName == null ||
        n.tagIds.any { data.tagNamesById[it] == tagName } || tagName in commentTagNames[n.id].orEmpty()
}

/** Port of iOS `NoteCreationView.filterDisplayNotes` + `noteListDerived` + `NoteListViewModel.updateDisplayNotes`. */
object NoteListBuilder {

    fun build(
        data: NoteListData,
        filter: NoteFilter,
        tagName: String?,
        day: LocalDate?,
        today: LocalDate,
        zone: ZoneId,
    ): List<NoteListSection> {
        fun localDay(note: NoteEntity) = note.createdAt.atZone(zone).toLocalDate()
        val includesPinnedToday = filter == NoteFilter.All && day == today

        val topLevel = data.notes.filter { !NoteRelations.isHiddenFromTopLevel(it, data.notes, zone) }
        val commentedThatDay = day?.let { d ->
            data.comments.filter { it.createdAt.atZone(zone).toLocalDate() == d }.mapNotNull { it.noteId }.toSet()
        }
        val rules = NoteFilterRules(data)
        fun typeMatches(n: NoteEntity) = rules.typeMatches(n, filter)
        fun tagMatches(n: NoteEntity) = rules.tagMatches(n, tagName)

        fun dayMatches(n: NoteEntity): Boolean {
            if (filter == NoteFilter.Pinned || filter == NoteFilter.AnsweredPrayer || day == null) return true
            if (includesPinnedToday && n.isPinned == true) return true
            return localDay(n) == day || commentedThatDay?.contains(n.id) == true
        }

        fun isSelectedDayActivity(n: NoteEntity) = day != null && (localDay(n) == day || (includesPinnedToday && n.isPinned == true))

        val filtered = topLevel.filter { typeMatches(it) && tagMatches(it) && dayMatches(it) }
            .sortedWith(
                compareByDescending<NoteEntity> { isSelectedDayActivity(it) }
                    .thenByDescending { includesPinnedToday && it.isPinned == true }
                    .thenByDescending { it.createdAt },
            )

        // All + today: a pinned note from an earlier day is listed under today.
        fun groupingDay(n: NoteEntity): LocalDate =
            if (includesPinnedToday && n.isPinned == true && localDay(n) != day) day!! else localDay(n)

        return filtered.groupBy(::groupingDay).toSortedMap(compareByDescending { it }).map { (d, notes) ->
            NoteListSection(d, rowsForDay(notes))
        }
    }

    /** Identity of a loose verse-anchored quick note's verse; null for every other note. */
    private fun verseKey(n: NoteEntity): String? =
        if (n.isVerseAnchoredQuickNote) "${n.bookOrd}:${n.chapterOrd}:${n.verseStart}:${n.verseEnd ?: n.verseStart}" else null

    /**
     * Same-verse notes are pulled next to the first of their verse and merged into one row; verse groups
     * then read in Bible order (each oldest first) while other rows keep their place.
     */
    private fun rowsForDay(notes: List<NoteEntity>): List<NoteListRow> {
        val clustered = mutableListOf<NoteEntity>()
        val placed = mutableSetOf<String>()
        for (n in notes) {
            val key = verseKey(n)
            if (key == null) {
                clustered += n
            } else if (placed.add(key)) {
                clustered += notes.filter { verseKey(it) == key }
            }
        }

        val rowNotes = mutableListOf<MutableList<NoteEntity>>()
        val rowKeys = mutableListOf<String?>()
        for (n in clustered) {
            val key = verseKey(n)
            if (key != null && rowKeys.lastOrNull() == key) {
                rowNotes.last() += n
            } else {
                rowNotes += mutableListOf(n)
                rowKeys += key
            }
        }

        val slots = rowNotes.indices.filter { rowKeys[it] != null }
        val sortedGroups = slots.map { slot -> rowNotes[slot].sortedBy { it.createdAt } }
            .sortedWith(compareBy({ it[0].bookOrd ?: 0 }, { it[0].chapterOrd ?: 0 }, { it[0].verseStart ?: 0 }, { it[0].verseEnd ?: it[0].verseStart ?: 0 }))
        for ((slot, group) in slots.zip(sortedGroups)) rowNotes[slot] = group.toMutableList()

        return rowNotes.mapIndexed { i, ns -> NoteListRow(ns, isVerseGroup = rowKeys[i] != null) }
    }
}
