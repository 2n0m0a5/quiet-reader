package app.quietreader.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.quietreader.LocalQuiet
import app.quietreader.data.Book
import app.quietreader.data.BookFormat
import app.quietreader.data.Group
import app.quietreader.data.Shelf
import app.quietreader.data.Sort
import app.quietreader.data.Status
import app.quietreader.data.collectionIds
import app.quietreader.data.rating
import app.quietreader.data.series
import app.quietreader.data.seriesIndex
import app.quietreader.data.status
import app.quietreader.data.strings
import app.quietreader.data.tags
import app.quietreader.data.withCollection
import app.quietreader.data.withStatus
import app.quietreader.data.withTags
import app.quietreader.reader.Field
import app.quietreader.reader.Sheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.positions
import java.io.File
import kotlin.math.roundToInt

@Composable
private fun SheetBody(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
        Text(title, style = Type.section.copy(color = c.text))
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun Label(text: String) {
    val c = LocalColors.current
    Spacer(Modifier.height(14.dp))
    Text(text, style = Type.label.copy(color = c.text))
    Spacer(Modifier.height(8.dp))
}

/** Every library sheet, by name in [AppState.librarySheet], plus a book's page ([AppState.details]). */
@Composable
fun LibrarySheets(state: AppState) {
    val close = { state.librarySheet = null }
    when (state.librarySheet) {
        "menu" -> LibraryMenu(state, close)
        "collections" -> CollectionsSheet(close)
        "duplicates" -> DuplicatesSheet(state, close)
        "bulk-status" -> BulkStatus(state, close)
        "bulk-collection" -> BulkCollection(state, close)
        "bulk-tags" -> BulkTags(state, close)
        "bulk-remove" -> BulkRemove(state, close)
        "edit" -> state.details?.let { EditBook(it, close) }
    }
    if (state.librarySheet != "edit") state.details?.let { id -> BookPage(id, state) { state.details = null } }
}

/** ⋯ in the library: select, sort, group, view, search, collections, duplicates, recently deleted, folders. */
@Composable
private fun LibraryMenu(state: AppState, close: () -> Unit) {
    val settings = LocalQuiet.current.settings
    val c = LocalColors.current
    Sheet(close) {
        SheetBody("Library") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextKey("Select books", { state.selecting = true; close() }, Modifier.weight(1f), kind = KeyKind.Primary)
                TextKey("Search", { state.searching = true; close() }, Modifier.weight(1f))
            }
            Label("Sort")
            ChoiceRow(Sort.entries.map { it to it.label }, settings.librarySort, { settings.librarySort = it }, perRow = 2)
            Label("Group")
            ChoiceRow(Group.entries.map { it to it.label }, settings.libraryGroup, { settings.libraryGroup = it }, perRow = 3)
            Label("View")
            ChoiceRow(listOf(false to "Covers", true to "List"), settings.libraryList, { settings.libraryList = it }, perRow = 2)
            Text("Covers: pinch with two fingers to make them bigger or smaller.", style = Type.hint.copy(color = c.muted), modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().background(c.panel, KeyShape).border(1.dp, c.line, KeyShape)) {
                @Composable fun row(label: String, go: () -> Unit) {
                    Row(Modifier.fillMaxWidth().clickable { go() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = Type.body.copy(color = c.text), modifier = Modifier.weight(1f))
                        Icon(Icons.ChevronRight, null, Modifier.size(16.dp), tint = c.muted)
                    }
                }
                row("Collections") { state.librarySheet = "collections" }
                Divider()
                row("Duplicates") { state.librarySheet = "duplicates" }
                Divider()
                row("Book folders") { close(); state.tab = Tab.Settings; state.page = SettingsPage.Library }
                Divider()
                row("Recently deleted") { close(); state.tab = Tab.Settings; state.page = SettingsPage.Trash }
            }
        }
    }
}

