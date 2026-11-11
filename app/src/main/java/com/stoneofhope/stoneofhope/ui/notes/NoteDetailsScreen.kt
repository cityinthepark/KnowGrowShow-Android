package com.stoneofhope.stoneofhope.ui.notes

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.stoneofhope.stoneofhope.data.notes.NoteSwipeSequence
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Badge
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.stoneofhope.stoneofhope.ui.effects.transformText
import com.stoneofhope.stoneofhope.ui.effects.withTransformGlow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.notes.NoteBadges
import com.stoneofhope.stoneofhope.data.notes.PinSegment
import com.stoneofhope.stoneofhope.data.notes.ResolvedVerse
import com.stoneofhope.stoneofhope.data.notes.ThreadDay
import com.stoneofhope.stoneofhope.data.notes.ThreadItem
import com.stoneofhope.stoneofhope.data.notes.VerseDisplay
import com.stoneofhope.stoneofhope.data.notes.isTextEditable
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
/**
 * Note details, following iOS `NoteDetailsView` (373d3a5) in Material: a prayer's or verse note's linked
 * verse as its own card; the note card with its date block, tags, body and a row of action icons;
 * comments (or an anchor's reflections) as one card per day beside a date column; and the composer.
 * The "⋯" sheet stays as a second way to every action.
 */
