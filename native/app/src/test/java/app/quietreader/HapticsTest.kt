package app.quietreader

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import app.quietreader.ui.AppState
import app.quietreader.ui.Haptics
import app.quietreader.ui.QuietApp
import app.quietreader.ui.QuietTheme
import app.quietreader.ui.SettingsPage
import app.quietreader.ui.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Vibration: keys, tabs and choices click; turning it off stops them; turning it on confirms. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class HapticsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var settings: QuietSettings
    private val clicks = mutableListOf<String>()

    private inner class Counting(context: Context) : Haptics(context, { settings.haptics }) {
        override fun perform(confirm: Boolean) { clicks += if (confirm) "confirm" else "tick" }
    }

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("quiet", Context.MODE_PRIVATE).edit().clear().commit()
        val quiet = Quiet(context, dbName = null)
        settings = quiet.settings
        val haptics = Counting(context)
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalQuiet provides quiet) {
                QuietTheme(settings, haptics) { QuietApp(settings, AppState(Tab.Settings, SettingsPage.Interface)) }
            }
        }
    }

    @Test fun clicksThenStopsThenConfirms() {
        compose.onNodeWithText("Night").performClick()
        assertEquals(listOf("tick"), clicks)

        compose.onNodeWithText("Vibration").performScrollTo().performClick() // off
        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithText("Notes").performClick()
        assertEquals(listOf("tick"), clicks)

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Interface").performClick()
        compose.onNodeWithText("Vibration").performScrollTo().performClick() // on again
        assertEquals("confirm", clicks.last())
    }

    @Test fun textSizeDialClicksAtEachStep() {
        compose.onNodeWithContentDescription("Text size").performSemanticsAction(SemanticsActions.SetProgress) { it(120f) }
        compose.runOnIdle { assertEquals(120, settings.textScale) }
        compose.onNodeWithContentDescription("Text size").performScrollTo().performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(150, settings.textScale) }
        assertTrue("slider should click as it moves", clicks.count { it == "tick" } >= 3)
    }
}
