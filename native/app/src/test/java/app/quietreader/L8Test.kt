package app.quietreader

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.quietreader.reader.BottomBar
import app.quietreader.reader.ReaderState
import app.quietreader.ui.AppState
import app.quietreader.ui.CoachMarks
import app.quietreader.ui.QuietApp
import app.quietreader.ui.QuietTheme
import app.quietreader.ui.SettingsPage
import app.quietreader.ui.Tab
import app.quietreader.ui.buildReport
import app.quietreader.ui.contrast
import app.quietreader.ui.readerTips
import app.quietreader.ui.resolveColors
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

/** L8: the tour, What's new, Report a problem, custom colours, TalkBack, large text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class L8Test {
    @get:Rule val compose = createComposeRule()
    private lateinit var quiet: Quiet
    private lateinit var settings: QuietSettings

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("quiet", Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.deleteRecursively()
        quiet = Quiet(context, dbName = null)
        settings = quiet.settings
    }

    private fun show(state: AppState) = compose.setContent {
        CompositionLocalProvider(LocalQuiet provides quiet) { QuietTheme(settings, null) { QuietApp(settings, state) } }
    }

    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun tourWalksThroughAndEnds() {
        val state = AppState()
        show(state)
        compose.runOnIdle { state.startTour() }
        compose.onNodeWithText("Add books").assertExists()
        shot("day-tour-1")
        // An empty library has no first book, so the shelf tip is left out: 6 tips.
        compose.onNodeWithText("1 / 6").assertExists()
        repeat(5) { compose.onNodeWithText("Next").performClick() }
        compose.onNodeWithText("Keep a backup").assertExists()
        shot("day-tour-last")
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle {
            assertEquals(null, state.tourStep)
            assertTrue(settings.tourDone)
        }
    }

    @Test fun readerTipsPointAtTheBar() {
        val reader = ReaderState().apply { progress = 0.3; page = 120; pages = 400; pageLabel = "120 / 400"; stations = listOf(0.1, 0.4, 0.7) }
        compose.setContent {
            CompositionLocalProvider(LocalQuiet provides quiet) {
                QuietTheme(settings, null) {
                    Box(Modifier.fillMaxSize()) {
                        BottomBar(reader, Modifier.align(Alignment.BottomCenter)) {}
                        var step by remember { mutableIntStateOf(0) }
                        val tips = readerTips.filter { it.anywhere || it.key in reader.targets }
                        CoachMarks(tips, reader.targets, step, onNext = { step++ }, onSkip = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Turning pages").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Your place").assertExists()
        shot("day-reader-tips")
    }

    @Test fun whatsNewAndReport() {
        ErrorLog.keep("Import failed", RuntimeException("boom"))
        val report = buildReport(quiet, ApplicationProvider.getApplicationContext())
        assertTrue(report, report.contains("Quiet 0.5.0 L8.1"))
        assertTrue(report, report.contains("boom"))
        val state = AppState(Tab.Settings, SettingsPage.About)
        show(state)
        compose.onNodeWithText("The tour").assertExists()
        shot("day-about-l8")
        compose.onNodeWithText("What’s new").performClick()
        compose.onNodeWithText("The curl folds the book’s page only", substring = true).assertExists()
        shot("day-whats-new")
        compose.runOnIdle { state.guide = "report" }
        compose.onNode(hasText("Report a problem") and isHeading()).assertExists()
        shot("day-report")
    }

    @Test fun customColours() {
        settings.theme = ThemeMode.Custom
        settings.customBg = "#18202e"
        settings.customText = "#e8e2d4"
        var colors: app.quietreader.ui.QuietColors? = null
        compose.setContent {
            colors = resolveColors(ThemeMode.Custom, settings)
            CompositionLocalProvider(LocalQuiet provides quiet) { QuietTheme(settings, null) { QuietApp(settings, AppState(Tab.Settings, SettingsPage.Interface)) } }
        }
        compose.runOnIdle {
            assertTrue(colors!!.isDark)
            assertEquals(Color(0xFF18202E), colors!!.readerBg)
        }
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.01)
        assertTrue(contrast(Color(0xFF18202E), Color(0xFFE8E2D4)) > 4.5)
        compose.onNodeWithText("It was a bright cold day", substring = true).assertExists()
        shot("custom-interface")
    }

    @Test fun talkBackReadsStates() {
        settings.theme = ThemeMode.Day
        show(AppState(Tab.Settings, SettingsPage.Interface))
        // Choices say which one is chosen; the whole vibration row is one switch.
        compose.onNodeWithText("Day").assertIsSelected()
        compose.onNodeWithText("Night").assertIsNotSelected()
        compose.onNodeWithText("Vibration").assertIsOn()
        compose.onNodeWithText("Vibration").performScrollTo().performClick()
        compose.onNodeWithText("Vibration").assertIsOff()
        compose.runOnIdle { assertEquals(false, settings.haptics) }
        // Titles are headings, so TalkBack can jump between them.
        compose.onNode(hasText("Interface") and isHeading()).assertExists()
        compose.onNode(hasText("Theme") and isHeading()).assertExists()
    }

    @Test fun largestTextFits() {
        settings.textScale = TextScale.MAX
        val state = AppState()
        show(state)
        shot("day-library-150")
        compose.runOnIdle { state.tab = Tab.Settings }
        shot("day-settings-150")
        compose.runOnIdle { state.page = SettingsPage.About }
        shot("day-about-150")
    }
}
