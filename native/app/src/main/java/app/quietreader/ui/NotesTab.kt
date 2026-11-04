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
 * tap to open the book there, hold for the note's options (edit, picture, copy, delete).
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)) {
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
            val book = byId[n.bookId]
            app.quietreader.reader.NoteRow(n, book?.title ?: "A book no longer in your library",
                onLongClick = { haptics?.confirm(); if (!n.isBookmark) editing = n else { quiet.notes.delete(n.id); state.undoable("Bookmark removed.") { quiet.notes.put(n) } } },
            ) {
                if (book != null && book.deletedAt == null) { haptics?.tick(); state.open(book.id, n) } else state.toast("That book isn’t in your library.")
            }
            Divider()
        }
    }
    editing?.let { n -> NoteEditor(n, byId[n.bookId], onDeleted = { gone -> state.undoable("Note deleted.") { quiet.notes.put(gone) } }) { editing = null } }
}
