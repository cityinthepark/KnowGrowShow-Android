package com.stoneofhope.stoneofhope.ui.sermon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.bible.BibleBooks
import com.stoneofhope.stoneofhope.data.bible.BibleRefConfig
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import com.stoneofhope.stoneofhope.data.sermon.LinkFailure
import com.stoneofhope.stoneofhope.data.sermon.SermonCreateResult
import com.stoneofhope.stoneofhope.data.sermon.SermonRepository
import com.stoneofhope.stoneofhope.data.sermon.SeriesList
import com.stoneofhope.stoneofhope.data.sermon.SeriesSort
import com.stoneofhope.stoneofhope.data.sermon.SeriesWithSermons
import com.stoneofhope.stoneofhope.data.sermon.VideoCreateResult
import com.stoneofhope.stoneofhope.data.sermon.VideoRepository
import com.stoneofhope.stoneofhope.data.sermon.YouTubeLinkParser
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.ui.bible.BibleRefHelp
import com.stoneofhope.stoneofhope.ui.bible.BibleRefLanguages
import com.stoneofhope.stoneofhope.ui.bible.BibleReferenceField
import com.stoneofhope.stoneofhope.ui.theme.AppColors
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where a sheet sends the user after saving. */
sealed interface SermonDestination {
    data class Sermon(val id: String, val notice: SermonNotice = SermonNotice.None) : SermonDestination
    data class Video(val youtubeID: String, val notice: SermonNotice = SermonNotice.None) : SermonDestination
}

/** The duplicate notices iOS sets up and then dismisses with the sheet (IOS_ISSUES D3). */
enum class SermonNotice { None, SermonExists, VideoExists, LinkIsVideo, LinkIsSermon }

