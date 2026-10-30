package com.stoneofhope.stoneofhope.data.bible

/** What tapping a version's check circle does (iOS `AvailableVersionsView.toggleVersionSelection`). */
sealed interface VersionToggle {
    data class Changed(val selected: List<String>) : VersionToggle

    /** Two are already chosen (iOS shows the "Selection limit" alert). */
    data object LimitReached : VersionToggle

    /** Android can't read this version yet (NIV / NKJV need API.Bible), so it can't be chosen. */
    data object Unavailable : VersionToggle
}

object BibleVersionSelection {
    const val MAX = 2

    /**
     * Select up to [MAX] versions, the first being the primary. Taking the last one away puts the default
     * version back (iOS `enforceMinimumSelection`).
     */
    fun toggle(selected: List<String>, abbr: String, defaultLanguage: BibleLanguage = BibleLanguage.current()): VersionToggle {
        val index = selected.indexOfFirst { it.equals(abbr, ignoreCase = true) }
        if (index >= 0) {
            val rest = selected.toMutableList().also { it.removeAt(index) }
            return VersionToggle.Changed(rest.ifEmpty { BibleVersions.firstLaunchDefault(defaultLanguage) })
        }
        if (!BibleVersions.isSelectable(abbr)) return VersionToggle.Unavailable
        if (selected.size >= MAX) return VersionToggle.LimitReached
        return VersionToggle.Changed(selected + BibleVersions.toStorage(abbr))
    }
}
