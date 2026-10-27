package com.stoneofhope.stoneofhope.ui.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.notes.NoteBadges
import com.stoneofhope.stoneofhope.data.notes.VerseDisplay
import com.stoneofhope.stoneofhope.data.notes.isTextEditable
import com.stoneofhope.stoneofhope.ui.theme.AppColors
import com.stoneofhope.stoneofhope.ui.theme.AppTheme

/** What the "⋯" sheet asks the screen to do (iOS `NoteActionButtonsRow` → `NotesManager`). */
sealed interface NoteAction {
    data object TogglePinned : NoteAction
    data object ToggleAnswered : NoteAction
    data class Star(val level: Int) : NoteAction
    data class AddTag(val name: String) : NoteAction
    data class RemoveTag(val tagId: String) : NoteAction
    data class Edit(val text: String) : NoteAction
    data object Delete : NoteAction
    /** [answered]: the prayer's "Answered today" toggle (details composer / comment sheet). */
    data class Comment(val text: String, val answered: Boolean) : NoteAction
}

/** The note the sheet is about, read live so a toggle shows its new state at once. */
data class NoteActionTarget(val note: NoteEntity, val displayContent: String, val verseRef: String?, val tagNames: List<String>)

internal enum class Sub { Star, Tags, Edit, Delete, Comment }

/** Which note's "⋯" sheet is open, and which follow-up dialog it led to. */
@Stable
class NoteActionsState {
    var noteId by mutableStateOf<String?>(null)
        private set
    private var sub by mutableStateOf<Sub?>(null)

    fun open(id: String) { noteId = id; sub = null }
    fun close() { noteId = null; sub = null }
    internal fun show(next: Sub) { sub = next }
    internal val current get() = sub
}

@Composable
fun rememberNoteActionsState() = remember { NoteActionsState() }

/**
 * Note "⋯" bottom sheet (design C3): preview, big buttons (Edit, Copy, Pin, Star, and Answered on
 * prayers), Tags and Add comment, then Delete. [target] is the open note read from the screen's
 * collected state, so toggles redraw at once. [onAddComment] null → a comment sheet opens here (the
 * list); the details screen focuses its composer instead.
 */
