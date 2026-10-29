package com.stoneofhope.stoneofhope.data.sermon

import java.util.Locale
import kotlin.math.floor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** iOS `YouTubeTranscriptSegment`. */
data class TranscriptSegment(val startSeconds: Double, val durationSeconds: Double, val text: String) {
    val endSeconds: Double get() = startSeconds + durationSeconds
}

/** iOS `ScriptCaptureDurationPreference`: how many seconds before the playhead one Script note covers. */
object ScriptCaptureDuration {
    const val KEY = "scriptCaptureDurationSeconds"
    const val DEFAULT = 10
    const val MIN = 1
    const val MAX = 30

    fun clamped(raw: Int) = raw.coerceIn(MIN, MAX)

    /** Character budget grows with the window so long clips aren't cut early. */
    fun maxCharacters(seconds: Int) = maxOf(240, clamped(seconds) * 24)
}

/** iOS `YouTubeTranscriptService.parseJSON3` / `parseTimedTextXML`. */
object TranscriptParser {

    private const val MIN_TRAILING_WORD_SECONDS = 0.15

    fun parseJson3(body: String): List<TranscriptSegment>? {
        val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val events = root["events"] as? JsonArray ?: return null
        val segments = mutableListOf<TranscriptSegment>()
        for (event in events) {
            val e = event as? JsonObject ?: continue
            val startMs = e["tStartMs"]?.jsonPrimitive?.intOrNull ?: continue
            val durationMs = e["dDurationMs"]?.jsonPrimitive?.intOrNull ?: 0
            val segs = e["segs"] as? JsonArray ?: continue
            val words = segs.mapNotNull { seg ->
                val s = seg as? JsonObject ?: return@mapNotNull null
                val text = s["utf8"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                s["tOffsetMs"]?.jsonPrimitive?.intOrNull to text
            }
            val text = words.joinToString("") { it.second }.trim()
            if (text.isEmpty()) continue
            val start = startMs / 1000.0
            val end = estimatedEnd(start, durationMs, words)
            segments += TranscriptSegment(start, maxOf(0.0, end - start), text)
        }
        return segments.ifEmpty { null }
    }

    /**
     * Auto-generated ("roll-up") captions carry word offsets and overlap on screen, so their end is
     * estimated from the words' cadence; manual tracks keep `dDurationMs`.
     */
    private fun estimatedEnd(start: Double, fallbackDurationMs: Int, words: List<Pair<Int?, String>>): Double {
        val hasCadence = words.drop(1).any { it.first != null }
        val lastOffset = words.lastOrNull()?.first
        if (!hasCadence || lastOffset == null) return start + fallbackDurationMs / 1000.0
        val offsets = words.map { (it.first ?: 0).toDouble() }
        val deltas = offsets.zipWithNext { a, b -> b - a }
        val average = if (deltas.isEmpty()) null else deltas.sum() / deltas.size
        val trailing = maxOf(MIN_TRAILING_WORD_SECONDS, (average ?: 350.0) / 1000.0)
        return start + lastOffset / 1000.0 + trailing
    }

    private val textPattern = Regex("<text start=\"([^\"]+)\"(?:\\s+dur=\"([^\"]+)\")?[^>]*>([^<]*)</text>")

    fun parseTimedTextXml(xml: String): List<TranscriptSegment>? {
        val segments = textPattern.findAll(xml).mapNotNull { m ->
            val start = m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            val duration = m.groups[2]?.value?.toDoubleOrNull() ?: 0.0
            val text = m.groupValues[3]
                .replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")
                .replace("&lt;", "<").replace("&gt;", ">")
                .trim()
            if (text.isEmpty()) null else TranscriptSegment(start, duration, text)
        }.toList()
        return segments.ifEmpty { null }
    }
}

/**
 * iOS `YouTubeTranscriptSnippetBuilder`: the captions of the last [ScriptCaptureDuration] seconds before
 * the playhead as one line, "[m:ss] …". Auto-generated captions have no punctuation, so commas and
 * full stops are guessed from the pauses between chunks (CJK text gets "，" / "。").
 */
object TranscriptSnippet {

    private const val SEGMENT_GAP_SECONDS = 1.2
    private const val COMMA_GAP_SECONDS = 0.35
    private const val SENTENCE_PUNCTUATION = "。！？.!?…、，,"

    fun noteText(timeSeconds: Double, segments: List<TranscriptSegment>, durationSeconds: Int): String? {
        val sorted = segments.sortedBy { it.startSeconds }
        if (sorted.isEmpty()) return null
        val target = ScriptCaptureDuration.clamped(durationSeconds).toDouble()
        val windowStart = maxOf(0.0, timeSeconds - target)

        var end = sorted.indexOfLast { it.startSeconds <= timeSeconds }.takeIf { it >= 0 } ?: segmentIndex(timeSeconds, sorted)
        var start = sorted.indexOfFirst { it.endSeconds > windowStart && it.startSeconds <= timeSeconds }.takeIf { it >= 0 } ?: end
        if (start > end) start = end
        end = maxOf(end, start)

        var body = ""
        for (i in start..end) {
            val gap = if (i > start) sorted[i].startSeconds - sorted[i - 1].endSeconds else 0.0
            body = join(body, sorted[i].text, gap)
        }
        body = body.replace(Regex("\\s+"), " ").trim()
        body = truncate(body, ScriptCaptureDuration.maxCharacters(durationSeconds))
        if (body.isEmpty()) return null
        return "[${timestamp(sorted[start].startSeconds)}] $body"
    }

    private fun segmentIndex(time: Double, segments: List<TranscriptSegment>): Int {
        segments.indexOfFirst { time >= it.startSeconds && time <= it.endSeconds }.takeIf { it >= 0 }?.let { return it }
        segments.indexOfFirst { it.startSeconds > time }.takeIf { it >= 0 }?.let { return maxOf(0, it - 1) }
        return maxOf(0, segments.size - 1)
    }

    private fun join(existing: String, addition: String, gapSeconds: Double): String {
        val add = addition.trim()
        if (add.isEmpty()) return existing
        if (existing.isEmpty()) return add
        // Captions that already carry punctuation are trusted as they are.
        if (existing.last() in SENTENCE_PUNCTUATION || add.first() in SENTENCE_PUNCTUATION) return joinPlain(existing, add)
        val cjk = isCjk(existing.last())
        return when {
            gapSeconds > SEGMENT_GAP_SECONDS -> existing + (if (cjk) "。" else ". ") + add
            gapSeconds > COMMA_GAP_SECONDS -> existing + (if (cjk) "，" else ", ") + add
            else -> joinPlain(existing, add)
        }
    }

    private fun joinPlain(existing: String, addition: String): String {
        val last = existing.last()
        val first = addition.first()
        if (last == ' ' || first == ' ') return existing + addition
        if (isCjk(last) || isCjk(first)) return existing + addition
        if (first.isLetter() && (last.isLetter() || last in SENTENCE_PUNCTUATION)) return "$existing $addition"
        return existing + addition
    }

    private fun isCjk(c: Char): Boolean =
        c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF || c.code in 0x3000..0x303F || c.code in 0xFF00..0xFFEF

    private fun truncate(text: String, maxCharacters: Int): String {
        if (text.length <= maxCharacters) return text
        var clipped = text.substring(0, maxCharacters)
        val lastSpace = clipped.lastIndexOf(' ')
        if (lastSpace >= 0) clipped = clipped.substring(0, lastSpace)
        return clipped.trim()
    }

    private fun timestamp(seconds: Double): String {
        val total = maxOf(0, floor(seconds).toInt())
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
    }
}
