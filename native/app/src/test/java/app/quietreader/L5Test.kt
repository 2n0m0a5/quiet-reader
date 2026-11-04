package app.quietreader

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onLast
import androidx.test.core.app.ApplicationProvider
import app.quietreader.data.Book
import app.quietreader.data.Group
import app.quietreader.data.Shelf
import app.quietreader.data.Sort
import app.quietreader.data.Status
import app.quietreader.data.collectionIds
import app.quietreader.data.status
import app.quietreader.data.withCollection
import app.quietreader.data.withStatus
import app.quietreader.ui.AppState
import app.quietreader.ui.Filter
import app.quietreader.ui.QuietApp
import app.quietreader.ui.QuietTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class L5Test {
    @get:Rule val compose = createComposeRule()
    private lateinit var quiet: Quiet

    private fun book(id: String, title: String, author: String = "", extra: JSONObject.() -> Unit = {}) =
        Book(JSONObject().apply { put("id", id); put("title", title); put("author", author); put("fileName", "$id.epub"); put("addedAt", id.hashCode().toLong()); put("progress", 0); extra() }, null)

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("quiet", Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.deleteRecursively()
        quiet = Quiet(context, dbName = null)
    }

    @Test fun statusesLikeTheWebApp() {
        assertEquals(Status.New, book("a", "A").status())
        assertEquals(Status.Reading, book("a", "A") { put("lastOpenedAt", 5L); put("progress", 0.3) }.status())
        assertEquals(Status.Finished, book("a", "A") { put("progress", 0.999) }.status())
        assertEquals(Status.Want, book("a", "A") { put("wantToRead", true) }.status())
        assertEquals(Status.Reading, book("a", "A") { put("progress", 0.999); put("lastOpenedAt", 5L) }.withStatus(Status.Reading).status())
        assertEquals(Status.Finished, book("a", "A").withStatus(Status.Finished).status())
        // Back to New (asked first in the app): the place is cleared.
        val read = Book(book("a", "A") { put("lastOpenedAt", 5L); put("progress", 0.42); put("cfi", "epubcfi(/6/4!/4)") }.json, "{\"href\":\"x\"}")
        val again = read.withStatus(Status.New)
        assertEquals(Status.New, again.status())
        assertEquals(0.0, again.progress, 0.0)
        assertEquals(null, again.locator)
        assertEquals(null, again.cfi)
    }

    @Test fun sortGroupSeriesDuplicates() {
        val books = listOf(
            book("1", "The Second", "Ada") { put("series", "River"); put("seriesIndex", 2.0); put("rating", 4) },
            book("2", "A First", "Ada") { put("series", "River"); put("seriesIndex", 1.0); put("progress", 1.0) },
            book("3", "Lamp", "Omar") { put("size", 500L) },
            book("4", "Lamp", "Omar") { put("size", 900L) },
        )
        assertEquals(listOf("2", "3", "4", "1"), Shelf.sort(books, Sort.Title).map { it.id }.let { listOf(it[0], it[1], it[2], it[3]) })
        assertEquals("1", Shelf.sort(books, Sort.Rating).first().id)
        assertEquals(listOf("Ada", "Omar"), Shelf.group(books, Group.Author, emptyMap()).map { it.first })
        val series = Shelf.series(books).single()
        assertEquals(listOf("2", "1"), series.books.map { it.id })
        assertEquals("1", series.next?.id) // #1 is finished, so #2 is next
        assertEquals(listOf(setOf("3", "4")), Shelf.duplicates(books).map { g -> g.map { it.id }.toSet() })
        assertTrue(Shelf.matches(books[0], "river"))
    }

    @Test fun collectionsKeepBooksSafe() {
        val id = quiet.collections.create("Summer")
        quiet.library.put(book("a", "A").withCollection(id, true))
        assertEquals(listOf(id), quiet.library.get("a")!!.collectionIds)
        quiet.collections.delete(id)
        assertTrue(quiet.library.get("a")!!.collectionIds.isEmpty())
        assertEquals(1, quiet.library.books.value.size)
    }

    /** The owner's L5 report: a collection chosen on the book's page must stay after closing it. */
    @Test fun bookPageSavesAtOnce() {
        quiet.library.put(book("a", "A"))
        val summer = quiet.collections.create("Summer")
        val state = AppState().apply { details = "a" }
        compose.setContent { CompositionLocalProvider(LocalQuiet provides quiet) { QuietTheme(quiet.settings, null) { QuietApp(quiet.settings, state) } } }
        compose.mainClock.advanceTimeBy(800)
        compose.onAllNodesWithText("Summer").onLast().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(summer), quiet.library.get("a")!!.collectionIds) }
        compose.onAllNodesWithText("Want").onLast().performClick()
        // Close the page the way the reader does (Read): nothing is undone.
        // Back to New asks first.
        compose.runOnIdle { quiet.library.put(quiet.library.get("a")!!.with({ put("lastOpenedAt", 5L); put("progress", 0.3) })) }
        compose.onAllNodesWithText("New").onLast().performClick()
        compose.onNodeWithText("Mark as new?").assertExists()
        compose.onNodeWithText("Mark as new").performClick()
        compose.runOnIdle { assertEquals(Status.New, quiet.library.get("a")!!.status()); quiet.library.put(quiet.library.get("a")!!.withStatus(Status.Want)) }
        compose.onNodeWithText("Read").performScrollTo().performClick()
        compose.runOnIdle {
            val b = quiet.library.get("a")!!
            assertEquals(listOf(summer), b.collectionIds)
            assertEquals(Status.Want, b.status())
        }
        // And out again.
        compose.runOnIdle { state.openBookId = null; state.details = "a" }
        compose.mainClock.advanceTimeBy(800)
        compose.onAllNodesWithText("✓ Summer").onLast().performScrollTo().performClick()
        compose.runOnIdle { assertTrue(quiet.library.get("a")!!.collectionIds.isEmpty()) }
    }

    /** Hold a cover to select; Info for one; Remove gives Undo; New always asks (even from Want). */
    @Test fun holdToSelectUndoAndNewAsks() {
        quiet.library.putAll(listOf(book("a", "Alpha") { put("wantToRead", true) }, book("b", "Beta"), book("c", "Gamma")))
        val state = AppState()
        compose.setContent { CompositionLocalProvider(LocalQuiet provides quiet) { QuietTheme(quiet.settings, null) { QuietApp(quiet.settings, state) } } }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Beta").performTouchInput { longClick() }
        compose.runOnIdle { assertTrue(state.selecting); assertEquals(setOf("b"), state.selected) }
        compose.onNodeWithText("Gamma").performClick()
        compose.runOnIdle { assertEquals(setOf("b", "c"), state.selected) }
        compose.onNodeWithText("Gamma").performClick()
        compose.onNodeWithText("Info").assertExists()
        compose.onNodeWithText("Remove").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Undo").assertExists()
        compose.runOnIdle { assertTrue(quiet.library.get("b")!!.deletedAt != null) }
        compose.onNodeWithText("Undo").performClick()
        compose.runOnIdle { assertEquals(null, quiet.library.get("b")!!.deletedAt) }
        // New from Want asks.
        compose.runOnIdle { state.details = "a" }
        compose.mainClock.advanceTimeBy(800)
        compose.onAllNodesWithText("New").onLast().performClick()
        compose.onNodeWithText("Mark as new?").assertExists()
        compose.onNodeWithText("It will leave your Want to read list. Highlights, notes and bookmarks stay.").assertExists()
    }

    @Test fun libraryScreens() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "web.quietbackup")
        javaClass.classLoader!!.getResourceAsStream("web-0.4.2.quietbackup")!!.use { i -> file.outputStream().use { i.copyTo(it) } }
        quiet.backup.restore(file)
        val ids = quiet.library.books.value.map { it.id }
        quiet.library.putAll(quiet.library.books.value.mapIndexed { i, b -> b.with({ put("series", "The River Trilogy"); put("seriesIndex", i + 1) }) })
        quiet.library.put(quiet.library.get(ids[0])!!.withStatus(Status.Finished))
        val c = quiet.collections.create("Summer")
        quiet.library.put(quiet.library.get(ids[1])!!.withCollection(c, true).with({ put("rating", 4) }))

        val state = AppState()
        compose.setContent { CompositionLocalProvider(LocalQuiet provides quiet) { QuietTheme(quiet.settings, null) { QuietApp(quiet.settings, state) } } }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Summer").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/day-library-l5.png")
        compose.runOnIdle { state.filter = Filter.Series }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("The River Trilogy").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/day-series.png")
        compose.runOnIdle { state.filter = Filter.All; state.details = ids[1] }
        compose.mainClock.advanceTimeBy(1500)
        compose.onAllNodes(androidx.compose.ui.test.isRoot()).onLast().captureRoboImage("build/outputs/roborazzi/day-book-page.png")
        compose.runOnIdle { state.details = null; state.selecting = true; state.selected = setOf(ids[0], ids[2]); quiet.settings.libraryList = true }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("2 selected").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/day-library-select.png")
    }
}
