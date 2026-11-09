package com.stoneofhope.stoneofhope.data.notes

import com.stoneofhope.stoneofhope.data.bible.BibleAnchor
import com.stoneofhope.stoneofhope.data.bible.BibleAnchorKey
import com.stoneofhope.stoneofhope.data.bible.BibleFormatter
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.bible.SwiftText
import com.stoneofhope.stoneofhope.data.local.entity.IndexedTextEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.NoteVerseAnchor
import com.stoneofhope.stoneofhope.data.model.isChapterNote
import com.stoneofhope.stoneofhope.data.model.isVerseAnchoredQuickNote

// Read-only presentation rules ported from iOS `Note` (Models/Note.swift),
// `NoteTypeComponents.swift` and `NoteHeaderBadgeView`. No Android types, so they are unit-tested.

private val OBJECT_ID = Regex("^[0-9a-fA-F]{24}$")

/**
 * iOS `NoteComment.isAnsweredMarker`: the "Answered" timeline entry stores the id of the comment it
 * is paired with (or its own id) as its `content`. The UI shows a label instead of that id.
 */
val NoteCommentEntity.isAnsweredMarker: Boolean get() = OBJECT_ID.matches(content)

/** iOS `linkedAnsweredCommentId`. */
val NoteCommentEntity.linkedAnsweredCommentId: String? get() = if (isAnsweredMarker) content.lowercase() else null

/**
 * iOS `displayBibleVerseRefPlain` ("Genesis 3:4") reads `refKey` only, so a `.bibleVerse` anchor written
 * without `refKey` (Web) shows an empty title there. Android falls back to the ords for anchors.
 */
val NoteEntity.bibleVerseRef: String?
    get() {
        refKey?.let { BibleFormatter.display(it, versionAbbr, showVersion = false) }?.takeIf { it.isNotEmpty() }?.let { return it }
        if (noteType != NoteType.BibleVerse) return null
        val book = bookOrd ?: return null
        val chapter = chapterOrd ?: return null
        val start = verseStart ?: return null
        return BibleFormatter.display(book, chapter, start, verseEnd, versionAbbr, showVersion = false)
    }

/** iOS `displayAnchoredVerseRefPlain`: verse label of a verse-anchored quick/study note. */
val NoteEntity.anchoredVerseRef: String?
    get() {
        if (!isVerseAnchoredQuickNote) return null
        val ref = refKey?.let { BibleFormatter.display(it, versionAbbr, showVersion = false) }
        if (!ref.isNullOrEmpty() && refKey?.startsWith("CH:") != true) return ref
        return BibleFormatter.display(bookOrd!!, chapterOrd!!, verseStart, verseEnd, versionAbbr, showVersion = false)
    }

/** iOS `displayBibleChapterRefPlain`: "Jeremiah 29". */
val NoteEntity.chapterRef: String?
    get() {
        val key = refKey
        if (key != null && key.startsWith("CH:")) return BibleFormatter.display(key, versionAbbr, showVersion = false)
        (chapterKey ?: BibleAnchorKey.chapterKey(bookOrd, chapterOrd))?.let {
            return BibleFormatter.display(it, versionAbbr, showVersion = false)
        }
        if (key == null || !key.startsWith("VS:")) return null
        val parts = key.split(":")
        val book = parts.getOrNull(1)?.let(SwiftText::asciiInt) ?: return null
        val chapter = parts.getOrNull(2)?.let(SwiftText::asciiInt) ?: return null
        return BibleFormatter.display("CH:$book:$chapter", versionAbbr, showVersion = false)
    }

/** iOS `showsBibleRefInHeaderBadge`. */
val NoteEntity.showsBibleRefInHeaderBadge: Boolean
    get() = (noteType == NoteType.BibleVerse || noteType == NoteType.FlashCard) && bibleVerseRef != null

/** Fixed labels the UI localizes (iOS `headerBadgeTitle` strings). */
enum class NoteLabel { QuickNote, StudyNote, Prayers, SermonNote, YouTubeVideo, FlashCard, PersonalGrowth, Starred, Image }

/** iOS `listIconTint` families; the UI maps them to AppColor. */
enum class NoteTint { Notes, Prayer, BibleVerse, Sermon, Video, Flash, Starred, Default }

/** What `NoteHeaderBadgeView` draws: an icon, then a verse reference, "Chapter · ref" or a label. */
sealed interface NoteBadgeTitle {
    data class Reference(val text: String) : NoteBadgeTitle
    data class Chapter(val ref: String) : NoteBadgeTitle
    data class Label(val label: NoteLabel) : NoteBadgeTitle
}

