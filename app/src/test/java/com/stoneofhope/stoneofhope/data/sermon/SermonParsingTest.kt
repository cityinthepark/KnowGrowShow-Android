package com.stoneofhope.stoneofhope.data.sermon

import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** iOS `SermonReferenceParser`, `YouTubeLinkParser`, `parseSermonInfo`, `YouTubeVideoTitleParser`, Script snippets, sermon rules. */
class SermonParsingTest {

    private val defaultLocale = Locale.getDefault()

    @Before fun english() = Locale.setDefault(Locale.US)

    @After fun restore() = Locale.setDefault(defaultLocale)

    // MARK: - Sermon reference

    @Test fun multipleChaptersKeepTheTypedText() {
        val r = SermonReferenceParser.parse("Romans 3, 5:1-3")
        assertEquals(ParsedSermonRef(45, listOf(3, 5), "Romans 3, 5:1-3", isValid = true), r)
    }

    @Test fun chineseInputDisplaysTheInterfaceLanguageName() {
        assertEquals(ParsedSermonRef(45, listOf(8), "Romans 8", isValid = true), SermonReferenceParser.parse("罗马书 8"))
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        assertEquals("罗马书 8", SermonReferenceParser.parse("Romans 8").chapterRange)
    }

    @Test fun booksWithANumberOrSeveralWordsParse() {
        // IOS_ISSUES K21: iOS reads only the first word ("1"), so these never parse there.
        assertEquals(ParsedSermonRef(62, listOf(3), "1 John 3", isValid = true), SermonReferenceParser.parse("1 John 3"))
        assertEquals(listOf(2), SermonReferenceParser.parse("Song of Solomon 2").chapters)
        assertEquals(62, SermonReferenceParser.parse("约翰一书3").bookOrd)
        assertEquals(listOf(3), SermonReferenceParser.parse("约翰一书3").chapters)
        assertEquals(listOf(5), SermonReferenceParser.parse("1Jn 5").chapters)
    }

    @Test fun iosEdgeCases() {
        // A book alone: the book is kept, no chapters, the raw text shown.
        assertEquals(ParsedSermonRef(1, emptyList(), "Genesis", isValid = false), SermonReferenceParser.parse("Genesis"))
        assertEquals(ParsedSermonRef(null, emptyList(), "Hello 3", isValid = false), SermonReferenceParser.parse("Hello 3"))
        assertEquals(ParsedSermonRef(null, emptyList(), "", isValid = false), SermonReferenceParser.parse("  "))
        // Only the first book (before ";") and nothing after an en dash.
        assertEquals("John 3", SermonReferenceParser.parse("John 3; Romans 5").chapterRange)
        assertEquals(listOf(1), SermonReferenceParser.parse("Genesis 1–3").chapters)
        // Chapter ranges, out-of-range chapters dropped, one-chapter books.
        assertEquals(listOf(2, 3, 4), SermonReferenceParser.parse("Genesis 2-4").chapters)
        assertEquals(listOf(3), SermonReferenceParser.parse("Romans 3, 99").chapters)
        assertEquals(listOf(1), SermonReferenceParser.parse("Jude 5").chapters)
    }

    @Test fun reversedChapterRangeDoesNotCrash() {
        // IOS_ISSUES K22: Swift traps on `5...3`.
        assertEquals(listOf(3, 4, 5), SermonReferenceParser.extractValidChapters("5-3", 1))
    }

    // MARK: - YouTube links

