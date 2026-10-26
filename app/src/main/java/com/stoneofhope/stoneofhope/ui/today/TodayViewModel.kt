package com.stoneofhope.stoneofhope.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.local.dao.FlashCardDao
import com.stoneofhope.stoneofhope.data.reading.ReadingModel
import com.stoneofhope.stoneofhope.data.reading.ReadingRepository
import com.stoneofhope.stoneofhope.data.repository.NoteRepository
import com.stoneofhope.stoneofhope.data.today.TodayActivity
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.data.today.TodayRepository
import com.stoneofhope.stoneofhope.ui.bible.BibleRefLanguages
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val reading: ReadingRepository,
    private val today: TodayRepository,
    private val notes: NoteRepository,
    private val clock: Clock,
    flashCards: FlashCardDao,
    bibleVersions: BibleVersionPreferences,
) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val _day = MutableStateFlow(currentDay())
    private val _sort = MutableStateFlow(ReadingModel.Sort.LastRead)

    /** Chip windows stay put while the user taps chips; reset when the day changes. */
    private val chipWindowStarts = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    /** "Today". Unlike iOS (fixed when the view is built) it rolls over at midnight and on resume. */
    val day: StateFlow<LocalDate> = _day.asStateFlow()

    /** Not persisted, same as iOS. */
    val sort: StateFlow<ReadingModel.Sort> = _sort.asStateFlow()

    val carousel: StateFlow<List<ReadingModel.CarouselBook>> =
        combine(_day, _sort, ::Pair)
            .flatMapLatest { (d, s) -> reading.observeCarousel(d, zone, s, chipWindowStarts) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activity: StateFlow<TodayActivity> =
        _day.flatMapLatest { d -> today.observeActivity(d, zone, isToday = true) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayActivity.Empty)

    val activeFlashCards: StateFlow<Int> =
        flashCards.observeActiveCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Book-name languages for the reference fields, from the selected Bible versions. */
    val bibleRefLanguages: BibleRefLanguages = bibleVersions.selectedVersions().let { selected ->
        BibleRefLanguages(
            matching = BibleVersions.bookMatchingLanguages(selected),
            display = BibleVersions.primaryBookDisplayLanguage(selected),
            secondary = BibleVersions.secondaryBookDisplayLanguage(selected),
        )
    }

    val tagNames: StateFlow<List<String>> =
        notes.observeTagNames().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            while (true) {
                val now = clock.instant().atZone(zone)
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
                delay(Duration.between(now, nextMidnight).toMillis() + 1_000)
                refreshDay()
            }
        }
    }

    /** Call on resume: a device asleep past midnight doesn't run the timer above. */
    fun refreshDay() {
        val day = currentDay()
        if (day != _day.value) {
            chipWindowStarts.clear()
            _day.value = day
        }
    }

    fun toggleSort() {
        _sort.value = if (_sort.value == ReadingModel.Sort.LastRead) ReadingModel.Sort.BookOrder else ReadingModel.Sort.LastRead
    }

    fun toggleChapterRead(bookOrd: Int, chapter: Int) {
        viewModelScope.launch { reading.toggleChapterRead(bookOrd, chapter, zone) }
    }

    fun savePrayer(content: String, tagName: String?, bibleRefText: String?) {
        viewModelScope.launch { notes.savePrayerNote(content, tagName, bibleRefText) }
    }

    fun saveNote(content: String, bibleRefText: String?) {
        viewModelScope.launch { notes.saveNoteFromContent(content, bibleRefText, zone = zone) }
    }

    private fun currentDay(): LocalDate = clock.instant().atZone(zone).toLocalDate()
}
