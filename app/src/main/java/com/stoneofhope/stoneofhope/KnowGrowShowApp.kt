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
import com.stoneofhope.stoneofhope.ui.ComingSoonScreen
import com.stoneofhope.stoneofhope.ui.RootViewModel
import com.stoneofhope.stoneofhope.ui.account.AccountScreen
import com.stoneofhope.stoneofhope.data.chapter.ChapterVerse
import com.stoneofhope.stoneofhope.data.notes.NoteFilter
import com.stoneofhope.stoneofhope.ui.chapter.ChapterScreen
import com.stoneofhope.stoneofhope.ui.chapter.ChapterTab
import com.stoneofhope.stoneofhope.ui.chapter.ChapterVerseScreen
import com.stoneofhope.stoneofhope.ui.chapter.ChapterVerseViewModel
import com.stoneofhope.stoneofhope.ui.chapter.ChapterViewModel
import com.stoneofhope.stoneofhope.ui.main.MainScreen
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
    const val ComingSoon = "comingSoon/{title}"
    const val ReadingLog = "readingLog/{${ReadingLogViewModel.ARG_BOOK_ORD}}"
    const val NoteList = "notes/{${NoteListViewModel.ARG_FILTER}}/{${NoteListViewModel.ARG_DAY}}"
    const val NoteDetails = "note/{${NoteDetailsViewModel.ARG_NOTE_ID}}"
    const val NotesReview = "review/{${NotesReviewViewModel.ARG_DAY}}"
    const val Chapter = "chapter/{${ChapterViewModel.ARG_BOOK_ORD}}/{${ChapterViewModel.ARG_CHAPTER_ORD}}/{${ChapterViewModel.ARG_TAB}}"
    const val VersePage = "chapterVerse/{${ChapterViewModel.ARG_BOOK_ORD}}/{${ChapterViewModel.ARG_CHAPTER_ORD}}/" +
        "{${ChapterVerseViewModel.ARG_VERSE_START}}/{${ChapterVerseViewModel.ARG_VERSE_END}}"

    fun noteList(filter: NoteFilter, day: LocalDate?) = "notes/${filter.ordinal}/${day?.toEpochDay() ?: NoteListViewModel.NO_DAY}"

    fun noteDetails(noteId: String) = "note/$noteId"

    fun notesReview(day: LocalDate) = "review/${day.toEpochDay()}"

    fun readingLog(bookOrd: Int) = "readingLog/$bookOrd"

    fun chapter(bookOrd: Int, chapterOrd: Int, tab: ChapterTab) = "chapter/$bookOrd/$chapterOrd/${tab.ordinal}"

    fun chapterVerse(v: ChapterVerse) = "chapterVerse/${v.bookOrd}/${v.chapterOrd}/${v.verseStart}/${v.verseEnd}"

    fun comingSoon(title: String) = "comingSoon/" + Uri.encode(title)
}

@Composable
fun KnowGrowShowApp(rootViewModel: RootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    // Decided once per launch: the welcome flow only until the user has made a choice.
    val startDestination = remember { if (rootViewModel.showWelcome) Routes.Welcome else Routes.Main }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = KnowGrowShowThemeSurface
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination
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
                    onOpenAccount = { navController.navigate(Routes.Account) },
                    onOpenPlaceholder = { title -> navController.navigate(Routes.comingSoon(title)) },
                    onOpenReadingLog = { bookOrd -> navController.navigate(Routes.readingLog(bookOrd)) },
                    onOpenChapter = { bookOrd, chapter, tab -> navController.navigate(Routes.chapter(bookOrd, chapter, tab)) },
                    onOpenNotes = { filter, day -> navController.navigate(Routes.noteList(filter, day)) },
                    onOpenReview = { day -> navController.navigate(Routes.notesReview(day)) },
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
            composable(Routes.ComingSoon) { entry ->
                ComingSoonScreen(title = entry.arguments?.getString("title").orEmpty(), onBack = { navController.popBackStack() })
            }
        }
    }
}
