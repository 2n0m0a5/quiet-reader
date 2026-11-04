package app.quietreader.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quietreader.LocalQuiet
import app.quietreader.data.Book
import app.quietreader.data.Group
import app.quietreader.data.Shelf
import app.quietreader.data.Status
import app.quietreader.data.status
import app.quietreader.data.collectionIds
import app.quietreader.data.series
import app.quietreader.data.seriesIndex
import app.quietreader.reader.Field
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Library filters (the band at the top). */
enum class Filter(val label: String) { All("All"), Reading("Reading"), Want("Want"), Done("Done"), Series("Series") }

/**
 * The library: filters (All · Reading · Want · Done · Series), your collections, search; the
 * books as covers (pinch to make them bigger or smaller) or as a list, sorted and grouped your
 * way. Tap to read, hold for the book's page; Select (in ⋯) to change several at once.
 */
@Composable
fun LibraryScreen(state: AppState) {
    val quiet = LocalQuiet.current
    val settings = quiet.settings
    val c = LocalColors.current
    val all by quiet.library.books.collectAsState()
    val version by quiet.library.recordsVersion.collectAsState()
    val collections = remember(version) { quiet.collections.all() }
    val shelf = all.filter { it.deletedAt == null }

    if (shelf.isEmpty()) { EmptyLibrary(state); LibrarySheets(state); return }

    val filtered = shelf.filter { b ->
        when (state.filter) {
            Filter.Reading -> b.status() == Status.Reading
            Filter.Want -> b.status() == Status.Want
            Filter.Done -> b.status() == Status.Finished
            else -> true
        } && (state.collection == null || state.collection in b.collectionIds) && Shelf.matches(b, state.query)
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            ChoiceRow(Filter.entries.map { it to it.label }, state.filter, { state.filter = it }, perRow = 5)
            if (collections.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("All books", state.collection == null) { state.collection = null }
                    collections.entries.sortedBy { it.value.lowercase() }.forEach { (id, name) ->
                        Chip(name, state.collection == id) { state.collection = if (state.collection == id) null else id }
                    }
                }
            }
            if (state.searching) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(state.query, { state.query = it }, "Title, author, series or tag", Modifier.weight(1f), singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    IconKey(Icons.Close, "Close search", { state.query = ""; state.searching = false })
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (state.filter == Filter.Series) SeriesShelves(state, filtered)
            else Books(state, filtered, collections)
        }
        if (state.selected.isNotEmpty() || state.selecting) SelectionBar(state, shelf)
    }
    LibrarySheets(state)
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Key(onClick, kind = if (on) KeyKind.Primary else KeyKind.Normal, padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
        Text(label, style = Type.hint.copy(color = if (on) c.bg else c.text, fontSize = 12.sp))
    }
}

/** Pinch with two fingers to change the cover size; one finger scrolls as usual. */
private fun Modifier.pinchToResize(onScale: (Float) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var last = 0f
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                val d = (pressed[0].position - pressed[1].position).getDistance()
                if (last > 0f && d > 0f) onScale(d / last)
                last = d
                pressed.forEach { it.consume() }
            } else last = 0f
        }
    }
}

