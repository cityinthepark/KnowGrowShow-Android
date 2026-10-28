package com.stoneofhope.stoneofhope.ui.reading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.bible.BibleBook
import com.stoneofhope.stoneofhope.data.bible.BibleBooks
import com.stoneofhope.stoneofhope.data.bible.BibleLanguage
import com.stoneofhope.stoneofhope.data.reading.ReadingModel
import com.stoneofhope.stoneofhope.ui.theme.AppTheme

/**
 * iOS `BibleBookListView` ("Reading Log"): all 66 books, one expanded at a time, with a 5-column
 * chapter grid. Tap a chapter to toggle read, long-press for the chapter detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingLogScreen(
    onBack: () -> Unit,
    onOpenChapter: (title: String) -> Unit,
    viewModel: ReadingLogViewModel = hiltViewModel(),
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf<Int?>(viewModel.initialBookOrd) }
    // Opens with the starting book at the top, like iOS `scrollTo(initialBookOrd, anchor: .top)`.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (viewModel.initialBookOrd ?: 1) - 1)

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDay()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().background(AppTheme.colors.pageBackground).safeDrawingPadding()) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.reading_log), fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.pageBackground),
            windowInsets = WindowInsets(0),
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.testTag("readingLog"),
        ) {
            items(BibleBooks.all, key = { it.ord }) { book ->
                val log = books[book.ord] ?: ReadingModel.BookLog.Empty
                BookRow(
                    book = book,
                    log = log,
                    expanded = expanded == book.ord,
                    canRestart = viewModel.canRestart(log),
                    onToggleExpanded = { expanded = if (expanded == book.ord) null else book.ord },
                    onRestart = { viewModel.restart(book.ord) },
                    onToggleChapter = { chapter -> viewModel.toggleChapterRead(book.ord, chapter) },
                    onOpenChapter = { chapter -> onOpenChapter("${book.fullName()} $chapter") },
                )
            }
        }
    }
}

@Composable
private fun BookRow(
    book: BibleBook,
    log: ReadingModel.BookLog,
    expanded: Boolean,
    canRestart: Boolean,
    onToggleExpanded: () -> Unit,
    onRestart: () -> Unit,
    onToggleChapter: (Int) -> Unit,
    onOpenChapter: (Int) -> Unit,
) {
    val colors = AppTheme.colors
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surface0, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 4.dp, vertical = 14.dp)
                    .testTag("book_${book.ord}"),
            ) {
                Text(book.fullName(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.neutral100)
                Spacer(Modifier.width(6.dp))
                Text(
                    abbreviationsLabel(book),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.neutral50,
                    modifier = Modifier.weight(1f),
                )
                if (canRestart) {
                    Surface(
                        onClick = onRestart,
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp).testTag("restart_${book.ord}"),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.reading_restart), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = colors.neutral50,
                )
            }
            AnimatedVisibility(expanded) {
                ChapterGrid(book, log, onToggleChapter, onOpenChapter)
            }
        }
    }
}

/** iOS `BibleBookHelper.getFirstTwoAbbreviations`: "(Gen, Gn)" / "(创)". */
private fun abbreviationsLabel(book: BibleBook): String =
    book.abbreviations(BibleLanguage.current()).take(2).joinToString(", ", prefix = "(", postfix = ")")

private const val COLUMNS = 5

@Composable
private fun ChapterGrid(book: BibleBook, log: ReadingModel.BookLog, onToggle: (Int) -> Unit, onOpen: (Int) -> Unit) {
    Column(Modifier.padding(top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (1..book.chapterCount).chunked(COLUMNS).forEach { rowChapters ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowChapters.forEach { chapter ->
                    ChapterCell(
                        chapter = chapter,
                        state = log.state(chapter),
                        readCount = log.readCounts[chapter] ?: 0,
                        onTap = { onToggle(chapter) },
                        onLongPress = { onOpen(chapter) },
                        modifier = Modifier.weight(1f).testTag("cell_${book.ord}_$chapter"),
                    )
                }
                // Keep the last row's cells the same width as full rows.
                repeat(COLUMNS - rowChapters.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** iOS `ChapterReadCell`: 52pt tall, rounded 8, read-count in the corner when read more than once. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterCell(
    chapter: Int,
    state: ReadingModel.ChipState,
    readCount: Int,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier,
) {
    val style = readingChipStyle(state)
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(style.fill)
            .then(if (style.border != null) Modifier.border(style.border, shape) else Modifier)
            .combinedClickable(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTap()
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            ),
    ) {
        Text("$chapter", color = style.text, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
        ReadingModel.readCountBadge(readCount)?.let { badge ->
            Text(
                badge,
                color = style.text.copy(alpha = 0.9f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 5.dp),
            )
        }
    }
}