@Composable
fun NoteActionsHost(
    state: NoteActionsState,
    target: NoteActionTarget?,
    allTags: List<TagEntity>,
    onAction: (NoteEntity, NoteAction) -> Unit,
    onAddComment: ((NoteEntity) -> Unit)? = null,
) {
    // Handlers inside the sheet can outlive a recomposition; they must act on the note as it is now
    // (a second Pin tap has to see the first one).
    val latest by rememberUpdatedState(target)
    if (state.noteId == null) return
    val t = target
    if (t == null) { state.close(); return }
    val note = t.note
    val act: (NoteAction) -> Unit = { a -> latest?.let { onAction(it.note, a) } }

    when (state.current) {
        null -> ActionsSheet(t, onDismiss = state::close, onSub = state::show, onAction = act, onAddComment = {
            if (onAddComment != null) { state.close(); onAddComment(note) } else state.show(Sub.Comment)
        })
        Sub.Star -> StarDialog(onDismiss = state::close) { state.close(); act(NoteAction.Star(it)) }
        Sub.Tags -> TagPickerSheet(allTags, note.tagIds.toSet(), onAdd = { act(NoteAction.AddTag(it)) }, onRemove = { act(NoteAction.RemoveTag(it)) }, onDismiss = state::close)
        Sub.Edit -> EditNoteSheet(note.content, onDismiss = state::close) { state.close(); act(NoteAction.Edit(it)) }
        Sub.Comment -> CommentSheet(t.displayContent, canMarkAnswered = false, onDismiss = state::close) { text, answered ->
            state.close(); act(NoteAction.Comment(text, answered))
        }
        Sub.Delete -> AlertDialog(
            onDismissRequest = state::close,
            title = { Text(stringResource(R.string.note_delete_title)) },
            confirmButton = {
                TextButton(onClick = { state.close(); act(NoteAction.Delete) }, modifier = Modifier.testTag("confirmDelete")) {
                    Text(stringResource(R.string.action_delete), color = AppTheme.colors.red80)
                }
            },
            dismissButton = { TextButton(onClick = state::close) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionsSheet(t: NoteActionTarget, onDismiss: () -> Unit, onSub: (Sub) -> Unit, onAction: (NoteAction) -> Unit, onAddComment: () -> Unit) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val copied = stringResource(R.string.action_copied)
    val note = t.note
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.38f),
        dragHandle = null,
        shape = RectangleShape,
        tonalElevation = 0.dp,
    ) {
        // Floating card: 8 from the sides and bottom, 32 corners (design §4).
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = c.surface0,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp).navigationBarsPadding().testTag("noteActionsSheet"),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 5.dp).background(c.neutral20, RoundedCornerShape(50)))
                }
                Column(Modifier.padding(horizontal = 4.dp)) {
                    t.verseRef?.let {
                        Text("${NoteBadges.BIBLE_ICON} " + it.uppercase(), style = capsStyle(0.08f), color = c.scriptureInk, maxLines = 1)
                    }
                    Text(firstLine(t.displayContent), style = textStyle(14), color = c.neutral90, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (note.isTextEditable) {
                        BigButton(Icons.Outlined.Edit, stringResource(R.string.action_edit), null, Modifier.weight(1f)) { onSub(Sub.Edit) }
                    }
                    if (note.noteType != NoteType.Image) {
                        BigButton(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy), null, Modifier.weight(1f)) {
                            copyToClipboard(context, VerseDisplay.copyText(note, t.displayContent))
                            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                    val pinned = note.isPinned == true
                    BigButton(
                        if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        stringResource(if (pinned) R.string.tile_pinned else R.string.action_pin),
                        c.pinnedTile.takeIf { pinned }, Modifier.weight(1f).testTag("sheetPin"),
                    ) { onAction(NoteAction.TogglePinned) }
                    val starred = note.starLevel > 0
                    BigButton(
                        if (starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        stringResource(if (starred) R.string.tile_starred else R.string.action_star),
                        c.starredTile.takeIf { starred }, Modifier.weight(1f).testTag("sheetStar"),
                    ) { onSub(Sub.Star) }
                    if (note.noteType == NoteType.Prayer) {
                        val answered = note.isAnswered == true
                        BigButton(null, stringResource(R.string.answered), AppColors.ChipPair(c.answeredPrayerTile.background, c.answeredInk).takeIf { answered }, Modifier.weight(1f).testTag("sheetAnswered"), emoji = if (answered) "🙌" else "🙏") {
                            onAction(NoteAction.ToggleAnswered)
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(18.dp), color = c.pageBackground) {
                    Column {
                        ListRow(Icons.Outlined.Sell, stringResource(R.string.action_tags), tagsSummary(t.tagNames), Modifier.testTag("sheetTags")) { onSub(Sub.Tags) }
                        HorizontalDivider(Modifier.padding(start = 56.dp), color = c.neutral20)
                        ListRow(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.action_add_comment), null, Modifier.testTag("sheetComment"), onClick = onAddComment)
                    }
                }
                Surface(onClick = { onSub(Sub.Delete) }, shape = RoundedCornerShape(18.dp), color = c.red20, modifier = Modifier.fillMaxWidth().testTag("noteDelete")) {
                    Row(Modifier.height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Delete, null, tint = c.red80)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.delete_note), style = textStyle(16, weight = FontWeight.SemiBold), color = c.red80)
                    }
                }
            }
        }
    }
}

private fun firstLine(text: String) = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

/** "#a #b +N" (at most two names). */
private fun tagsSummary(names: List<String>): String? {
    if (names.isEmpty()) return null
    val shown = names.take(2).joinToString(" ") { "#$it" }
    return if (names.size > 2) "$shown +${names.size - 2}" else shown
}

