package app.quietreader.reader

import app.quietreader.ui.click
import app.quietreader.ui.asHeading
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.quietreader.LocalQuiet
import app.quietreader.R
import app.quietreader.data.Book
import app.quietreader.data.Note
import app.quietreader.data.Notes
import app.quietreader.ui.ChoiceRow
import app.quietreader.ui.Divider
import app.quietreader.ui.Icons
import app.quietreader.ui.KeyKind
import app.quietreader.ui.KeyShape
import app.quietreader.ui.LocalColors
import app.quietreader.ui.LocalHaptics
import app.quietreader.ui.Switch
import app.quietreader.ui.SwitchRow
import app.quietreader.ui.TextKey
import app.quietreader.ui.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun hex(color: String): Color = runCatching { Color(android.graphics.Color.parseColor(color)) }.getOrDefault(Color.Yellow)

/** A text field in Radio style: a recessed panel. */
@Composable
fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, singleLine: Boolean = false, onDone: (() -> Unit)? = null) {
    val c = LocalColors.current
    BasicTextField(
        value, onChange,
        modifier = modifier.fillMaxWidth().background(c.panel, KeyShape).border(1.dp, c.line, KeyShape).padding(12.dp),
        textStyle = Type.body.copy(color = c.text, fontSize = 15.sp),
        cursorBrush = SolidColor(c.accent),
        singleLine = singleLine,
        keyboardOptions = if (onDone != null) KeyboardOptions(imeAction = ImeAction.Search) else KeyboardOptions.Default,
        keyboardActions = KeyboardActions(onSearch = { onDone?.invoke() }),
        decorationBox = { inner ->
            Box { if (value.isEmpty()) Text(placeholder, style = Type.body.copy(color = c.muted, fontSize = 15.sp)); inner() }
        },
    )
}

/**
 * A highlight, note or quote: colour, how it's drawn, your note, whether it's a quote and who
 * says it, tags; then share it as a picture, copy, or delete. Saves as you change it.
 */
