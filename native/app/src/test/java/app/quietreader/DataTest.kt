package app.quietreader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.quietreader.data.Backup
import app.quietreader.data.BookFormat
import app.quietreader.data.ImportResult
import app.quietreader.data.Importer
import app.quietreader.data.Library
import app.quietreader.data.Safety
import app.quietreader.data.TextBook
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

/**
 * The library's data: adding books, the web app's book IDs, backups both ways (a real backup
 * made by Quiet 0.4.2 is in test resources), the safety copy and Recently deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataTest {
    private lateinit var context: Context
    private lateinit var quiet: Quiet

    private fun resource(name: String): File {
        val out = File(context.cacheDir, name)
        javaClass.classLoader!!.getResourceAsStream(name)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("quiet", Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.deleteRecursively()
        quiet = Quiet(context, dbName = null) // in memory
    }

    @Test fun bookIdsMatchTheWebApp() {
        // The web app's fingerprint() of the same 300 KB, computed with its own code (Node).
        val file = File(context.cacheDir, "f.bin")
        file.writeBytes(ByteArray(300 * 1024) { ((it * 31 + 7) % 251).toByte() })
        assertEquals("082086682b043f9e71908e05", Importer.fingerprint(file))
    }

    @Test fun addsAnEpubWithItsDetailsAndCover() = runBlocking {
        val result = quiet.importer.importFile(resource("sample.epub"), "sample.epub")
        val book = (result as ImportResult.Added).book
        assertEquals(BookFormat.Epub, book.format)
        assertTrue("title from the book: ${book.title}", book.title.isNotBlank() && book.title != "sample")
        assertTrue(quiet.library.fileOf(book.id).exists())
        // Adding it again doesn't double it.
        assertTrue(quiet.importer.importFile(resource("sample.epub"), "sample.epub") is ImportResult.AlreadyThere)
        assertEquals(1, quiet.library.books.value.size)
    }

    @Test fun textBooksBecomeReadable() = runBlocking {
        val result = quiet.importer.importFile(resource("sample.txt"), "My Notes.txt")
        val book = (result as ImportResult.Added).book
        assertEquals(BookFormat.Txt, book.format)
        assertEquals("My Notes", book.title)
        val epub = quiet.library.derivedOf(book.id)
        assertTrue(epub.exists())
        assertNotNull("Readium opens the EPUB made from the text", quiet.readium.open(epub))
        ZipFile(epub).use { zip -> assertEquals("mimetype", zip.entries().nextElement().name) }
    }

    @Test fun windows1251TextIsDecoded() {
        val russian = "Глава первая. Тихая река."
        assertEquals(russian, TextBook.decode(russian.toByteArray(charset("windows-1251"))))
        assertEquals(russian, TextBook.decode(russian.toByteArray()))
    }

    @Test fun unknownOrBrokenFilesSaySo() = runBlocking {
        val odd = File(context.cacheDir, "notes.xyz").apply { writeText("x") }
        assertTrue(quiet.importer.importFile(odd, "notes.xyz") is ImportResult.NotSupported)
        val broken = File(context.cacheDir, "book.mobi").apply { writeText("not really a mobi") }
        assertTrue(quiet.importer.importFile(broken, "book.mobi") is ImportResult.Failed)
        assertEquals(0, quiet.library.books.value.size)
    }

    @Test fun restoresABackupMadeByTheWebApp() {
        val file = resource("web-0.4.2.quietbackup")
        val summary = quiet.backup.read(file)
        assertEquals(3, summary.books)
        assertEquals(1, summary.notes)
        val restored = quiet.backup.restore(file)
        assertEquals(3, restored.added)
        assertEquals(1, restored.notes)
        assertTrue("settings come along into an empty library", restored.settings)
        assertEquals(135, quiet.settings.bookSize)
        val titles = quiet.library.books.value.map { it.title }.toSet()
        assertEquals(setOf("Short Notes", "The Quiet River", "The Lamp and the Clock"), titles)
        quiet.library.books.value.forEach {
            assertTrue(quiet.library.fileOf(it.id).exists())
            assertTrue(quiet.library.coverOf(it.id).exists())
        }
        // The note is kept (Notes arrive in L4).
        assertEquals("A note to keep", quiet.library.records("annotations").single().getString("note"))
        // Again: nothing doubles.
        val again = quiet.backup.restore(file)
        assertEquals(0, again.added)
        assertEquals(3, quiet.library.books.value.size)
        assertEquals(1, quiet.library.records("annotations").size)
    }

    @Test fun nativeBackupRoundTripsInTheWebFormat() = runBlocking {
        quiet.backup.restore(resource("web-0.4.2.quietbackup"))
        quiet.importer.importFile(resource("sample.txt"), "Plain.txt")
        val lamp = quiet.library.books.value.first { it.title == "The Lamp and the Clock" }
        quiet.library.put(lamp.with({ put("progress", 0.42); put("lastOpenedAt", 1_800_000_000_000L); put("cfi", "epubcfi(/6/4!/4)") }, locator = """{"href":"x.xhtml","type":"application/xhtml+xml"}"""))
        quiet.settings.theme = ThemeMode.Night
        quiet.settings.lineHeight = 20
        quiet.settings.readerKeys = listOf(ReaderKey.GoTo, ReaderKey.Contents, ReaderKey.Light, ReaderKey.Text)

        // Also kept in build/ so the web app's test can restore it (scripts/check-native-backup.cjs).
        val out = File("build/outputs/native.quietbackup").apply { parentFile?.mkdirs() }
        val n = out.outputStream().use { quiet.backup.write(it, "test") }
        assertEquals(4, n)
        assertNotNull(quiet.settings.lastBackup)

        // What the web app reads: the manifest, the files, the settings.
        ZipFile(out).use { zip ->
            val manifest = JSONObject(zip.getInputStream(zip.getEntry("quiet-backup.json")).bufferedReader().readText())
            assertEquals("quiet-backup", manifest.getString("format"))
            val books = manifest.getJSONArray("books")
            assertEquals(4, books.length())
            for (i in 0 until books.length()) {
                val b = books.getJSONObject(i)
                assertNotNull(zip.getEntry("books/${b.getString("id")}"))
                if (b.getBoolean("hasCover")) assertNotNull(zip.getEntry("covers/${b.getString("id")}"))
            }
            assertEquals(1, manifest.getJSONArray("annotations").length())
            val settings = JSONObject(manifest.getJSONObject("settings").getString("quiet:settings"))
            assertEquals("dark", settings.getString("theme"))
            assertEquals(135, settings.getInt("fontSize"))
        }

        // A new phone.
        val fresh = Quiet(context, dbName = null)
        context.getSharedPreferences("quiet", Context.MODE_PRIVATE).edit().clear().commit()
        val r = fresh.backup.restore(out)
        assertEquals(4, r.added)
        val back = fresh.library.books.value.first { it.title == "The Lamp and the Clock" }
        assertEquals(0.42, back.progress, 1e-9)
        assertEquals("epubcfi(/6/4!/4)", back.cfi)
        assertNull("Readium's locator isn't in the web format", back.locator)
        // Reader settings came along too.
        assertEquals(20, fresh.settings.lineHeight)
        assertEquals(ReaderKey.GoTo, fresh.settings.readerKeys.first())
    }

    @Test fun safetyCopyRebuildsALostDatabase() = runBlocking {
        quiet.importer.importFile(resource("sample.epub"), "sample.epub")
        quiet.safety.save()
        // The database is lost, the files stay.
        val again = Quiet(context, dbName = null)
        assertEquals(0, again.library.books.value.size)
        assertEquals(1, again.safety.recover())
        assertEquals(1, again.library.books.value.size)
        // An empty library never overwrites a copy with books, unless emptied on purpose.
        val empty = Quiet(context, dbName = null)
        empty.safety.save()
        assertEquals(1, empty.safety.count())
        empty.safety.emptiedOnPurpose = true
        empty.safety.save()
        assertEquals(0, empty.safety.count())
    }

    @Test fun recentlyDeletedEmptiesAfter30Days() = runBlocking {
        val book = (quiet.importer.importFile(resource("sample.epub"), "sample.epub") as ImportResult.Added).book
        val day = 86_400_000L
        quiet.library.put(book.with({ put("deletedAt", System.currentTimeMillis() - 29 * day) }))
        Safety.emptyOldTrash(quiet.library)
        assertEquals(1, quiet.library.books.value.size)
        quiet.library.put(book.with({ put("deletedAt", System.currentTimeMillis() - 31 * day) }))
        Safety.emptyOldTrash(quiet.library)
        assertEquals(0, quiet.library.books.value.size)
        assertTrue(!quiet.library.fileOf(book.id).exists())
        // Adding a book that's in Recently deleted brings it back.
        val again = (quiet.importer.importFile(resource("sample.epub"), "sample.epub") as ImportResult.Added).book
        quiet.library.put(again.with({ put("deletedAt", System.currentTimeMillis()) }))
        val back = quiet.importer.importFile(resource("sample.epub"), "sample.epub") as ImportResult.AlreadyThere
        assertNull(back.book.deletedAt)
    }

    @Test fun notABackupSaysSo() {
        val junk = File(context.cacheDir, "junk.quietbackup").apply { writeText("hello") }
        try { quiet.backup.read(junk); throw AssertionError("should fail") } catch (e: Backup.NotABackup) {
            assertEquals("This isn’t a Quiet backup.", e.message)
        }
    }

    @Test fun libraryKeepsUnknownFields() {
        val lib = Library(context, null)
        lib.put(app.quietreader.data.Book(JSONObject("""{"id":"a","title":"T","fileName":"t.epub","tags":["x"],"rating":4}"""), null))
        val again = lib.get("a")!!
        assertEquals(4, again.json.getInt("rating"))
        assertEquals("x", again.json.getJSONArray("tags").getString(0))
    }
}
