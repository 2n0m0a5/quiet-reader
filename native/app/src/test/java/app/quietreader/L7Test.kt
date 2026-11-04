package app.quietreader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.quietreader.data.BookFormat
import app.quietreader.data.ImportResult
import app.quietreader.data.convert.Converters
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.services.positions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * L7: every format becomes an EPUB that Readium opens, with its title, chapters and text.
 * MOBI, AZW3, a joint MOBI+KF8 and a Kindle comic were made by Calibre from the sample books;
 * the rest come from tests/gen/mkbooks.py.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class L7Test {
    private lateinit var context: Context
    private lateinit var quiet: Quiet

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.filesDir.deleteRecursively()
        quiet = Quiet(context, dbName = null)
    }

    private fun sample(name: String): File {
        val out = File(context.cacheDir, name)
        javaClass.classLoader!!.getResourceAsStream("formats/$name")!!.use { i -> out.outputStream().use { i.copyTo(it) } }
        return out
    }

    /** Converts, opens with Readium, and returns (title, chapters, text of all chapters, positions). */
    private fun open(name: String): Triple<String, Int, String> = runBlocking {
        val out = File(context.cacheDir, "$name.epub")
        Converters.toEpub(sample(name), name, out)
        File("build/outputs/converted").mkdirs(); out.copyTo(File("build/outputs/converted/$name.epub"), overwrite = true)
        val pub = quiet.readium.open(out) ?: error("Readium couldn't open the EPUB made from $name")
        try {
            val text = StringBuilder()
            for (l in pub.readingOrder) text.append(pub.get(l)?.read()?.getOrNull()?.let { String(it) } ?: "").append('\n')
            assertTrue("$name has pages", pub.positions().isNotEmpty())
            Triple(pub.metadata.title ?: "", pub.readingOrder.size, text.toString())
        } finally { pub.close() }
    }

    @Test fun kindleMobi() {
        val (title, chapters, text) = open("lamp.mobi")
        assertEquals("The Lamp and the Clock", title)
        assertTrue("chapters: $chapters", chapters >= 2)
        assertTrue(text.contains("river", ignoreCase = true))
    }

    @Test fun kindleAzw3() {
        val (title, chapters, text) = open("lamp.azw3")
        assertEquals("The Lamp and the Clock", title)
        assertTrue("chapters: $chapters", chapters >= 2)
        assertTrue(text.contains("river", ignoreCase = true))
        assertTrue("no kindle: links left", !text.contains("kindle:"))
    }

    @Test fun kindleJointFile() {
        val (title, _, text) = open("lamp-joint.mobi")
        assertEquals("The Lamp and the Clock", title)
        assertTrue(!text.contains("kindle:"))
    }

    @Test fun kindleComic() { assertTrue(open("comic.azw3").second >= 1) }

    @Test fun documents() {
        for (name in listOf("sample.fb2", "sample.fbz", "sample.docx", "sample.odt", "sample.rtf", "sample.md", "sample.html", "sample.htmlz", "sample.pdb")) {
            val (title, chapters, text) = open(name)
            assertTrue("$name: title", title.isNotBlank())
            assertTrue("$name: chapters", chapters >= 1)
            val words = Regex("[\\p{L}]{3,}").findAll(text.replace(Regex("<[^>]+>"), " ")).count()
            assertTrue("$name: text ($words words)", words > 40)
        }
    }

    @Test fun comics() {
        for (name in listOf("sample.cbz", "sample.cbt", "sample.cb7", "sample.cbr")) {
            val (_, pages, text) = open(name)
            assertTrue("$name: pages $pages", pages >= 3)
            assertTrue("$name: pictures", text.contains("<img"))
        }
    }

    @Test fun importingAddsThemToTheLibrary() = runBlocking {
        val r = quiet.importer.importFile(sample("lamp.azw3"), "lamp.azw3")
        val book = (r as ImportResult.Added).book
        assertEquals(BookFormat.Mobi, book.format)
        assertEquals("AZW3", book.json.getString("format"))
        assertEquals("The Lamp and the Clock", book.title)
        assertTrue(quiet.library.derivedOf(book.id).exists())
        val comic = (quiet.importer.importFile(sample("sample.cbr"), "My Comic.cbr") as ImportResult.Added).book
        assertEquals(BookFormat.Comic, comic.format)
        assertTrue("a comic's cover is its first page", quiet.library.coverOf(comic.id).exists())
    }
}
