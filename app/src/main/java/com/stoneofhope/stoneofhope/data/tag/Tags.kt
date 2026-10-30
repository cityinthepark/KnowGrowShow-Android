package com.stoneofhope.stoneofhope.data.tag

import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity

/**
 * How much a tag labels, live rows only (IOS_ISSUES L8 / L9: one count for the row summary and the
 * merge confirmation). Personal videos only, like the tag page.
 */
data class TagCounts(val notes: Int = 0, val comments: Int = 0, val sermons: Int = 0, val videos: Int = 0) {
    val total: Int get() = notes + comments + sermons + videos
}

object TagStats {
    fun counts(
        notes: List<NoteEntity>, comments: List<NoteCommentEntity>, sermons: List<SermonEntity>, videos: List<YouTubeVideoEntity>,
    ): Map<String, TagCounts> {
        val result = HashMap<String, TagCounts>()
        fun bump(ids: List<String>, change: (TagCounts) -> TagCounts) {
            for (id in ids.distinct()) result[id] = change(result[id] ?: TagCounts())
        }
        notes.forEach { n -> bump(n.tagIds) { it.copy(notes = it.notes + 1) } }
        comments.forEach { c -> bump(c.tagIds) { it.copy(comments = it.comments + 1) } }
        sermons.forEach { s -> bump(s.tagIds) { it.copy(sermons = it.sermons + 1) } }
        videos.forEach { v -> bump(v.tagIds) { it.copy(videos = it.videos + 1) } }
        return result
    }
}

enum class TagRenameResult { Renamed, Empty, Duplicate }

/**
 * iOS `PresetTag`: the two names with a special mark. [names] are the English and the translated
 * names, lower-cased and trimmed, as iOS matches them.
 */
enum class PresetTag(val names: Set<String>) {
    Trash(setOf("trash", "trash it", "回收站")),
    Milestone(setOf("milestone", "里程碑"));

    companion object {
        fun of(name: String): PresetTag? {
            val n = name.trim().lowercase()
            return entries.firstOrNull { n in it.names }
        }

        /**
         * iOS `PresetTag.labelText(for:)`: the mark before the name. iOS draws Trash as a coral symbol;
         * here it is the 🗑️ emoji.
         */
        fun label(name: String): String = when (of(name)) {
            Milestone -> "🏆 $name"
            Trash -> "🗑️ $name"
            null -> name
        }
    }
}
