package com.stoneofhope.stoneofhope.ui.tag

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity
import com.stoneofhope.stoneofhope.data.notes.VerseDisplay
import com.stoneofhope.stoneofhope.data.repository.TagRepository
import com.stoneofhope.stoneofhope.data.tag.TagCounts
import com.stoneofhope.stoneofhope.data.tag.TagRenameResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MyTagsState(val tags: List<TagEntity> = emptyList(), val counts: Map<String, TagCounts> = emptyMap(), val loaded: Boolean = false)

/** iOS `MyTagsViewModel` + the actions of `MyTagsView`. */
@HiltViewModel
class MyTagsViewModel @Inject constructor(private val repository: TagRepository) : ViewModel() {
    val state: StateFlow<MyTagsState> = combine(repository.observeTags(), repository.observeCounts()) { tags, counts ->
        MyTagsState(tags, counts, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyTagsState())

    fun create(name: String) {
        viewModelScope.launch { repository.create(name) }
    }

    suspend fun rename(id: String, name: String): TagRenameResult = repository.rename(id, name)

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun merge(sourceId: String, targetId: String) {
        viewModelScope.launch { repository.merge(sourceId, targetId) }
    }
}

/** A note row on the tag page: a preview of what the note says. */
data class TagNoteRow(val note: NoteEntity, val preview: String)

data class TagNotesState(
    val name: String? = null,
    val sermons: List<SermonEntity> = emptyList(),
    val videos: List<YouTubeVideoEntity> = emptyList(),
    val notes: List<TagNoteRow> = emptyList(),
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = sermons.isEmpty() && videos.isEmpty() && notes.isEmpty()
}

/** iOS `TagNotesViewModel`. Argument: [ARG_ID]. */
@HiltViewModel
class TagNotesViewModel @Inject constructor(savedState: SavedStateHandle, db: AppDatabase) : ViewModel() {
    private val tagId: String = checkNotNull(savedState.get<String>(ARG_ID))

    val state: StateFlow<TagNotesState> = combine(
        db.tagDao().observeActive(), db.noteDao().observeActive(), db.noteCommentDao().observeActive(),
        db.sermonDao().observeActive(), db.youTubeVideoDao().observePersonalCatalog(), db.userVerseDao().observeAll(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val tags = values[0] as List<TagEntity>
        val allNotes = values[1] as List<NoteEntity>
        val comments = values[2] as List<com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity>
        val sermons = values[3] as List<SermonEntity>
        val videos = values[4] as List<YouTubeVideoEntity>
        val verses = (values[5] as List<com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity>).associateBy { it.id }
        val tag = tags.firstOrNull { it.id == tagId } ?: return@combine TagNotesState(loaded = true)
        // iOS: a note shows up when it, or one of its comments, carries the tag.
        val viaComments = comments.filter { tagId in it.tagIds }.map { it.noteId }.toSet()
        val notes = allNotes.filter { tagId in it.tagIds || it.id in viaComments }.sortedByDescending { it.createdAt }
        TagNotesState(
            name = tag.name,
            sermons = sermons.filter { tagId in it.tagIds }.sortedByDescending { it.createdAt },
            videos = videos.filter { tagId in it.tagIds },
            notes = notes.map { TagNoteRow(it, VerseDisplay.displayContent(it, verses).trim().let { t -> if (t.length > 120) t.take(120) + "…" else t }) },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagNotesState())

    companion object {
        const val ARG_ID = "id"
    }
}
