package com.stoneofhope.stoneofhope.ui.sermon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.bible.BibleBooks
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import com.stoneofhope.stoneofhope.data.sermon.SermonRepository
import com.stoneofhope.stoneofhope.data.sermon.SermonRules
import com.stoneofhope.stoneofhope.data.sermon.SeriesList
import com.stoneofhope.stoneofhope.data.sermon.SeriesSort
import com.stoneofhope.stoneofhope.data.sermon.SeriesWithSermons
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Series tab shows (iOS `SeriesViewModel`). */
data class SeriesTabState(
    val ungrouped: List<SermonEntity> = emptyList(),
    val topical: List<SeriesWithSermons> = emptyList(),
    val bookGroups: List<SeriesList.BookGroup> = emptyList(),
    val byLastPreached: List<SeriesWithSermons> = emptyList(),
    val hasAnySeries: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class SeriesViewModel @Inject constructor(
    db: AppDatabase,
    private val sermons: SermonRepository,
) : ViewModel() {
    /** Not persisted, as on iOS. */
    val sort = MutableStateFlow(SeriesSort.BookOrder)
    val query = MutableStateFlow("")

    /** iOS `BookSeriesDisplayMode`: grouped by book, or one list by last preached. */
    val byBooks = MutableStateFlow(true)

    val state: StateFlow<SeriesTabState> = combine(
        db.sermonSeriesDao().observeActive(), db.sermonDao().observeActive(), sort, query,
    ) { series, sermonRows, so, q ->
        val rows = SeriesList.withSermons(series, sermonRows)
        val books = SeriesList.filter(SeriesList.bookTracked(rows), q)
        SeriesTabState(
            ungrouped = SeriesList.ungrouped(sermonRows, series.map { it.id }.toSet(), q),
            topical = SeriesList.filter(SeriesList.sort(SeriesList.topical(rows), so), q),
            bookGroups = SeriesList.groupByBook(books),
            byLastPreached = SeriesList.sortByStartedOnDescending(books),
            hasAnySeries = rows.isNotEmpty(),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesTabState())

    fun createSeries(title: String, bookOrd: Int?) {
        viewModelScope.launch { sermons.createSeries(title, bookOrd) }
    }

    fun deleteSermon(sermonId: String) {
        viewModelScope.launch { sermons.delete(sermonId) }
    }
}

/**
 * iOS `SeriesTabView`: title, sort menu and "＋"; search; "Ungrouped Sermons"; "Topical Series" (series
 * without a book, K17); "By Books" grouped by book or by last preached. Each series expands to its
 * sermons, oldest first; a long press deletes one of the user's own sermons (also ungrouped ones: K19).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(nav: SermonNavigation, onOpenSeries: (String) -> Unit, viewModel: SeriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val byBooks by viewModel.byBooks.collectAsStateWithLifecycle()
    var sortOpen by remember { mutableStateOf(false) }
    var createOpen by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var ungroupedOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SermonEntity?>(null) }
    val c = AppTheme.colors

    Column(Modifier.fillMaxSize().background(c.pageBackground)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sermon_series_section), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box {
                TextButton(onClick = { sortOpen = true }, modifier = Modifier.testTag("seriesSort")) {
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
            IconButton(onClick = { createOpen = true }, modifier = Modifier.testTag("seriesCreateOpen")) { Icon(Icons.Filled.Add, stringResource(R.string.series_create)) }
        }
        OutlinedTextField(
            value = query, onValueChange = { viewModel.query.value = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { viewModel.query.value = "" }) { Icon(Icons.Outlined.Close, null) } },
            placeholder = { Text(stringResource(R.string.series_search)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("seriesSearch"),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).testTag("seriesList"),
        ) {
            if (state.ungrouped.isNotEmpty()) {
                item(key = "ungrouped") {
                    SeriesCard(
                        title = stringResource(R.string.series_ungrouped), sermons = state.ungrouped, expanded = ungroupedOpen,
                        onToggle = { ungroupedOpen = !ungroupedOpen }, onDetail = null, isUserData = true,
                        nav = nav, onDelete = { pendingDelete = it }, tag = "ungrouped",
                    )
                }
            }
            if (state.topical.isNotEmpty()) {
                item(key = "topicalHeader") { SectionHeader(stringResource(R.string.series_topical)) }
                items(state.topical, key = { "t_" + it.series.id }) { row ->
                    SeriesRowCard(row, expanded, { expanded = if (expanded == row.series.id) null else row.series.id }, onOpenSeries, nav) { pendingDelete = it }
                }
            }
            if (state.bookGroups.isNotEmpty()) {
                item(key = "booksHeader") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(stringResource(R.string.series_by_books), Modifier.weight(1f))
                        TextButton(onClick = { viewModel.byBooks.value = !byBooks }, modifier = Modifier.testTag("seriesBooksMode")) {
                            Text(stringResource(if (byBooks) R.string.series_by_last_preached else R.string.series_by_books))
                        }
                    }
                }
                if (byBooks) {
                    state.bookGroups.forEach { group ->
                        item(key = "book_${group.bookOrd}") {
                            Text(BibleBooks.name(group.bookOrd), style = textStyle(15, weight = FontWeight.SemiBold), color = c.neutral90, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                        }
                        items(group.series, key = { "b_" + it.series.id }) { row ->
                            SeriesRowCard(row, expanded, { expanded = if (expanded == row.series.id) null else row.series.id }, onOpenSeries, nav) { pendingDelete = it }
                        }
                    }
                } else {
                    items(state.byLastPreached, key = { "l_" + it.series.id }) { row ->
                        SeriesRowCard(row, expanded, { expanded = if (expanded == row.series.id) null else row.series.id }, onOpenSeries, nav) { pendingDelete = it }
                    }
                }
            }
            if (state.loaded && state.topical.isEmpty() && state.bookGroups.isEmpty()) {
                item(key = "empty") {
                    // IOS_ISSUES K20: iOS says "No matching series" even when nothing was searched.
                    val text = if (state.hasAnySeries || query.isNotBlank()) R.string.series_no_match else R.string.series_none_yet
                    Text(stringResource(text), color = c.neutral90, modifier = Modifier.fillMaxWidth().padding(top = 24.dp).testTag("seriesEmpty"))
                }
            }
        }
    }

    if (createOpen) CreateSeriesSheet(onDismiss = { createOpen = false }, onCreate = { t, b -> viewModel.createSeries(t, b); createOpen = false })
    pendingDelete?.let { sermon ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.sermon_delete_title)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSermon(sermon.id); pendingDelete = null }, modifier = Modifier.testTag("confirmDeleteSermon")) {
                    Text(stringResource(R.string.sermon_delete), color = c.red80)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = textStyle(13, weight = FontWeight.Bold), color = AppTheme.colors.neutral90,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun SeriesRowCard(
    row: SeriesWithSermons, expandedId: String?, onToggle: () -> Unit, onOpenSeries: (String) -> Unit,
    nav: SermonNavigation, onDelete: (SermonEntity) -> Unit,
) {
    SeriesCard(
        title = row.series.title, sermons = SeriesList.sermonsInOrder(row.sermons), expanded = expandedId == row.series.id,
        onToggle = onToggle, onDetail = { onOpenSeries(row.series.id) }, isUserData = row.series.isUserData,
        nav = nav, onDelete = onDelete, tag = row.series.id,
    )
}

/** iOS `SeriesRowView` / `UngroupedSermonsRowView`. */
@Composable
private fun SeriesCard(
    title: String, sermons: List<SermonEntity>, expanded: Boolean, onToggle: () -> Unit, onDetail: (() -> Unit)?,
    isUserData: Boolean, nav: SermonNavigation, onDelete: (SermonEntity) -> Unit, tag: String,
) {
    val c = AppTheme.colors
    Surface(shape = RoundedCornerShape(18.dp), color = c.surface0, border = BorderStroke(1.dp, c.neutral20), modifier = Modifier.fillMaxWidth().testTag("seriesCard_$tag")) {
        Column {
            Surface(onClick = onToggle, color = c.surface0) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = textStyle(16, weight = FontWeight.SemiBold), color = c.neutral100, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(pluralStringResource(R.plurals.day_sermons_subtitle, sermons.size, sermons.size), style = textStyle(12), color = c.neutral90)
                    }
                    if (onDetail != null) {
                        IconButton(onClick = onDetail, modifier = Modifier.testTag("seriesDetail_$tag")) {
                            Icon(if (isUserData) Icons.Outlined.EditNote else Icons.Outlined.Info, stringResource(R.string.series_edit), tint = c.blue80)
                        }
                    }
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, stringResource(R.string.series_expand), tint = c.neutral90, modifier = Modifier.padding(end = 8.dp))
                }
            }
            if (expanded) {
                HorizontalDivider(color = c.blue20)
                sermons.forEachIndexed { index, sermon ->
                    SeriesSermonRow(sermon, nav, onDelete)
                    if (index < sermons.lastIndex) HorizontalDivider(color = c.neutral20, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

/** A sermon inside a series card: reference, title, date, Listen / Watch / Read (Read works here: K19). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesSermonRow(sermon: SermonEntity, nav: SermonNavigation, onDelete: (SermonEntity) -> Unit, actions: SermonActionsViewModel = hiltViewModel()) {
    val c = AppTheme.colors
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = { nav.openSermon(sermon.id, VideoEntry.Default) }, onLongClick = { if (sermon.isUserData) onDelete(sermon) })
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("seriesSermon_${sermon.id}"),
    ) {
        sermon.chapterRange?.trim()?.takeIf { it.isNotEmpty() }?.let { Text(it, style = textStyle(13, weight = FontWeight.SemiBold), color = c.blue80) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sermon.title.ifBlank { stringResource(R.string.sermon_untitled) }, style = textStyle(15), color = c.neutral100, modifier = Modifier.weight(1f))
            SermonRules.day(sermon.preachedOn)?.let { Text(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(it), style = textStyle(12), color = c.neutral90) }
        }
        SermonMediaRow(
            sermon,
            onListen = { actions.listen(sermon); nav.openSermon(sermon.id, VideoEntry.Suppressed) },
            onWatch = { nav.openSermon(sermon.id, VideoEntry.Autoplay) },
            onOpenUrl = { openUrl(context, it) },
        )
    }
}

/** iOS create-series sheet: a title and an optional book (names in the interface language: K16). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSeriesSheet(onDismiss: () -> Unit, onCreate: (String, Int?) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var bookOrd by rememberSaveable { mutableStateOf<Int?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val c = AppTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.series_create), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = title, onValueChange = { title = it }, singleLine = true, label = { Text(stringResource(R.string.sermon_title)) },
                modifier = Modifier.fillMaxWidth().testTag("newSeriesTitle"),
            )
            Text(stringResource(R.string.series_book_optional), style = MaterialTheme.typography.labelLarge)
            Box {
                Surface(onClick = { menuOpen = true }, shape = RoundedCornerShape(14.dp), color = c.pageBackground, modifier = Modifier.fillMaxWidth().testTag("newSeriesBook")) {
                    Text(
                        bookOrd?.let { BibleBooks.name(it) } ?: stringResource(R.string.series_book_none),
                        color = if (bookOrd == null) c.neutral90 else c.blue100, modifier = Modifier.padding(14.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.series_book_none)) }, onClick = { bookOrd = null; menuOpen = false })
                    BibleBooks.all.forEach { book ->
                        DropdownMenuItem(text = { Text(book.fullName()) }, onClick = { bookOrd = book.ord; menuOpen = false })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onCreate(title, bookOrd) }, enabled = title.isNotBlank(), modifier = Modifier.weight(1f).testTag("newSeriesCreate")) {
                    Text(stringResource(R.string.action_create))
                }
            }
        }
    }
}

// MARK: - Series detail

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    db: AppDatabase,
    private val sermons: SermonRepository,
) : ViewModel() {
    val seriesId: String = checkNotNull(savedState[ARG_ID])

    val series: StateFlow<Loaded<SermonSeriesEntity>> = db.sermonSeriesDao().observeById(seriesId)
        .map { Loaded(true, it?.takeIf { s -> s.deletedAt == null }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loaded())

    val sermonCount: StateFlow<Int> = db.sermonDao().observeActive().map { list -> list.count { it.seriesId == seriesId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Called as the page closes, when this scope is being cancelled: the write must still finish. */
    fun rename(title: String) { viewModelScope.launch { withContext(NonCancellable) { sermons.renameSeries(seriesId, title) } } }

    fun setInProgress(inProgress: Boolean) { viewModelScope.launch { sermons.setSeriesFinished(seriesId, !inProgress) } }

    fun delete(onDone: () -> Unit) { viewModelScope.launch { if (sermons.deleteEmptySeries(seriesId)) onDone() } }

    companion object {
        const val ARG_ID = "seriesId"
    }
}

/**
 * iOS `SeriesDetailView`: title (saved when leaving, as on iOS), start date, in progress / finished,
 * sermon count, and Delete while the series has no sermons. Church series are read-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(onBack: () -> Unit, viewModel: SeriesDetailViewModel = hiltViewModel()) {
    val state by viewModel.series.collectAsStateWithLifecycle()
    val count by viewModel.sermonCount.collectAsStateWithLifecycle()
    val s = state.value
    if (s == null) {
        MissingPage(stringResource(R.string.series_not_found), loaded = state.loaded, onBack = onBack)
        return
    }
    val c = AppTheme.colors
    var title by rememberSaveable { mutableStateOf(s.title) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val latestTitle by rememberUpdatedState(title)
    val editable = s.isUserData
    // iOS saves the title on Close and on disappear.
    DisposableEffect(Unit) { onDispose { if (editable) viewModel.rename(latestTitle) } }

    Column(Modifier.fillMaxSize().background(c.pageBackground).safeDrawingPadding()) {
        CenterAlignedTopAppBar(
            title = { Text(if (editable) title else s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = c.pageBackground),
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = c.surface0, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.series_info), style = textStyle(13, weight = FontWeight.Bold), color = c.neutral90)
                    if (editable) {
                        OutlinedTextField(
                            value = title, onValueChange = { title = it }, singleLine = true, label = { Text(stringResource(R.string.sermon_title)) },
                            modifier = Modifier.fillMaxWidth().testTag("seriesTitle"),
                        )
                    } else {
                        DetailRow(stringResource(R.string.sermon_title), s.title)
                    }
                    DetailRow(
                        stringResource(R.string.series_start_date),
                        SermonRules.day(s.startedOn)?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(it) } ?: "—",
                    )
                    DetailRow(stringResource(R.string.series_status), stringResource(if (s.isFinished) R.string.series_finished else R.string.series_in_progress))
                    if (editable) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.series_in_progress), style = textStyle(15), color = c.neutral100, modifier = Modifier.weight(1f))
                            Switch(checked = !s.isFinished, onCheckedChange = viewModel::setInProgress, modifier = Modifier.testTag("seriesInProgress"))
                        }
                    }
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = c.surface0, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) { DetailRow(stringResource(R.string.series_sermon_count), "$count") }
            }
            if (!editable) Text(stringResource(R.string.series_read_only), style = textStyle(12), color = c.neutral90)
            if (editable && count == 0) {
                Button(
                    onClick = { confirmDelete = true },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.red80),
                    modifier = Modifier.fillMaxWidth().testTag("seriesDelete"),
                ) { Text(stringResource(R.string.series_delete)) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.series_delete_title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }, modifier = Modifier.testTag("confirmDeleteSeries")) {
                    Text(stringResource(R.string.series_delete), color = c.red80)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val c = AppTheme.colors
    Row {
        Text(label, style = textStyle(15), color = c.neutral100, modifier = Modifier.weight(1f))
        Text(value, style = textStyle(15), color = c.neutral90)
    }
}
