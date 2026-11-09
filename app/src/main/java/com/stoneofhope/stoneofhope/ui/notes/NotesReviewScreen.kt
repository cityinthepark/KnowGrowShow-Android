package com.stoneofhope.stoneofhope.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.notes.MarkedVerse
import com.stoneofhope.stoneofhope.data.notes.NoteBadges
import com.stoneofhope.stoneofhope.data.notes.ResolvedVerse
import com.stoneofhope.stoneofhope.data.notes.ThreadItem

/** iOS `VerseCardDesign` tokens used by the review. */
private object ReviewDesign {
    val scrimBase = Color(0xFF001A17)
    val leavesScrim = Color(0xFF0A3020)
    val leavesScrimBottom = Color(0xFF062418)
    val answered = Color(0xFF7BD88F)
    val tagChip = Color(0xFFFF9500)
    val sidePadding = 20.dp
    val trailingPadding = 56.dp
    val controlSize = 40.dp

    /** iOS `.brightness(-0.06).saturation(0.94).contrast(0.94)` on the photo. */
    val photoFilter = ColorFilter.colorMatrix(
        ColorMatrix().apply {
            setToSaturation(0.94f)
            timesAssign(ColorMatrix(floatArrayOf(0.94f, 0f, 0f, 0f, -7.6f, 0f, 0.94f, 0f, 0f, -7.6f, 0f, 0f, 0.94f, 0f, -7.6f, 0f, 0f, 0f, 1f, 0f)))
        },
    )

    val scrimGradient = Brush.verticalGradient(
        0f to leavesScrim.copy(alpha = 0.42f),
        0.20f to leavesScrim.copy(alpha = 0.30f),
        0.45f to leavesScrim.copy(alpha = 0.26f),
        0.72f to leavesScrim.copy(alpha = 0.32f),
        1f to leavesScrimBottom.copy(alpha = 0.48f),
    )
}

/**
 * iOS `NotesReviewView`: a day's notes full screen over the green-leaves photo — swipe sideways between
 * notes, scroll up within one to read its comments. Read-only, like iOS.
 */
@Composable
fun NotesReviewScreen(onClose: () -> Unit, viewModel: NotesReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pages = state.pages
    val pager = rememberPagerState { pages.size }
    Box(Modifier.fillMaxSize().background(ReviewDesign.leavesScrim).testTag("notesReview")) {
        Image(
            painterResource(R.drawable.notes_review_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = ReviewDesign.photoFilter,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(ReviewDesign.leavesScrim.copy(alpha = 0.10f)))
        Box(Modifier.fillMaxSize().background(ReviewDesign.scrimGradient))

        if (state.loaded && pages.isEmpty()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.review_empty), style = textStyle(17, weight = FontWeight.SemiBold), color = Color.White)
                TextButton(onClick = onClose) { Text(stringResource(R.string.bible_ref_close), style = textStyle(15, weight = FontWeight.SemiBold), color = Color.White) }
            }
        } else {
            HorizontalPager(pager, key = { pages[it].card.note.id }, modifier = Modifier.fillMaxSize().testTag("reviewPager")) { index ->
                ReviewPageContent(pages[index])
            }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = ReviewDesign.sidePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = ReviewDesign.scrimBase.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.size(ReviewDesign.controlSize).testTag("reviewClose"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, stringResource(R.string.bible_ref_close), tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            if (pages.isNotEmpty()) {
                Text("${pager.currentPage + 1} / ${pages.size}", style = textStyle(13, weight = FontWeight.SemiBold), color = Color.White.copy(alpha = 0.85f), modifier = Modifier.testTag("reviewPosition"))
            }
        }

        if (pages.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.indices.forEach { i ->
                    val active = i == pager.currentPage
                    Box(Modifier.size(if (active) 8.dp else 6.dp).background(Color.White.copy(alpha = if (active) 1f else 0.4f), CircleShape))
                }
            }
        }
    }
}

/**
 * One note: header, body (an anchor's verse text), a prayer's verse card and the time, centered on the
 * screen; with comments it stops 90dp short so the first one peeks in as a hint to scroll up.
 */
