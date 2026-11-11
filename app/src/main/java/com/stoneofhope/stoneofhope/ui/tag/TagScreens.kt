package com.stoneofhope.stoneofhope.ui.tag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.notes.NoteSwipeSequence
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.sermon.SermonRules
import com.stoneofhope.stoneofhope.data.tag.PresetTag
import com.stoneofhope.stoneofhope.data.tag.TagCounts
import com.stoneofhope.stoneofhope.data.tag.TagRenameResult
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagPage(title: String, onBack: () -> Unit, tag: String, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        modifier = Modifier.safeDrawingPadding().testTag(tag),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        content = content,
    )
}

// MARK: - My Tags (iOS MyTagsView)

/** Every tag with what it labels; add, rename, merge into another tag, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTagsScreen(onBack: () -> Unit, onOpenTag: (String) -> Unit, viewModel: MyTagsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var newName by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingName by rememberSaveable { mutableStateOf("") }
    var editError by remember { mutableStateOf<Int?>(null) }
    var deleting by remember { mutableStateOf<TagEntity?>(null) }
    var mergeSource by remember { mutableStateOf<TagEntity?>(null) }
    var mergeTarget by remember { mutableStateOf<TagEntity?>(null) }
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors

    TagPage(stringResource(R.string.tags_my), onBack, "myTags") { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "new") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.tag_new)) }, modifier = Modifier.weight(1f).testTag("myTagsNew"),
                    )
                    Button(onClick = { viewModel.create(newName); newName = "" }, enabled = newName.isNotBlank(), modifier = Modifier.testTag("myTagsAdd")) {
                        Text(stringResource(R.string.tag_add_button))
                    }
                }
            }
            if (state.loaded && state.tags.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.tags_empty), color = c.neutral50, modifier = Modifier.fillMaxWidth().padding(24.dp))
                }
            }
            items(state.tags, key = { it.id }) { tag ->
                if (editingId == tag.id) {
                    Surface(shape = RoundedCornerShape(14.dp), color = c.surface0, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = editingName, onValueChange = { editingName = it; editError = null }, singleLine = true,
                                placeholder = { Text(stringResource(R.string.tag_edit_hint)) }, modifier = Modifier.fillMaxWidth().testTag("tagEditField"),
                            )
                            editError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("tagEditError")) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    scope.launch {
                                        when (viewModel.rename(tag.id, editingName)) {
                                            TagRenameResult.Renamed -> editingId = null
                                            TagRenameResult.Empty -> Unit
                                            TagRenameResult.Duplicate -> editError = R.string.tag_duplicate
                                        }
                                    }
                                }, enabled = editingName.isNotBlank(), modifier = Modifier.testTag("tagEditSave")) { Text(stringResource(R.string.action_save)) }
                                TextButton(onClick = { editingId = null; editError = null }) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                } else {
                    TagRow(
                        tag, state.counts[tag.id] ?: TagCounts(), canMerge = state.tags.size > 1,
                        onOpen = { onOpenTag(tag.id) },
                        onRename = { editingId = tag.id; editingName = tag.name; editError = null },
                        onMerge = { mergeSource = tag },
                        onDelete = { deleting = tag },
                    )
                }
            }
        }
    }

    deleting?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.tag_delete_title)) },
            text = { Text(stringResource(R.string.tag_delete_message, tag.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(tag.id); deleting = null }, modifier = Modifier.testTag("tagConfirmDelete")) {
                    Text(stringResource(R.string.tag_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    mergeSource?.let { source ->
        if (mergeTarget == null) {
            ModalBottomSheet(onDismissRequest = { mergeSource = null }) {
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tag_merge_pick, source.name), style = textStyle(15, weight = FontWeight.SemiBold))
                    state.tags.filter { it.id != source.id }.forEach { target ->
                        TagRowBody(target, state.counts[target.id] ?: TagCounts(), Modifier.testTag("mergeTarget_${target.name}"), onClick = { mergeTarget = target })
                    }
                }
            }
        } else {
            val target = mergeTarget!!
            AlertDialog(
                onDismissRequest = { mergeSource = null; mergeTarget = null },
                title = { Text(stringResource(R.string.tag_merge_title)) },
                text = { Text(stringResource(R.string.tag_merge_confirm, source.name, target.name, (state.counts[source.id] ?: TagCounts()).total)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.merge(source.id, target.id); mergeSource = null; mergeTarget = null }, modifier = Modifier.testTag("tagConfirmMerge")) {
                        Text(stringResource(R.string.tag_merge_do), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { mergeSource = null; mergeTarget = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

@Composable
private fun TagRow(tag: TagEntity, counts: TagCounts, canMerge: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onMerge: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    TagRowBody(tag, counts, Modifier.testTag("tagRow_${tag.name}"), onClick = onOpen) {
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("tagMenu_${tag.name}")) { Icon(Icons.Filled.MoreVert, stringResource(R.string.flash_more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.tag_rename)) }, onClick = { menu = false; onRename() }, modifier = Modifier.testTag("tagRename"))
                if (canMerge) DropdownMenuItem(text = { Text(stringResource(R.string.tag_merge_action)) }, onClick = { menu = false; onMerge() }, modifier = Modifier.testTag("tagMerge"))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tag_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() }, modifier = Modifier.testTag("tagDelete"),
                )
            }
        }
    }
}

/** Name with its preset mark, and "3 notes · 1 comment". */
@Composable
private fun TagRowBody(tag: TagEntity, counts: TagCounts, modifier: Modifier = Modifier, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val c = AppTheme.colors
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = c.surface0, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(PresetTag.label(tag.name), style = textStyle(17, weight = FontWeight.SemiBold), color = c.neutral100)
                val parts = buildList {
                    if (counts.notes > 0) add(pluralStringResource(R.plurals.tag_count_notes, counts.notes, counts.notes))
                    if (counts.comments > 0) add(pluralStringResource(R.plurals.tag_count_comments, counts.comments, counts.comments))
                    if (counts.sermons > 0) add(pluralStringResource(R.plurals.tag_count_sermons, counts.sermons, counts.sermons))
                    if (counts.videos > 0) add(pluralStringResource(R.plurals.tag_count_videos, counts.videos, counts.videos))
                }
                if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = textStyle(14), color = c.neutral50)
            }
            trailing()
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.neutral50)
        }
    }
}

