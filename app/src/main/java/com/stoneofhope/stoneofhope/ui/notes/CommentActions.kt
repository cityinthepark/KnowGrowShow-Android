package com.stoneofhope.stoneofhope.ui.notes

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.notes.ThreadItem
import com.stoneofhope.stoneofhope.ui.theme.AppColors
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import kotlin.math.roundToInt

/** Which comment's actions are open (read live from the thread by id), where its card sat, and the follow-up dialog. */
@Stable
class CommentActionsState {
    var itemId by mutableStateOf<String?>(null)
        private set
    internal var cardBounds by mutableStateOf(Rect.Zero)
        private set
    private var sub by mutableStateOf<Sub?>(null)

    fun open(id: String, bounds: Rect) { itemId = id; cardBounds = bounds; sub = null }
    fun close() { itemId = null; sub = null }
    internal fun show(next: Sub) { sub = next }
    internal val current get() = sub
    /** The lifted card is showing (the screen behind it blurs). */
    val isLifted get() = itemId != null && sub == null
}

@Composable
fun rememberCommentActionsState() = remember { CommentActionsState() }

/**
 * Comment / reflection actions (design C4; iOS `TimelineCommentLine` context menu): a scrim, the card
 * lifted where it sat with its full date, and Edit · Copy · Star · Tags · Delete under it — above it
 * when there's no room. An "Answered" marker has only Star · Tags · Delete, like iOS. [item] is the
 * open comment read from the screen's collected state, so the tag picker shows changes at once.
 */
@Composable
fun CommentActionsHost(
    state: CommentActionsState,
    item: ThreadItem?,
    allTags: List<TagEntity>,
    onAction: (ThreadItem, NoteAction) -> Unit,
) {
    val latest by rememberUpdatedState(item)
    if (state.itemId == null) return
    val t = item
    if (t == null) { state.close(); return }
    val act: (NoteAction) -> Unit = { a -> latest?.let { onAction(it, a) } }

    when (state.current) {
        null -> Lifted(t, state.cardBounds, onDismiss = state::close, onSub = state::show)
        Sub.Edit -> EditNoteSheet(
            t.text,
            onDismiss = state::close,
            title = stringResource(if (t.isReflection) R.string.edit_reflection_title else R.string.edit_comment_title),
        ) { state.close(); act(NoteAction.Edit(it)) }
        Sub.Star -> StarDialog(onDismiss = state::close) { state.close(); act(NoteAction.Star(it)) }
        Sub.Tags -> TagPickerSheet(allTags, t.tagIds.toSet(), onAdd = { act(NoteAction.AddTag(it)) }, onRemove = { act(NoteAction.RemoveTag(it)) }, onDismiss = state::close)
        Sub.Delete -> AlertDialog(
            onDismissRequest = state::close,
            title = { Text(stringResource(if (t.isReflection) R.string.reflection_delete_title else R.string.comment_delete_title)) },
            confirmButton = {
                TextButton(onClick = { state.close(); act(NoteAction.Delete) }, modifier = Modifier.testTag("confirmDelete")) {
                    Text(stringResource(R.string.action_delete), color = AppTheme.colors.red80)
                }
            },
            dismissButton = { TextButton(onClick = state::close) { Text(stringResource(R.string.cancel)) } },
        )
        Sub.Comment -> state.close()
    }
}

