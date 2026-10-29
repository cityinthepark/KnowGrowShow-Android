package com.stoneofhope.stoneofhope.ui.sermon

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.stoneofhope.stoneofhope.BuildConfig
import com.stoneofhope.stoneofhope.data.sermon.YouTubeLinkParser
import java.net.URL
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One page's YouTube player (iOS `YouTubePlayerController`). The [WebView] lives here, not in the
 * composable, so turning the phone (or tapping full screen) moves the same player between layouts
 * without reloading it — iOS keeps its `WKWebView` mounted for the same reason.
 */
@Stable
class YouTubePlayerState(private val context: Context, val videoID: String, autoplay: Boolean) {
    /** iOS shows the thumbnail until the user taps it, unless the page was opened with "Watch". */
    var isActive by mutableStateOf(autoplay)
        private set
    var isReady by mutableStateOf(false)
        private set

    private var webView: WebView? = null

    fun activate() { isActive = true }

    @SuppressLint("SetJavaScriptEnabled")
    internal fun webView(): WebView = webView ?: WebView(context).also { view ->
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        // With the wrap-content height AndroidView would give it, WebView lays the page out at height 0
        // and the YouTube frame never shows.
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view.settings.javaScriptEnabled = true
        view.settings.mediaPlaybackRequiresUserGesture = false
        view.settings.domStorageEnabled = true
        view.setBackgroundColor(android.graphics.Color.BLACK)
        view.webChromeClient = WebChromeClient()
        view.webViewClient = WebViewClient()
        view.addJavascriptInterface(Bridge(), "AndroidBridge")
        // YouTube requires an identifying referrer for embeds in apps; iOS uses its bundle id the same way.
        val origin = "https://" + context.packageName
        view.loadDataWithBaseURL("$origin/", playerHtml(videoID, origin), "text/html", "utf-8", null)
        webView = view
    }

    private inner class Bridge {
        @JavascriptInterface
        fun post(message: String) {
            val type = runCatching { JSONObject(message).optString("type") }.getOrNull()
            if (type == "ready") view()?.post { isReady = true }
        }
    }

    private fun view(): WebView? = webView

    /** iOS `getCurrentTime()`: the playhead in seconds, or null before the player is ready. */
    suspend fun currentTime(): Double? {
        val view = webView ?: return null
        if (!isReady) return null
        return suspendCancellableCoroutine { cont ->
            view.evaluateJavascript("ytCurrentTime();") { result -> cont.resume(result?.toDoubleOrNull()) }
        }
    }

    fun pause() {
        webView?.evaluateJavascript("ytPause();", null)
    }

    fun onPause() {
        pause()
        webView?.onPause()
    }

    fun onResume() {
        webView?.onResume()
    }

    fun release() {
        webView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        webView = null
    }
}

@Composable
fun rememberYouTubePlayerState(videoID: String, autoplay: Boolean): YouTubePlayerState {
    val context = LocalContext.current
    val state = remember(videoID) { YouTubePlayerState(context, videoID, autoplay) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(state, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // iOS pauses when the page goes away.
                Lifecycle.Event.ON_PAUSE -> state.onPause()
                Lifecycle.Event.ON_RESUME -> state.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            state.release()
        }
    }
    return state
}

/** The 16:9 player, or its thumbnail with a play button until activated (iOS `YouTubeInlinePlayerView`). */
@Composable
fun YouTubePlayerView(state: YouTubePlayerState, thumbnailUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black).testTag("youtubePlayer"), contentAlignment = Alignment.Center) {
        if (state.isActive) {
            AndroidView(
                factory = { state.webView().also { (it.parent as? ViewGroup)?.removeView(it) } },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val image by rememberNetworkImage(thumbnailUrl ?: YouTubeLinkParser.thumbnailUrl(state.videoID))
            image?.let {
                Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
                modifier = Modifier.size(64.dp).clickable(onClick = state::activate).testTag("youtubePlay"),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(36.dp)) }
            }
        }
    }
}

/** The player sized for the portrait page. */
fun Modifier.videoAspect(): Modifier = this.fillMaxWidth().aspectRatio(16f / 9f)

private val imageCache = LruCache<String, Bitmap>(24)

/** A small network image loader for thumbnails (the app has no image library). */
@Composable
fun rememberNetworkImage(url: String?) = produceState<Bitmap?>(initialValue = url?.let(imageCache::get), url) {
    if (url == null || value != null) return@produceState
    value = withContext(Dispatchers.IO) {
        runCatching { URL(url).openStream().use(BitmapFactory::decodeStream) }.getOrNull()
    }?.also { imageCache.put(url, it) }
}

/**
 * The page iOS loads (`YouTubeEmbedWebView.loadPlayer`), with YouTube's own controls (play, seek, CC)
 * instead of the custom overlay iOS draws.
 */
private fun playerHtml(videoID: String, origin: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<meta name="referrer" content="strict-origin-when-cross-origin">
<style>
html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
#player { position: absolute; inset: 0; width: 100%; height: 100%; }
#player iframe { position: absolute; inset: 0; width: 100% !important; height: 100% !important; border: 0; }
</style>
</head>
<body>
<div id="player"></div>
<script>
var player;
function post(msg) { AndroidBridge.post(JSON.stringify(msg)); }
function onYouTubeIframeAPIReady() {
  player = new YT.Player('player', {
    width: '100%', height: '100%', videoId: '$videoID',
    playerVars: { playsinline: 1, autoplay: 1, controls: 1, rel: 0, modestbranding: 1, enablejsapi: 1, fs: 0, origin: '$origin' },
    events: {
      onReady: function() { post({ type: 'ready' }); },
      onStateChange: function(e) { post({ type: 'state', state: e.data }); }
    }
  });
}
function ytPause() { if (player && player.pauseVideo) player.pauseVideo(); }
function ytCurrentTime() { if (player && player.getCurrentTime) return player.getCurrentTime(); return 0; }
</script>
<script src="https://www.youtube.com/iframe_api" referrerpolicy="strict-origin-when-cross-origin"></script>
</body>
</html>
""".trimIndent()
