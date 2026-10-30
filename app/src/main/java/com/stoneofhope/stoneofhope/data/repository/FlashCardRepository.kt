package com.stoneofhope.stoneofhope.data.repository

import com.stoneofhope.stoneofhope.data.bible.BibleAnchorKey
import com.stoneofhope.stoneofhope.data.bible.BibleReferenceParser
import com.stoneofhope.stoneofhope.data.bible.BibleReferenceValidator
import com.stoneofhope.stoneofhope.data.bible.SwiftText
import com.stoneofhope.stoneofhope.data.flashcard.FlashCardAddResult
import com.stoneofhope.stoneofhope.data.flashcard.FlashCardAddStatus
import com.stoneofhope.stoneofhope.data.flashcard.FlashCards
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.FlashCardEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.ObjectIds
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * The Android side of iOS `NotesManager`'s flash card functions. A card stores only the verse range;
 * the text is frozen into `userVerses` when the card is made. Every write marks the row `needsSync`
 * and stamps `updatedAt` with the moment of the change (not midnight).
 */
@Singleton
class FlashCardRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
    private val notes: NoteRepository,
    private val bibleVersions: BibleVersionPreferences,
) {
    private val dao get() = db.flashCardDao()

    /** Live (not deleted) cards, archived ones included; newest `updatedAt` first. */
    fun observeLive(): Flow<List<FlashCardEntity>> = dao.observeActive()

    /** iOS `addFlashCards(content:topic:)`: one card per selected version that has text for the range. */
    suspend fun add(reference: String, topic: String?): FlashCardAddResult {
        val selected = bibleVersions.selectedVersions()
        val normalized = BibleReferenceValidator.normalizePunctuation(reference)
        val parsed = BibleReferenceParser.parseFlexible(normalized, bibleVersions.bookMatchingLanguages())
        val start = parsed?.startVerse
        if (parsed == null || start == null || selected.isEmpty()) return FlashCardAddResult(FlashCardAddStatus.Failed)
        val end = parsed.endVerse
        val refKey = BibleAnchorKey.verseRefKey(parsed.bookOrd, parsed.chapter, start, end)
        val contents = notes.freezeVerseRange(parsed.bookOrd, parsed.chapter, start, end ?: start)
        val topicValue = topic?.let(SwiftText::trim)?.ifEmpty { null }

        var created = 0
        var restored = 0
        var alreadyActive = 0
        val skipped = mutableListOf<String>()
        for (version in selected) {
            val storage = FlashCards.storageVersion(version)
            if (!hasText(contents[version])) {
                skipped += storage
                continue
            }
            when (touchExisting(refKey, storage)) {
                Touched.Restored -> restored++
                Touched.AlreadyActive -> alreadyActive++
                null -> {
                    val now = clock.instant()
                    dao.upsert(
                        FlashCardEntity(
                            id = ObjectIds.newId(), bookOrd = parsed.bookOrd, chapterOrd = parsed.chapter, verseStart = start, verseEnd = end,
                            versionAbbr = storage, refKey = refKey, topic = topicValue, createdAt = now, updatedAt = now, needsSync = true,
                        ),
                    )
                    created++
                }
            }
        }
        val status = when {
            created > 0 -> FlashCardAddStatus.Created
            restored > 0 -> FlashCardAddStatus.RestoredToActive
            alreadyActive > 0 -> FlashCardAddStatus.AlreadyActive
            else -> FlashCardAddStatus.Failed
        }
        return FlashCardAddResult(status, if (status == FlashCardAddStatus.Failed) emptyList() else skipped)
    }

    /** iOS `addFlashCard(fromBibleVerseNoteId:)`: the note's range in the note's own version. */
    suspend fun addFromBibleVerseNote(noteId: String): FlashCardAddResult {
        val note = db.noteDao().getById(noteId)
        val refKey = note?.refKey
        val book = note?.bookOrd
        val chapter = note?.chapterOrd
        val start = note?.verseStart
        if (note == null || note.noteType != NoteType.BibleVerse || note.deletedAt != null ||
            refKey == null || book == null || chapter == null || start == null
        ) return FlashCardAddResult(FlashCardAddStatus.Failed)

        val storage = FlashCards.storageVersion(note.versionAbbr ?: bibleVersions.primaryVersion())
        val contents = notes.freezeVerseRange(book, chapter, start, note.verseEnd ?: start, extraVersions = listOf(storage))
        if (!hasText(contents[storage])) return FlashCardAddResult(FlashCardAddStatus.Failed)

        // iOS restores an archived card and also un-archives an active one (a no-op) with a fresh `updatedAt`.
        val existing = live(refKey, storage)
        if (existing != null) {
            dao.upsert(existing.copy(isArchived = false, updatedAt = clock.instant(), needsSync = true))
            return FlashCardAddResult(if (existing.isArchived) FlashCardAddStatus.RestoredToActive else FlashCardAddStatus.AlreadyActive)
        }
        val now = clock.instant()
        dao.upsert(
            FlashCardEntity(
                id = ObjectIds.newId(), bookOrd = book, chapterOrd = chapter, verseStart = start, verseEnd = note.verseEnd,
                versionAbbr = storage, refKey = refKey, createdAt = now, updatedAt = now, needsSync = true,
            ),
        )
        return FlashCardAddResult(FlashCardAddStatus.Created)
    }

    /** iOS `toggleFlashCardArchive`. */
    suspend fun toggleArchive(id: String) {
        val card = dao.getById(id) ?: return
        dao.upsert(card.copy(isArchived = !card.isArchived, updatedAt = clock.instant(), needsSync = true))
    }

    /** iOS `deleteFlashCard`: a soft delete; adding the same verse again makes a new card. */
    suspend fun delete(id: String) {
        val card = dao.getById(id) ?: return
        val now = clock.instant()
        dao.upsert(card.copy(deletedAt = now, updatedAt = now, needsSync = true))
    }

    private enum class Touched { Restored, AlreadyActive }

    /** iOS: an existing card is un-archived, or just bumped when it is already active. null = no such card. */
    private suspend fun touchExisting(refKey: String, storageVersion: String): Touched? {
        val existing = live(refKey, storageVersion) ?: return null
        val now = clock.instant()
        return if (existing.isArchived) {
            dao.upsert(existing.copy(isArchived = false, updatedAt = now, needsSync = true))
            Touched.Restored
        } else {
            dao.upsert(existing.copy(updatedAt = now, needsSync = true))
            Touched.AlreadyActive
        }
    }

    private suspend fun live(refKey: String, storageVersion: String): FlashCardEntity? =
        dao.liveByRefKey(refKey).firstOrNull { it.versionAbbr.orEmpty().uppercase() == storageVersion }

    private fun hasText(map: Map<Int, String>?) = map.orEmpty().values.any { SwiftText.trim(it).isNotEmpty() }
}
