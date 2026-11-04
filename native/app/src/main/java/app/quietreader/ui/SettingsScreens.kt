package app.quietreader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quietreader.BuildConfig
import app.quietreader.MotionMode
import app.quietreader.QuietSettings
import app.quietreader.Rotation
import app.quietreader.TextScale
import app.quietreader.ThemeMode
import app.quietreader.phoneWantsLessMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Settings: a home list of sections; each opens on its own page, as in the web app. */
@Composable
fun SettingsScreen(state: AppState, settings: QuietSettings) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = state.page,
        transitionSpec = { pageTransition(reduced, deeper = targetState.ordinal > initialState.ordinal) },
        label = "settings-page",
    ) { page ->
        when (page) {
            SettingsPage.Home -> SettingsHome(state)
            SettingsPage.Interface -> InterfacePage(state, settings)
            SettingsPage.Reader -> ReaderPage(state, settings)
            SettingsPage.Library -> LibraryPage(state)
            SettingsPage.Files -> FilesPage(state)
            SettingsPage.Privacy -> PrivacyPage(state, settings)
            SettingsPage.Trash -> TrashPage(state)
            SettingsPage.About -> AboutPage(state)
        }
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        content = content,
    )
}

private data class Section(val number: String, val title: String, val hint: String, val layer: String?, val page: SettingsPage?)

private val sections = listOf(
    Section("01", "Interface", "Day and night, app text size, motion, vibration", null, SettingsPage.Interface),
    Section("02", "Reader", "Scroll or pages, the reader bar, volume keys, time and battery", null, SettingsPage.Reader),
    Section("03", "Library", "Book folders Quiet watches for new books", null, SettingsPage.Library),
    Section("04", "Files & storage", "Back up and restore, space used, delete everything", null, SettingsPage.Files),
    Section("05", "Privacy & translation", "What leaves your phone: only text you translate", null, SettingsPage.Privacy),
)