@Composable
private fun Books(state: AppState, books: List<Book>, collections: Map<String, String>) {
    val quiet = LocalQuiet.current
    val settings = quiet.settings
    val c = LocalColors.current
    val sorted = Shelf.sort(books, settings.librarySort)
    val groups = Shelf.group(sorted, settings.libraryGroup, collections)
    var size by remember { mutableStateOf(settings.coverSize.toFloat()) }
    LazyVerticalGrid(
        columns = if (settings.libraryList) GridCells.Fixed(1) else GridCells.Adaptive(minSize = size.dp),
        modifier = Modifier.fillMaxSize().pinchToResize { f ->
            size = (size * f).coerceIn(72f, 200f)
            settings.coverSize = size.roundToInt()
        },
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(if (settings.libraryList) 4.dp else 18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "${books.size} ${if (books.size == 1) "book" else "books"} · ${settings.librarySort.label.lowercase()}" +
                    if (settings.libraryGroup != Group.None) " · by ${settings.libraryGroup.label.lowercase()}" else "",
                style = Type.mono.copy(color = c.muted, fontSize = 12.sp),
            )
        }
        if (books.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text("No books here.", style = Type.body.copy(color = c.muted), modifier = Modifier.padding(vertical = 24.dp))
        }
        groups.forEach { (name, list) ->
            if (name.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(name, style = Type.section.copy(color = c.text, fontSize = 16.sp), modifier = Modifier.padding(top = 8.dp))
            }
            items(list, key = { name + it.id }) { book ->
                if (settings.libraryList) BookRow(book, state) else BookTile(book, state)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.bookClicks(book: Book, state: AppState, haptics: Haptics?) = combinedClickable(
    onClick = {
        haptics?.tick()
        if (state.selecting) state.toggleSelected(book.id) else state.open(book.id)
    },
    onLongClick = {
        haptics?.confirm()
        if (state.selecting) state.toggleSelected(book.id) else state.details = book.id
    },
    onLongClickLabel = "Book info",
)

@Composable
private fun BookTile(book: Book, state: AppState) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val chosen = book.id in state.selected
    Column(Modifier.bookClicks(book, state, haptics)) {
        Box {
            Cover(book, Modifier.fillMaxWidth())
            if (state.selecting) SelectMark(chosen, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        ProgressLine(book)
        Spacer(Modifier.height(6.dp))
        Text(book.title, style = Type.rowTitle.copy(color = c.text, fontSize = 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(book.author.ifBlank { book.format.label }, style = Type.hint.copy(color = c.muted), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            StatusText(book)
        }
    }
}

@Composable
private fun BookRow(book: Book, state: AppState) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    Row(Modifier.fillMaxWidth().bookClicks(book, state, haptics).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.selecting) { SelectMark(book.id in state.selected, Modifier); Spacer(Modifier.width(10.dp)) }
        Cover(book, Modifier.width(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, style = Type.rowTitle.copy(color = c.text), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(book.author.ifBlank { null }, book.series.ifBlank { null }?.let { s -> book.seriesIndex?.let { "$s #${fmt(it)}" } ?: s })
                .joinToString(" · "), style = Type.hint.copy(color = c.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            ProgressLine(book)
        }
        Spacer(Modifier.width(10.dp))
        StatusText(book)
    }
}

fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

@Composable
private fun SelectMark(on: Boolean, modifier: Modifier) {
    val c = LocalColors.current
    Box(modifier.size(24.dp).background(if (on) c.accent else c.panel, RoundedCornerShape(12.dp)).border(1.5.dp, if (on) c.accent else c.keyEdge, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center) {
        if (on) Icon(Icons.Check, null, Modifier.size(14.dp), tint = c.onAccent)
    }
}

@Composable
fun ProgressLine(book: Book) {
    val c = LocalColors.current
    val done = book.status() == Status.Finished
    Box(Modifier.fillMaxWidth().height(3.dp).background(c.panelDeep, RoundedCornerShape(2.dp))) {
        val f = if (done) 1f else book.progress.toFloat().coerceIn(0f, 1f)
        if (f > 0f) Box(Modifier.fillMaxWidth(f).height(3.dp).background(if (done) c.done else c.accent, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun StatusText(book: Book) {
    val c = LocalColors.current
    val s = book.status()
    Text(
        when (s) {
            Status.Finished -> "Done"; Status.Want -> "Want"; Status.New -> "New"
            Status.Reading -> "${(book.progress * 100).roundToInt()}%"
        },
        style = Type.mono.copy(color = if (s == Status.Finished) c.done else c.muted, fontSize = 11.sp),
    )
}

/** Series: each as a shelf of covers in order, with the next one to read. */
@Composable
private fun SeriesShelves(state: AppState, books: List<Book>) {
    val c = LocalColors.current
    val series = Shelf.series(books)
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        if (series.isEmpty()) item {
            Text("No series yet. Series come from the book itself, or from Edit on a book’s page (hold a book).",
                style = Type.body.copy(color = c.muted), modifier = Modifier.padding(vertical = 24.dp))
        }
        series.forEach { s ->
            item(key = s.name) {
                Column(Modifier.fillMaxWidth().padding(bottom = 18.dp).background(c.panel, CardShape).border(1.dp, c.line, CardShape).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = Type.section.copy(color = c.text, fontSize = 16.sp))
                            Text("${s.done} of ${s.books.size} read", style = Type.mono.copy(color = c.muted, fontSize = 12.sp))
                        }
                        s.next?.let { next -> TextKey("Next: #${next.seriesIndex?.let(::fmt) ?: (s.books.indexOf(next) + 1)}", { state.open(next.id) }, kind = KeyKind.Primary) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        s.books.forEach { b ->
                            Column(Modifier.width(76.dp).bookClicks(b, state, null)) {
                                Cover(b, Modifier.fillMaxWidth())
                                Spacer(Modifier.height(4.dp))
                                ProgressLine(b)
                                Text("#${b.seriesIndex?.let(::fmt) ?: "–"}", style = Type.mono.copy(color = c.muted, fontSize = 11.sp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Several books at once: status, collections, tags, remove. */
@Composable
private fun SelectionBar(state: AppState, shelf: List<Book>) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().background(c.panelDeep).hairline(c.line, top = true).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${state.selected.size} selected", style = Type.rowTitle.copy(color = c.text), modifier = Modifier.weight(1f))
            TextKey("All", { state.selected = shelf.map { it.id }.toSet() })
            Spacer(Modifier.width(6.dp))
            TextKey("Done", { state.selecting = false; state.selected = emptySet() }, kind = KeyKind.Primary)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val none = state.selected.isEmpty()
            TextKey("Status", { state.librarySheet = "bulk-status" }, Modifier.weight(1f), enabled = !none)
            TextKey("Collection", { state.librarySheet = "bulk-collection" }, Modifier.weight(1f), enabled = !none)
            TextKey("Tags", { state.librarySheet = "bulk-tags" }, Modifier.weight(1f), enabled = !none)
            TextKey("Remove", { state.librarySheet = "bulk-remove" }, Modifier.weight(1f), kind = KeyKind.Danger, enabled = !none)
        }
    }
}

@Composable
private fun EmptyLibrary(state: AppState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            Icons.Library,
            "Make yourself at home.",
            "A quiet, distraction-free place for your books. Add an EPUB, PDF or TXT file, or a whole folder of them, and they’ll wait for you here, " +
                "right where you left off. More formats arrive in L7.",
        ) {
            TextKey("Choose a book", { state.pickBooks() }, kind = KeyKind.Primary)
            TextKey("Add a book folder", { state.pickFolder() })
        }
    }
}

/** A book's cover, or (without one) a plain one with its title, like a cloth binding. */
@Composable
fun Cover(book: Book, modifier: Modifier = Modifier) {
    val quiet = LocalQuiet.current
    val c = LocalColors.current
    val version by quiet.library.recordsVersion.collectAsState()
    val image by produceState<ImageBitmap?>(null, book.id, book.json.optLong("coverChangedAt")) {
        value = withContext(Dispatchers.IO) {
            quiet.library.coverOf(book.id).takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
        }
    }
    val shape = RoundedCornerShape(3.dp)
    Box(modifier.aspectRatio(2f / 3f).clip(shape).background(c.panel).border(1.dp, c.line, shape)) {
        val img = image
        if (img != null) {
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(book.title, style = Type.reading.copy(color = c.text, fontSize = 15.sp, lineHeight = 18.sp), maxLines = 5, overflow = TextOverflow.Ellipsis)
                Column {
                    if (book.author.isNotBlank()) Text(book.author, style = Type.hint.copy(color = c.muted, fontSize = 10.sp), maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Text(book.format.label.ifBlank { book.fileName.substringAfterLast('.').uppercase() }, style = Type.mono.copy(color = c.accentStrong, fontSize = 10.sp))
                }
            }
        }
    }
}
