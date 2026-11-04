package app.quietreader.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.quietreader.BookFont
import app.quietreader.BookSize
import app.quietreader.Flow
import app.quietreader.LocalQuiet
import app.quietreader.ThemeMode
import app.quietreader.data.Book
import app.quietreader.data.BookFormat
import app.quietreader.ui.ChoiceRow
import app.quietreader.ui.Dial
import app.quietreader.ui.Divider
import app.quietreader.ui.IconKey
import app.quietreader.ui.Icons
import app.quietreader.ui.KeyShape
import app.quietreader.ui.KnobMark
import app.quietreader.ui.LocalColors
import app.quietreader.ui.LocalHaptics
import app.quietreader.ui.LocalReducedMotion
import app.quietreader.ui.QuietColors
import app.quietreader.ui.Type
import app.quietreader.ui.hairline
import kotlin.math.roundToInt

/** One entry of a book's contents, flattened with its depth. */
data class ContentsEntry(val title: String, val depth: Int, val key: Any)

/** What a book view tells the chrome around it. */
class ReaderState {
    var chapter by mutableStateOf("")
    var progress by mutableStateOf(0.0)       // 0–1 through the whole book
    var pageLabel by mutableStateOf("")       // "12 / 340"
    var contents by mutableStateOf<List<ContentsEntry>>(emptyList())
    var currentContents by mutableStateOf<Any?>(null)
    var barsShown by mutableStateOf(true)
    var goTo: (ContentsEntry) -> Unit = {}
    fun toggleBars() { barsShown = !barsShown }
}

/** The book's page colours for the theme (Radio's reading colours). */
fun pageColors(c: QuietColors) = c.readerBg to c.readerText

/**
 * Reading a book: the page fills the screen; tap the middle for the bars (Back and the chapter
 * at the top; Contents, Day/Night, Text and your place at the bottom, within thumb's reach).
 * The full reader bar (tuning scale, five keys, More) comes in L3.
 */
@Composable
fun ReaderScreen(book: Book, onClose: () -> Unit) {
    val quiet = LocalQuiet.current
    val settings = quiet.settings
    val c = LocalColors.current
    val state = remember(book.id) { ReaderState() }
    var sheet by remember { mutableStateOf<String?>(null) }

    BackHandler { onClose() }
    ImmersiveWhileReading(hidden = !state.barsShown)

    Box(Modifier.fillMaxSize().background(c.readerBg)) {
        when (book.format) {
            BookFormat.Pdf -> PdfReader(book, state)
            else -> EpubReader(book, state)
        }
        ReaderBars(
            state = state,
            book = book,
            onBack = onClose,
            onContents = { sheet = "contents" },
            onDayNight = { settings.theme = if (c.isDark) ThemeMode.Day else ThemeMode.Night },
            onText = { sheet = "text" },
        )
    }

    when (sheet) {
        "contents" -> ContentsSheet(state, book.format == BookFormat.Pdf) { sheet = null }
        "text" -> TextSheet(book.format == BookFormat.Pdf) { sheet = null }
    }
}

/** Hides the phone's status and navigation bars while the reader's own bars are hidden. */
@Composable
private fun ImmersiveWhileReading(hidden: Boolean) {
    val view = LocalView.current
    val activity = LocalContext.current as? Activity ?: return
    val controller = remember { WindowCompat.getInsetsController(activity.window, view) }
    LaunchedEffect(hidden) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(Unit) { onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) } }
}