@Composable
fun NoteDetailsScreen(onBack: () -> Unit, onOpenNeighbor: (noteId: String) -> Unit = {}, viewModel: NoteDetailsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = rememberNoteActionsState()
    val commentActions = rememberCommentActionsState()
    val composerFocus = remember { FocusRequester() }
    LaunchedEffect(state.gone) { if (state.gone) onBack() }
    val c = AppTheme.colors
    val card = state.card

    Box(
        Modifier.fillMaxSize().background(c.pageBackground).swipeBetweenNotes { step -> viewModel.neighbor(step)?.let(onOpenNeighbor) },
    ) {
        // Android 12+ blurs the page behind a lifted comment (design C4); older versions only dim it.
        Column(Modifier.fillMaxSize().statusBarsPadding().then(if (commentActions.isLifted) Modifier.blur(3.dp) else Modifier)) {
            TopBar(card, onBack, onMenu = { card?.let { actions.open(it.note.id) } })
            if (card == null) return@Column
            val note = card.note
            val linkedVerse = state.verse?.takeIf { !card.isAnchor }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).testTag("noteDetails"),
            ) {
                if (linkedVerse != null) {
                    item(key = "linkedVerse") {
                        Surface(shape = RoundedCornerShape(16.dp), color = c.surface0, border = BorderStroke(1.dp, c.neutral20), modifier = Modifier.fillMaxWidth().testTag("linkedVerse")) {
                            Column { VerseArea(linkedVerse, foldable = true, divider = false, moreVersions = state.moreVersions) }
                        }
                    }
                }
                item(key = "card") {
                    NoteCard(
                        card = card,
                        anchorVerse = state.verse?.takeIf { card.isAnchor },
                        moreVersions = state.moreVersions,
                        onAction = { viewModel.onAction(note, it) },
                        onSub = { actions.openSub(note.id, it) },
                        onComment = { composerFocus.requestFocus() },
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                item(key = "thread") {
                    Thread(card.isAnchor, state.thread, state.threadCount, commentActions, onAction = viewModel::onThreadAction)
                }
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
}

/** Back, the type chip (iOS: the badge as the title), and "⋯" for the full actions sheet. */
@Composable
private fun TopBar(card: NoteCardUi?, onBack: () -> Unit, onMenu: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)) {
        RoundButton(onClick = onBack, description = stringResource(R.string.back), modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, modifier = Modifier.size(20.dp))
        }
        if (card != null) {
            TypeChip(card.badge, size = 13, modifier = Modifier.align(Alignment.Center))
            RoundButton(onClick = onMenu, description = stringResource(R.string.action_more), modifier = Modifier.align(Alignment.CenterEnd).testTag("detailsMenu")) {
                Icon(Icons.Filled.MoreHoriz, null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * iOS `noteCard`: the date block with the tags to its right, the body (an anchor's verse text in every
 * selected version), then the action icons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteCard(
    card: NoteCardUi,
    anchorVerse: ResolvedVerse?,
    moreVersions: List<ResolvedVerse>,
    onAction: (NoteAction) -> Unit,
    onSub: (Sub) -> Unit,
    onComment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val note = card.note
    Surface(shape = RoundedCornerShape(16.dp), color = c.surface0, border = BorderStroke(1.dp, c.neutral20), modifier = modifier.fillMaxWidth()) {
        Column {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateBlock(note.createdAt)
                    if (card.tagNames.isNotEmpty()) {
                        FlowRow(
                            Modifier.weight(1f).padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) { card.tagNames.forEach { TagPill(it, ownerId = note.id) } }
                    }
                }
                if (!card.isAnchor) {
                    if (note.noteType == NoteType.Image) {
                        var viewing by rememberSaveable { mutableStateOf(false) }
                        NoteImage(note, maxHeight = 360.dp, onClick = { viewing = true })
                        if (viewing) NoteImageViewer(note, onDismiss = { viewing = false })
                    }
                    paragraphs(card.displayContent).forEach {
                        Text(it, style = textStyle(17, 1.6f).withTransformGlow(note.id), color = c.neutral100, modifier = Modifier.transformText(note.id).testTag("noteBody"))
                    }
                }
            }
            if (anchorVerse != null) VerseArea(anchorVerse, foldable = false, divider = false, moreVersions = moreVersions)
            HorizontalDivider(color = c.neutral20)
            NoteDetailActionBar(card, onAction, onSub, onComment)
        }
    }
}

/** iOS `NoteDateBlock`: the day in large digits, then "Nov · Mon" over "2026 · 7:15 AM". */
@Composable
private fun DateBlock(createdAt: Instant) {
    val c = AppTheme.colors
    val at = createdAt.atZone(ZoneId.systemDefault())
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("dateBlock")) {
        Text("${at.dayOfMonth}", style = textStyle(40, 1.1f, FontWeight.SemiBold), color = c.yellow100)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${monthShort(at.toLocalDate())} · ${weekdayShort(at.toLocalDate())}", style = textStyle(15, weight = FontWeight.SemiBold), color = c.neutral100)
            Text("${at.year} · ${clockTime(createdAt)}", style = textStyle(12), color = c.neutral90)
        }
    }
}

/**
 * iOS `NoteDetailActionBar` / `NoteActionButtonsRow`, spread across the card: 🙌/🙏 on prayers, copy,
 * pin, delete, comment, star (an inline 1–3 picker), tags (with a count), and edit on editable notes.
 */
@Composable
private fun NoteDetailActionBar(card: NoteCardUi, onAction: (NoteAction) -> Unit, onSub: (Sub) -> Unit, onComment: () -> Unit) {
    val c = AppTheme.colors
    val note = card.note
    val context = LocalContext.current
    val copied = stringResource(R.string.action_copied)
    var picking by rememberSaveable { mutableStateOf(false) }
    // Up to eight buttons plus three stars: 44dp targets so a prayer's row fits a phone (iOS: 36pt wide).
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 44.dp) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalAlignment = Alignment.End) {
        Row(Modifier.fillMaxWidth().testTag("noteActionBar"), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (note.noteType == NoteType.Prayer) {
                val answered = note.isAnswered == true
                val description = stringResource(R.string.answered)
                IconButton(onClick = { onAction(NoteAction.ToggleAnswered) }, modifier = Modifier.testTag("barAnswered")) {
                    Text(if (answered) "🙌" else "🙏", style = textStyle(19), modifier = Modifier.semantics { contentDescription = description })
                }
            }
            if (note.noteType != NoteType.Image) {
                IconButton(onClick = {
                    copyToClipboard(context, VerseDisplay.copyText(note, card.displayContent))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy), tint = c.neutral90, modifier = Modifier.size(20.dp)) }
            }
            val pinned = note.isPinned == true
            IconButton(onClick = { onAction(NoteAction.TogglePinned) }, modifier = Modifier.testTag("barPin")) {
                Icon(
                    if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    stringResource(if (pinned) R.string.action_unpin else R.string.action_pin),
                    tint = if (pinned) c.pinnedTile.foreground else c.neutral90, modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = { onSub(Sub.Delete) }, modifier = Modifier.testTag("barDelete")) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete), tint = c.red80, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onComment, modifier = Modifier.testTag("barComment")) {
                Icon(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.action_add_comment), tint = c.neutral90, modifier = Modifier.size(20.dp))
            }
            StarsButton(note.starLevel, minSize = 44.dp, starSize = 14.dp, onClick = { picking = !picking }, modifier = Modifier.testTag("barStar"))
            TagsButton(note.tagIds.size, onClick = { onSub(Sub.Tags) }, modifier = Modifier.testTag("barTags"))
            if (note.isTextEditable) {
                IconButton(onClick = { onSub(Sub.Edit) }, modifier = Modifier.testTag("barEdit")) {
                    Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit), tint = c.neutral90, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (picking) InlineStarPicker(Modifier.padding(end = 6.dp, bottom = 8.dp)) { picking = false; onAction(NoteAction.Star(it)) }
    }
    }
}

