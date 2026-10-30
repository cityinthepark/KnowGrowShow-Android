package com.stoneofhope.stoneofhope.ui.flashcard

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.bible.BibleRefConfig
import com.stoneofhope.stoneofhope.data.flashcard.FlashCardAddResult
import com.stoneofhope.stoneofhope.data.flashcard.FlashCardAddStatus
import com.stoneofhope.stoneofhope.data.flashcard.FlashCardSort
import com.stoneofhope.stoneofhope.data.flashcard.FlashCards
import com.stoneofhope.stoneofhope.ui.bible.BibleReferenceField
import com.stoneofhope.stoneofhope.ui.bible.BibleRefHelp
import com.stoneofhope.stoneofhope.ui.notes.MarkedText
import com.stoneofhope.stoneofhope.ui.notes.annotated
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** iOS `VerseCardDesign` tokens used by the deck. */
private object DeckDesign {
    val scrimBase = Color(0xFF001A17)
    val scrimBaseBottom = Color(0xFF001412)
    val sidePadding = 20.dp
    val trailingPadding = 56.dp
    val controlSize = 40.dp
    val scrimGradient = Brush.verticalGradient(
        0f to scrimBase.copy(alpha = 0.55f),
        0.22f to scrimBase.copy(alpha = 0.15f),
        0.45f to scrimBase.copy(alpha = 0.05f),
        0.78f to scrimBase.copy(alpha = 0.75f),
        1f to scrimBaseBottom.copy(alpha = 0.92f),
    )
}

// MARK: - List (iOS FlashCardListView)

/** Flash cards: "Active" (with ▶ review) and "Completed", sorted by date or book; `+` adds a card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashCardListScreen(
    onBack: () -> Unit,
    onOpenCard: (String) -> Unit,
    onStartReview: () -> Unit,
    onOpenVersions: () -> Unit,
    viewModel: FlashCardsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<FlashCardUi?>(null) }
    val context = LocalContext.current
    val resources = LocalResources.current

    val sorted = remember(state, sort) {
        val order = FlashCards.sorted(state.cards.map { it.card }, sort).map { it.id }
        val byId = state.cards.associateBy { it.id }
        order.mapNotNull(byId::get)
    }
    val active = sorted.filter { !it.isArchived }
    val completed = sorted.filter { it.isArchived }

    Scaffold(
        modifier = Modifier.safeDrawingPadding().testTag("flashCardList"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.flashcards_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    TextButton(onClick = viewModel::toggleSort, modifier = Modifier.testTag("flashSort")) {
                        Text(stringResource(if (sort == FlashCardSort.ByDate) R.string.flash_sort_by_book else R.string.flash_sort_by_date))
                    }
                    TextButton(onClick = onOpenVersions, modifier = Modifier.testTag("flashVersions")) {
                        Text(viewModel.versionsLabel, style = MaterialTheme.typography.labelSmall, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.testTag("flashAdd")) {
                Icon(Icons.Filled.Add, stringResource(R.string.flash_add))
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        if (state.loaded && state.cards.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.flash_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active.isNotEmpty()) {
                    item(key = "activeHeader") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel(stringResource(R.string.flash_active), Modifier.weight(1f))
                            IconButton(onClick = onStartReview, modifier = Modifier.testTag("flashStartReview")) {
                                Icon(Icons.Filled.PlayCircle, stringResource(R.string.flash_start_review), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    items(active, key = { it.id }) { FlashCardRow(it, { onOpenCard(it.id) }, { viewModel.toggleArchive(it.id) }, { toDelete = it }) }
                }
                if (completed.isNotEmpty()) {
                    item(key = "completedHeader") { SectionLabel(stringResource(R.string.flash_completed), Modifier.padding(top = 12.dp)) }
                    items(completed, key = { it.id }) { FlashCardRow(it, { onOpenCard(it.id) }, { viewModel.toggleArchive(it.id) }, { toDelete = it }) }
                }
            }
        }
    }

    toDelete?.let { ui ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.flash_delete_title)) },
            text = { Text(stringResource(R.string.flash_delete_message, ui.reference)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(ui.id); toDelete = null }, modifier = Modifier.testTag("flashConfirmDelete")) {
                    Text(stringResource(R.string.flash_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showAdd) {
        AddFlashCardSheet(
            viewModel = viewModel,
            onDismiss = { showAdd = false },
            onAdded = { result ->
                showAdd = false
                if (result.skippedVersions.isNotEmpty()) {
                    Toast.makeText(context, resources.getString(R.string.flash_skipped, result.skippedVersions.joinToString()), Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = textStyle(13, weight = FontWeight.SemiBold), color = AppTheme.colors.neutral50, modifier = modifier)
}

/** iOS `FlashCardRow`: reference, one line of the verse, archive / restore and delete. */
@Composable
private fun FlashCardRow(ui: FlashCardUi, onClick: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    val c = AppTheme.colors
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = c.surface0, modifier = Modifier.fillMaxWidth().testTag("flashRow")) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ui.reference, style = textStyle(15, weight = FontWeight.SemiBold), color = c.neutral100, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onArchive, modifier = Modifier.size(36.dp).testTag("flashArchive")) {
                    Icon(
                        if (ui.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        stringResource(if (ui.isArchived) R.string.flash_restore else R.string.flash_mark_completed),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp).testTag("flashDelete")) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.flash_delete), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            if (ui.verse.text.isNotBlank()) {
                Text(ui.verse.text, style = textStyle(12), color = c.neutral50, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// MARK: - Add (iOS AddFlashCardSheet)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFlashCardSheet(viewModel: FlashCardsViewModel, onDismiss: () -> Unit, onAdded: (FlashCardAddResult) -> Unit) {
    var reference by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf("") }
    var valid by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding().testTag("addFlashCardSheet"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.flash_new_title), style = textStyle(22, weight = FontWeight.Bold))
            Text(stringResource(R.string.flash_new_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BibleReferenceField(
                value = reference,
                onValueChange = { reference = it; message = null },
                config = BibleRefConfig.FlashCard,
                languages = viewModel.bibleRefLanguages,
                help = BibleRefHelp.Note,
                showAbbreviations = true,
                onValidationChange = { valid = it.isValid },
            )
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                placeholder = { Text(stringResource(R.string.flash_topic)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("flashTopic"),
            )
            message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("flashAddMessage")) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            val result = viewModel.add(reference.trim(), topic)
                            busy = false
                            when (result.status) {
                                FlashCardAddStatus.Created, FlashCardAddStatus.RestoredToActive -> onAdded(result)
                                // iOS closes the sheet without a word here (IOS_ISSUES L1).
                                FlashCardAddStatus.AlreadyActive -> message = R.string.flash_already
                                FlashCardAddStatus.Failed -> message = R.string.flash_add_failed
                            }
                        }
                    },
                    enabled = valid && !busy,
                    modifier = Modifier.weight(1f).testTag("flashAddSave"),
                ) { Text(stringResource(R.string.flash_add)) }
            }
        }
    }
}

