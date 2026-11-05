package app.quietreader.reader

import app.quietreader.ui.click
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.quietreader.Align
import app.quietreader.LocalQuiet
import app.quietreader.Margins
import app.quietreader.QuietSettings
import app.quietreader.ReaderKey
import app.quietreader.ThemeMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import app.quietreader.ui.ChoiceRow
import app.quietreader.ui.Dial
import app.quietreader.ui.Divider
import app.quietreader.ui.Icons
import app.quietreader.ui.Key
import app.quietreader.ui.KeyKind
import app.quietreader.ui.KeyShape
import app.quietreader.ui.LayerTag
import app.quietreader.ui.LocalColors
import app.quietreader.ui.LocalHaptics
import app.quietreader.ui.LocalReducedMotion
import app.quietreader.ui.Switch
import app.quietreader.ui.SwitchRow
import app.quietreader.ui.TextKey
import app.quietreader.ui.Type
import app.quietreader.ui.hairline
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun ReaderKey.icon(dark: Boolean): ImageVector = when (this) {
    ReaderKey.Contents -> Icons.ListIcon
    ReaderKey.DayNight -> if (dark) Icons.Sun else Icons.Moon
    ReaderKey.Light -> Icons.Sun
    ReaderKey.Text -> Icons.TypeIcon
    ReaderKey.Layout -> Icons.Layout
    ReaderKey.GoTo -> Icons.Hash
    ReaderKey.Mode -> Icons.BookOpen
    ReaderKey.Notes -> Icons.Bookmark
    ReaderKey.Search -> Icons.Search
    ReaderKey.Bookmark -> Icons.Bookmark
}

private fun ReaderKey.title(dark: Boolean) = if (this == ReaderKey.DayNight) (if (dark) "Day" else "Night") else label

/**
 * The bottom of the reader, within thumb's reach: your place as a tuning scale (chapters are the
 * stations, the orange needle is you, drag it to move), the page and time left, then four keys
 * you choose and More.
 */
@Composable
fun BottomBar(state: ReaderState, modifier: Modifier, onKey: (ReaderKey?) -> Unit) {
    val c = LocalColors.current
    val settings = LocalQuiet.current.settings
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = state.barsShown,
        modifier = modifier,
        enter = if (reduced) fadeIn(snap()) else fadeIn(tween(180)) + slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 520f)) { it },
        exit = if (reduced) fadeOut(snap()) else fadeOut(tween(160)) + slideOutVertically(androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 520f)) { it },
    ) {
        Column(
            Modifier.fillMaxWidth().background(c.panelDeep).hairline(c.line, top = true)
                .windowInsetsPadding(WindowInsets.navigationBars).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        ) {
            state.backTo?.let { (label, back) ->
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.Center) {
                    TextKey("‹ Back to $label", { back(); state.backTo = null })
                }
            }
            TuningScale(state)
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.pageLabel.ifBlank { "${(state.progress * 100).roundToInt()}%" }, style = Type.mono.copy(color = c.text, fontSize = 12.sp))
                Spacer(Modifier.weight(1f))
                Text(timeLeft(state), style = Type.mono.copy(color = c.muted, fontSize = 12.sp))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                settings.readerKeys.forEach { key ->
                    val icon = if (key == ReaderKey.Bookmark && state.bookmarkHere) Icons.BookmarkFilled else key.icon(c.isDark)
                    BarKey(icon, key.title(c.isDark), Modifier.weight(1f)) { onKey(key) }
                }
                BarKey(Icons.More, "More", Modifier.weight(1f)) { onKey(null) }
            }
        }
    }
}

/** About 1 minute per 1.4 Readium positions (~1,024 characters each, ~250 words a minute). */
private fun timeLeft(state: ReaderState): String {
    if (state.pages <= 0 || state.page <= 0) return ""
    val minutes = ((state.pages - state.page) * 0.7).roundToInt()
    return when {
        minutes < 1 -> "almost done"
        minutes < 60 -> "$minutes min left"
        else -> "${minutes / 60} h ${minutes % 60} min left"
    }
}