data class NoteBadge(
    val icon: String,
    val title: NoteBadgeTitle,
    val tint: NoteTint,
    /** 📖 after a verse reference when the anchor belongs to a (live) sermon. */
    val sermonMark: Boolean = false,
)

object NoteBadges {
    const val BIBLE_ICON = "✝️"

    /** iOS `NoteType.listIconContent` (SF Symbols replaced by emoji). */
    fun typeIcon(type: NoteType): String = when (type) {
        NoteType.Note -> "📝"
        NoteType.StudyNote -> "📚"
        NoteType.Prayer -> "🌿"
        NoteType.BibleVerse -> BIBLE_ICON
        NoteType.SermonNote -> "📖"
        NoteType.YouTubeVideo -> "▶️"
        NoteType.FlashCard -> "🗂️"
        NoteType.Starred -> "⭐"
        NoteType.Image -> "🖼️"
        NoteType.PersonalGrowth -> "🚶"
    }

    private fun typeTint(type: NoteType): NoteTint = when (type) {
        NoteType.Note, NoteType.StudyNote, NoteType.Image -> NoteTint.Notes
        NoteType.Prayer -> NoteTint.Prayer
        NoteType.BibleVerse -> NoteTint.BibleVerse
        NoteType.SermonNote -> NoteTint.Sermon
        NoteType.YouTubeVideo -> NoteTint.Video
        NoteType.FlashCard -> NoteTint.Flash
        NoteType.Starred -> NoteTint.Starred
        NoteType.PersonalGrowth -> NoteTint.Default
    }

    /** iOS `NoteHeaderBadgeView.body` + `headerBadgeIconContent` / `headerBadgeTitle` / `headerBadgeIconTint`. */
    fun badge(note: NoteEntity, hasLinkedSermon: Boolean): NoteBadge {
        val tint = tint(note)
        val verseRef = note.bibleVerseRef
        if ((note.noteType == NoteType.BibleVerse || note.noteType == NoteType.FlashCard) && verseRef != null) {
            return NoteBadge(BIBLE_ICON, NoteBadgeTitle.Reference(verseRef), tint, hasLinkedSermon && note.noteType == NoteType.BibleVerse)
        }
        if (note.noteType != NoteType.Prayer && !note.showsBibleRefInHeaderBadge) {
            note.anchoredVerseRef?.takeIf { it.isNotEmpty() }?.let {
                return NoteBadge(BIBLE_ICON, NoteBadgeTitle.Reference(it), tint)
            }
            note.chapterRef?.takeIf { it.isNotEmpty() }?.let {
                return NoteBadge(BIBLE_ICON, NoteBadgeTitle.Chapter(it), tint)
            }
        }
        return NoteBadge(icon(note), title(note), tint)
    }

    private fun icon(note: NoteEntity): String = when {
        note.sermonId != null && note.noteType != NoteType.BibleVerse -> typeIcon(NoteType.SermonNote)
        note.youtubeID != null -> typeIcon(NoteType.YouTubeVideo)
        note.noteType == NoteType.BibleVerse || (note.noteType == NoteType.FlashCard && note.bibleVerseRef != null) -> BIBLE_ICON
        else -> typeIcon(note.noteType)
    }

    private fun title(note: NoteEntity): NoteBadgeTitle {
        if (note.sermonId != null) {
            return if (note.noteType == NoteType.BibleVerse) NoteBadgeTitle.Reference(note.bibleVerseRef.orEmpty())
            else NoteBadgeTitle.Label(NoteLabel.SermonNote)
        }
        if (note.youtubeID != null) return NoteBadgeTitle.Label(NoteLabel.YouTubeVideo)
        return when (note.noteType) {
            NoteType.BibleVerse -> NoteBadgeTitle.Reference(note.bibleVerseRef.orEmpty())
            NoteType.FlashCard -> note.bibleVerseRef?.let(NoteBadgeTitle::Reference) ?: NoteBadgeTitle.Label(NoteLabel.FlashCard)
            NoteType.Prayer -> NoteBadgeTitle.Label(NoteLabel.Prayers)
            NoteType.Note, NoteType.StudyNote -> {
                val chapter = if (note.isChapterNote) note.chapterRef else null
                chapter?.let(NoteBadgeTitle::Reference)
                    ?: NoteBadgeTitle.Label(if (note.noteType == NoteType.StudyNote) NoteLabel.StudyNote else NoteLabel.QuickNote)
            }
            NoteType.PersonalGrowth -> NoteBadgeTitle.Label(NoteLabel.PersonalGrowth)
            NoteType.SermonNote -> NoteBadgeTitle.Label(NoteLabel.SermonNote)
            NoteType.YouTubeVideo -> NoteBadgeTitle.Label(NoteLabel.YouTubeVideo)
            NoteType.Starred -> NoteBadgeTitle.Label(NoteLabel.Starred)
            NoteType.Image -> NoteBadgeTitle.Label(NoteLabel.Image)
        }
    }

