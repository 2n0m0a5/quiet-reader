package app.quietreader.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quietreader.LocalQuiet
import app.quietreader.data.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The library: your books as covers, the one you read last first. Empty, it welcomes you. */
@Composable
fun LibraryScreen(state: AppState) {
    val quiet = LocalQuiet.current
    val all by quiet.library.books.collectAsState()
    val books = all.filter { it.deletedAt == null }
    var menuFor by remember { mutableStateOf<Book?>(null) }

    if (books.isEmpty()) {
        EmptyLibrary(state)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val c = LocalColors.current
                Text(
                    "${books.size} ${if (books.size == 1) "book" else "books"}",
                    style = Type.mono.copy(color = c.muted),
                )
            }
            items(books, key = { it.id }) { book ->
                BookTile(book, onOpen = { state.open(book.id) }, onMenu = { menuFor = book })
            }
        }
    }
    menuFor?.let { book -> BookMenu(book, state) { menuFor = null } }
}

@Composable
private fun EmptyLibrary(state: AppState) {
    BoxCenter {
        EmptyState(
            Icons.Library,
            "Make yourself at home.",
            "A quiet, distraction-free place for your books. Add an EPUB, PDF or TXT file and it’ll wait for you here, " +
                "right where you left off. More formats arrive in L7, and book folders in L5.",
        ) {
            TextKey("Choose a book", { state.pickBooks() }, kind = KeyKind.Primary)
            TextKey("Restore a backup", { state.tab = Tab.Settings; state.page = SettingsPage.Files })
        }
    }
}

@Composable
private fun BoxCenter(content: @Composable () -> Unit) =
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookTile(book: Book, onOpen: () -> Unit, onMenu: () -> Unit) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    Column(
        Modifier.combinedClickable(
            onClick = { haptics?.tick(); onOpen() },
            onLongClick = { haptics?.confirm(); onMenu() },
            onLongClickLabel = "Book options",
        ),
    ) {
        Cover(book, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        // Progress: a thin line, orange while reading, green when finished.
        Box(Modifier.fillMaxWidth().height(3.dp).background(c.panelDeep, RoundedCornerShape(2.dp))) {
            val f = if (book.finished) 1f else book.progress.toFloat().coerceIn(0f, 1f)
            if (f > 0f) Box(Modifier.fillMaxWidth(f).height(3.dp).background(if (book.finished) c.done else c.accent, RoundedCornerShape(2.dp)))
        }
        Spacer(Modifier.height(6.dp))
        Text(book.title, style = Type.rowTitle.copy(color = c.text, fontSize = 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                book.author.ifBlank { book.format.label },
                style = Type.hint.copy(color = c.muted), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(
                when {
                    book.finished -> "Done"
                    book.lastOpenedAt == null && book.progress == 0.0 -> "New"
                    else -> "${(book.progress * 100).roundToInt()}%"
                },
                style = Type.mono.copy(color = if (book.finished) c.done else c.muted, fontSize = 11.sp),
            )
        }
    }
}

/** A book's cover, or (without one) a plain one with its title, like a cloth binding. */
@Composable
fun Cover(book: Book, modifier: Modifier = Modifier) {
    val quiet = LocalQuiet.current
    val c = LocalColors.current
    val image by produceState<ImageBitmap?>(null, book.id) {
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
                    Text(book.format.label, style = Type.mono.copy(color = c.accentStrong, fontSize = 10.sp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookMenu(book: Book, state: AppState, onClose: () -> Unit) {
    val quiet = LocalQuiet.current
    val c = LocalColors.current
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bg, contentColor = c.text,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(36.dp, 4.dp).background(c.keyEdge, RoundedCornerShape(2.dp))) },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(book, Modifier.width(56.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = Type.rowTitle.copy(color = c.text, fontSize = 16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (book.author.isNotBlank()) Text(book.author, style = Type.hint.copy(color = c.muted))
                    Text("${book.format.label} · ${(book.progress * 100).roundToInt()}%", style = Type.mono.copy(color = c.muted, fontSize = 11.sp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextKey("Read", { onClose(); state.open(book.id) }, Modifier.weight(1f), kind = KeyKind.Primary)
                TextKey("Remove", {
                    quiet.library.put(book.with({ put("deletedAt", System.currentTimeMillis()) }))
                    onClose()
                    state.toast("Moved to Recently deleted. It stays there for 30 days.")
                }, Modifier.weight(1f), kind = KeyKind.Danger)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Removed books wait in Settings → Files & storage → Recently deleted.",
                style = Type.hint.copy(color = c.muted, textAlign = TextAlign.Start, fontWeight = FontWeight.Normal),
            )
        }
    }
}
