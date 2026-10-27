package com.stoneofhope.stoneofhope.data.repository

import androidx.room.withTransaction
import com.stoneofhope.stoneofhope.data.bible.BibleAnchor
import com.stoneofhope.stoneofhope.data.bible.BibleAnchorKey
import com.stoneofhope.stoneofhope.data.bible.BibleBookMatcher
import com.stoneofhope.stoneofhope.data.bible.BibleBooks
import com.stoneofhope.stoneofhope.data.bible.BibleLanguage
import com.stoneofhope.stoneofhope.data.bible.BibleLinkBucket
import com.stoneofhope.stoneofhope.data.bible.BibleRefConfig
import com.stoneofhope.stoneofhope.data.bible.BibleReferenceParser
import com.stoneofhope.stoneofhope.data.bible.BibleReferenceValidator
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.bible.SwiftText
import com.stoneofhope.stoneofhope.data.bible.VerseShortcutDetector
import com.stoneofhope.stoneofhope.data.bible.text.BibleVerseReader
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.NoteVerseAnchor
import com.stoneofhope.stoneofhope.data.model.ObjectIds
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** iOS `VerseReferenceSaveResult`. */
data class VerseReferenceSaveResult(val anchor: NoteEntity, val reflection: NoteEntity?, val didCreateAnchor: Boolean)

/** What a note sheet save produced. */
sealed interface NoteInputSaveResult {
    data class Plain(val note: NoteEntity) : NoteInputSaveResult
    data class Verse(val result: VerseReferenceSaveResult) : NoteInputSaveResult
}

/**
 * Note reads/writes for screens: the Android side of iOS `NotesManager`. Every local write marks rows
 * `needsSync` (contract §2.1). The Bible-reference rules follow iOS code
 * (`NOTE_INPUT_BIBLE_REFERENCE_DETECTION.md` §3, §5; `USER_VERSE_CONTRACT.md` §4–§7).
 */
