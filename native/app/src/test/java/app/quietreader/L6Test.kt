package app.quietreader

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.quietreader.data.Book
import app.quietreader.data.ReadingDay
import app.quietreader.data.StatsMath
import app.quietreader.data.Status
import app.quietreader.data.withStatus
import app.quietreader.reader.Tracker
import app.quietreader.ui.AppState
import app.quietreader.ui.QuietApp
import app.quietreader.ui.QuietTheme
import app.quietreader.ui.Tab
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class L6Test {
    @get:Rule val compose = createComposeRule()
    private lateinit var quiet: Quiet
    private val today = LocalDate.now()

    private fun day(back: Long, minutes: Double, pages: Double = minutes, book: String = "a", hour: Int = 21) =
        ReadingDay(today.minusDays(back).toString(), book, minutes * 60, pages, DoubleArray(24) { if (it == hour) minutes * 60 else 0.0 }, 1)

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("quiet", Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.deleteRecursively()
        quiet = Quiet(context, dbName = null)
    }

    @Test fun streakWeekAndSpeed() {
        val days = listOf(day(0, 25.0), day(1, 40.0), day(2, 12.0), day(4, 30.0))
        val s = StatsMath.summary(days, emptyList(), dailyGoalMinutes = 0, today = today)
        assertEquals(3, s.streak) // today, yesterday, the day before; then a gap
        assertEquals(25.0 * 60, s.today, 0.1)
        assertEquals(7, s.week.size)
        assertEquals("Today", s.week.last().label)
        // 107 pages of 250 words in 107 minutes: 250 words a minute.
        assertEquals(250, s.wordsPerMinute)
        // With a 30-minute goal only yesterday and the day 4 back count.
        // With a 30-minute goal: today (25 min) isn’t done yet, yesterday (40) counts, the day before (12) doesn’t.
        assertEquals(1, StatsMath.summary(days, emptyList(), 30, today).streak)
    }

    @Test fun trackerCountsOnlyActiveReading() {
        val tracker = Tracker(quiet.readingLog, "a") { 0L }
        val start = System.currentTimeMillis()
        repeat(12) { i -> tracker.tick(visible = true, now = start + i * 5_000L) }      // a minute of reading
        tracker.tick(visible = false, now = start + 61_000L)                              // off screen: no time
        tracker.tick(visible = true, now = start + 10 * 60_000L)                          // idle for minutes: no time
        tracker.moved(10, 0.68); tracker.moved(11, 0.68); tracker.moved(40, 0.68)        // one page, then a jump
        tracker.save()
        val day = quiet.readingLog.all().single()
        assertEquals(60.0, day.seconds, 0.1)
        assertEquals(0.68, day.pages, 0.001)
        assertEquals(1, day.sittings)
        assertEquals(60.0, tracker.sessionSeconds, 0.1)
    }

    @Test fun statsScreen() {
        val book = Book(JSONObject("""{"id":"a","title":"The Quiet River","author":"Ada Lane","fileName":"a.epub","addedAt":1,"progress":0.4,"lastOpenedAt":5,"words":60000}"""), null)
        val done = Book(JSONObject("""{"id":"b","title":"Short Notes","author":"Lina Moss","fileName":"b.epub","addedAt":1,"progress":1.0}"""), null)
            .withStatus(Status.Finished)
        quiet.library.putAll(listOf(book, done))
        val days = (0L..40L).filter { it % 3 != 2L }.map { day(it, (10..55).random().toDouble(), book = if (it % 2 == 0L) "a" else "b", hour = listOf(7, 13, 21, 22).random()) }
        quiet.library.putRecords("reading", days.map { it.toJson() })
        quiet.settings.dailyGoal = 30
        quiet.settings.yearGoal = 12
        val state = AppState(Tab.Stats)
        compose.setContent { CompositionLocalProvider(LocalQuiet provides quiet) { QuietTheme(quiet.settings, null) { QuietApp(quiet.settings, state) } } }
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithText("This week").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/day-stats-l6.png")
        compose.onNodeWithText("More stats", substring = true).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Book by book").performScrollTo()
        compose.onNodeWithText("Your reading year").performScrollTo()
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/day-stats-more.png")
        assertTrue(quiet.readingLog.all().isNotEmpty())
    }
}
