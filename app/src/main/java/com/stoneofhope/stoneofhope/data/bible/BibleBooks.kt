package com.stoneofhope.stoneofhope.data.bible

import java.util.Locale

// GENERATED from iOS Models/Consts/BibleBooks.swift — do not edit by hand.
// Traditional Chinese names/abbreviations were produced with the same CFStringTransform
// "Hans-Hant" iOS applies at runtime, so all three languages match iOS exactly.

/** iOS `BibleLanguage`. */
enum class BibleLanguage { English, ChineseSimplified, ChineseTraditional;

    companion object {
        /** iOS `BibleBook.currentLanguage`: zh-Hant → traditional, other zh → simplified, else English. */
        fun current(locale: Locale = Locale.getDefault()): BibleLanguage {
            if (locale.language != "zh") return English
            val traditional = locale.script == "Hant" ||
                (locale.script.isEmpty() && locale.country in setOf("TW", "HK", "MO"))
            return if (traditional) ChineseTraditional else ChineseSimplified
        }
    }
}

/** iOS `BibleBook`. [ord] runs 1...66. */
class BibleBook(
    val ord: Int,
    private val fullNames: Map<BibleLanguage, String>,
    private val abbreviationLists: Map<BibleLanguage, List<String>>,
    val versesPerChapter: IntArray,
) {
    val chapterCount: Int get() = versesPerChapter.size
    val testament: String get() = if (ord <= 39) "OT" else "NT"

    fun fullName(language: BibleLanguage = BibleLanguage.current()): String = fullNames.getValue(language)

    fun abbreviations(language: BibleLanguage): List<String> = abbreviationLists.getValue(language)

    fun isValidChapter(chapter: Int) = chapter in 1..chapterCount

    fun isValidVerse(verse: Int, chapter: Int) = isValidChapter(chapter) && verse in 1..versesPerChapter[chapter - 1]
}

/** iOS `BibleBooks` + `BibleBookHelper.book(forOrd:)`. */
object BibleBooks {

    fun book(ord: Int): BibleBook? = all.getOrNull(ord - 1)

    /** Localized full name, e.g. "John" / "约翰福音"; "?" for an unknown ord (iOS `getName(for:)`). */
    fun name(ord: Int, language: BibleLanguage = BibleLanguage.current()): String = book(ord)?.fullName(language) ?: "?"

    private fun book(
        ord: Int, en: String, hans: String, hant: String,
        abbrEn: List<String>, abbrHans: List<String>, abbrHant: List<String>,
        verses: IntArray,
    ) = BibleBook(
        ord,
        mapOf(BibleLanguage.English to en, BibleLanguage.ChineseSimplified to hans, BibleLanguage.ChineseTraditional to hant),
        mapOf(BibleLanguage.English to abbrEn, BibleLanguage.ChineseSimplified to abbrHans, BibleLanguage.ChineseTraditional to abbrHant),
        verses,
    )

