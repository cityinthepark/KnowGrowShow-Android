package com.stoneofhope.stoneofhope.data.bible

/** iOS `BibleFormatter`: reference labels such as "John 3:16-18 (KJV)". */
object BibleFormatter {

    /** iOS looks the version up case-sensitively and falls back to English. */
    private fun language(versionAbbr: String?): BibleLanguage =
        BibleVersions.available.firstOrNull { it.abbreviation == versionAbbr }?.language ?: BibleLanguage.English

    /** Parses `"CH:book:chapter"` and `"VS:book:chapter:v1-v2"` keys. */
    fun display(refKey: String, versionAbbr: String?, showVersion: Boolean = true): String? =
        display(refKey, language(versionAbbr), versionAbbr, showVersion)

    /** Same as [display] but with the book name in [language] (the iOS overload always derives it). */
    fun display(refKey: String, language: BibleLanguage, versionAbbr: String? = null, showVersion: Boolean = false): String? {
        val parts = refKey.split(":").filter { it.isNotEmpty() }
        if (parts.size == 3 && parts[0] == "CH") {
            val bookOrd = SwiftText.asciiInt(parts[1]) ?: return null
            val chapter = SwiftText.asciiInt(parts[2]) ?: return null
            val name = BibleBooks.book(bookOrd)?.fullName(language) ?: return null
            return "$name $chapter"
        }
        if (parts.size != 4 || parts[0] != "VS") return null
        val bookOrd = SwiftText.asciiInt(parts[1]) ?: return null
        val chapter = SwiftText.asciiInt(parts[2]) ?: return null
        val verseParts = parts[3].split("-").filter { it.isNotEmpty() }
        val v1 = SwiftText.asciiInt(verseParts.firstOrNull()) ?: return null
        val v2 = if (verseParts.size > 1) SwiftText.asciiInt(verseParts[1]) else null
        val name = BibleBooks.book(bookOrd)?.fullName(language) ?: return null
        val sb = StringBuilder("$name $chapter:$v1")
        if (v2 != null && v2 != v1) sb.append("-").append(v2)
        if (showVersion && versionAbbr != null) sb.append(" (").append(versionAbbr.uppercase()).append(")")
        return sb.toString()
    }

    /** Ord-based label (userVerses / new notes); does not read `refKey`. */
    fun display(
        bookOrd: Int,
        chapterOrd: Int,
        verseStart: Int?,
        verseEnd: Int?,
        versionAbbr: String?,
        showVersion: Boolean = true,
        language: BibleLanguage = language(versionAbbr),
    ): String? {
        val name = BibleBooks.book(bookOrd)?.fullName(language) ?: return null
        val start = verseStart ?: return "$name $chapterOrd"
        val sb = StringBuilder("$name $chapterOrd:$start")
        val end = verseEnd ?: start
        if (end != start) sb.append("-").append(end)
        if (showVersion && !versionAbbr.isNullOrEmpty()) sb.append(" (").append(versionAbbr.uppercase()).append(")")
        return sb.toString()
    }
}
