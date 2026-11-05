package app.quietreader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.togetherWith
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
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
    val parent = if (state.page == SettingsPage.Trash) SettingsPage.Files else SettingsPage.Home
    // Back (the swipe from the edge, or the back key): the page follows your finger off to the
    // right, with the page you came from waiting underneath.
    val swipe = rememberBackSwipe(enabled = state.tab == Tab.Settings && state.page != SettingsPage.Home) { state.page = parent }
    LaunchedEffect(state.page) {
        // After a swipe, wait until the page behind has really taken over before letting go of it.
        if (swipe.finishedBySwipe) { androidx.compose.runtime.withFrameNanos { }; androidx.compose.runtime.withFrameNanos { } }
        swipe.reset()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        if (swipe.active) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = -(1f - swipe.progress) * w * 0.3f }) { SettingsPageContent(parent, state, settings) }
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f * (1f - swipe.progress))))
        }
        AnimatedContent(
            targetState = state.page,
            transitionSpec = {
                if (swipe.finishedBySwipe) EnterTransition.None togetherWith ExitTransition.None
                else pageTransition(reduced, deeper = targetState.ordinal > initialState.ordinal)
            },
            modifier = Modifier.graphicsLayer {
                translationX = swipe.progress * w
                shadowElevation = if (swipe.active) 18.dp.toPx() else 0f
            },
            label = "settings-page",
        ) { page -> SettingsPageContent(page, state, settings) }
    }
}

@Composable
private fun SettingsPageContent(page: SettingsPage, state: AppState, settings: QuietSettings) {
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

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp).padding(bottom = LocalNowRoom.current),
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
            SettingsRow("Report a problem", "You see the report and send it yourself", null, lead = { Spacer(Modifier.width(26.dp)) }) {
                state.guide = "report"
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
        SectionTitle("Theme", "System follows your phone: Day while it’s light, Night while it’s in dark mode. Black is for OLED screens. Custom: your own page and text colours.")
        Card {
            SettingBlock("Theme") {
                ChoiceRow(ThemeMode.entries.map { it to it.label }, settings.theme, { settings.theme = it }, perRow = 3)
            }
            if (settings.theme == ThemeMode.Custom) {
                Divider()
                CustomColours(settings)
            }
        }
        Spacer(Modifier.height(28.dp))
        SectionTitle("Now reading", "The book you're in, floating above the tabs. Tap to read, swipe sideways for your other books, swipe down to tuck it away, hold for more.")
        Card {
            ToggleRow("Show the Now reading bar", "Your last unfinished book, one tap away.", settings.nowBar) { settings.nowBar = it }
            if (settings.nowBar) {
                Divider()
                SettingBlock("Where") {
                    ChoiceRow(listOf(true to "Every tab", false to "Library only"), settings.nowBarEverywhere, { settings.nowBarEverywhere = it })
                }
                Divider()
                Column(Modifier.padding(16.dp)) {
                    val c = LocalColors.current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Opacity", style = Type.label.copy(color = c.text, fontWeight = FontWeight.Normal), modifier = Modifier.weight(1f))
                        Text("${settings.nowBarOpacity}%", style = Type.mono.copy(color = c.text))
                    }
                    Spacer(Modifier.height(6.dp))
                    Dial(
                        value = settings.nowBarOpacity.toFloat(),
                        onChange = { settings.nowBarOpacity = it.roundToInt() },
                        range = 40f..100f,
                        step = 5f,
                        label = "Opacity",
                        valueText = "${settings.nowBarOpacity} percent",
                        marks = listOf(100f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Frosted glass: lower lets the page behind glow through, blurred. The bar below shows it as you slide.", style = Type.hint.copy(color = c.muted))
                }
                Divider()
                ToggleRow("Hide while scrolling", "It slides away as you scroll down a list and comes back as you scroll up.", settings.nowBarAutoHide) { settings.nowBarAutoHide = it }
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
                when {
                    settings.motion == MotionMode.Reduced -> "Reduced: no sliding or springing anywhere in Quiet; things simply appear. Page curl becomes a plain turn."
                    lessMotion -> "Your phone has “Remove animations” on, so Quiet keeps still too. Android turns animations off for every app while it’s on."
                    else -> "Follow phone: full motion now; less motion when your phone’s “Remove animations” is on."
                },
            ) {
                ChoiceRow(MotionMode.entries.map { it to it.label }, settings.motion, { settings.motion = it }, perRow = 3)
            }
            Divider()
            SettingBlock("Screen rotation") {
                ChoiceRow(Rotation.entries.map { it to it.label }, settings.rotation, { settings.rotation = it }, perRow = 3)
            }
            Divider()
            val c = LocalColors.current
            SwitchRow(settings.haptics, { on -> setVibration(on) }, tick = false) {
                Column(Modifier.weight(1f)) {
                    Text("Vibration", style = Type.label.copy(color = c.text, fontWeight = FontWeight.Normal))
                    Spacer(Modifier.height(2.dp))
                    Text("A light tap when you press a key, bookmark, highlight or change a setting.", style = Type.hint.copy(color = c.muted))
                }
            }
        }
    }
}

