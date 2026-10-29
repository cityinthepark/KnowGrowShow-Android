package com.stoneofhope.stoneofhope.ui.sermon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.sermon.SermonRepository
import com.stoneofhope.stoneofhope.data.sermon.SermonRules
import com.stoneofhope.stoneofhope.data.sermon.YouTubeLinkParser
import com.stoneofhope.stoneofhope.ui.notes.TagPill
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Where the sermon and video rows lead (the caller owns navigation). */
class SermonNavigation(
    val openSermon: (sermonId: String, entry: VideoEntry) -> Unit,
    val openReader: (sermonId: String) -> Unit,
    val openVideo: (youtubeID: String) -> Unit,
)

/** Listen and Merge for sermon rows on Today, the Calendar and the chapter page. */
@HiltViewModel
class SermonActionsViewModel @Inject constructor(
    private val audio: SermonAudioPlayer,
    private val sermons: SermonRepository,
) : ViewModel() {
    /** iOS: Listen starts the shared player, then the sermon page opens with its video held back. */
    fun listen(sermon: SermonEntity) {
        val url = sermon.audioUrl?.trim()?.ifEmpty { null } ?: return
        audio.play(url, sermon.title, sermon.chapterRange?.trim()?.ifEmpty { null }, sermon.preacher, sermon.id)
    }

    fun merge(sourceId: String, targetId: String) {
        viewModelScope.launch { sermons.merge(sourceId, targetId) }
    }
}

/**
 * One sermon in the day's sermon card (iOS `SummaryListRowView` built by `sermonListItems`): "ref -
 * title", the note count, Merge (a day with two sermons or more, this one with notes), Open (today) or
 * Notes (another day: the reader when there are notes), tags, then Listen / Watch / Read.
 */
@Composable
fun SermonDayRow(
    sermon: SermonEntity,
    noteCount: Int,
    tagNames: List<String>,
    isToday: Boolean,
    canMerge: Boolean,
    nav: SermonNavigation,
    onMerge: () -> Unit,
    actions: SermonActionsViewModel = hiltViewModel(),
) {
    val c = AppTheme.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("sermonRow_${sermon.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(SermonRules.displayTitle(sermon), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (noteCount > 0) Text("$noteCount", style = textStyle(13), color = c.neutral90, modifier = Modifier.padding(horizontal = 4.dp))
            if (canMerge) TextButton(onClick = onMerge, modifier = Modifier.testTag("merge_${sermon.id}")) { Text(stringResource(R.string.merge)) }
            if (isToday) {
                TextButton(onClick = { nav.openSermon(sermon.id, VideoEntry.Default) }) { Text(stringResource(R.string.action_open)) }
            } else {
                TextButton(onClick = { if (noteCount > 0) nav.openReader(sermon.id) else nav.openSermon(sermon.id, VideoEntry.Default) }) {
                    Text(stringResource(R.string.action_notes))
                }
            }
        }
        if (tagNames.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tagNames.forEach { TagPill(it, compact = true) } }
        }
        SermonMediaRow(
            sermon,
            onListen = { actions.listen(sermon); nav.openSermon(sermon.id, VideoEntry.Suppressed) },
            onWatch = { nav.openSermon(sermon.id, VideoEntry.Autoplay) },
            onOpenUrl = { openUrl(context, it) },
        )
    }
}

/**
 * iOS media buttons: headphones and the YouTube button go to the sermon page (playing); a video link
 * that isn't YouTube and the PDF open in the browser.
 */
