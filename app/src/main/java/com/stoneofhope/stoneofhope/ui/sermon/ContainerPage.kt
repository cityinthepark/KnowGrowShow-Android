package com.stoneofhope.stoneofhope.ui.sermon

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.notes.NoteRelations
import com.stoneofhope.stoneofhope.data.notes.NoteThread
import com.stoneofhope.stoneofhope.data.notes.ThreadItem
import com.stoneofhope.stoneofhope.data.notes.threadItem
import com.stoneofhope.stoneofhope.data.notes.VerseMarks
import com.stoneofhope.stoneofhope.data.sermon.ScriptCaptureDuration
import com.stoneofhope.stoneofhope.data.sermon.YouTubeLinkParser
import com.stoneofhope.stoneofhope.ui.bible.BibleRefLanguages
import com.stoneofhope.stoneofhope.ui.notes.NoteAction
import com.stoneofhope.stoneofhope.ui.notes.NoteActionsHost
import com.stoneofhope.stoneofhope.ui.notes.NoteCardUi
import com.stoneofhope.stoneofhope.ui.notes.NoteCards
import com.stoneofhope.stoneofhope.ui.notes.NoteListCard
import com.stoneofhope.stoneofhope.ui.notes.VerseAnchorCard
import com.stoneofhope.stoneofhope.ui.notes.VerseBackfillEffect
import com.stoneofhope.stoneofhope.ui.notes.rememberNoteActionsState
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import com.stoneofhope.stoneofhope.ui.today.ContainerNoteSheet
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The notes of one sermon or video, as cards, oldest first (iOS `NoteListViewModel(filter: .forSermon)`). */
data class ContainerNotesState(
    val cards: List<NoteCardUi> = emptyList(),
    val allTags: List<TagEntity> = emptyList(),
    val loaded: Boolean = false,
) {
    fun actionTarget(noteId: String) = cards.firstOrNull { it.note.id == noteId }?.actionTarget()
}

/** Cards for [notes] (the container's active notes); reflections stay under their anchor. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun containerNotes(db: AppDatabase, notes: Flow<List<NoteEntity>>, zone: ZoneId, selectedVersions: List<String>): Flow<ContainerNotesState> {
    val comments = notes.flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyList()) else db.noteCommentDao().observeActiveForNotes(list.map { it.id })
    }
    return combine(
        notes, comments, db.tagDao().observeActiveByCreation(), db.userVerseDao().observeAll(),
        combine(db.indexedTextDao().observeActiveVerseMarks(), db.sermonDao().observeActiveIds(), ::Pair),
    ) { list, c, tags, verses, (markRows, sermonIds) ->
        val tagNames = tags.associate { it.id to it.name }
        val versesById = verses.associateBy { it.id }
        val marks = VerseMarks.byVerseId(markRows)
        val cards = list.filter { !NoteRelations.isHiddenFromTopLevel(it, list, zone) }
            .sortedBy { it.createdAt }
            .map { note ->
                val card = NoteCards.card(note, list, c, tagNames, sermonIds.toSet(), versesById, zone, marks, selectedVersions)
                val reflections = if (card.isAnchor) {
                    NoteRelations.reflections(note, list, zone).map {
                        ThreadItem(it.id, it.content, false, it.createdAt, it.starLevel, it.tagIds, it.tagIds.mapNotNull(tagNames::get), isReflection = true)
                    }
                } else emptyList()
                val comments = c.filter { it.noteId == note.id }.map { it.threadItem(tagNames) }
                card.copy(inline = NoteThread.inline(reflections, comments))
            }
        ContainerNotesState(cards, tags, loaded = true)
    }
}

/** What the Script button reports. */
enum class ScriptResult { Saved, NoTranscript }

/** iOS `verseTextUnavailable` (userInfo "offline"): why a just-saved verse has no text yet. */
enum class VerseTextNotice { Offline, Unavailable }

/** iOS shows the verse-text notice for 6 seconds. */
private const val VERSE_NOTICE_MILLIS = 6_000L

