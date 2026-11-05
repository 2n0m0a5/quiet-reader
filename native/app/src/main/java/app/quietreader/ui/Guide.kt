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

/** The app's tour (7 tips). */
val appTour = listOf(
    Tip("add", "Add books", "EPUB, MOBI, AZW3, FB2, PDF, Word, comics… Or use “Open with Quiet” from Telegram or your files.", Tab.Library),
    Tip("shelf", "Your books", "Tap a cover to read. Hold one to select several. Swipe left or right for Reading, Want, Done and Series.", Tab.Library),
    Tip("now", "Now reading", "Your book, one tap away. Swipe it sideways for your other books, down to tuck it away; hold it for more.", Tab.Library),
    Tip("menu", "Library options", "Sort, group, collections, duplicates, book folders and Recently deleted.", Tab.Library),
    Tip("tab-notes", "Notes", "Highlights, notes, quotes and bookmarks from every book, in one place.", Tab.Library),
    Tip("tab-stats", "Stats", "Your reading time today and this week, your streak and your goals.", Tab.Library),
    Tip("theme", "Day and Night", "One tap, any time. More themes in Settings → Interface.", Tab.Library),
    Tip("tab-settings", "Keep a backup", "Settings → Files & storage → Back up library: one file with everything. Keep it somewhere safe.", Tab.Library),
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
 ,
        ) {
            Text("${index + 1} / ${tips.size}", style = Type.mono.copy(color = c.muted, fontSize = 11.sp))
            Spacer(Modifier.height(4.dp))
            Text(tip.title, style = Type.section.copy(color = c.text, fontSize = 17.sp))
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

/** One version in What's new; [era] starts a new part of Quiet's story. */
class Release(val version: String, val items: List<String>, val era: String? = null)

/** Quiet's whole story, newest first: from one HTML file to the native Android app. */
val whatsNew = listOf(
    Release("0.5.0 L8.4", listOf(
        "Opening Quiet: the knob turns on, its dot lights, and it flies into the top bar as the library rises in",
        "Pulling to the next chapter: a ring fills as you pull, the arrow pops at the point of no return, and the sign names the chapter",
        "Chapter changes no longer jump or flicker",
        "Leaving a Settings page (or a book) with the back swipe no longer flashes the old page",
    ), era = "The native Android app"),
    Release("0.5.0 L8.3", listOf(
        "Swipe left or right in the library for All, Reading, Want, Done and Series; the key follows your finger",
        "Now reading: your book floats above the tabs, one tap away. Swipe it sideways for your other books, down to tuck it away, hold for more",
        "The bar is frosted glass; choose where it shows and how solid it is in Settings → Interface",
        "It slides away while you scroll down and comes back as you scroll up",
    )),
    Release("0.5.0 L8.2", listOf(
        "Books grow out of their cover when you open them, and shrink back into it when you close them",
        "The back swipe drags pages and books away with your finger; change your mind and they spring back",
        "Settings pages slide in over each other, like iPhone",
        "Scroll mode: the page stretches at a chapter’s end; pull, feel the click, let go for the next chapter",
        "Tabs glide, choices slide, covers sink under your finger, lists reflow, numbers count up",
        "Tap the tab you’re on to glide back to the top",
    )),
    Release("0.5.0 L8.1", listOf(
        "The curl folds the book’s page only, in Quiet’s page colour (no white box from the book)",
        "Every tap vibrates the same way, on phones where it was faint or missing",
        "Switches no longer stick out of their card",
        "What’s new tells the whole story",
    )),
    Release("0.5.0 L8", listOf(
        "A short tour on the real buttons (replay it from About)",
        "What’s new: all of Quiet’s story, from the first HTML file",
        "Report a problem: you read the report, then send it yourself",
        "Custom page and text colours",
        "Motion and text size finished",
    )),
    Release("0.5.0 L7", listOf("Every format: MOBI, AZW3, FB2, Word, ODT, RTF, Markdown, HTML, Palm, comics", "PDF zoom: pinch and double-tap")),
    Release("0.5.0 L6", listOf("Stats: today’s needle meter, the week’s lights, streak, goals, and five detailed stats", "“You read for 25 min” when you close a book")),
    Release("0.5.0 L5", listOf("Filters, collections, tags, series, sort and group", "Hold a cover to select several, with Undo", "A page for each book", "Book folders")),
    Release("0.5.0 L4", listOf("Highlights, underline, strikethrough, notes, quotes with owners, bookmarks", "Translate, search in a book", "The Notes tab")),
    Release("0.5.0 L3", listOf("The reader bar: tuning scale, your four keys, More", "Light, warmth, look and layout, volume keys, clock", "Page curl, footnotes, pictures, bionic reading, the ruler, your fonts")),
    Release("0.5.0 L1–L2", listOf("Read EPUB, PDF and TXT, in scroll or pages", "Back up and restore (the same file as 0.4.2), safety copy, Recently deleted")),
    Release("0.5.0 L0", listOf("Quiet rebuilt as a real Android app (Kotlin), no web page inside", "The Radio look, keys that press down, real vibration")),

    Release("iPhone 1.0", listOf("Quiet 0.4.2 on the iPhone: the reader, curl, notes, stats, backups", "Installed with Sideloadly or AltStore"), era = "iPhone"),

    Release("0.4.2", listOf("The page curl follows your finger, like paper; let go early and it settles back"), era = "0.4 · Radio"),
    Release("0.4.1", listOf("Stats like a radio: a needle meter for today, level lights for the week, three readouts")),
    Release("0.4.0", listOf(
        "One design: Radio, after a 1960s radio, with the knob icon",
        "The reader bar at your thumb, and your own four keys",
        "Library backup and restore in one file; a safety copy that brings a lost library back",
        "Book info, Settings in sections, the tour, Report a problem, the Motion setting",
    )),

    Release("0.3.4", listOf(
        "A fourth style: Frosty Glass",
        "Books zoom out of their covers; swipe from the edge to go back",
        "Page curl; Word, ODT, RTF, Markdown, Palm, HTMLZ; CBR, CB7, CBT comics",
        "Your own fonts; series",
    ), era = "0.3 · Styles, notes and stats"),
    Release("0.3.3", listOf("Five reading stats: your year, when you read, your pace, finished books, book by book", "Motion like iOS: lists stretch, pull for the next chapter, sheets drag down", "Each style moves its own way")),
    Release("0.3.2", listOf("Two design styles: Quiet Star and Paper (and Old Tashkent)", "A tab bar: Library, Notes, Reading, Settings")),
    Release("0.3.1", listOf("Book folders: new books show up by themselves", "Quotes have owners", "A second translation service when Google is busy")),
    Release("0.3.0", listOf(
        "Themes (Black and Custom), text weight, paragraph spacing, indent, hyphenation",
        "Warm light, the reading ruler, bionic reading, clock and battery, footnotes, picture zoom",
        "Tags, ratings, reviews, Want to read, edit details and covers, shelf view, select several, Recently deleted, duplicates",
        "Underline, strikethrough, quotes, the Notes screen, translate, reading stats, goals and streak",
    )),

    Release("0.2.0", listOf(
        "The second Android app: library with covers, search, sort, grid or list, statuses and collections",
        "Scroll or pages; fonts, size, spacing, margins, alignment, three themes",
        "Bookmarks, highlights in four colours with notes, search in a book, look up and translate, time left",
        "Open with Quiet, volume keys turn pages, the screen stays on",
    ), era = "The beginning"),
    Release("0.1.0", listOf("Quiet as an Android app (an APK), built by GitHub on every change")),
    Release("Web app, offline", listOf("Installable from the browser (Add to Home Screen), works without internet, made for phones")),
    Release("Web app", listOf("Quiet on the web, with a library, published on GitHub Pages")),
    Release("The first Quiet", listOf("One HTML file that opens an EPUB: a quiet reader with nothing extra")),
)

@Composable
fun WhatsNewSheet(onClose: () -> Unit) {
    val c = LocalColors.current
    Sheet(onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text("What’s new", style = Type.section.copy(color = c.text))
            Text("Quiet ${BuildConfig.QUIET_VERSION} · ${BuildConfig.QUIET_LAYER} · native", style = Type.mono.copy(color = c.muted, fontSize = 12.sp))
            whatsNew.forEachIndexed { i, r ->
                r.era?.let { era ->
                    Spacer(Modifier.height(if (i == 0) 14.dp else 26.dp))
                    Text(era.uppercase(), style = Type.overline.copy(color = c.muted))
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                }
                Spacer(Modifier.height(12.dp))
                Text(r.version, style = Type.rowTitle.copy(color = if (i == 0) c.accentStrong else c.text))
                Spacer(Modifier.height(4.dp))
                r.items.forEach { line ->
                    val style = Type.body.copy(color = if (i == 0) c.text else c.muted)
                    // A hanging bullet: wrapped lines start under the words, not under the dot.
                    Row { Text("•", style = style, modifier = Modifier.width(14.dp)); Text(line, style = style, modifier = Modifier.weight(1f)) }
                }
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
            Text("Report a problem", style = Type.section.copy(color = c.text))
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
