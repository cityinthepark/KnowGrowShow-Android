package com.stoneofhope.stoneofhope.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.bible.BibleRefConfig
import com.stoneofhope.stoneofhope.ui.bible.BibleRefHelp
import com.stoneofhope.stoneofhope.ui.bible.BibleRefLanguages
import com.stoneofhope.stoneofhope.ui.bible.BibleReferenceField
import com.stoneofhope.stoneofhope.ui.bible.BookPickerSheet
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import kotlinx.coroutines.delay

/**
 * iOS `PrayerCreateView`: header with a tag menu, Bible reference (verse level; anything less is
 * dropped on save, never blocks it), prayer text, Save (disabled while the text is blank).
 * The tag menu only offers existing tags, like iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerSheet(
    tagNames: List<String>,
    bibleRefLanguages: BibleRefLanguages,
    onDismiss: () -> Unit,
    onSave: (content: String, tagName: String?, bibleRefText: String?) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var reference by rememberSaveable { mutableStateOf("") }
    // Null = no tag. Kept apart from the "+ Tag" label (iOS compares against the localized text).
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val colors = AppTheme.colors

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = colors.prayerChip.background, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("🌿", fontSize = 20.sp) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.prayer_new), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.prayer_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    TextButton(onClick = { menuOpen = true }, enabled = tagNames.isNotEmpty() || tag != null) {
                        Text(tag?.let { "#$it" } ?: stringResource(R.string.tag_add))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.tag_add)) }, onClick = { tag = null; menuOpen = false })
                        tagNames.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { tag = name; menuOpen = false })
                        }
                    }
                }
            }
            BibleReferenceField(
                value = reference,
                onValueChange = { reference = it },
                config = BibleRefConfig.FlashCard,
                languages = bibleRefLanguages,
                help = BibleRefHelp.Prayer,
                showAbbreviations = false,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.prayer_placeholder)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).testTag("prayerText"),
            )
            SheetButtons(
                saveLabel = stringResource(R.string.prayer_save),
                saveEnabled = text.isNotBlank(),
                onCancel = onDismiss,
                onSave = { onSave(text, tag, reference.trim().ifEmpty { null }) },
                saveTag = "prayerSave",
            )
        }
    }
}

/**
 * iOS `NoteInputSheetView`: Bible reference, note text, Save. Save needs either text or a verse-level
 * reference (a verse with no comment is a valid note). On Today ([config] `.flashCard`) a reference
 * that stops at a chapter is dropped on save; the chapter page passes `.chapterAnchored` and its own
 * chapter as [initialReference] (iOS `defaultBookChapter`). The body may itself start with "John 3:16 …".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(
    bibleRefLanguages: BibleRefLanguages,
    onDismiss: () -> Unit,
    onSave: (content: String, bibleRefText: String?) -> Unit,
    initialReference: String = "",
    config: BibleRefConfig = BibleRefConfig.FlashCard,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var reference by rememberSaveable { mutableStateOf(initialReference) }
    var referenceHasVerse by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BibleReferenceField(
                value = reference,
                onValueChange = { reference = it },
                config = config,
                languages = bibleRefLanguages,
                help = BibleRefHelp.Note,
                showAbbreviations = true,
                onValidationChange = { referenceHasVerse = it.resolved?.verseStart != null },
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.note_placeholder)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("noteText"),
            )
            SheetButtons(
                saveLabel = stringResource(R.string.note_save),
                saveEnabled = text.isNotBlank() || referenceHasVerse,
                onCancel = onDismiss,
                onSave = { onSave(text.trim(), reference.trim().ifEmpty { null }) },
                saveTag = "noteSave",
            )
        }
    }
}

/**
 * iOS `NoteInputSheetView(anchorLabelOnly:)` on the sermon / video pages: no reference field. The book
 * button writes the book's name at the start of the text ("John " + text), so "John 3:16 …" and "v3 …"
 * (the sermon's chapter) are typed in one box. The "Chapter Note" switch (off by default) decides
 * whether text with no verse is also a note on the sermon's chapter. Save needs text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerNoteSheet(
    bibleRefLanguages: BibleRefLanguages,
    onDismiss: () -> Unit,
    onSave: (content: String, chapterNote: Boolean) -> Unit,
) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var chapterNote by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var focusAfterPick by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusAfterPick) {
        if (focusAfterPick > 0) {
            delay(250) // let the book list finish closing before raising the keyboard
            focusRequester.requestFocus()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = { pickerOpen = true },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.height(40.dp).testTag("bookPicker"),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✝️")
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.bible_ref_book), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.note_sheet_chapter_note), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(10.dp))
                Switch(checked = chapterNote, onCheckedChange = { chapterNote = it }, modifier = Modifier.testTag("chapterNoteSwitch"))
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.note_placeholder)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).focusRequester(focusRequester).testTag("noteText"),
            )
            SheetButtons(
                saveLabel = stringResource(R.string.note_save),
                saveEnabled = text.text.isNotBlank(),
                onCancel = onDismiss,
                onSave = { onSave(text.text.trim(), chapterNote) },
                saveTag = "noteSave",
            )
        }
    }
    if (pickerOpen) {
        BookPickerSheet(
            selectedOrd = null,
            languages = bibleRefLanguages,
            showAbbreviations = true,
            onDismiss = { pickerOpen = false },
            onSelect = { book ->
                val name = book.fullName(bibleRefLanguages.display)
                // Cursor right after the name, ready for "3:16".
                text = TextFieldValue("$name ${text.text}", selection = TextRange(name.length + 1))
                pickerOpen = false
                focusAfterPick++
            },
        )
    }
}

@Composable
private fun SheetButtons(saveLabel: String, saveEnabled: Boolean, onCancel: () -> Unit, onSave: () -> Unit, saveTag: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
        Button(onClick = onSave, enabled = saveEnabled, modifier = Modifier.weight(1f).testTag(saveTag)) { Text(saveLabel) }
    }
}