/**
 * iOS `ContainerNoteListView`, shared by the sermon and video pages: the inline video (full screen when
 * the phone is turned, as on iOS, or with the full-screen button), the page's info bar, the notes, and
 * "＋" for a new note. iOS also has camera / photo / frame capture here; those come with images later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerPage(
    title: String,
    notes: ContainerNotesState,
    videoID: String?,
    thumbnailUrl: String?,
    autoplay: Boolean,
    sectionDay: LocalDate?,
    bibleRefLanguages: BibleRefLanguages,
    scriptSeconds: Int,
    onScriptSeconds: (Int) -> Unit,
    onScript: suspend (time: Double?) -> ScriptResult,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onAction: (NoteEntity, NoteAction) -> Unit,
    onSaveNote: (content: String, chapterNote: Boolean) -> Unit,
    isOnline: Boolean,
    onBackfillVerse: (noteId: String) -> Unit,
    verseNotices: Flow<VerseTextNotice>,
    snackbar: SnackbarHostState,
    actions: @Composable () -> Unit,
    infoBar: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val player = videoID?.let { rememberYouTubePlayerState(it, autoplay) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullScreen = player != null && landscape
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var durationOpen by rememberSaveable { mutableStateOf(false) }
    val noteActions = rememberNoteActionsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var lastCount by remember { mutableIntStateOf(-1) }
    val scriptSaved = stringResource(R.string.script_saved)
    val scriptNone = stringResource(R.string.script_none)
    val verseOffline = stringResource(R.string.verse_text_offline)
    val verseUnavailable = stringResource(R.string.verse_text_unavailable)

    LaunchedEffect(verseNotices) {
        verseNotices.collectLatest { notice ->
            val message = if (notice == VerseTextNotice.Offline) verseOffline else verseUnavailable
            // Leaving the timeout dismisses it.
            withTimeoutOrNull(VERSE_NOTICE_MILLIS) { snackbar.showSnackbar(message, duration = SnackbarDuration.Indefinite) }
        }
    }

    // iOS scrolls to the newest note after adding one.
    LaunchedEffect(notes.cards.size) {
        if (lastCount >= 0 && notes.cards.size > lastCount) listState.animateScrollToItem(notes.cards.size)
        if (notes.loaded) lastCount = notes.cards.size
    }
    FullScreenSystemBars(fullScreen)
    if (fullScreen) BackHandler { context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }

    Box(Modifier.fillMaxSize().background(if (fullScreen) Color.Black else c.pageBackground)) {
        if (player != null && landscape) {
            YouTubePlayerView(player, thumbnailUrl, Modifier.fillMaxSize())
            IconButton(
                onClick = { context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED },
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding(),
            ) { Icon(Icons.Outlined.FullscreenExit, stringResource(R.string.exit_full_screen), tint = Color.White) }
            return@Box
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                        if (player != null) {
                            IconButton(onClick = { durationOpen = true }, modifier = Modifier.size(32.dp).testTag("scriptDuration")) {
                                Icon(Icons.Outlined.Settings, stringResource(R.string.script_duration_title), tint = c.neutral90, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.pageBackground),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
            )
            if (player != null) {
                YouTubePlayerView(player, thumbnailUrl, Modifier.videoAspect())
                VideoToolsRow(
                    videoID = player.videoID,
                    onScript = {
                        val result = onScript(player.currentTime())
                        scope.launch { snackbar.showSnackbar(if (result == ScriptResult.Saved) scriptSaved else scriptNone) }
                    },
                    onFullScreen = { context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE },
                )
            }
            infoBar()
            HorizontalDivider(color = c.neutral20)
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("containerNotes"),
            ) {
                items(notes.cards, key = { it.note.id }) { card ->
                    VerseBackfillEffect(card, isOnline, onBackfillVerse)
                    if (card.isAnchor) {
                        VerseAnchorCard(card, sectionDay, onOpen = { onOpenNote(card.note.id) }, onMenu = { noteActions.open(card.note.id) })
                    } else {
                        NoteListCard(
                            card = card, sectionDay = sectionDay, maxLines = 6, verseExpanded = false, verse = null,
                            onOpen = { onOpenNote(card.note.id) }, onMenu = { noteActions.open(card.note.id) },
                            onToggleVerse = { onOpenNote(card.note.id) },
                        )
                    }
                }
                if (notes.loaded && notes.cards.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.sermon_notes_empty), style = textStyle(15), color = c.neutral90,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp).testTag("containerEmpty"),
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { sheetOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp).testTag("containerAddNote"),
        ) { Icon(Icons.Filled.Add, stringResource(R.string.new_note)) }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
    }

    if (sheetOpen) {
        ContainerNoteSheet(
            bibleRefLanguages = bibleRefLanguages,
            onDismiss = { sheetOpen = false },
            onSave = { content, chapterNote -> sheetOpen = false; onSaveNote(content, chapterNote) },
        )
    }
    if (durationOpen) ScriptDurationDialog(scriptSeconds, onScriptSeconds) { durationOpen = false }
    NoteActionsHost(state = noteActions, target = noteActions.noteId?.let(notes::actionTarget), allTags = notes.allTags, onAction = onAction)
    // Leaving the page puts the orientation back.
    DisposableEffect(Unit) { onDispose { context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }
}

/** Under the video: Script, open in YouTube, full screen (iOS draws these over the video). */
@Composable
private fun VideoToolsRow(videoID: String, onScript: suspend () -> Unit, onFullScreen: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = {
                if (!busy) scope.launch { busy = true; try { onScript() } finally { busy = false } }
            },
            label = { Text(stringResource(R.string.script_capture)) },
            leadingIcon = {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Subtitles, null, modifier = Modifier.size(18.dp))
            },
            modifier = Modifier.padding(start = 8.dp).testTag("scriptCapture"),
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { openUrl(context, YouTubeLinkParser.watchUrl(videoID)) }) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.open_in_youtube))
        }
        IconButton(onClick = onFullScreen, modifier = Modifier.testTag("fullScreen")) {
            Icon(Icons.Outlined.Fullscreen, stringResource(R.string.full_screen))
        }
    }
}

/** iOS `scriptDurationQuickSettingsSheet`: 1–30 seconds, default 10. */
@Composable
private fun ScriptDurationDialog(seconds: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(seconds.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.script_duration_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val n = value.roundToInt()
                Text(pluralStringResource(R.plurals.script_duration_value, n, n), style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = value, onValueChange = { value = it },
                    valueRange = ScriptCaptureDuration.MIN.toFloat()..ScriptCaptureDuration.MAX.toFloat(),
                    steps = ScriptCaptureDuration.MAX - ScriptCaptureDuration.MIN - 1,
                )
                Text(stringResource(R.string.script_duration_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onChange(value.roundToInt()); onDismiss() }) { Text(stringResource(R.string.bible_ref_done)) } },
    )
}

/** Hides the status and navigation bars while the video fills the screen. */
@Composable
private fun FullScreenSystemBars(fullScreen: Boolean) {
    val view = LocalView.current
    DisposableEffect(fullScreen) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (fullScreen) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** PDFs and non-YouTube links go to the browser, as on iOS. */
fun openUrl(context: Context, url: String) {
    val trimmed = url.trim()
    val uri = (if (trimmed.contains("://")) trimmed else "https://$trimmed").toUri()
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        .onFailure { Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_SHORT).show() }
}
