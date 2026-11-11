package com.stoneofhope.stoneofhope.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.stoneofhope.stoneofhope.data.bible.BibleLanguage
import com.stoneofhope.stoneofhope.data.bible.BibleVersionSelection
import com.stoneofhope.stoneofhope.data.bible.BibleVersions
import com.stoneofhope.stoneofhope.data.bible.VersionToggle
import com.stoneofhope.stoneofhope.data.sermon.ScriptCapturePreferences
import com.stoneofhope.stoneofhope.data.settings.AppearancePreferences
import com.stoneofhope.stoneofhope.data.settings.BibleVersionPreferences
import com.stoneofhope.stoneofhope.data.settings.FlashCardPreferences
import com.stoneofhope.stoneofhope.data.settings.FontSizePreference
import com.stoneofhope.stoneofhope.data.settings.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** iOS `SettingsView`'s "App Settings" section. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appearance: AppearancePreferences,
    private val flashCards: FlashCardPreferences,
    private val script: ScriptCapturePreferences,
    db: AppDatabase,
) : ViewModel() {
    /** iOS `pendingSyncConflictCount`: the red badge on the Sync Conflicts row. */
    val pendingConflicts: StateFlow<Int> = db.syncConflictDao().observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val theme: StateFlow<ThemePreference> = appearance.theme
    val fontSize: StateFlow<FontSizePreference> = appearance.fontSize

    private val _showOnLaunch = MutableStateFlow(flashCards.showOnLaunch)
    val showOnLaunch: StateFlow<Boolean> = _showOnLaunch

    private val _scriptSeconds = MutableStateFlow(script.seconds)
    val scriptSeconds: StateFlow<Int> = _scriptSeconds

    fun setTheme(value: ThemePreference) = appearance.setTheme(value)

    fun setFontSize(value: FontSizePreference) = appearance.setFontSize(value)

    fun setShowOnLaunch(value: Boolean) {
        flashCards.showOnLaunch = value
        _showOnLaunch.value = value
    }

    fun setScriptSeconds(value: Int) {
        script.seconds = value
        _scriptSeconds.value = script.seconds
    }
}

data class BibleVersionsState(val selected: List<String>)

/** iOS `AvailableVersionsView`: the selected versions, up to two, first = primary. */
@HiltViewModel
class BibleVersionsViewModel @Inject constructor(private val prefs: BibleVersionPreferences) : ViewModel() {
    private val _selected = MutableStateFlow(prefs.selectedVersions())
    val selected: StateFlow<List<String>> = _selected

    fun toggle(abbr: String): VersionToggle {
        val result = BibleVersionSelection.toggle(_selected.value, abbr, BibleLanguage.current())
        if (result is VersionToggle.Changed) {
            prefs.setSelectedVersions(result.selected)
            _selected.value = result.selected
        }
        return result
    }
}

/** The text of iOS `getSelectedVersionsDisplayString()`: "KJV, 和合本". */
fun BibleVersionPreferences.displayString(): String = selectedVersions().joinToString(", ") { BibleVersions.toDisplay(it) }
