package com.stoneofhope.stoneofhope.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.local.entity.SyncConflictEntity
import com.stoneofhope.stoneofhope.data.model.ConflictResolution
import com.stoneofhope.stoneofhope.data.model.SyncConflictType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateTime: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())

private fun Instant.display(): String = dateTime.format(this)

@Composable
private fun SyncConflictType.title(): String = stringResource(
    when (this) {
        SyncConflictType.Note -> R.string.sync_conflict_note
        SyncConflictType.NoteComment -> R.string.sync_conflict_comment
        SyncConflictType.IndexedText -> R.string.sync_conflict_highlight
    }
)

/** iOS `SyncConflictListView`: unresolved conflicts; tapping one opens the resolution sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConflictsScreen(onBack: () -> Unit, viewModel: SyncConflictsViewModel = hiltViewModel()) {
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val resolving by viewModel.resolving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val selected = conflicts?.firstOrNull { it.id == selectedId }

    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("syncConflicts")) {
        TopAppBar(
            title = { Text(stringResource(R.string.sync_conflicts)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            windowInsets = WindowInsets(0),
        )
        val list = conflicts
        when {
            list == null -> Unit
            list.isEmpty() -> Text(
                stringResource(R.string.sync_conflicts_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp).testTag("syncConflictsEmpty"),
            )
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.sync_conflicts_review),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp),
                )
                list.forEach { conflict ->
                    ListItem(
                        headlineContent = { Text(conflict.recordType.title(), fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Column {
                                Text(conflict.localPreview, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(conflict.detectedAt.display(), style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        modifier = Modifier.testTag("conflict_${conflict.id}").clickable { viewModel.select(conflict.id) },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }

    if (selected != null) {
        ModalBottomSheet(
            onDismissRequest = { if (!resolving) viewModel.select(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ConflictResolutionSheet(selected, resolving, error?.let { stringResource(it) }, viewModel::resolve) { viewModel.select(null) }
        }
    }
}

/** iOS `ConflictResolutionSheet`: both versions and the three resolution modes. */
@Composable
private fun ConflictResolutionSheet(
    conflict: SyncConflictEntity,
    resolving: Boolean,
    error: String?,
    onResolve: (ConflictResolution) -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().testTag("conflictSheet")) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.sync_conflict_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.sync_conflict_explainer), style = MaterialTheme.typography.bodyMedium)
            Version(stringResource(R.string.sync_conflict_this_device), conflict.localPreview, conflict.localUpdatedAt, "conflictLocal")
            Version(stringResource(R.string.sync_conflict_other_device), conflict.remotePreview, conflict.remoteUpdatedAt, "conflictRemote")
            ResolveButton(R.string.sync_conflict_keep_local, "keepLocal", !resolving) { onResolve(ConflictResolution.KeepLocal) }
            ResolveButton(R.string.sync_conflict_keep_remote, "keepRemote", !resolving) { onResolve(ConflictResolution.KeepRemote) }
            ResolveButton(R.string.sync_conflict_keep_both, "createCopy", !resolving) { onResolve(ConflictResolution.CreateCopy) }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("conflictError"))
            }
            OutlinedButton(onClick = onClose, enabled = !resolving, modifier = Modifier.fillMaxWidth().testTag("conflictClose")) {
                Text(stringResource(R.string.sync_conflict_close))
            }
        }
        if (resolving) CircularProgressIndicator(Modifier.align(Alignment.Center))
    }
}

@Composable
private fun Version(title: String, preview: String, at: Instant, tag: String) {
    Column(Modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(preview, style = MaterialTheme.typography.bodyMedium)
        Text(at.display(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResolveButton(label: Int, tag: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(stringResource(label)) }
}
