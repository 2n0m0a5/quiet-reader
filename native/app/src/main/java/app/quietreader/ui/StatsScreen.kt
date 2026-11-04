package app.quietreader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quietreader.LocalQuiet
import app.quietreader.data.StatsMath
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Stats, like a radio's front: a needle meter for today against your goal, the week as level
 * lights, three readouts (streak, words a minute, books this year), and the five detailed stats
 * one tap away. Made from the "reading" records (the same as 0.4.2's, so a backup brings them).
 */
@Composable
fun StatsScreen() {
    val quiet = LocalQuiet.current
    val settings = quiet.settings
    val c = LocalColors.current
    val version by quiet.library.recordsVersion.collectAsState()
    val books by quiet.library.books.collectAsState()
    val days = remember(version) { quiet.readingLog.all() }
    val s = remember(days, books, settings.dailyGoal) { StatsMath.summary(days, books, settings.dailyGoal) }
    var more by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Section("Today") {
            Meter(s.today, settings.dailyGoal)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${(s.today / 60).roundToInt()}", style = Type.monoLarge.copy(color = c.text, fontSize = 30.sp))
                Spacer(Modifier.width(6.dp))
                Text("min today", style = Type.hint.copy(color = c.muted), modifier = Modifier.padding(bottom = 5.dp))
            }
            Spacer(Modifier.height(4.dp))
            val goal = settings.dailyGoal * 60.0
            Text(
                when {
                    goal == 0.0 -> "Choose a daily goal below, if you’d like one."
                    s.today >= goal -> "Today’s goal is done. Well read."
                    else -> "${StatsMath.time(goal - s.today)} to your goal."
                },
                style = Type.hint.copy(color = if (goal > 0 && s.today >= goal) c.done else c.muted),
            )
            Spacer(Modifier.height(14.dp))
            Text("Daily goal", style = Type.label.copy(color = c.text))
            Spacer(Modifier.height(8.dp))
            ChoiceRow(listOf(0 to "Off", 10 to "10 min", 20 to "20 min", 30 to "30 min", 60 to "1 hour"), settings.dailyGoal, { settings.dailyGoal = it }, perRow = 5)
        }
        Section("This week") {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(StatsMath.time(s.weekTotal), style = Type.monoLarge.copy(color = c.text, fontSize = 24.sp))
                Spacer(Modifier.width(8.dp))
                Text("in the last 7 days", style = Type.hint.copy(color = c.muted), modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            WeekLights(s.week)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Readout(s.streak.toString(), if (s.streak == 1) "day in a row" else "days in a row", Modifier.weight(1f))
            Readout(s.wordsPerMinute?.toString() ?: "–", "words a minute", Modifier.weight(1f))
            Readout(if (settings.yearGoal > 0) "${s.finishedThisYear}/${settings.yearGoal}" else s.finishedThisYear.toString(), "books this year", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Books a year", style = Type.label.copy(color = c.text))
                Spacer(Modifier.height(8.dp))
                ChoiceRow(listOf(0 to "Off", 6 to "6", 12 to "12", 24 to "24", 52 to "52"), settings.yearGoal, { settings.yearGoal = it }, perRow = 5)
                Spacer(Modifier.height(10.dp))
                Text("This year ${StatsMath.time(s.yearTotal)} · all time ${StatsMath.time(s.allTotal)}", style = Type.mono.copy(color = c.muted, fontSize = 12.sp))
            }
        }
        Spacer(Modifier.height(18.dp))
        TextKey(if (more) "Fewer stats" else "More stats: your year, when, pace, finished, book by book", { more = !more }, Modifier.fillMaxWidth())
        if (more) Details()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    Text(title, style = Type.section.copy(color = c.text), modifier = Modifier.asHeading())
    Spacer(Modifier.height(10.dp))
    Card { Column(Modifier.padding(16.dp), content = content) }
    Spacer(Modifier.height(22.dp))
}

/** The needle meter: 0 to your goal (the orange stretch is past it), or 0 to an hour without one. */
@Composable
private fun Meter(today: Double, goalMinutes: Int) {
    val c = LocalColors.current
    val goal = goalMinutes * 60.0
    val full = if (goal > 0) goal * 1.25 else 3600.0
    val target = (-50f + 100f * (today / full).toFloat().coerceIn(0f, 1.04f))
    val angle by animateFloatAsState(target, glide(), label = "needle")
    val label = "Today: ${(today / 60).roundToInt()} of ${if (goal > 0) goalMinutes else 60} minutes"
    Box(Modifier.fillMaxWidth().aspectRatio(2.1f).background(c.bg, KeyShape).border(1.dp, c.line, KeyShape).semantics { contentDescription = label }) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val cx = size.width / 2; val cy = size.height * 0.98f
            val r = minOf(size.width / 2 * 0.92f, size.height * 0.92f)
            fun at(deg: Float, radius: Float) = Offset(cx + radius * sin(Math.toRadians(deg.toDouble())).toFloat(), cy - radius * cos(Math.toRadians(deg.toDouble())).toFloat())
            val box = androidx.compose.ui.geometry.Rect(Offset(cx - r, cy - r), Size(r * 2, r * 2))
            val goalDeg = if (goal > 0) 30f else 50f
            drawArc(c.text, -90f - 50f, 50f + goalDeg, false, box.topLeft, box.size, style = Stroke(2.dp.toPx()))
            if (goal > 0) drawArc(c.accent, -90f + goalDeg, 50f - goalDeg, false, box.topLeft, box.size, style = Stroke(4.dp.toPx()))
            for (d in -50..50 step 5) {
                val long = d % 25 == 0
                drawLine(c.text, at(d.toFloat(), r), at(d.toFloat(), r - (if (long) 12 else 6).dp.toPx()), (if (long) 1.5f else 1f).dp.toPx())
            }
            drawLine(c.text, at(angle, -6.dp.toPx()), at(angle, r - 8.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round)
            drawCircle(c.text, 6.dp.toPx(), Offset(cx, cy))
        }
        val max = if (goal > 0) goalMinutes else 60
        Text("0", style = Type.mono.copy(color = c.muted, fontSize = 11.sp), modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 26.dp))
        Text("$max", style = Type.mono.copy(color = c.muted, fontSize = 11.sp),
            modifier = Modifier.align(Alignment.TopEnd).padding(end = if (goal > 0) 48.dp else 16.dp, top = if (goal > 0) 22.dp else 40.dp))
    }
    Spacer(Modifier.height(12.dp))
}

