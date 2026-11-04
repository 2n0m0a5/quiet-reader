package app.quietreader.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quietreader.BuildConfig
import app.quietreader.ErrorLog
import app.quietreader.LocalQuiet
import app.quietreader.reader.Field
import app.quietreader.reader.Sheet
import kotlin.math.roundToInt

// ---------- The tour: tips pointing at the real screens ----------

/** A tip that points at [key] (a control marked with [tourTarget]); [anywhere] tips need no control. */
class Tip(val key: String, val title: String, val text: String, val tab: Tab? = null, val anywhere: Boolean = false)

/** The app's tour (7 tips). The reader has its own tips the first time a book opens. */
val appTour = listOf(
    Tip("add", "Add books", "EPUB, MOBI, AZW3, FB2, PDF, Word, comics… Or use “Open with Quiet” from Telegram or your files.", Tab.Library),
    Tip("shelf", "Your books", "Tap a cover to read. Hold one to select several: status, collections, tags, remove.", Tab.Library),
    Tip("menu", "Library options", "Sort, group, collections, duplicates, book folders and Recently deleted.", Tab.Library),
    Tip("tab-notes", "Notes", "Highlights, notes, quotes and bookmarks from every book, in one place.", Tab.Library),
    Tip("tab-stats", "Stats", "Your reading time today and this week, your streak and your goals.", Tab.Library),
    Tip("theme", "Day and Night", "One tap, any time. More themes in Settings → Interface.", Tab.Library),
    Tip("tab-settings", "Keep a backup", "Settings → Files & storage → Back up library: one file with everything. Keep it somewhere safe.", Tab.Library),
)

/** The reader's tips, the first time a book opens. */
val readerTips = listOf(
    Tip("page", "Turning pages", "Tap the left or right edge, or swipe. Tap the middle to hide or show these bars.", anywhere = true),
    Tip("scale", "Your place", "The orange needle is where you are; each mark is a chapter. Tap or drag to jump. “Back to…” takes you back."),
    Tip("key-contents", "Contents", "Chapters, and the one you’re in."),
    Tip("key-text", "Text", "Size, font, spacing, and Scroll or Pages."),
    Tip("key-light", "Light", "Brightness and warm light, just for reading."),
    Tip("hold", "Highlights and notes", "Hold a word, then drag to choose more: highlight, note, quote, copy, translate.", anywhere = true),
    Tip("more", "More", "Notes, search, go to page, bookmarks, and Customize bar to choose your four keys."),
)

/** Marks a control the tour can point at. */
fun Modifier.tourTarget(targets: MutableMap<String, Rect>, key: String) =
    onGloballyPositioned { targets[key] = it.boundsInWindow() }

/**
 * Dims the screen except a window around the control, with a short card next to it.
 * Tap anywhere or Next to go on; Skip ends it. Back ends it too.
 */
@Composable
fun CoachMarks(tips: List<Tip>, targets: Map<String, Rect>, index: Int, onNext: () -> Unit, onSkip: () -> Unit) {
    val c = LocalColors.current
    val tip = tips.getOrNull(index) ?: return
    val target = targets[tip.key]
    BackHandler { onSkip() }
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shown by animateFloatAsState(1f, glide(), label = "coach")
    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInWindow().topLeft }
            .clickable(remember { MutableInteractionSource() }, indication = null) { onNext() },
    ) {
        val hole = target?.translate(-origin)?.inflate(with(density) { 6.dp.toPx() })
        Canvas(Modifier.fillMaxSize()) {
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                hole?.let { addRoundRect(RoundRect(it, CornerRadius(12.dp.toPx()))) }
            }
            drawPath(path, Color.Black.copy(alpha = 0.6f * shown))
            hole?.let { drawRoundRect(c.accent, it.topLeft, it.size, CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx())) }
        }
        // The card goes where there's more room: below the control, or above it.
        val heightPx = with(density) { maxHeight.toPx() }
        val below = hole == null || hole.center.y < heightPx / 2
        val place = when {
            hole == null -> Modifier.align(androidx.compose.ui.Alignment.Center)
            below -> Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top = with(density) { (hole.bottom + 14.dp.toPx()).toDp() })
            else -> Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = with(density) { (heightPx - hole.top + 14.dp.toPx()).toDp() })
        }
        Column(
            place.fillMaxWidth().padding(horizontal = 20.dp)
                .background(c.bg, CardShape).border(1.dp, c.keyEdge, CardShape).padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text("${index + 1} / ${tips.size}", style = Type.mono.copy(color = c.muted, fontSize = 11.sp))
            Spacer(Modifier.height(4.dp))
            Text(tip.title, style = Type.section.copy(color = c.text, fontSize = 17.sp), modifier = Modifier.asHeading())
            Spacer(Modifier.height(4.dp))
            Text(tip.text, style = Type.body.copy(color = c.text))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextKey("Skip", onSkip, Modifier.weight(1f))
                TextKey(if (index == tips.size - 1) "Done" else "Next", onNext, Modifier.weight(1f), kind = KeyKind.Primary)
            }
        }
    }
}

// ---------- What's new ----------

