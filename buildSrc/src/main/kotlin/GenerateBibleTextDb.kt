import com.google.gson.JsonParser
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Builds the read-only Room database `bible/bible_text.db` from the bundled iOS Bible files, so the
 * app ships ready-to-query text (iOS instead parses the files into Realm on first launch).
 *
 * The verse text must equal what iOS stores in `DownloadedVerse.verseContent`:
 * - `KJV.json` (`BibleDownloadManager.saveBibleDataToDownloadedVerses`): `text` verbatim, version
 *   `distribution_abbreviation`.
 * - `CUV.xml` (`BibleXMLParser`): the characters between `<v id=N/>` and `<ve/>`, each parser chunk
 *   trimmed of whitespace. Chunk boundaries are a libxml2 detail, so the build fails if a verse has
 *   inner whitespace — then the iOS result could depend on where the chunks fall. Then
 *   [CuvVersificationFix] (iOS `CUVVersificationFix` revision 2) aligns it to KJV's 31,102 verses.
 *
 * Tables, indices and the identity hash come from Room's exported schema, so Room opens the file
 * without a migration.
 */
@CacheableTask
abstract class GenerateBibleTextDb : DefaultTask() {

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val kjvJson: RegularFileProperty

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val cuvXml: RegularFileProperty

    /** `schemas/…BibleTextDatabase/<version>.json`, written by Room's KSP processor. */
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val roomSchema: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private class Verse(val versionAbbr: String, val bookOrd: Int, val chapterOrd: Int, val verseOrd: Int, val content: String)

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val dbFile = File(out, "bible/bible_text.db").apply { parentFile.mkdirs() }

        val kjv = readKjv(kjvJson.get().asFile)
        val cuv = fixedCuv(readCuv(cuvXml.get().asFile), kjv)
        val verses = kjv + cuv
        val schema = JsonParser.parseString(roomSchema.get().asFile.readText()).asJsonObject.getAsJsonObject("database")
        val entity = schema.getAsJsonArray("entities").single().asJsonObject
        val table = entity.get("tableName").asString

        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.autoCommit = false
            conn.exec(entity.get("createSql").asString.replace("\${TABLE_NAME}", table))
            entity.getAsJsonArray("indices")?.forEach { conn.exec(it.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", table)) }
            schema.getAsJsonArray("setupQueries").forEach { conn.exec(it.asString) }
            conn.prepareStatement(
                "INSERT INTO `$table` (versionAbbr, bookOrd, chapterOrd, verseOrd, content) VALUES (?, ?, ?, ?, ?)"
            ).use { insert ->
                for (v in verses) {
                    insert.setString(1, v.versionAbbr)
                    insert.setInt(2, v.bookOrd)
                    insert.setInt(3, v.chapterOrd)
                    insert.setInt(4, v.verseOrd)
                    insert.setString(5, v.content)
                    insert.addBatch()
                }
                insert.executeBatch()
            }
            conn.commit()
            conn.autoCommit = true
            conn.exec("PRAGMA user_version = ${schema.get("version").asInt}")
            conn.exec("VACUUM")
        }
        logger.lifecycle("bible_text.db: ${verses.groupingBy { it.versionAbbr }.eachCount()} (${dbFile.length() / 1024} KB)")
    }

    /**
     * iOS `CUVVersificationFix` applied after parsing; the result must have exactly KJV's verses
     * (31,102), so a changed source or a wrong fix fails the build.
     */
    private fun fixedCuv(parsed: List<Verse>, kjv: List<Verse>): List<Verse> {
        val byKey = parsed.associateTo(linkedMapOf()) { VerseKey(it.bookOrd, it.chapterOrd, it.verseOrd) to it.content }
        if (byKey.size != parsed.size) throw GradleException("CUV.xml has duplicate verse ids")
        CuvVersificationFix.apply(byKey)
        val kjvKeys = kjv.map { VerseKey(it.bookOrd, it.chapterOrd, it.verseOrd) }.toSet()
        if (byKey.keys != kjvKeys) {
            throw GradleException("CUV after CuvVersificationFix differs from the KJV table: missing ${(kjvKeys - byKey.keys).sorted().take(5)}, extra ${(byKey.keys - kjvKeys).sorted().take(5)}")
        }
        return byKey.entries.sortedBy { it.key }.map { (k, text) -> Verse("CUV", k.bookOrd, k.chapterOrd, k.verseOrd, text) }
    }