/** The week as level lights: ten cells a day, lit by how long you read; today's in orange. */
@Composable
private fun WeekLights(week: List<StatsMath.Week>) {
    val c = LocalColors.current
    val max = maxOf(week.maxOf { it.seconds }, 1.0)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        week.forEach { d ->
            val lit = if (d.seconds > 0) maxOf(1, (10 * d.seconds / max).roundToInt()) else 0
            // TalkBack: "Mon, 25 minutes".
            val minutes = (d.seconds / 60).roundToInt()
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}.clearAndSetSemantics {
                contentDescription = "${d.label}, " + if (minutes == 0 && d.seconds > 0) "under a minute" else "$minutes ${if (minutes == 1) "minute" else "minutes"}"
            }, horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                    val gap = 3.dp.toPx(); val h = (size.height - gap * 9) / 10
                    for (i in 0 until 10) {
                        val on = i < lit
                        drawRoundRect(
                            if (on) (if (d.label == "Today") c.accent else c.text) else c.panelDeep,
                            topLeft = Offset(0f, size.height - (i + 1) * h - i * gap), size = Size(size.width, h), cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(d.label, style = Type.hint.copy(color = if (d.label == "Today") c.text else c.muted, fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Readout(value: String, label: String, modifier: Modifier) {
    val c = LocalColors.current
    Column(modifier.semantics(mergeDescendants = true) {}.background(c.panel, CardShape).border(1.dp, c.line, CardShape).padding(12.dp)) {
        Text(value, style = Type.monoLarge.copy(color = c.text, fontSize = 24.sp), maxLines = 1)
        Text(label, style = Type.hint.copy(color = c.muted), maxLines = 2)
    }
}

/** Simple bars with labels under some of them. */
@Composable
private fun Bars(values: List<Double>, labels: List<String>, highlightLast: Boolean = false, height: Int = 90, summary: String = "") {
    val c = LocalColors.current
    val max = maxOf(values.maxOrNull() ?: 0.0, 1.0)
    // TalkBack reads the summary; the labels under the bars are left out.
    Canvas(Modifier.fillMaxWidth().height(height.dp).semantics { contentDescription = summary.ifBlank { "Chart" } }) {
        val n = values.size; val gap = 3.dp.toPx(); val w = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val h = (size.height * (v / max)).toFloat().coerceAtLeast(if (v > 0) 2.dp.toPx() else 1f)
            drawRoundRect(if (highlightLast && i == n - 1) c.accent else if (v > 0) c.text else c.panelDeep,
                topLeft = Offset(i * (w + gap), size.height - h), size = Size(w, h), cornerRadius = CornerRadius(2.dp.toPx()))
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        labels.forEach { Text(it, style = Type.mono.copy(color = c.muted, fontSize = 10.sp), modifier = Modifier.weight(1f), maxLines = 1) }
    }
}

@Composable
private fun Line(k: String, v: String) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(k, style = Type.body.copy(color = c.muted), modifier = Modifier.weight(1f))
        Text(v, style = Type.mono.copy(color = c.text))
    }
}

/** The five detailed stats. */
@Composable
private fun Details() {
    val quiet = LocalQuiet.current
    val c = LocalColors.current
    val version by quiet.library.recordsVersion.collectAsState()
    val books by quiet.library.books.collectAsState()
    val d = remember(version, books) { StatsMath.detail(quiet.readingLog.all(), books) }
    val today = LocalDate.now()
    Spacer(Modifier.height(22.dp))

    Section("Your reading year") {
        // 26 weeks, a column each, Monday at the top.
        val max = maxOf(d.calendar.maxOf { it.second }, 1.0)
        Canvas(Modifier.fillMaxWidth().aspectRatio(26f / 7f)) {
            val gap = 2.dp.toPx(); val cell = (size.width - gap * 25) / 26
            d.calendar.forEachIndexed { i, (day, s) ->
                val col = i / 7; val row = i % 7
                val future = day.isAfter(today)
                val level = if (s < 60) 0f else (0.25f + 0.75f * (s / max).toFloat())
                drawRoundRect(
                    when { future -> c.bg; level == 0f -> c.panelDeep; else -> c.accent.copy(alpha = level) },
                    topLeft = Offset(col * (cell + gap), row * (cell + gap)), size = Size(cell, cell), cornerRadius = CornerRadius(1.5.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Line("Days read this year", d.daysThisYear.toString())
        Line("Longest run", "${d.longestRun} days")
        d.bestDay?.let { (day, s) -> Line("Best day", "${LocalDate.parse(day).let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" }} · ${StatsMath.time(s)}") }
    }
    Section("When you read") {
        Bars(d.hours.toList(), listOf("0", "", "", "", "", "", "6", "", "", "", "", "", "12", "", "", "", "", "", "18", "", "", "", "", "23"),
            summary = busiest(d.hours.toList()) { "Reading by hour: most at $it:00" })
        Spacer(Modifier.height(14.dp))
        Bars(d.weekdays.toList(), listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), height = 60,
            summary = busiest(d.weekdays.toList()) { "Reading by weekday: most on ${java.time.DayOfWeek.of(it + 1).getDisplayName(TextStyle.FULL, Locale.ENGLISH)}" })
    }
    Section("Your pace") {
        Bars(d.weeks.map { it.second / 60 }, d.weeks.mapIndexed { i, (start, _) -> if (i % 3 == 0) "${start.dayOfMonth}/${start.monthValue}" else "" }, highlightLast = true,
            summary = "Minutes read each week; this week ${(d.weeks.lastOrNull()?.second?.div(60) ?: 0.0).roundToInt()}")
        Spacer(Modifier.height(10.dp))
        d.sitting?.let { Line("Average sitting", StatsMath.time(it)) }
        d.perReadingDay?.let { Line("On days you read", StatsMath.time(it)) }
        Line("Words a minute, last 30 days", d.speedNow?.toString() ?: "–")
        Line("The 30 days before", d.speedBefore?.toString() ?: "–")
    }
    Section("Finished books") {
        Bars(d.finishedMonths.map { it.second.toDouble() }, d.finishedMonths.mapIndexed { i, (m, _) -> if (i % 2 == 1) m.month.getDisplayName(TextStyle.NARROW, Locale.ENGLISH) else "" }, highlightLast = true, height = 70,
            summary = "Books finished each month; this month ${d.finishedMonths.lastOrNull()?.second ?: 0}")
        Spacer(Modifier.height(10.dp))
        Line("Finished, all time", d.finishedAll.toString())
        d.daysToFinish?.let { Line("Days to finish a book (median)", fmt(it)) }
    }
    Section("Book by book") {
        if (d.books.isEmpty()) Text("Books you read show up here, with their time.", style = Type.body.copy(color = c.muted))
        d.books.forEachIndexed { i, r ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(r.book, Modifier.width(34.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.book.title, style = Type.rowTitle.copy(color = c.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(StatsMath.time(r.seconds), "${r.days} days", r.wpm?.let { "$it wpm" }, r.minutesLeft?.let { "${StatsMath.time(it * 60.0)} left" })
                        .joinToString(" · "), style = Type.mono.copy(color = c.muted, fontSize = 11.sp))
                }
            }
        }
    }
}

/** "most at …" for a chart, or "nothing yet". */
private fun busiest(values: List<Double>, say: (Int) -> String): String {
    val i = values.indices.maxByOrNull { values[it] } ?: return "Nothing read yet"
    return if (values[i] <= 0.0) "Nothing read yet" else say(i)
}