/** iOS `StarIconView` as a button: up to three stars (an outline when none), as wide as they need. */
@Composable
private fun StarsButton(level: Int, minSize: Dp, starSize: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.action_star)
    Box(
        modifier.sizeIn(minWidth = minSize, minHeight = minSize).clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }.padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) { StarIcons(level, size = starSize) }
}

/** The tag icon, with the number of tags on it when there is more than one (iOS badge). */
@Composable
private fun TagsButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier, iconSize: Dp = 20.dp) {
    val c = AppTheme.colors
    IconButton(onClick = onClick, modifier = modifier) {
        BadgedBox(badge = { if (count > 1) Badge { Text("$count") } }) {
            Icon(if (count > 0) Icons.Filled.Sell else Icons.Outlined.Sell, stringResource(R.string.action_tags), tint = if (count > 0) c.yellow100 else c.neutral90, modifier = Modifier.size(iconSize))
        }
    }
}

/** iOS `InlineStarPicker`: one to three stars, or none. */
@Composable
private fun InlineStarPicker(modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val c = AppTheme.colors
    Surface(shape = RoundedCornerShape(14.dp), color = c.pageBackground, shadowElevation = 3.dp, modifier = modifier.testTag("starPicker")) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            (1..3).forEach { level ->
                TextButton(onClick = { onSelect(level) }, modifier = Modifier.testTag("star$level")) {
                    StarIcons(level, size = 18.dp)
                }
            }
            IconButton(onClick = { onSelect(0) }, modifier = Modifier.testTag("star0")) {
                Icon(Icons.Filled.Cancel, stringResource(R.string.action_remove_star), tint = c.neutral90)
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
 * is its note, so it never folds. [moreVersions] (an anchor's other selected versions, iOS `456b7e6`)
 * follow, each under its own version tag.
 */
@Composable
internal fun VerseArea(verse: ResolvedVerse, foldable: Boolean, divider: Boolean = true, moreVersions: List<ResolvedVerse> = emptyList()) {
    val c = AppTheme.colors
    val fill = c.noteTypeBibleVerse.copy(alpha = 0.06f)
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    val needsFold = foldable && (verse.verses.size > 3 || overflows)
    Column(Modifier.fillMaxWidth().background(fill).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = if (needsFold) 4.dp else 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${NoteBadges.BIBLE_ICON} " + verse.refLabel.uppercase(), style = capsStyle(0.08f), color = c.scriptureInk)
            VersionTag(verse.versionAbbr)
        }
        Box(Modifier.padding(top = 8.dp)) {
            FoldBox(collapsed = foldable && !expanded, maxHeight = FoldedHeight, onOverflow = { overflows = it }) {
                Column {
                    VerseParagraphs(verse)
                    moreVersions.forEach { other ->
                        Box(Modifier.padding(top = 14.dp, bottom = 8.dp)) { VersionTag(other.versionAbbr) }
                        VerseParagraphs(other)
                    }
                }
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
private fun VersionTag(versionAbbr: String) {
    if (versionAbbr.isEmpty()) return
    val c = AppTheme.colors
    Text(
        BibleVersions.toDisplay(versionAbbr),
        style = textStyle(11, weight = FontWeight.SemiBold),
        color = c.scriptureInk,
        modifier = Modifier.background(c.noteTypeBibleVerse.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp).testTag("versionTag"),
    )
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

private val RailWidth = 40.dp

/**
 * iOS `NoteDetailTimelineContent`: "Comments" (or "Reflections") with its count, a month line per month,
 * and each day as one card beside its date — a line in the date column joins consecutive days. A pinned
 * run with nothing else that day is a compact line (one per run, the user's choice).
 */
@Composable
internal fun Thread(
    isAnchor: Boolean,
    days: List<ThreadDay>,
    count: Int,
    actions: CommentActionsState,
    onAction: (ThreadItem, NoteAction) -> Unit,
) {
    val c = AppTheme.colors
    if (days.isEmpty()) {
        Text(stringResource(if (isAnchor) R.string.no_reflections else R.string.no_comments), style = textStyle(14), color = c.neutral90, modifier = Modifier.padding(bottom = 12.dp))
        return
    }
    Column {
        Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(if (isAnchor) R.string.reflections_title else R.string.comments_title), style = textStyle(17, weight = FontWeight.SemiBold), color = c.neutral100)
            if (count > 0) Text("$count", style = textStyle(13), color = c.neutral90, modifier = Modifier.padding(bottom = 1.dp).testTag("threadCount"))
        }
        days.forEachIndexed { i, day ->
            val month = YearMonth.from(day.day)
            if (i == 0 || month != YearMonth.from(days[i - 1].day)) {
                Text(monthYearTitle(month), style = textStyle(13, weight = FontWeight.SemiBold), color = c.neutral90, modifier = Modifier.padding(top = if (i == 0) 0.dp else 8.dp, bottom = 10.dp).testTag("monthTitle"))
            }
            val compact = day.items.isEmpty() && !day.answered
            val lineColor = c.neutral20
            Row(
                Modifier.fillMaxWidth().padding(bottom = if (compact) 8.dp else 12.dp).drawBehind {
                    if (day.connectsBelow) {
                        val x = RailWidth.toPx() / 2
                        drawLine(lineColor, Offset(x, (if (compact) 22.dp else 46.dp).toPx()), Offset(x, size.height - 4.dp.toPx()), strokeWidth = 2.dp.toPx())
                    }
                }.testTag(if (day.connectsBelow) "dayConnected" else "day"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Top,
            ) {
                if (compact) {
                    Text("${day.day.dayOfMonth}", style = textStyle(13, weight = FontWeight.SemiBold), color = c.yellow100, textAlign = TextAlign.Center, modifier = Modifier.width(RailWidth))
                    day.pinned?.let { PinnedLine(it) }
                } else {
                    Column(Modifier.width(RailWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${day.day.dayOfMonth}", style = textStyle(20, 1.15f, FontWeight.SemiBold), color = c.yellow100)
                        // iOS `.minimumScaleFactor(0.7)`: "Yesterday" shrinks to fit the column.
                        BasicText(
                            dayLabel(day.day), style = textStyle(11).copy(color = c.neutral90, textAlign = TextAlign.Center), maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Surface(shape = RoundedCornerShape(14.dp), color = c.surface0, border = BorderStroke(1.dp, c.neutral20), modifier = Modifier.weight(1f).testTag("dayCard")) {
                        Column {
                            day.pinned?.let { Box(Modifier.padding(start = 14.dp, top = 12.dp)) { PinnedLine(it) } }
                            if (day.answered) AnsweredLine(bottom = if (day.items.isEmpty()) 12.dp else 0.dp)
                            day.items.forEachIndexed { j, item ->
                                if (j > 0) HorizontalDivider(color = c.neutral20)
                                CommentLine(item, actions, onAction)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** iOS `timelineWeekdayLabel`: "Today", "Yesterday", else the short weekday. */
@Composable
private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> weekdayShort(day)
    }
}

/** "📌 Pinned" or "📌 Pinned · Oct 20 – 26" (`PinLog` keeps days, not times). */
@Composable
private fun PinnedLine(run: PinSegment) {
    val label = if (run.start == run.end) stringResource(R.string.thread_pinned)
    else stringResource(R.string.thread_pinned_range, dayRange(run.start, run.end))
    Text("📌 $label", style = textStyle(13, weight = FontWeight.Medium), color = AppTheme.colors.neutral90, modifier = Modifier.testTag("pinnedLine"))
}

/** iOS `showsAnsweredBadge`: the day a prayer was marked answered without an "Answered" comment. */
@Composable
private fun AnsweredLine(bottom: Dp) {
    Text(
        "🙌 " + stringResource(R.string.answered),
        style = textStyle(12, weight = FontWeight.SemiBold),
        color = AppTheme.colors.answeredInk,
        modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = bottom).testTag("answeredLine"),
    )
}

/**
 * iOS `TimelineCommentLine`: 🙌 (a folded "Answered" marker), tags and stars on the first line; the text
 * (or "🙌 Answered Today" for a marker standing alone); then the time and the action icons. Long press
 * lifts the line with its actions (design C4), as before.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentLine(item: ThreadItem, actions: CommentActionsState, onAction: (ThreadItem, NoteAction) -> Unit) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val copied = stringResource(R.string.action_copied)
    val haptics = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var picking by rememberSaveable(item.id) { mutableStateOf(false) }
    val showsAnswered = item.isAnswered && !item.isAnsweredMarker
    Column(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .pointerInput(item.id) {
                detectTapGestures(onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.open(item.id, bounds)
                })
            }
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 2.dp)
            .testTag("comment"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showsAnswered || item.starLevel > 0 || item.tagNames.isNotEmpty()) {
            FlowRow(Modifier.padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (showsAnswered) Text("🙌", style = textStyle(15), modifier = Modifier.align(Alignment.CenterVertically).testTag("commentAnswered"))
                item.tagNames.forEach { TagPill(it, compact = true, ownerId = item.id) }
                if (item.starLevel > 0) StarIcons(item.starLevel, Modifier.align(Alignment.CenterVertically), size = 13.dp)
            }
        }
        if (item.isAnsweredMarker) {
            Text("🙌 " + stringResource(R.string.answered_today), style = textStyle(15, 1.5f, FontWeight.SemiBold), color = c.answeredInk, modifier = Modifier.testTag("answeredMarker"))
        } else {
            Text(item.text, style = textStyle(15, 1.6f).withTransformGlow(item.id), color = c.neutral100, modifier = Modifier.padding(end = 10.dp).transformText(item.id))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(clockTime(item.createdAt), style = textStyle(12), color = c.neutral90, maxLines = 1)
            Spacer(Modifier.weight(1f))
            val small = Modifier.size(36.dp)
            if (!item.isAnsweredMarker) {
                IconButton(onClick = {
                    copyToClipboard(context, item.text)
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                }, modifier = small) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy), tint = c.neutral90, modifier = Modifier.size(16.dp)) }
            }
            IconButton(onClick = { actions.openSub(item.id, Sub.Delete) }, modifier = small.testTag("commentDelete")) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete), tint = c.red80, modifier = Modifier.size(16.dp))
            }
            StarsButton(item.starLevel, minSize = 36.dp, starSize = 13.dp, onClick = { picking = !picking }, modifier = Modifier.testTag("commentStar"))
            TagsButton(item.tagIds.size, onClick = { actions.openSub(item.id, Sub.Tags) }, modifier = small.testTag("commentTags"), iconSize = 16.dp)
            if (!item.isAnsweredMarker) {
                IconButton(onClick = { actions.openSub(item.id, Sub.Edit) }, modifier = small.testTag("commentEdit")) {
                    Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit), tint = c.neutral90, modifier = Modifier.size(16.dp))
                }
            }
        }
        if (picking) {
            InlineStarPicker(Modifier.align(Alignment.End).padding(end = 6.dp, bottom = 8.dp)) { picking = false; onAction(item, NoteAction.Star(it)) }
        }
    }
}

/**
 * The bottom composer (§3.4): a pill field ([hint]: "Add a comment…" / "Add a reflection…") growing to
 * 5 lines, a round send button, and for unanswered prayers an "Answered Today" toggle above it while
 * typing (iOS `commentComposer`).
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
    var focused by remember { mutableStateOf(false) }
    // iOS: the toggle shows while the field is focused or has text. Unlike iOS (IOS_ISSUES O4), a hidden
    // toggle is also off, so an empty send can't log a stray "Answered" marker.
    val showToggle = canMarkAnswered && (focused || text.isNotEmpty())
    LaunchedEffect(showToggle) { if (!showToggle) answered = false }
    val markAnswered = showToggle && answered
    val canSend = text.isNotBlank() || markAnswered
    Column(Modifier.fillMaxWidth().background(c.surface0).imePadding().navigationBarsPadding()) {
        HorizontalDivider(color = c.neutral20)
        top?.invoke()
        if (showToggle) {
            val description = stringResource(R.string.action_mark_answered)
            Surface(
                onClick = { answered = !answered },
                shape = RoundedCornerShape(50),
                color = if (answered) c.answeredPrayerTile.background else c.pageBackground,
                border = BorderStroke(1.dp, if (answered) c.answeredInk.copy(alpha = 0.4f) else c.neutral20),
                modifier = Modifier.padding(start = 16.dp, top = 10.dp).height(32.dp).testTag("answeredToday")
                    .semantics { contentDescription = description; selected = answered },
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
                modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { focused = it.isFocused }.testTag("composerField"),
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
                    onSend(text.trim(), markAnswered)
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

/**
 * iOS `NoteDetailsPagerView`: swipe sideways for the next / previous note of the list this one was opened
 * from. Watches the gesture without consuming it, so the page still scrolls and its buttons still work.
 */
private fun Modifier.swipeBetweenNotes(onStep: (Int) -> Unit): Modifier = composed {
    val density = LocalDensity.current.density
    val widthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var total = Offset.Zero
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                total += change.positionChange()
                if (!change.pressed) break
            }
            NoteSwipeSequence.swipeStep(total.x / density, total.y / density, down.position.x / density, widthDp)?.let(onStep)
        }
    }
}
