package com.stoneofhope.stoneofhope.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Upload shortly after an edit instead of waiting for the app to go to the background. [dirtyCount] re-emits
 * whenever the rows that sync change; after [delayMs] without a new emission, and only while something is
 * still waiting to upload, [push] runs for the signed-in account. A burst of saves therefore becomes one push
 * (and [CloudSync] already merges overlapping requests). Nothing is retried here: a failed or skipped push
 * leaves the rows dirty and the usual triggers (foreground, network back, background) pick them up.
 */
object EditPushTrigger {
    const val DEFAULT_DELAY_MS = 3_000L

    @OptIn(FlowPreview::class)
    fun start(
        scope: CoroutineScope,
        dirtyCount: Flow<Int>,
        delayMs: Long = DEFAULT_DELAY_MS,
        currentUid: () -> String?,
        push: suspend (String) -> Unit,
    ): Job = scope.launch {
        dirtyCount
            .filter { it > 0 }
            .debounce(delayMs)
            .collect { currentUid()?.let { uid -> push(uid) } }
    }
}
