package app.quietreader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quietreader.LocalQuiet
import app.quietreader.data.Note
import app.quietreader.reader.Field
import app.quietreader.reader.NoteEditor
import app.quietreader.reader.hex

/**
 * Notes: every highlight, note, quote and bookmark from every book, newest first. Filter, search,
 * tap to open the book there; hold to select (then edit one, copy or delete them, with Undo).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesTab(state: AppState) {
    val quiet = LocalQuiet.current
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val version by quiet.library.recordsVersion.collectAsState()
    val books by quiet.library.books.collectAsState()
    val all = remember(version) { quiet.notes.all() }
    var filter by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Note?>(null) }
    // Select several (hold a note, as with books): edit one, copy or delete them.
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selecting by remember { mutableStateOf(false) }
    fun toggle(id: String) { selected = if (id in selected) selected - id else selected + id }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    androidx.activity.compose.BackHandler(enabled = selecting) { selecting = false; selected = emptySet() }
    val byId = books.associateBy { it.id }

    if (all.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            EmptyState(
                Icons.NotebookPen,
                "Your notes will gather here.",
                "Hold a word while reading to select text, then Highlight, Note or Quote. Bookmarks too. Everything from every book shows up on this page.",
            )
        }
        return
    }
    val shown = all.filter { n ->
        when (filter) { "notes" -> !n.isBookmark && !n.quote; "quotes" -> n.quote; "marks" -> n.isBookmark; else -> true }
    }.filter { n ->
        query.isBlank() || listOf(n.text, n.note, n.speaker, n.label, byId[n.bookId]?.title.orEmpty(), n.tags.joinToString(" "))
            .any { it.contains(query.trim(), ignoreCase = true) }
    }
    Box(Modifier.fillMaxSize()) {
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(state.reselect) { if (state.reselect > 0 && state.tab == Tab.Notes) list.animateScrollToItem(0) }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = if (selecting) 130.dp else 24.dp + LocalNowRoom.current)) {
        item {
            fun n(count: Int, one: String) = "$count ${if (count == 1) one else one + "s"}"
            Text("${n(all.count { !it.isBookmark && !it.quote }, "highlight")} · ${n(all.count { it.quote }, "quote")} · ${n(all.count { it.isBookmark }, "bookmark")}",
                style = Type.mono.copy(color = c.muted, fontSize = 12.sp))
            Spacer(Modifier.height(10.dp))
            Field(query, { query = it }, "Search your notes", singleLine = true)
            Spacer(Modifier.height(10.dp))
            ChoiceRow(listOf("all" to "All", "notes" to "Highlights", "quotes" to "Quotes", "marks" to "Bookmarks"), filter, { filter = it })
            Spacer(Modifier.height(8.dp))
            if (shown.isEmpty()) Text("Nothing matches.", style = Type.body.copy(color = c.muted), modifier = Modifier.padding(vertical = 16.dp))
        }
        items(shown, key = { it.id }) { n ->
            // Notes glide into place when you filter, search or delete.
            Column(if (LocalReducedMotion.current) Modifier else Modifier.animateItem()) {
            val book = byId[n.bookId]
            app.quietreader.reader.NoteRow(n, book?.title ?: "A book no longer in your library",
                onLongClick = { haptics?.confirm(); if (!selecting) { selecting = true; selected = setOf(n.id) } else toggle(n.id) },
                selected = if (selecting) n.id in selected else null,
            ) {
                when {
                    selecting -> { haptics?.tick(); toggle(n.id) }
                    book != null && book.deletedAt == null -> { haptics?.tick(); state.open(book.id, n) }
                    else -> state.toast("That book isn’t in your library.")
                }
            }
            Divider()
            }
        }
    }
    if (selecting) Column(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth().background(c.panelDeep).hairline(c.line, top = true).padding(12.dp)) {
        val chosen = all.filter { it.id in selected }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${selected.size} selected", style = Type.rowTitle.copy(color = c.text))
                Text("Tap notes to add or take them out", style = Type.hint.copy(color = c.muted))
            }
            TextKey("All", { selected = shown.map { it.id }.toSet() })
            Spacer(Modifier.width(6.dp))
            TextKey("Done", { selecting = false; selected = emptySet() }, kind = KeyKind.Primary)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextKey("Edit", { editing = chosen.firstOrNull { !it.isBookmark }; selecting = false; selected = emptySet() }, Modifier.weight(1f),
                enabled = chosen.size == 1 && !chosen[0].isBookmark)
            TextKey("Copy", {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(chosen.joinToString("\n\n") { n ->
                    (if (n.isBookmark) n.label else "“${n.text}”") + (if (n.note.isNotBlank()) "\n${n.note}" else "") + " — ${byId[n.bookId]?.title.orEmpty()}"
                }))
                state.toast("Copied ${chosen.size}.")
            }, Modifier.weight(1f), enabled = chosen.isNotEmpty())
            TextKey("Delete", {
                chosen.forEach { quiet.notes.delete(it.id) }
                selecting = false; selected = emptySet()
                state.undoable("${chosen.size} deleted.") { chosen.forEach { quiet.notes.put(it) } }
            }, Modifier.weight(1f), kind = KeyKind.Danger, enabled = chosen.isNotEmpty())
        }
    }
    }
    editing?.let { n -> NoteEditor(n, byId[n.bookId], onDeleted = { gone -> state.undoable("Note deleted.") { quiet.notes.put(gone) } }) { editing = null } }
}
