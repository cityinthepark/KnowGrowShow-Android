package com.stoneofhope.stoneofhope.data.notes

import com.stoneofhope.stoneofhope.data.bible.BibleAnchor
import com.stoneofhope.stoneofhope.data.bible.BibleAnchorKey
import com.stoneofhope.stoneofhope.data.bible.BibleFormatter
import com.stoneofhope.stoneofhope.data.bible.SwiftText
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
)

object VerseDisplay {

    /** The `userVerses` ids a verse-level note needs, in its own version. */
    fun userVerseIds(note: NoteEntity): List<String> {
        val anchor = BibleAnchor.from(note) ?: return emptyList()
        return anchor.verseOrds.map { UserVerseEntity.makeId(anchor.versionAbbr, anchor.bookOrd, anchor.chapterOrd, it) }
    }

    /** iOS `resolveUserVerses(for:)`: verses joined with one space; a missing verse is "". */
    fun resolve(note: NoteEntity, versesById: Map<String, UserVerseEntity>): ResolvedVerse? {
        val anchor = BibleAnchor.from(note) ?: return null
        if (anchor.isChapterLevel) return null
        val verses = anchor.verseOrds.map { v ->
            v to versesById[UserVerseEntity.makeId(anchor.versionAbbr, anchor.bookOrd, anchor.chapterOrd, v)]?.content.orEmpty()
        }
        return ResolvedVerse(anchor.refLabel(showVersion = false).orEmpty(), verses.joinToString(" ") { it.second }, anchor.versionAbbr, verses)
    }

    /**
     * iOS `displayContent(for:)`: a `.bibleVerse` anchor shows its verse text; anything else (and an
     * anchor whose verses are not in the pool yet) shows `content`.
     */
    fun displayContent(note: NoteEntity, versesById: Map<String, UserVerseEntity>): String {
        if (note.noteType != NoteType.BibleVerse) return note.content
        val combined = resolve(note, versesById)?.combinedContent
        return if (combined != null && SwiftText.trim(combined).isNotEmpty()) combined else note.content
    }

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
