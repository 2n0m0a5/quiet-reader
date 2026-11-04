package app.quietreader

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.quietreader.ui.AppState
import app.quietreader.ui.QuietApp
import app.quietreader.ui.QuietTheme
import app.quietreader.ui.SettingsPage
import app.quietreader.ui.Tab
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every L0 screen in Day and Night, saved as pictures (build/outputs/roborazzi/), plus the
 * basics: tabs switch, Settings pages open and go back, the theme is remembered.
 * Robolectric runs this on the JVM: the cloud machine can't run an Android emulator.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreensTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var settings: QuietSettings
    private lateinit var quiet: Quiet

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("quiet", Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.deleteRecursively()
        quiet = Quiet(context, dbName = null)
        settings = quiet.settings
    }

    private fun show(state: AppState) = compose.setContent {
        androidx.compose.runtime.CompositionLocalProvider(LocalQuiet provides quiet) { QuietTheme(settings, null) { QuietApp(settings, state) } }
    }

    /** The library with books (restored from the web app's backup) and the Files page. */
    @Test fun libraryWithBooks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = java.io.File(context.cacheDir, "web.quietbackup")
        javaClass.classLoader!!.getResourceAsStream("web-0.4.2.quietbackup")!!.use { i -> file.outputStream().use { i.copyTo(it) } }
        quiet.backup.restore(file)
        val lamp = quiet.library.books.value.first { it.title == "The Lamp and the Clock" }
        quiet.library.put(lamp.with({ put("progress", 0.42); put("lastOpenedAt", System.currentTimeMillis()) }))
        val notes = quiet.library.books.value.first { it.title == "Short Notes" }
        quiet.library.put(notes.with({ put("progress", 1.0); put("lastOpenedAt", 1L) }))
        val state = AppState()
        show(state)
        shot("day-library-books")
        compose.runOnIdle { state.tab = Tab.Settings; state.page = SettingsPage.Files }
        shot("day-files")
        compose.runOnIdle { settings.theme = ThemeMode.Night; state.tab = Tab.Library }
        shot("night-library-books")
    }

    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun everyScreenDayAndNight() {
        val state = AppState()
        show(state)
        for (theme in listOf(ThemeMode.Day, ThemeMode.Night)) {
            val tag = theme.label.lowercase()
            compose.runOnIdle { settings.theme = theme }
            for (tab in Tab.entries) {
                compose.runOnIdle { state.tab = tab; state.page = SettingsPage.Home }
                shot("$tag-${tab.name.lowercase()}")
            }
            compose.runOnIdle { state.tab = Tab.Settings; state.page = SettingsPage.Interface }
            shot("$tag-interface")
            compose.runOnIdle { state.page = SettingsPage.About }
            shot("$tag-about")
        }
    }

    @Test fun tabsAndPagesWork() {
        val state = AppState()
        show(state)
        compose.onNodeWithText("Make yourself at home.").assertIsDisplayed()
        compose.onNodeWithText("Restore a backup").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Interface").performClick()
        compose.onNodeWithText("Screen rotation").assertIsDisplayed()
        compose.onNodeWithText("Night").performClick()
        compose.runOnIdle { assertEquals(ThemeMode.Night, settings.theme) }
        // Remembered: a fresh settings object reads the same value back.
        val again = QuietSettings(ApplicationProvider.getApplicationContext<Context>())
        assertEquals(ThemeMode.Night, again.theme)
        compose.runOnIdle { state.page = SettingsPage.Home }
        compose.onNodeWithText("About Quiet").performClick()
        compose.onNodeWithText("made by extrasolarions on howling dark").assertExists()
        compose.onNodeWithText("Notes").performClick()
        compose.onNodeWithText("Your notes will gather here.").assertIsDisplayed()
    }

    @Test fun textSizeScalesTheApp() {
        settings.textScale = 130
        show(AppState(Tab.Settings, SettingsPage.Interface))
        shot("day-interface-larger-text")
    }
}