@Composable
fun NoteEditor(start: Note, book: Book?, focusNote: Boolean = false, onDeleted: (Note) -> Unit = {}, onClose: () -> Unit) {
    val quiet = LocalQuiet.current
    val c = LocalColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var note by remember(start.id) { mutableStateOf(start) }
    var text by remember(start.id) { mutableStateOf(start.note) }
    var speaker by remember(start.id) { mutableStateOf(start.speaker) }
    var tags by remember(start.id) { mutableStateOf(start.tags.joinToString(", ")) }
    fun save(n: Note = note) {
        note = n.with { put("note", text); put("speaker", speaker); put("tags", Notes.tagsJson(tags.split(','))) }
        quiet.notes.put(note)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (focusNote) runCatching { focus.requestFocus() } }
    Sheet({ save(); onClose() }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(if (note.quote) "Quote" else "Highlight", style = Type.section.copy(color = c.text), modifier = Modifier.asHeading())
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().background(c.panel, KeyShape).padding(12.dp)) {
                Box(Modifier.width(3.dp).heightIn(min = 20.dp).background(hex(note.color)))
                Spacer(Modifier.width(10.dp))
                Text(note.text, style = Type.reading.copy(color = c.readerText, fontSize = 16.sp, lineHeight = 23.sp), maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Note.COLORS.forEach { col ->
                    val on = col.value == note.color
                    Box(
                        Modifier.size(34.dp).background(hex(col.value), RoundedCornerShape(17.dp))
                            .border(if (on) 3.dp else 1.dp, if (on) c.text else c.line, RoundedCornerShape(17.dp))
                            .click { quiet.settings.lastColor = col.value; save(note.with { put("color", col.value) }) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ChoiceRow(listOf("highlight" to "Highlight", "underline" to "Underline", "strike" to "Strike", "plain" to "Plain"),
                note.style, { s -> save(note.with { put("style", s) }) })
            Spacer(Modifier.height(14.dp))
            Field(text, { text = it }, "Your note…", Modifier.focusRequester(focus))
            Spacer(Modifier.height(12.dp))
            SwitchRow(note.quote, { on -> save(note.with { put("quote", on) }) }, padding = PaddingValues(vertical = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Save to Quotes", style = Type.label.copy(color = c.text))
                    Text("Quotes are kept apart from highlights, with who says them.", style = Type.hint.copy(color = c.muted))
                }
            }
            if (note.quote) {
                Spacer(Modifier.height(8.dp))
                Field(speaker, { speaker = it }, "Who says it? (empty: ${book?.author?.ifBlank { null } ?: "the author"})", singleLine = true)
            }
            Spacer(Modifier.height(10.dp))
            Field(tags, { tags = it }, "Tags, separated by commas", singleLine = true)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextKey("Done", { save(); onClose() }, Modifier.weight(1f), kind = KeyKind.Primary)
                TextKey("Picture", { save(); shareQuote(context, note, book, speaker) }, Modifier.weight(1f))
                TextKey("Copy", { clipboard.setText(AnnotatedString(note.text)) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            TextKey("Delete", { save(); quiet.notes.delete(note.id); onDeleted(note); onClose() }, Modifier.fillMaxWidth(), kind = KeyKind.Danger)
        }
    }
}

/** Translation of the text you chose (only that text is sent: Google, or MyMemory). */
@Composable
fun TranslateSheet(text: String, onClose: () -> Unit) {
    val quiet = LocalQuiet.current
    val c = LocalColors.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var target by remember { mutableStateOf(quiet.settings.translateTo) }
    var attempt by remember { mutableStateOf(0) }
    val result by produceState<Result<Translate.Result>?>(null, target, attempt) {
        value = null
        value = runCatching { Translate.translate(text, target) }
    }
    Sheet(onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Translate", style = Type.section.copy(color = c.text), modifier = Modifier.asHeading())
            Spacer(Modifier.height(8.dp))
            Text(text, style = Type.reading.copy(color = c.muted, fontSize = 15.sp), maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            val r = result
            when {
                r == null -> Text("Translating…", style = Type.body.copy(color = c.muted))
                r.isFailure -> {
                    Text(r.exceptionOrNull()?.message ?: "Couldn’t translate just now.", style = Type.body.copy(color = c.text))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextKey("Try again", { attempt++ }, Modifier.weight(1f))
                        TextKey("Open in Google Translate", {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Translate.webUrl(text, target)))) }
                        }, Modifier.weight(1.6f), kind = KeyKind.Primary)
                    }
                }
                else -> {
                    val t = r.getOrThrow()
                    Text(t.text, style = Type.reading.copy(color = c.text, fontSize = 19.sp, lineHeight = 27.sp))
                    t.dictionary.forEach { (pos, terms) ->
                        Spacer(Modifier.height(6.dp))
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(color = c.accentStrong, fontWeight = FontWeight.SemiBold)) { append("$pos  ") }
                            append(terms.joinToString(", "))
                        }, style = Type.body.copy(color = c.text))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${t.service}${if (t.source.isNotBlank()) " · from ${Translate.name(t.source)}" else ""}", style = Type.hint.copy(color = c.muted))
                    Spacer(Modifier.height(8.dp))
                    TextKey("Copy translation", { clipboard.setText(AnnotatedString(t.text)) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Into", style = Type.label.copy(color = c.text))
            Spacer(Modifier.height(8.dp))
            ChoiceRow(Translate.LANGUAGES.take(8).map { it to Translate.name(it) }, target, { target = it; quiet.settings.translateTo = it }, perRow = 4)
        }
    }
}

/** The book's bookmarks, highlights, notes and quotes, in reading order. */
@Composable
fun BookNotesSheet(state: ReaderState, book: Book, onClose: () -> Unit) {
    val quiet = LocalQuiet.current
    val c = LocalColors.current
    val version by quiet.library.recordsVersion.collectAsState()
    val notes = remember(version) { quiet.notes.forBook(book.id) }
    var filter by remember { mutableStateOf("all") }
    Sheet(onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Notes & bookmarks", style = Type.section.copy(color = c.text), modifier = Modifier.asHeading())
            Spacer(Modifier.height(10.dp))
            ChoiceRow(listOf("all" to "All", "marks" to "Bookmarks", "notes" to "Highlights", "quotes" to "Quotes"), filter, { filter = it })
            Spacer(Modifier.height(10.dp))
            val shown = notes.filter {
                when (filter) { "marks" -> it.isBookmark; "notes" -> !it.isBookmark && !it.quote; "quotes" -> it.quote; else -> true }
            }
            if (shown.isEmpty()) Text(
                if (state.annotatable) "Nothing here yet. Hold a word to select text, then Highlight, Note or Quote. The bookmark key is at the top."
                else "Nothing here yet. The bookmark key is at the top. (Highlights work in EPUB and TXT books.)",
                style = Type.body.copy(color = c.muted),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                items(shown, key = { it.id }) { n ->
                    val haptics = app.quietreader.ui.LocalHaptics.current
                    NoteRow(n, null) { haptics?.tick(); onClose(); state.markBack(); state.goToNote(n) }
                    Divider()
                }
            }
        }
    }
}

/** One note in a list: its colour, text, your note, where it is. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NoteRow(n: Note, bookTitle: String?, onLongClick: (() -> Unit)? = null, selected: Boolean? = null, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().combinedClickable(onLongClick = onLongClick, onClick = onClick)
        .background(if (selected == true) c.accentSoft else Color.Transparent).padding(vertical = 12.dp)) {
        if (selected != null) {
            Box(Modifier.size(22.dp).background(if (selected) c.accent else c.panel, RoundedCornerShape(11.dp)).border(1.5.dp, if (selected) c.accent else c.keyEdge, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center) { if (selected) Icon(Icons.Check, null, Modifier.size(13.dp), tint = c.onAccent) }
            Spacer(Modifier.width(10.dp))
        }
        if (n.isBookmark) Icon(Icons.BookmarkFilled, null, Modifier.size(16.dp), tint = c.accent)
        else Box(Modifier.width(3.dp).height(40.dp).background(if (n.style == "plain") c.keyEdge else hex(n.color)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (bookTitle != null) Text(bookTitle, style = Type.hint.copy(color = c.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (n.isBookmark) n.label.ifBlank { "Bookmark" } else n.text,
                style = (if (n.isBookmark) Type.rowTitle else Type.reading.copy(fontSize = 16.sp, lineHeight = 22.sp)).copy(color = c.text),
                maxLines = 4, overflow = TextOverflow.Ellipsis,
            )
            if (n.note.isNotBlank()) { Spacer(Modifier.height(3.dp)); Text(n.note, style = Type.body.copy(color = c.accentStrong)) }
            val where = listOfNotNull(
                n.label.takeIf { !n.isBookmark && it.isNotBlank() },
                if (n.quote) "Quote${if (n.speaker.isNotBlank()) " · ${n.speaker}" else ""}" else null,
                "${(n.progress * 100).toInt()}%",
            ).joinToString(" · ")
            Text(where, style = Type.mono.copy(color = c.muted, fontSize = 11.sp))
        }
    }
}

data class SearchHit(val before: String, val match: String, val after: String, val label: String, val key: Any)

/** Search in this book. */
@Composable
fun SearchSheet(state: ReaderState, onClose: () -> Unit) {
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(state.lastSearch) }
    var hits by remember { mutableStateOf(state.lastHits) }
    var busy by remember { mutableStateOf(false) }
    fun run() {
        val q = query.trim(); if (q.length < 2 || busy) return
        busy = true
        scope.launch { hits = state.search?.invoke(q) ?: emptyList(); state.lastSearch = q; state.lastHits = hits; busy = false }
    }
    Sheet(onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Search in book", style = Type.section.copy(color = c.text), modifier = Modifier.asHeading())
            Spacer(Modifier.height(10.dp))
            if (state.search == null) {
                Text("Search works in EPUB and TXT books.", style = Type.body.copy(color = c.muted)); return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(query, { query = it }, "Words to find", Modifier.weight(1f), singleLine = true, onDone = ::run)
                Spacer(Modifier.width(8.dp))
                TextKey(if (busy) "…" else "Find", ::run, kind = KeyKind.Primary)
            }
            Spacer(Modifier.height(10.dp))
            if (!busy && hits.isEmpty() && state.lastSearch.isNotBlank() && state.lastSearch == query.trim())
                Text("Not found in this book.", style = Type.body.copy(color = c.muted))
            if (hits.isNotEmpty()) Text("${hits.size}${if (hits.size >= 300) "+" else ""} found", style = Type.mono.copy(color = c.muted, fontSize = 12.sp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(hits) { h ->
                    Column(Modifier.fillMaxWidth().click { onClose(); state.markBack(); state.showHit(h) }.padding(vertical = 10.dp)) {
                        if (h.label.isNotBlank()) Text(h.label, style = Type.hint.copy(color = c.muted), maxLines = 1)
                        Text(buildAnnotatedString {
                            append("…" + h.before.takeLast(60))
                            withStyle(SpanStyle(background = c.accentSoft, fontWeight = FontWeight.SemiBold)) { append(h.match) }
                            append(h.after.take(60) + "…")
                        }, style = Type.body.copy(color = c.text), maxLines = 3)
                    }
                    Divider()
                }
            }
        }
    }
}

