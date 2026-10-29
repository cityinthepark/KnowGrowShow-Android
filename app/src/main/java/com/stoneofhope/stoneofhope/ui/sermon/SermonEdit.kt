package com.stoneofhope.stoneofhope.ui.sermon

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.bible.BibleRefConfig
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.sermon.SermonEdit
import com.stoneofhope.stoneofhope.data.sermon.SermonRepository
import com.stoneofhope.stoneofhope.data.sermon.SermonRules
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.ui.bible.BibleRefHelp
import com.stoneofhope.stoneofhope.ui.bible.BibleRefLanguages
import com.stoneofhope.stoneofhope.ui.bible.BibleReferenceField
import com.stoneofhope.stoneofhope.ui.notes.TagPickerSheet
import com.stoneofhope.stoneofhope.ui.notes.TagPill
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SermonEditState(
    val sermon: SermonEntity? = null,
    val series: SermonSeriesEntity? = null,
    val allTags: List<TagEntity> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class SermonEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    db: AppDatabase,
    private val sermons: SermonRepository,
    bibleVersions: BibleVersionPreferences,
    private val clock: Clock,
) : ViewModel() {
    val sermonId: String = checkNotNull(savedState[SermonViewModel.ARG_ID])

    val bibleRefLanguages = bibleVersions.selectedVersions().let { selected ->
        BibleRefLanguages(
            matching = BibleVersions.bookMatchingLanguages(selected),
            display = BibleVersions.primaryBookDisplayLanguage(selected),
            secondary = BibleVersions.secondaryBookDisplayLanguage(selected),
        )
    }

    val state: StateFlow<SermonEditState> = combine(
        db.sermonDao().observeById(sermonId), db.sermonSeriesDao().observeActive(), db.tagDao().observeActiveByCreation(),
    ) { s, series, tags ->
        SermonEditState(s?.takeIf { it.deletedAt == null }, series.firstOrNull { it.id == s?.seriesId }, tags, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SermonEditState())

    fun today(): LocalDate = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()

    fun save(edit: SermonEdit, onDone: () -> Unit) {
        viewModelScope.launch { sermons.update(sermonId, edit); onDone() }
    }

    /** Series and tags apply at once, as on iOS. */
    fun setSeries(seriesId: String?) { viewModelScope.launch { sermons.setSeries(sermonId, seriesId) } }

    fun detectSeries(onNone: () -> Unit) {
        viewModelScope.launch { if (!sermons.detectSeries(sermonId)) onNone() }
    }

    fun addTag(name: String) { viewModelScope.launch { sermons.addTag(sermonId, name) } }

    fun removeTag(tagId: String) { viewModelScope.launch { sermons.removeTag(sermonId, tagId) } }

    fun delete(onDone: () -> Unit) { viewModelScope.launch { sermons.delete(sermonId); onDone() } }
}

/**
 * iOS `SermonEditView`, plus a preached-on date (§4 question 4: iOS has none, IOS_ISSUES K10), the
 * sermon reference field with its checks (K13), "Remove" from a series (K13), and Delete (iOS only
 * deletes from a series row in the Series tab; ungrouped sermons can't be deleted there: K19).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SermonEditScreen(onBack: () -> Unit, onDeleted: () -> Unit, viewModel: SermonEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.sermon
    if (s == null) {
        MissingPage(stringResource(R.string.sermon_not_found), loaded = state.loaded, onBack = onBack)
        return
    }
    val c = AppTheme.colors
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var initialized by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableLongStateOf(0L) }
    var reference by rememberSaveable { mutableStateOf("") }
    var preacher by rememberSaveable { mutableStateOf("") }
    var video by rememberSaveable { mutableStateOf("") }
    var audio by rememberSaveable { mutableStateOf("") }
    var pdf by rememberSaveable { mutableStateOf("") }
    var intro by rememberSaveable { mutableStateOf("") }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var dateOpen by rememberSaveable { mutableStateOf(false) }
    var tagsOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(s.id) {
        if (initialized) return@LaunchedEffect
        initialized = true
        title = s.title
        day = (SermonRules.day(s.preachedOn) ?: viewModel.today()).toEpochDay()
        reference = s.chapterRange.orEmpty()
        preacher = s.preacher.orEmpty()
        video = s.videoUrl.orEmpty()
        audio = s.audioUrl.orEmpty()
        pdf = s.pdfUrl.orEmpty()
        intro = s.intro.orEmpty()
    }
    val noSeriesMessage = stringResource(R.string.series_detect_none)

    Column(Modifier.fillMaxSize().background(c.pageBackground).safeDrawingPadding().imePadding()) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.sermon_edit_title), fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            actions = {
                TextButton(
                    onClick = {
                        viewModel.save(
                            SermonEdit(title, LocalDate.ofEpochDay(day), reference, preacher, video, audio, pdf, intro), onBack,
                        )
                    },
                    modifier = Modifier.testTag("sermonEditSave"),
                ) { Text(stringResource(R.string.action_save), fontWeight = FontWeight.SemiBold) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = c.pageBackground),
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EditCard {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.sermon_title)) },
                    maxLines = 4, modifier = Modifier.fillMaxWidth().testTag("editTitle"),
                )
                Surface(onClick = { dateOpen = true }, shape = RoundedCornerShape(14.dp), color = c.pageBackground, modifier = Modifier.fillMaxWidth().testTag("editDate")) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.sermon_date), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(LocalDate.ofEpochDay(day)), color = c.blue100)
                        Spacer(Modifier.padding(4.dp))
                        Icon(Icons.Outlined.CalendarMonth, null, tint = c.neutral90)
                    }
                }
                BibleReferenceField(
                    value = reference, onValueChange = { reference = it }, config = BibleRefConfig.Sermon,
                    languages = viewModel.bibleRefLanguages, help = BibleRefHelp.Sermon, showAbbreviations = false,
                )
                OutlinedTextField(
                    value = preacher, onValueChange = { preacher = it }, label = { Text(stringResource(R.string.sermon_preacher)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            EditCard(stringResource(R.string.sermon_series_section)) {
                val series = state.series
                if (series != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(series.title, style = textStyle(16, weight = FontWeight.SemiBold), color = c.neutral100, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.setSeries(null) }, modifier = Modifier.testTag("seriesRemove")) { Text(stringResource(R.string.series_remove), color = c.red80) }
                        TextButton(onClick = { pickerOpen = true }) { Text(stringResource(R.string.series_change)) }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = { viewModel.detectSeries { scope.launch { snackbar.showSnackbar(noSeriesMessage) } } },
                            modifier = Modifier.weight(1f).testTag("seriesDetect"),
                        ) { Text(stringResource(R.string.series_detect)) }
                        FilledTonalButton(onClick = { pickerOpen = true }, modifier = Modifier.weight(1f).testTag("seriesAdd")) { Text(stringResource(R.string.series_add_to)) }
                    }
                }
            }
            EditCard(stringResource(R.string.action_tags)) {
                val names = state.allTags.filter { it.id in s.tagIds }.map { it.name }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.forEach { TagPill(it) }
                }
                OutlinedButton(onClick = { tagsOpen = true }, modifier = Modifier.testTag("editTags")) {
                    Text(stringResource(if (names.isEmpty()) R.string.tags_add else R.string.action_edit))
                }
            }
            EditCard(stringResource(R.string.media_title)) {
                MediaField(stringResource(R.string.media_video_url), video, { video = it }, "editVideo")
                MediaField(stringResource(R.string.media_audio_url), audio, { audio = it }, "editAudio")
                MediaField(stringResource(R.string.media_pdf_url), pdf, { pdf = it }, "editPdf")
            }
            EditCard(stringResource(R.string.intro_title)) {
                OutlinedTextField(value = intro, onValueChange = { intro = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp))
            }
            Button(
                onClick = { confirmDelete = true },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.red80),
                modifier = Modifier.fillMaxWidth().testTag("sermonDelete"),
            ) { Text(stringResource(R.string.sermon_delete)) }
            Spacer(Modifier.padding(8.dp))
        }
        SnackbarHost(snackbar)
    }

    if (dateOpen) {
        val today = viewModel.today()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { dateOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    dateOpen = false
                }) { Text(stringResource(R.string.bible_ref_done)) }
            },
            dismissButton = { TextButton(onClick = { dateOpen = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(pickerState) }
    }
    if (pickerOpen) {
        SeriesPickerSheet(
            selectedId = s.seriesId,
            onDismiss = { pickerOpen = false },
            onSelect = { viewModel.setSeries(it?.id); pickerOpen = false },
        )
    }
    if (tagsOpen) {
        TagPickerSheet(state.allTags, s.tagIds.toSet(), onAdd = viewModel::addTag, onRemove = viewModel::removeTag, onDismiss = { tagsOpen = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.sermon_delete_title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onDeleted) }, modifier = Modifier.testTag("confirmDeleteSermon")) {
                    Text(stringResource(R.string.sermon_delete), color = c.red80)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun EditCard(title: String? = null, content: @Composable () -> Unit) {
    val c = AppTheme.colors
    Surface(shape = RoundedCornerShape(24.dp), color = c.surface0, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            title?.let { Text(it, style = textStyle(16, weight = FontWeight.SemiBold), color = c.neutral100) }
            content()
        }
    }
}

/** A link field with iOS's copy button. */
@Composable
private fun MediaField(label: String, value: String, onChange: (String) -> Unit, tag: String) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f).testTag(tag),
        )
        IconButton(onClick = { copy(context, value.trim()) }, enabled = value.isNotBlank()) {
            Icon(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy))
        }
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("link", text))
    Toast.makeText(context, R.string.action_copied, Toast.LENGTH_SHORT).show()
}
