package com.stoneofhope.stoneofhope.data.model

/**
 * Raw values are the Firestore wire strings shared with iOS (`NoteType: String`) and Web.
 * Never rename a raw value; add new cases instead.
 */
enum class NoteType(val raw: String) {
    Note("note"),
    Prayer("prayer"),
    FlashCard("flashCard"),
    BibleVerse("bibleVerse"),
    PersonalGrowth("personalGrowth"),
    SermonNote("sermonNote"),
    YouTubeVideo("youtubeVideo"),
    Starred("starred"),

    /** Created on web only; behaves exactly like [Note] (quick note) but keeps its raw value for sync. */
    StudyNote("studyNote"),
    Image("image");

    val isQuickNoteType: Boolean get() = this == Note || this == StudyNote

    companion object {
        fun fromRaw(raw: String?): NoteType? = entries.firstOrNull { it.raw == raw }
    }
}

/** `IndexedText.markStyle` wire values. */
enum class MarkStyle(val raw: String) {
    Highlight("highlight"),
    Underline("underline");

    companion object {
        fun fromRaw(raw: String?): MarkStyle? = entries.firstOrNull { it.raw == raw }
    }
}

/** Local-only (`SyncConflict` never syncs). */
enum class SyncConflictType(val raw: String) {
    Note("note"),
    NoteComment("noteComment"),
    IndexedText("indexedText");

    companion object {
        fun fromRaw(raw: String?): SyncConflictType? = entries.firstOrNull { it.raw == raw }
    }
}

/** Local-only (`SyncConflict.resolution`). */
enum class ConflictResolution(val raw: String) {
    /** Push the local version, discarding the remote edit. */
    KeepLocal("keepLocal"),

    /** Overwrite the local row with the remote version, discarding the local edit. */
    KeepRemote("keepRemote"),

    /** Keep the local row untouched; create a new row from the remote version so nothing is lost. */
    CreateCopy("createCopy");

    companion object {
        fun fromRaw(raw: String?): ConflictResolution? = entries.firstOrNull { it.raw == raw }
    }
}
