package com.stoneofhope.stoneofhope.data.model

/** iOS `NoteVerseAnchor`. */
object NoteVerseAnchor {
    /** U+00B7 MIDDLE DOT. `Note.content` of a verse anchor; anchors never store user text or verse body. */
    const val PLACEHOLDER = "·"

    /** A lone `"."` counts as a placeholder too, so note counts skip it. */
    fun isPlaceholderContent(content: String): Boolean {
        val trimmed = content.trim()
        return trimmed.isEmpty() || trimmed == PLACEHOLDER || trimmed == "."
    }
}
