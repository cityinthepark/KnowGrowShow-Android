package com.stoneofhope.stoneofhope.ui.chapter

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.bible.BibleRefConfig
import com.stoneofhope.stoneofhope.data.chapter.ChapterSermons
import com.stoneofhope.stoneofhope.data.bible.BookChapter
import com.stoneofhope.stoneofhope.data.sermon.SermonRules
import com.stoneofhope.stoneofhope.ui.sermon.SermonActionsViewModel
import com.stoneofhope.stoneofhope.ui.sermon.VideoEntry
import com.stoneofhope.stoneofhope.ui.sermon.openUrl
import com.stoneofhope.stoneofhope.data.chapter.ChapterVerse
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.notes.NoteBadges
import com.stoneofhope.stoneofhope.ui.notes.MarkedText
import com.stoneofhope.stoneofhope.ui.notes.NoteActionsHost
import com.stoneofhope.stoneofhope.ui.notes.annotated
import com.stoneofhope.stoneofhope.ui.notes.capsStyle
import com.stoneofhope.stoneofhope.ui.notes.rememberNoteActionsState
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import com.stoneofhope.stoneofhope.ui.today.NoteSheet
import com.stoneofhope.stoneofhope.ui.today.chapterTitle
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/**
 * iOS `ChapterDetailView`: "Chapter Sermons | Chapter Notes" under the chapter's name, swipeable. On the
 * notes tab "+" opens the note sheet with this chapter filled in (iOS `ChapterNoteView`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterScreen(
    onBack: () -> Unit,
    onOpenNote: (noteId: String) -> Unit,
    onOpenVerse: (ChapterVerse) -> Unit,
    onOpenSermon: (sermonId: String, chapter: BookChapter, entry: VideoEntry) -> Unit,
    viewModel: ChapterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = viewModel.initialTab.ordinal) { ChapterTab.entries.size }
    val scope = rememberCoroutineScope()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val actions = rememberNoteActionsState()
    val c = AppTheme.colors

    Box(Modifier.fillMaxSize().background(c.pageBackground).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            CenterAlignedTopAppBar(
                title = { Text(chapterTitle(viewModel.chapter.bookOrd, viewModel.chapter.chapterOrd), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.pageBackground),
                windowInsets = WindowInsets(0),
            )
            PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = c.pageBackground) {
                ChapterTab.entries.forEach { tab ->
                    Tab(
                        selected = pager.currentPage == tab.ordinal,
                        onClick = { scope.launch { pager.animateScrollToPage(tab.ordinal) } },
                        text = { Text(stringResource(tab.title)) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = c.neutral90,
                        modifier = Modifier.testTag("chapterTab_${tab.name}"),
                    )
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                when (ChapterTab.entries[page]) {
                    ChapterTab.Sermons -> SermonsPage(state) { id, entry -> onOpenSermon(id, viewModel.chapter, entry) }
                    ChapterTab.Notes -> NotesPage(state, onOpenNote, onOpenVerse, onMenu = actions::open, onEnsureVerses = viewModel::ensureVerses)
                }
            }
        }
        if (pager.currentPage == ChapterTab.Notes.ordinal) {
            FloatingActionButton(
                onClick = { sheetOpen = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("chapterAddNote"),
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.chapter_add_note))
            }
        }
    }

    if (sheetOpen) {
        NoteSheet(
            bibleRefLanguages = viewModel.bibleRefLanguages,
            onDismiss = { sheetOpen = false },
            onSave = { content, ref ->
                sheetOpen = false
                viewModel.saveNote(content, ref)
            },
            initialReference = viewModel.defaultReference,
            config = BibleRefConfig.ChapterAnchored,
        )
    }
    NoteActionsHost(
        state = actions,
        target = actions.noteId?.let(state::actionTarget),
        allTags = state.allTags,
        onAction = viewModel::onAction,
    )
}

// MARK: - Chapter Sermons (iOS ChapterSermonView)

@Composable
private fun SermonsPage(state: ChapterState, onOpenSermon: (String, VideoEntry) -> Unit) {
    if (!state.loaded) return
    if (state.sermons.isEmpty()) {
        EmptyState(stringResource(R.string.chapter_no_sermons), Modifier.testTag("chapterNoSermons"))
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().testTag("chapterSermons"),
    ) {
        state.sermons.forEach { year ->
            item(key = "year_${year.year}") {
                Text(
                    year.year?.toString() ?: stringResource(R.string.sermon_date_unknown),
                    style = textStyle(17, weight = FontWeight.Bold),
                    color = AppTheme.colors.neutral100,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }
            items(year.sermons, key = { it.id }) { SermonCard(it, onOpenSermon) }
        }
    }
}

private data class SermonLink(@param:StringRes val label: Int, val icon: ImageVector, val onClick: () -> Unit)

/**
 * iOS `SermonRow`: chapter range, date and the intro toggle; the title opens the sermon page on this
 * chapter; the preacher; "⋯" with Listen / Watch (both go to the sermon page and play there) and Read
 * (the browser). A video link that isn't YouTube also opens in the browser.
 */
