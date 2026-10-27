package com.stoneofhope.stoneofhope.data.notes

import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import java.time.LocalDate
import java.time.ZoneId

/** iOS `NotesManager.notesForDailyReview`: the pages of Today's "Review". */
object NoteReview {

    private val types = setOf(NoteType.Note, NoteType.StudyNote, NoteType.BibleVerse, NoteType.Image, NoteType.Prayer)

    /**
     * Quick notes, study notes, verse anchors, images and prayers created on [day] or commented on that
     * day; on today, every pinned note too. Reflections under an anchor are left out (they show on the
     * anchor's page). Oldest first. [notes] and [comments] are the active rows.
     */
    fun notes(day: LocalDate, today: LocalDate, notes: List<NoteEntity>, comments: List<NoteCommentEntity>, zone: ZoneId): List<NoteEntity> {
        val commentedIds = comments.filter { it.createdAt.atZone(zone).toLocalDate() == day }.mapNotNull { it.noteId }.toSet()
        val ofDay = notes.filter { n ->
            n.noteType in types && (n.createdAt.atZone(zone).toLocalDate() == day || n.id in commentedIds)
        }
        val pinned = if (day == today) notes.filter { it.isPinned == true && ofDay.none { d -> d.id == it.id } } else emptyList()
        return (ofDay + pinned)
            .filterNot { NoteRelations.isHiddenFromTopLevel(it, notes, zone) }
            .sortedBy { it.createdAt }
    }
}