/** A book's page: status, rating, review, collections, tags, the facts, your reading, and actions. */
@Composable
private fun BookPage(id: String, state: AppState, onClose: () -> Unit) {
    val quiet = LocalQuiet.current
    val c = LocalColors.current
    val context = LocalContext.current
    val books by quiet.library.books.collectAsState()
    val version by quiet.library.recordsVersion.collectAsState()
    val book = books.firstOrNull { it.id == id } ?: return
    val collections = remember(version) { quiet.collections.all() }
    val notes = remember(version) { quiet.notes.forBook(id) }
    // Every change is saved at once, always on the book as it is now in the library
    // (0.5.0 L5 saved the page's first copy on close, which undid collection and status changes).
    fun update(change: (Book) -> Book) { quiet.library.get(id)?.let { quiet.library.put(change(it)) } }
    var review by remember(id) { mutableStateOf(book.json.optString("review")) }
    androidx.compose.runtime.LaunchedEffect(review) {
        kotlinx.coroutines.delay(500)
        if (quiet.library.get(id)?.json?.optString("review") != review) update { it.with({ put("review", review) }) }
    }
    val allTags = remember(books) { books.flatMap { it.tags }.distinct().sortedBy { it.lowercase() } }
    // Pages (Readium positions) and words, counted once and kept in the record.
    val counts by produceState(book.json.optInt("pages", 0) to book.json.optInt("words", 0), id) {
        if (value.first > 0 || book.format == BookFormat.Other) return@produceState
        value = withContext(Dispatchers.IO) { countPagesAndWords(quiet, book) }
        quiet.library.get(id)?.let { quiet.library.put(it.with({ put("pages", value.first); put("words", value.second) })) }
    }
    Sheet({ update { it.with({ put("review", review) }) }; onClose() }) {
        SheetBody("") {
            Row {
                Cover(book, Modifier.width(92.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = Type.pageTitle.copy(color = c.text, fontSize = 19.sp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    if (book.author.isNotBlank()) Text(book.author, style = Type.body.copy(color = c.muted))
                    if (book.series.isNotBlank()) Text("${book.series}${book.seriesIndex?.let { " #" + fmt(it) } ?: ""}", style = Type.hint.copy(color = c.accentStrong))
                    Spacer(Modifier.height(10.dp))
                    TextKey(if (book.lastOpenedAt == null) "Read" else "Go on reading", {
                        update { it.with({ put("review", review) }) }; onClose(); state.open(id)
                    }, Modifier.fillMaxWidth(), kind = KeyKind.Primary)
                }
            }
            Label("Status")
            ChoiceRow(listOf(Status.Want to "Want", Status.Reading to "Reading", Status.Finished to "Finished", Status.New to "New"),
                book.status(), { s -> update { it.withStatus(s) } })
            Label("Rating")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { n ->
                    Key({ update { b -> b.with({ put("rating", if (b.rating == n) 0 else n) }) } }, padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        contentDescription = "$n stars") {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Text(if (n <= book.rating) "★" else "☆", style = Type.section.copy(color = if (n <= book.rating) c.accent else c.muted))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Field(review, { review = it }, "A few words about it (only for you)")
            Label("Collections")
            Text("Tap to put the book in a collection or take it out.", style = Type.hint.copy(color = c.muted))
            Spacer(Modifier.height(8.dp))
            var newCollection by remember { mutableStateOf("") }
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                collections.entries.sortedBy { it.value.lowercase() }.forEach { (cid, name) ->
                    val on = cid in book.collectionIds
                    Key({ update { it.withCollection(cid, !on) } }, kind = if (on) KeyKind.Primary else KeyKind.Normal,
                        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                        Text((if (on) "✓ " else "") + name, style = Type.hint.copy(color = if (on) c.bg else c.text, fontSize = 12.sp), maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(newCollection, { newCollection = it }, "New collection", Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(8.dp))
                TextKey("Add", {
                    if (newCollection.isNotBlank()) { val cid = quiet.collections.create(newCollection); update { it.withCollection(cid, true) }; newCollection = "" }
                })
            }
            Label("Tags")
            TagEditor(book.tags, allTags) { list -> update { it.withTags(list) } }

            Facts("The book", listOfNotNull(
                counts.first.takeIf { it > 0 }?.let { "Length" to "$it pages" },
                counts.second.takeIf { it > 0 }?.let { "Words" to "%,d".format(it) },
                counts.second.takeIf { it > 0 }?.let { "Reading time" to readingTime(it) },
                book.json.optString("publisher").ifBlank { null }?.let { "Publisher" to it },
                book.json.optString("published").ifBlank { null }?.let { "Published" to it },
                book.json.optString("language").ifBlank { null }?.let { "Language" to it },
                book.json.optString("isbn").ifBlank { null }?.let { "ISBN" to it },
                book.strings("subjects").takeIf { it.isNotEmpty() }?.let { "Subjects" to it.joinToString(", ") },
            ))
            book.json.optString("description").ifBlank { null }?.let { d ->
                Spacer(Modifier.height(8.dp))
                Text(androidx.core.text.HtmlCompat.fromHtml(d, 0).toString().trim(), style = Type.body.copy(color = c.text), maxLines = 12, overflow = TextOverflow.Ellipsis)
            }
            Facts("The file", listOf(
                "Format" to book.format.label.ifBlank { book.fileName.substringAfterLast('.').uppercase() },
                "Size" to fileSize(book.json.optLong("size")),
                "File name" to book.fileName,
                "Added" to shortDate(book.addedAt),
            ))
            Facts("Your reading", listOfNotNull(
                "Progress" to "${(book.progress * 100).roundToInt()}%",
                book.lastOpenedAt?.let { "Last read" to shortDate(it) },
                book.json.optLong("finishedAt", 0).takeIf { it > 0 }?.let { "Finished" to shortDate(it) },
                "Highlights" to notes.count { !it.isBookmark && !it.quote }.toString(),
                "Notes" to notes.count { it.note.isNotBlank() }.toString(),
                "Quotes" to notes.count { it.quote }.toString(),
                "Bookmarks" to notes.count { it.isBookmark }.toString(),
                "Hours, sittings" to "in L6 (Stats)",
            ))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextKey("Edit", { state.librarySheet = "edit" }, Modifier.weight(1f))
                TextKey("Cover", { state.pickCover(id) }, Modifier.weight(1f))
                TextKey("Share file", { shareFile(context, quiet, book) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextKey("Export notes", { exportNotes(context, book, notes) }, Modifier.weight(1f), enabled = notes.isNotEmpty())
                TextKey("Remove", {
                    update { it.with({ put("deletedAt", System.currentTimeMillis()) }) }; onClose()
                    state.toast("Moved to Recently deleted. It stays there for 30 days.")
                }, Modifier.weight(1f), kind = KeyKind.Danger)
            }
        }
    }
}

@Composable
private fun Facts(title: String, rows: List<Pair<String, String>>) {
    val c = LocalColors.current
    if (rows.isEmpty()) return
    Spacer(Modifier.height(20.dp))
    Text(title.uppercase(), style = Type.overline.copy(color = c.muted))
    Spacer(Modifier.height(6.dp))
    rows.forEachIndexed { i, (k, v) ->
        if (i > 0) Divider()
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(k, style = Type.body.copy(color = c.muted), modifier = Modifier.width(120.dp))
            Text(v, style = Type.body.copy(color = c.text), modifier = Modifier.weight(1f))
        }
    }
}

private fun readingTime(words: Int): String {
    val minutes = words / 250
    return if (minutes < 60) "about $minutes min" else "about ${minutes / 60} h ${minutes % 60} min (at 250 words a minute)"
}

/** Pages (Readium positions) and words, for EPUB and TXT; pages for PDF. */
private suspend fun countPagesAndWords(quiet: app.quietreader.Quiet, book: Book): Pair<Int, Int> = runCatching {
    if (book.format == BookFormat.Pdf) {
        android.os.ParcelFileDescriptor.open(quiet.library.fileOf(book.id), android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            android.graphics.pdf.PdfRenderer(fd).use { it.pageCount to 0 }
        }
    } else {
        val file = if (book.format == BookFormat.Txt) quiet.library.derivedOf(book.id) else quiet.library.fileOf(book.id)
        val publication = quiet.readium.open(file) ?: return@runCatching 0 to 0
        try {
            val pages = publication.positions().size
            var words = 0
            for (link in publication.readingOrder) {
                val html = publication.get(link)?.read()?.getOrNull()?.let { String(it) } ?: continue
                val text = html.replace(Regex("<[^>]+>"), " ").replace(Regex("&[a-z#0-9]+;"), " ")
                words += Regex("[\\p{L}\\p{N}]+").findAll(text).count()
            }
            pages to words
        } finally { publication.close() }
    }
}.getOrDefault(0 to 0)

private fun shareFile(context: android.content.Context, quiet: app.quietreader.Quiet, book: Book) = runCatching {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val out = File(dir, book.fileName.ifBlank { book.title + "." + book.format.label.lowercase() })
    quiet.library.fileOf(book.id).copyTo(out, overwrite = true)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", out)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = book.format.mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share the book"))
}

/** The book's notes as Markdown text, to share (Telegram, mail, notes apps). */
private fun exportNotes(context: android.content.Context, book: Book, notes: List<app.quietreader.data.Note>) {
    val text = buildString {
        append("# ${book.title}\n"); if (book.author.isNotBlank()) append("${book.author}\n")
        notes.filter { !it.isBookmark }.forEach { n ->
            append("\n> ${n.text.replace("\n", "\n> ")}\n")
            if (n.quote && n.speaker.isNotBlank()) append("— ${n.speaker}\n")
            if (n.note.isNotBlank()) append("\n${n.note}\n")
            append("\n_${listOfNotNull(n.label.ifBlank { null }, "${(n.progress * 100).toInt()}%").joinToString(" · ")}_\n")
        }
    }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Notes: ${book.title}"); putExtra(Intent.EXTRA_TEXT, text)
    }, "Export notes"))
}

/** Edit details: title, author, series and number, publisher, language, description. */
@Composable
private fun EditBook(id: String, close: () -> Unit) {
    val quiet = LocalQuiet.current
    val book = quiet.library.get(id) ?: return
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var series by remember { mutableStateOf(book.series) }
    var index by remember { mutableStateOf(book.seriesIndex?.let(::fmt) ?: "") }
    var publisher by remember { mutableStateOf(book.json.optString("publisher")) }
    var language by remember { mutableStateOf(book.json.optString("language")) }
    var description by remember { mutableStateOf(book.json.optString("description")) }
    Sheet(close) {
        SheetBody("Edit details") {
            @Composable fun field(label: String, v: String, set: (String) -> Unit, single: Boolean = true) {
                Text(label, style = Type.hint.copy(color = LocalColors.current.muted)); Spacer(Modifier.height(4.dp))
                Field(v, set, label, singleLine = single); Spacer(Modifier.height(10.dp))
            }
            field("Title", title, { title = it })
            field("Author", author, { author = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(3f)) { field("Series", series, { series = it }) }
                Column(Modifier.weight(1f)) { field("Number", index, { index = it }) }
            }
            field("Publisher", publisher, { publisher = it })
            field("Language", language, { language = it })
            field("Description", description, { description = it }, single = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextKey("Cancel", close, Modifier.weight(1f))
                TextKey("Save", {
                    quiet.library.put((quiet.library.get(id) ?: book).with({
                        put("title", title.trim().ifBlank { book.title }); put("author", author.trim())
                        put("series", series.trim()); index.replace(',', '.').toDoubleOrNull()?.let { put("seriesIndex", it) } ?: put("seriesIndex", org.json.JSONObject.NULL)
                        put("publisher", publisher.trim()); put("language", language.trim()); put("description", description.trim())
                    }))
                    close()
                }, Modifier.weight(1f), kind = KeyKind.Primary)
            }
        }
    }
}

@Composable
private fun CollectionsSheet(close: () -> Unit) {
    val quiet = LocalQuiet.current
    val c = LocalColors.current
    val version by quiet.library.recordsVersion.collectAsState()
    val books by quiet.library.books.collectAsState()
    val collections = remember(version) { quiet.collections.all().entries.sortedBy { it.value.lowercase() } }
    var name by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    Sheet(close) {
        SheetBody("Collections") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(name, { name = it }, "New collection", Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(8.dp))
                TextKey("Add", { if (name.isNotBlank()) { quiet.collections.create(name); name = "" } }, kind = KeyKind.Primary)
            }
            Spacer(Modifier.height(12.dp))
            if (collections.isEmpty()) Text("Group books your way: “Summer”, “Russian classics”, “For Dad”. A book can be in several.",
                style = Type.body.copy(color = c.muted))
            collections.forEach { (id, n) ->
                Divider()
                if (editing == id) Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(newName, { newName = it }, n, Modifier.weight(1f), singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    TextKey("Save", { quiet.collections.rename(id, newName.ifBlank { n }); editing = null }, kind = KeyKind.Primary)
                } else Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(n, style = Type.rowTitle.copy(color = c.text))
                        Text("${books.count { it.deletedAt == null && id in it.collectionIds }} books", style = Type.mono.copy(color = c.muted, fontSize = 11.sp))
                    }
                    TextKey("Rename", { editing = id; newName = n })
                    Spacer(Modifier.width(6.dp))
                    IconKey(Icons.Trash, "Delete $n", { quiet.collections.delete(id) }, kind = KeyKind.Danger)
                }
            }
        }
    }
}