@Composable
private fun SettingsHome(state: AppState) {
    Page {
        Card {
            sections.forEachIndexed { i, s ->
                if (i > 0) Divider()
                SettingsRow(
                    title = s.title, hint = s.hint, layer = s.layer,
                    lead = { Text(s.number, style = Type.mono.copy(color = LocalColors.current.muted)) },
                ) {
                    if (s.page != null) state.page = s.page else state.toast("${s.title} comes in ${s.layer}.")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card {
            SettingsRow("About Quiet", "Version, what’s new, the tour, your data", null, lead = { Logo(26.dp) }) {
                state.page = SettingsPage.About
            }
            Divider()
            SettingsRow("Report a problem", "You see the report and send it yourself", "L8", lead = { Spacer(Modifier.width(26.dp)) }) {
                state.toast("Report a problem comes in L8.")
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, hint: String, layer: String?, lead: @Composable () -> Unit, onClick: () -> Unit) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { haptics?.tick(); onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(34.dp)) { lead() }
        Column(Modifier.weight(1f).alpha(if (layer == null) 1f else 0.6f)) {
            Text(title, style = Type.rowTitle.copy(color = c.text))
            Spacer(Modifier.height(2.dp))
            Text(hint, style = Type.hint.copy(color = c.muted))
        }
        Spacer(Modifier.width(10.dp))
        if (layer != null) LayerTag(layer)
        else Icon(Icons.ChevronRight, null, Modifier.size(18.dp), tint = c.text)
    }
}

@Composable
private fun PageHeader(title: String, state: AppState, back: SettingsPage = SettingsPage.Home) {
    val c = LocalColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconKey(Icons.ChevronLeft, "Back", { state.page = back })
        Spacer(Modifier.width(12.dp))
        Text(title, style = Type.pageTitle.copy(color = c.text))
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun SectionTitle(title: String, hint: String = "") {
    val c = LocalColors.current
    Text(title, style = Type.section.copy(color = c.text))
    if (hint.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(hint, style = Type.hint.copy(color = c.muted))
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SettingBlock(label: String, hint: String = "", content: @Composable () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.padding(16.dp)) {
        Text(label, style = Type.label.copy(color = c.text, fontWeight = FontWeight.Normal))
        Spacer(Modifier.height(10.dp))
        content()
        if (hint.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(hint, style = Type.hint.copy(color = c.muted))
        }
    }
}

@Composable
private fun InterfacePage(state: AppState, settings: QuietSettings) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    // Turning vibration on gives a firm click, so you feel that it works.
    fun setVibration(on: Boolean) {
        settings.haptics = on
        if (on) haptics?.confirm()
    }
    Page {
        PageHeader("Interface", state)
        SectionTitle("Theme", "System follows your phone: Day while it’s light, Night while it’s in dark mode.")
        Card {
            SettingBlock("Theme") {
                ChoiceRow(ThemeMode.entries.map { it to it.label }, settings.theme, { settings.theme = it })
            }
        }
        Spacer(Modifier.height(28.dp))
        SectionTitle("The app")
        Card {
            TextSizeBlock(settings)
            Divider()
            val lessMotion = phoneWantsLessMotion(context)
            SettingBlock(
                "Motion",
                if (lessMotion) "Your phone is asking apps for less motion right now (“Remove animations”). Choose Full to keep Quiet’s animations anyway."
                else "Your phone isn’t asking for less motion, so Follow phone means full motion.",
            ) {
                ChoiceRow(MotionMode.entries.map { it to it.label }, settings.motion, { settings.motion = it }, perRow = 3)
            }
            Divider()
            SettingBlock("Screen rotation") {
                ChoiceRow(Rotation.entries.map { it to it.label }, settings.rotation, { settings.rotation = it }, perRow = 3)
            }
            Divider()
            val c = LocalColors.current
            Row(
                Modifier.fillMaxWidth().clickable { setVibration(!settings.haptics) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Vibration", style = Type.label.copy(color = c.text, fontWeight = FontWeight.Normal))
                    Spacer(Modifier.height(2.dp))
                    Text("A light tap when you press a key, bookmark, highlight or change a setting.", style = Type.hint.copy(color = c.muted))
                }
                Spacer(Modifier.width(12.dp))
                Switch(settings.haptics, { on -> setVibration(on) }, "Vibration")
            }
        }
    }
}

/** Text size: a dial from 80% to 150%; the whole app resizes as you slide. */
@Composable
private fun TextSizeBlock(settings: QuietSettings) {
    val c = LocalColors.current
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Text size", style = Type.label.copy(color = c.text, fontWeight = FontWeight.Normal), modifier = Modifier.weight(1f))
            if (settings.textScale != TextScale.DEFAULT) {
                TextKey("Reset", { settings.textScale = TextScale.DEFAULT })
                Spacer(Modifier.width(10.dp))
            }
            Text("${settings.textScale}%", style = Type.mono.copy(color = c.text))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A", style = Type.label.copy(color = c.muted, fontSize = 12.sp))
            Spacer(Modifier.width(10.dp))
            Dial(
                value = settings.textScale.toFloat(),
                onChange = { settings.textScale = it.roundToInt() },
                range = TextScale.MIN.toFloat()..TextScale.MAX.toFloat(),
                step = TextScale.STEP.toFloat(),
                label = "Text size",
                valueText = "${settings.textScale} percent",
                marks = listOf(TextScale.DEFAULT.toFloat()),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text("A", style = Type.label.copy(color = c.muted, fontSize = 20.sp))
        }
    }
}

@Composable
private fun AboutPage(state: AppState) {
    val c = LocalColors.current
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val quiet = app.quietreader.LocalQuiet.current
    val books by quiet.library.books.collectAsState()
    val spaceUsed by produceState("…", books.size) {
        value = withContext(Dispatchers.IO) { fileSize(folderSize(context.filesDir.parentFile ?: context.filesDir)) }
    }
    Page {
        PageHeader("About Quiet", state)
        Card {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Logo(64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("quiet", style = Type.emptyTitle.copy(color = c.text, fontSize = 26.sp))
                        Text("Version ${BuildConfig.QUIET_VERSION} · ${BuildConfig.QUIET_LAYER} · native", style = Type.mono.copy(color = c.muted))
                        Text("A reader with nothing extra.", style = Type.body.copy(color = c.muted))
                    }
                }

                Overline("Your data · on this phone only")
                Row(
                    Modifier.fillMaxWidth().border(1.dp, c.line, RoundedCornerShape(8.dp)).padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Fact(books.count { it.deletedAt == null }.toString(), "Books")
                    Fact(spaceUsed, "Space used")
                    Fact(quiet.settings.lastBackup?.let { shortDate(it) } ?: "Never", "Last backup")
                }

                Overline("Promises")
                listOf(
                    "No account, no ads, no tracking.",
                    "Books and notes never leave this phone. Only text you choose to translate is sent.",
                    "No AI features.",
                ).forEachIndexed { i, line ->
                    if (i > 0) Divider()
                    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(c.done, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(12.dp))
                        Text(line, style = Type.body.copy(color = c.text))
                    }
                }

                Overline("Formats")
                Text(
                    "This native build opens EPUB, PDF and TXT (without DRM). Every other format (MOBI, AZW3, FB2, Word, comics…) arrives in L7; books in those formats are kept safe in your library until then.",
                    style = Type.body.copy(color = c.text),
                )

                Overline("Made with")
                listOf(
                    Triple("Kotlin and Jetpack Compose", "the app, Apache License 2.0", "https://developer.android.com/compose"),
                    Triple("Readium", "the e-book engine, BSD 3-Clause License", "https://readium.org"),
                    Triple("Archivo, DM Mono, Crimson Pro", "fonts, SIL Open Font License", "https://fonts.google.com"),
                    Triple("Lucide", "icons, ISC License", "https://lucide.dev"),
                ).forEach { (name, what, url) ->
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = c.accentStrong)) { append(name) }
                            append(" — $what")
                        },
                        style = Type.body.copy(color = c.muted),
                        modifier = Modifier.clickable { runCatching { uri.openUri(url) } }.padding(vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("made by extrasolarions on howling dark", style = Type.body.copy(color = c.muted))
            }
        }
    }
}

