package com.stoneofhope.stoneofhope.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.notes.NoteBadges
import com.stoneofhope.stoneofhope.data.notes.PinSegment
import com.stoneofhope.stoneofhope.data.notes.ResolvedVerse
import com.stoneofhope.stoneofhope.data.notes.ThreadDay
import com.stoneofhope.stoneofhope.data.notes.ThreadItem
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import java.time.YearMonth

/**
 * Redesigned note details (docs/design/note-details): one card holding the verse (folded when long)
 * and the note, with tags / star / "⋯" at its foot; comments (or an anchor's reflections) by day;
 * and a composer at the bottom instead of the "+" button.
 */
@Composable
fun NoteDetailsScreen(onBack: () -> Unit, viewModel: NoteDetailsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = rememberNoteActionsState()
    val commentActions = rememberCommentActionsState()
    val composerFocus = remember { FocusRequester() }
    var starDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.gone) { if (state.gone) onBack() }
    val c = AppTheme.colors
    val card = state.card

    Box(Modifier.fillMaxSize().background(c.pageBackground)) {
        // Android 12+ blurs the page behind a lifted comment (design C4); older versions only dim it.
        Column(Modifier.fillMaxSize().statusBarsPadding().then(if (commentActions.isLifted) Modifier.blur(3.dp) else Modifier)) {
            TopBar(card, onBack, onMenu = { card?.let { actions.open(it.note.id) } })
            if (card == null) return@Column
            val note = card.note
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
                modifier = Modifier.weight(1f).testTag("noteDetails"),
            ) {
                item(key = "card") {
                    MainCard(
                        card = card,
                        verse = state.verse,
                        onStar = { viewModel.onAction(note, NoteAction.Star(if (note.starLevel > 0) 0 else 1)) },
                        onStarLevels = { starDialog = true },
                        onMenu = { actions.open(note.id) },
                    )
                }
                item(key = "thread") { Thread(card.isAnchor, state.thread, state.threadCount, onItemActions = commentActions::open) }
            }
            Composer(
                hint = stringResource(if (card.isAnchor) R.string.add_reflection_hint else R.string.add_comment_hint),
                canMarkAnswered = note.noteType == NoteType.Prayer && note.isAnswered != true,
                focusRequester = composerFocus,
                onSend = { text, answered -> viewModel.onAction(note, NoteAction.Comment(text, answered)) },
            )
        }
        CommentActionsHost(
            state = commentActions,
            item = commentActions.itemId?.let(state::threadItem),
            allTags = state.allTags,
            onAction = viewModel::onThreadAction,
        )
    }

    NoteActionsHost(
        state = actions,
        target = actions.noteId?.let(state::actionTarget),
        allTags = state.allTags,
        onAction = viewModel::onAction,
        onAddComment = { composerFocus.requestFocus() },
    )
    if (starDialog && card != null) {
        StarDialog(onDismiss = { starDialog = false }) { starDialog = false; viewModel.onAction(card.note, NoteAction.Star(it)) }
    }
}

