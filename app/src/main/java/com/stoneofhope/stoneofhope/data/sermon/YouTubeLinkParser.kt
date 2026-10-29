package com.stoneofhope.stoneofhope.data.sermon

import com.stoneofhope.stoneofhope.data.bible.SwiftText
import java.net.URI
import java.net.URLDecoder

/**
 * iOS `YouTubeLinkParser`: the video id in `youtube.com/watch?v=…`, `youtu.be/…`, `/embed/…`,
 * `/shorts/…`, `/live/…`, `/v/…`, `youtube://` links, and in share-sheet text that contains one.
 */
object YouTubeLinkParser {

    fun videoID(urlString: String): String? {
        val trimmed = SwiftText.trim(urlString)
        if (trimmed.isEmpty()) return null
        fromResolved(trimmed)?.let { return it }
        if (!trimmed.contains("://")) fromResolved("https://$trimmed")?.let { return it }
        return null
    }

    /** iOS `videoID(fromSharePayload:)`: a bare link, or the first YouTube link inside shared text. */
    fun videoIDFromSharePayload(text: String): String? =
        videoID(text) ?: firstYouTubeURLString(text)?.let(::videoID)

    fun firstYouTubeURLString(text: String): String? {
        val trimmed = SwiftText.trim(text)
        if (trimmed.isEmpty()) return null
        if (videoID(trimmed) != null) return trimmed
        // iOS asks NSDataDetector for links first.
        for (m in Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE).findAll(trimmed)) {
            val candidate = strippedTrailingPunctuation(m.value)
            if (videoID(candidate) != null) return candidate
        }
        val m = youtubeLinkPattern.find(trimmed) ?: return null
        val raw = strippedTrailingPunctuation(m.value)
        val candidate = if (raw.contains("://")) raw else "https://$raw"
        return candidate.takeIf { videoID(it) != null }
    }

    /** iOS `canonicalWatchURLString(from:)`. */
    fun canonicalWatchURL(payload: String): String? = videoIDFromSharePayload(payload)?.let(::watchUrl)

    fun watchUrl(videoID: String) = "https://www.youtube.com/watch?v=$videoID"

    /** iOS fixed thumbnail for a video id (`hqdefault`). */
    fun thumbnailUrl(videoID: String) = "https://img.youtube.com/vi/$videoID/hqdefault.jpg"

    fun isShortsURL(urlString: String): Boolean {
        val uri = resolvedURI(urlString) ?: return false
        if (uri.host?.contains("youtube.com", ignoreCase = true) != true) return false
        return uri.rawPath.orEmpty().lowercase().startsWith("/shorts/")
    }

    private val youtubeLinkPattern = Regex(
        "(?:https?://)?(?:www\\.|m\\.)?(?:youtube\\.com|youtu\\.be)/[^\\s<>\"']+|youtube://[^\\s<>\"']+|vnd\\.youtube://[^\\s<>\"']+",
        RegexOption.IGNORE_CASE,
    )

    private fun fromResolved(urlString: String): String? {
        val uri = parse(strippedTrailingPunctuation(urlString)) ?: return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val queryID = queryValue(uri, "v")?.let(SwiftText::trim)

        if (scheme == "youtube" || scheme == "vnd.youtube") {
            if (queryID != null && isLikelyVideoID(queryID)) return queryID
            if (host.contains("youtube.com") || host.contains("youtu.be")) {
                return fromResolved("https" + urlString.substring(urlString.indexOf("://")))
            }
        }

        if (host == "youtu.be" || host.endsWith(".youtu.be")) {
            val id = SwiftText.trim(uri.rawPath.orEmpty().trimEnd('/').substringAfterLast('/'))
            return id.takeIf(::isLikelyVideoID)
        }

        val isYouTubeHost = host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
        if (!isYouTubeHost) return null

        val path = uri.rawPath.orEmpty()
        if ((path == "/watch" || path.startsWith("/watch/")) && queryID != null && isLikelyVideoID(queryID)) return queryID
        for (prefix in listOf("/embed/", "/shorts/", "/live/", "/v/")) {
            if (!path.lowercase().startsWith(prefix)) continue
            val id = path.substring(prefix.length).split("/").firstOrNull()?.let(SwiftText::trim)
            if (!id.isNullOrEmpty() && isLikelyVideoID(id)) return id
        }
        return null
    }

    private fun parse(s: String): URI? = runCatching { URI(s) }.getOrNull()

    private fun resolvedURI(urlString: String): URI? {
        val trimmed = strippedTrailingPunctuation(SwiftText.trim(urlString))
        parse(trimmed)?.takeIf { it.host != null }?.let { return it }
        return if (!trimmed.contains("://")) parse("https://$trimmed") else null
    }

    private fun queryValue(uri: URI, name: String): String? =
        uri.rawQuery?.split("&")?.firstNotNullOfOrNull { pair ->
            val key = pair.substringBefore("=")
            if (key == name) runCatching { URLDecoder.decode(pair.substringAfter("=", ""), "UTF-8") }.getOrNull() else null
        }

    /** 8–20 letters, digits, "-" or "_". */
    private fun isLikelyVideoID(value: String): Boolean =
        value.length in 8..20 && value.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    /** `trimmingCharacters(in: ".,;:!?)]}>\"'")` — both ends, as Swift does. */
    private fun strippedTrailingPunctuation(value: String): String = value.trim { it in ".,;:!?)]}>\"'" }
}
