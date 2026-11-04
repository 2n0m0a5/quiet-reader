package app.quietreader

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.quietreader.reader.rememberOpened
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A book stays open while it's on screen, and is closed once when it leaves (the L2 blank-page bug). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenedTest {
    @get:Rule val compose = createComposeRule()

    class Book(val name: String) { var closed = 0 }

    @Test fun staysOpenWhileShownAndClosesOnceWhenGone() {
        val opened = mutableListOf<Book>()
        var shown by mutableStateOf(true)
        var id by mutableStateOf("a")
        compose.setContent {
            if (shown) {
                val book = rememberOpened(id, open = { Book(id).also { opened += it } }, close = { it.closed++ })
                Text(book?.let { "${it.name} closed=${it.closed}" } ?: "opening")
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("a closed=0").assertExists()
        assertEquals(0, opened.single().closed)

        id = "b" // another book: the first closes, the second stays open
        compose.waitForIdle()
        compose.onNodeWithText("b closed=0").assertExists()
        assertEquals(listOf(1, 0), opened.map { it.closed })

        shown = false // leaving the reader closes it
        compose.waitForIdle()
        assertEquals(listOf(1, 1), opened.map { it.closed })
    }
}