@Composable
private fun Lifted(item: ThreadItem, cardBounds: Rect, onDismiss: () -> Unit, onSub: (Sub) -> Unit) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val copied = stringResource(R.string.action_copied)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val statusBars = WindowInsets.statusBars
    val navBars = WindowInsets.navigationBars
    Layout(
        content = {
            LiftedCard(item)
            ActionBar(item, onSub = onSub, onCopy = {
                copyToClipboard(context, item.text)
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                onDismiss()
            })
        },
        modifier = Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .background(Color.Black.copy(alpha = 0.38f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .testTag("commentActions"),
    ) { measurables, constraints ->
        val gap = 10.dp.roundToPx()
        val top = statusBars.getTop(this) + 8.dp.roundToPx()
        val bottom = constraints.maxHeight - navBars.getBottom(this) - 8.dp.roundToPx()
        val width = cardBounds.width.roundToInt().coerceIn(0, constraints.maxWidth)
        val bar = measurables[1].measure(Constraints.fixedWidth(width))
        val card = measurables[0].measure(Constraints(minWidth = width, maxWidth = width, maxHeight = (bottom - top - gap - bar.height).coerceAtLeast(0)))
        val x = (cardBounds.left - origin.x).roundToInt()
        val y0 = (cardBounds.top - origin.y).roundToInt()
        layout(constraints.maxWidth, constraints.maxHeight) {
            when {
                // Room below the card where it sat.
                y0 >= top && y0 + card.height + gap + bar.height <= bottom -> {
                    card.place(x, y0); bar.place(x, y0 + card.height + gap)
                }
                // Near the bottom: the bar goes above.
                y0 - gap - bar.height >= top && y0 + card.height <= bottom -> {
                    card.place(x, y0); bar.place(x, y0 - gap - bar.height)
                }
                // Partly off screen or very tall: move the card into view, bar below.
                else -> {
                    val y = y0.coerceIn(top, (bottom - card.height - gap - bar.height).coerceAtLeast(top))
                    card.place(x, y); bar.place(x, y + card.height + gap)
                }
            }
        }
    }
}

/** The card as it was, lifted: the full text (scrolls if taller than the screen) and the full date. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiftedCard(item: ThreadItem) {
    val c = AppTheme.colors
    Surface(shape = RoundedCornerShape(16.dp), color = c.surface0, shadowElevation = 12.dp, modifier = Modifier.testTag("liftedComment")) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (item.isAnsweredMarker) {
                Text("🙌 " + stringResource(R.string.answered), style = textStyle(15, 1.6f, FontWeight.SemiBold), color = c.answeredInk)
            } else {
                Text(item.text, style = textStyle(15, 1.6f), color = c.neutral100)
            }
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(fullDateTime(item.createdAt), style = textStyle(12), color = c.neutral90, modifier = Modifier.align(Alignment.CenterVertically))
                if (item.starLevel > 0) StarIcons(item.starLevel, Modifier.align(Alignment.CenterVertically), size = 12.dp)
                item.tagNames.forEach { TagPill(it, compact = true) }
            }
        }
    }
}

/** Edit · Copy · Star · Tags · Delete; a starred comment and one with tags light up their cell. */
@Composable
private fun ActionBar(item: ThreadItem, onSub: (Sub) -> Unit, onCopy: () -> Unit) {
    val c = AppTheme.colors
    Surface(shape = RoundedCornerShape(20.dp), color = c.surface0, shadowElevation = 8.dp, modifier = Modifier.testTag("commentActionBar")) {
        Row(Modifier.padding(6.dp)) {
            if (!item.isAnsweredMarker) {
                Cell(Icons.Outlined.Edit, stringResource(R.string.action_edit), null, Modifier.weight(1f).testTag("commentEdit")) { onSub(Sub.Edit) }
                Cell(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy), null, Modifier.weight(1f).testTag("commentCopy"), onClick = onCopy)
            }
            val starred = item.starLevel > 0
            Cell(
                if (starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                stringResource(if (starred) R.string.tile_starred else R.string.action_star),
                c.starredTile.takeIf { starred }, Modifier.weight(1f).testTag("commentStar"),
            ) { onSub(Sub.Star) }
            val tags = item.tagIds.size
            Cell(
                Icons.Outlined.Sell,
                if (tags > 0) stringResource(R.string.action_tags_count, tags) else stringResource(R.string.action_tags),
                c.pinnedTile.takeIf { tags > 0 }, Modifier.weight(1f).testTag("commentTags"),
            ) { onSub(Sub.Tags) }
            Cell(Icons.Outlined.Delete, stringResource(R.string.action_delete), AppColors.ChipPair(Color.Transparent, c.red80), Modifier.weight(1f).testTag("commentDelete")) {
                onSub(Sub.Delete)
            }
        }
    }
}

@Composable
private fun Cell(icon: ImageVector, label: String, active: AppColors.ChipPair?, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val fg = active?.foreground ?: c.neutral100
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = active?.background ?: Color.Transparent, modifier = modifier.height(58.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = textStyle(11, weight = FontWeight.SemiBold), color = fg, maxLines = 1)
        }
    }
}