/** The scale: a track, a station mark at each chapter, the orange needle at your place. Drag or tap to move. */
@Composable
private fun TuningScale(state: ReaderState, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    var drag by remember { mutableFloatStateOf(-1f) } // 0–1 while dragging
    val shown = if (drag >= 0f) drag.toDouble() else state.progress
    Canvas(
        modifier.fillMaxWidth().height(30.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> state.markBack(); state.goToProgress((o.x / size.width).toDouble().coerceIn(0.0, 1.0)); haptics?.tick() }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { state.markBack(); state.goToProgress(drag.toDouble()); drag = -1f; haptics?.tick() },
                    onDragCancel = { drag = -1f },
                ) { change, _ ->
                    change.consume()
                    val next = (change.position.x / size.width).coerceIn(0f, 1f)
                    // A click as the needle passes a station.
                    if (state.stations.any { s -> (s - drag) * (s - next) < 0 }) haptics?.tick()
                    drag = next
                }
            },
    ) {
        val y = size.height / 2
        drawLine(c.keyEdge, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
        for (i in 0..20) {
            val x = size.width * i / 20
            val h = if (i % 5 == 0) 5.dp.toPx() else 2.5.dp.toPx()
            drawLine(c.keyEdge, Offset(x, y - h), Offset(x, y + h), 1.dp.toPx())
        }
        state.stations.forEach { s ->
            val x = (size.width * s).toFloat()
            drawLine(c.text.copy(alpha = 0.55f), Offset(x, y - 8.dp.toPx()), Offset(x, y - 2.dp.toPx()), 1.5.dp.toPx())
        }
        val x = (size.width * shown.toFloat()).coerceIn(0f, size.width)
        drawLine(c.accent, Offset(x, 1.dp.toPx()), Offset(x, size.height - 1.dp.toPx()), 2.5.dp.toPx())
    }
}

@Composable
private fun BarKey(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    Key(onClick, modifier, padding = PaddingValues(vertical = 8.dp), contentDescription = label) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(18.dp), tint = c.text)
            Spacer(Modifier.height(3.dp))
            Text(label, style = Type.tab.copy(color = c.text, fontSize = 11.sp), maxLines = 1)
        }
    }
}

// ---------- Window: brightness and keep-awake while reading ----------

@Composable
fun ReaderWindow(settings: QuietSettings) {
    val activity = LocalContext.current as? Activity ?: return
    LaunchedEffect(settings.brightness) {
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = if (settings.brightness < 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            else (settings.brightness / 100f).coerceIn(0.01f, 1f)
        }
    }
    LaunchedEffect(settings.keepAwake) {
        if (settings.keepAwake) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    DisposableEffect(Unit) {
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
}

/** Warm light: an amber layer over the page (it doesn't catch touches). */
@Composable
fun Warmth(warmth: Int) {
    if (warmth <= 0) return
    Box(Modifier.fillMaxSize().background(Color(0xFFFF9A3C).copy(alpha = warmth / 100f * 0.32f)))
}

/** Time and battery, small, in the corner while reading. */
@Composable
fun Clock(modifier: Modifier) {
    val c = LocalColors.current
    val context = LocalContext.current
    val text by produceState("") {
        while (true) {
            val battery = (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) + (battery?.takeIf { it in 0..100 }?.let { " · $it%" } ?: "")
            delay(20_000)
        }
    }
    Text(text, style = Type.mono.copy(color = c.readerText.copy(alpha = 0.5f), fontSize = 11.sp),
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 4.dp, end = 12.dp))
}

// ---------- Sheets ----------

@Composable
private fun SheetColumn(title: String, content: @Composable () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
        Text(title, style = Type.section.copy(color = c.text))
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun Labelled(label: String, value: String = "", content: @Composable () -> Unit) {
    val c = LocalColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.label.copy(color = c.text), modifier = Modifier.weight(1f))
        if (value.isNotBlank()) Text(value, style = Type.mono.copy(color = c.text))
    }
    Spacer(Modifier.height(6.dp))
    content()
    Spacer(Modifier.height(16.dp))
}

/** More: every reader control, in one list. */
@Composable
fun MoreSheet(pdf: Boolean, open: (String?) -> Unit) {
    val c = LocalColors.current
    val settings = LocalQuiet.current.settings
    Sheet({ open(null) }) {
        SheetColumn("More") {
            Labelled("Theme") { ChoiceRow(ThemeMode.entries.map { it to it.label }, settings.theme, { settings.theme = it }) }
            Labelled("Reading ruler") { ChoiceRow(app.quietreader.Ruler.entries.map { it to it.label }, settings.ruler, { settings.ruler = it }, perRow = 3) }
            if (!pdf) {
                SwitchRow(settings.bionic, { settings.bionic = it }, padding = PaddingValues(vertical = 10.dp)) {
                    Text("Bionic reading", style = Type.label.copy(color = c.text), modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
            }
            Column(Modifier.fillMaxWidth().background(c.panel, KeyShape).border(1.dp, c.line, KeyShape)) {
                @Composable fun row(label: String, icon: ImageVector, layer: String? = null, go: () -> Unit) {
                    Row(Modifier.fillMaxWidth().click(enabled = layer == null) { go() }.padding(14.dp).alpha(if (layer == null) 1f else 0.55f),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(18.dp), tint = c.text)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = Type.body.copy(color = c.text), modifier = Modifier.weight(1f))
                        if (layer != null) LayerTag(layer) else Icon(Icons.ChevronRight, null, Modifier.size(16.dp), tint = c.muted)
                    }
                }
                row("Light: brightness and warmth", Icons.Sun) { open("light") }
                Divider()
                if (!pdf) { row("Text size and font", Icons.TypeIcon) { open("text") }; Divider() }
                if (!pdf) { row("Look & layout", Icons.Layout) { open("layout") }; Divider() }
                row("Contents", Icons.ListIcon) { open("contents") }
                Divider()
                row("Go to page", Icons.Hash) { open("goto") }
                Divider()
                row("Customize bar", Icons.Settings) { open("keys") }
                Divider()
                row("Notes, quotes & bookmarks", Icons.Bookmark) { open("notes") }
                Divider()
                row("Search in book", Icons.Search) { open("search") }
                Divider()
                row("Book info", Icons.Info) { open("info") }
            }
        }
    }
}

