package com.stoneofhope.stoneofhope.ui.flashcard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.flashcard.FlashCardAddResult
import com.stoneofhope.stoneofhope.data.flashcard.FlashCardSort
import com.stoneofhope.stoneofhope.data.flashcard.FlashCards
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.FlashCardEntity
import com.stoneofhope.stoneofhope.data.notes.MarkedVerse
import com.stoneofhope.stoneofhope.data.notes.VerseDisplay
import com.stoneofhope.stoneofhope.data.notes.VerseMarks
import com.stoneofhope.stoneofhope.data.repository.FlashCardRepository
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.data.settings.FlashCardPreferences
import com.stoneofhope.stoneofhope.ui.bible.BibleRefLanguages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One card as the screens show it: its reference and the verse text frozen in `userVerses`, marks included. */
data class FlashCardUi(val card: FlashCardEntity, val reference: String, val verse: MarkedVerse) {
    val id: String get() = card.id
    val isArchived: Boolean get() = card.isArchived
}

data class FlashCardsState(val cards: List<FlashCardUi> = emptyList(), val loaded: Boolean = false)

/** The note "⋯" sheet's "Add to Flash Card" (iOS `NoteCellView.bibleVerseBadgeMenu`). */
@HiltViewModel
class FlashCardAdderViewModel @Inject constructor(private val repository: FlashCardRepository) : ViewModel() {
    suspend fun addFromBibleVerseNote(noteId: String): FlashCardAddResult = repository.addFromBibleVerseNote(noteId)
}

/** iOS `FlashCardListViewModel`, shared by the list, the flip-through page and the verse deck. */
@HiltViewModel
class FlashCardsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    db: AppDatabase,
    private val repository: FlashCardRepository,
    private val preferences: FlashCardPreferences,
    private val bibleVersions: BibleVersionPreferences,
) : ViewModel() {

    /** The card a detail page opened on (null on the list and the deck). */
    val startId: String? = savedState.get<String>(ARG_ID)?.takeIf { it.isNotEmpty() && it != NONE }

    /** The deck's card limit; 0 = every active card. */
    val deckLimit: Int = savedState.get<Int>(ARG_LIMIT) ?: 0

    private val _sort = MutableStateFlow(FlashCardSort.ByDate)
    val sort: StateFlow<FlashCardSort> = _sort

    private val verseData = combine(db.userVerseDao().observeAll(), db.indexedTextDao().observeActiveVerseMarks()) { v, m ->
        v.associateBy { it.id } to VerseMarks.byVerseId(m)
    }

    val state: StateFlow<FlashCardsState> = combine(repository.observeLive(), verseData) { cards, (versesById, marks) ->
        FlashCardsState(
            cards.map { card ->
                val verse = FlashCards.anchor(card)?.let { VerseDisplay.resolve(it, versesById, marks) }?.combined?.trimmed() ?: MarkedVerse("")
                FlashCardUi(card, FlashCards.displayRef(card).orEmpty(), verse)
            },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FlashCardsState())

    /** Selected versions as the list's top-right label ("KJV, 和合本"). */
    val versionsLabel: String get() = bibleVersions.selectedVersions().joinToString(", ") { BibleVersions.toDisplay(it) }

    val bibleRefLanguages: BibleRefLanguages = bibleVersions.selectedVersions().let { selected ->
        BibleRefLanguages(
            matching = BibleVersions.bookMatchingLanguages(selected),
            display = BibleVersions.primaryBookDisplayLanguage(selected),
            secondary = BibleVersions.secondaryBookDisplayLanguage(selected),
        )
    }

    fun toggleSort() = _sort.update { if (it == FlashCardSort.ByDate) FlashCardSort.ByBook else FlashCardSort.ByDate }

    fun toggleArchive(id: String) {
        viewModelScope.launch { repository.toggleArchive(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    suspend fun add(reference: String, topic: String?): FlashCardAddResult = repository.add(reference, topic)

    /** The deck's "Don't show recent verses". */
    fun hideOnLaunch() {
        preferences.showOnLaunch = false
    }

    companion object {
        const val ARG_ID = "id"
        const val ARG_LIMIT = "limit"
        const val NONE = "-"
    }
}