    /** iOS `headerBadgeIconTint`. */
    fun tint(note: NoteEntity): NoteTint = when {
        note.sermonId != null && note.noteType == NoteType.BibleVerse -> NoteTint.BibleVerse
        note.sermonId != null -> NoteTint.Sermon
        note.youtubeID != null -> NoteTint.Video
        note.noteType == NoteType.BibleVerse || (note.noteType == NoteType.FlashCard && note.bibleVerseRef != null) -> NoteTint.BibleVerse
        note.noteType != NoteType.Prayer && !note.showsBibleRefInHeaderBadge && !note.chapterRef.isNullOrEmpty() -> NoteTint.BibleVerse
        else -> typeTint(note.noteType)
    }
}

/**
 * iOS `ResolvedVerseDisplay`: a note's verse range read from `userVerses` in the note's own version.
 * [verses] keeps each verse (number → text, "" when missing) for layouts that number them.
 */
data class ResolvedVerse(
    val refLabel: String,
    val combinedContent: String,
    val versionAbbr: String = "",
    val verses: List<Pair<Int, String>> = emptyList(),
    /** Web highlights / underlines per verse number (offsets into that verse's text). */
    val marks: Map<Int, List<VerseMark>> = emptyMap(),
) {
    /** One verse with its marks. */
    fun verse(number: Int, text: String) = MarkedVerse(text, marks[number].orEmpty())

    /** [combinedContent] with every verse's marks moved to where that verse starts (iOS `combinedHighlightedText`). */
    val combined: MarkedVerse
        get() {
            var offset = 0
            val all = verses.flatMap { (n, text) ->
                val shifted = marks[n].orEmpty().map { it.copy(start = it.start + offset, end = it.end + offset) }
                offset += text.length + 1
                shifted
            }
            return MarkedVerse(combinedContent, all)
        }
}

/** One version's text of a verse anchor (an element of iOS `verseVersionBlocks`). */
data class VerseVersionBlock(val versionAbbr: String, val resolved: ResolvedVerse)

object VerseDisplay {

    /**
     * The `userVerses` ids a verse-level note needs: its own version, plus every [selected] version for a
     * `.bibleVerse` anchor (which shows them all, see [versionBlocks]).
     */
    fun userVerseIds(note: NoteEntity, selected: List<String> = emptyList()): List<String> {
        val anchor = BibleAnchor.from(note) ?: return emptyList()
        val versions = if (note.noteType == NoteType.BibleVerse) blockVersions(anchor.versionAbbr, selected) else listOf(anchor.versionAbbr)
        return versions.flatMap { v -> anchor.verseOrds.map { UserVerseEntity.makeId(v, anchor.bookOrd, anchor.chapterOrd, it) } }
    }

    private fun normalized(version: String) = SwiftText.trim(version).uppercase()

    /** iOS `verseVersionBlocks` order: the selected versions, the anchor's own one first when it is not selected. */
    private fun blockVersions(own: String, selected: List<String>): List<String> =
        if (selected.any { normalized(it) == normalized(own) }) selected else listOf(own) + selected

    /**
     * iOS `verseVersionBlocks(for:)`: a `.bibleVerse` anchor's text in each of those versions that has
     * it. The anchor keeps only its primary `versionAbbr`; the other versions' `userVerses` sit beside it.
     */
    fun versionBlocks(
        note: NoteEntity,
        selected: List<String>,
        versesById: Map<String, UserVerseEntity>,
        marksByVerseId: Map<String, List<IndexedTextEntity>> = emptyMap(),
    ): List<VerseVersionBlock> {
        if (note.noteType != NoteType.BibleVerse) return emptyList()
        val own = BibleAnchor.from(note)?.takeIf { !it.isChapterLevel } ?: return emptyList()
        return blockVersions(own.versionAbbr, selected).mapNotNull { v ->
            resolve(own.copy(versionAbbr = v), versesById, marksByVerseId)
                ?.takeIf { SwiftText.trim(it.combinedContent).isNotEmpty() }
                ?.let { VerseVersionBlock(v, it) }
        }
    }