@Composable
private fun Overline(text: String) {
    val c = LocalColors.current
    Spacer(Modifier.height(22.dp))
    Text(text.uppercase(), style = Type.overline.copy(color = c.muted))
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Fact(value: String, label: String) {
    val c = LocalColors.current
    Column {
        Text(value, style = Type.monoLarge.copy(color = c.text))
        Text(label, style = Type.hint.copy(color = c.muted))
    }
}

private fun folderSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

fun fileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

fun shortDate(millis: Long): String =
    java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(millis))

private fun daysSince(millis: Long) = ((System.currentTimeMillis() - millis) / 86_400_000L).toInt()

/** Files & storage: backup and restore, Recently deleted, space used, delete everything. */
@Composable
private fun FilesPage(state: AppState) {
    val quiet = app.quietreader.LocalQuiet.current
    val c = LocalColors.current
    val books by quiet.library.books.collectAsState()
    val shelf = books.count { it.deletedAt == null }
    val trash = books.count { it.deletedAt != null }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val spaceUsed by produceState("…", books.size) {
        value = withContext(Dispatchers.IO) { fileSize(folderSize(context.filesDir)) }
    }
    Page {
        PageHeader("Files & storage", state)
        SectionTitle("Backup", "One file with your books, places, notes, quotes, stats, fonts and settings. Keep it somewhere safe (Google Drive, your computer, Telegram’s Saved Messages). It opens in Quiet 0.4.2 too.")
        Card {
            Column(Modifier.padding(16.dp)) {
                val last = quiet.settings.lastBackup
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Last backup", style = Type.label.copy(color = c.text, fontWeight = FontWeight.Normal), modifier = Modifier.weight(1f))
                    Text(last?.let { shortDate(it) } ?: "Never", style = Type.mono.copy(color = c.text))
                }
                if (shelf > 0 && (last == null || daysSince(last) >= 14)) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(c.accent, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(if (last == null) "No backup yet." else "No backup in ${daysSince(last)} days.", style = Type.hint.copy(color = c.accentStrong))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextKey("Back up library", { state.saveBackup() }, Modifier.weight(1f), kind = KeyKind.Primary, enabled = books.isNotEmpty())
                    TextKey("Restore", { state.pickBackup() }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text("Restoring adds what’s missing and never deletes anything. Settings come along only into an empty library.", style = Type.hint.copy(color = c.muted))
            }
        }
        Spacer(Modifier.height(28.dp))
        SectionTitle("Storage")
        Card {
            SettingsRow("Recently deleted", if (trash == 0) "Empty" else "$trash ${if (trash == 1) "book" else "books"} · deleted for good after 30 days", null,
                lead = { Icon(Icons.Trash, null, Modifier.size(18.dp), tint = c.muted) }) { state.page = SettingsPage.Trash }
            Divider()
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Space used", style = Type.label.copy(color = c.text, fontWeight = FontWeight.Normal), modifier = Modifier.weight(1f))
                Text(spaceUsed, style = Type.mono.copy(color = c.text))
            }
            Divider()
            Column(Modifier.padding(16.dp)) {
                Text("Delete everything", style = Type.label.copy(color = c.text, fontWeight = FontWeight.Normal))
                Spacer(Modifier.height(4.dp))
                Text("Every book, place and setting on this phone. Back up first if you might want them again.", style = Type.hint.copy(color = c.muted))
                Spacer(Modifier.height(10.dp))
                TextKey("Delete everything…", { confirmDelete = true }, kind = KeyKind.Danger, enabled = books.isNotEmpty())
            }
        }
    }
    if (confirmDelete) ConfirmDialog(
        title = "Delete everything?",
        text = "All $shelf books, your places and the books in Recently deleted will be gone from this phone. This can’t be undone.",
        confirm = "Delete everything",
        onDismiss = { confirmDelete = false },
    ) {
        confirmDelete = false
        quiet.safety.emptiedOnPurpose = true
        quiet.library.clearAll()
        state.toast("Your library is empty.")
    }
}

