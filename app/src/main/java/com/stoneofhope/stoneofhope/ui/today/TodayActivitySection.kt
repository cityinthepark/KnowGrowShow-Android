package com.stoneofhope.stoneofhope.ui.today

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.today.TodayActivity
import com.stoneofhope.stoneofhope.data.today.TodayActivity.TileKind
import com.stoneofhope.stoneofhope.ui.theme.AppColors
import com.stoneofhope.stoneofhope.ui.theme.AppTheme

/** iOS `TodayActivityView`: highlight tiles, then expandable Chapter / Sermon / Video cards. Hidden when empty. */
@Composable
fun TodayActivitySection(
    activity: TodayActivity,
    onOpen: (title: String) -> Unit,
    onOpenTile: (TileKind) -> Unit,
    onReview: () -> Unit,
    onUnread: (bookOrd: Int, chapter: Int) -> Unit,
) {
    if (activity.isEmpty) return
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.today_activity), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (activity.showReview) {
                AssistChip(onClick = onReview, label = { Text("▶ " + stringResource(R.string.today_review)) }, modifier = Modifier.testTag("todayReview"))
            }
        }
        Tiles(activity.tiles, onOpenTile)
        if (activity.chaptersRead.isNotEmpty()) ChaptersCard(activity, onOpen, onUnread)
        if (activity.sermons.isNotEmpty()) SermonsCard(activity.sermons, onOpen)
        if (activity.videos.isNotEmpty()) VideosCard(activity.videos, onOpen)
    }
}

/** One tile takes the full row; two or more go two per row (iOS grid). */
@Composable
private fun Tiles(tiles: List<TodayActivity.Tile>, onOpen: (TileKind) -> Unit) {
    if (tiles.isEmpty()) return
    FlowRow(
        maxItemsInEachRow = 2,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        tiles.forEach { tile ->
            val style = tileStyle(tile.kind)
            val title = stringResource(style.title)
            Surface(
                onClick = { onOpen(tile.kind) },
                shape = RoundedCornerShape(14.dp),
                color = style.pair.background,
                contentColor = style.pair.foreground,
                modifier = Modifier.weight(1f).testTag("tile_${tile.kind.name}"),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        style.emoji != null -> Text(style.emoji, fontSize = 16.sp)
                        style.icon != null -> Icon(style.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("$title (${tile.count})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private class TileStyle(val title: Int, val pair: AppColors.ChipPair, val icon: ImageVector? = null, val emoji: String? = null)

@Composable
private fun tileStyle(kind: TileKind): TileStyle {
    val c = AppTheme.colors
    return when (kind) {
        TileKind.Pinned -> TileStyle(R.string.tile_pinned, c.pinnedTile, icon = Icons.Filled.PushPin)
        TileKind.Starred -> TileStyle(R.string.tile_starred, c.starredTile, icon = Icons.Filled.Star)
        TileKind.QuickNote -> TileStyle(R.string.note_type_note, c.notesChip, emoji = "📝")
        TileKind.StudyNote -> TileStyle(R.string.tile_study_note, c.notesChip, emoji = "📚")
        TileKind.Bible -> TileStyle(R.string.tile_bible, c.bibleVerseChip, emoji = "✝️")
        TileKind.Prayer -> TileStyle(R.string.note_type_prayer, c.prayerChip, emoji = "🌿")
        TileKind.AnsweredPrayer -> TileStyle(R.string.tile_answered_prayers, c.answeredPrayerTile, emoji = "🙌")
        TileKind.Image -> TileStyle(R.string.tile_image, c.notesChip, icon = Icons.Filled.Image)
    }
}

@Composable
private fun ExpandableCard(
    id: String,
    title: String,
    subtitle: String,
    count: Int,
    pair: AppColors.ChipPair,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = AppTheme.colors.surface0, modifier = Modifier.fillMaxWidth()) {
        Column {
            Surface(onClick = { expanded = !expanded }, color = AppTheme.colors.surface0, modifier = Modifier.testTag("card_$id")) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(10.dp), color = pair.background, contentColor = pair.foreground) {
                        Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("$title ($count)", style = MaterialTheme.typography.titleSmall)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 14.dp, end = 6.dp, bottom = 8.dp)) { content() }
            }
        }
    }
}

@Composable
private fun ChaptersCard(activity: TodayActivity, onOpen: (String) -> Unit, onUnread: (Int, Int) -> Unit) {
    val logs = activity.chaptersRead
    ExpandableCard(
        id = "chaptersRead",
        title = stringResource(R.string.card_chapter_read),
        subtitle = pluralStringResource(R.plurals.today_chapters_subtitle, logs.size, logs.size),
        count = logs.size,
        pair = AppTheme.colors.chapterReadTile,
        icon = Icons.AutoMirrored.Outlined.MenuBook,
    ) {
        logs.forEach { log ->
            val name = chapterTitle(log.bookOrd, log.chapter)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onUnread(log.bookOrd, log.chapter) }) { Text(stringResource(R.string.action_unread)) }
                TextButton(onClick = { onOpen(name) }) { Text(stringResource(R.string.card_sermons)) }
                TextButton(onClick = { onOpen(name) }) { Text(stringResource(R.string.action_notes)) }
            }
        }
    }
}

@Composable
private fun SermonsCard(rows: List<TodayActivity.SermonRow>, onOpen: (String) -> Unit) {
    ExpandableCard(
        id = "sermons",
        title = stringResource(R.string.card_sermons),
        subtitle = pluralStringResource(R.plurals.today_sermons_subtitle, rows.size, rows.size),
        count = rows.size,
        pair = AppTheme.colors.sermonChip,
        icon = Icons.AutoMirrored.Outlined.MenuBook,
    ) {
        rows.forEach { row ->
            val ref = row.sermon.chapterRange?.trim().orEmpty()
            val title = if (ref.isEmpty()) row.sermon.title else "$ref - ${row.sermon.title}"
            ItemRow(title, row.noteCount, row.tagNames) { onOpen(row.sermon.title) }
        }
    }
}

/** One video: a plain card that opens it. Several: an expandable list (iOS). */
@Composable
private fun VideosCard(rows: List<TodayActivity.VideoRow>, onOpen: (String) -> Unit) {
    val colors = AppTheme.colors
    if (rows.size == 1) {
        val row = rows.single()
        Surface(onClick = { onOpen(row.video.title) }, shape = RoundedCornerShape(16.dp), color = colors.surface0, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = colors.videoChip.background, contentColor = colors.videoChip.foreground) {
                    Icon(Icons.Outlined.PlayCircle, contentDescription = null, modifier = Modifier.padding(8.dp).size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (row.tagNames.isNotEmpty()) {
                        Text(row.tagNames.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        return
    }
    ExpandableCard(
        id = "videos",
        title = stringResource(R.string.card_videos),
        subtitle = pluralStringResource(R.plurals.today_videos_subtitle, rows.size, rows.size),
        count = rows.size,
        pair = colors.videoChip,
        icon = Icons.Outlined.PlayCircle,
    ) {
        rows.forEach { row -> ItemRow(row.video.title, row.noteCount, row.tagNames) { onOpen(row.video.title) } }
    }
}

@Composable
private fun ItemRow(title: String, noteCount: Int, tags: List<String>, onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = buildList {
                if (noteCount > 0) add("📝 $noteCount")
                addAll(tags.map { "#$it" })
            }
            if (meta.isNotEmpty()) {
                Text(meta.joinToString("  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onOpen) { Text(stringResource(R.string.action_open)) }
    }
}
