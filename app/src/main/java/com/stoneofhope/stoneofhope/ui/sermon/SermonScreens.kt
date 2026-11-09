package com.stoneofhope.stoneofhope.ui.sermon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.bible.BibleBooks
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.bible.BookChapter
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity
import com.stoneofhope.stoneofhope.data.repository.NoteActionRepository
import com.stoneofhope.stoneofhope.data.sermon.ScriptCapturePreferences
import com.stoneofhope.stoneofhope.data.sermon.SermonRepository
import com.stoneofhope.stoneofhope.data.sermon.SermonRules
import com.stoneofhope.stoneofhope.data.sermon.TranscriptSegment
import com.stoneofhope.stoneofhope.data.sermon.TranscriptSnippet
import com.stoneofhope.stoneofhope.data.sermon.VideoRepository
import com.stoneofhope.stoneofhope.data.sermon.YouTubeVideoTitleParser
import com.stoneofhope.stoneofhope.data.sermon.YouTubeWeb
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.ui.bible.BibleRefLanguages
import com.stoneofhope.stoneofhope.ui.notes.NoteAction
import com.stoneofhope.stoneofhope.ui.notes.NoteActionDispatcher
import com.stoneofhope.stoneofhope.ui.notes.TagPickerSheet
import com.stoneofhope.stoneofhope.ui.notes.TagPill
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A row read from Room: [loaded] is false until the first emission, so "not found" never flashes. */
data class Loaded<T>(val loaded: Boolean = false, val value: T? = null)

/** How the sermon page treats its video when opened (iOS `SermonNoteVideoEntry`). */
enum class VideoEntry { Default, Autoplay, Suppressed }

/** Shared by the sermon and video pages: languages, the Script window and its transcript cache. */
abstract class ContainerViewModel(
    private val web: YouTubeWeb,
    private val scriptPrefs: ScriptCapturePreferences,
    bibleVersions: BibleVersionPreferences,
) : ViewModel() {
    protected val zone: ZoneId get() = ZoneId.systemDefault()
    private val selected = bibleVersions.selectedVersions()

    val bibleRefLanguages = BibleRefLanguages(
        matching = BibleVersions.bookMatchingLanguages(selected),
        display = BibleVersions.primaryBookDisplayLanguage(selected),
        secondary = BibleVersions.secondaryBookDisplayLanguage(selected),
    )

    /** iOS `selectedBibleVersionsBracketed`: "[KJV] [CUV]". */
    val versionsLabel: String = selected.joinToString(" ") { "[" + BibleVersions.toDisplay(it) + "]" }

    private val _scriptSeconds = MutableStateFlow(scriptPrefs.seconds)
    val scriptSeconds: StateFlow<Int> = _scriptSeconds.asStateFlow()

    fun setScriptSeconds(value: Int) {
        scriptPrefs.seconds = value
        _scriptSeconds.value = scriptPrefs.seconds
    }

    private var transcript: List<TranscriptSegment>? = null
    private var transcriptFetched = false

    /** "Book c" for the note sheet's reference field. */
    fun referenceText(chapter: BookChapter?): String =
        chapter?.let { "${BibleBooks.name(it.bookOrd, bibleRefLanguages.display)} ${it.chapterOrd}" }.orEmpty()

    /** iOS Script button: the captions before the playhead, saved as a note, or nothing when there are none. */
    protected suspend fun scriptText(videoID: String, time: Double?): String? {
        if (time == null) return null
        if (!transcriptFetched) {
            transcript = web.transcript(videoID)
            transcriptFetched = transcript != null
        }
        val segments = transcript ?: return null
        return TranscriptSnippet.noteText(time, segments, scriptSeconds.value)
    }
}