val whatsNew = listOf(
    "0.5.0 L8" to listOf("A short tour (replay it from About)", "What’s new (this)", "Report a problem: you read the report, then send it yourself",
        "Custom colours for the page", "Screen readers (TalkBack) read every control", "Motion and text size settings finished"),
    "0.5.0 L7" to listOf("Every format: MOBI, AZW3, FB2, Word, ODT, RTF, Markdown, HTML, Palm, comics", "PDF zoom"),
    "0.5.0 L6" to listOf("Stats: today’s meter, the week, streak, goals, and five detailed stats", "“You read for 25 min” when you close a book"),
    "0.5.0 L5" to listOf("Filters, collections, series, sort and group", "Hold a cover to select several", "A page for each book", "Book folders"),
    "0.5.0 L4" to listOf("Highlights, notes, quotes and bookmarks", "Translate, search in a book", "The Notes tab"),
    "0.5.0 L3" to listOf("The reader bar: tuning scale and keys", "Curl, footnotes, pictures, bionic reading, the ruler, your fonts"),
    "0.5.0 L1–L2" to listOf("Read EPUB, PDF and TXT", "Back up and restore, Recently deleted"),
)

@Composable
fun WhatsNewSheet(onClose: () -> Unit) {
    val c = LocalColors.current
    Sheet(onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("What’s new", style = Type.section.copy(color = c.text), modifier = Modifier.asHeading())
            Text("Quiet ${BuildConfig.QUIET_VERSION} · ${BuildConfig.QUIET_LAYER} · native", style = Type.mono.copy(color = c.muted, fontSize = 12.sp))
            whatsNew.forEachIndexed { i, (version, items) ->
                Spacer(Modifier.height(16.dp))
                Text(version, style = Type.rowTitle.copy(color = if (i == 0) c.accentStrong else c.text))
                Spacer(Modifier.height(4.dp))
                items.forEach { Text("• $it", style = Type.body.copy(color = if (i == 0) c.text else c.muted)) }
            }
            Spacer(Modifier.height(18.dp))
            TextKey("Close", onClose, Modifier.fillMaxWidth(), kind = KeyKind.Primary)
        }
    }
}

// ---------- Report a problem ----------

/** A short technical report: what's on this phone and in Quiet, and recent errors. No books, no notes. */
fun buildReport(quiet: app.quietreader.Quiet, context: android.content.Context): String {
    val s = quiet.settings
    val books = quiet.library.books.value
    val m = context.resources.displayMetrics
    val formats = books.groupingBy { it.format.label.ifBlank { "other" } }.eachCount().entries.joinToString(", ") { "${it.key} ${it.value}" }
    return buildString {
        appendLine("Quiet ${BuildConfig.QUIET_VERSION} ${BuildConfig.QUIET_LAYER} (native Android app, ${BuildConfig.APPLICATION_ID})")
        appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Screen ${m.widthPixels}×${m.heightPixels}, density ${m.density}")
        appendLine("Theme ${s.theme.label}, motion ${s.motion.label}${if (app.quietreader.phoneWantsLessMotion(context)) " (the phone asks for less)" else ""}, app text ${s.textScale}%")
        appendLine("Reading: ${s.flow.label}, page turn ${s.pageTurn.label}, font ${s.customFont ?: s.bookFont.label}, size ${s.bookSize}%")
        appendLine("Library: ${books.count { it.deletedAt == null }} books${if (formats.isNotBlank()) " ($formats)" else ""}, ${books.count { it.deletedAt != null }} in Recently deleted")
        appendLine("Notes ${quiet.notes.all().size}, folders ${quiet.folders.all().size}, last backup ${s.lastBackup?.let { shortDate(it) } ?: "never"}")
        appendLine()
        val errors = ErrorLog.recent()
        appendLine(if (errors.isEmpty()) "No errors since Quiet opened." else "Recent errors:\n" + errors.joinToString("\n"))
        ErrorLog.lastCrash()?.let { appendLine(); appendLine("Last crash:"); appendLine(it) }
    }.trim()
}

@Composable
fun ReportSheet(onClose: () -> Unit) {
    val quiet = LocalQuiet.current
    val c = LocalColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val report = remember { buildReport(quiet, context) }
    var what by remember { mutableStateOf("") }
    fun text() = (if (what.isNotBlank()) what.trim() + "\n\n" else "") + report
    fun open(uri: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
    Sheet(onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("Report a problem", style = Type.section.copy(color = c.text), modifier = Modifier.asHeading())
            Spacer(Modifier.height(4.dp))
            Text("Quiet has no servers: read the report, then send it yourself, to whoever helps you with Quiet. It holds no books or notes.",
                style = Type.hint.copy(color = c.muted))
            Spacer(Modifier.height(12.dp))
            Field(what, { what = it }, "What happened? (what you did just before)")
            Spacer(Modifier.height(10.dp))
            Text(report, style = Type.mono.copy(color = c.text, fontSize = 11.sp, lineHeight = 16.sp),
                modifier = Modifier.fillMaxWidth().background(c.panel, KeyShape).border(1.dp, c.line, KeyShape).padding(12.dp))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextKey("Email", {
                    open("mailto:?subject=${Uri.encode("Quiet ${BuildConfig.QUIET_VERSION} ${BuildConfig.QUIET_LAYER}: a problem")}&body=${Uri.encode(text())}")
                }, Modifier.weight(1f), kind = KeyKind.Primary)
                TextKey("Telegram", { open("https://t.me/share/url?url=${Uri.encode("Quiet problem report")}&text=${Uri.encode(text())}") }, Modifier.weight(1f))
                TextKey("Copy", { clipboard.setText(AnnotatedString(text())) }, Modifier.weight(1f))
            }
            if (ErrorLog.lastCrash() != null) {
                Spacer(Modifier.height(8.dp))
                TextKey("Forget the last crash", { ErrorLog.clearCrash(); onClose() }, Modifier.fillMaxWidth())
            }
        }
    }
}
