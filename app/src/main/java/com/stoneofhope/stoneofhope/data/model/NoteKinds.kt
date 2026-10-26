package com.stoneofhope.stoneofhope.data.model

import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity

// Classifications ported from iOS `Note` (Models/Note.swift); Today's counts depend on them.

/** `.note` or web-originated `.studyNote` — both behave as a quick note. */
val NoteEntity.isQuickNoteType: Boolean get() = noteType.isQuickNoteType

/** A quick note anchored to a whole chapter: book + chapter, no verse. */
val NoteEntity.isChapterNote: Boolean
    get() = isQuickNoteType && bookOrd != null && chapterOrd != null && verseStart == null

/** A quick note anchored to a verse, including placeholder-only bodies (`"·"` / `"."`). */
val NoteEntity.isVerseAnchoredQuickNote: Boolean
    get() = isQuickNoteType && verseStart != null && bookOrd != null && chapterOrd != null

/** A verse-anchored quick note with real user text (belongs under its `.bibleVerse` row). */
val NoteEntity.isVerseReflection: Boolean
    get() = isVerseAnchoredQuickNote && !NoteVerseAnchor.isPlaceholderContent(content)
