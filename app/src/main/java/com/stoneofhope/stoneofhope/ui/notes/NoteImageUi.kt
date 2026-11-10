package com.stoneofhope.stoneofhope.ui.notes

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.image.NoteImageStore
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The store for composables, which have no injection: it only needs the app context. */
@Composable
private fun rememberImageStore(): NoteImageStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { NoteImageStore(context) }
}

/** The picture saved under [name], decoded off the main thread; null while loading or when this device lacks the file. */
@Composable
private fun rememberStoredImage(name: String?): Bitmap? {
    val store = rememberImageStore()
    val bitmap by produceState<Bitmap?>(null, name) {
        value = name?.let { withContext(Dispatchers.IO) { store.load(it) } }
    }
    return bitmap
}

/**
 * An Image note's picture (iOS `noteImageBody`): the thumbnail, fitted to the width and at most [maxHeight]
 * tall. A note synced from another device has no file here yet (iOS fetches the thumbnail from Firebase
 * Storage; that is not ported), so it says so. [onClick] opens the full photo.
 */
@Composable
fun NoteImage(note: NoteEntity, maxHeight: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = AppTheme.colors
    val name = note.thumbnailPath?.takeIf { it.isNotBlank() } ?: note.imagePath?.takeIf { it.isNotBlank() }
    val store = rememberImageStore()
    val bitmap = rememberStoredImage(name)
    val missing = name == null || !store.exists(name)
    Surface(shape = RoundedCornerShape(12.dp), color = c.pageBackground, modifier = modifier.fillMaxWidth().testTag("noteImage_${note.id}")) {
        when {
            bitmap != null -> Image(
                bitmap.asImageBitmap(), contentDescription = stringResource(R.string.image_full),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).clip(RoundedCornerShape(12.dp)).let { if (onClick != null) it.clickable(onClick = onClick) else it },
            )
            missing -> Text("🖼️  " + stringResource(R.string.image_unavailable), style = textStyle(13), color = c.neutral90, modifier = Modifier.padding(14.dp))
            else -> Box(Modifier.fillMaxWidth().heightIn(min = 80.dp))
        }
    }
}

/** The photo at full size over everything, pinch to zoom and drag; "✕" closes it. */
@Composable
fun NoteImageViewer(note: NoteEntity, onDismiss: () -> Unit) {
    val name = note.imagePath?.takeIf { it.isNotBlank() } ?: note.thumbnailPath?.takeIf { it.isNotBlank() }
    val store = rememberImageStore()
    // The original when this device has it, else the thumbnail.
    val bitmap = rememberStoredImage(name?.takeIf { store.exists(it) } ?: note.thumbnailPath)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("imageViewer")) {
            bitmap?.let {
                Image(
                    it.asImageBitmap(), contentDescription = stringResource(R.string.image_full), contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale == 1f) { offsetX = 0f; offsetY = 0f } else { offsetX += pan.x; offsetY += pan.y }
                            }
                        }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().testTag("imageViewerClose")) {
                Icon(Icons.Filled.Close, stringResource(R.string.back), tint = Color.White)
            }
        }
    }
}
