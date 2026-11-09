package com.stoneofhope.stoneofhope.data.bible.text

import com.stoneofhope.stoneofhope.data.bible.BibleReferenceParser
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.bible.SwiftText
import com.stoneofhope.stoneofhope.data.local.dao.BibleCacheDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verse text for translations that are not on the device (NIV, NKJV via API.Bible on iOS:
 * `FetchedVerseCacheManager.ensureChapterCached`). Interface only for now — see
 * [UnavailableOnDemandVerseSource].
 */
interface OnDemandVerseSource {
    /** Plain per-verse text keyed by verse number; missing verses are simply absent. */
    suspend fun verses(versionAbbr: String, bookOrd: Int, chapterOrd: Int, verseRange: IntRange): Map<Int, String>
}

/**
 * Android does not fetch licensed translations yet, so they resolve to no text. Callers then behave
 * like iOS offline: the verse anchor is still saved, just without `userVerses` rows.
 */
class UnavailableOnDemandVerseSource @Inject constructor() : OnDemandVerseSource {
    override suspend fun verses(versionAbbr: String, bookOrd: Int, chapterOrd: Int, verseRange: IntRange) = emptyMap<Int, String>()
}

/** One verse of a [VerseRangeResolution]. [content] is empty when the verse could not be resolved. */
data class VerseLine(val verseOrd: Int, val displaySegment: String, val content: String)

/** iOS `VerseRangeResolution`. */
data class VerseRangeResolution(val lines: List<VerseLine>, val mergedText: String) {
    val isEmpty: Boolean get() = SwiftText.trim(mergedText).isEmpty()

    companion object {
        val Empty = VerseRangeResolution(emptyList(), "")
    }
}

/**
 * iOS `BibleVerseRangeReader.resolve`: version + book + chapter + verse range → per-verse text and the
 * merged `"3:16 For God…"` block. Bulk versions (KJV, CUV) read the bundled text, then any downloaded
 * rows; API-only versions (NIV, NKJV) skip those and go to [onDemand].
 */
@Singleton
class BibleVerseReader @Inject constructor(
    private val bundled: BundledVerseDao,
    private val downloaded: BibleCacheDao,
    private val onDemand: OnDemandVerseSource,
) {
    suspend fun resolve(versionAbbr: String, bookOrd: Int, chapterOrd: Int, verseRange: IntRange): VerseRangeResolution {
        val lo = minOf(verseRange.first, verseRange.last)
        val hi = maxOf(verseRange.first, verseRange.last)
        if (!BibleReferenceParser.isWithinCanon(bookOrd, chapterOrd, lo, hi)) return VerseRangeResolution.Empty

        val bulkByVerse = if (BibleVersions.ignoresDownloadedVerses(versionAbbr)) emptyMap() else loadBulk(versionAbbr, bookOrd, chapterOrd, lo, hi)
        val onDemandByVerse = if (BibleVersions.isDownloadable(versionAbbr) || (lo..hi).all { it in bulkByVerse }) {
            emptyMap()
        } else {
            onDemand.verses(versionAbbr, bookOrd, chapterOrd, lo..hi)
        }

        val read = (lo..hi).map { v ->
            val bulk = bulkByVerse[v]
            if (bulk != null) {
                VerseLine(v, "$chapterOrd:$v $bulk", bulk)
            } else {
                val text = onDemandByVerse[v].orEmpty()
                VerseLine(v, if (text.isEmpty()) "" else "$chapterOrd:$v $text", text)
            }
        }
        val lines = mergingRepeatedVerses(read, chapterOrd)
        // Psalms and Proverbs keep one verse per line, like iOS `mergedVerseBlock`.
        val separator = if (bookOrd == 19 || bookOrd == 20) "\n" else " "
        val merged = lines.map { it.displaySegment }.filter { SwiftText.trim(it).isNotEmpty() }.joinToString(separator)
        return VerseRangeResolution(lines, merged)
    }

    /**
     * iOS `mergingRepeatedVerses`: a merged verse (CUV "併於上節", see buildSrc `CuvVersificationFix`)
     * carries the same text as the verse before it. Show that text once, labelled with the whole span
     * (`1:20-21 …`); the later lines keep their [VerseLine.content] (for `userVerses`) but get an
     * empty display segment.
     */
    internal fun mergingRepeatedVerses(lines: List<VerseLine>, chapterOrd: Int): List<VerseLine> {
        val result = lines.toMutableList()
        var start = 0
        while (start < result.size) {
            val text = result[start].content
            var end = start
            while (text.isNotEmpty() && end + 1 < result.size && result[end + 1].content == text) end++
            if (end > start) {
                result[start] = result[start].copy(displaySegment = "$chapterOrd:${result[start].verseOrd}-${result[end].verseOrd} $text")
                for (i in start + 1..end) result[i] = result[i].copy(displaySegment = "")
            }
            start = end + 1
        }
        return result
    }

    /** iOS `versionAbbr ==[c] %@`: bundled rows are stored upper case. */
    private suspend fun loadBulk(versionAbbr: String, bookOrd: Int, chapterOrd: Int, lo: Int, hi: Int): Map<Int, String> {
        val upper = versionAbbr.uppercase()
        val map = bundled.range(upper, bookOrd, chapterOrd, lo, hi).associate { it.verseOrd to it.content }.toMutableMap()
        if (map.size < hi - lo + 1) {
            for (row in downloaded.downloadedChapter(upper, bookOrd, chapterOrd)) {
                if (row.verseOrd in lo..hi) map.putIfAbsent(row.verseOrd, row.verseContent)
            }
        }
        return map
    }
}