@Composable
private fun ReviewPageContent(page: ReviewPage) {
    val note = page.card.note
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hasThread = page.items.isNotEmpty()
        val screen = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(min = if (hasThread) screen - 90.dp else screen)
                    .statusBarsPadding()
                    .padding(start = ReviewDesign.sidePadding, end = ReviewDesign.trailingPadding, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                Spacer(Modifier.height(ReviewDesign.controlSize))
                Header(page)
                // An anchor reads its verse text in every selected version with the Web's highlights (iOS `highlightedVerseText`).
                val body = MarkedVerse(page.card.displayContent, page.card.bodyMarks)
                MarkedText(body.annotated(), style = textStyle(26, 1.4f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.testTag("reviewBody"))
                if (note.noteType == NoteType.Prayer) page.verse?.let { LinkedVerseCard(it) }
                Text(relativeTime(note.createdAt), style = textStyle(12, weight = FontWeight.SemiBold), color = Color.White.copy(alpha = 0.55f))
            }
            if (hasThread) Thread(page)
        }
    }
}

/** Type label (verse reference, "Chapter · …", or the type), then 📌, stars, 🙌 and tags — wrapping under it when long. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(page: ReviewPage) {
    val note = page.card.note
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(page.card.badge.icon, style = textStyle(16))
            Text(page.card.badge.label(), style = textStyle(17, weight = FontWeight.Bold), color = Color.White, maxLines = 1)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (note.isPinned == true) {
                Icon(Icons.Filled.PushPin, stringResource(R.string.tile_pinned), tint = Color.White, modifier = Modifier.size(14.dp).align(Alignment.CenterVertically))
            }
            if (note.starLevel > 0) StarIcons(note.starLevel, Modifier.align(Alignment.CenterVertically))
            if (note.noteType == NoteType.Prayer && note.isAnswered == true) Text("🙌", style = textStyle(13), modifier = Modifier.align(Alignment.CenterVertically))
            page.card.tagNames.filter { it.isNotEmpty() }.forEach { name ->
                Text(
                    name,
                    style = textStyle(11, weight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterVertically).background(ReviewDesign.tagChip, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** iOS `LinkedVerseCard(style: .dark)`. */
@Composable
private fun LinkedVerseCard(verse: ResolvedVerse) {
    Column(
        Modifier.fillMaxWidth()
            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(NoteBadges.BIBLE_ICON, style = textStyle(15))
            Text(verse.refLabel, style = textStyle(17, weight = FontWeight.Bold), color = Color.White, maxLines = 1)
        }
        MarkedText(verse.combined.trimmed().annotated(), style = textStyle(16, 1.4f), color = Color.White.copy(alpha = 0.9f))
    }
}

/** "N comments" (or reflections) and one translucent card each. */
@Composable
private fun Thread(page: ReviewPage) {
    Column(
        Modifier.fillMaxWidth().padding(start = ReviewDesign.sidePadding, end = ReviewDesign.trailingPadding, top = 24.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
        val label = if (page.card.isAnchor) pluralStringResource(R.plurals.reflections_count, page.count, page.count)
        else pluralStringResource(R.plurals.comments_count, page.count, page.count)
        Text(label, style = textStyle(13, weight = FontWeight.SemiBold), color = Color.White.copy(alpha = 0.65f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            page.items.forEach { ThreadCard(it) }
        }
        Spacer(Modifier.height(24.dp + 36.dp))
    }
}

@Composable
private fun ThreadCard(item: ThreadItem) {
    Column(
        Modifier.fillMaxWidth()
            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag("reviewComment"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (item.isAnsweredMarker) {
            Text("🙌 " + stringResource(R.string.answered_today), style = textStyle(16, weight = FontWeight.SemiBold), color = ReviewDesign.answered)
        } else {
            Text(item.text, style = textStyle(18, 1.35f, FontWeight.Medium), color = Color.White.copy(alpha = 0.95f))
        }
        Text(relativeTime(item.createdAt), style = textStyle(12, weight = FontWeight.SemiBold), color = Color.White.copy(alpha = 0.55f))
    }
}