@HiltViewModel
class SermonViewModel @Inject constructor(
    savedState: SavedStateHandle,
    db: AppDatabase,
    private val sermons: SermonRepository,
    private val actions: NoteActionRepository,
    private val audio: SermonAudioPlayer,
    web: YouTubeWeb,
    scriptPrefs: ScriptCapturePreferences,
    bibleVersions: BibleVersionPreferences,
    private val clock: Clock,
) : ContainerViewModel(web, scriptPrefs, bibleVersions) {

    val sermonId: String = checkNotNull(savedState[ARG_ID])
    val entry: VideoEntry = VideoEntry.entries[savedState.get<Int>(ARG_ENTRY) ?: 0]
    val notice: SermonNotice = SermonNotice.entries[savedState.get<Int>(ARG_NOTICE) ?: 0]

    /** The chapter the page was opened from (iOS `defaultBookChapter`). */
    private val fromChapter: BookChapter? = run {
        val b = savedState.get<Int>(ARG_BOOK) ?: -1
        val c = savedState.get<Int>(ARG_CHAPTER) ?: -1
        if (b > 0 && c > 0) BookChapter(b, c) else null
    }

    /** A deleted sermon reads as missing. */
    val sermonState: StateFlow<Loaded<SermonEntity>> = db.sermonDao().observeById(sermonId)
        .map { Loaded(true, it?.takeIf { s -> s.deletedAt == null }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loaded())

    private val sermon get() = sermonState.value

    val notes: StateFlow<ContainerNotesState> = containerNotes(db, db.noteDao().observeForSermons(listOf(sermonId)), zone)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContainerNotesState())

    /** iOS `anchorChapter`: the chapter the page came from, else the sermon's first. */
    fun anchorChapter(s: SermonEntity): BookChapter? =
        fromChapter ?: s.bookOrd?.let { b -> s.chapters.firstOrNull()?.let { BookChapter(b, it) } }

    fun saveNote(content: String, ref: String?) {
        val s = sermon.value ?: return
        viewModelScope.launch { sermons.saveNote(sermonId, content, ref, anchorChapter(s), zone) }
    }

    fun onAction(note: NoteEntity, action: NoteAction) {
        viewModelScope.launch { NoteActionDispatcher.dispatch(actions, note, action, zone = zone) }
    }

    fun playAudio() {
        val s = sermon.value ?: return
        val url = s.audioUrl?.trim()?.ifEmpty { null } ?: return
        audio.play(url, s.title, s.chapterRange?.trim()?.ifEmpty { null }, s.preacher, s.id)
    }

    suspend fun captureScript(time: Double?): ScriptResult {
        val s = sermon.value ?: return ScriptResult.NoTranscript
        val videoID = SermonRules.youTubeID(s) ?: return ScriptResult.NoTranscript
        val text = scriptText(videoID, time) ?: return ScriptResult.NoTranscript
        sermons.saveScriptText(sermonId, text, anchorChapter(s), zone)
        return ScriptResult.Saved
    }

    fun today() = clock.instant().atZone(zone).toLocalDate()

    companion object {
        const val ARG_ID = "sermonId"
        const val ARG_BOOK = "book"
        const val ARG_CHAPTER = "chapter"
        const val ARG_ENTRY = "entry"
        const val ARG_NOTICE = "notice"
    }
}