// MARK: - Flip-through (iOS FlashCardDetailView .review)

/** One card, reference on the front; tap to flip to the verse; swipe to the next active card. */
@Composable
fun FlashCardDetailScreen(onBack: () -> Unit, viewModel: FlashCardsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val start = state.cards.firstOrNull { it.id == viewModel.startId }
    // iOS: a completed card is shown alone; otherwise every active card, newest first.
    val pages = remember(state, start?.isArchived) {
        if (start?.isArchived == true) listOf(start)
        else state.cards.filter { !it.isArchived }.let { live -> FlashCards.sorted(live.map { it.card }, FlashCardSort.ByDate).mapNotNull { c -> live.firstOrNull { it.id == c.id } } }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("flashCardDetail")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Text(start?.reference.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(if (state.loaded) R.string.flash_not_found else R.string.flash_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        val pager = rememberPagerState(initialPage = pages.indexOfFirst { it.id == viewModel.startId }.coerceAtLeast(0)) { pages.size }
        HorizontalPager(pager, key = { pages[it].id }, userScrollEnabled = pages.size > 1, modifier = Modifier.weight(1f)) { index ->
            FlipCard(pages[index])
        }
        if (pages.size > 1) {
            Text(
                stringResource(R.string.flash_card_n_of_m, pager.currentPage + 1, pages.size),
                style = textStyle(12), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp).testTag("flashPosition"),
            )
        }
    }
}

/** iOS `StyledCard`: front = reference (+ topic), back = reference and verse; a half turn on tap. */
@Composable
private fun FlipCard(ui: FlashCardUi) {
    var flipped by rememberSaveable(ui.id) { mutableStateOf(false) }
    val angle by animateFloatAsState(if (flipped) 180f else 0f, label = "flip")
    val density = LocalDensity.current.density
    val c = AppTheme.colors
    val brown = Color(0xFF8B5E3C)
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = { flipped = !flipped },
            shape = RoundedCornerShape(20.dp),
            color = c.surface0,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp).graphicsLayer { rotationY = angle; cameraDistance = 12f * density }.testTag("flashFlip"),
        ) {
            if (angle <= 90f) {
                Column(Modifier.fillMaxWidth().heightIn(min = 280.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ui.reference, style = textStyle(30, weight = FontWeight.Bold), color = brown, modifier = Modifier.testTag("flashFront"))
                    ui.card.topic?.let { Text(it, style = textStyle(15), color = Color.Gray) }
                }
            } else {
                // The back is drawn mirrored by the layer's rotation; turn it back.
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 280.dp).graphicsLayer { rotationY = 180f }.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                ) {
                    Text(ui.reference, style = textStyle(17, weight = FontWeight.Bold), color = brown)
                    MarkedText(ui.verse.annotated(), style = textStyle(26, 1.4f, FontWeight.SemiBold), color = c.neutral100, modifier = Modifier.testTag("flashBack"))
                }
            }
        }
    }
}