    val all: List<BibleBook> = listOf(
        book(1, "Genesis", "创世记", "創世記",
            listOf("Gen", "Gn"), listOf("创"), listOf("創"),
            intArrayOf(31, 25, 24, 26, 32, 22, 24, 22, 29, 32, 32, 20, 18, 24, 21, 16, 27, 33, 38, 18, 34, 24, 20, 67, 34, 35, 46, 22, 35, 43, 55, 32, 20, 31, 29, 43, 36, 30, 23, 23, 57, 38, 34, 34, 28, 34, 31, 22, 33, 26)),
        book(2, "Exodus", "出埃及记", "出埃及記",
            listOf("Exo", "Ex"), listOf("出"), listOf("出"),
            intArrayOf(22, 25, 22, 31, 23, 30, 25, 32, 35, 29, 10, 51, 22, 31, 27, 36, 16, 27, 25, 26, 36, 31, 33, 18, 40, 37, 21, 43, 46, 38, 18, 35, 23, 35, 35, 38, 29, 31, 43, 38)),
        book(3, "Leviticus", "利未记", "利未記",
            listOf("Lev", "Le"), listOf("利"), listOf("利"),
            intArrayOf(17, 16, 17, 35, 19, 30, 38, 36, 24, 20, 47, 8, 59, 57, 33, 34, 16, 30, 37, 27, 24, 33, 44, 23, 55, 46, 34)),
        book(4, "Numbers", "民数记", "民數記",
            listOf("Num", "Nu"), listOf("民"), listOf("民"),
            intArrayOf(54, 34, 51, 49, 31, 27, 89, 26, 23, 36, 35, 16, 33, 45, 41, 50, 13, 32, 22, 29, 35, 41, 30, 25, 18, 65, 23, 31, 40, 16, 54, 42, 56, 29, 34, 13)),
        book(5, "Deuteronomy", "申命记", "申命記",
            listOf("Deu", "Dt"), listOf("申"), listOf("申"),
            intArrayOf(46, 37, 29, 49, 33, 25, 26, 20, 29, 22, 32, 32, 18, 29, 23, 22, 20, 22, 21, 20, 23, 30, 25, 22, 19, 19, 26, 68, 29, 20, 30, 52, 29, 12)),
        book(6, "Joshua", "约书亚记", "約書亞記",
            listOf("Jos", "Jo", "Jsh"), listOf("书"), listOf("書"),
            intArrayOf(18, 24, 17, 24, 15, 27, 26, 35, 27, 43, 23, 24, 33, 15, 63, 10, 18, 28, 51, 9, 45, 34, 16, 33)),
        book(7, "Judges", "士师记", "士師記",
            listOf("Jug", "Jg", "Jdg"), listOf("士"), listOf("士"),
            intArrayOf(36, 23, 31, 24, 31, 40, 25, 35, 57, 18, 40, 15, 25, 20, 20, 31, 13, 31, 30, 48, 25)),
        book(8, "Ruth", "路得记", "路得記",
            listOf("Rut", "Ru"), listOf("得"), listOf("得"),
            intArrayOf(22, 23, 18, 22)),
        book(9, "1 Samuel", "撒母耳记上", "撒母耳記上",
            listOf("1Sa", "1S", "1Sam", "1 Sa", "1 S", "1 Sam"), listOf("撒上"), listOf("撒上"),
            intArrayOf(28, 36, 21, 22, 12, 21, 17, 22, 27, 27, 15, 25, 23, 52, 35, 23, 58, 30, 24, 42, 15, 23, 29, 22, 44, 25, 12, 25, 11, 31, 13)),
        book(10, "2 Samuel", "撒母耳记下", "撒母耳記下",
            listOf("2Sa", "2S", "2Sam", "2Sa", "2S", "2Sam"), listOf("撒下"), listOf("撒下"),
            intArrayOf(27, 32, 39, 12, 25, 23, 29, 18, 13, 19, 27, 31, 39, 33, 37, 23, 29, 33, 43, 26, 22, 51, 39, 25)),
        book(11, "1 Kings", "列王记上", "列王記上",
            listOf("1Ki", "1K", "1Kg", "1 Ki", "1 K", "1 Kg"), listOf("王上"), listOf("王上"),
            intArrayOf(53, 46, 28, 34, 18, 38, 51, 66, 28, 29, 43, 33, 34, 31, 34, 34, 24, 46, 21, 43, 29, 53)),
        book(12, "2 Kings", "列王记下", "列王記下",
            listOf("2Ki", "2K", "2Kg", "2 Ki", "2 K", "2 Kg"), listOf("王下"), listOf("王下"),
            intArrayOf(18, 25, 27, 44, 27, 33, 20, 29, 37, 36, 21, 21, 25, 29, 38, 20, 41, 37, 37, 21, 26, 20, 37, 20, 30)),
        book(13, "1 Chronicles", "历代志上", "歷代志上",
            listOf("1Ch", "1Chr", "1 Ch", "1 Chr"), listOf("代上"), listOf("代上"),
            intArrayOf(54, 55, 24, 43, 26, 81, 40, 40, 44, 14, 47, 40, 14, 17, 29, 43, 27, 17, 19, 8, 30, 19, 32, 31, 31, 32, 34, 21, 30)),
        book(14, "2 Chronicles", "历代志下", "歷代志下",
            listOf("2Ch", "2Chr", "2 Ch", "2 Chr"), listOf("代下"), listOf("代下"),
            intArrayOf(17, 18, 17, 22, 14, 42, 22, 18, 31, 19, 23, 16, 22, 15, 19, 14, 19, 34, 11, 37, 20, 12, 21, 27, 28, 23, 9, 27, 36, 27, 21, 33, 25, 33, 27, 23)),
        book(15, "Ezra", "以斯拉记", "以斯拉記",
            listOf("Ezr"), listOf("拉"), listOf("拉"),
            intArrayOf(11, 70, 13, 24, 17, 22, 28, 36, 15, 44)),
        book(16, "Nehemiah", "尼希米记", "尼希米記",
            listOf("Neh", "Ne"), listOf("尼"), listOf("尼"),
            intArrayOf(11, 20, 32, 23, 19, 19, 73, 18, 38, 39, 36, 47, 31)),
        book(17, "Esther", "以斯帖记", "以斯帖記",
            listOf("Est", "Es"), listOf("斯"), listOf("斯"),
            intArrayOf(22, 23, 15, 17, 14, 14, 10, 17, 32, 3)),
        book(18, "Job", "约伯记", "約伯記",
            listOf("Job", "Jb"), listOf("伯"), listOf("伯"),
            intArrayOf(22, 13, 26, 21, 27, 30, 21, 22, 35, 22, 20, 25, 28, 22, 35, 22, 16, 21, 29, 29, 34, 30, 17, 25, 6, 14, 23, 28, 25, 31, 40, 22, 33, 37, 16, 33, 24, 41, 30, 24, 34, 17)),
        book(19, "Psalms", "诗篇", "詩篇",
            listOf("Psm", "Ps", "Pslm"), listOf("诗"), listOf("詩"),
            intArrayOf(6, 12, 8, 8, 12, 10, 17, 9, 20, 18, 7, 8, 6, 7, 5, 11, 15, 50, 14, 9, 13, 31, 6, 10, 22, 12, 14, 9, 11, 12, 24, 11, 22, 22, 28, 12, 40, 22, 13, 17, 13, 11, 5, 26, 17, 11, 9, 14, 20, 23, 19, 9, 6, 7, 23, 13, 11, 11, 17, 12, 8, 12, 11, 10, 13, 20, 7, 35, 36, 5, 24, 20, 28, 23, 10, 12, 20, 72, 13, 19, 16, 8, 18, 12, 13, 17, 7, 18, 52, 17, 16, 15, 5, 23, 11, 13, 12, 9, 9, 5, 8, 28, 22, 35, 45, 48, 43, 13, 31, 7, 10, 10, 9, 8, 18, 19, 2, 29, 176, 7, 8, 9, 4, 8, 5, 6, 5, 6, 8, 8, 3, 18, 3, 3, 21, 26, 9, 8, 24, 13, 10, 7, 12, 15, 21, 10, 20, 14, 9, 6)),
        book(20, "Proverbs", "箴言", "箴言",
            listOf("Pro", "Prv", "Pr", "Prov"), listOf("箴"), listOf("箴"),
            intArrayOf(33, 22, 35, 27, 23, 35, 27, 36, 18, 32, 31, 28, 25, 35, 33, 33, 28, 24, 29, 30, 31, 29, 35, 34, 28, 28, 27, 28, 27, 33, 31)),
        book(21, "Ecclesiastes", "传道书", "傳道書",
            listOf("Ecc", "Ec"), listOf("传"), listOf("傳"),
            intArrayOf(18, 26, 22, 16, 20, 12, 29, 17, 18, 20, 10, 14)),
        book(22, "Song of Solomon", "雅歌", "雅歌",
            listOf("Son", "Sg", "SoS"), listOf("歌"), listOf("歌"),
            intArrayOf(17, 17, 11, 16, 16, 13, 13, 14)),
        book(23, "Isaiah", "以赛亚书", "以賽亞書",
            listOf("Isa", "Is"), listOf("赛"), listOf("賽"),
            intArrayOf(31, 22, 26, 6, 30, 13, 25, 22, 21, 34, 16, 6, 22, 32, 9, 14, 14, 7, 25, 6, 17, 25, 18, 23, 12, 21, 13, 29, 24, 33, 9, 20, 24, 17, 10, 22, 38, 22, 8, 31, 29, 25, 28, 28, 25, 13, 15, 22, 26, 11, 23, 15, 12, 17, 13, 12, 21, 14, 21, 22, 11, 12, 19, 12, 25, 24)),
        book(24, "Jeremiah", "耶利米书", "耶利米書",
            listOf("Jer", "Je"), listOf("耶"), listOf("耶"),
            intArrayOf(19, 37, 25, 31, 31, 30, 34, 22, 26, 25, 23, 17, 27, 22, 21, 21, 27, 23, 15, 18, 14, 30, 40, 10, 38, 24, 22, 17, 32, 24, 40, 44, 26, 22, 19, 32, 21, 28, 18, 16, 18, 22, 13, 30, 5, 28, 7, 47, 39, 46, 64, 34)),
        book(25, "Lamentations", "耶利米哀歌", "耶利米哀歌",
            listOf("Lam", "La"), listOf("哀"), listOf("哀"),
            intArrayOf(22, 22, 66, 22, 22)),
        book(26, "Ezekiel", "以西结书", "以西結書",
            listOf("Eze", "Ez"), listOf("结"), listOf("結"),
            intArrayOf(28, 10, 27, 17, 17, 14, 27, 18, 11, 22, 25, 28, 23, 23, 8, 63, 24, 32, 14, 49, 32, 31, 49, 27, 17, 21, 36, 26, 21, 26, 18, 32, 33, 31, 15, 38, 28, 23, 29, 49, 26, 20, 27, 31, 25, 24, 23, 35)),
        book(27, "Daniel", "但以理书", "但以理書",
            listOf("Dan", "Dn"), listOf("但"), listOf("但"),
            intArrayOf(21, 49, 30, 37, 31, 28, 28, 27, 27, 21, 45, 13)),
        book(28, "Hosea", "何西阿书", "何西阿書",
            listOf("Hos", "Ho"), listOf("何"), listOf("何"),
            intArrayOf(11, 23, 5, 19, 15, 11, 16, 14, 17, 15, 12, 14, 16, 9)),
        book(29, "Joel", "约珥书", "約珥書",
            listOf("Joe", "Jl"), listOf("珥"), listOf("珥"),
            intArrayOf(20, 32, 21)),
        book(30, "Amos", "阿摩司书", "阿摩司書",
            listOf("Amo", "Am"), listOf("摩"), listOf("摩"),
            intArrayOf(15, 16, 15, 13, 27, 14, 17, 14, 15)),
        book(31, "Obadiah", "俄巴底亚书", "俄巴底亞書",
            listOf("Oba", "Ob"), listOf("俄"), listOf("俄"),
            intArrayOf(21)),
        book(32, "Jonah", "约拿书", "約拿書",
            listOf("Jon", "Jh"), listOf("拿"), listOf("拿"),
            intArrayOf(17, 10, 10, 11)),
        book(33, "Micah", "弥迦书", "彌迦書",
            listOf("Mic", "Mi"), listOf("弥"), listOf("彌"),
            intArrayOf(16, 13, 12, 13, 15, 16, 20)),
        book(34, "Nahum", "那鸿书", "那鴻書",
            listOf("Nah", "Na"), listOf("鸿"), listOf("鴻"),
            intArrayOf(15, 13, 19)),
        book(35, "Habakkuk", "哈巴谷书", "哈巴谷書",
            listOf("Hab", "Hb"), listOf("哈"), listOf("哈"),
            intArrayOf(17, 20, 19)),
        book(36, "Zephaniah", "西番雅书", "西番雅書",
            listOf("Zep", "Zp"), listOf("番"), listOf("番"),
            intArrayOf(18, 15, 20)),
        book(37, "Haggai", "哈该书", "哈該書",
            listOf("Hag", "Hg"), listOf("该"), listOf("該"),
            intArrayOf(15, 23)),
        book(38, "Zechariah", "撒迦利亚书", "撒迦利亞書",
            listOf("Zec", "Zc"), listOf("亚"), listOf("亞"),
            intArrayOf(21, 13, 10, 14, 11, 15, 14, 23, 17, 12, 17, 14, 9, 21)),
        book(39, "Malachi", "玛拉基书", "瑪拉基書",
            listOf("Mal", "Ml"), listOf("玛"), listOf("瑪"),
            intArrayOf(14, 17, 18, 6)),
        book(40, "Matthew", "马太福音", "馬太福音",
            listOf("Mat", "Mt", "Matt"), listOf("太"), listOf("太"),
            intArrayOf(25, 23, 17, 25, 48, 34, 29, 34, 38, 42, 30, 50, 58, 36, 39, 28, 27, 35, 30, 34, 46, 46, 39, 51, 46, 75, 66, 20)),
        book(41, "Mark", "马可福音", "馬可福音",
            listOf("Mak", "Mk"), listOf("可"), listOf("可"),
            intArrayOf(45, 28, 35, 41, 43, 56, 37, 38, 50, 52, 33, 44, 37, 72, 47, 20)),
        book(42, "Luke", "路加福音", "路加福音",
            listOf("Luk", "Lk"), listOf("路"), listOf("路"),
            intArrayOf(80, 52, 38, 44, 39, 49, 50, 56, 62, 42, 54, 59, 35, 35, 32, 31, 37, 43, 48, 47, 38, 71, 56, 53)),
        book(43, "John", "约翰福音", "約翰福音",
            listOf("Jhn", "Jn"), listOf("约"), listOf("約"),
            intArrayOf(51, 25, 36, 54, 47, 71, 53, 59, 41, 42, 57, 50, 38, 31, 27, 33, 26, 40, 42, 31, 25)),
        book(44, "Acts", "使徒行传", "使徒行傳",
            listOf("Act", "Ac"), listOf("徒"), listOf("徒"),
            intArrayOf(26, 47, 26, 37, 42, 15, 60, 40, 43, 48, 30, 25, 52, 28, 41, 40, 34, 28, 41, 38, 40, 30, 35, 27, 27, 32, 44, 31)),
        book(45, "Romans", "罗马书", "羅馬書",
            listOf("Rom", "Ro"), listOf("罗"), listOf("羅"),
            intArrayOf(32, 29, 31, 25, 21, 23, 25, 39, 33, 21, 36, 21, 14, 23, 33, 27)),
        book(46, "1 Corinthians", "哥林多前书", "哥林多前書",
            listOf("1Co", "1C", "1Cor", "1 Co", "1 C", "1 Cor"), listOf("林前"), listOf("林前"),
            intArrayOf(31, 16, 23, 21, 13, 20, 40, 13, 27, 33, 34, 31, 13, 40, 58, 24)),
        book(47, "2 Corinthians", "哥林多后书", "哥林多後書",
            listOf("2Co", "2C", "2Cor", "2 Co", "2 C", "2 Cor"), listOf("林后"), listOf("林後"),
            intArrayOf(24, 17, 18, 18, 21, 18, 16, 24, 15, 18, 33, 21, 14)),
        book(48, "Galatians", "加拉太书", "加拉太書",
            listOf("Gal", "Ga"), listOf("加"), listOf("加"),
            intArrayOf(24, 21, 29, 31, 26, 18)),
        book(49, "Ephesians", "以弗所书", "以弗所書",
            listOf("Eph", "Ep"), listOf("弗"), listOf("弗"),
            intArrayOf(23, 22, 21, 32, 33, 24)),
        book(50, "Philippians", "腓利比书", "腓利比書",
            listOf("Phl", "Php", "Ph"), listOf("腓"), listOf("腓"),
            intArrayOf(30, 30, 21, 23)),
        book(51, "Colossians", "歌罗西书", "歌羅西書",
            listOf("Col", "Co"), listOf("西"), listOf("西"),
            intArrayOf(29, 23, 25, 18)),
        book(52, "1 Thessalonians", "帖撒罗尼迦前书", "帖撒羅尼迦前書",
            listOf("1Th", "1 Th"), listOf("帖前"), listOf("帖前"),
            intArrayOf(10, 20, 13, 18, 28)),
        book(53, "2 Thessalonians", "帖撒罗尼迦后书", "帖撒羅尼迦後書",
            listOf("2Th", "2 Th"), listOf("帖后"), listOf("帖後"),
            intArrayOf(12, 17, 18)),
        book(54, "1 Timothy", "提摩太前书", "提摩太前書",
            listOf("1Ti", "1T", "1Tim", "1 Ti", "1 T", "1 Tim"), listOf("提前"), listOf("提前"),
            intArrayOf(20, 15, 16, 16, 25, 21)),
        book(55, "2 Timothy", "提摩太后书", "提摩太后書",
            listOf("2Ti", "2T", "2Tim", "2 Ti", "2 T", "2 Tim"), listOf("提后"), listOf("提後"),
            intArrayOf(18, 26, 17, 22)),
        book(56, "Titus", "提多书", "提多書",
            listOf("Tit", "Ti"), listOf("多"), listOf("多"),
            intArrayOf(16, 15, 15)),
        book(57, "Philemon", "腓利门书", "腓利門書",
            listOf("Phm", "Phlmn"), listOf("门"), listOf("門"),
            intArrayOf(25)),
        book(58, "Hebrews", "希伯来书", "希伯來書",
            listOf("Heb", "He"), listOf("来"), listOf("來"),
            intArrayOf(14, 18, 19, 16, 14, 20, 28, 13, 28, 39, 40, 29, 25)),
        book(59, "James", "雅各书", "雅各書",
            listOf("Jas", "Ja", "Jm"), listOf("雅"), listOf("雅"),
            intArrayOf(27, 26, 18, 17, 20)),
        book(60, "1 Peter", "彼得前书", "彼得前書",
            listOf("1Pe", "1P", "1Pt", "1p", "1 Pe", "1 P", "1 Pt"), listOf("彼前"), listOf("彼前"),
            intArrayOf(25, 25, 22, 19, 14)),
        book(61, "2 Peter", "彼得后书", "彼得後書",
            listOf("2Pe", "2P", "2Pt", "2p", "2 Pe", "2 P", "2 Pt"), listOf("彼后"), listOf("彼後"),
            intArrayOf(21, 22, 18)),
        book(62, "1 John", "约翰一书", "約翰一書",
            listOf("1Jn", "1Jo", "1J", "1 Jn", "1 Jo", "1 J", "1Joh", "1 Joh"), listOf("约一"), listOf("約一"),
            intArrayOf(10, 29, 24, 21, 21)),
        book(63, "2 John", "约翰二书", "約翰二書",
            listOf("2Jn", "2Jo", "2J", "2 Jn", "2 Jo", "2 J", "2Joh", "2 Joh"), listOf("约二"), listOf("約二"),
            intArrayOf(13)),
        book(64, "3 John", "约翰三书", "約翰三書",
            listOf("3Jn", "3Jo", "3J", "3 Jn", "3 Jo", "3 J", "3Joh", "3 Joh"), listOf("约三"), listOf("約三"),
            intArrayOf(14)),
        book(65, "Jude", "犹大书", "猶大書",
            listOf("Jud", "Jd"), listOf("犹"), listOf("猶"),
            intArrayOf(25)),
        book(66, "Revelation", "启示录", "啓示錄",
            listOf("Rev", "Re"), listOf("启"), listOf("啓"),
            intArrayOf(20, 29, 22, 11, 14, 17, 17, 13, 21, 11, 19, 17, 18, 20, 8, 21, 18, 24, 21, 15, 27, 21)),
    )
}