@Composable
private fun DuplicatesSheet(state: AppState, close: () -> Unit) {
    val quiet = LocalQuiet.current
    val c = LocalColors.current
    val books by quiet.library.books.collectAsState()
    val groups = Shelf.duplicates(books.filter { it.deletedAt == null })
    Sheet(close) {
        SheetBody("Duplicates") {
            if (groups.isEmpty()) Text("No duplicates: every book is here once.", style = Type.body.copy(color = c.muted))
            groups.forEach { group ->
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).background(c.panel, CardShape).border(1.dp, c.line, CardShape).padding(12.dp)) {
                    group.forEachIndexed { i, b ->
                        if (i > 0) Divider()
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Cover(b, Modifier.width(36.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.title, style = Type.rowTitle.copy(color = c.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${b.format.label} · ${fileSize(b.json.optLong("size"))} · ${(b.progress * 100).roundToInt()}% · added ${shortDate(b.addedAt)}",
                                    style = Type.mono.copy(color = c.muted, fontSize = 11.sp))
                            }
                            IconKey(Icons.Trash, "Remove", { quiet.library.put(b.with({ put("deletedAt", System.currentTimeMillis()) })) }, kind = KeyKind.Danger)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Several books at once ----------

private fun selectedBooks(state: AppState, quiet: app.quietreader.Quiet) = quiet.library.books.value.filter { it.id in state.selected }

@Composable
private fun BulkStatus(state: AppState, close: () -> Unit) {
    val quiet = LocalQuiet.current
    Sheet(close) {
        SheetBody("Status of ${state.selected.size} books") {
            listOf(Status.Want to "Want to read", Status.Reading to "Reading", Status.Finished to "Finished", Status.New to "New").forEach { (s, label) ->
                TextKey(label, { quiet.library.putAll(selectedBooks(state, quiet).map { it.withStatus(s) }); close() }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BulkCollection(state: AppState, close: () -> Unit) {
    val quiet = LocalQuiet.current
    val c = LocalColors.current
    val version by quiet.library.recordsVersion.collectAsState()
    val collections = remember(version) { quiet.collections.all().entries.sortedBy { it.value.lowercase() } }
    var name by remember { mutableStateOf("") }
    Sheet(close) {
        SheetBody("Add ${state.selected.size} books to a collection") {
            collections.forEach { (id, n) ->
                TextKey(n, { quiet.library.putAll(selectedBooks(state, quiet).map { it.withCollection(id, true) }); state.toast("Added to “$n”."); close() }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(name, { name = it }, "New collection", Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(8.dp))
                TextKey("Create", {
                    if (name.isNotBlank()) {
                        val id = quiet.collections.create(name)
                        quiet.library.putAll(selectedBooks(state, quiet).map { it.withCollection(id, true) }); state.toast("Added to “$name”."); close()
                    }
                }, kind = KeyKind.Primary)
            }
            if (collections.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Take them out of a collection:", style = Type.hint.copy(color = c.muted))
                Spacer(Modifier.height(6.dp))
                collections.forEach { (id, n) ->
                    TextKey("Remove from “$n”", { quiet.library.putAll(selectedBooks(state, quiet).map { it.withCollection(id, false) }); close() }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun BulkTags(state: AppState, close: () -> Unit) {
    val quiet = LocalQuiet.current
    var tags by remember { mutableStateOf("") }
    Sheet(close) {
        SheetBody("Tag ${state.selected.size} books") {
            Field(tags, { tags = it }, "Tags to add, separated by commas", singleLine = true)
            Spacer(Modifier.height(12.dp))
            TextKey("Add tags", {
                val add = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                quiet.library.putAll(selectedBooks(state, quiet).map { it.withTags(it.tags + add) }); close()
            }, Modifier.fillMaxWidth(), kind = KeyKind.Primary)
        }
    }
}

@Composable
private fun BulkRemove(state: AppState, close: () -> Unit) {
    val quiet = LocalQuiet.current
    ConfirmDialog("Remove ${state.selected.size} books?", "They go to Recently deleted for 30 days.", "Remove", close) {
        val now = System.currentTimeMillis()
        quiet.library.putAll(selectedBooks(state, quiet).map { it.with({ put("deletedAt", now) }) })
        state.selected = emptySet(); state.selecting = false
        close()
    }
}

/**
 * Tags as chips: type one and tap Add (or Enter, or a comma) to add it; × removes it. Tags used on
 * other books are offered below, one tap to add.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TagEditor(tags: List<String>, suggestions: List<String>, onChange: (List<String>) -> Unit) {
    val c = LocalColors.current
    var typing by remember { mutableStateOf("") }
    fun add(text: String) {
        val new = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (new.isNotEmpty()) onChange((tags + new).distinctBy { it.lowercase() })
        typing = ""
    }
    if (tags.isNotEmpty()) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { tag ->
                Key({ onChange(tags - tag) }, kind = KeyKind.Primary, padding = androidx.compose.foundation.layout.PaddingValues(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    contentDescription = "Remove tag $tag") {
                    Text("#$tag", style = Type.hint.copy(color = c.bg, fontSize = 12.sp), maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Close, null, Modifier.size(12.dp), tint = c.bg)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Field(typing, { v -> if (v.contains(',')) add(v) else typing = v }, "Add a tag", Modifier.weight(1f), singleLine = true, onDone = { add(typing) })
        Spacer(Modifier.width(8.dp))
        TextKey("Add", { add(typing) }, enabled = typing.isNotBlank())
    }
    val offer = suggestions.filter { s -> tags.none { it.equals(s, ignoreCase = true) } && (typing.isBlank() || s.contains(typing.trim(), ignoreCase = true)) }.take(12)
    if (offer.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            offer.forEach { s ->
                Key({ onChange(tags + s); typing = "" }, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("+ $s", style = Type.hint.copy(color = c.text, fontSize = 12.sp), maxLines = 1)
                }
            }
        }
    }
}
