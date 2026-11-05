package app.quietreader

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Frames from the middle of the motions, in the real app, to look at (build/outputs/roborazzi/motion-*.png). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class MotionTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun app(): QuietApplication {
        val app = ApplicationProvider.getApplicationContext<QuietApplication>()
        app.getSharedPreferences("quiet", Context.MODE_PRIVATE).edit().clear().putBoolean("tourDone", true)
            .putString("seenVersion", "${BuildConfig.QUIET_VERSION} ${BuildConfig.QUIET_LAYER}").commit()
        return app
    }

    private fun frame(name: String, ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/motion-$name.png")
    }

    @Test fun bookGrowsOutOfItsCover() {
        val app = app()
        val file = java.io.File(app.cacheDir, "web.quietbackup")
        javaClass.classLoader!!.getResourceAsStream("web-0.4.2.quietbackup")!!.use { i -> file.outputStream().use { i.copyTo(it) } }
        app.quiet.backup.restore(file)
        val title = app.quiet.library.books.value.first().title
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.onAllNodesWithText(title).onFirst().performClick()
            frame("open-1", 64)
            frame("open-2", 130)
            frame("open-3", 1200)
        }
    }

    @Test fun settingsPushAndChoiceSlide() {
        app()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithText("Settings").performClick()
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText("Interface").performClick()
            frame("push-a", 50); frame("push-b", 100); frame("push-c", 200)
            compose.mainClock.advanceTimeBy(1500)
            compose.onNodeWithText("Night").performClick()
            frame("choice-a", 50); frame("choice-b", 100); frame("choice-c", 200)
        }
    }
}
