package com.stoneofhope.stoneofhope.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Photos of an Image note, as iOS `FileHelper` keeps them: a JPEG (0.75) and a 480-px thumbnail JPEG (0.65)
 * in an `images` folder, and only the file names in the note's `imagePath` / `thumbnailPath` (so the
 * folder can move). iOS mirrors the thumbnail to Firebase Storage; that part is not here yet.
 */

/** The two file names of a saved photo; they go into the note. */
data class SavedImage(val original: String, val thumbnail: String)

/** A photo just taken or picked: its saved names, and the bitmap for text recognition. */
class ImportedImage(val saved: SavedImage, val bitmap: Bitmap)

object ImageSizing {
    /** iOS `scaledToFit(maxDimension: 480)`. */
    const val THUMBNAIL_MAX = 480

    /** The biggest side kept for the original, so a 50-megapixel photo cannot exhaust memory. */
    const val ORIGINAL_MAX = 4096

    const val ORIGINAL_QUALITY = 75
    const val THUMBNAIL_QUALITY = 65

    /** [width] x [height] shrunk to fit [max] on its longer side, same proportions; never enlarged. */
    fun fit(width: Int, height: Int, max: Int): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= max || longest <= 0) return width to height
        val scale = max.toFloat() / longest
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }
}

@Singleton
class NoteImageStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val directory: File get() = File(context.filesDir, "images").also { it.mkdirs() }

    /** iOS `resolveImageURL`: only the last path component counts, so a stored absolute path still finds its file. */
    fun file(name: String): File = File(directory, File(name).name)

    fun exists(name: String): Boolean = file(name).isFile

    /** Reads [uri] (camera or photo picker), saves both JPEGs, and returns them. Null when it cannot be read. */
    suspend fun import(uri: Uri): ImportedImage? = withContext(Dispatchers.IO) {
        val bitmap = try {
            // The platform decoder turns the photo upright by itself. Software memory: a hardware bitmap
            // cannot be compressed or read for text.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val (w, h) = ImageSizing.fit(info.size.width, info.size.height, ImageSizing.ORIGINAL_MAX)
                decoder.setTargetSize(w, h)
            }
        } catch (_: Exception) {
            return@withContext null
        }
        save(bitmap)?.let { ImportedImage(it, bitmap) }
    }

    /** Both files, or null (and nothing left behind) when either cannot be written. */
    fun save(bitmap: Bitmap): SavedImage? {
        val original = newName()
        val thumbnail = newName()
        val (tw, th) = ImageSizing.fit(bitmap.width, bitmap.height, ImageSizing.THUMBNAIL_MAX)
        val small = if (tw == bitmap.width && th == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, tw, th, true)
        val ok = write(original, bitmap, ImageSizing.ORIGINAL_QUALITY) && write(thumbnail, small, ImageSizing.THUMBNAIL_QUALITY)
        if (small !== bitmap) small.recycle()
        if (!ok) {
            file(original).delete()
            file(thumbnail).delete()
            return null
        }
        return SavedImage(original, thumbnail)
    }

    /** The picture of [name] (thumbnail or original), or null when this device does not have the file. */
    fun load(name: String): Bitmap? {
        cache.get(name)?.let { return it }
        val file = file(name)
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.path)?.also { cache.put(name, it) }
    }

    fun delete(name: String) {
        cache.remove(name)
        file(name).delete()
    }

    /** A deleted note's two picture files. Like the note, they cannot come back. */
    fun deleteFilesOf(note: NoteEntity) {
        listOfNotNull(note.imagePath, note.thumbnailPath).filter { it.isNotEmpty() }.forEach(::delete)
    }

    private fun write(name: String, bitmap: Bitmap, quality: Int): Boolean = try {
        file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    } catch (_: Exception) {
        false
    }

    private fun newName() = UUID.randomUUID().toString().uppercase() + ".jpg"

    private companion object {
        /** About 16 MB of decoded pictures, so a list of thumbnails is not decoded again on every scroll. */
        val cache = object : LruCache<String, Bitmap>(min(16 * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8).toInt()) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }
    }
}

/** iOS `addImageNote`: an Image note carries no text, only the two file names, and sits in a sermon or a video. */
object ImageNotes {
    fun note(id: String, saved: SavedImage, now: Instant, sermonId: String?, youtubeID: String?): NoteEntity {
        val note = NoteEntity(
            id = id, noteType = NoteType.Image, content = "",
            createdAt = now, updatedAt = now, imagePath = saved.original, thumbnailPath = saved.thumbnail, needsSync = true,
        )
        // A sermon link clears the video id and the other way round (iOS `linkNote` / `youtubeID`).
        return when {
            sermonId != null -> note.copy(sermonId = sermonId)
            youtubeID != null -> note.copy(youtubeID = youtubeID)
            else -> note
        }
    }
}
