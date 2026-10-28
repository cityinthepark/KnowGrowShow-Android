package com.stoneofhope.stoneofhope.ui.today

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.notes.NoteFilter
import com.stoneofhope.stoneofhope.data.today.TodayActivity.TileKind
import com.stoneofhope.stoneofhope.ui.chapter.ChapterTab
import com.stoneofhope.stoneofhope.ui.reading.ReadingLogViewModel
import com.stoneofhope.stoneofhope.ui.theme.AppColors
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Which quick-add sheet is open. */
private enum class TodaySheet { Prayer, Note }

/**
 * iOS `TodayView`: greeting, reading carousel, quick add, today's activity.
 * [onOpen] receives the title of a screen that isn't ported yet (shown as "coming soon");
 * [onOpenChapter] opens a chapter page (iOS `ChapterDetailView`) on the given tab;
 * [onOpenNotes] is a tile's note list (iOS `NoteCreationView(date, type)`); [onOpenReview] the day's
 * "Review" (iOS `NotesReviewView`).
 */
@Composable
fun TodayScreen(
    onOpen: (title: String) -> Unit,
    onOpenReadingLog: (bookOrd: Int) -> Unit,
    onOpenChapter: (bookOrd: Int, chapter: Int, tab: ChapterTab) -> Unit,
    onOpenNotes: (NoteFilter, LocalDate) -> Unit,
    onOpenReview: (LocalDate) -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val day by viewModel.day.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val carousel by viewModel.carousel.collectAsStateWithLifecycle()
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    val flashCount by viewModel.activeFlashCards.collectAsStateWithLifecycle()
    val tagNames by viewModel.tagNames.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<TodaySheet?>(null) }
    val context = LocalContext.current
    val comingSoon = stringResource(R.string.tab_placeholder)
    val profileTitle = stringResource(R.string.today_profile)
    val flashcardTitle = stringResource(R.string.flashcard)

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDay()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("todayList"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { GreetingHeader(day, onProfile = { onOpen(profileTitle) }) }
        item {
            ReadingCarousel(
                books = carousel,
                sort = sort,
                onToggleSort = viewModel::toggleSort,
                onToggleChapter = viewModel::toggleChapterRead,
                // iOS `BookCarouselView`: a long press opens the chapter on its sermons.
                onOpenChapter = { bookOrd, chapter -> onOpenChapter(bookOrd, chapter, ChapterTab.Sermons) },
                onOpenBook = onOpenReadingLog,
                // iOS opens the Reading Log at Genesis.
                onStartReading = { onOpenReadingLog(1) },
                onAddBook = { onOpenReadingLog(ReadingLogViewModel.NO_BOOK) },
            )
        }
        item {
            QuickAddRow(
                flashCount = flashCount,
                onPrayer = { sheet = TodaySheet.Prayer },
                onNote = { sheet = TodaySheet.Note },
                // T3 of the Today plan.
                onSermon = { Toast.makeText(context, comingSoon, Toast.LENGTH_SHORT).show() },
                onVideo = { Toast.makeText(context, comingSoon, Toast.LENGTH_SHORT).show() },
                onFlashCards = { onOpen(flashcardTitle) },
            )
        }
        item {
            TodayActivitySection(
                activity = activity,
                onOpen = onOpen,
                onOpenChapter = onOpenChapter,
                onOpenTile = { kind -> onOpenNotes(kind.noteFilter, day) },
                onReview = { onOpenReview(day) },
                onUnread = viewModel::toggleChapterRead,
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    when (sheet) {
        TodaySheet.Prayer -> PrayerSheet(
            tagNames = tagNames,
            bibleRefLanguages = viewModel.bibleRefLanguages,
            onDismiss = { sheet = null },
            onSave = { content, tag, ref -> viewModel.savePrayer(content, tag, ref); sheet = null },
        )
        TodaySheet.Note -> NoteSheet(
            bibleRefLanguages = viewModel.bibleRefLanguages,
            onDismiss = { sheet = null },
            onSave = { content, ref -> viewModel.saveNote(content, ref); sheet = null },
        )
        null -> Unit
    }
}

/** iOS `TodayGreetingHeader`: greeting by local hour, long date, avatar → profile. */
@Composable
private fun GreetingHeader(day: LocalDate, onProfile: () -> Unit) {
    val hour = LocalTime.now().hour
    val greeting = stringResource(
        when (hour) {
            in 5..11 -> R.string.today_good_morning
            in 12..16 -> R.string.today_good_afternoon
            else -> R.string.today_good_evening
        }
    )
    val locale = Locale.getDefault()
    val date = day.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale))
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val profile = stringResource(R.string.today_profile)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.avatarBackground)
                .clickable(onClick = onProfile)
                .semantics { contentDescription = profile },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = colors.avatarIcon)
        }
    }
}

/** iOS `TodayStudyToolsRow`: four icon-only quick-add buttons and the flashcard capsule. */
@Composable
private fun QuickAddRow(
    flashCount: Int,
    onPrayer: () -> Unit,
    onNote: () -> Unit,
    onSermon: () -> Unit,
    onVideo: () -> Unit,
    onFlashCards: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.quick_add), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Surface(
                onClick = onFlashCards,
                shape = RoundedCornerShape(50),
                color = colors.flashChip.background,
                contentColor = colors.flashChip.foreground,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.flashcard), style = MaterialTheme.typography.labelLarge)
                    if (flashCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text("$flashCount", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAddButton("🌿", stringResource(R.string.note_type_prayer), colors.prayerChip, onPrayer, "quickAddPrayer")
            QuickAddButton("📝", stringResource(R.string.note_type_note), colors.notesChip, onNote, "quickAddNote")
            QuickAddButton("📖", stringResource(R.string.note_type_sermon), colors.sermonChip, onSermon, "quickAddSermon")
            QuickAddButton("▶️", stringResource(R.string.note_type_video), colors.videoChip, onVideo, "quickAddVideo")
        }
    }
}

/** iOS uses plain emoji for note-type icons so every platform renders them the same. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.QuickAddButton(
    emoji: String,
    label: String,
    pair: AppColors.ChipPair,
    onClick: () -> Unit,
    tag: String,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = pair.background,
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .testTag(tag)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp) }
    }
}

/** Today tile → its note list filter (iOS `TodayActivityView` tile actions). */
internal val TileKind.noteFilter: NoteFilter
    get() = when (this) {
        TileKind.Pinned -> NoteFilter.Pinned
        TileKind.Starred -> NoteFilter.Starred
        TileKind.QuickNote -> NoteFilter.QuickNote
        TileKind.StudyNote -> NoteFilter.StudyNote
        TileKind.Bible -> NoteFilter.Bible
        TileKind.Prayer -> NoteFilter.Prayer
        TileKind.AnsweredPrayer -> NoteFilter.AnsweredPrayer
        TileKind.Image -> NoteFilter.Image
    }