    private fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }

    private fun readKjv(file: File): List<Verse> {
        val root = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
        val abbr = root.get("distribution_abbreviation").asString
        return root.getAsJsonArray("books").flatMap { b ->
            val book = b.asJsonObject
            book.getAsJsonArray("chapters").flatMap { c ->
                val chapter = c.asJsonObject
                chapter.getAsJsonArray("verses").map { v ->
                    val verse = v.asJsonObject
                    Verse(abbr, book.get("nr").asInt, chapter.get("chapter").asInt, verse.get("verse").asInt, verse.get("text").asString)
                }
            }
        }
    }

    private fun readCuv(file: File): List<Verse> {
        val factory = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.IS_COALESCING, true)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
        }
        val verses = mutableListOf<Verse>()
        var bookOrd = 0
        var chapterOrd = 0
        var verseOrd = 0
        var text: StringBuilder? = null
        file.inputStream().use { input ->
            val r = factory.createXMLStreamReader(input, "UTF-8")
            while (r.hasNext()) {
                when (r.next()) {
                    XMLStreamConstants.START_ELEMENT -> when (r.localName) {
                        "book" -> r.getAttributeValue(null, "id")?.let { bookOrd = USFX_BOOKS[it] ?: 0 }
                        "c" -> r.getAttributeValue(null, "id")?.toIntOrNull()?.let { chapterOrd = it }
                        "v" -> r.getAttributeValue(null, "id")?.toIntOrNull()?.let { verseOrd = it; text = StringBuilder() }
                        "ve" -> {
                            val raw = text ?: throw GradleException("CUV.xml: <ve/> without <v> after $bookOrd:$chapterOrd:$verseOrd")
                            val content = raw.toString().trim { it.isWhitespace() || it == '\u0085' }
                            if (content.any { it.isWhitespace() }) {
                                throw GradleException("CUV.xml $bookOrd:$chapterOrd:$verseOrd has inner whitespace; iOS's chunk-trimmed text may differ")
                            }
                            if (bookOrd == 0) throw GradleException("CUV.xml: unknown book before $chapterOrd:$verseOrd")
                            verses += Verse("CUV", bookOrd, chapterOrd, verseOrd, content)
                            text = null
                        }
                    }
                    XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> text?.append(r.text)
                }
            }
        }
        return verses
    }

    private companion object {
        /** iOS `BibleXMLParser.getBookOrd(from:)`. */
        val USFX_BOOKS: Map<String, Int> = listOf(
            "GEN", "EXO", "LEV", "NUM", "DEU", "JOS", "JDG", "RUT", "1SA", "2SA", "1KI", "2KI", "1CH", "2CH",
            "EZR", "NEH", "EST", "JOB", "PSA", "PRO", "ECC", "SNG", "ISA", "JER", "LAM", "EZK", "DAN", "HOS",
            "JOL", "AMO", "OBA", "JON", "MIC", "NAM", "HAB", "ZEP", "HAG", "ZEC", "MAL", "MAT", "MRK", "LUK",
            "JHN", "ACT", "ROM", "1CO", "2CO", "GAL", "EPH", "PHP", "COL", "1TH", "2TH", "1TI", "2TI", "TIT",
            "PHM", "HEB", "JAS", "1PE", "2PE", "1JN", "2JN", "3JN", "JUD", "REV",
        ).withIndex().associate { (i, id) -> id to i + 1 }
    }
}