@Composable
private fun ReaderBars(
    state: ReaderState,
    book: Book,
    onBack: () -> Unit,
    onContents: () -> Unit,
    onDayNight: () -> Unit,
    onText: () -> Unit,
) {
    val c = LocalColors.current
    val reduced = LocalReducedMotion.current
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.barsShown,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = if (reduced) fadeIn(snap()) else fadeIn(tween(180)) + slideInVertically(tween(220)) { -it },
            exit = if (reduced) fadeOut(snap()) else fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it },
        ) {
            Row(
                Modifier.fillMaxWidth().background(c.bg).hairline(c.line).windowInsetsPadding(WindowInsets.statusBars)
                    .height(56.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconKey(Icons.ChevronLeft, "Back to library", onBack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.chapter.ifBlank { book.title }, style = Type.rowTitle.copy(color = c.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.chapter.isNotBlank()) Text(book.title, style = Type.hint.copy(color = c.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        AnimatedVisibility(
            visible = state.barsShown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (reduced) fadeIn(snap()) else fadeIn(tween(180)) + slideInVertically(tween(220)) { it },
            exit = if (reduced) fadeOut(snap()) else fadeOut(tween(160)) + slideOutVertically(tween(200)) { it },
        ) {
            Column(
                Modifier.fillMaxWidth().background(c.panelDeep).hairline(c.line, top = true)
                    .windowInsetsPadding(WindowInsets.navigationBars).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            ) {
                // Your place: a thin scale with the orange needle, and the readout.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressScale(state.progress, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Text("${(state.progress * 100).roundToInt()}%", style = Type.mono.copy(color = c.text))
                }
                if (state.pageLabel.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(state.pageLabel, style = Type.mono.copy(color = c.muted, fontSize = Type.hint.fontSize))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderKey(Icons.ListIcon, "Contents", Modifier.weight(1f), onContents)
                    ReaderKey(if (c.isDark) Icons.Sun else Icons.Moon, if (c.isDark) "Day" else "Night", Modifier.weight(1f), onDayNight)
                    ReaderKey(Icons.TypeIcon, "Text", Modifier.weight(1f), onText)
                }
            }
        }
    }
}

/** The progress scale: a hairline track, ticks every tenth, and the orange needle at your place. */
@Composable
private fun ProgressScale(progress: Double, modifier: Modifier) {
    val c = LocalColors.current
    androidx.compose.foundation.Canvas(modifier.height(18.dp)) {
        val y = size.height / 2
        drawLine(c.keyEdge, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
        for (i in 0..10) {
            val x = size.width * i / 10
            drawLine(c.keyEdge, androidx.compose.ui.geometry.Offset(x, y - 3.dp.toPx()), androidx.compose.ui.geometry.Offset(x, y + 3.dp.toPx()), 1.dp.toPx())
        }
        val x = (size.width * progress.toFloat()).coerceIn(0f, size.width)
        drawLine(c.accent, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 2.dp.toPx())
    }
}

@Composable
private fun ReaderKey(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    app.quietreader.ui.Key(onClick, modifier, padding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp), contentDescription = label) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(18.dp), tint = c.text)
            Spacer(Modifier.height(3.dp))
            Text(label, style = Type.tab.copy(color = c.text))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onClose: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalColors.current
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bg,
        contentColor = c.text,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = if (c.isDark) 0.5f else 0.25f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(36.dp, 4.dp).background(c.keyEdge, RoundedCornerShape(2.dp))) },
    ) { content() }
}

@Composable
private fun ContentsSheet(state: ReaderState, pdf: Boolean, onClose: () -> Unit) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    Sheet(onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(if (pdf) "Pages" else "Contents", style = Type.section.copy(color = c.text))
            Spacer(Modifier.height(12.dp))
            if (state.contents.isEmpty()) {
                Text("This book has no contents list.", style = Type.body.copy(color = c.muted))
            } else {
                val current = state.contents.indexOfFirst { it.key == state.currentContents }
                val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
                LazyColumn(state = list, modifier = Modifier.fillMaxWidth().background(c.panel, KeyShape)) {
                    itemsIndexed(state.contents) { i, entry ->
                        if (i > 0) Divider()
                        val here = i == current
                        Row(
                            Modifier.fillMaxWidth().clickable { haptics?.tick(); state.goTo(entry); onClose() }
                                .padding(start = (14 + entry.depth * 16).dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(entry.title, style = (if (here) Type.rowTitle else Type.body).copy(color = c.text), modifier = Modifier.weight(1f))
                            if (here) Box(Modifier.size(7.dp).background(c.accent, RoundedCornerShape(4.dp)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextSheet(pdf: Boolean, onClose: () -> Unit) {
    val settings = LocalQuiet.current.settings
    val c = LocalColors.current
    Sheet(onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Text", style = Type.section.copy(color = c.text))
            Spacer(Modifier.height(14.dp))
            if (!pdf) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Size", style = Type.label.copy(color = c.text), modifier = Modifier.weight(1f))
                    Text("${settings.bookSize}%", style = Type.mono.copy(color = c.text))
                }
                Dial(
                    value = settings.bookSize.toFloat(),
                    onChange = { settings.bookSize = it.roundToInt() },
                    range = BookSize.MIN.toFloat()..BookSize.MAX.toFloat(),
                    step = BookSize.STEP.toFloat(),
                    label = "Book text size",
                    valueText = "${settings.bookSize} percent",
                    marks = listOf(BookSize.DEFAULT.toFloat()),
                )
                Spacer(Modifier.height(14.dp))
                Text("Font", style = Type.label.copy(color = c.text))
                Spacer(Modifier.height(8.dp))
                ChoiceRow(BookFont.entries.map { it to it.label }, settings.bookFont, { settings.bookFont = it }, perRow = 3)
                Spacer(Modifier.height(16.dp))
            }
            Text("Reading mode", style = Type.label.copy(color = c.text))
            Spacer(Modifier.height(8.dp))
            ChoiceRow(Flow.entries.map { it to it.label }, settings.flow, { settings.flow = it }, perRow = 2)
            Spacer(Modifier.height(16.dp))
            Text("Theme", style = Type.label.copy(color = c.text))
            Spacer(Modifier.height(8.dp))
            ChoiceRow(ThemeMode.entries.map { it to it.label }, settings.theme, { settings.theme = it })
        }
    }
}

/** A loading page: the knob on the casing while the book opens. */
@Composable
fun Opening(text: String = "") {
    val c = LocalColors.current
    Box(Modifier.fillMaxSize().background(c.readerBg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KnobMark(Modifier.size(44.dp))
            if (text.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(text, style = Type.body.copy(color = c.muted))
            }
        }
    }
}