/** iOS `SermonNoteView`. */
@Composable
fun SermonScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onEdit: (String) -> Unit,
    viewModel: SermonViewModel = hiltViewModel(),
) {
    val sermonState by viewModel.sermonState.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val scriptSeconds by viewModel.scriptSeconds.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    NoticeEffect(viewModel.notice, snackbar)

    val s = sermonState.value
    if (s == null) {
        MissingPage(stringResource(R.string.sermon_not_found), loaded = sermonState.loaded, onBack = onBack)
        return
    }
    val c = AppTheme.colors
    ContainerPage(
        title = s.title.ifBlank { stringResource(R.string.sermon_untitled) },
        notes = notes,
        videoID = SermonRules.youTubeID(s).takeIf { viewModel.entry != VideoEntry.Suppressed },
        thumbnailUrl = null,
        autoplay = viewModel.entry == VideoEntry.Autoplay,
        sectionDay = SermonRules.day(s.preachedOn) ?: viewModel.today(),
        bibleRefLanguages = viewModel.bibleRefLanguages,
        defaultReference = viewModel.referenceText(viewModel.anchorChapter(s)),
        scriptSeconds = scriptSeconds,
        onScriptSeconds = viewModel::setScriptSeconds,
        onScript = viewModel::captureScript,
        onBack = onBack,
        onOpenNote = onOpenNote,
        onAction = viewModel::onAction,
        onSaveNote = viewModel::saveNote,
        snackbar = snackbar,
        actions = {
            if (s.isUserData) TextButton(onClick = { onEdit(s.id) }, modifier = Modifier.testTag("sermonEdit")) { Text(stringResource(R.string.action_edit)) }
        },
        infoBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    s.chapterRange?.trim()?.takeIf { it.isNotEmpty() }?.let { ReferenceChip(it) }
                    Spacer(Modifier.weight(1f))
                    Text(viewModel.versionsLabel, style = textStyle(12, weight = FontWeight.SemiBold), color = c.blue80)
                }
                val audio = s.audioUrl?.trim()?.ifEmpty { null }
                val pdf = s.pdfUrl?.trim()?.ifEmpty { null }
                if (audio != null || pdf != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (audio != null) {
                            IconButton(onClick = viewModel::playAudio, modifier = Modifier.testTag("sermonAudio")) {
                                Icon(Icons.Outlined.Headphones, stringResource(R.string.sermon_play_audio), tint = c.blue80)
                            }
                        }
                        if (pdf != null) {
                            IconButton(onClick = { openUrl(context, pdf) }, modifier = Modifier.testTag("sermonPdf")) {
                                Icon(Icons.Outlined.Description, stringResource(R.string.sermon_open_pdf), tint = c.blue80)
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun ReferenceChip(text: String) {
    val c = AppTheme.colors
    Surface(shape = RoundedCornerShape(50), color = c.bibleVerseChip.background, contentColor = c.bibleVerseChip.foreground) {
        Text("✝️ $text", style = textStyle(13, weight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** The duplicate notice iOS meant to show (D3), once per page. */
@Composable
internal fun NoticeEffect(notice: SermonNotice, snackbar: SnackbarHostState) {
    var shown by rememberSaveable { mutableStateOf(false) }
    val message = when (notice) {
        SermonNotice.None -> null
        SermonNotice.SermonExists -> stringResource(R.string.notice_sermon_exists)
        SermonNotice.VideoExists -> stringResource(R.string.notice_video_exists)
        SermonNotice.LinkIsVideo -> stringResource(R.string.notice_link_is_video)
        SermonNotice.LinkIsSermon -> stringResource(R.string.notice_link_is_sermon)
    }
    LaunchedEffect(notice) {
        if (shown || message == null) return@LaunchedEffect
        shown = true
        snackbar.showSnackbar(message)
    }
}

@Composable
internal fun MissingPage(message: String, loaded: Boolean, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
        if (loaded) Text(message, color = AppTheme.colors.neutral90, modifier = Modifier.align(Alignment.Center))
    }
}

// MARK: - Video page

@HiltViewModel
class VideoViewModel @Inject constructor(
    savedState: SavedStateHandle,
    db: AppDatabase,
    private val videos: VideoRepository,
    private val actions: NoteActionRepository,
    web: YouTubeWeb,
    scriptPrefs: ScriptCapturePreferences,
    bibleVersions: BibleVersionPreferences,
    private val clock: Clock,
) : ContainerViewModel(web, scriptPrefs, bibleVersions) {

    val youtubeID: String = checkNotNull(savedState[ARG_ID])
    val notice: SermonNotice = SermonNotice.entries[savedState.get<Int>(SermonViewModel.ARG_NOTICE) ?: 0]

    /** Personal rows only; a sermon's index row is not a video (iOS `personalYouTubeVideo`). */
    val videoState: StateFlow<Loaded<YouTubeVideoEntity>> = db.youTubeVideoDao().observeById(youtubeID)
        .map { Loaded(true, it?.takeIf { v -> v.deletedAt == null && v.sermonId == null }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loaded())

    private val video get() = videoState.value

    val notes: StateFlow<ContainerNotesState> = containerNotes(db, db.noteDao().observeForVideos(listOf(youtubeID)), zone)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContainerNotesState())

    fun anchorChapter(v: YouTubeVideoEntity): BookChapter? =
        v.bookOrd?.let { b -> v.chapterOrd?.let { BookChapter(b, it) } }

    /**
     * iOS `displayBibleReferenceText`: "Genesis 3", or "Genesis 3:9" when the title's verse is in the
     * same book and chapter as the stored anchor.
     */
    fun referenceLabel(v: YouTubeVideoEntity): String? {
        val chapter = anchorChapter(v) ?: return null
        val name = BibleBooks.name(chapter.bookOrd, bibleRefLanguages.display)
        val fromTitle = YouTubeVideoTitleParser.anchor(v.title, bibleRefLanguages.matching)
        val start = fromTitle.verseStart
        if (fromTitle.bookOrd != chapter.bookOrd || fromTitle.chapterOrd != chapter.chapterOrd || start == null) return "$name ${chapter.chapterOrd}"
        val end = fromTitle.verseEnd
        return if (end != null && end != start) "$name ${chapter.chapterOrd}:$start-$end" else "$name ${chapter.chapterOrd}:$start"
    }

    fun saveNote(content: String, ref: String?) {
        val v = video.value ?: return
        viewModelScope.launch { videos.saveNote(youtubeID, content, ref, anchorChapter(v), zone) }
    }

    fun onAction(note: NoteEntity, action: NoteAction) {
        viewModelScope.launch { NoteActionDispatcher.dispatch(actions, note, action, zone = zone) }
    }

    fun addTag(name: String) { viewModelScope.launch { videos.addTag(youtubeID, name) } }

    fun removeTag(tagId: String) { viewModelScope.launch { videos.removeTag(youtubeID, tagId) } }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch { videos.delete(youtubeID); onDone() }
    }

    suspend fun captureScript(time: Double?): ScriptResult {
        val v = video.value ?: return ScriptResult.NoTranscript
        val text = scriptText(youtubeID, time) ?: return ScriptResult.NoTranscript
        videos.saveScriptText(youtubeID, text, anchorChapter(v), zone)
        return ScriptResult.Saved
    }

    fun today() = clock.instant().atZone(zone).toLocalDate()

    companion object {
        const val ARG_ID = "youtubeID"
    }
}

/** iOS `YouTubeVideoNoteView`. */
@Composable
fun VideoScreen(onBack: () -> Unit, onOpenNote: (String) -> Unit, viewModel: VideoViewModel = hiltViewModel()) {
    val videoState by viewModel.videoState.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val scriptSeconds by viewModel.scriptSeconds.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var tagsOpen by rememberSaveable { mutableStateOf(false) }
    NoticeEffect(viewModel.notice, snackbar)

    val v = videoState.value
    if (v == null) {
        MissingPage(stringResource(R.string.video_not_found), loaded = videoState.loaded, onBack = onBack)
        return
    }
    val c = AppTheme.colors
    val tagNames = notes.allTags.filter { it.id in v.tagIds }.map { it.name }
    ContainerPage(
        title = v.title,
        notes = notes,
        videoID = v.youtubeID,
        thumbnailUrl = v.thumbnailUrl,
        autoplay = false,
        sectionDay = v.createdAt.atZone(ZoneId.systemDefault()).toLocalDate(),
        bibleRefLanguages = viewModel.bibleRefLanguages,
        defaultReference = viewModel.referenceText(viewModel.anchorChapter(v)),
        scriptSeconds = scriptSeconds,
        onScriptSeconds = viewModel::setScriptSeconds,
        onScript = viewModel::captureScript,
        onBack = onBack,
        onOpenNote = onOpenNote,
        onAction = viewModel::onAction,
        onSaveNote = viewModel::saveNote,
        snackbar = snackbar,
        actions = {
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("videoDelete")) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
            }
        },
        infoBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(v.channelName.orEmpty(), style = textStyle(14, weight = FontWeight.SemiBold), color = c.neutral90, modifier = Modifier.weight(1f))
                    viewModel.referenceLabel(v)?.let { ReferenceChip(it) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        onClick = { tagsOpen = true }, shape = RoundedCornerShape(50), color = c.surface0,
                        border = BorderStroke(1.dp, c.neutral20), modifier = Modifier.testTag("videoTags"),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Sell, null, modifier = Modifier.size(14.dp), tint = c.blue80)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(if (tagNames.isEmpty()) R.string.tags_add else R.string.action_tags), style = textStyle(12), color = c.blue80)
                        }
                    }
                    tagNames.forEach { TagPill(it, compact = true) }
                }
            }
        },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.video_delete_title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }, modifier = Modifier.testTag("confirmDeleteVideo")) {
                    Text(stringResource(R.string.video_delete), color = c.red80)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (tagsOpen) {
        TagPickerSheet(notes.allTags, v.tagIds.toSet(), onAdd = viewModel::addTag, onRemove = viewModel::removeTag, onDismiss = { tagsOpen = false })
    }
}
