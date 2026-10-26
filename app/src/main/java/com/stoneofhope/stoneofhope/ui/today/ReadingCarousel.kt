package com.stoneofhope.stoneofhope.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.bible.BibleBooks
import com.stoneofhope.stoneofhope.data.reading.ReadingModel
import com.stoneofhope.stoneofhope.ui.theme.AppTheme

fun bookTitle(bookOrd: Int): String = BibleBooks.name(bookOrd)

fun chapterTitle(bookOrd: Int, chapter: Int): String = "${BibleBooks.name(bookOrd)} $chapter"

/** iOS `BookCarouselView`: one card per book with progress, and 3 chapter chips around the last read. */
@Composable
fun ReadingCarousel(
    books: List<ReadingModel.CarouselBook>,
    sort: ReadingModel.Sort,
    onToggleSort: () -> Unit,
    onToggleChapter: (bookOrd: Int, chapter: Int) -> Unit,
    onOpenChapter: (bookOrd: Int, chapter: Int) -> Unit,
    onOpenBook: (bookOrd: Int) -> Unit,
    onStartReading: () -> Unit,
) {
    Column {
        Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reading_by_books), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (books.isNotEmpty()) {
                // Like iOS, the button names the order you would switch to.
                TextButton(onClick = onToggleSort) {
                    Text(
                        stringResource(
                            if (sort == ReadingModel.Sort.LastRead) R.string.reading_by_book_order else R.string.reading_by_last_read
                        )
                    )
                }
            }
        }
        if (books.isEmpty()) {
            StartReadingCard(onStartReading)
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("readingCarousel"),
            ) {
                itemsIndexed(books, key = { _, book -> book.progress.id }) { index, book ->
                    BookCard(book, index, onToggleChapter, onOpenChapter, onOpenBook)
                }
            }
        }
    }
}

@Composable
private fun StartReadingCard(onStart: () -> Unit) {
    val colors = AppTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface0,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.reading_start_prompt), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = colors.blue100),
            ) { Text(stringResource(R.string.reading_mark_progress)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    book: ReadingModel.CarouselBook,
    index: Int,
    onToggleChapter: (Int, Int) -> Unit,
    onOpenChapter: (Int, Int) -> Unit,
    onOpenBook: (Int) -> Unit,
) {
    val tint = AppTheme.colors.bookTint(index)
    Surface(
        onClick = { onOpenBook(book.bookOrd) },
        shape = RoundedCornerShape(18.dp),
        color = tint.background,
        modifier = Modifier.width(158.dp).testTag("bookCard_${book.bookOrd}"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                bookTitle(book.bookOrd).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = tint.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${book.chaptersRead}/${book.chapterCount}",
                style = MaterialTheme.typography.bodySmall,
                color = tint.foreground,
            )
            LinearProgressIndicator(
                progress = { if (book.chapterCount == 0) 0f else book.chaptersRead / book.chapterCount.toFloat() },
                color = tint.foreground,
                trackColor = tint.foreground.copy(alpha = 0.15f),
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                book.chips.forEach { chip ->
                    ChapterChip(
                        chip = chip,
                        onTap = { onToggleChapter(book.bookOrd, chip.chapter) },
                        onLongPress = { onOpenChapter(book.bookOrd, chip.chapter) },
                        tag = "chip_${book.bookOrd}_${chip.chapter}",
                    )
                }
            }
        }
    }
}

/** Solid blue = read today; light blue with border = read earlier this cycle; muted = unread. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterChip(chip: ReadingModel.Chip, onTap: () -> Unit, onLongPress: () -> Unit, tag: String) {
    val colors = AppTheme.colors
    val haptics = LocalHapticFeedback.current
    val (fill, text, border) = when (chip.state) {
        ReadingModel.ChipState.ReadToday -> Triple(colors.blue100, Color.White, null)
        ReadingModel.ChipState.Read -> Triple(colors.blue20, colors.blue100, BorderStroke(1.dp, colors.blue100.copy(alpha = 0.7f)))
        ReadingModel.ChipState.Unread -> Triple(colors.readingChipMutedFill, colors.blue100, null)
    }
    Box {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(fill)
                .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
                .combinedClickable(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTap()
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    },
                )
                .testTag(tag),
            contentAlignment = Alignment.Center,
        ) {
            Text("${chip.chapter}", color = text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        ReadingModel.readCountBadge(chip.totalReads)?.let { badge ->
            Text(
                badge,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-4).dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.salmon100)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}
