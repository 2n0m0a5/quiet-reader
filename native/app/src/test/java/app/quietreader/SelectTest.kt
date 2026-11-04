package app.quietreader

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.quietreader.data.Book
import app.quietreader.data.Note
import app.quietreader.ui.AppState
import app.quietreader.ui.QuietApp
import app.quietreader.ui.QuietTheme
import app.quietreader.ui.SettingsPage
import app.quietreader.ui.Tab
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Hold to select, everywhere there's a list: Recently deleted and Notes (the library has its own test). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class SelectTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var quiet: Quiet

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("quiet", Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.deleteRecursively()
        quiet = Quiet(context, dbName = null)
    }

    private fun show(state: AppState) = compose.setContent {
        CompositionLocalProvider(LocalQuiet provides quiet) { QuietTheme(quiet.settings, null) { QuietApp(quiet.settings, state) } }
    }

    @Test fun recentlyDeletedSelectsSeveral() {
        val now = System.currentTimeMillis()
        quiet.library.putAll(listOf("Alpha", "Beta", "Gamma").map { t ->
            Book(JSONObject().put("id", t).put("title", t).put("fileName", "$t.epub").put("addedAt", 1).put("progress", 0).put("deletedAt", now), null)
        })
        show(AppState(Tab.Settings, SettingsPage.Trash))
        compose.onNodeWithText("Alpha").performTouchInput { longClick() }
        compose.onNodeWithText("Beta").performClick()
        compose.onNodeWithText("2 selected").assertExists()
        compose.onNodeWithText("Put back").performClick()
        compose.runOnIdle {
            assertNull(quiet.library.get("Alpha")!!.deletedAt); assertNull(quiet.library.get("Beta")!!.deletedAt)
        }
        compose.onNodeWithText("Gamma").performTouchInput { longClick() }
        compose.onNodeWithText("Delete for good…").performClick()
        compose.onNodeWithText("Delete “Gamma” for good?").assertExists()
        compose.onNodeWithText("Delete for good").performClick()
        compose.runOnIdle { assertNull(quiet.library.get("Gamma")) }
    }

    @Test fun notesSelectAndDeleteWithUndo() {
        quiet.library.put(Book(JSONObject().put("id", "b").put("title", "Book").put("fileName", "b.epub").put("addedAt", 1).put("progress", 0), null))
        val one = Note.new("b", "highlight", "First passage", "").with { put("createdAt", 2) }
        val two = Note.new("b", "highlight", "Second passage", "").with { put("createdAt", 1) }
        quiet.notes.put(one); quiet.notes.put(two)
        show(AppState(Tab.Notes))
        compose.onNodeWithText("First passage").performTouchInput { longClick() }
        compose.onNodeWithText("Second passage").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(0, quiet.notes.all().size) }
        compose.onNodeWithText("Undo").performClick()
        compose.runOnIdle { assertEquals(2, quiet.notes.all().size) }
    }
}
