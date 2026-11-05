package app.quietreader

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import app.quietreader.ui.AppState
import app.quietreader.ui.Filter
import app.quietreader.ui.QuietApp
import app.quietreader.ui.QuietTheme
import app.quietreader.ui.SettingsPage
import app.quietreader.ui.Tab
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Now reading bar and swiping between shelves. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class NowReadingTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var quiet: Quiet

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("quiet", Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.deleteRecursively()
        quiet = Quiet(context, dbName = null)
        val file = java.io.File(context.cacheDir, "web.quietbackup")
        javaClass.classLoader!!.getResourceAsStream("web-0.4.2.quietbackup")!!.use { i -> file.outputStream().use { i.copyTo(it) } }
        quiet.backup.restore(file)
        val lamp = quiet.library.books.value.first { it.title == "The Lamp and the Clock" }
        quiet.library.put(lamp.with({ put("progress", 0.42); put("lastOpenedAt", System.currentTimeMillis()); put("finished", false) }))
    }

    private fun show(state: AppState) = compose.setContent {
        CompositionLocalProvider(LocalQuiet provides quiet) { QuietTheme(quiet.settings, null) { QuietApp(quiet.settings, state) } }
    }

    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun barShowsTheBookAndTucksAway() {
        val state = AppState()
        show(state)
        compose.onNodeWithContentDescription("Read The Lamp and the Clock").assertExists()
        assertTrue(compose.onAllNodesWithText("42%", substring = true).fetchSemanticsNodes().isNotEmpty())
        shot("now-bar-library")
        compose.runOnIdle { state.tab = Tab.Stats }
        shot("now-bar-stats")
        // Swipe it down: tucked away (with Undo).
        compose.onNodeWithContentDescription("Read The Lamp and the Clock").performTouchInput { swipeDown() }
        compose.runOnIdle { assertTrue(state.nowTucked) }
        compose.onNodeWithText("Undo").assertExists()
    }

    @Test fun barSettings() {
        quiet.settings.nowBarOpacity = 60
        show(AppState(Tab.Settings, SettingsPage.Interface))
        compose.onNodeWithText("Show the Now reading bar").assertExists()
        shot("now-bar-settings")
        compose.runOnIdle { quiet.settings.nowBar = false }
        compose.onNodeWithContentDescription("Read The Lamp and the Clock").assertDoesNotExist()
    }

    @Test fun swipeBetweenShelves() {
        val state = AppState()
        show(state)
        compose.onAllNodesWithText("Short Notes").onFirst().performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertEquals(Filter.Reading, state.filter) }
        shot("shelf-reading")
    }
}