/** Recently deleted: books wait here for 30 days. */
@Composable
private fun TrashPage(state: AppState) {
    val quiet = app.quietreader.LocalQuiet.current
    val c = LocalColors.current
    val books by quiet.library.books.collectAsState()
    val trash = books.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }
    var confirmEmpty by remember { mutableStateOf(false) }
    Page {
        PageHeader("Recently deleted", state, back = SettingsPage.Files)
        if (trash.isEmpty()) {
            Text("Nothing here. Books you remove wait here for 30 days, then they’re deleted for good.", style = Type.body.copy(color = c.muted))
        } else {
            Card {
                trash.forEachIndexed { i, book ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(book, Modifier.width(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(book.title, style = Type.rowTitle.copy(color = c.text), maxLines = 2)
                            val left = (app.quietreader.data.Safety.TRASH_DAYS - daysSince(book.deletedAt ?: 0)).coerceAtLeast(0)
                            Text("$left ${if (left == 1) "day" else "days"} left", style = Type.hint.copy(color = c.muted))
                        }
                        IconKey(Icons.Restore, "Put back", {
                            quiet.library.put(book.with({ put("deletedAt", org.json.JSONObject.NULL) }))
                            state.toast("“${book.title}” is back in your library.")
                        })
                        Spacer(Modifier.width(8.dp))
                        IconKey(Icons.Trash, "Delete for good", { quiet.library.deleteForever(listOf(book.id)) }, kind = KeyKind.Danger)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            TextKey("Empty Recently deleted…", { confirmEmpty = true }, kind = KeyKind.Danger)
        }
    }
    if (confirmEmpty) ConfirmDialog(
        title = "Delete these books for good?",
        text = "${trash.size} ${if (trash.size == 1) "book" else "books"} will be deleted from this phone. This can’t be undone.",
        confirm = "Delete for good",
        onDismiss = { confirmEmpty = false },
    ) {
        confirmEmpty = false
        quiet.library.deleteForever(trash.map { it.id })
    }
}

/** Reader: reading defaults (also in the reader's More), the bar, keys and screen. */
@Composable
private fun ReaderPage(state: AppState, settings: QuietSettings) {
    val c = LocalColors.current
    Page {
        PageHeader("Reader", state)
        SectionTitle("Page", "Size, font, light and layout are also under More while a book is open.")
        Card {
            SettingBlock("Reading mode") {
                ChoiceRow(app.quietreader.Flow.entries.map { it to it.label }, settings.flow, { settings.flow = it }, perRow = 2)
            }
            Divider()
            SettingBlock("Page turn (in page mode)", "Curl bends the page like paper under your finger. Choosing it switches to Pages.") {
                ChoiceRow(app.quietreader.PageTurn.entries.map { it to it.label }, settings.pageTurn, {
                    settings.pageTurn = it
                    if (it == app.quietreader.PageTurn.Curl && settings.flow != app.quietreader.Flow.Paginated) {
                        settings.flow = app.quietreader.Flow.Paginated
                        state.toast("Curl turns pages, so reading mode is now Pages.")
                    }
                }, perRow = 3)
            }
            Divider()
            SettingBlock("Reading ruler", "Line marks the line you’re on; Focus dims the rest. To move it, hold it for a moment, then drag.") {
                ChoiceRow(app.quietreader.Ruler.entries.map { it to it.label }, settings.ruler, { settings.ruler = it }, perRow = 3)
            }
            Divider()
            ToggleRow("Bionic reading", "The start of each word in bold, to guide the eye.", settings.bionic) { settings.bionic = it }
        }
        Spacer(Modifier.height(28.dp))
        SectionTitle("Your fonts", "TTF, OTF, WOFF or WOFF2 files. They appear under Text → Font.")
        YourFonts(state, settings)
        Spacer(Modifier.height(28.dp))
        SectionTitle("Reader bar", "The four keys at the bottom of the page. More always has the rest.")
        Card { Column(Modifier.padding(16.dp)) { app.quietreader.reader.CustomizeKeys() } }
        Spacer(Modifier.height(28.dp))
        SectionTitle("While reading")
        Card {
            ToggleRow("Volume keys turn pages", "Volume down for the next page, up for the previous one.", settings.volumeKeys) { settings.volumeKeys = it }
            Divider()
            ToggleRow("Time and battery", "Small, in the corner, while the bars are hidden.", settings.showClock) { settings.showClock = it }
            Divider()
            ToggleRow("Keep the screen on", "While a book is open.", settings.keepAwake) { settings.keepAwake = it }
        }
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, on: Boolean, set: (Boolean) -> Unit) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().clickable { set(!on) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.label.copy(color = c.text, fontWeight = FontWeight.Normal))
            Spacer(Modifier.height(2.dp))
            Text(hint, style = Type.hint.copy(color = c.muted))
        }
        Spacer(Modifier.width(12.dp))
        Switch(on, set, title)
    }
}

