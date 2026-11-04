package app.quietreader.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** One day's reading of one book, in the web app's form (db.ts ReadingDay). */
data class ReadingDay(
    val day: String, val bookId: String, val seconds: Double, val pages: Double,
    val hours: DoubleArray, val sittings: Int,
) {
    val id get() = "$day|$bookId"
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("day", day); put("bookId", bookId); put("seconds", seconds); put("pages", pages)
        put("hours", JSONArray(hours.map { it })); put("sittings", sittings)
    }

    companion object {
        fun of(j: JSONObject) = ReadingDay(
            j.optString("day"), j.optString("bookId"), j.optDouble("seconds", 0.0), j.optDouble("pages", 0.0),
            DoubleArray(24) { h -> j.optJSONArray("hours")?.optDouble(h, 0.0)?.takeIf { !it.isNaN() } ?: 0.0 },
            j.optInt("sittings", 0),
        )
    }
}

fun dayKey(millis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

/** Reading time in the "reading" records: added to while you read, read by the Stats screen. */
class ReadingLog(private val library: Library) {
    fun all(): List<ReadingDay> = library.records(STORE).map(ReadingDay::of).filter { it.day.isNotBlank() }

    /** Adds a stretch of reading (the tracker saves every 30 seconds). */
    fun add(day: String, bookId: String, seconds: Double, pages: Double, hours: DoubleArray, newSitting: Boolean) {
        val id = "$day|$bookId"
        val old = library.records(STORE).firstOrNull { it.optString("id") == id }?.let(ReadingDay::of)
        val merged = ReadingDay(
            day, bookId, (old?.seconds ?: 0.0) + seconds, (old?.pages ?: 0.0) + pages,
            DoubleArray(24) { (old?.hours?.get(it) ?: 0.0) + hours[it] }, (old?.sittings ?: 0) + if (newSitting) 1 else 0,
        )
        library.putRecords(STORE, listOf(merged.toJson()))
    }

    companion object { const val STORE = "reading" }
}

/** The Stats screen's numbers, worked out like the web app's (src/stats/stats.ts). */
object StatsMath {
    const val WORDS_PER_PAGE = 250

    data class Week(val day: String, val label: String, val seconds: Double)
    data class Summary(
        val today: Double, val streak: Int, val week: List<Week>, val weekTotal: Double, val yearTotal: Double, val allTotal: Double,
        val wordsPerMinute: Int?, val finishedThisYear: Int,
    )

    private fun wpm(seconds: Double, pages: Double): Int? =
        if (seconds >= 300 && pages >= 3) ((pages * WORDS_PER_PAGE / (seconds / 60)) / 10).roundToInt() * 10 else null

    fun summary(days: List<ReadingDay>, books: List<Book>, dailyGoalMinutes: Int, today: LocalDate = LocalDate.now()): Summary {
        val perDay = days.groupBy { it.day }.mapValues { (_, v) -> v.sumOf { it.seconds } }
        val enough = { d: LocalDate -> (perDay[d.toString()] ?: 0.0) >= maxOf(60, dailyGoalMinutes * 60) }
        var streak = 0
        var cursor = if (enough(today)) today else today.minusDays(1)
        while (enough(cursor)) { streak++; cursor = cursor.minusDays(1) }
        val week = (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            Week(d.toString(), if (back == 0) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), perDay[d.toString()] ?: 0.0)
        }
        val since = today.minusDays(29).toString()
        val recent = days.filter { it.day >= since }
        val recentSeconds = recent.sumOf { it.seconds }
        val recentPages = recent.sumOf { it.pages }
        val speed = if (recentSeconds >= 600 && recentPages >= 5) ((recentPages * WORDS_PER_PAGE / (recentSeconds / 60)) / 10).roundToInt() * 10 else null
        val year = today.year.toString()
        val finishedThisYear = books.count { b ->
            b.deletedAt == null && b.status() == Status.Finished &&
                Instant.ofEpochMilli(b.json.optLong("finishedAt", 0).takeIf { it > 0 } ?: b.lastOpenedAt ?: b.addedAt)
                    .atZone(ZoneId.systemDefault()).year == today.year
        }
        return Summary(
            today = perDay[today.toString()] ?: 0.0, streak = streak, week = week, weekTotal = week.sumOf { it.seconds },
            yearTotal = perDay.filterKeys { it.startsWith(year) }.values.sum(), allTotal = perDay.values.sum(),
            wordsPerMinute = speed, finishedThisYear = finishedThisYear,
        )
    }

    // ---------- The five detailed stats ----------

    data class Detail(
        val calendar: List<Pair<LocalDate, Double>>,  // 26 weeks, Monday first; future days are after today
        val daysThisYear: Int, val longestRun: Int, val bestDay: Pair<String, Double>?,
        val hours: DoubleArray, val weekdays: DoubleArray,
        val weeks: List<Pair<LocalDate, Double>>, val sitting: Double?, val perReadingDay: Double?, val speedNow: Int?, val speedBefore: Int?,
        val finishedMonths: List<Pair<LocalDate, Int>>, val daysToFinish: Double?, val finishedAll: Int,
        val books: List<BookRow>,
    )
    data class BookRow(val book: Book, val seconds: Double, val pages: Double, val days: Int, val wpm: Int?, val minutesLeft: Int?)

    fun detail(days: List<ReadingDay>, books: List<Book>, today: LocalDate = LocalDate.now()): Detail {
        val zone = ZoneId.systemDefault()
        val perDay = days.groupBy { it.day }.mapValues { (_, v) -> v.sumOf { it.seconds } }
        val monday = today.minusDays(((today.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7).toLong())
        val start = monday.minusWeeks(25)
        val calendar = (0 until 26 * 7).map { i -> start.plusDays(i.toLong()).let { it to (perDay[it.toString()] ?: 0.0) } }
        val readDays = perDay.filterValues { it >= 60 }.keys.sorted()
        var longest = 0; var run = 0; var prev: LocalDate? = null
        for (d in readDays.map(LocalDate::parse)) {
            run = if (prev != null && ChronoUnit.DAYS.between(prev, d) == 1L) run + 1 else 1
            longest = maxOf(longest, run); prev = d
        }
        val best = perDay.maxByOrNull { it.value }?.toPair()
        val hours = DoubleArray(24); days.forEach { d -> d.hours.forEachIndexed { h, s -> hours[h] += s } }
        val weekdays = DoubleArray(7); perDay.forEach { (d, s) -> weekdays[LocalDate.parse(d).dayOfWeek.value - 1] += s }
        val weeks = (11 downTo 0).map { back ->
            val from = monday.minusWeeks(back.toLong()); val to = from.plusWeeks(1)
            from to days.filter { it.day >= from.toString() && it.day < to.toString() }.sumOf { it.seconds }
        }
        val withSittings = days.filter { it.sittings > 0 }
        val sittingCount = withSittings.sumOf { it.sittings }
        val sitting = if (sittingCount > 0) withSittings.sumOf { it.seconds } / sittingCount else null
        val perReadingDay = readDays.takeIf { it.isNotEmpty() }?.let { r -> r.sumOf { perDay[it]!! } / r.size }
        fun speedBetween(fromBack: Long, toBack: Long): Int? {
            val a = today.minusDays(fromBack).toString(); val b = today.minusDays(toBack).toString()
            val w = days.filter { it.day > a && it.day <= b }
            return wpm(w.sumOf { it.seconds }, w.sumOf { it.pages })
        }
        val finished = books.filter { it.deletedAt == null && it.status() == Status.Finished }
        val finishedMonths = (11 downTo 0).map { back ->
            val from = today.withDayOfMonth(1).minusMonths(back.toLong()); val to = from.plusMonths(1)
            from to finished.count { b ->
                val at = b.json.optLong("finishedAt", 0)
                at > 0 && Instant.ofEpochMilli(at).atZone(zone).toLocalDate().let { it >= from && it < to }
            }
        }
        val firstDay = days.groupBy { it.bookId }.mapValues { (_, v) -> v.minOf { it.day } }
        val toFinish = finished.mapNotNull { b ->
            val first = firstDay[b.id] ?: return@mapNotNull null
            val at = b.json.optLong("finishedAt", 0).takeIf { it > 0 } ?: return@mapNotNull null
            maxOf(1L, ChronoUnit.DAYS.between(LocalDate.parse(first), Instant.ofEpochMilli(at).atZone(zone).toLocalDate())).toDouble()
        }.sorted()
        val median = if (toFinish.isEmpty()) null else if (toFinish.size % 2 == 1) toFinish[toFinish.size / 2] else (toFinish[toFinish.size / 2 - 1] + toFinish[toFinish.size / 2]) / 2
        val byBook = books.associateBy { it.id }
        val rows = days.groupBy { it.bookId }.mapNotNull { (id, list) ->
            val book = byBook[id] ?: return@mapNotNull null
            val seconds = list.sumOf { it.seconds }; val pages = list.sumOf { it.pages }
            val speed = wpm(seconds, pages)
            val words = book.json.optInt("words", 0)
            val left = if (words > 0 && book.status() != Status.Finished) ((1 - book.progress) * words / (speed ?: 230)).roundToInt() else null
            BookRow(book, seconds, pages, list.count { it.seconds >= 60 }, speed, left)
        }.sortedByDescending { it.seconds }
        return Detail(
            calendar, readDays.count { it.startsWith(today.year.toString()) }, longest, best, hours, weekdays,
            weeks, sitting, perReadingDay, speedBetween(30, 0), speedBetween(60, 30),
            finishedMonths, median, finished.size, rows,
        )
    }

    /** "45 min", "2 h 5 min", "0 min". */
    fun time(seconds: Double): String {
        val m = (seconds / 60).roundToInt()
        return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
    }
}