@Composable
fun SermonMediaRow(sermon: SermonEntity, onListen: () -> Unit, onWatch: () -> Unit, onOpenUrl: (String) -> Unit) {
    val c = AppTheme.colors
    val audio = sermon.audioUrl?.trim()?.ifEmpty { null }
    val youTube = SermonRules.youTubeID(sermon)
    val otherVideo = sermon.videoUrl?.trim()?.ifEmpty { null }?.takeIf { youTube == null }
    val pdf = sermon.pdfUrl?.trim()?.ifEmpty { null }
    if (audio == null && youTube == null && otherVideo == null && pdf == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (audio != null) {
            IconButton(onClick = onListen, modifier = Modifier.size(36.dp).testTag("listen_${sermon.id}")) {
                Icon(Icons.Outlined.Headphones, stringResource(R.string.sermon_listen), tint = c.blue80, modifier = Modifier.size(20.dp))
            }
        }
        if (youTube != null || otherVideo != null) {
            IconButton(
                onClick = { if (youTube != null) onWatch() else otherVideo?.let(onOpenUrl) },
                modifier = Modifier.size(36.dp).testTag("watch_${sermon.id}"),
            ) { Icon(Icons.Outlined.SmartDisplay, stringResource(R.string.sermon_watch), tint = c.red80, modifier = Modifier.size(20.dp)) }
        }
        if (pdf != null) {
            IconButton(onClick = { onOpenUrl(pdf) }, modifier = Modifier.size(36.dp).testTag("read_${sermon.id}")) {
                Icon(Icons.Outlined.Description, stringResource(R.string.sermon_read), tint = c.blue80, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** The merge dialog for one day's sermons. */
@Composable
fun MergeSermonHost(
    source: SermonEntity?,
    sameDay: List<SermonEntity>,
    noteCounts: Map<String, Int>,
    onDismiss: () -> Unit,
    actions: SermonActionsViewModel = hiltViewModel(),
) {
    source ?: return
    MergeSermonDialog(
        sourceTitle = SermonRules.displayTitle(source),
        sourceNoteCount = noteCounts[source.id] ?: 0,
        targets = SermonRules.mergeTargets(source, sameDay, noteCounts),
        onMerge = { target -> actions.merge(source.id, target); onDismiss() },
        onDismiss = onDismiss,
    )
}

// MARK: - Floating audio player

@HiltViewModel
class AudioBarViewModel @Inject constructor(private val player: SermonAudioPlayer) : ViewModel() {
    val state = player.state
    fun playPause() = player.playPause()
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun close() = player.stop()
}

/**
 * iOS `FloatingAudioPlayer`: above the tab bar, and over every page while audio is loaded. iOS also
 * has a Script button here (speech-to-text of the audio); that waits for a later phase.
 */
@Composable
fun FloatingAudioBar(modifier: Modifier = Modifier, viewModel: AudioBarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.visible) return
    val c = AppTheme.colors
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape, color = c.surface0, tonalElevation = 2.dp,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp).shadow(8.dp, shape).fillMaxWidth().testTag("audioBar"),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp)) {
            Text(state.title, style = textStyle(15, weight = FontWeight.SemiBold), color = c.neutral100, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.reference.isNotEmpty() || state.preacher.isNotEmpty()) {
                Row {
                    Text(state.reference, style = textStyle(12), color = c.blue80, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(state.preacher, style = textStyle(12), color = c.neutral90, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                }
            }
            val duration = state.durationMs.coerceAtLeast(1)
            Slider(
                value = if (dragging) dragValue else (state.positionMs.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { viewModel.seekTo((dragValue * duration).toLong()); dragging = false },
                enabled = state.durationMs > 0,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val text = if (state.failed) stringResource(R.string.audio_failed) else "${clock(state.positionMs)} / ${clock(state.durationMs)}"
                Text(text, style = textStyle(12), color = if (state.failed) c.red80 else c.neutral90, modifier = Modifier.weight(1f))
                if (state.isLoading && !state.isPlaying) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                IconButton(onClick = viewModel::playPause, modifier = Modifier.testTag("audioPlayPause")) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        stringResource(if (state.isPlaying) R.string.audio_pause else R.string.audio_play),
                    )
                }
                IconButton(onClick = viewModel::close, modifier = Modifier.testTag("audioClose")) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.audio_close))
                }
            }
        }
    }
}

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
}

/** Thumbnail URL for a video row: the saved one, else YouTube's fixed one. */
fun videoThumbnail(youtubeID: String, saved: String?) = saved?.ifBlank { null } ?: YouTubeLinkParser.thumbnailUrl(youtubeID)