@Singleton
class NoteRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
    private val userVerses: UserVerseManager,
    private val verseReader: BibleVerseReader,
    private val bibleVersions: BibleVersionPreferences,
) {
    private val noteDao get() = db.noteDao()

    fun observeActive(): Flow<List<NoteEntity>> = noteDao.observeActive()

    /** iOS `getAllTagNames()`: active tags, oldest first. */
    fun observeTagNames(): Flow<List<String>> = db.tagDao().observeActiveByCreation().map { tags -> tags.map { it.name } }

    /** The selected versions, read once per save (iOS reads `UserDefaults` at each step). */
    private class Versions(val selected: List<String>) {
        val primary: String = BibleVersions.primary(selected)
        val languages: List<BibleLanguage> = BibleVersions.bookMatchingLanguages(selected)
    }

    private fun versions() = Versions(bibleVersions.selectedVersions())

    // MARK: - Note sheet (iOS saveNoteFromContent(_:bibleRefText:) → saveSeparatedNoteInput)

    /**
     * Today / note sheet save, with no sermon, video or chapter context:
     * 1. A "Book c:v[-v]" typed at the start of the body wins over the reference field: verse anchor
     *    plus the rest of the body as its own reflection note.
     * 2. "vN" in the body, with the field naming only a chapter, becomes that chapter's verse N.
     * 3. A field that reaches verse level saves anchor + reflection; a chapter-only field is dropped.
     * 4. Otherwise a plain note.
     */
    suspend fun saveNoteFromContent(
        content: String,
        bibleRefText: String?,
        tagName: String? = null,
        noteType: NoteType? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): NoteInputSaveResult? {
        val v = versions()
        var trimmedContent = SwiftText.trim(content)
        var trimmedRef = bibleRefText?.let(SwiftText::trim).orEmpty()

        detectExplicitVerseReference(trimmedContent, v)?.let { det ->
            val result = saveVerseReference(
                det.bookOrd, det.chapter, det.start, det.end, v.primary, det.userVerseContents,
                reflectionText = det.suffix, tagName = tagName, zone = zone,
            )
            return result?.let(NoteInputSaveResult::Verse)
        }

        val shortcut = VerseShortcutDetector.detect(trimmedContent)
        val base = shortcut?.let { chapterBase(trimmedRef, v) }
        if (shortcut != null && base != null) {
            val verses = if (shortcut.start == shortcut.end) "${shortcut.start}" else "${shortcut.start}-${shortcut.end}"
            val ref = "${BibleBooks.name(base.first)} ${base.second}:$verses"
            // iOS rewrites even when "v99" is past the chapter's end; the reference is then dropped
            // and "v99" is lost from the saved text. Android only rewrites a reference that will save.
            if (BibleReferenceValidator.validate(ref, BibleRefConfig.FlashCard, v.languages).resolved?.verseStart != null) {
                trimmedRef = ref
                trimmedContent = shortcut.remainder.orEmpty()
            }
        }

        if (trimmedRef.isNotEmpty()) {
            saveVerseFromReferenceField(trimmedRef, trimmedContent.ifEmpty { null }, tagName, v, zone)?.let {
                return NoteInputSaveResult.Verse(it)
            }
            // Not verse level: keep the note, drop the reference.
        }

        if (trimmedContent.isEmpty()) return null
        val now = clock.instant()
        return db.withTransaction {
            NoteEntity(
                id = ObjectIds.newId(), noteType = noteType ?: NoteType.Note, content = trimmedContent,
                createdAt = now, updatedAt = now, needsSync = true,
            ).let { withTag(it, tagName) }.also { noteDao.upsert(it) }
        }.let(NoteInputSaveResult::Plain)
    }

    private class VerseDetection(
        val bookOrd: Int, val chapter: Int, val start: Int, val end: Int,
        val suffix: String?, val userVerseContents: Map<String, Map<Int, String>>,
    )

    /**
     * iOS `detectExplicitBibleVerseReference`. Android also accepts the full-width "：" "－" a Chinese
     * keyboard types (iOS only normalises those in the reference field). The replacements are one
     * character each, so match positions still index the original text the reflection is cut from.
     */
    private suspend fun detectExplicitVerseReference(content: String, v: Versions): VerseDetection? {
        val trimmed = SwiftText.trim(content)
        val normalized = BibleReferenceValidator.normalizePunctuation(trimmed)
        val parsed = BibleReferenceParser.parseFlexible(normalized, v.languages) ?: return null
        val start = parsed.startVerse ?: return null
        val match = BibleReferenceParser.chapterVersePattern.find(normalized) ?: return null

        // The text before the numbers must itself be the same book (rules out "…text 3:16 John").
        val prefix = SwiftText.trimWhitespaceAndPunctuation(normalized.substring(0, match.range.first))
        if (BibleBookMatcher.bookOrd(prefix, v.languages) != parsed.bookOrd) return null

        val end = parsed.endVerse ?: start
        if (!BibleReferenceParser.isWithinCanon(parsed.bookOrd, parsed.chapter, start, end)) return null

        val merged = mergedVerseContent(v.selected, parsed.bookOrd, parsed.chapter, start..end)
        val suffix = SwiftText.trim(trimmed.substring(match.range.last + 1))
        val contents = if (merged.isEmpty()) emptyMap() else resolvedUserVerseContents(v.selected, parsed.bookOrd, parsed.chapter, start, end)
        return VerseDetection(parsed.bookOrd, parsed.chapter, start, end, suffix.ifEmpty { null }, contents)
    }

    /** iOS `chapterBase(forRefText:anchorChapter:)` with no reading-chapter context (Today). */
    private fun chapterBase(ref: String, v: Versions): Pair<Int, Int>? {
        if (ref.isEmpty()) return null
        val resolved = BibleReferenceValidator.validate(ref, BibleRefConfig.ChapterAnchored, v.languages).resolved ?: return null
        return if (resolved.verseStart == null) resolved.bookOrd to resolved.chapter else null
    }

    /** iOS `saveBibleVerseFromSeparatedInput`: null (nothing written) unless the field reaches a verse. */
    private suspend fun saveVerseFromReferenceField(
        refText: String, commentText: String?, tagName: String?, v: Versions, zone: ZoneId,
    ): VerseReferenceSaveResult? {
        val resolved = BibleReferenceValidator.validate(refText, BibleRefConfig.FlashCard, v.languages).resolved ?: return null
        val start = resolved.verseStart ?: return null
        val end = resolved.verseEnd ?: start
        val merged = mergedVerseContent(v.selected, resolved.bookOrd, resolved.chapter, start..end)
        val contents = if (merged.isEmpty()) emptyMap() else resolvedUserVerseContents(v.selected, resolved.bookOrd, resolved.chapter, start, end)
        return saveVerseReference(
            resolved.bookOrd, resolved.chapter, start, end, v.primary, contents,
            reflectionText = commentText, tagName = tagName, zone = zone,
        )
    }

    // MARK: - Prayer (iOS savePrayerNote + applyBibleAnchor + ensureUserVerses)

    /**
     * The prayer text is saved verbatim (the body is never scanned for references). A reference that
     * reaches verse level is stored as metadata on the prayer; anything less is silently dropped.
     */
    suspend fun savePrayerNote(content: String, tagName: String?, bibleRefText: String?): NoteEntity? {
        val text = SwiftText.trim(content)
        if (text.isEmpty()) return null
        val v = versions()
        val now = clock.instant()
        var note = NoteEntity(
            id = ObjectIds.newId(), noteType = NoteType.Prayer, content = text,
            createdAt = now, updatedAt = now, needsSync = true,
        )
        val ref = bibleRefText?.let(SwiftText::trim)?.takeIf { it.isNotEmpty() }
            ?.let { BibleReferenceValidator.validate(it, BibleRefConfig.FlashCard, v.languages).resolved }
        val start = ref?.verseStart
        if (ref != null && start != null) {
            note = note.copy(
                bookOrd = ref.bookOrd, chapterOrd = ref.chapter, verseStart = start, verseEnd = ref.verseEnd ?: start,
                refKey = ref.refKey, versionAbbr = v.primary,
            )
        }
        note = withAnchorMetadata(note)
        val saved = db.withTransaction { withTag(note, tagName).also { noteDao.upsert(it) } }
        ensureUserVerses(saved.id)
        return noteDao.getById(saved.id)
    }

    /**
     * iOS `ensureUserVerses(for:)`: `userVerses` rows for a note's verse-level anchor in every selected
     * version plus the note's own. Existing non-blank rows win.
     */
    suspend fun ensureUserVerses(noteId: String) {
        var note = noteDao.getById(noteId) ?: return
        val bookOrd = note.bookOrd ?: return
        val chapterOrd = note.chapterOrd ?: return
        val start = note.verseStart ?: return
        val end = note.verseEnd ?: start
        val v = versions()
        if (note.versionAbbr?.let(SwiftText::trim).isNullOrEmpty()) {
            note = note.copy(versionAbbr = v.primary, needsSync = true, updatedAt = clock.instant())
            noteDao.upsert(note)
        }
        val noteVersion = note.versionAbbr?.let(SwiftText::trim).orEmpty()
        val contents = resolvedUserVerseContents(
            v.selected, bookOrd, chapterOrd, start, end, extraVersions = listOfNotNull(noteVersion.ifEmpty { null }),
        )
        val now = clock.instant()
        db.withTransaction { persistUserVerses(bookOrd, chapterOrd, start, end, contents, now) }
    }

    // MARK: - Unified verse write (iOS saveVerseReference)

    /**
     * Ensures `userVerses`, then a `.bibleVerse` anchor de-duplicated by (ords, version, link bucket,
     * local calendar day) — reusing a soft-deleted one if there is one — then a new `.note` reflection
     * when [reflectionText] is not blank. Reflections are never de-duplicated and carry no tags.
     * Like iOS, anchors and reflections still get `refKey` and `chapterKey`.
     */
    suspend fun saveVerseReference(
        bookOrd: Int,
        chapterOrd: Int,
        verseStart: Int,
        verseEnd: Int,
        versionAbbr: String?,
        userVerseContents: Map<String, Map<Int, String>>,
        reflectionText: String? = null,
        tagName: String? = null,
        sermonId: String? = null,
        youtubeID: String? = null,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = clock.instant(),
    ): VerseReferenceSaveResult? {
        val resolvedYoutubeID = youtubeID?.let(SwiftText::trim)?.ifEmpty { null }
        val bucket = BibleLinkBucket.from(sermonId, resolvedYoutubeID)
        val refKey = BibleAnchorKey.verseRefKey(bookOrd, chapterOrd, verseStart, verseEnd)
        val reflectionBody = reflectionText?.let(SwiftText::trim)?.ifEmpty { null }

        fun NoteEntity.withVerseFields() = copy(
            bookOrd = bookOrd, chapterOrd = chapterOrd, verseStart = verseStart, verseEnd = verseEnd,
            versionAbbr = versionAbbr, refKey = refKey,
        )

        return db.withTransaction {
            persistUserVerses(bookOrd, chapterOrd, verseStart, verseEnd, userVerseContents, now)

            val candidates = noteDao.verseAnchorCandidates(NoteType.BibleVerse, bookOrd, chapterOrd, verseStart)
                .filter { it.matchesAnchor(verseStart, verseEnd, versionAbbr, bucket) }
            val existing = candidates.firstOrNull { !it.isSoftDeleted && it.createdAt.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate() }
            val restorable = candidates.firstOrNull { it.isSoftDeleted }

            var didCreate = false
            val anchor = when {
                existing != null -> existing
                restorable != null -> withAnchorMetadata(
                    restorable.withVerseFields().copy(
                        content = NoteVerseAnchor.PLACEHOLDER, deletedAt = null, isDeleted = false,
                        createdAt = now, updatedAt = now, needsSync = true,
                    ).withVerseLink(sermonId, resolvedYoutubeID),
                ).also { noteDao.upsert(it) }
                else -> {
                    didCreate = true
                    val note = NoteEntity(
                        id = ObjectIds.newId(), noteType = NoteType.BibleVerse, content = NoteVerseAnchor.PLACEHOLDER,
                        createdAt = now, updatedAt = now, needsSync = true,
                    ).withVerseFields().withVerseLink(sermonId, resolvedYoutubeID)
                    withTag(withAnchorMetadata(note), tagName).also { noteDao.upsert(it) }
                }
            }

            val reflection = reflectionBody?.let { body ->
                val note = NoteEntity(
                    id = ObjectIds.newId(), noteType = NoteType.Note, content = body,
                    createdAt = now, updatedAt = now, needsSync = true,
                ).withVerseFields().withVerseLink(sermonId, resolvedYoutubeID)
                withAnchorMetadata(note).also { noteDao.upsert(it) }
            }
            VerseReferenceSaveResult(anchor, reflection, didCreate)
        }
    }

    /**
     * iOS `findVerseAnchor` filters. iOS compares `verseEnd` exactly, so an anchor whose `verseEnd` is
     * unset (Web / older data) never matches and is duplicated; Android reads unset as `verseStart`.
     */
    private fun NoteEntity.matchesAnchor(verseStart: Int, verseEnd: Int, versionAbbr: String?, bucket: BibleLinkBucket): Boolean =
        (this.verseEnd ?: this.verseStart) == verseEnd && this.verseStart == verseStart &&
            normalizedVersion(this.versionAbbr) == normalizedVersion(versionAbbr) &&
            BibleLinkBucket.from(this) == bucket

    private val NoteEntity.isSoftDeleted get() = deletedAt != null || isDeleted

    private fun normalizedVersion(value: String?) = SwiftText.trim(value.orEmpty()).uppercase()

    /** iOS `applyVerseLink`: a sermon link clears the video id and vice versa. */
    private fun NoteEntity.withVerseLink(sermonId: String?, youtubeID: String?): NoteEntity = when {
        sermonId != null -> copy(sermonId = sermonId, youtubeID = null)
        youtubeID != null -> copy(youtubeID = youtubeID, sermonId = null)
        else -> this
    }

    /** iOS `refreshAnchorMetadata`: `chapterKey` follows the ords; `isDeleted` follows `deletedAt`. */
    private fun withAnchorMetadata(note: NoteEntity) =
        note.copy(chapterKey = BibleAnchorKey.chapterKey(note.bookOrd, note.chapterOrd), isDeleted = note.deletedAt != null)

    /** iOS `persistUserVerses`: versions with no text at all get no rows. */
    private suspend fun persistUserVerses(
        bookOrd: Int, chapterOrd: Int, verseStart: Int, verseEnd: Int,
        contentsByVersion: Map<String, Map<Int, String>>, now: Instant,
    ) {
        for ((versionAbbr, contents) in contentsByVersion) {
            if (contents.values.none { SwiftText.trim(it).isNotEmpty() }) continue
            userVerses.getOrCreateAll(BibleAnchor(bookOrd, chapterOrd, verseStart, verseEnd, versionAbbr), contents, now)
        }
    }

    // MARK: - Verse text (iOS resolvedUserVerseContents / getMergedVerseContentResolvingAPI)

    /**
     * Per-verse text for every selected version (plus [extraVersions]). Non-blank `userVerses` content
     * is used as-is; the rest comes from [BibleVerseReader].
     */
    suspend fun resolvedUserVerseContents(
        selected: List<String>, bookOrd: Int, chapterOrd: Int, verseStart: Int, verseEnd: Int,
        extraVersions: List<String> = emptyList(),
    ): Map<String, Map<Int, String>> {
        val range = minOf(verseStart, verseEnd)..maxOf(verseStart, verseEnd)
        val versions = selected.toMutableList()
        for (extra in extraVersions.map(SwiftText::trim)) if (extra.isNotEmpty() && extra !in versions) versions += extra
        val byVersion = LinkedHashMap<String, Map<Int, String>>()
        for (version in versions) {
            val map = userVerses.frozenContents(version, bookOrd, chapterOrd, range).toMutableMap()
            val missing = range.filter { it !in map }
            if (missing.isNotEmpty()) {
                val fetched = verseReader.resolve(version, bookOrd, chapterOrd, missing.min()..missing.max())
                    .lines.filter { it.content.isNotEmpty() }.associate { it.verseOrd to it.content }
                for (verse in missing) fetched[verse]?.let { map[verse] = it }
            }
            byVersion[version] = map
        }
        return byVersion
    }

    /** Merged text across the selected versions, `"<block> [KJV]"` each; "" when no version has text. */
    private suspend fun mergedVerseContent(selected: List<String>, bookOrd: Int, chapter: Int, range: IntRange): String {
        val merged = StringBuilder()
        for (version in selected) {
            val r = verseReader.resolve(version, bookOrd, chapter, range)
            if (!r.isEmpty) merged.append(r.mergedText).append(" [").append(BibleVersions.toDisplay(version)).append("]\n\n")
        }
        return SwiftText.trim(merged.toString())
    }

    // MARK: - Misc

    /** iOS `attachTags`: an active tag matched case-insensitively, else a new one. Call inside a transaction. */
    private suspend fun withTag(note: NoteEntity, tagName: String?): NoteEntity {
        if (tagName.isNullOrEmpty()) return note
        val tag = db.tagDao().findActiveByName(tagName)
            ?: TagEntity(id = ObjectIds.newId(), name = tagName, createdAt = clock.instant(), needsSync = true)
                .also { db.tagDao().upsert(it) }
        return note.copy(tagIds = note.tagIds + tag.id)
    }
}