@Composable
private fun YourFonts(state: AppState, settings: QuietSettings) {
    val quiet = app.quietreader.LocalQuiet.current
    val c = LocalColors.current
    // Re-read when the library's records change (a font added or removed).
    val books by quiet.library.books.collectAsState()
    var version by remember { mutableStateOf(0) }
    val families = remember(books, version, state.fontsVersion) { quiet.fonts.families() }
    Card {
        families.forEachIndexed { i, family ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(family, style = Type.body.copy(color = c.text), modifier = Modifier.weight(1f))
                if (settings.customFont == family) { Text("in use", style = Type.hint.copy(color = c.accentStrong)); Spacer(Modifier.width(10.dp)) }
                IconKey(Icons.Trash, "Remove $family", {
                    quiet.fonts.remove(family)
                    if (settings.customFont == family) settings.customFont = null
                    version++
                }, kind = KeyKind.Danger)
            }
        }
        if (families.isNotEmpty()) Divider()
        Column(Modifier.padding(16.dp)) { TextKey("Add a font…", { state.pickFont() }) }
    }
}

/** Privacy & translation: what leaves the phone (only text you choose to translate), and into which language. */
@Composable
private fun PrivacyPage(state: AppState, settings: QuietSettings) {
    val c = LocalColors.current
    Page {
        PageHeader("Privacy & translation", state)
        SectionTitle("What leaves your phone")
        Card {
            Column(Modifier.padding(16.dp)) {
                listOf(
                    "No account, no ads, no tracking, no analytics.",
                    "Your books, places, notes and stats stay on this phone. Backups are files you keep.",
                    "Only text you choose to translate is sent: to Google Translate, or to MyMemory if Google refuses.",
                    "No AI features.",
                ).forEach { line ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Box(Modifier.padding(top = 7.dp).size(7.dp).background(c.done, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(12.dp))
                        Text(line, style = Type.body.copy(color = c.text))
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        SectionTitle("Translate into", "Select text in a book, then Translate.")
        Card {
            Column(Modifier.padding(16.dp)) {
                ChoiceRow(app.quietreader.reader.Translate.LANGUAGES.map { it to app.quietreader.reader.Translate.name(it) }, settings.translateTo,
                    { settings.translateTo = it }, perRow = 3)
            }
        }
    }
}

/** Library: the book folders Quiet watches. */
@Composable
private fun LibraryPage(state: AppState) {
    val quiet = app.quietreader.LocalQuiet.current
    val c = LocalColors.current
    var version by remember { mutableStateOf(0) }
    val folders = remember(version, state.foldersVersion) { quiet.folders.all() }
    Page {
        PageHeader("Library", state)
        SectionTitle("Book folders", "Choose folders with your books (and folders inside them). Quiet adds every EPUB, PDF and TXT in them, " +
            "when it starts and when you come back after a while. A book you delete stays deleted. Prepare a folder full of books first, for example Download/Books.")
        Card {
            if (folders.isEmpty()) Text("No folders yet.", style = Type.body.copy(color = c.muted), modifier = Modifier.padding(16.dp))
            folders.forEachIndexed { i, uri ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(quiet.folders.name(uri), style = Type.body.copy(color = c.text), modifier = Modifier.weight(1f))
                    IconKey(Icons.Trash, "Stop watching", { quiet.folders.remove(uri); version++ }, kind = KeyKind.Danger)
                }
            }
            Divider()
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextKey("Add a folder…", { state.pickFolder() }, Modifier.weight(1f), kind = KeyKind.Primary)
                TextKey("Look now", { state.scanFolders() }, Modifier.weight(1f), enabled = folders.isNotEmpty())
            }
        }
    }
}