    /**
     * iOS `isMissingVerseText(for:)`: an anchor missing its text in its own version or any selected one,
     * so the list should fetch again.
     */
    fun isMissingVerseText(note: NoteEntity, selected: List<String>, versesById: Map<String, UserVerseEntity>): Boolean {
        if (note.noteType != NoteType.BibleVerse) return false
        val anchor = BibleAnchor.from(note)?.takeIf { !it.isChapterLevel } ?: return false
        val shown = versionBlocks(note, selected, versesById).map { normalized(it.versionAbbr) }.toSet()
        return (selected + anchor.versionAbbr).any { normalized(it) !in shown }
    }

    /**
     * iOS `highlightedVerseText(for:)`: an anchor's text with its marks. One version is the text alone;
     * several read "text [NKJV]", a blank line, "text [和合本]". Null when no version has text yet.
     */
    fun markedBody(
        note: NoteEntity,
        selected: List<String>,
        versesById: Map<String, UserVerseEntity>,
        marksByVerseId: Map<String, List<IndexedTextEntity>> = emptyMap(),
    ): MarkedVerse? = joined(versionBlocks(note, selected, versesById, marksByVerseId))

    /** [markedBody] for blocks already read (one block: its text alone; several: labelled, a blank line apart). */
    fun joined(blocks: List<VerseVersionBlock>): MarkedVerse? {
        if (blocks.isEmpty()) return null
        if (blocks.size == 1) return blocks[0].resolved.combined
        val text = StringBuilder()
        val marks = mutableListOf<VerseMark>()
        blocks.forEachIndexed { i, b ->
            if (i > 0) text.append("\n\n")
            val base = text.length
            val combined = b.resolved.combined
            text.append(combined.text).append(" [").append(BibleVersions.toDisplay(b.versionAbbr)).append("]")
            combined.marks.mapTo(marks) { it.copy(start = it.start + base, end = it.end + base) }
        }
        return MarkedVerse(text.toString(), marks)
    }

    /**
     * iOS `resolveUserVerses(for:)`: verses joined with one space; a missing verse is "". [marksByVerseId]
     * (from [VerseMarks.byVerseId]) adds the Web's highlights and underlines.
     */
    fun resolve(
        note: NoteEntity,
        versesById: Map<String, UserVerseEntity>,
        marksByVerseId: Map<String, List<IndexedTextEntity>> = emptyMap(),
    ): ResolvedVerse? {
        return resolve(BibleAnchor.from(note) ?: return null, versesById, marksByVerseId)
    }

    /** The same read for any verse range in one version (a flash card is a range over `userVerses`). */
    fun resolve(
        anchor: BibleAnchor,
        versesById: Map<String, UserVerseEntity>,
        marksByVerseId: Map<String, List<IndexedTextEntity>> = emptyMap(),
    ): ResolvedVerse? {
        if (anchor.isChapterLevel) return null
        val ids = anchor.verseOrds.associateWith { UserVerseEntity.makeId(anchor.versionAbbr, anchor.bookOrd, anchor.chapterOrd, it) }
        val verses = anchor.verseOrds.map { v -> v to versesById[ids.getValue(v)]?.content.orEmpty() }
        val marks = verses.associate { (v, text) -> v to VerseMarks.onText(text, marksByVerseId[ids.getValue(v)].orEmpty()) }
            .filterValues { it.isNotEmpty() }
        return ResolvedVerse(anchor.refLabel(showVersion = false).orEmpty(), verses.joinToString(" ") { it.second }, anchor.versionAbbr, verses, marks)
    }

    /**
     * iOS `displayContent(for:)`: a `.bibleVerse` anchor shows its verse text in every version that has
     * it ([markedBody] without marks); anything else (and an anchor with no text yet) shows `content`.
     */
    fun displayContent(note: NoteEntity, selected: List<String>, versesById: Map<String, UserVerseEntity>): String =
        markedBody(note, selected, versesById)?.text ?: note.content

    /** iOS hides the anchor body while it would only be the "·" placeholder. */
    fun showsBody(note: NoteEntity, displayContent: String): Boolean =
        !(note.noteType == NoteType.BibleVerse && displayContent == NoteVerseAnchor.PLACEHOLDER)

    /** What "Copy" puts on the clipboard: "<reference>\n<text>" for a verse anchor. */
    fun copyText(note: NoteEntity, displayContent: String): String {
        val ref = if (note.noteType == NoteType.BibleVerse) note.bibleVerseRef?.let { "$it\n" }.orEmpty() else ""
        return ref + displayContent
    }
}

/** iOS `Note.isEditable` minus images (the edit action is hidden for both). */
val NoteEntity.isTextEditable: Boolean get() = noteType != NoteType.BibleVerse && noteType != NoteType.Image
