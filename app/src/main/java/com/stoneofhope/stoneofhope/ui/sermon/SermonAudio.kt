package com.stoneofhope.stoneofhope.ui.sermon

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.stoneofhope.stoneofhope.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background playback for sermon audio (iOS `AudioPlayerManager` with its `AVAudioSession` and lock-
 * screen controls). Media3 shows the notification and lock-screen controls itself; media sessions are
 * exempt from the Android 13 notification permission.
 */
class SermonAudioService : MediaSessionService() {
    private var session: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/** What the floating player bar shows (iOS `AudioPlayerManager`'s published properties). */
data class AudioUiState(
    val visible: Boolean = false,
    val title: String = "",
    val reference: String = "",
    val preacher: String = "",
    val sermonId: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val failed: Boolean = false,
)

/** The app's one audio player; the service owns the actual player, this talks to it. */
@Singleton
class SermonAudioPlayer @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(AudioUiState())
    val state: StateFlow<AudioUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh(player)

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(failed = true, isPlaying = false, isLoading = false) }
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let { block(it); return }
        pending += block
        if (connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, SermonAudioService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: run { pending.clear(); return@addListener }
            controller = c
            c.addListener(listener)
            pending.toList().forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * iOS `AudioPlayerManager.load`. iOS only loads, so "Listen" needs a second tap on play
     * (IOS_ISSUES K14); here it starts playing.
     */
    fun play(url: String, title: String, reference: String?, preacher: String?, sermonId: String?) {
        _state.value = AudioUiState(
            visible = true, title = title, reference = reference.orEmpty(), preacher = preacher.orEmpty(),
            sermonId = sermonId, isLoading = true,
        )
        withController { c ->
            val metadata = MediaMetadata.Builder().setTitle(title).setArtist(preacher).setSubtitle(reference).build()
            c.setMediaItem(MediaItem.Builder().setUri(url).setMediaId(sermonId ?: url).setMediaMetadata(metadata).build())
            c.prepare()
            c.play()
        }
        startTicker()
    }

    fun playPause() = withController { c -> if (c.isPlaying) c.pause() else c.play() }

    fun seekTo(positionMs: Long) {
        _state.update { it.copy(positionMs = positionMs) }
        withController { it.seekTo(positionMs) }
    }

    /** iOS close button: stops and hides the bar. */
    fun stop() {
        ticker?.cancel()
        _state.value = AudioUiState()
        controller?.run {
            stop()
            clearMediaItems()
        }
    }

    private fun refresh(player: Player) {
        _state.update {
            if (!it.visible) it else it.copy(
                isPlaying = player.isPlaying,
                isLoading = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET && d > 0 } ?: it.durationMs,
            )
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                controller?.let(::refresh)
                delay(500)
            }
        }
    }
}