/** Back, the type chip (+ a star when starred) over "Mon, Oct 12, 2026 · 7:15 AM", and "⋯" (§3.1). */
@Composable
private fun TopBar(card: NoteCardUi?, onBack: () -> Unit, onMenu: () -> Unit) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(onClick = onBack, description = stringResource(R.string.back)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (card != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypeChip(card.badge, size = 13)
                    if (card.note.starLevel > 0) Icon(Icons.Filled.Star, null, tint = c.starredTile.foreground, modifier = Modifier.size(14.dp))
                }
                Text(fullDateTime(card.note.createdAt), style = textStyle(12), color = c.neutral90, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (card != null) {
            RoundButton(onClick = onMenu, description = stringResource(R.string.action_more), modifier = Modifier.testTag("detailsMenu")) {
                Icon(Icons.Filled.MoreHoriz, null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** The single card: verse area, note body, and the tags / star / "⋯" row (§3.2). */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun MainCard(card: NoteCardUi, verse: ResolvedVerse?, onStar: () -> Unit, onStarLevels: () -> Unit, onMenu: () -> Unit) {
    val c = AppTheme.colors
    val note = card.note
    Surface(shape = RoundedCornerShape(24.dp), color = c.surface0, border = BorderStroke(1.dp, c.neutral20), modifier = Modifier.fillMaxWidth()) {
        Column {
            if (verse != null) VerseArea(verse, foldable = !card.isAnchor)
            if (!card.isAnchor) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (note.noteType == NoteType.Image) {
                        Surface(shape = RoundedCornerShape(14.dp), color = c.pageBackground, modifier = Modifier.fillMaxWidth()) {
                            Text("🖼️  " + stringResource(R.string.image_placeholder), style = textStyle(14), color = c.neutral90, modifier = Modifier.padding(16.dp))
                        }
                    }
                    paragraphs(card.displayContent).forEach {
                        Text(it, style = textStyle(18, 1.7f, FontWeight.Medium), color = c.neutral100, modifier = Modifier.testTag("noteBody"))
                    }
                }
            }
            HorizontalDivider(color = c.neutral20.copy(alpha = 0.6f))
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (note.noteType == NoteType.Prayer && note.isAnswered == true) Text("🙌", style = textStyle(14), modifier = Modifier.align(Alignment.CenterVertically))
                    if (note.isPinned == true) {
                        Icon(Icons.Filled.PushPin, stringResource(R.string.tile_pinned), tint = c.pinnedTile.foreground, modifier = Modifier.size(14.dp).align(Alignment.CenterVertically))
                    }
                    card.tagNames.forEach { TagPill(it) }
                }
                val starred = note.starLevel > 0
                Box(
                    Modifier.size(44.dp).combinedClickable(onClick = onStar, onLongClick = onStarLevels).testTag("cardStar"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        stringResource(R.string.action_star),
                        tint = if (starred) c.starredTile.foreground else c.neutral90,
                    )
                }
                IconButton(onClick = onMenu, modifier = Modifier.size(44.dp).testTag("cardMenu")) {
                    Icon(Icons.Filled.MoreHoriz, stringResource(R.string.action_more), tint = c.neutral90)
                }
            }
        }
    }
}

/** Keep line breaks; blank lines don't make empty paragraphs. */
private fun paragraphs(text: String): List<String> = text.split("\n").map { it.trimEnd() }.filter { it.isNotBlank() }

private val FoldedHeight = 136.dp

/**
 * The verse area (§3.2a): reference + version tag, one paragraph per verse with superscript numbers.
 * Over 3 verses or 5 lines it folds to ~136dp with a fade and "Show all N verses"; an anchor's verse
 * is its note, so it never folds.
 */
@Composable
internal fun VerseArea(verse: ResolvedVerse, foldable: Boolean, divider: Boolean = true) {
    val c = AppTheme.colors
    val fill = c.noteTypeBibleVerse.copy(alpha = 0.06f)
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    val needsFold = foldable && (verse.verses.size > 3 || overflows)
    Column(Modifier.fillMaxWidth().background(fill).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = if (needsFold) 4.dp else 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${NoteBadges.BIBLE_ICON} " + verse.refLabel.uppercase(), style = capsStyle(0.08f), color = c.scriptureInk)
            if (verse.versionAbbr.isNotEmpty()) {
                Text(
                    verse.versionAbbr,
                    style = textStyle(11, weight = FontWeight.SemiBold),
                    color = c.scriptureInk,
                    modifier = Modifier.background(c.noteTypeBibleVerse.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Box(Modifier.padding(top = 8.dp)) {
            FoldBox(collapsed = foldable && !expanded, maxHeight = FoldedHeight, onOverflow = { overflows = it }) {
                VerseParagraphs(verse)
            }
            if (needsFold && !expanded) {
                val base = fill.compositeOver(c.surface0)
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(48.dp).background(Brush.verticalGradient(listOf(base.copy(alpha = 0f), base))))
            }
        }
        if (needsFold) {
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 2.dp), modifier = Modifier.height(36.dp).testTag("verseFold")) {
                val label = if (expanded) stringResource(R.string.collapse)
                else pluralStringResource(R.plurals.show_all_verses, verse.verses.size, verse.verses.size)
                Text(label, style = textStyle(13, weight = FontWeight.SemiBold), color = c.scriptureInk)
                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = c.scriptureInk, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (divider) HorizontalDivider(color = c.noteTypeBibleVerse.copy(alpha = 0.12f))
}

@Composable
private fun VerseParagraphs(verse: ResolvedVerse) {
    val c = AppTheme.colors
    val style = textStyle(17, 1.6f, serif = true)
    val shown = verse.verses.filter { it.second.isNotBlank() }
    if (shown.size <= 1) {
        MarkedText(verse.combined.trimmed().annotated(), style = style, color = c.neutral100, modifier = Modifier.testTag("verseText"))
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("verseText")) {
            shown.forEach { (n, text) -> MarkedText(numberedVerse(n, verse.verse(n, text), c.scriptureInk.copy(alpha = 0.7f)), style = style, color = c.neutral100) }
        }
    }
}

/**
 * Lays its child out at full height but shows at most [maxHeight] while [collapsed]; reports whether
 * the full content is taller than that (the "more than 5 lines" rule).
 */
@Composable
private fun FoldBox(collapsed: Boolean, maxHeight: Dp, onOverflow: (Boolean) -> Unit, content: @Composable () -> Unit) {
    val limit = with(LocalDensity.current) { maxHeight.roundToPx() }
    Layout(content = content, modifier = Modifier.clipToBounds()) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(maxHeight = Constraints.Infinity))
        onOverflow(placeable.height > limit)
        val height = if (collapsed) minOf(placeable.height, limit) else placeable.height
        layout(placeable.width, height) { placeable.place(0, 0) }
    }
}

/** "Comments" / "Reflections" with count and month, then day groups with a date column (§3.3). */
@Composable
internal fun Thread(isAnchor: Boolean, days: List<ThreadDay>, count: Int, onItemActions: (id: String, cardBounds: Rect) -> Unit) {
    val c = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (isAnchor) R.string.reflections_title else R.string.comments_title), style = textStyle(17, weight = FontWeight.Bold), color = c.neutral100)
            Box(Modifier.heightIn(min = 22.dp).background(c.neutral20.copy(alpha = 0.6f), RoundedCornerShape(11.dp)).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                Text("$count", style = textStyle(12, weight = FontWeight.SemiBold), color = c.neutral90)
            }
            Spacer(Modifier.weight(1f))
            days.firstOrNull()?.let { Text(monthYearCaps(YearMonth.from(it.day)), style = capsStyle(0.06f).copy(fontWeight = FontWeight.SemiBold), color = c.neutral90) }
        }
        if (days.isEmpty()) {
            Text(stringResource(if (isAnchor) R.string.no_reflections else R.string.no_comments), style = textStyle(14), color = c.neutral90, modifier = Modifier.padding(horizontal = 4.dp))
        }
        days.forEachIndexed { i, day ->
            val month = YearMonth.from(day.day)
            if (i > 0 && month != YearMonth.from(days[i - 1].day)) {
                Text(monthYearCaps(month), style = capsStyle(0.06f).copy(fontWeight = FontWeight.SemiBold), color = c.neutral90, modifier = Modifier.padding(horizontal = 4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(36.dp).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${day.day.dayOfMonth}", style = textStyle(20, 1f, FontWeight.Bold), color = c.neutral100)
                    Text(weekdayShort(day.day), style = textStyle(11), color = c.neutral90, modifier = Modifier.padding(top = 3.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    day.pinned?.let { PinnedLine(it) }
                    if (day.answered) AnsweredLine()
                    day.items.forEach { item -> CommentCard(item, onActions = { onItemActions(item.id, it) }) }
                }
            }
        }
    }
}

/**
 * A pinned run as one gray line on its last day (§3.3; `PinLog` keeps days, not times):
 * "📌 Pinned" or "📌 Pinned · Oct 20 – 26".
 */
@Composable
private fun PinnedLine(run: PinSegment) {
    val label = if (run.start == run.end) stringResource(R.string.thread_pinned)
    else stringResource(R.string.thread_pinned_range, dayRange(run.start, run.end))
    Text("📌 $label", style = textStyle(13, weight = FontWeight.Medium), color = AppTheme.colors.neutral90, modifier = Modifier.padding(start = 4.dp, top = 12.dp).testTag("pinnedLine"))
}

/** iOS `showsAnsweredBadge`: the day a prayer was marked answered without an "Answered" comment. */
@Composable
private fun AnsweredLine() {
    Text(
        "🙌 " + stringResource(R.string.answered),
        style = textStyle(13, weight = FontWeight.SemiBold),
        color = AppTheme.colors.answeredInk,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp).testTag("answeredLine"),
    )
}

