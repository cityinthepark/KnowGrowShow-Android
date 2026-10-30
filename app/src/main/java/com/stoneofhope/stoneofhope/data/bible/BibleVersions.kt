package com.stoneofhope.stoneofhope.data.bible

/** iOS `BibleVersion` (only the fields Android uses). */
data class BibleVersion(
    val name: String,
    val abbreviation: String,
    val displayName: String,
    val language: BibleLanguage,
    /** Bulk text installed on the device (KJV, CUV). The others are fetched per chapter (API.Bible). */
    val isDownloadable: Boolean,
)

/** iOS `AVAILABLE_VERSIONS` + the pure parts of `BibleVersionHelper`. */
object BibleVersions {

    val available: List<BibleVersion> = listOf(
        BibleVersion("King James Version", "KJV", "KJV", BibleLanguage.English, isDownloadable = true),
        BibleVersion("New International Version", "NIV", "NIV", BibleLanguage.English, isDownloadable = false),
        BibleVersion("New King James Version", "NKJV", "NKJV", BibleLanguage.English, isDownloadable = false),
        // iOS names CUV "和合本繁体": the bundled text is Traditional Chinese.
        BibleVersion("和合本繁体", "CUV", "和合本", BibleLanguage.ChineseTraditional, isDownloadable = true),
    )

    /** Versions whose text ships inside the app (iOS bundles `KJV.json` and `CUV.xml`). */
    val bundled: List<String> = listOf("KJV", "CUV")

    /** Versions whose text the app can read now: the bundled ones. */
    fun isSelectable(abbr: String): Boolean = bundled.any { it.equals(abbr, ignoreCase = true) }

    private val displayToStorage = mapOf("KJV" to "KJV", "和合本" to "CUV", "NIV" to "NIV", "NKJV" to "NKJV")
    private val storageToDisplay = mapOf("KJV" to "KJV", "CUV" to "和合本", "NIV" to "NIV", "NKJV" to "NKJV")

    fun toStorage(display: String): String = displayToStorage[display] ?: display.uppercase()

    fun toDisplay(storage: String): String = storageToDisplay[storage.uppercase()] ?: storage

    /** Case-insensitive lookup, like iOS `caseInsensitiveCompare`. */
    fun find(abbr: String): BibleVersion? = available.firstOrNull { it.abbreviation.equals(abbr, ignoreCase = true) }

    fun language(abbr: String): BibleLanguage? = find(abbr)?.language

    /** iOS `BibleVerseRangeReader.isDownloadableVersion`: unknown versions are not. */
    fun isDownloadable(abbr: String): Boolean = find(abbr)?.isDownloadable ?: false

    /** iOS `ignoresDownloadedVerses`: known API-only versions skip bulk rows. Unknown versions don't. */
    fun ignoresDownloadedVerses(abbr: String): Boolean = find(abbr)?.let { !it.isDownloadable } ?: false

    /** iOS `bookMatchingLanguages()`: selected versions' languages, both Chinese scripts if either. */
    fun bookMatchingLanguages(selected: List<String>): List<BibleLanguage> {
        val result = selected.mapNotNull(::language).distinct().toMutableList()
        if (BibleLanguage.ChineseTraditional in result && BibleLanguage.ChineseSimplified !in result) {
            result += BibleLanguage.ChineseSimplified
        }
        if (BibleLanguage.ChineseSimplified in result && BibleLanguage.ChineseTraditional !in result) {
            result += BibleLanguage.ChineseTraditional
        }
        return result
    }

    /** iOS `primaryBookDisplayLanguage()`. */
    fun primaryBookDisplayLanguage(selected: List<String>, fallback: BibleLanguage = BibleLanguage.current()): BibleLanguage =
        language(primary(selected)) ?: fallback

    /** iOS `secondaryBookDisplayLanguage()`: the second version's language when it is another family. */
    fun secondaryBookDisplayLanguage(selected: List<String>): BibleLanguage? {
        if (selected.size < 2) return null
        val primary = language(selected[0]) ?: return null
        val secondary = language(selected[1]) ?: return null
        return if (isChinese(primary) == isChinese(secondary)) null else secondary
    }

    /** iOS `getPrimaryVersion()`. */
    fun primary(selected: List<String>): String = selected.firstOrNull() ?: "KJV"

    /** iOS `getSelectedVersions()` parsing of the stored `"KJV,CUV"` string (Swift `split` drops empty parts). */
    fun parseStored(raw: String): List<String> =
        raw.split(",").filter { it.isNotEmpty() }.map { toStorage(SwiftText.trimWhitespaces(it)) }

    /** What iOS saves after its first-launch import (`MainTabView`): CUV for Chinese, else KJV. */
    fun firstLaunchDefault(language: BibleLanguage = BibleLanguage.current()): List<String> =
        if (language == BibleLanguage.English) listOf("KJV") else listOf("CUV")

    private fun isChinese(language: BibleLanguage) = language != BibleLanguage.English
}