/** Light: screen brightness (or the phone's), and warm light. */
@Composable
fun LightSheet(onClose: () -> Unit) {
    val settings = LocalQuiet.current.settings
    Sheet(onClose) {
        SheetColumn("Light") {
            val follow = settings.brightness < 0
            Labelled("Brightness", if (follow) "phone" else "${settings.brightness}%") {
                ChoiceRow(listOf(true to "Follow phone", false to "Quiet’s own"), follow, { f ->
                    settings.brightness = if (f) -1 else 60
                }, perRow = 2)
                if (!follow) {
                    Spacer(Modifier.height(8.dp))
                    Dial(settings.brightness.toFloat(), { settings.brightness = it.roundToInt() }, 5f..100f, 5f, "Brightness", "${settings.brightness} percent")
                }
            }
            Labelled("Warmth", if (settings.warmth == 0) "off" else "${settings.warmth}%") {
                Dial(settings.warmth.toFloat(), { settings.warmth = it.roundToInt() }, 0f..100f, 5f, "Warmth", "${settings.warmth} percent", marks = listOf(0f))
            }
        }
    }
}

/** Look & layout: line spacing, margins, alignment, hyphenation, weight, paragraphs. */
@Composable
fun LayoutSheet(pdf: Boolean, onClose: () -> Unit) {
    val settings = LocalQuiet.current.settings
    val c = LocalColors.current
    Sheet(onClose) {
        SheetColumn("Look & layout") {
            if (pdf) { Text("PDF pages keep their own layout.", style = Type.body.copy(color = c.muted)); return@SheetColumn }
            Labelled("Line spacing", "%.1f".format(settings.lineHeight / 10.0)) {
                Dial(settings.lineHeight.toFloat(), { settings.lineHeight = it.roundToInt() }, 10f..25f, 1f, "Line spacing", "${settings.lineHeight / 10.0}", marks = listOf(17f))
            }
            Labelled("Margins") { ChoiceRow(Margins.entries.map { it to it.label }, settings.margins, { settings.margins = it }, perRow = 3) }
            Labelled("Alignment") { ChoiceRow(Align.entries.map { it to it.label }, settings.align, { settings.align = it }, perRow = 2) }
            Labelled("Weight", "${settings.fontWeight}") {
                Dial(settings.fontWeight.toFloat(), { settings.fontWeight = it.roundToInt() }, 300f..700f, 50f, "Weight", "${settings.fontWeight}", marks = listOf(400f))
            }
            Labelled("Paragraph spacing", if (settings.paragraphSpacing < 0) "book’s" else "%.1f em".format(settings.paragraphSpacing / 10.0)) {
                Dial((settings.paragraphSpacing + 1).toFloat(), { settings.paragraphSpacing = it.roundToInt() - 1 }, 0f..21f, 1f, "Paragraph spacing", "", marks = listOf(0f))
            }
            Labelled("First-line indent", if (settings.indent < 0) "book’s" else "%.1f em".format(settings.indent / 10.0)) {
                Dial((settings.indent + 1).toFloat(), { settings.indent = it.roundToInt() - 1 }, 0f..31f, 1f, "First-line indent", "", marks = listOf(0f))
            }
            SwitchRow(settings.hyphenate, { settings.hyphenate = it }, padding = PaddingValues(vertical = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Hyphenation", style = Type.label.copy(color = c.text))
                    Text("Off: whole words move to the next line.", style = Type.hint.copy(color = c.muted))
                }
            }
            Spacer(Modifier.height(16.dp))
            TextKey("Back to the book’s own layout", {
                settings.lineHeight = 17; settings.margins = Margins.Normal; settings.align = Align.Start; settings.hyphenate = false
                settings.fontWeight = 400; settings.paragraphSpacing = -1; settings.indent = -1
            })
        }
    }
}

