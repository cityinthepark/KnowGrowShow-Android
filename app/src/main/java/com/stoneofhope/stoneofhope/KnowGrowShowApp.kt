package com.stoneofhope.stoneofhope

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.time.LocalDate
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import com.stoneofhope.stoneofhope.data.bible.BookChapter
import com.stoneofhope.stoneofhope.data.flashcard.FlashCards
import com.stoneofhope.stoneofhope.data.sermon.YouTubeLinkParser
import com.stoneofhope.stoneofhope.ui.sermon.AudioBarViewModel
import com.stoneofhope.stoneofhope.ui.sermon.CreateVideoSheet
import com.stoneofhope.stoneofhope.ui.sermon.FloatingAudioBar
import com.stoneofhope.stoneofhope.ui.sermon.IncomingShare
import com.stoneofhope.stoneofhope.ui.sermon.SermonDestination
import com.stoneofhope.stoneofhope.ui.sermon.SermonEditScreen
import com.stoneofhope.stoneofhope.ui.sermon.SermonNavigation
import com.stoneofhope.stoneofhope.ui.sermon.SermonNotice
import com.stoneofhope.stoneofhope.ui.sermon.SermonReaderScreen
import com.stoneofhope.stoneofhope.ui.sermon.SermonScreen
import com.stoneofhope.stoneofhope.ui.sermon.SermonViewModel
import com.stoneofhope.stoneofhope.ui.sermon.SeriesDetailScreen
import com.stoneofhope.stoneofhope.ui.sermon.SeriesDetailViewModel
import com.stoneofhope.stoneofhope.ui.sermon.VideoEntry
import com.stoneofhope.stoneofhope.ui.sermon.VideoScreen
import com.stoneofhope.stoneofhope.ui.sermon.VideoViewModel
import com.stoneofhope.stoneofhope.ui.ComingSoonScreen
import com.stoneofhope.stoneofhope.ui.RootViewModel
import com.stoneofhope.stoneofhope.ui.account.AccountScreen
import com.stoneofhope.stoneofhope.ui.account.CloudResetNoticeDialog
import com.stoneofhope.stoneofhope.data.chapter.ChapterVerse
import com.stoneofhope.stoneofhope.data.notes.NoteFilter
import com.stoneofhope.stoneofhope.ui.chapter.ChapterScreen
import com.stoneofhope.stoneofhope.ui.chapter.ChapterTab
import com.stoneofhope.stoneofhope.ui.chapter.ChapterVerseScreen
import com.stoneofhope.stoneofhope.ui.chapter.ChapterVerseViewModel
import com.stoneofhope.stoneofhope.ui.chapter.ChapterViewModel
import com.stoneofhope.stoneofhope.ui.flashcard.FlashCardDeckScreen
import com.stoneofhope.stoneofhope.ui.flashcard.FlashCardDetailScreen
import com.stoneofhope.stoneofhope.ui.flashcard.FlashCardListScreen
import com.stoneofhope.stoneofhope.ui.flashcard.FlashCardsViewModel
import com.stoneofhope.stoneofhope.ui.flashcard.LaunchDeckViewModel
import com.stoneofhope.stoneofhope.ui.main.MainScreen
import com.stoneofhope.stoneofhope.ui.profile.ProfileScreen
import com.stoneofhope.stoneofhope.ui.settings.BibleVersionCopyrightScreen
import com.stoneofhope.stoneofhope.ui.settings.BibleVersionsScreen
import com.stoneofhope.stoneofhope.ui.settings.OnlineResourceScreen
import com.stoneofhope.stoneofhope.ui.settings.OnlineResourceViewModel
import com.stoneofhope.stoneofhope.ui.settings.SyncConflictsScreen
import com.stoneofhope.stoneofhope.ui.tag.MyTagsScreen
import com.stoneofhope.stoneofhope.ui.tag.TagNotesScreen
import com.stoneofhope.stoneofhope.ui.tag.TagNotesViewModel
import com.stoneofhope.stoneofhope.ui.notes.NoteDetailsScreen
import com.stoneofhope.stoneofhope.ui.notes.NoteDetailsViewModel
import com.stoneofhope.stoneofhope.ui.notes.NoteListScreen
import com.stoneofhope.stoneofhope.ui.notes.NoteListViewModel
import com.stoneofhope.stoneofhope.ui.notes.NotesReviewScreen
import com.stoneofhope.stoneofhope.ui.notes.NotesReviewViewModel
import com.stoneofhope.stoneofhope.ui.reading.ReadingLogScreen
import com.stoneofhope.stoneofhope.ui.reading.ReadingLogViewModel
import com.stoneofhope.stoneofhope.ui.theme.KnowGrowShowThemeSurface
import com.stoneofhope.stoneofhope.ui.welcome.WelcomeScreen

