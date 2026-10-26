package com.stoneofhope.stoneofhope.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.ui.theme.AppTheme

/**
 * iOS `PrayerCreateView`: header with a tag menu, prayer text, Save (disabled while blank).
 * The tag menu only offers existing tags, like iOS. Bible reference field: Today plan T2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerSheet(tagNames: List<String>, onDismiss: () -> Unit, onSave: (content: String, tagName: String?) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
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
                onSave = { onSave(text, tag) },
                saveTag = "prayerSave",
            )
        }
    }
}

/** iOS `NoteInputSheetView` without the reference field (Today plan T2): note text and Save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(onDismiss: () -> Unit, onSave: (content: String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.note_placeholder)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("noteText"),
            )
            SheetButtons(
                saveLabel = stringResource(R.string.note_save),
                saveEnabled = text.isNotBlank(),
                onCancel = onDismiss,
                onSave = { onSave(text) },
                saveTag = "noteSave",
            )
        }
    }
}

@Composable
private fun SheetButtons(saveLabel: String, saveEnabled: Boolean, onCancel: () -> Unit, onSave: () -> Unit, saveTag: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
        Button(onClick = onSave, enabled = saveEnabled, modifier = Modifier.weight(1f).testTag(saveTag)) { Text(saveLabel) }
    }
}
