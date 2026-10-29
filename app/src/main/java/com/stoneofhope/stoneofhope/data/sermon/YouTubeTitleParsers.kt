package com.stoneofhope.stoneofhope.data.sermon

import com.stoneofhope.stoneofhope.data.bible.BibleLanguage
import com.stoneofhope.stoneofhope.data.bible.BibleReferenceParser
import com.stoneofhope.stoneofhope.data.bible.SwiftText

/** iOS `ParsedYouTubeSermonInfo` (the fields the sermon import uses). */
data class YouTubeSermonInfo(
    val title: String,
    val bibleRef: String?,
    val preacher: String?,
    val youtubeID: String,
)

/**
 * iOS `NotesManager.parseSermonInfo(from:videoID:)`: title, Bible reference and preacher from a YouTube
 * title such as "Title: John 3 | Preacher" or "Preacher: Title | John 3"; the channel name stands in
 * for a preacher it can't find.
 *
 * Two iOS mistakes are fixed (IOS_ISSUES K4): a left side that is itself a reference ("John 3:16 |
 * Pastor Kim") was split at its colon into preacher "John 3" and title "16"; and a title without "|"
 * was split at any "-", so "Romans 8:1-11" became title "Romans 8:1", preacher "11". Android keeps a
 * reference whole and only splits at a spaced " - ".
 */
object YouTubeSermonTitleParser {

    fun parse(fullTitle: String, authorName: String?, videoID: String, languages: List<BibleLanguage> = emptyList()): YouTubeSermonInfo {
        val parts = fullTitle.split("|").map(SwiftText::trimWhitespaces)
        var title: String
        var bibleRef: String? = null
        var preacher: String? = null

        if (parts.size == 2) {
            val (left, right) = parts
            when {
                left.contains(":") && isBibleReference(left, languages) -> {
                    title = left
                    bibleRef = left
                    preacher = right
                }
                left.contains(":") -> {
                    val first = SwiftText.trimWhitespaces(left.substringBefore(":"))
                    val second = SwiftText.trimWhitespaces(left.substringAfter(":"))
                    when {
                        isBibleReference(second, languages) -> { title = first; bibleRef = second; preacher = right }
                        isBibleReference(right, languages) -> { preacher = first; title = second; bibleRef = right }
                        else -> { preacher = first; title = second }
                    }
                }
                isBibleReference(right, languages) -> { title = left; bibleRef = right }
                else -> title = fullTitle
            }
        } else if (parts.size == 1) {
            val dashParts = parts[0].split(" - ").map(SwiftText::trimWhitespaces)
            if (dashParts.size == 2) {
                title = dashParts[0]
                preacher = dashParts[1]
            } else {
                title = parts[0]
            }
        } else {
            title = parts[0]
        }

        return YouTubeSermonInfo(title, bibleRef, preacher ?: authorName, videoID)
    }

    /** iOS `NotesManager.isBibleReference`. */
    fun isBibleReference(text: String, languages: List<BibleLanguage> = emptyList()): Boolean =
        BibleReferenceParser.parseChaptersFlexible(text, languages) != null || SermonReferenceParser.parse(text, languages).isValid
}

/** iOS `YouTubeVideoBibleAnchor`; the verse is display-only (not stored on the video). */
data class YouTubeVideoAnchor(val bookOrd: Int?, val chapterOrd: Int?, val verseStart: Int?, val verseEnd: Int?)

/** iOS `YouTubeVideoTitleParser`: a book / chapter (and best-effort verse) found anywhere in a video title. */
object YouTubeVideoTitleParser {

    fun anchor(title: String, languages: List<BibleLanguage> = emptyList()): YouTubeVideoAnchor {
        for (candidate in candidates(title)) {
            val result = SermonReferenceParser.parse(candidate, languages)
            // A book with no chapter ("Genesis") still anchors the video, to chapter 1.
            val bookOrd = result.bookOrd ?: continue
            val realChapter = result.chapters.firstOrNull()
            val verse = realChapter?.let { verseRange(it, candidate) }
            return YouTubeVideoAnchor(bookOrd, realChapter ?: 1, verse?.first, verse?.second)
        }
        return YouTubeVideoAnchor(null, null, null, null)
    }

    /** "c:v" or "c:v-w" for the chapter already found, so an unrelated "12:5" elsewhere is ignored. */
    private fun verseRange(chapter: Int, candidate: String): Pair<Int, Int>? {
        val m = Regex("\\b$chapter:(\\d+)(?:-(\\d+))?\\b").find(candidate) ?: return null
        val start = SwiftText.asciiInt(m.groupValues[1]) ?: return null
        val end = SwiftText.asciiInt(m.groups[2]?.value) ?: start
        return start to end
    }

    /** The title from every word onwards, left to right (iOS: the parser only reads the first word). */
    private fun candidates(title: String): List<String> {
        val words = SwiftText.trim(title).split(" ").filter { it.isNotEmpty() }
        return words.indices.map { words.subList(it, words.size).joinToString(" ") }
    }
}
