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
import app.quietreader.ReaderKey
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

/** What a book view tells the chrome around it, and what the chrome can ask of it. */
class ReaderState {
    var chapter by mutableStateOf("")
    var progress by mutableStateOf(0.0)       // 0–1 through the whole book
    var page by mutableStateOf(0)             // 1-based; 0 when unknown
    var pages by mutableStateOf(0)
    var pageLabel by mutableStateOf("")       // "12 / 340"
    var stations by mutableStateOf<List<Double>>(emptyList()) // where chapters start, 0–1
    var contents by mutableStateOf<List<ContentsEntry>>(emptyList())
    var currentContents by mutableStateOf<Any?>(null)
    var barsShown by mutableStateOf(true)
    /** Where you were before a jump (Contents, Go to, the scale), to go back. */
    var backTo by mutableStateOf<Pair<String, () -> Unit>?>(null)
    var goTo: (ContentsEntry) -> Unit = {}
    var goToProgress: (Double) -> Unit = {}
    var goToPage: (Int) -> Unit = {}
    var turn: (forward: Boolean) -> Unit = {}
    /** Remembers the current place as "back", for the jump that follows. */
    var markBack: () -> Unit = {}
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

    ReaderWindow(settings)
    androidx.compose.runtime.DisposableEffect(state) {
        quiet.pageTurner = { forward -> state.turn(forward) }
        onDispose { quiet.pageTurner = null }
    }
    Box(Modifier.fillMaxSize().background(c.readerBg)) {
        when (book.format) {
            BookFormat.Pdf -> PdfReader(book, state)
            else -> EpubReader(book, state)
        }
        Warmth(settings.warmth)
        if (settings.showClock && !state.barsShown) Clock(Modifier.align(Alignment.TopEnd))
        ReaderBars(state = state, book = book, onBack = onClose)
        BottomBar(state, Modifier.align(Alignment.BottomCenter)) { key ->
            when (key) {
                null -> sheet = "more"
                ReaderKey.Contents -> sheet = "contents"
                ReaderKey.DayNight -> settings.theme = if (c.isDark) ThemeMode.Day else ThemeMode.Night
                ReaderKey.Light -> sheet = "light"
                ReaderKey.Text -> sheet = "text"
                ReaderKey.Layout -> sheet = "layout"
                ReaderKey.GoTo -> sheet = "goto"
                ReaderKey.Mode -> settings.flow = if (settings.flow == Flow.Scrolled) Flow.Paginated else Flow.Scrolled
            }
        }
    }

    val pdf = book.format == BookFormat.Pdf
    val close = { sheet = null }
    when (sheet) {
        "contents" -> ContentsSheet(state, pdf, close)
        "text" -> TextSheet(pdf, close)
        "more" -> MoreSheet(pdf) { sheet = it }
        "light" -> LightSheet(close)
        "layout" -> LayoutSheet(pdf, close)
        "goto" -> GoToSheet(state, close)
        "keys" -> CustomizeSheet(close)
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Sheet(onClose: () -> Unit, content: @Composable () -> Unit) {
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

/** When a book can't be opened: say so plainly, on the page. */
@Composable
fun CantOpen(text: String) {
    val c = LocalColors.current
    Box(Modifier.fillMaxSize().background(c.readerBg).padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Type.body.copy(color = c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
    }
}
