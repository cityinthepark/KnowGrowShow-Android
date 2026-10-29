package com.stoneofhope.stoneofhope.ui.sermon

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Text shared into the app (iOS `YouTubeShareInbox`). */
data class SharedLink(val text: String, val title: String)

/** Holds a share until the app shows the new-video sheet for it. */
object IncomingShare {
    private val _pending = MutableStateFlow<SharedLink?>(null)
    val pending: StateFlow<SharedLink?> = _pending.asStateFlow()

    /** The YouTube app shares the link as text and "Watch "Title" on YouTube" as the subject. */
    fun offer(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) return
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val title = Regex("^Watch \"(.+)\" on YouTube$").find(subject)?.groupValues?.get(1) ?: subject
        _pending.value = SharedLink(text, title)
        // Handled: a configuration change must not offer it again.
        intent.action = null
    }

    fun consume() { _pending.value = null }
}