private object Routes {
    const val Welcome = "welcome"
    const val Main = "main"
    const val Account = "account"
    const val SyncConflicts = "syncConflicts"
    const val ComingSoon = "comingSoon/{title}"
    const val FlashCards = "flashCards"
    const val Profile = "profile"
    const val MyTags = "myTags"
    const val BibleVersions = "bibleVersions"
    const val VersionCopyright = "bibleVersion/{abbr}"
    const val OnlineResource = "onlineResource/{${OnlineResourceViewModel.ARG_KEY}}"
    const val TagNotes = "tag/{${TagNotesViewModel.ARG_ID}}"
    const val FlashCardDetail = "flashCard/{${FlashCardsViewModel.ARG_ID}}"
    const val FlashDeck = "flashDeck/{${FlashCardsViewModel.ARG_LIMIT}}"
    const val ReadingLog = "readingLog/{${ReadingLogViewModel.ARG_BOOK_ORD}}"
    const val NoteList = "notes/{${NoteListViewModel.ARG_FILTER}}/{${NoteListViewModel.ARG_DAY}}"
    const val NoteDetails = "note/{${NoteDetailsViewModel.ARG_NOTE_ID}}"
    const val NotesReview = "review/{${NotesReviewViewModel.ARG_DAY}}"
    const val Chapter = "chapter/{${ChapterViewModel.ARG_BOOK_ORD}}/{${ChapterViewModel.ARG_CHAPTER_ORD}}/{${ChapterViewModel.ARG_TAB}}"
    const val VersePage = "chapterVerse/{${ChapterViewModel.ARG_BOOK_ORD}}/{${ChapterViewModel.ARG_CHAPTER_ORD}}/" +
        "{${ChapterVerseViewModel.ARG_VERSE_START}}/{${ChapterVerseViewModel.ARG_VERSE_END}}"
    const val Sermon = "sermon/{${SermonViewModel.ARG_ID}}?${SermonViewModel.ARG_BOOK}={${SermonViewModel.ARG_BOOK}}" +
        "&${SermonViewModel.ARG_CHAPTER}={${SermonViewModel.ARG_CHAPTER}}&${SermonViewModel.ARG_ENTRY}={${SermonViewModel.ARG_ENTRY}}" +
        "&${SermonViewModel.ARG_NOTICE}={${SermonViewModel.ARG_NOTICE}}"
    const val SermonReader = "sermonReader/{${SermonViewModel.ARG_ID}}"
    const val SeriesDetail = "seriesDetail/{${SeriesDetailViewModel.ARG_ID}}"
    const val SermonEdit = "sermonEdit/{${SermonViewModel.ARG_ID}}"
    const val Video = "video/{${VideoViewModel.ARG_ID}}?${SermonViewModel.ARG_NOTICE}={${SermonViewModel.ARG_NOTICE}}"

    fun tagNotes(id: String) = "tag/$id"

    fun onlineResource(churchKey: String) = "onlineResource/" + Uri.encode(churchKey)

    fun flashCardDetail(id: String) = "flashCard/$id"

    /** [limit] 0 = every active card (▶ on the list); the launch deck passes the iOS cap. */
    fun flashDeck(limit: Int) = "flashDeck/$limit"

    fun noteList(filter: NoteFilter, day: LocalDate?) = "notes/${filter.ordinal}/${day?.toEpochDay() ?: NoteListViewModel.NO_DAY}"

    fun noteDetails(noteId: String) = "note/$noteId"

    fun notesReview(day: LocalDate) = "review/${day.toEpochDay()}"

    fun readingLog(bookOrd: Int) = "readingLog/$bookOrd"

    fun chapter(bookOrd: Int, chapterOrd: Int, tab: ChapterTab) = "chapter/$bookOrd/$chapterOrd/${tab.ordinal}"

    fun chapterVerse(v: ChapterVerse) = "chapterVerse/${v.bookOrd}/${v.chapterOrd}/${v.verseStart}/${v.verseEnd}"

    fun comingSoon(title: String) = "comingSoon/" + Uri.encode(title)