/**
 * A comment (or reflection) card; an "Answered" marker reads as a green label (§3.3). Long press or
 * its "⋯" opens the actions (C4) with the card's place on screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentCard(item: ThreadItem, onActions: (cardBounds: Rect) -> Unit) {
    val c = AppTheme.colors
    val haptics = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val open by rememberUpdatedState { onActions(bounds) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = c.surface0,
        border = BorderStroke(1.dp, c.neutral20),
        modifier = Modifier.fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    open()
                })
            }
            .testTag("comment"),
    ) {
        Row {
            Column(Modifier.weight(1f).padding(start = 14.dp, top = 12.dp, bottom = 12.dp)) {
                if (item.isAnsweredMarker) {
                    Text("🙌 " + stringResource(R.string.answered), style = textStyle(15, 1.6f, FontWeight.SemiBold), color = c.answeredInk)
                } else {
                    Text(item.text, style = textStyle(15, 1.6f), color = c.neutral100)
                }
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(clockTime(item.createdAt), style = textStyle(12), color = c.neutral90, modifier = Modifier.align(Alignment.CenterVertically))
                    if (item.starLevel > 0) StarIcons(item.starLevel, Modifier.align(Alignment.CenterVertically), size = 12.dp)
                    item.tagNames.forEach { TagPill(it, compact = true) }
                }
            }
            IconButton(onClick = { open() }, modifier = Modifier.padding(top = 4.dp, end = 4.dp).size(36.dp).testTag("commentMenu")) {
                Icon(Icons.Filled.MoreHoriz, stringResource(R.string.action_more), tint = c.neutral90, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * The bottom composer (§3.4): a pill field ([hint]: "Add a comment…" / "Add a reflection…") growing to
 * 5 lines, a round send button, and for unanswered prayers an "Answered today" toggle above it.
 * [top] sits above the field (the chapter verse page's Reflection / Prayer switch).
 */
