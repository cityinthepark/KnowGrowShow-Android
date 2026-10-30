package com.stoneofhope.stoneofhope.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.church.ChurchImportResult
import com.stoneofhope.stoneofhope.data.church.ChurchProfile
import com.stoneofhope.stoneofhope.data.church.ChurchProfileStore
import com.stoneofhope.stoneofhope.data.church.ChurchSermonImporter
import com.stoneofhope.stoneofhope.data.church.OnlineChurches
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Settings "Online Resources" rows: every enabled church, the cached list first, then the catalog. */
@HiltViewModel
class OnlineResourcesViewModel @Inject constructor(private val store: ChurchProfileStore, churches: OnlineChurches) : ViewModel() {
    private val _churches = MutableStateFlow(store.cached())
    val churches: StateFlow<List<ChurchProfile>> = _churches

    /** The primary church's key, for its badge. */
    val primaryKey: StateFlow<String?> = churches.observe()
        .map { rows -> rows.firstOrNull { it.isPrimary }?.churchKey }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { _churches.value = store.refresh() }
    }
}

/** What the result dialog says; [counts] (series, sermons) fill `online_import_done`. */
data class ImportMessage(@StringRes val text: Int, val counts: Pair<Int, Int>? = null, @StringRes val retryHint: Int? = null)

/**
 * iOS `importAlertTitle`, plus the cases iOS reports as "No new sermons found." (IOS_ISSUES M3):
 * no connection, and files left to retry (M1).
 */
fun ChurchImportResult.message(): ImportMessage = when (this) {
    ChurchImportResult.Unreachable -> ImportMessage(R.string.online_import_unreachable)
    ChurchImportResult.NotImportable -> ImportMessage(R.string.online_import_unavailable)
    is ChurchImportResult.Done -> {
        val hint = R.string.online_import_incomplete.takeIf { incompleteFiles.isNotEmpty() }
        if (nothingNew) ImportMessage(R.string.online_import_nothing_new, retryHint = hint)
        else ImportMessage(R.string.online_import_done, series to sermons, hint)
    }
}

data class OnlineResourceState(
    val profile: ChurchProfile? = null,
    /** Offers "Import Latest Updates" instead of "Import all Sermons". */
    val imported: Boolean = false,
    val importing: Boolean = false,
    val message: ImportMessage? = null,
    /** In "My churches" (imported here or, after C4, on another device). */
    val followed: Boolean = false,
    val primary: Boolean = false,
)

/** iOS `OnlineResourceDetailView`. Argument: [ARG_KEY], the church key. */
@HiltViewModel
class OnlineResourceViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val store: ChurchProfileStore,
    private val importer: ChurchSermonImporter,
    private val churches: OnlineChurches,
) : ViewModel() {
    private val churchKey: String = checkNotNull(savedState.get<String>(ARG_KEY))

    private val _state = MutableStateFlow(OnlineResourceState(profile = store.profile(churchKey)))
    val state: StateFlow<OnlineResourceState> = _state

    init {
        viewModelScope.launch { _state.update { it.copy(imported = importer.hasImported(churchKey)) } }
        viewModelScope.launch {
            churches.observe().collect { rows ->
                val row = rows.firstOrNull { it.churchKey == churchKey }
                _state.update { it.copy(followed = row != null, primary = row?.isPrimary == true) }
            }
        }
    }

    /** Moves the primary mark here (the old primary loses it). */
    fun setPrimary() {
        viewModelScope.launch { churches.setPrimary(churchKey) }
    }

    fun import() {
        if (_state.value.importing) return
        val updates = _state.value.imported
        _state.update { it.copy(importing = true) }
        viewModelScope.launch {
            val result = if (updates) importer.importUpdates(churchKey) else importer.importAll(churchKey)
            val imported = importer.hasImported(churchKey)
            _state.update { it.copy(importing = false, imported = imported, message = result.message()) }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    companion object {
        const val ARG_KEY = "churchKey"
    }
}
