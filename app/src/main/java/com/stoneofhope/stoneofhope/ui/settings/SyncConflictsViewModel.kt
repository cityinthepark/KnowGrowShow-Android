package com.stoneofhope.stoneofhope.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.SyncConflictEntity
import com.stoneofhope.stoneofhope.data.model.ConflictResolution
import com.stoneofhope.stoneofhope.data.sync.SyncConflictResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** iOS `SyncConflictListView` + `ConflictResolutionSheet`: pending conflicts, and resolving the selected one. */
@HiltViewModel
class SyncConflictsViewModel @Inject constructor(
    db: AppDatabase,
    private val resolver: SyncConflictResolver,
) : ViewModel() {

    /** Unresolved conflicts, most recently detected first. Resolved ones leave the list (iOS shows only these). */
    val conflicts: StateFlow<List<SyncConflictEntity>?> = db.syncConflictDao().observeUnresolved()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _resolving = MutableStateFlow(false)
    val resolving: StateFlow<Boolean> = _resolving.asStateFlow()

    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error.asStateFlow()

    fun select(id: String?) {
        _selectedId.value = id
        _error.value = null
    }

    fun resolve(resolution: ConflictResolution) {
        val id = _selectedId.value ?: return
        if (_resolving.value) return
        _resolving.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                resolver.resolve(id, resolution)
                _selectedId.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: SyncConflictResolver.ResolveException) {
                _error.value = when (e) {
                    SyncConflictResolver.ResolveException.NotSignedIn -> R.string.sync_conflict_err_signed_out
                    SyncConflictResolver.ResolveException.RemoteDocumentMissing -> R.string.sync_conflict_err_missing
                    SyncConflictResolver.ResolveException.Unreadable -> R.string.sync_conflict_err_unreadable
                }
            } catch (e: Exception) {
                _error.value = R.string.sync_conflict_err_network
            } finally {
                _resolving.value = false
            }
        }
    }
}