private val pageSwatches = listOf(
    "Paper" to "#f3efe6", "White" to "#ffffff", "Sepia" to "#f4ecd8", "Sage" to "#e3eadb", "Rose" to "#f3e4e1",
    "Slate" to "#2b2f36", "Navy" to "#18202e", "Forest" to "#1c2620", "Black" to "#000000",
)
private val inkSwatches = listOf(
    "Graphite" to "#2b2a26", "Black" to "#000000", "Brown" to "#4a3b2c", "Navy" to "#1e2a44",
    "Cream" to "#e8e2d4", "Grey" to "#bdbdbd", "Amber" to "#e6b673", "Green" to "#a9c8a0",
)

/** Custom theme: the page colour and the text colour, with a sample and a warning if it's hard to read. */
@Composable
private fun CustomColours(settings: QuietSettings) {
    val c = LocalColors.current
    val bg = parseColour(settings.customBg) ?: Color(0xFFF3EFE6)
    val ink = parseColour(settings.customText) ?: Color(0xFF2B2A26)
    Column(Modifier.padding(16.dp)) {
        Box(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(8.dp)).border(1.dp, c.line, RoundedCornerShape(8.dp)).padding(14.dp)) {
            Text("It was a bright cold day in April, and the clocks were striking thirteen.", style = Type.body.copy(color = ink, fontSize = 16.sp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Page", style = Type.label.copy(color = c.text, fontWeight = FontWeight.Normal))
        Spacer(Modifier.height(8.dp))
        Swatches(pageSwatches, settings.customBg) { settings.customBg = it }
        Spacer(Modifier.height(14.dp))
        Text("Text", style = Type.label.copy(color = c.text, fontWeight = FontWeight.Normal))
        Spacer(Modifier.height(8.dp))
        Swatches(inkSwatches, settings.customText) { settings.customText = it }
        val ratio = contrast(bg, ink)
        if (ratio < 4.5) {
            Spacer(Modifier.height(10.dp))
            Text("Hard to read (contrast ${"%.1f".format(ratio)} : 1). Pick a ${if (bg.luminance() < 0.4f) "lighter" else "darker"} text colour.",
                style = Type.hint.copy(color = c.danger))
        }
    }
}

@Composable
private fun Swatches(options: List<Pair<String, String>>, chosen: String, onPick: (String) -> Unit) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    androidx.compose.foundation.layout.FlowRow(
        Modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { (name, hex) ->
            val on = hex.equals(chosen, ignoreCase = true)
            Box(
                Modifier.size(44.dp)
                    .border(if (on) 2.dp else 1.dp, if (on) c.accent else c.keyEdge, RoundedCornerShape(22.dp))
                    .padding(4.dp)
                    .background(parseColour(hex) ?: Color.Gray, RoundedCornerShape(18.dp))
                    .clickable { haptics?.tick(); onPick(hex) }
                    .semantics { contentDescription = name },
            )
        }
    }
}

private fun parseColour(hex: String): Color? = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()

/** The WCAG contrast ratio between two colours (1 to 21). */
internal fun contrast(a: Color, b: Color): Double {
    val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (hi + 0.05) / (lo + 0.05)
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
    val recordsVersion by quiet.library.recordsVersion.collectAsState()
    val spaceUsed by produceState("…", books.size) {
        value = withContext(Dispatchers.IO) { fileSize(folderSize(context.filesDir.parentFile ?: context.filesDir)) }
    }
    val notes = remember(recordsVersion) { quiet.notes.all() }
    val fonts = remember(recordsVersion, state.fontsVersion) { quiet.fonts.all().size }
    Page {
        PageHeader("About Quiet", state)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextKey("The tour", {
                quiet.settings.tourDone = false
                state.startTour()
            }, Modifier.weight(1f))
            TextKey("What’s new", { state.guide = "whatsnew" }, Modifier.weight(1f))
            TextKey("Report", { state.guide = "report" }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
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
                    Fact(books.count { it.deletedAt == null }.toString(), "Books", Modifier.weight(1f))
                    Fact(spaceUsed, "Space used", Modifier.weight(1f))
                    Fact(quiet.settings.lastBackup?.let { shortDate(it) } ?: "Never", "Last backup", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().border(1.dp, c.line, RoundedCornerShape(8.dp)).padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Fact(notes.count { !it.isBookmark }.toString(), "Highlights", Modifier.weight(1f))
                    Fact(notes.count { !it.isBookmark && it.note.isNotBlank() }.toString(), "Notes", Modifier.weight(1f))
                    Fact(notes.count { it.quote }.toString(), "Quotes", Modifier.weight(1f))
                    Fact(fonts.toString(), "Fonts", Modifier.weight(1f))
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
                    "EPUB, MOBI, AZW3 and AZW (without DRM), FB2, PDF, Word (DOCX), OpenDocument (ODT), RTF, Markdown, TXT, HTML, Palm (PDB) and comics (CBZ, CBR, CB7, CBT).",
                    style = Type.body.copy(color = c.text),
                )

                Overline("Made with")
                listOf(
                    Triple("Kotlin and Jetpack Compose", "the app, Apache License 2.0", "https://developer.android.com/compose"),
                    Triple("Readium", "the e-book engine, BSD 3-Clause License", "https://readium.org"),
                    Triple("foliate-js", "the MOBI and AZW3 reader Quiet’s is ported from, MIT License", "https://github.com/johnfactotum/foliate-js"),
                    Triple("jsoup", "HTML, FB2 and Word reading, MIT License", "https://jsoup.org"),
                    Triple("commonmark-java", "Markdown, BSD 2-Clause License", "https://github.com/commonmark/commonmark-java"),
                    Triple("Apache Commons Compress", "CB7 and CBT comics, Apache License 2.0", "https://commons.apache.org/proper/commons-compress/"),
                    Triple("XZ for Java", "7-Zip archives, public domain", "https://tukaani.org/xz/java.html"),
                    Triple("junrar", "CBR comics, UnRAR License", "https://github.com/junrar/junrar"),
                    Triple("Archivo, DM Mono, Crimson Pro", "fonts, SIL Open Font License", "https://fonts.google.com"),
                    Triple("Lucide", "icons, ISC License", "https://lucide.dev"),
                    Triple("Haze", "the frosted glass, Apache License 2.0", "https://github.com/chrisbanes/haze"),
                ).forEach { (name, what, url) ->
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = c.accentStrong)) { append(name) }
                            append(" — $what")
                        },
                        style = Type.body.copy(color = c.muted),
                        modifier = Modifier.click(role = null) { runCatching { uri.openUri(url) } }.padding(vertical = 3.dp),
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
private fun Fact(value: String, label: String, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    // One line each: shrinks a little at large text sizes rather than break a word.
    Column(modifier.padding(end = 4.dp)) {
        val big = Type.monoLarge.copy(color = c.text)
        val small = Type.hint.copy(color = c.muted)
        BasicText(value, style = big, maxLines = 1, softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = big.fontSize, stepSize = 0.5.sp))
        BasicText(label, style = small, maxLines = 1, softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = small.fontSize, stepSize = 0.5.sp))
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TrashPage(state: AppState) {
    val quiet = app.quietreader.LocalQuiet.current
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val books by quiet.library.books.collectAsState()
    val trash = books.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }
    // Select several: hold a book (as in the library), tap more; then put back or delete for good.
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selecting by remember { mutableStateOf(false) }
    var forGood by remember { mutableStateOf<List<app.quietreader.data.Book>>(emptyList()) }
    fun toggle(id: String) { selected = if (id in selected) selected - id else selected + id }
    fun putBack(list: List<app.quietreader.data.Book>) {
        quiet.library.putAll(list.map { it.with({ put("deletedAt", org.json.JSONObject.NULL) }) })
        state.toast(if (list.size == 1) "“${list[0].title}” is back in your library." else "${list.size} books are back in your library.")
    }
    androidx.activity.compose.BackHandler(enabled = selecting) { selecting = false; selected = emptySet() }
    if (forGood.isNotEmpty()) ConfirmDialog(
        if (forGood.size == 1) "Delete “${forGood[0].title}” for good?" else "Delete ${forGood.size} books for good?",
        "The book files and their places are deleted from this phone. This can’t be undone.", "Delete for good", { forGood = emptyList() },
    ) {
        quiet.library.deleteForever(forGood.map { it.id }); forGood = emptyList(); selected = emptySet(); selecting = false
    }
    Box(Modifier.fillMaxSize()) {
        Page {
            PageHeader("Recently deleted", state, back = SettingsPage.Files)
            if (trash.isEmpty()) {
                Text("Nothing here. Books you remove wait here for 30 days, then they’re deleted for good.", style = Type.body.copy(color = c.muted))
            } else {
                Text(if (selecting) "Tap books to add or take them out." else "Hold a book to select several.", style = Type.hint.copy(color = c.muted))
                Spacer(Modifier.height(10.dp))
                Card {
                    trash.forEachIndexed { i, book ->
                        if (i > 0) Divider()
                        val on = book.id in selected
                        Row(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClick = { if (selecting) { haptics?.tick(); toggle(book.id) } },
                                onLongClick = { haptics?.confirm(); if (!selecting) { selecting = true; selected = setOf(book.id) } else toggle(book.id) },
                            ).background(if (on) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selecting) {
                                Box(Modifier.size(22.dp).background(if (on) c.accent else c.panel, RoundedCornerShape(11.dp)).border(1.5.dp, if (on) c.accent else c.keyEdge, RoundedCornerShape(11.dp)),
                                    contentAlignment = Alignment.Center) { if (on) Icon(Icons.Check, null, Modifier.size(13.dp), tint = c.onAccent) }
                                Spacer(Modifier.width(10.dp))
                            }
                            Cover(book, Modifier.width(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(book.title, style = Type.rowTitle.copy(color = c.text), maxLines = 2)
                                val left = (app.quietreader.data.Safety.TRASH_DAYS - daysSince(book.deletedAt ?: 0)).coerceAtLeast(0)
                                Text("$left ${if (left == 1) "day" else "days"} left", style = Type.hint.copy(color = c.muted))
                            }
                            if (!selecting) {
                                IconKey(Icons.Restore, "Put back", { putBack(listOf(book)) })
                                Spacer(Modifier.width(8.dp))
                                IconKey(Icons.Trash, "Delete for good", { forGood = listOf(book) }, kind = KeyKind.Danger)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (!selecting) TextKey("Empty Recently deleted…", { forGood = trash }, kind = KeyKind.Danger)
                if (selecting) Spacer(Modifier.height(120.dp))
            }
        }
        if (selecting) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.panelDeep).hairline(c.line, top = true).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${selected.size} selected", style = Type.rowTitle.copy(color = c.text), modifier = Modifier.weight(1f))
                TextKey("All", { selected = trash.map { it.id }.toSet() })
                Spacer(Modifier.width(6.dp))
                TextKey("Done", { selecting = false; selected = emptySet() }, kind = KeyKind.Primary)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val chosen = trash.filter { it.id in selected }
                TextKey("Put back", { putBack(chosen); selected = emptySet(); selecting = false }, Modifier.weight(1f), enabled = chosen.isNotEmpty())
                TextKey("Delete for good…", { forGood = chosen }, Modifier.weight(1f), kind = KeyKind.Danger, enabled = chosen.isNotEmpty())
            }
        }
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
    SwitchRow(on, set) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.label.copy(color = c.text, fontWeight = FontWeight.Normal))
            Spacer(Modifier.height(2.dp))
            Text(hint, style = Type.hint.copy(color = c.muted))
        }
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
    var removing by remember { mutableStateOf<String?>(null) }
    removing?.let { family ->
        ConfirmDialog("Remove “$family”?", "The font file is deleted from this phone. Books using it go back to Crimson Pro.", "Remove font", { removing = null }) {
            quiet.fonts.remove(family)
            if (settings.customFont == family) settings.customFont = null
            version++; removing = null
        }
    }
    Card {
        families.forEachIndexed { i, family ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(family, style = Type.body.copy(color = c.text), modifier = Modifier.weight(1f))
                if (settings.customFont == family) { Text("in use", style = Type.hint.copy(color = c.accentStrong)); Spacer(Modifier.width(10.dp)) }
                IconKey(Icons.Trash, "Remove $family", { removing = family }, kind = KeyKind.Danger)
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
