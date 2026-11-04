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
    /** Turns a page at once, with no animation (the curl draws its own). False at the book's end. */
    var jump: (forward: Boolean) -> Boolean = { false }
    /** Set while the curl layer is on: turns with the curl. */
    var curlTurn: ((forward: Boolean) -> Unit)? = null
    /** The book's own view (EPUB), so taps can pass through the curl layer. */
    var pageView: android.view.View? = null
    var footnote by mutableStateOf<String?>(null)
    var image by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    // L4: notes, bookmarks, search
    var annotatable by mutableStateOf(false)
    var bookmarkHere by mutableStateOf(false)
    var toggleBookmark: () -> Unit = {}
    var editing by mutableStateOf<Pair<app.quietreader.data.Note, Boolean>?>(null) // the note, and whether to type a note
    var translating by mutableStateOf<String?>(null)
    var goToNote: (app.quietreader.data.Note) -> Unit = {}
    var pendingNote: app.quietreader.data.Note? = null
    var search: (suspend (String) -> List<SearchHit>)? = null
    var showHit: (SearchHit) -> Unit = {}
    var lastSearch = ""
    var lastHits: List<SearchHit> = emptyList()
    fun toggleBars() { barsShown = !barsShown }
    /** A page turn by key or volume button, with the chosen page turn. */
    fun pageTurn(forward: Boolean) { curlTurn?.invoke(forward) ?: turn(forward) }
}

/** The book's page colours for the theme (Radio's reading colours). */
fun pageColors(c: QuietColors) = c.readerBg to c.readerText

/**
 * Reading a book: the page fills the screen; tap the middle for the bars (Back and the chapter
 * at the top; Contents, Day/Night, Text and your place at the bottom, within thumb's reach).
 * The full reader bar (tuning scale, five keys, More) comes in L3.
 */
@Composable
fun ReaderScreen(book: Book, startNote: app.quietreader.data.Note? = null, onInfo: () -> Unit = {}, onClose: () -> Unit) {
    val quiet = LocalQuiet.current
    val settings = quiet.settings
    val c = LocalColors.current
    val state = remember(book.id) { ReaderState().also { it.pendingNote = startNote } }
    var sheet by remember { mutableStateOf<String?>(null) }

    BackHandler { onClose() }
    ImmersiveWhileReading(hidden = !state.barsShown)

    ReaderWindow(settings)
    androidx.compose.runtime.DisposableEffect(state) {
        quiet.pageTurner = { forward -> state.pageTurn(forward) }
        onDispose { quiet.pageTurner = null }
    }
    Box(Modifier.fillMaxSize().background(c.readerBg)) {
        when (book.format) {
            BookFormat.Pdf -> PdfReader(book, state)
            else -> EpubReader(book, state)
        }
        val curl = settings.pageTurn == app.quietreader.PageTurn.Curl && settings.flow == Flow.Paginated
        if (curl) {
            CurlLayer(state) { at ->
                val view = state.pageView
                if (view == null) state.toggleBars() else passTap(view, at)
            }
            androidx.compose.runtime.DisposableEffect(Unit) { onDispose { state.curlTurn = null } }
        }
        RulerLayer()
        Warmth(settings.warmth)
        if (settings.showClock && !state.barsShown) Clock(Modifier.align(Alignment.TopEnd))
        ReaderBars(state = state, book = book, onBack = onClose)
        state.image?.let { ImageViewer(it) { state.image = null } }
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
                ReaderKey.Notes -> sheet = "notes"
                ReaderKey.Search -> sheet = "search"
                ReaderKey.Bookmark -> state.toggleBookmark()
            }
        }
    }

    val pdf = book.format == BookFormat.Pdf
    val close = { sheet = null }
    when (sheet) {
        "contents" -> ContentsSheet(state, pdf, close)
        "text" -> TextSheet(pdf, close)
        "more" -> MoreSheet(pdf) { if (it == "info") { sheet = null; onInfo() } else sheet = it }
        "light" -> LightSheet(close)
        "layout" -> LayoutSheet(pdf, close)
        "goto" -> GoToSheet(state, close)
        "keys" -> CustomizeSheet(close)
        "notes" -> BookNotesSheet(state, book, close)
        "search" -> SearchSheet(state, close)
    }
    state.editing?.let { (note, typing) -> NoteEditor(note, book, typing) { state.editing = null } }
    state.translating?.let { TranslateSheet(it) { state.translating = null } }
    state.footnote?.let { FootnoteSheet(it) { state.footnote = null } }

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
                Spacer(Modifier.width(8.dp))
                app.quietreader.ui.Key({ state.toggleBookmark() }, padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    contentDescription = if (state.bookmarkHere) "Remove bookmark" else "Bookmark this page") {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(if (state.bookmarkHere) Icons.BookmarkFilled else Icons.Bookmark, null, Modifier.size(18.dp),
                            tint = if (state.bookmarkHere) c.accent else c.text)
                    }
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
                val quiet = LocalQuiet.current
                val custom = remember { quiet.fonts.families() }
                val options: List<Pair<String, String>> = BookFont.entries.map { "q:" + it.key to it.label } + custom.map { "c:$it" to it }
                val chosen = settings.customFont?.let { "c:$it" } ?: ("q:" + settings.bookFont.key)
                ChoiceRow(options, chosen, { v ->
                    if (v.startsWith("c:")) settings.customFont = v.removePrefix("c:")
                    else { settings.customFont = null; settings.bookFont = BookFont.entries.first { "q:" + it.key == v } }
                }, perRow = 3)
                if (custom.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Add your own fonts in Settings → Reader → Your fonts.", style = Type.hint.copy(color = c.muted))
                }
                Spacer(Modifier.height(16.dp))
            }
            Text("Reading mode", style = Type.label.copy(color = c.text))
            Spacer(Modifier.height(8.dp))
            ChoiceRow(Flow.entries.map { it to it.label }, settings.flow, { settings.flow = it }, perRow = 2)
            Spacer(Modifier.height(16.dp))
            Text("Page turn", style = Type.label.copy(color = c.text))
            Spacer(Modifier.height(8.dp))
            ChoiceRow(app.quietreader.PageTurn.entries.map { it to it.label }, settings.pageTurn, {
                settings.pageTurn = it
                if (it == app.quietreader.PageTurn.Curl) settings.flow = Flow.Paginated
            }, perRow = 3)
            Spacer(Modifier.height(6.dp))
            Text(
                if (settings.flow == Flow.Scrolled) "In Scroll mode you read by scrolling; page turns are for Pages. Curl switches to Pages."
                else "Curl bends the page like paper under your finger.",
                style = Type.hint.copy(color = c.muted),
            )
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

/** Passes a tap through the curl layer to the book's view (links, pictures, the bars). */
private fun passTap(view: android.view.View, at: androidx.compose.ui.geometry.Offset) {
    val loc = IntArray(2).also { view.getLocationOnScreen(it) }
    val x = at.x - loc[0]; val y = at.y - loc[1]
    val t = android.os.SystemClock.uptimeMillis()
    val down = android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
    val up = android.view.MotionEvent.obtain(t, t + 40, android.view.MotionEvent.ACTION_UP, x, y, 0)
    view.dispatchTouchEvent(down); view.dispatchTouchEvent(up)
    down.recycle(); up.recycle()
}