// MARK: - Verse deck (iOS FlashCardDetailView .display)

/**
 * The full-screen verse deck over the ocean photo: swipe between cards, tap to close, "⋯" turns the
 * launch deck off. [FlashCardsViewModel.deckLimit] caps the cards (5 on launch, all from ▶; IOS_ISSUES L2).
 */
@Composable
fun FlashCardDeckScreen(onClose: () -> Unit, viewModel: FlashCardsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pages = remember(state) {
        val live = state.cards.filter { !it.isArchived }
        val ordered = FlashCards.sorted(live.map { it.card }, FlashCardSort.ByDate).mapNotNull { c -> live.firstOrNull { it.id == c.id } }
        if (viewModel.deckLimit > 0) ordered.take(viewModel.deckLimit) else ordered
    }
    val pager = rememberPagerState { pages.size }
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.loaded, pages.isEmpty()) { if (state.loaded && pages.isEmpty()) onClose() }

    Box(Modifier.fillMaxSize().background(DeckDesign.scrimBase).testTag("flashDeck")) {
        Image(painterResource(R.drawable.verse_card_ocean_background), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(DeckDesign.scrimGradient))

        HorizontalPager(pager, key = { pages[it].id }, modifier = Modifier.fillMaxSize().testTag("flashDeckPager")) { index ->
            DeckPage(pages[index], onTap = { if (menuOpen) menuOpen = false else onClose() })
        }

        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = DeckDesign.sidePadding), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(Icons.Filled.Close, stringResource(R.string.bible_ref_close), onClose, "flashDeckClose")
            Spacer(Modifier.weight(1f))
            Box {
                CircleButton(Icons.Filled.MoreVert, stringResource(R.string.flash_more), { menuOpen = true }, "flashDeckMore")
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.flash_dont_show)) },
                        onClick = { menuOpen = false; viewModel.hideOnLaunch(); onClose() },
                        modifier = Modifier.testTag("flashDeckHide"),
                    )
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (pages.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    pages.indices.forEach { i ->
                        val active = i == pager.currentPage
                        Box(Modifier.size(if (active) 8.dp else 6.dp).background(Color.White.copy(alpha = if (active) 1f else 0.4f), CircleShape))
                    }
                }
            }
            Text(
                stringResource(R.string.flash_tap_close), style = textStyle(13, weight = FontWeight.Medium), color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 8.dp).clickable(onClick = onClose),
            )
        }
    }
}

/** Reference and verse, centred when short and scrollable when long. */
@Composable
private fun DeckPage(ui: FlashCardUi, onTap: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onTap)) {
        val screen = maxHeight
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = screen)
                    .statusBarsPadding()
                    .padding(start = DeckDesign.sidePadding, end = DeckDesign.trailingPadding, top = DeckDesign.controlSize + 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                Text(ui.reference, style = textStyle(17, weight = FontWeight.Bold), color = Color.White, modifier = Modifier.testTag("flashDeckRef"))
                MarkedText(ui.verse.annotated(), style = textStyle(26, 1.4f, FontWeight.SemiBold), color = Color.White, modifier = Modifier.testTag("flashDeckVerse"))
            }
        }
    }
}

@Composable
private fun CircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, tag: String) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = DeckDesign.scrimBase.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.size(DeckDesign.controlSize).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(18.dp)) }
    }
}