// MARK: - Tag page (iOS TagNotesView)

/** What carries one tag: sermons, videos, then notes (a note also shows when one of its comments has the tag). */
@Composable
fun TagNotesScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenSermon: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    viewModel: TagNotesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT) }
    TagPage(state.name?.let(PresetTag::label).orEmpty(), onBack, "tagNotes") { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.loaded && state.isEmpty) {
                item { Text(stringResource(R.string.tag_nothing), color = c.neutral50, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
            }
            if (state.sermons.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.tag_section_sermons)) }
                items(state.sermons, key = { "s" + it.id }) { s -> ItemCard(onClick = { onOpenSermon(s.id) }) { Text(SermonRules.displayTitle(s), style = textStyle(16), color = c.neutral100, maxLines = 2) } }
            }
            if (state.videos.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.tag_section_videos)) }
                items(state.videos, key = { "v" + it.youtubeID }) { v -> ItemCard(onClick = { onOpenVideo(v.youtubeID) }) { Text(v.title, style = textStyle(16), color = c.neutral100, maxLines = 2) } }
            }
            if (state.notes.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.tag_section_notes)) }
                items(state.notes, key = { "n" + it.note.id }) { row ->
                    ItemCard(
                        onClick = {
                            NoteSwipeSequence.offer(state.notes.map { it.note.id })
                            onOpenNote(row.note.id)
                        },
                        modifier = Modifier.testTag("tagNoteRow"),
                    ) {
                        Text(row.preview.ifEmpty { stringResource(R.string.tag_empty_note) }, style = textStyle(16), color = c.neutral100, maxLines = 3)
                        Text(formatter.format(row.note.createdAt.atZone(ZoneId.systemDefault())), style = textStyle(12), color = c.neutral50)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = textStyle(13, weight = FontWeight.SemiBold), color = AppTheme.colors.neutral50, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ItemCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = AppTheme.colors.surface0, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}
