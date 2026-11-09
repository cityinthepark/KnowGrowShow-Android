package com.stoneofhope.stoneofhope.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.notes.MarkedVerse
import com.stoneofhope.stoneofhope.data.notes.NoteBadges
import com.stoneofhope.stoneofhope.data.notes.ResolvedVerse
import com.stoneofhope.stoneofhope.ui.theme.AppColors
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import java.time.LocalDate

// The three card styles of the redesigned list (docs/design/note-list §2).

private val CardShape = RoundedCornerShape(20.dp)

@Composable
private fun CardSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = AppTheme.colors
    Surface(shape = CardShape, color = c.surface0, border = BorderStroke(1.dp, c.neutral20), modifier = modifier.fillMaxWidth(), content = content)
}

/** One verse with its number as a superscript (design §2: 10 / Bold, `scriptureInk` 70%) and its marks. */
fun numberedVerse(number: Int, verse: MarkedVerse, numberColor: Color): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = numberColor, baselineShift = BaselineShift.Superscript, fontFamily = FontFamily.Default)) {
        append("$number")
    }
    append("\u2009")
    appendMarked(verse.trimmed())
}

/** A verse range as one paragraph, numbered when there is more than one verse (design L2). */
fun numberedVerses(verse: ResolvedVerse, numberColor: Color): AnnotatedString {
    val shown = verse.verses.filter { it.second.isNotBlank() }
    if (shown.size <= 1) return verse.combined.trimmed().annotated()
    return buildAnnotatedString {
        shown.forEachIndexed { i, (n, text) ->
            if (i > 0) append(" ")
            append(numberedVerse(n, verse.verse(n, text), numberColor))
        }
    }
}

/** "⋯" at 36 visual size inside a 44+ touch target. */
@Composable
private fun MoreButton(noteId: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp).testTag("noteMenu_$noteId")) {
        Icon(Icons.Filled.MoreHoriz, stringResource(R.string.action_more), tint = AppTheme.colors.neutral90)
    }
}

@Composable
private fun StatusIcons(note: NoteEntity) {
    val c = AppTheme.colors
    if (note.isPinned == true) Icon(Icons.Filled.PushPin, stringResource(R.string.tile_pinned), tint = c.pinnedTile.foreground, modifier = Modifier.size(13.dp))
    if (note.starLevel > 0) StarIcons(note.starLevel, size = 13.dp)
}

