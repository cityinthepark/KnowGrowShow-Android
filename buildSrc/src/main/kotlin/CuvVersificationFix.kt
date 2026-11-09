import org.gradle.api.GradleException

/** A verse position in the app's KJV-style table (`versesPerChapterMaster`). */
data class VerseKey(val bookOrd: Int, val chapterOrd: Int, val verseOrd: Int) : Comparable<VerseKey> {
    override fun compareTo(other: VerseKey) = compareValuesBy(this, other, { it.bookOrd }, { it.chapterOrd }, { it.verseOrd })
    override fun toString() = "$bookOrd:$chapterOrd:$verseOrd"
}

/**
 * Port of iOS `CUVVersificationFix` (revision 2, iOS 101ad61 + e4bb313): aligns the bundled CUV.xml
 * (eBible.org 新標點和合本, cmn-cu89t) to the KJV verse table. Splits keep the source text and its
 * reading order exactly; nothing is reworded. Each fix checks the source text it expects first, so a
 * changed source fails the build instead of producing misaligned verses.
 */
object CuvVersificationFix {
    /** 新標點 folds some verses into the one before and prints this in the emptied one. */
    const val MERGED_VERSE_PLACEHOLDER = "併於上節"
    const val EXPECTED_MERGED_VERSE_COUNT = 69

    private fun mismatch(key: VerseKey, detail: String): Nothing =
        throw GradleException("CUV $key $detail — re-check CuvVersificationFix")

    fun apply(verses: MutableMap<VerseKey, String>) {
        fun expect(key: VerseKey, text: String) {
            if (verses[key] != text) mismatch(key, "is not the expected source text")
        }
        fun split(key: VerseKey, text: String, firstPart: String, next: VerseKey) {
            expect(key, text)
            if (!text.startsWith(firstPart) || verses[next] != null) mismatch(key, "split point not found")
            verses[key] = firstPart
            verses[next] = text.substring(firstPart.length)
        }

        // Deut 13:17–18 — printed as one sentence; split after 17's first sentence.
        split(
            VerseKey(5, 13, 17),
            "那當毀滅的物連一點都不可粘你的手。你要聽從耶和華你神的話，遵守我今日所吩咐你的一切誡命，行耶和華你神眼中看為正的事，耶和華就必轉意，不發烈怒，恩待你，憐恤你，照他向你列祖所起的誓使你人數增多。」",
            firstPart = "那當毀滅的物連一點都不可粘你的手。",
            next = VerseKey(5, 13, 18),
        )

        // 1 Chr 21:31 / 22:1–18 — Hebrew numbering, so all of ch. 22 is one low. Renumber only.
        val chr2131 = VerseKey(13, 21, 31)
        expect(chr2131, "大衛說：「這就是耶和華神的殿，為以色列人獻燔祭的壇。」")
        val chronicles22 = mutableMapOf(VerseKey(13, 22, 1) to verses.remove(chr2131)!!)
        for (key in verses.keys.filter { it.bookOrd == 13 && it.chapterOrd == 22 }) {
            chronicles22[VerseKey(13, 22, key.verseOrd + 1)] = verses.remove(key)!!
        }
        if (chronicles22.size != 19) mismatch(VerseKey(13, 22, 1), "chapter 22 does not have 18 source verses")
        verses.putAll(chronicles22)

        // Ps 116:18–19 — merged; 19 is the closing doxology.
        split(
            VerseKey(19, 116, 18),
            "我要在他眾民面前，在耶和華殿的院內，在耶路撒冷當中，向耶和華還我的願。你們要讚美耶和華！",
            firstPart = "我要在他眾民面前，在耶和華殿的院內，在耶路撒冷當中，向耶和華還我的願。",
            next = VerseKey(19, 116, 19),
        )

        // John 7:53 is printed at the start of 8:1 — move that first clause back to 7:53.
        val john81 = VerseKey(43, 8, 1)
        val john753 = VerseKey(43, 7, 53)
        expect(john81, "於是各人都回家去了；耶穌卻往橄欖山去，")
        if (verses[john753] != null) mismatch(john753, "already exists")
        verses[john753] = "於是各人都回家去了；"
        verses[john81] = "耶穌卻往橄欖山去，"

        // 3 John 1:14–15 — CUV splits KJV's 1:14 in two; the table has 14 verses, so merge back.
        val john3115 = VerseKey(64, 1, 15)
        expect(john3115, "願你平安。眾位朋友都問你安。請你替我按着姓名問眾位朋友安。")
        val john3114 = verses[VerseKey(64, 1, 14)] ?: mismatch(VerseKey(64, 1, 14), "is missing")
        verses[VerseKey(64, 1, 14)] = john3114 + verses.remove(john3115)!!

        // "併於上節" verses — give each the merged text it was folded into. Ascending order, so a chain
        // (Ps 8:7–8 → 8:6) copies an already-filled previous verse.
        val mergedKeys = verses.keys.filter { verses[it] == MERGED_VERSE_PLACEHOLDER }.sorted()
        if (mergedKeys.size != EXPECTED_MERGED_VERSE_COUNT) {
            mismatch(mergedKeys.firstOrNull() ?: VerseKey(0, 0, 0), "has ${mergedKeys.size} $MERGED_VERSE_PLACEHOLDER verses, expected $EXPECTED_MERGED_VERSE_COUNT")
        }
        for (key in mergedKeys) {
            val text = verses[VerseKey(key.bookOrd, key.chapterOrd, key.verseOrd - 1)]
            if (text.isNullOrEmpty() || text == MERGED_VERSE_PLACEHOLDER) mismatch(key, "has no previous verse to take merged text from")
            verses[key] = text
        }
    }
}