    fun sermon(id: String, chapter: BookChapter? = null, entry: VideoEntry = VideoEntry.Default, notice: SermonNotice = SermonNotice.None) =
        "sermon/$id?book=${chapter?.bookOrd ?: -1}&chapter=${chapter?.chapterOrd ?: -1}&entry=${entry.ordinal}&notice=${notice.ordinal}"

    fun sermonReader(id: String) = "sermonReader/$id"

    fun seriesDetail(id: String) = "seriesDetail/$id"

    fun sermonEdit(id: String) = "sermonEdit/$id"

    fun video(youtubeID: String, notice: SermonNotice = SermonNotice.None) = "video/${Uri.encode(youtubeID)}?notice=${notice.ordinal}"
}

@Composable
fun KnowGrowShowApp(
    rootViewModel: RootViewModel = hiltViewModel(),
    audioBar: AudioBarViewModel = hiltViewModel(),
    launchDeck: LaunchDeckViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    // Decided once per launch: the welcome flow only until the user has made a choice.
    val startDestination = remember { if (rootViewModel.showWelcome) Routes.Welcome else Routes.Main }
    val sermonNav = remember(navController) {
        SermonNavigation(
            openSermon = { id, entry -> navController.navigate(Routes.sermon(id, entry = entry)) },
            openReader = { id -> navController.navigate(Routes.sermonReader(id)) },
            openVideo = { id -> navController.navigate(Routes.video(id)) },
        )
    }
    val openDestination: (SermonDestination) -> Unit = { dest ->
        when (dest) {
            is SermonDestination.Sermon -> navController.navigate(Routes.sermon(dest.id, notice = dest.notice))
            is SermonDestination.Video -> navController.navigate(Routes.video(dest.youtubeID, dest.notice))
        }
    }
    val backStack by navController.currentBackStackEntryAsState()
    val audio by audioBar.state.collectAsStateWithLifecycle()
    // On pushed pages the audio bar sits below the page (MainScreen puts it above the tab bar).
    val barBelowPage = audio.visible && backStack?.destination?.route.let { it != null && it != Routes.Main && it != Routes.Welcome }
    val share by IncomingShare.pending.collectAsStateWithLifecycle()

    // iOS pops the verse deck on every return to the foreground; here: first foreground and after 30+ min away (IOS_ISSUES L4).
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { launchDeck.onStop() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        scope.launch {
            if (!launchDeck.shouldShow()) return@launch
            val route = navController.currentDestination?.route
            if (route != null && route != Routes.Welcome && route != Routes.FlashDeck && IncomingShare.pending.value == null) {
                navController.navigate(Routes.flashDeck(FlashCards.LAUNCH_DECK_SIZE))
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = KnowGrowShowThemeSurface
    ) {
        Column(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.weight(1f).then(if (barBelowPage) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
            ) {
                composable(Routes.Welcome) {
                    // Like iOS, both choices land on the tabs; cloud sign-in lives under Settings.
                    WelcomeScreen(
                        onContinueLocal = {
                            rootViewModel.chooseLocalOnly()
                            navController.navigate(Routes.Main) { popUpTo(Routes.Welcome) { inclusive = true } }
                        },
                        onContinueRegister = {
                            rootViewModel.chooseCloud()
                            navController.navigate(Routes.Main) { popUpTo(Routes.Welcome) { inclusive = true } }
                        }
                    )
                }
                composable(Routes.Main) {
                    MainScreen(
                        onOpenPlaceholder = { title -> navController.navigate(Routes.comingSoon(title)) },
                        onOpenFlashCards = { navController.navigate(Routes.FlashCards) },
                        onOpenProfile = { navController.navigate(Routes.Profile) },
                        onOpenReadingLog = { bookOrd -> navController.navigate(Routes.readingLog(bookOrd)) },
                        onOpenChapter = { bookOrd, chapter, tab -> navController.navigate(Routes.chapter(bookOrd, chapter, tab)) },
                        onOpenNotes = { filter, day -> navController.navigate(Routes.noteList(filter, day)) },
                        onOpenReview = { day -> navController.navigate(Routes.notesReview(day)) },
                        onOpenNote = { id -> navController.navigate(Routes.noteDetails(id)) },
                        sermonNav = sermonNav,
                        onOpenDestination = openDestination,
                        onOpenSeries = { id -> navController.navigate(Routes.seriesDetail(id)) },
                        onOpenChurch = { key -> navController.navigate(Routes.onlineResource(key)) },
                        onOpenSyncConflicts = { navController.navigate(Routes.SyncConflicts) },
                    )
                }
                composable(
                    Routes.NoteList,
                    arguments = listOf(
                        navArgument(NoteListViewModel.ARG_FILTER) { type = NavType.IntType },
                        navArgument(NoteListViewModel.ARG_DAY) { type = NavType.LongType },
                    ),
                ) {
                    NoteListScreen(
                        onBack = { navController.popBackStack() },
                        onOpenNote = { id -> navController.navigate(Routes.noteDetails(id)) },
                        onOpenVersions = { navController.navigate(Routes.BibleVersions) },
                    )
                }
                composable(
                    Routes.NoteDetails,
                    arguments = listOf(navArgument(NoteDetailsViewModel.ARG_NOTE_ID) { type = NavType.StringType }),
                ) {
                    NoteDetailsScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    Routes.NotesReview,
                    arguments = listOf(navArgument(NotesReviewViewModel.ARG_DAY) { type = NavType.LongType }),
                ) {
                    NotesReviewScreen(onClose = { navController.popBackStack() })
                }
                composable(Routes.SyncConflicts) {
                    SyncConflictsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.Account) {
                    AccountScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    Routes.ReadingLog,
                    arguments = listOf(navArgument(ReadingLogViewModel.ARG_BOOK_ORD) { type = NavType.IntType }),
                ) {
                    ReadingLogScreen(
                        onBack = { navController.popBackStack() },
                        onOpenChapter = { bookOrd, chapter -> navController.navigate(Routes.chapter(bookOrd, chapter, ChapterTab.Sermons)) },
                    )
                }
                composable(
                    Routes.Chapter,
                    arguments = listOf(
                        navArgument(ChapterViewModel.ARG_BOOK_ORD) { type = NavType.IntType },
                        navArgument(ChapterViewModel.ARG_CHAPTER_ORD) { type = NavType.IntType },
                        navArgument(ChapterViewModel.ARG_TAB) { type = NavType.IntType },
                    ),
                ) {
                    ChapterScreen(
                        onBack = { navController.popBackStack() },
                        onOpenNote = { id -> navController.navigate(Routes.noteDetails(id)) },
                        onOpenVerse = { verse -> navController.navigate(Routes.chapterVerse(verse)) },
                        onOpenSermon = { id, chapter, entry -> navController.navigate(Routes.sermon(id, chapter, entry)) },
                    )
                }
                composable(
                    Routes.VersePage,
                    arguments = listOf(
                        navArgument(ChapterViewModel.ARG_BOOK_ORD) { type = NavType.IntType },
                        navArgument(ChapterViewModel.ARG_CHAPTER_ORD) { type = NavType.IntType },
                        navArgument(ChapterVerseViewModel.ARG_VERSE_START) { type = NavType.IntType },
                        navArgument(ChapterVerseViewModel.ARG_VERSE_END) { type = NavType.IntType },
                    ),
                ) {
                    ChapterVerseScreen(
                        onBack = { navController.popBackStack() },
                        onOpenNote = { id -> navController.navigate(Routes.noteDetails(id)) },
                    )
                }
                composable(
                    Routes.Sermon,
                    arguments = listOf(
                        navArgument(SermonViewModel.ARG_ID) { type = NavType.StringType },
                        navArgument(SermonViewModel.ARG_BOOK) { type = NavType.IntType; defaultValue = -1 },
                        navArgument(SermonViewModel.ARG_CHAPTER) { type = NavType.IntType; defaultValue = -1 },
                        navArgument(SermonViewModel.ARG_ENTRY) { type = NavType.IntType; defaultValue = 0 },
                        navArgument(SermonViewModel.ARG_NOTICE) { type = NavType.IntType; defaultValue = 0 },
                    ),
                ) {
                    SermonScreen(
                        onBack = { navController.popBackStack() },
                        onOpenNote = { id -> navController.navigate(Routes.noteDetails(id)) },
                        onEdit = { id -> navController.navigate(Routes.sermonEdit(id)) },
                        onOpenVersions = { navController.navigate(Routes.BibleVersions) },
                    )
                }
                composable(Routes.SermonReader, arguments = listOf(navArgument(SermonViewModel.ARG_ID) { type = NavType.StringType })) {
                    SermonReaderScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSermon = { id, entry -> navController.navigate(Routes.sermon(id, entry = entry)) },
                    )
                }
                composable(Routes.SeriesDetail, arguments = listOf(navArgument(SeriesDetailViewModel.ARG_ID) { type = NavType.StringType })) {
                    SeriesDetailScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SermonEdit, arguments = listOf(navArgument(SermonViewModel.ARG_ID) { type = NavType.StringType })) {
                    SermonEditScreen(
                        onBack = { navController.popBackStack() },
                        // The sermon page is gone too.
                        onDeleted = { if (!navController.popBackStack(Routes.Sermon, inclusive = true)) navController.popBackStack() },
                    )
                }
                composable(
                    Routes.Video,
                    arguments = listOf(
                        navArgument(VideoViewModel.ARG_ID) { type = NavType.StringType },
                        navArgument(SermonViewModel.ARG_NOTICE) { type = NavType.IntType; defaultValue = 0 },
                    ),
                ) {
                    VideoScreen(
                        onBack = { navController.popBackStack() },
                        onOpenNote = { id -> navController.navigate(Routes.noteDetails(id)) },
                    )
                }
                composable(Routes.Profile) {
                    ProfileScreen(
                        onBack = { navController.popBackStack() },
                        onOpenTags = { navController.navigate(Routes.MyTags) },
                        onOpenVersions = { navController.navigate(Routes.BibleVersions) },
                        onOpenAccount = { navController.navigate(Routes.Account) },
                    )
                }
                composable(Routes.BibleVersions) {
                    BibleVersionsScreen(onBack = { navController.popBackStack() }, onOpenCopyright = { abbr -> navController.navigate("bibleVersion/$abbr") })
                }
                composable(Routes.VersionCopyright, arguments = listOf(navArgument("abbr") { type = NavType.StringType })) { entry ->
                    BibleVersionCopyrightScreen(abbr = entry.arguments?.getString("abbr").orEmpty(), onBack = { navController.popBackStack() })
                }
                composable(Routes.OnlineResource, arguments = listOf(navArgument(OnlineResourceViewModel.ARG_KEY) { type = NavType.StringType })) {
                    OnlineResourceScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.MyTags) {
                    MyTagsScreen(onBack = { navController.popBackStack() }, onOpenTag = { id -> navController.navigate(Routes.tagNotes(id)) })
                }
                composable(Routes.TagNotes, arguments = listOf(navArgument(TagNotesViewModel.ARG_ID) { type = NavType.StringType })) {
                    TagNotesScreen(
                        onBack = { navController.popBackStack() },
                        onOpenNote = { id -> navController.navigate(Routes.noteDetails(id)) },
                        onOpenSermon = { id -> navController.navigate(Routes.sermon(id)) },
                        onOpenVideo = { id -> navController.navigate(Routes.video(id)) },
                    )
                }
                composable(Routes.FlashCards) {
                    FlashCardListScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCard = { id -> navController.navigate(Routes.flashCardDetail(id)) },
                        onStartReview = { navController.navigate(Routes.flashDeck(0)) },
                        onOpenVersions = { navController.navigate(Routes.BibleVersions) },
                    )
                }
                composable(Routes.FlashCardDetail, arguments = listOf(navArgument(FlashCardsViewModel.ARG_ID) { type = NavType.StringType })) {
                    FlashCardDetailScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.FlashDeck, arguments = listOf(navArgument(FlashCardsViewModel.ARG_LIMIT) { type = NavType.IntType })) {
                    FlashCardDeckScreen(onClose = { navController.popBackStack() })
                }
                composable(Routes.ComingSoon) { entry ->
                    ComingSoonScreen(title = entry.arguments?.getString("title").orEmpty(), onBack = { navController.popBackStack() })
                }
            }
            if (barBelowPage) FloatingAudioBar(Modifier.navigationBarsPadding())
        }
    }

    // iOS AppRootView: one-time "signed out because the cloud was cleared elsewhere" alert.
    CloudResetNoticeDialog()

    // iOS: a link shared from the YouTube app opens the new-video sheet over whatever is on screen.
    share?.let { shared ->
        val url = YouTubeLinkParser.firstYouTubeURLString(shared.text)
        if (url == null) {
            val context = LocalContext.current
            LaunchedEffect(shared) {
                Toast.makeText(context, R.string.share_not_youtube, Toast.LENGTH_SHORT).show()
                IncomingShare.consume()
            }
        } else {
            CreateVideoSheet(
                onDismiss = IncomingShare::consume,
                onCreated = { IncomingShare.consume(); openDestination(it) },
                initialUrl = url,
                sharedTitle = shared.title,
            )
        }
    }
}
