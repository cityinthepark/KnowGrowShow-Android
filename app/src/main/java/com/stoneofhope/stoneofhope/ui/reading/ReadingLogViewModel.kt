package com.stoneofhope.stoneofhope.ui.reading

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.data.reading.ReadingModel
import com.stoneofhope.stoneofhope.data.reading.ReadingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** iOS `BibleViewModel` + `BibleReadingService` calls used by `BibleBookListView`. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReadingLogViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val reading: ReadingRepository,
    private val clock: Clock,
) : ViewModel() {

    /**
     * The book the screen opens on (expanded and scrolled to), from the navigation argument; null for
     * [NO_BOOK], which opens at the top with every book collapsed.
     */
    val initialBookOrd: Int? = savedState.get<Int>(ARG_BOOK_ORD)?.takeIf { it in 1..66 }

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val _day = MutableStateFlow(currentDay())
    val day: StateFlow<LocalDate> = _day.asStateFlow()

    val books: StateFlow<Map<Int, ReadingModel.BookLog>> =
        _day.flatMapLatest { reading.observeBookLogs(it, zone) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun refreshDay() {
        _day.value = currentDay()
    }

    fun toggleChapterRead(bookOrd: Int, chapter: Int) {
        viewModelScope.launch { reading.toggleChapterRead(bookOrd, chapter, zone) }
    }

    fun restart(bookOrd: Int) {
        viewModelScope.launch { reading.restartProgress(bookOrd) }
    }

    fun canRestart(log: ReadingModel.BookLog): Boolean = log.canRestart(_day.value, zone)

    private fun currentDay(): LocalDate = clock.instant().atZone(zone).toLocalDate()

    companion object {
        const val ARG_BOOK_ORD = "bookOrd"

        /** Opens the list to pick a book (Today's "+"), with nothing expanded. */
        const val NO_BOOK = 0
    }
}
