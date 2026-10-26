package com.stoneofhope.stoneofhope.data.local

import androidx.room.TypeConverter
import com.stoneofhope.stoneofhope.data.model.ConflictResolution
import com.stoneofhope.stoneofhope.data.model.MarkStyle
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.model.SyncConflictType
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.Json

/**
 * Instants are stored as epoch **microseconds** — Firestore `Timestamp` precision — so a timestamp
 * pulled from iOS/Web and pushed back is bit-for-bit unchanged (millis would truncate it).
 *
 * Lists are stored as JSON arrays, mirroring the wire shape (`tagIds`, `chapters`, ...).
 * Tag membership queries use `LIKE '%"<id>"%'`, safe because ids are fixed-length hex.
 */
class Converters {

    @TypeConverter
    fun instantToMicros(value: Instant?): Long? = value?.let(::toEpochMicros)

    @TypeConverter
    fun microsToInstant(value: Long?): Instant? = value?.let(::fromEpochMicros)

    @TypeConverter
    fun stringListToJson(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> = json.decodeFromString(value)

    @TypeConverter
    fun intListToJson(value: List<Int>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToIntList(value: String): List<Int> = json.decodeFromString(value)

    @TypeConverter
    fun noteTypeToRaw(value: NoteType): String = value.raw

    @TypeConverter
    fun rawToNoteType(value: String): NoteType = NoteType.fromRaw(value) ?: NoteType.Note

    @TypeConverter
    fun markStyleToRaw(value: MarkStyle): String = value.raw

    @TypeConverter
    fun rawToMarkStyle(value: String): MarkStyle = MarkStyle.fromRaw(value) ?: MarkStyle.Highlight

    @TypeConverter
    fun conflictTypeToRaw(value: SyncConflictType): String = value.raw

    @TypeConverter
    fun rawToConflictType(value: String): SyncConflictType =
        SyncConflictType.fromRaw(value) ?: SyncConflictType.Note

    @TypeConverter
    fun resolutionToRaw(value: ConflictResolution?): String? = value?.raw

    @TypeConverter
    fun rawToResolution(value: String?): ConflictResolution? = ConflictResolution.fromRaw(value)

    companion object {
        private val json = Json

        fun toEpochMicros(instant: Instant): Long = ChronoUnit.MICROS.between(Instant.EPOCH, instant)

        fun fromEpochMicros(micros: Long): Instant = Instant.EPOCH.plus(micros, ChronoUnit.MICROS)
    }
}