/** Go to a page or a percentage. Where you were is kept, to go back. */
@Composable
fun GoToSheet(state: ReaderState, onClose: () -> Unit) {
    val c = LocalColors.current
    var page by remember { mutableIntStateOf(state.page.coerceAtLeast(1)) }
    Sheet(onClose) {
        SheetColumn("Go to") {
            if (state.pages > 0) {
                Labelled("Page", "$page / ${state.pages}") {
                    Dial(page.toFloat(), { page = it.roundToInt() }, 1f..state.pages.toFloat().coerceAtLeast(2f), 1f, "Page", "page $page",
                        marks = state.stations.map { (it * state.pages).toFloat() })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextKey("−10", { page = (page - 10).coerceAtLeast(1) }, Modifier.weight(1f))
                    TextKey("−1", { page = (page - 1).coerceAtLeast(1) }, Modifier.weight(1f))
                    TextKey("+1", { page = (page + 1).coerceAtMost(state.pages) }, Modifier.weight(1f))
                    TextKey("+10", { page = (page + 10).coerceAtMost(state.pages) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                TextKey("Go to page $page", { state.markBack(); state.goToPage(page); onClose() }, Modifier.fillMaxWidth(), kind = KeyKind.Primary)
            } else {
                Text("Pages are still being counted…", style = Type.body.copy(color = c.muted))
            }
        }
    }
}

/** Customize bar: pick four keys and drag them into order; More always stays. */
@Composable
fun CustomizeSheet(onClose: () -> Unit) {
    Sheet(onClose) { SheetColumn("Customize bar") { CustomizeKeys() } }
}

@Composable
fun CustomizeKeys() {
    val c = LocalColors.current
    val settings = LocalQuiet.current.settings
    val haptics = LocalHaptics.current
    val chosen = settings.readerKeys
    Text("Your four keys, in order. Hold and drag ≡ to move one; tap a key below to swap it in.", style = Type.hint.copy(color = c.muted))
    Spacer(Modifier.height(10.dp))
    val rowHeight = 52.dp
    var dragging by remember { mutableIntStateOf(-1) }
    var dy by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxWidth().background(c.panel, KeyShape).border(1.dp, c.line, KeyShape)) {
        chosen.forEachIndexed { i, key ->
            if (i > 0) Divider()
            val lifted = dragging == i
            Row(
                Modifier.fillMaxWidth().height(rowHeight).zIndex(if (lifted) 1f else 0f)
                    .offset { IntOffset(0, if (lifted) dy.roundToInt() else 0) }
                    .background(if (lifted) c.bg else c.panel).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", style = Type.mono.copy(color = c.muted), modifier = Modifier.width(22.dp))
                Icon(key.icon(c.isDark), null, Modifier.size(18.dp), tint = c.text)
                Spacer(Modifier.width(12.dp))
                Text(key.label, style = Type.body.copy(color = c.text), modifier = Modifier.weight(1f))
                Text("≡", style = Type.section.copy(color = c.muted), modifier = Modifier.padding(8.dp).pointerInput(chosen) {
                    val step = rowHeight.toPx()
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragging = i; dy = 0f; haptics?.confirm() },
                        onDragEnd = { dragging = -1; dy = 0f },
                        onDragCancel = { dragging = -1; dy = 0f },
                    ) { change, amount ->
                        change.consume()
                        dy += amount.y
                        val list = settings.readerKeys
                        val from = dragging
                        val to = (from + (dy / step).roundToInt()).coerceIn(0, list.size - 1)
                        if (to != from && abs(dy) > step / 2) {
                            settings.readerKeys = list.toMutableList().apply { add(to, removeAt(from)) }
                            dy -= (to - from) * step
                            dragging = to
                            haptics?.tick()
                        }
                    }
                })
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Other keys", style = Type.label.copy(color = c.text))
    Spacer(Modifier.height(8.dp))
    var replacing by remember { mutableIntStateOf(3) }
    Text("Tap one to put it in place of key ${replacing + 1}:", style = Type.hint.copy(color = c.muted))
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chosen.indices.forEach { i ->
            TextKey("${i + 1}", { replacing = i }, Modifier.weight(1f), kind = if (i == replacing) KeyKind.Primary else KeyKind.Normal)
        }
    }
    Spacer(Modifier.height(8.dp))
    ReaderKey.entries.filter { it !in chosen }.forEach { key ->
        Row(Modifier.fillMaxWidth().clickable {
            haptics?.tick()
            settings.readerKeys = chosen.toMutableList().apply { set(replacing.coerceAtMost(size - 1), key) }
        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(key.icon(c.isDark), null, Modifier.size(18.dp), tint = c.text)
            Spacer(Modifier.width(12.dp))
            Text(key.label, style = Type.body.copy(color = c.text, fontWeight = FontWeight.Normal))
        }
    }
    Spacer(Modifier.height(10.dp))
    TextKey("Reset to Contents · Day/Night · Light · Text", { settings.readerKeys = ReaderKey.DEFAULT })
}