    @Test fun videoIdsFromEveryLinkShape() {
        val id = "dQw4w9WgXcQ"
        for (link in listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?v=$id&t=42s",
            "https://youtu.be/$id?si=abc123",
            "youtu.be/$id",
            "https://www.youtube.com/embed/$id",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/live/$id?feature=share",
            "https://www.youtube-nocookie.com/embed/$id",
            "youtube://www.youtube.com/watch?v=$id",
        )) {
            assertEquals(link, id, YouTubeLinkParser.videoID(link))
        }
        assertNull(YouTubeLinkParser.videoID("https://example.com/watch?v=$id"))
        assertNull(YouTubeLinkParser.videoID("https://youtu.be/abc"))
        assertEquals(id, YouTubeLinkParser.videoIDFromSharePayload("Watch this: https://youtu.be/$id?si=x."))
        assertTrue(YouTubeLinkParser.isShortsURL("https://youtube.com/shorts/$id"))
        assertFalse(YouTubeLinkParser.isShortsURL("https://youtu.be/$id"))
    }

    // MARK: - YouTube titles

    private fun info(title: String) = YouTubeSermonTitleParser.parse(title, "Grace Church", "id")

    @Test fun sermonTitleShapesFromIos() {
        assertEquals(YouTubeSermonInfo("Grace Abounds", "Romans 5", "Pastor Lee", "id"), info("Grace Abounds: Romans 5 | Pastor Lee"))
        assertEquals(YouTubeSermonInfo("Grace", "Romans 5", "Pastor Lee", "id"), info("Pastor Lee: Grace | Romans 5"))
        assertEquals(YouTubeSermonInfo("Grace", "Romans 5", "Grace Church", "id"), info("Grace | Romans 5"))
        assertEquals(YouTubeSermonInfo("Sunday Service | Live", null, "Grace Church", "id"), info("Sunday Service | Live"))
        assertEquals(YouTubeSermonInfo("Hope", null, "Pastor Lee", "id"), info("Hope - Pastor Lee"))
    }

    @Test fun referencesInTitlesStayWhole() {
        // IOS_ISSUES K4: iOS gives preacher "John 3", title "16"; and title "Romans 8:1", preacher "11".
        assertEquals(YouTubeSermonInfo("John 3:16", "John 3:16", "Pastor Kim", "id"), info("John 3:16 | Pastor Kim"))
        assertEquals(YouTubeSermonInfo("Romans 8:1-11", null, "Grace Church", "id"), info("Romans 8:1-11"))
    }

    @Test fun videoTitleAnchors() {
        assertEquals(YouTubeVideoAnchor(1, 3, 9, 9), YouTubeVideoTitleParser.anchor("\"Where Are You?\" Genesis 3:9"))
        assertEquals(YouTubeVideoAnchor(1, 1, null, null), YouTubeVideoTitleParser.anchor("Genesis overview"))
        assertEquals(YouTubeVideoAnchor(43, 3, 16, 18), YouTubeVideoTitleParser.anchor("Born again — John 3:16-18 (12:5 later)"))
        assertEquals(YouTubeVideoAnchor(null, null, null, null), YouTubeVideoTitleParser.anchor("A walk in the park"))
    }

    // MARK: - Script

    private fun seg(start: Double, dur: Double, text: String) = TranscriptSegment(start, dur, text)

    @Test fun scriptTakesTheWindowBeforeThePlayhead() {
        val segments = listOf(
            seg(0.0, 2.0, "in the beginning"), seg(2.0, 2.0, "god created"), seg(4.5, 2.0, "the heaven"),
            seg(8.0, 2.0, "and the earth"), seg(30.0, 2.0, "later words"),
        )
        // 10 s back from 9 s: everything from the start; a 0.5 s gap gets a comma, a 1.5 s gap a full stop.
        assertEquals("[0:00] in the beginning god created, the heaven. and the earth", TranscriptSnippet.noteText(9.0, segments, 10))
        // 2 s back from 9 s: only what overlaps 7–9 s.
        assertEquals("[0:08] and the earth", TranscriptSnippet.noteText(9.0, segments, 2))
        assertEquals("[0:08] 起初，神创造", TranscriptSnippet.noteText(10.0, listOf(seg(8.0, 0.5, "起初"), seg(9.0, 0.5, "神创造")), 5))
        assertNull(TranscriptSnippet.noteText(1.0, emptyList(), 10))
    }

    @Test fun json3CaptionsParse() {
        val body = """{"events":[{"tStartMs":1000,"dDurationMs":2000,"segs":[{"utf8":"Hello "},{"utf8":"world"}]},{"tStartMs":4000,"segs":[{"utf8":"\n"}]}]}"""
        assertEquals(listOf(TranscriptSegment(1.0, 2.0, "Hello world")), TranscriptParser.parseJson3(body))
        assertEquals(
            listOf(TranscriptSegment(1.5, 2.0, "Tom & Jerry")),
            TranscriptParser.parseTimedTextXml("""<transcript><text start="1.5" dur="2.0">Tom &amp; Jerry</text></transcript>"""),
        )
    }

    // MARK: - Rules

    @Test fun dayKeysAreGregorianWithAsciiDigits() {
        // IOS_ISSUES K2: iOS formats with the device calendar.
        for (locale in listOf(Locale.forLanguageTag("th-TH-u-ca-buddhist"), Locale.forLanguageTag("ar-EG"), Locale.forLanguageTag("ja-JP-u-ca-japanese"))) {
            Locale.setDefault(locale)
            assertEquals("20261028", SermonRules.dayKey(LocalDate.of(2026, 10, 28)))
        }
        assertEquals(LocalDate.of(2026, 3, 1), SermonRules.day("20260301"))
        assertNull(SermonRules.day("2026031"))
    }

    @Test fun defaultTitlesGetASuffix() {
        assertEquals("Oct 28", SermonRules.uniqueTitle("Oct 28", listOf("Other")))
        assertEquals("Oct 28 - 2", SermonRules.uniqueTitle("Oct 28", listOf("Oct 28")))
        assertEquals("Oct 28 - 6", SermonRules.uniqueTitle("Oct 28", listOf("Oct 28", "Oct 28 - 2", "Oct 28 - 5", "Oct 28 - x")))
    }

    private fun series(id: String, bookOrd: Int?, startedOn: String, finished: Boolean = false, user: Boolean = true) =
        SermonSeriesEntity(id = id, title = id, bookOrd = bookOrd, startedOn = startedOn, isFinished = finished, isUserData = user)

    @Test fun seriesDetectionOnlyJoinsTheSameBook() {
        val today = LocalDate.of(2026, 10, 28)
        val all = listOf(
            series("romansOld", 45, "20260301"),
            series("romans", 45, "20260801"),
            series("romansNewerFinished", 45, "20260901", finished = true),
            series("romansChurch", 45, "20261001", user = false),
            series("topical", null, "20261020"),
        )
        assertEquals("romans", SermonRules.detectSeries(45, all, today)?.id)
        // iOS would fall back to "topical" here (IOS_ISSUES K3).
        assertNull(SermonRules.detectSeries(43, all, today))
        assertNull(SermonRules.detectSeries(null, all, today))
        // Started more than six months ago.
        assertNull(SermonRules.detectSeries(45, listOf(series("old", 45, "20260427")), today))
    }

    private fun sermon(id: String, f: SermonEntity.() -> SermonEntity = { this }) =
        SermonEntity(id = id, title = id, createdAt = Instant.EPOCH).f()

    @Test fun mergeTargetGetsWhatItLacks() {
        val target = sermon("t") { copy(audioUrl = "a-target", tagIds = listOf("x")) }
        val source = sermon("s") {
            copy(
                youtubeID = "vid", videoUrl = "https://www.youtube.com/watch?v=vid", audioUrl = "a-source", pdfUrl = "p",
                bookOrd = 45, chapters = listOf(5), chapterRange = "Romans 5", preacher = "Lee", intro = "i", seriesId = "sr",
                tagIds = listOf("x", "y"),
            )
        }
        val filled = SermonRules.filledFrom(target, source)
        assertEquals("a-target", filled.audioUrl)
        assertEquals("vid", filled.youtubeID)
        assertEquals("p", filled.pdfUrl)
        assertEquals(listOf(5), filled.chapters)
        assertEquals("Romans 5", filled.chapterRange)
        assertEquals("Lee", filled.preacher)
        assertEquals("sr", filled.seriesId)
        assertEquals(listOf("x", "y"), filled.tagIds)
        assertEquals("t", filled.title)
    }

    @Test fun mergeOffersOnlyEmptySermons() {
        val a = sermon("A"); val b = sermon("b"); val c = sermon("C")
        val targets = SermonRules.mergeTargets(a, listOf(a, b, c), mapOf("A" to 2, "b" to 1))
        assertEquals(listOf("C", "b"), targets.map { it.sermon.id })
        assertEquals(listOf(true, false), targets.map { it.canMerge })
        assertTrue(SermonRules.canOfferMerge(noteCount = 1, sermonsThatDay = 2))
        assertFalse(SermonRules.canOfferMerge(noteCount = 0, sermonsThatDay = 2))
        assertFalse(SermonRules.canOfferMerge(noteCount = 3, sermonsThatDay = 1))
    }
}
