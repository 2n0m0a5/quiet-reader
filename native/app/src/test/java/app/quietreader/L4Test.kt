package app.quietreader

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.quietreader.data.Note
import app.quietreader.reader.NoteEditor
import app.quietreader.reader.decorationOf
import app.quietreader.reader.locatorOf
import app.quietreader.reader.quotePicture
import app.quietreader.ui.AppState
import app.quietreader.ui.QuietApp
import app.quietreader.ui.QuietTheme
import app.quietreader.ui.Tab
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.services.search.search
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** L4: notes from 0.4.2 find their place, search finds words, quote pictures, the Notes tab. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class L4Test {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var quiet: Quiet

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("quiet", Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.deleteRecursively()
        quiet = Quiet(context, dbName = null)
        val file = File(context.cacheDir, "web.quietbackup")
        javaClass.classLoader!!.getResourceAsStream("web-0.4.2.quietbackup")!!.use { i -> file.outputStream().use { i.copyTo(it) } }
        quiet.backup.restore(file)
    }

    @Test fun webNotesFindTheirChapterAndText() = runBlocking {
        val note = quiet.notes.all().single()
        val book = quiet.library.get(note.bookId)!!
        val publication = quiet.readium.open(quiet.library.fileOf(book.id))!!
        val locator = locatorOf(note, publication)!!
        assertEquals(publication.readingOrder[0].url().removeFragment(), locator.href.removeFragment())
        assertEquals("The river below the bridge.", locator.text.highlight)
        assertNotNull(decorationOf(note, publication))
        assertTrue("bookmarks aren't drawn", decorationOf(note.with { put("type", "bookmark") }, publication) == null)
    }

    @Test fun searchFindsWords() = runBlocking {
        val book = quiet.library.books.value.first { it.title == "The Lamp and the Clock" }
        val publication = quiet.readium.open(quiet.library.fileOf(book.id))!!
        val iterator = publication.search("river")!!
        val first = iterator.next().getOrNull()
        assertTrue("found 'river'", (first?.locators?.size ?: 0) > 0)
        assertEquals("river", first!!.locators[0].text.highlight!!.lowercase())
    }

    @Test fun quotePictureAndScreens() {
        val note = quiet.notes.all().single().with { put("quote", true); put("speaker", "Ada"); put("style", "plain") }
        quiet.notes.put(note)
        val book = quiet.library.get(note.bookId)
        val bmp = quotePicture(context, note, book, "Ada")
        File("build/outputs/roborazzi").mkdirs()
        File("build/outputs/roborazzi/quote-picture.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        assertEquals(1080, bmp.width)

        quiet.notes.put(Note.new(note.bookId, "bookmark", "", "").with { put("label", "Chapter 1 · page 3"); put("progress", 0.2) })
        val state = AppState(Tab.Notes)
        compose.setContent { CompositionLocalProvider(LocalQuiet provides quiet) { QuietTheme(quiet.settings, null) { QuietApp(quiet.settings, state) } } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("A note to keep").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/day-notes-tab.png")
    }

    @Test fun noteEditor() {
        val note = quiet.notes.all().single()
        compose.setContent {
            CompositionLocalProvider(LocalQuiet provides quiet) {
                QuietTheme(quiet.settings, null) { NoteEditor(note, quiet.library.get(note.bookId)) {} }
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Save to Quotes").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/day-note-editor.png")
    }
}