@HiltViewModel
class SermonSheetsViewModel @Inject constructor(
    db: AppDatabase,
    private val sermons: SermonRepository,
    private val videos: VideoRepository,
    bibleVersions: BibleVersionPreferences,
) : ViewModel() {

    val bibleRefLanguages: BibleRefLanguages = bibleVersions.selectedVersions().let { selected ->
        BibleRefLanguages(
            matching = BibleVersions.bookMatchingLanguages(selected),
            display = BibleVersions.primaryBookDisplayLanguage(selected),
            secondary = BibleVersions.secondaryBookDisplayLanguage(selected),
        )
    }

    val sort = MutableStateFlow(SeriesSort.BookOrder)
    val query = MutableStateFlow("")

    private val allSeries = combine(db.sermonSeriesDao().observeActive(), db.sermonDao().observeActive()) { s, sermons ->
        SeriesList.withSermons(s, sermons)
    }

    val pickerRows: StateFlow<List<SeriesWithSermons>> = combine(allSeries, sort, query) { rows, so, q -> SeriesList.pickerRows(rows, so, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasAnySeries: StateFlow<Boolean> = combine(allSeries, query) { rows, _ -> rows.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun createManual(title: String, ref: String, preacher: String, seriesId: String?, onDone: (SermonDestination) -> Unit) {
        viewModelScope.launch { onDone(SermonDestination.Sermon(sermons.createManual(title, ref, preacher, seriesId).id)) }
    }

    fun createSermonFromLink(url: String, ref: String?, onResult: (SermonDestination?, LinkFailure?) -> Unit) {
        viewModelScope.launch {
            when (val r = sermons.createFromYouTube(url, ref)) {
                is SermonCreateResult.Created -> onResult(SermonDestination.Sermon(r.sermonId), null)
                is SermonCreateResult.Existing -> onResult(SermonDestination.Sermon(r.sermonId, SermonNotice.SermonExists), null)
                is SermonCreateResult.ExistingVideo -> onResult(SermonDestination.Video(r.youtubeID, SermonNotice.LinkIsVideo), null)
                is SermonCreateResult.Failed -> onResult(null, r.reason)
            }
        }
    }

    fun createVideo(url: String, ref: String?, onResult: (SermonDestination?, LinkFailure?) -> Unit) {
        viewModelScope.launch {
            when (val r = videos.create(url, ref)) {
                is VideoCreateResult.Created -> onResult(SermonDestination.Video(r.youtubeID), null)
                is VideoCreateResult.Existing -> onResult(SermonDestination.Video(r.youtubeID, SermonNotice.VideoExists), null)
                is VideoCreateResult.ExistingSermon -> onResult(SermonDestination.Sermon(r.sermonId, SermonNotice.LinkIsSermon), null)
                is VideoCreateResult.Failed -> onResult(null, r.reason)
            }
        }
    }

    fun createSeries(title: String, onDone: (SermonSeriesEntity) -> Unit) {
        viewModelScope.launch { onDone(sermons.createSeries(title, bookOrd = null)) }
    }
}

@Composable
private fun LinkFailure.message(): String = stringResource(
    when (this) {
        LinkFailure.InvalidLink -> R.string.link_invalid
        LinkFailure.Shorts -> R.string.youtube_shorts_not_sermon
        LinkFailure.Unavailable -> R.string.link_unavailable
    }
)

/** The round icon and two-line title at the top of iOS's create sheets. */
@Composable
private fun SheetHeader(emoji: String, pair: AppColors.ChipPair, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = pair.background, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SheetButtons(enabled: Boolean, working: Boolean, onCancel: () -> Unit, onCreate: () -> Unit, tag: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
        Button(onClick = onCreate, enabled = enabled && !working, modifier = Modifier.weight(1f).testTag(tag)) {
            if (working) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.action_create))
        }
    }
}

/**
 * iOS `CreateSermonSheet`: fill in title, reference, preacher and series, or paste a YouTube link (the
 * manual fields then grey out). The series the user picks is kept (IOS_ISSUES D2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSermonSheet(
    onDismiss: () -> Unit,
    onCreated: (SermonDestination) -> Unit,
    viewModel: SermonSheetsViewModel = hiltViewModel(),
) {
    var title by rememberSaveable { mutableStateOf("") }
    var reference by rememberSaveable { mutableStateOf("") }
    var preacher by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    var series by remember { mutableStateOf<SermonSeriesEntity?>(null) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<LinkFailure?>(null) }
    val c = AppTheme.colors

    val usingLink = link.isNotBlank()
    val isShorts = YouTubeLinkParser.isShortsURL(link)
    val canCreate = if (usingLink) YouTubeLinkParser.videoID(link) != null && !isShorts else title.isNotBlank() || reference.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SheetHeader("📖", c.sermonChip, stringResource(R.string.sermon_new), stringResource(R.string.sermon_new_subtitle))
            if (usingLink) {
                Text(stringResource(R.string.youtube_using_link), style = MaterialTheme.typography.labelMedium, color = c.salmon100)
            }
            Column(Modifier.alpha(if (usingLink) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, enabled = !usingLink,
                    label = { Text(stringResource(R.string.sermon_title)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("sermonTitle"),
                )
                if (!usingLink) {
                    BibleReferenceField(
                        value = reference, onValueChange = { reference = it }, config = BibleRefConfig.Sermon,
                        languages = viewModel.bibleRefLanguages, help = BibleRefHelp.Sermon, showAbbreviations = true,
                    )
                }
                OutlinedTextField(
                    value = preacher, onValueChange = { preacher = it }, enabled = !usingLink,
                    label = { Text(stringResource(R.string.sermon_preacher)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("sermonPreacher"),
                )
                SeriesRow(series?.title, enabled = !usingLink, onClick = { pickerOpen = true })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text(stringResource(R.string.or_divider), style = MaterialTheme.typography.labelMedium, color = c.neutral90, modifier = Modifier.padding(horizontal = 12.dp))
                HorizontalDivider(Modifier.weight(1f))
            }
            Text(stringResource(R.string.youtube_import), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = link, onValueChange = { link = it; error = null },
                placeholder = { Text(stringResource(R.string.youtube_paste_link)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().testTag("sermonLink"),
            )
            if (isShorts) Text(stringResource(R.string.youtube_shorts_not_sermon), color = c.red80, style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it.message(), color = c.red80, style = MaterialTheme.typography.bodySmall) }
            SheetButtons(canCreate, working, onDismiss, tag = "sermonCreate", onCreate = {
                working = true
                if (usingLink) {
                    viewModel.createSermonFromLink(link, ref = null) { dest, failure ->
                        working = false
                        if (dest != null) onCreated(dest) else error = failure
                    }
                } else {
                    viewModel.createManual(title, reference, preacher, series?.id) { working = false; onCreated(it) }
                }
            })
        }
    }

    if (pickerOpen) {
        SeriesPickerSheet(
            selectedId = series?.id,
            onDismiss = { pickerOpen = false },
            onSelect = { series = it; pickerOpen = false },
            viewModel = viewModel,
        )
    }
}

@Composable
private fun SeriesRow(title: String?, enabled: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp), color = c.pageBackground,
        modifier = Modifier.fillMaxWidth().testTag("sermonSeriesRow"),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sermon_series), style = MaterialTheme.typography.labelLarge)
                Text(
                    title ?: stringResource(R.string.sermon_series_later),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (title == null) c.neutral90 else c.blue100,
                )
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = c.neutral50)
        }
    }
}

/**
 * iOS `CreateYouTubeVideoSheet`: paste a link and save it as a video or as a sermon (Shorts only as a
 * video), with an optional chapter. From the YouTube share button the link and title come prefilled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateVideoSheet(
    onDismiss: () -> Unit,
    onCreated: (SermonDestination) -> Unit,
    initialUrl: String = "",
    sharedTitle: String = "",
    viewModel: SermonSheetsViewModel = hiltViewModel(),
) {
    var link by rememberSaveable { mutableStateOf(initialUrl) }
    var asSermon by rememberSaveable { mutableStateOf(false) }
    var reference by rememberSaveable { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<LinkFailure?>(null) }
    val c = AppTheme.colors
    val isShorts = YouTubeLinkParser.isShortsURL(link)
    val videoID = YouTubeLinkParser.videoID(link)
    if (isShorts) asSermon = false
    val canCreate = videoID != null && !working

    // iOS `applyDetectedBibleReference(from:)`: a shared title fills the chapter field.
    LaunchedEffect(sharedTitle) {
        if (sharedTitle.isNotBlank() && reference.isEmpty()) {
            val anchor = com.stoneofhope.stoneofhope.data.sermon.YouTubeVideoTitleParser.anchor(sharedTitle, viewModel.bibleRefLanguages.matching)
            anchor.bookOrd?.let { reference = BibleBooks.name(it, viewModel.bibleRefLanguages.display) + " " + (anchor.chapterOrd ?: 1) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val subtitle = when {
                initialUrl.isNotEmpty() -> R.string.video_shared_subtitle
                isShorts -> R.string.video_shorts_subtitle
                else -> R.string.video_new_subtitle
            }
            SheetHeader("▶️", c.videoChip, stringResource(R.string.video_new), stringResource(subtitle))
            if (!isShorts) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !asSermon, onClick = { asSermon = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.testTag("saveAsVideo"),
                    ) { Text(stringResource(R.string.video_new)) }
                    SegmentedButton(
                        selected = asSermon, onClick = { asSermon = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.testTag("saveAsSermon"),
                    ) { Text(stringResource(R.string.video_add_as_sermon)) }
                }
            }
            OutlinedTextField(
                value = link, onValueChange = { link = it; error = null },
                placeholder = { Text(stringResource(R.string.youtube_paste_link)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().testTag("videoLink"),
            )
            if (sharedTitle.isNotBlank()) Text(sharedTitle, style = MaterialTheme.typography.bodyMedium)
            if (initialUrl.isNotEmpty() && videoID != null) {
                val image by rememberNetworkImage(YouTubeLinkParser.thumbnailUrl(videoID))
                image?.let {
                    Image(
                        it.asImageBitmap(), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)),
                    )
                }
            }
            HorizontalDivider()
            BibleReferenceField(
                value = reference, onValueChange = { reference = it }, config = BibleRefConfig.ChapterAnchored,
                languages = viewModel.bibleRefLanguages, help = BibleRefHelp.Chapter, showAbbreviations = true,
            )
            error?.let { Text(it.message(), color = c.red80, style = MaterialTheme.typography.bodySmall) }
            SheetButtons(canCreate, working, onDismiss, tag = "videoCreate", onCreate = {
                working = true
                val handle: (SermonDestination?, LinkFailure?) -> Unit = { dest, failure ->
                    working = false
                    if (dest != null) onCreated(dest) else error = failure
                }
                val ref = reference.trim().ifEmpty { null }
                if (asSermon) viewModel.createSermonFromLink(link, ref, handle) else viewModel.createVideo(link, ref, handle)
            })
        }
    }
}

/**
 * iOS `SeriesPickerView`: create a series, sort, search, pick one. Tapping the chosen series again
 * clears the choice; Cancel keeps it as it was (iOS clears it: IOS_ISSUES K13).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesPickerSheet(
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelect: (SermonSeriesEntity?) -> Unit,
    viewModel: SermonSheetsViewModel = hiltViewModel(),
) {
    val rows by viewModel.pickerRows.collectAsStateWithLifecycle()
    val hasAny by viewModel.hasAnySeries.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var newTitle by rememberSaveable { mutableStateOf("") }
    var sortOpen by remember { mutableStateOf(false) }
    val c = AppTheme.colors

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.series_select), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = c.pageBackground, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.series_create_new), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (!creating) TextButton(onClick = { creating = true }, modifier = Modifier.testTag("seriesCreateOpen")) { Text(stringResource(R.string.action_add)) }
                    }
                    if (creating) {
                        OutlinedTextField(
                            value = newTitle, onValueChange = { newTitle = it }, singleLine = true,
                            label = { Text(stringResource(R.string.series_new_title)) },
                            placeholder = { Text(stringResource(R.string.series_new_placeholder, LocalDate.now().year)) },
                            modifier = Modifier.fillMaxWidth().testTag("seriesNewTitle"),
                        )
                        Row {
                            TextButton(onClick = { creating = false; newTitle = "" }) { Text(stringResource(R.string.cancel)) }
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = { viewModel.createSeries(newTitle) { onSelect(it) } },
                                enabled = newTitle.isNotBlank(),
                                modifier = Modifier.testTag("seriesCreate"),
                            ) { Text(stringResource(R.string.action_add)) }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.series_all), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { sortOpen = true }) {
                        Text(stringResource(sort.label()))
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Outlined.Sort, null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        SeriesSort.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(stringResource(option.label())) }, onClick = { viewModel.sort.value = option; sortOpen = false })
                        }
                    }
                }
            }
            OutlinedTextField(
                value = query, onValueChange = { viewModel.query.value = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text(stringResource(R.string.series_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
            rows.forEach { row ->
                val selected = row.series.id == selectedId
                Surface(
                    onClick = { onSelect(if (selected) null else row.series) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) c.blue20 else c.surface0,
                    border = BorderStroke(1.dp, if (selected) c.blue80 else c.neutral20),
                    modifier = Modifier.fillMaxWidth().testTag("series_${row.series.id}"),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.series.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val count = pluralStringResource(R.plurals.day_sermons_subtitle, row.sermons.size, row.sermons.size)
                            Text(
                                listOfNotNull(row.bookOrd?.let { BibleBooks.name(it) }, count).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall, color = c.neutral90,
                            )
                        }
                        Icon(
                            if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                            tint = if (selected) c.blue80 else c.neutral50,
                        )
                    }
                }
            }
            if (rows.isEmpty()) {
                Text(
                    stringResource(if (hasAny) R.string.series_no_match else R.string.series_none_yet),
                    color = c.neutral90, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                )
            }
        }
    }
}

fun SeriesSort.label(): Int = when (this) {
    SeriesSort.BookOrder -> R.string.series_sort_book_order
    SeriesSort.BookName -> R.string.series_sort_book_name
    SeriesSort.StartedOn -> R.string.series_sort_started_on
    SeriesSort.Name -> R.string.series_sort_name
}

/** iOS `SermonMergePickerOverlay`: "Move N notes from … to:", only sermons without notes can receive. */
@Composable
fun MergeSermonDialog(
    sourceTitle: String,
    sourceNoteCount: Int,
    targets: List<com.stoneofhope.stoneofhope.data.sermon.SermonRules.MergeTarget>,
    onMerge: (targetId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.merge_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluralStringResource(R.plurals.merge_subtitle, sourceNoteCount, sourceNoteCount, sourceTitle), color = c.neutral90)
                HorizontalDivider()
                targets.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (t.canMerge) 1f else 0.5f)) {
                        Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (t.noteCount > 0) Text("${t.noteCount}", color = c.neutral90, modifier = Modifier.padding(horizontal = 8.dp))
                        TextButton(onClick = { onMerge(t.sermon.id) }, enabled = t.canMerge, modifier = Modifier.testTag("mergeInto_${t.sermon.id}")) {
                            Text(stringResource(R.string.merge))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
