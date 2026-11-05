package app.quietreader

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The real app, from the first start: the tour, then the first book shows the reader tips. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class FlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun firstStartTourThenReaderTips() {
        val app = ApplicationProvider.getApplicationContext<QuietApplication>()
        app.getSharedPreferences("quiet", Context.MODE_PRIVATE).edit().clear().commit()
        val file = java.io.File(app.cacheDir, "sample.epub")
        javaClass.classLoader!!.getResourceAsStream("sample.epub")!!.use { i -> file.outputStream().use { i.copyTo(it) } }
        kotlinx.coroutines.runBlocking { app.quiet.importer.importFile(file, "sample.epub") }
        val title = app.quiet.library.books.value.first().title
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Skip").performClick()
            compose.onNodeWithText(title).performClick()
            compose.mainClock.advanceTimeBy(2500)
            compose.onNodeWithText("Turning pages").assertExists()
        }
    }
}