@Composable
private fun BigButton(
    icon: ImageVector?,
    label: String,
    active: AppColors.ChipPair?,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val fg = active?.foreground ?: c.neutral100
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = active?.background ?: c.pageBackground, modifier = modifier.height(76.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp)) else Text(emoji.orEmpty(), style = textStyle(18))
            Spacer(Modifier.height(6.dp))
            Text(label, style = textStyle(13, weight = FontWeight.SemiBold), color = fg, maxLines = 1)
        }
    }
}

@Composable
private fun ListRow(icon: ImageVector, label: String, trailing: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).height(52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.neutral100)
        Spacer(Modifier.width(16.dp))
        Text(label, style = textStyle(16), color = c.neutral100, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = textStyle(14), color = c.neutral90, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("note", text))
}

/** iOS `InlineStarPicker`: 1–3 stars or none. */
@Composable
fun StarDialog(onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_star)) },
        text = {
            Column {
                (1..3).forEach { level ->
                    TextButton(onClick = { onSelect(level) }, modifier = Modifier.fillMaxWidth()) {
                        StarIcons(level, size = 20.dp)
                        Spacer(Modifier.weight(1f))
                        Text(pluralStringResource(R.plurals.stars_n, level, level))
                    }
                }
                TextButton(onClick = { onSelect(0) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_remove_star), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * iOS `CommentInputSheet` (list "Add comment"; details uses its composer). Save needs text, except
 * that "Answered today" may be saved alone (iOS then logs only the marker).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentSheet(preview: String, canMarkAnswered: Boolean, onDismiss: () -> Unit, onSave: (text: String, answered: Boolean) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var answered by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.comment_to), style = MaterialTheme.typography.titleMedium)
                    Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (canMarkAnswered) {
                    FilterChip(selected = answered, onClick = { answered = !answered }, label = { Text("🙌 " + stringResource(R.string.answered_today)) })
                }
            }
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("commentText"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onSave(text.trim(), answered) }, enabled = text.isNotBlank() || answered, modifier = Modifier.weight(1f).testTag("commentSave")) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

/** iOS `EditNoteSheet` (and `EditCommentSheet` with [title]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditNoteSheet(initial: String, onDismiss: () -> Unit, title: String = stringResource(R.string.edit_note_title), onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("editText"))
            Button(onClick = { onSave(text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("editSave")) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

/**
 * iOS `TagPickerPopup` for a note: every tag as a toggle chip (by name or by creation date), plus a
 * field that adds a new tag (an existing one, matched case-insensitively, is reused).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TagPickerSheet(
    allTags: List<TagEntity>,
    selectedIds: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var byName by rememberSaveable { mutableStateOf(true) }
    var sortMenu by remember { mutableStateOf(false) }
    var newTag by rememberSaveable { mutableStateOf("") }
    val tags = if (byName) allTags.sortedBy { it.name.lowercase() } else allTags.sortedBy { it.createdAt }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tags_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, null) }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.sort_by_name)) }, onClick = { byName = true; sortMenu = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.sort_by_created)) }, onClick = { byName = false; sortMenu = false })
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.bible_ref_close)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                tags.forEach { tag ->
                    val selected = tag.id in selectedIds
                    FilterChip(
                        selected = selected,
                        onClick = { if (selected) onRemove(tag.id) else onAdd(tag.name) },
                        label = { Text(tag.name) },
                        modifier = Modifier.testTag("tag_${tag.name}"),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.tag_new_placeholder)) },
                    modifier = Modifier.weight(1f).testTag("newTag"),
                )
                Button(onClick = { onAdd(newTag.trim()); newTag = "" }, enabled = newTag.isNotBlank()) {
                    Text(stringResource(R.string.tag_add_button))
                }
            }
        }
    }
}