@Composable
internal fun Composer(
    hint: String,
    canMarkAnswered: Boolean,
    focusRequester: FocusRequester,
    onSend: (String, Boolean) -> Unit,
    top: (@Composable () -> Unit)? = null,
) {
    val c = AppTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    var answered by rememberSaveable { mutableStateOf(false) }
    val canSend = text.isNotBlank() || (canMarkAnswered && answered)
    Column(Modifier.fillMaxWidth().background(c.surface0).imePadding().navigationBarsPadding()) {
        HorizontalDivider(color = c.neutral20)
        top?.invoke()
        if (canMarkAnswered) {
            Surface(
                onClick = { answered = !answered },
                shape = RoundedCornerShape(50),
                color = if (answered) c.answeredPrayerTile.background else c.pageBackground,
                border = BorderStroke(1.dp, if (answered) c.answeredInk.copy(alpha = 0.4f) else c.neutral20),
                modifier = Modifier.padding(start = 16.dp, top = 10.dp).height(32.dp).testTag("answeredToday"),
            ) {
                Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text("🙌 " + stringResource(R.string.answered_today), style = textStyle(13, weight = FontWeight.SemiBold), color = if (answered) c.answeredInk else c.neutral90)
                }
            }
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                maxLines = 5,
                textStyle = textStyle(15, 1.4f).copy(color = c.neutral100),
                cursorBrush = SolidColor(c.blue80),
                modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("composerField"),
                decorationBox = { inner ->
                    Box(
                        Modifier.heightIn(min = 44.dp).background(c.pageBackground, RoundedCornerShape(22.dp))
                            .border(1.dp, c.neutral20, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.isEmpty()) {
                            Text(hint, style = textStyle(15, 1.4f), color = c.neutral90)
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(10.dp))
            Surface(
                onClick = {
                    onSend(text.trim(), canMarkAnswered && answered)
                    text = ""
                    answered = false
                },
                enabled = canSend,
                shape = CircleShape,
                color = if (canSend) c.blue80 else c.neutral20,
                modifier = Modifier.size(44.dp).testTag("composerSend"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ArrowUpward, stringResource(R.string.send), tint = if (canSend) Color.White else c.neutral90)
                }
            }
        }
    }
}