// ---------- Quote pictures ----------

/** Draws a quote as a picture (1080 × 1350, Radio's reading colours) and shares it. */
fun shareQuote(context: Context, note: Note, book: Book?, speaker: String = note.speaker) {
    val bitmap = quotePicture(context, note, book, speaker)
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "quote.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share the quote"))
}

/** Saves a quote picture to Pictures/Quiet. Returns true when saved. */
fun saveQuote(context: Context, note: Note, book: Book?): Boolean = runCatching {
    val bitmap = quotePicture(context, note, book, note.speaker)
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Quiet quote ${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Quiet")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    true
}.getOrDefault(false)

fun quotePicture(context: Context, note: Note, book: Book?, speaker: String): Bitmap {
    val w = 1080; val h = 1350; val pad = 110
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.rgb(0xF2, 0xF0, 0xEB))
    val serif = ResourcesCompat.getFont(context, R.font.crimson_pro)
    val sans = ResourcesCompat.getFont(context, R.font.archivo)
    val ink = android.graphics.Color.rgb(0x24, 0x23, 0x1F)
    val muted = android.graphics.Color.rgb(0x5D, 0x5B, 0x55)
    val accent = android.graphics.Color.rgb(0xC9, 0x54, 0x1F)
    // The quote, as large as fits.
    var size = 88f
    var layout: StaticLayout
    val quote = "“" + note.text.trim() + "”"
    while (true) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; typeface = serif; textSize = size }
        layout = StaticLayout.Builder.obtain(quote, 0, quote.length, paint, w - pad * 2).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.25f).build()
        if (layout.height < h - 520 || size <= 30f) break
        size -= 4f
    }
    val top = ((h - layout.height) / 2f - 80f).coerceAtLeast(160f)
    canvas.save(); canvas.translate(pad.toFloat(), top); layout.draw(canvas); canvas.restore()
    val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; typeface = sans; textSize = 34f }
    val who = speaker.ifBlank { book?.author ?: "" }
    var y = top + layout.height + 90f
    canvas.drawRect(pad.toFloat(), y - 40f, pad + 60f, y - 36f, Paint().apply { color = accent })
    if (who.isNotBlank()) { canvas.drawText("— $who", pad.toFloat(), y + 10f, small.apply { color = ink }); y += 56f }
    book?.title?.let { canvas.drawText(it.take(48), pad.toFloat(), y + 10f, small.apply { color = muted; textSize = 30f }) }
    // The knob mark and the name, small, at the bottom.
    val cx = pad + 22f; val cy = h - 110f
    canvas.drawCircle(cx, cy, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(0xBD, 0xB9, 0xAF); style = Paint.Style.STROKE; strokeWidth = 3f })
    canvas.drawLine(cx, cy, cx - 11f, cy + 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND })
    canvas.drawCircle(cx - 20f, cy + 20f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
    canvas.drawText("quiet", cx + 40f, cy + 12f, small.apply { color = ink; textSize = 34f })
    return bmp
}
