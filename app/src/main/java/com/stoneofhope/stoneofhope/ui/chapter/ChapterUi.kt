package com.stoneofhope.stoneofhope.ui.chapter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.ui.notes.AnsweredChip
import com.stoneofhope.stoneofhope.ui.notes.NoteCardUi
import com.stoneofhope.stoneofhope.ui.notes.StarIcons
import com.stoneofhope.stoneofhope.ui.notes.TagPill
import com.stoneofhope.stoneofhope.ui.notes.relativeTime
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.notes.threadLabel
import com.stoneofhope.stoneofhope.ui.theme.AppTheme

// Pieces shared by the chapter page and its verse page, in the note list / details style (docs/design).

/**
 * A chapter note, or a prayer on the verse page: when (iOS `relativeOrTimestampDisplayString` — the
 * list cards' clock-only time would hide the day here), status, the text, then comments, one tag and
 * "⋯". Tap opens the note; long press or "⋯" opens its actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DatedNoteCard(card: NoteCardUi, onOpen: () -> Unit, onMenu: () -> Unit) {
    val c = AppTheme.colors
    val note = card.note
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = c.surface0,
        border = BorderStroke(1.dp, c.neutral20),
        modifier = Modifier.fillMaxWidth().testTag("noteRow_${note.id}"),
    ) {
        Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = onMenu).padding(start = 16.dp, top = 12.dp, end = 6.dp, bottom = 2.dp)) {
            Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (note.noteType == NoteType.Prayer && note.isAnswered == true) AnsweredChip()
                Text(relativeTime(note.createdAt), style = textStyle(12), color = c.neutral90, maxLines = 1, modifier = Modifier.weight(1f))
                if (note.isPinned == true) {
                    Icon(Icons.Filled.PushPin, stringResource(R.string.tile_pinned), tint = c.pinnedTile.foreground, modifier = Modifier.size(13.dp))
                }
                if (note.starLevel > 0) StarIcons(note.starLevel, size = 13.dp)
            }
            if (card.displayContent.isNotBlank()) {
                Text(
                    card.displayContent,
                    style = textStyle(15, 1.55f),
                    color = c.neutral100,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, end = 10.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = c.neutral90, modifier = Modifier.size(14.dp))
                Text(threadLabel(isAnchor = false, count = card.threadCount), style = textStyle(12), color = c.neutral90)
                card.tagNames.firstOrNull()?.let { TagPill(it, compact = true) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMenu, modifier = Modifier.size(44.dp).testTag("noteMenu_${note.id}")) {
                    Icon(Icons.Filled.MoreHoriz, stringResource(R.string.action_more), tint = c.neutral90)
                }
            }
        }
    }
}

/** A section title with its count in a gray capsule, like the details page's "Comments 3". */
@Composable
internal fun SectionTitle(title: String, count: Int? = null) {
    val c = AppTheme.colors
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = textStyle(17, weight = FontWeight.Bold), color = c.neutral100)
        if (count != null) {
            Box(
                Modifier.heightIn(min = 22.dp).background(c.neutral20.copy(alpha = 0.6f), RoundedCornerShape(11.dp)).padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("$count", style = textStyle(12, weight = FontWeight.SemiBold), color = c.neutral90)
            }
        }
    }
}

/** A quiet message in the upper part of an empty page. */
@Composable
internal fun EmptyState(title: String, modifier: Modifier = Modifier, hint: String? = null) {
    val c = AppTheme.colors
    Column(modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = textStyle(16, weight = FontWeight.SemiBold), color = c.neutral90, textAlign = TextAlign.Center)
        hint?.let { Text(it, style = textStyle(14, 1.5f), color = c.neutral90, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)) }
    }
}
