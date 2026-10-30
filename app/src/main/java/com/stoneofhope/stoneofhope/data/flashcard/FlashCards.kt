package com.stoneofhope.stoneofhope.data.flashcard

import com.stoneofhope.stoneofhope.data.bible.BibleAnchor
import com.stoneofhope.stoneofhope.data.bible.BibleFormatter
import com.stoneofhope.stoneofhope.data.local.entity.FlashCardEntity

/** iOS `FlashCardSortMode`. */
enum class FlashCardSort { ByDate, ByBook }

/** iOS `FlashCardAddResult.Status`; [AlreadyActive] is the one iOS never tells the user about (IOS_ISSUES L1). */
enum class FlashCardAddStatus { Created, RestoredToActive, AlreadyActive, Failed }

/** [skippedVersions]: selected versions with no verse text, so no card was made for them (IOS_ISSUES L6). */
data class FlashCardAddResult(val status: FlashCardAddStatus, val skippedVersions: List<String> = emptyList())

object FlashCards {

    /** iOS `FlashCard.displayBibleVerseRef`: "John 3:16 [KJV]", null when the ref key does not read. */
    fun displayRef(card: FlashCardEntity): String? =
        card.refKey?.let { BibleFormatter.display(it, card.versionAbbr, showVersion = true) }?.takeIf { it.isNotEmpty() }

    /** The verse range a card stands for; null when it has no book, chapter, start verse or version. */
    fun anchor(card: FlashCardEntity): BibleAnchor? {
        val book = card.bookOrd ?: return null
        val chapter = card.chapterOrd ?: return null
        val start = card.verseStart ?: return null
        val version = card.versionAbbr?.trim().orEmpty().ifEmpty { return null }
        return BibleAnchor(book, chapter, start, card.verseEnd, version)
    }

    /** iOS `FlashCardListViewModel.sortCards`: by date = `updatedAt` newest first; by book = book, chapter, verse, version, newest. */
    fun sorted(cards: List<FlashCardEntity>, sort: FlashCardSort): List<FlashCardEntity> = when (sort) {
        FlashCardSort.ByDate -> cards.sortedByDescending { it.updatedAt }
        FlashCardSort.ByBook -> cards.sortedWith(
            compareBy<FlashCardEntity>({ it.bookOrd ?: Int.MAX_VALUE }, { it.chapterOrd ?: 0 }, { it.verseStart ?: 0 }, { it.versionAbbr.orEmpty() })
                .thenByDescending { it.updatedAt },
        )
    }

    /** iOS `flashCardMatching` / `normalizedFlashCardVersion`: versions compare upper-cased. */
    fun storageVersion(version: String): String = com.stoneofhope.stoneofhope.data.bible.BibleVersions.toStorage(version).uppercase()

    /** iOS `VerseCardDesign.maxPages`: the launch deck shows this many cards. */
    const val LAUNCH_DECK_SIZE = 5
}

/**
 * When the verse deck pops up on its own (IOS_ISSUES L4). iOS shows it on every return to the
 * foreground; Android only on the first foreground of a process and after being away
 * [AWAY_MILLIS] or more.
 */
class LaunchDeckPolicy {
    private var evaluatedOnce = false
    private var stoppedAt: Long? = null

    fun onStop(nowMillis: Long) {
        stoppedAt = nowMillis
    }

    fun shouldShow(nowMillis: Long, enabled: Boolean, hasActiveCards: Boolean): Boolean {
        val first = !evaluatedOnce
        evaluatedOnce = true
        val away = stoppedAt?.let { nowMillis - it >= AWAY_MILLIS } ?: false
        stoppedAt = null
        return enabled && hasActiveCards && (first || away)
    }

    companion object {
        const val AWAY_MILLIS = 30 * 60 * 1000L
    }
}