/**
 * Prayer / quick note / study / sermon / video / chapter note card (design §2.2): type chip, the prayer's
 * verse pill (unfolds the verse in place), Answered, status and time; up to 4 lines (2 when pinned);
 * then comments, one tag and "⋯". With nothing to show in that bottom row it is left out and "⋯"
 * moves to a long press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteListCard(
    card: NoteCardUi,
    sectionDay: LocalDate?,
    maxLines: Int,
    verseExpanded: Boolean,
    verse: ResolvedVerse?,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onToggleVerse: () -> Unit,
) {
    val c = AppTheme.colors
    val note = card.note
    val answered = note.noteType == NoteType.Prayer && note.isAnswered == true
    // Chip + verse pill + Answered leave no room on the title line: status icons move to the bottom row.
    val crowded = card.prayerRef != null && answered
    val hasBottomRow = card.threadCount > 0 || card.tagNames.isNotEmpty() || crowded
    CardSurface(Modifier.testTag("noteRow_${note.id}")) {
        Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = onMenu).padding(start = 16.dp, top = 12.dp, end = 6.dp, bottom = 6.dp)) {
            Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypeChip(card.badge, Modifier.weight(1f, fill = false))
                    card.prayerRef?.let { VersePill(it, verseExpanded, note.id, onToggleVerse) }
                    if (answered) AnsweredChip()
                }
                if (!crowded) StatusIcons(note)
                Text(cardTime(note.createdAt, sectionDay), style = textStyle(12), color = c.neutral90, maxLines = 1)
            }
            if (verseExpanded) {
                Surface(shape = RoundedCornerShape(14.dp), color = c.noteTypeBibleVerse.copy(alpha = 0.08f), modifier = Modifier.padding(top = 8.dp, end = 10.dp).fillMaxWidth()) {
                    val text = verse?.let { numberedVerses(it, c.scriptureInk.copy(alpha = 0.7f)) }
                    MarkedText(
                        text ?: AnnotatedString(stringResource(R.string.verse_unavailable)),
                        style = textStyle(15, 1.6f, serif = true),
                        color = if (text != null && text.isNotBlank()) c.neutral100 else c.neutral90,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).testTag("prayerVerseText"),
                    )
                }
            }
            if (note.noteType == NoteType.Image) {
                Surface(shape = RoundedCornerShape(12.dp), color = c.pageBackground, modifier = Modifier.padding(top = 8.dp, end = 10.dp).fillMaxWidth()) {
                    Text("🖼️  " + stringResource(R.string.image_placeholder), style = textStyle(13), color = c.neutral90, modifier = Modifier.padding(14.dp))
                }
            }
            if (card.showsBody && card.displayContent.isNotBlank()) {
                Text(
                    card.displayContent,
                    style = textStyle(15, 1.55f),
                    color = c.neutral100,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, end = 10.dp),
                )
            }
            if (hasBottomRow) {
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = c.neutral90, modifier = Modifier.size(14.dp))
                    Text(threadLabel(card.isAnchor, card.threadCount), style = textStyle(12), color = c.neutral90)
                    card.tagNames.firstOrNull()?.let { TagPill(it, compact = true) }
                    if (crowded) StatusIcons(note)
                    Spacer(Modifier.weight(1f))
                    MoreButton(note.id, onMenu)
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** The verse chip of a verse-linked prayer: light when folded, solid ink with "⌃" when open (L2). */
@Composable
private fun VersePill(ref: String, expanded: Boolean, noteId: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (expanded) c.scriptureInk else c.noteTypeBibleVerse.copy(alpha = 0.14f),
        modifier = Modifier.height(24.dp).testTag("prayerVerse_$noteId"),
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${NoteBadges.BIBLE_ICON} $ref", style = textStyle(12, weight = FontWeight.SemiBold), color = if (expanded) Color.White else c.scriptureInk, maxLines = 1)
            if (expanded) Icon(Icons.Filled.KeyboardArrowUp, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/** The light purple scripture header shared by anchor and reflection-group cards. */
@Composable
private fun ScriptureHeader(ref: String, sermonMark: Boolean, time: String?, note: NoteEntity?) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${NoteBadges.BIBLE_ICON} " + ref.uppercase(), style = capsStyle(0.06f), color = c.scriptureInk, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            if (sermonMark) {
                Pill(c.sermonChip.background) {
                    Text(NoteBadges.typeIcon(NoteType.SermonNote) + " " + stringResource(R.string.chip_sermon), style = textStyle(11, weight = FontWeight.SemiBold), color = c.sermonChip.foreground)
                }
            }
        }
        note?.let { StatusIcons(it) }
        time?.let { Text(it, style = textStyle(12), color = c.neutral90) }
    }
}

private fun scriptureFill(c: AppColors) = c.noteTypeBibleVerse.copy(alpha = 0.06f)

/** A `.bibleVerse` anchor (design §2.3): the verse in full on purple, then "N reflections" and "⋯". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VerseAnchorCard(card: NoteCardUi, sectionDay: LocalDate?, onOpen: () -> Unit, onMenu: () -> Unit) {
    val c = AppTheme.colors
    val note = card.note
    CardSurface(Modifier.testTag("noteRow_${note.id}")) {
        Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = onMenu)) {
            Column(Modifier.fillMaxWidth().background(scriptureFill(c)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                ScriptureHeader(card.verseRef.orEmpty(), card.badge.sermonMark, cardTime(note.createdAt, sectionDay), note)
                if (card.showsBody) {
                    MarkedText(
                        MarkedVerse(card.displayContent, card.bodyMarks).annotated(),
                        style = textStyle(16, 1.6f, serif = true),
                        color = c.neutral100,
                        modifier = Modifier.padding(top = 6.dp).testTag("anchorVerse"),
                    )
                }
            }
            HorizontalDivider(color = c.noteTypeBibleVerse.copy(alpha = 0.12f))
            Row(Modifier.height(44.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = c.neutral90, modifier = Modifier.size(14.dp))
                Text(threadLabel(isAnchor = true, count = card.threadCount), style = textStyle(12), color = c.neutral90)
                card.tagNames.firstOrNull()?.let { TagPill(it, compact = true) }
                Spacer(Modifier.weight(1f))
                MoreButton(note.id, onMenu)
            }
        }
    }
}

/**
 * iOS `NoteCellView.backfillMissingVerseText`: a row whose anchor lacks text in some selected version
 * asks for it when it appears and again whenever the network comes or goes.
 */
@Composable
fun VerseBackfillEffect(card: NoteCardUi, isOnline: Boolean, backfill: (noteId: String) -> Unit) {
    if (!card.missingVerseText) return
    LaunchedEffect(card.note.id, isOnline) { backfill(card.note.id) }
}