@Composable
private fun SermonCard(sermon: SermonEntity, onOpenSermon: (String, VideoEntry) -> Unit, actions: SermonActionsViewModel = hiltViewModel()) {
    val c = AppTheme.colors
    val context = LocalContext.current
    var expanded by rememberSaveable(sermon.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val intro = sermon.intro?.trim()?.takeIf { it.isNotEmpty() }
    val youTube = SermonRules.youTubeID(sermon)
    val links = listOfNotNull(
        ChapterSermons.audioUrl(sermon)?.let {
            SermonLink(R.string.sermon_listen, Icons.Outlined.Headphones) { actions.listen(sermon); onOpenSermon(sermon.id, VideoEntry.Suppressed) }
        },
        ChapterSermons.watchUrl(sermon)?.let { url ->
            SermonLink(R.string.sermon_watch, Icons.Outlined.SmartDisplay) {
                if (youTube != null) onOpenSermon(sermon.id, VideoEntry.Autoplay) else openUrl(context, url)
            }
        },
        ChapterSermons.pdfUrl(sermon)?.let { url -> SermonLink(R.string.sermon_read, Icons.Outlined.Description) { openUrl(context, url) } },
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = c.surface0,
        border = BorderStroke(1.dp, c.neutral20),
        modifier = Modifier.fillMaxWidth().testTag("sermon_${sermon.id}"),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Text(NoteBadges.typeIcon(NoteType.SermonNote), style = textStyle(17), modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        sermon.chapterRange?.trim().orEmpty(),
                        style = textStyle(13, weight = FontWeight.SemiBold),
                        color = c.blue80,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    ChapterSermons.preachedOn(sermon)?.let {
                        Text(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(it), style = textStyle(12), color = c.neutral90)
                    }
                    if (intro != null) {
                        IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp).testTag("sermonIntroToggle")) {
                            Icon(
                                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                stringResource(if (expanded) R.string.sermon_hide_intro else R.string.sermon_show_intro),
                                tint = c.neutral90,
                            )
                        }
                    }
                }
                Text(
                    sermon.title,
                    style = textStyle(17, 1.35f, FontWeight.SemiBold),
                    color = c.blue80,
                    modifier = Modifier.padding(top = 4.dp).clickable { onOpenSermon(sermon.id, VideoEntry.Default) }.testTag("sermonTitle_${sermon.id}"),
                )
                sermon.preacher?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = textStyle(12), color = c.neutral90, modifier = Modifier.padding(top = 2.dp))
                }
                if (expanded && intro != null) {
                    Text(intro, style = textStyle(14, 1.5f), color = c.neutral90, modifier = Modifier.padding(top = 6.dp).testTag("sermonIntro"))
                }
            }
            if (links.isNotEmpty()) {
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp).testTag("sermonMedia")) {
                        Icon(Icons.Filled.MoreHoriz, stringResource(R.string.sermon_media), tint = c.neutral90)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        links.forEach { link ->
                            DropdownMenuItem(
                                text = { Text(stringResource(link.label)) },
                                leadingIcon = { Icon(link.icon, null) },
                                onClick = {
                                    menuOpen = false
                                    link.onClick()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Chapter Notes (iOS ChapterNoteView)

@Composable
private fun NotesPage(
    state: ChapterState,
    onOpenNote: (String) -> Unit,
    onOpenVerse: (ChapterVerse) -> Unit,
    onMenu: (String) -> Unit,
    onEnsureVerses: (String) -> Unit,
) {
    if (!state.loaded) return
    if (state.chapterNotes.isEmpty() && state.verses.isEmpty()) {
        EmptyState(stringResource(R.string.chapter_no_notes), Modifier.testTag("chapterNoNotes"), hint = stringResource(R.string.chapter_no_notes_hint))
        return
    }
    LazyColumn(
        // Room at the bottom so "+" never covers the last card.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("chapterNotes"),
    ) {
        if (state.chapterNotes.isNotEmpty()) {
            item(key = "chapterHeader") { SectionTitle(NoteBadges.typeIcon(NoteType.Note) + " " + stringResource(R.string.chapter_note_header)) }
            items(state.chapterNotes, key = { it.note.id }) { card ->
                DatedNoteCard(card, onOpen = { onOpenNote(card.note.id) }, onMenu = { onMenu(card.note.id) })
            }
        }
        items(state.verses, key = { "verse_${it.verse.verseStart}_${it.verse.verseEnd}" }) { group ->
            group.textSourceId?.let { id -> LaunchedEffect(id) { onEnsureVerses(id) } }
            VerseGroupCard(group, onOpen = { onOpenVerse(group.verse) })
        }
    }
}

/**
 * One verse range: its reference and text on the scripture tint, then "3 reflections · 2 prayers ›"
 * when there is anything under it (a verse with only its anchor shows just the verse: H4).
 */
@Composable
private fun VerseGroupCard(group: ChapterVerseUi, onOpen: () -> Unit) {
    val c = AppTheme.colors
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = c.surface0,
        border = BorderStroke(1.dp, c.neutral20),
        modifier = Modifier.fillMaxWidth().testTag("chapterVerse_${group.verse.verseStart}_${group.verse.verseEnd}"),
    ) {
        Column {
            Column(Modifier.fillMaxWidth().background(c.noteTypeBibleVerse.copy(alpha = 0.06f)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("${NoteBadges.BIBLE_ICON} " + group.refLabel.uppercase(), style = capsStyle(0.06f), color = c.scriptureInk)
                group.text?.let {
                    MarkedText(it.annotated(), style = textStyle(16, 1.6f, serif = true), color = c.neutral100, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (group.reflectionCount > 0 || group.prayerCount > 0) {
                HorizontalDivider(color = c.noteTypeBibleVerse.copy(alpha = 0.12f))
                Row(
                    Modifier.height(44.dp).padding(start = 16.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = c.neutral90, modifier = Modifier.size(14.dp))
                    Text(verseSummary(group), style = textStyle(12), color = c.neutral90, modifier = Modifier.weight(1f).testTag("verseSummary"))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.neutral90, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** iOS `verseSummaryText`, with "reflections" for what iOS calls comments. */
@Composable
private fun verseSummary(group: ChapterVerseUi): String {
    val parts = mutableListOf<String>()
    if (group.reflectionCount > 0) parts += pluralStringResource(R.plurals.reflections_count, group.reflectionCount, group.reflectionCount)
    if (group.prayerCount > 0) parts += pluralStringResource(R.plurals.prayers_count, group.prayerCount, group.prayerCount)
    return parts.joinToString(" · ")
}
