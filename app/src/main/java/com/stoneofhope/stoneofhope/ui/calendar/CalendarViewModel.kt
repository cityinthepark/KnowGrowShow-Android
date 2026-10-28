package com.stoneofhope.stoneofhope.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.data.notes.NoteFilter
import com.stoneofhope.stoneofhope.data.reading.ReadingRepository
import com.stoneofhope.stoneofhope.data.today.DayMarks
import com.stoneofhope.stoneofhope.data.today.TodayActivity
import com.stoneofhope.stoneofhope.data.today.TodayRepository
import com.stoneofhope.stoneofhope.data.today.filtered
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** iOS `CalendarSummaryView`: a month of day marks, and the selected day's activity below it. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: TodayRepository,
    private val reading: ReadingRepository,
    private val clock: Clock,
) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val _today = MutableStateFlow(currentDay())
    private val _month = MutableStateFlow(YearMonth.from(_today.value))
    private val _selected = MutableStateFlow(_today.value)
    private val _filter = MutableStateFlow(NoteFilter.All)

    val today: StateFlow<LocalDate> = _today.asStateFlow()
    val month: StateFlow<YearMonth> = _month.asStateFlow()
    val selected: StateFlow<LocalDate> = _selected.asStateFlow()
    val filter: StateFlow<NoteFilter> = _filter.asStateFlow()

    val marks: StateFlow<Map<LocalDate, DayMarks>> =
        combine(_month, _today) { m, t -> m to t }
            .flatMapLatest { (m, t) -> repository.observeMonth(m, t, zone) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The selected day's Today activity, narrowed by the type menu (iOS `TodayActivityView(forDate:typeFilter:)`). */
    val activity: StateFlow<TodayActivity> =
        combine(_selected, _today) { d, t -> d to t }
            .flatMapLatest { (d, t) -> repository.observeActivity(d, zone, isToday = d == t) }
            .combine(_filter) { a, f -> a.filtered(f) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayActivity.Empty)

    val canGoToNextMonth: Boolean get() = _month.value < YearMonth.from(_today.value)

    fun previousMonth() { _month.value = _month.value.minusMonths(1) }

    /** iOS stops at the current month. */
    fun nextMonth() { if (canGoToNextMonth) _month.value = _month.value.plusMonths(1) }

    fun goToToday() {
        _selected.value = _today.value
        _month.value = YearMonth.from(_today.value)
    }

    /** Days after today can't be picked (iOS greys them out but still selects them: IOS_ISSUES I2). */
    fun select(day: LocalDate) {
        if (day.isAfter(_today.value)) return
        _selected.value = day
        _month.value = YearMonth.from(day)
    }

    /** iOS: tapping "Summary for …" brings the calendar back to the selected day's month. */
    fun showSelectedMonth() { _month.value = YearMonth.from(_selected.value) }

    fun setFilter(value: NoteFilter) { _filter.value = value }

    /** "Unread" on today's chapter rows. */
    fun toggleChapterRead(bookOrd: Int, chapter: Int) {
        viewModelScope.launch { reading.toggleChapterRead(bookOrd, chapter, zone) }
    }

    /** Midnight / back in the foreground: a calendar left on today moves on to the new today (IOS_ISSUES I4). */
    fun refreshDay() {
        val now = currentDay()
        val old = _today.value
        if (now == old) return
        _today.value = now
        if (_selected.value == old) _selected.value = now
        if (_month.value == YearMonth.from(old)) _month.value = YearMonth.from(now)
    }

    private fun currentDay(): LocalDate = clock.instant().atZone(zone).toLocalDate()
}
