package app.quietreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.quietreader.LocalQuiet
import app.quietreader.MotionMode
import app.quietreader.QuietSettings
import app.quietreader.ThemeMode
import app.quietreader.phoneWantsLessMotion
import kotlinx.coroutines.delay

enum class Tab(val label: String, val icon: ImageVector) {
    Library("Library", Icons.Library),
    Notes("Notes", Icons.NotebookText),
    Stats("Stats", Icons.ChartColumn),
    Settings("Settings", Icons.Settings),
}

enum class SettingsPage { Home, Interface, Reader, Library, Files, Trash, Privacy, About }

/** Where the reader is in the app, plus the message at the bottom. */
class AppState(tab: Tab = Tab.Library, page: SettingsPage = SettingsPage.Home) {
    var tab by mutableStateOf(tab)
    var page by mutableStateOf(page)
    var toast by mutableStateOf<String?>(null)
        private set
    var toastId by mutableIntStateOf(0)
        private set

    /** An action on the message, like Undo (it stays a little longer). */
    var toastAction: Pair<String, () -> Unit>? = null
        private set

    fun toast(message: String, action: Pair<String, () -> Unit>? = null) {
        toast = message
        toastAction = action
        toastId++
    }

    /** Something removed that can be put back: a message with Undo. */
    fun undoable(message: String, undo: () -> Unit) = toast(message, "Undo" to undo)

    fun hideToast() { toast = null }

    /** The book being read, if any. */
    var openBookId by mutableStateOf<String?>(null)
    var openNote: app.quietreader.data.Note? = null
    fun open(id: String, note: app.quietreader.data.Note? = null) { openNote = note; openBookId = id }
    /** Where each book's cover was last seen on screen (window pixels): a book opens out of it. */
    val coverBounds = HashMap<String, androidx.compose.ui.geometry.Rect>()
    /** Counts taps on the tab you're already on: its screen glides back to the top. */
    var reselect by mutableIntStateOf(0)

    // Things only the activity can do (system pickers), set by MainActivity.
    var pickBooks: () -> Unit = {}
    var saveBackup: () -> Unit = {}
    var pickBackup: () -> Unit = {}
    var pickFont: () -> Unit = {}
    var pickFolder: () -> Unit = {}
    var pickCover: (bookId: String) -> Unit = {}
    var scanFolders: () -> Unit = {}
    var foldersVersion by mutableIntStateOf(0)

    // The library's own state (kept while you switch tabs).
    var filter by mutableStateOf(Filter.All)
    var collection by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var searching by mutableStateOf(false)
    var selecting by mutableStateOf(false)
    var selected by mutableStateOf<Set<String>>(emptySet())
    var details by mutableStateOf<String?>(null)
    var librarySheet by mutableStateOf<String?>(null)
    // The guide: the tour's step (null when off), where its targets are, and the guide sheets.
    var tourStep by mutableStateOf<Int?>(null)
    val targets = androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>()
    var guide by mutableStateOf<String?>(null) // "whatsnew", "report"
    fun startTour() { tab = Tab.Library; page = SettingsPage.Home; selecting = false; tourStep = 0 }
    fun toggleSelected(id: String) { selected = if (id in selected) selected - id else selected + id }
    var fontsVersion by mutableIntStateOf(0)
    var busy by mutableStateOf<String?>(null)
    /** A backup that was read and waits for "Restore" (its summary, and what restoring does). */
    var confirmRestore by mutableStateOf<Pair<app.quietreader.data.Backup.Summary, () -> Unit>?>(null)
}

/** The colours for the chosen theme; System follows the phone's dark mode. */
@Composable
fun resolveColors(theme: ThemeMode, settings: QuietSettings? = null): QuietColors = when (theme) {
    ThemeMode.Day -> DayColors
    ThemeMode.Night -> NightColors
    ThemeMode.Black -> BlackColors
    ThemeMode.System -> if (isSystemInDarkTheme()) NightColors else DayColors
    // Custom: your page and text colours; the app around them follows the page's lightness.
    ThemeMode.Custom -> {
        val bg = runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(settings?.customBg ?: "#f3efe6")) }.getOrDefault(DayColors.readerBg)
        val ink = runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(settings?.customText ?: "#2b2a26")) }.getOrDefault(DayColors.readerText)
        (if (bg.luminance() < 0.4f) NightColors else DayColors).copy(readerBg = bg, readerText = ink)
    }
}

/** Quiet: the theme, motion and text size around the whole app. */
@Composable
fun QuietTheme(settings: QuietSettings, haptics: Haptics?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduced = when (settings.motion) {
        MotionMode.Full -> false
        MotionMode.Reduced -> true
        MotionMode.System -> phoneWantsLessMotion(context)
    }
    val target = resolveColors(settings.theme, settings)
    val colors = animateColors(target, if (reduced) snap() else tween(260))
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalReducedMotion provides reduced,
        LocalHaptics provides haptics,
        LocalDensity provides Density(density.density, density.fontScale * settings.textScale / 100f),
        content = content,
    )
}

@Composable
fun QuietApp(settings: QuietSettings, state: AppState = remember { AppState() }) {
    val c = LocalColors.current
    val reduced = LocalReducedMotion.current

    // Back: a Settings page goes back to Settings, a tab goes back to Library.
    BackHandler(enabled = state.tab == Tab.Library && (state.selecting || state.searching)) {
        state.selecting = false; state.selected = emptySet(); state.searching = false; state.query = ""
    }
    BackHandler(enabled = state.tab != Tab.Library && !(state.tab == Tab.Settings && state.page != SettingsPage.Home)) {
        state.tab = Tab.Library
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(state, settings)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = state.tab,
                    transitionSpec = { sideways(forward = targetState.ordinal > initialState.ordinal, reduced) },
                    label = "tab",
                ) { tab ->
                    when (tab) {
                        Tab.Library -> LibraryScreen(state)
                        Tab.Notes -> NotesTab(state)
                        Tab.Stats -> CompositionLocalProvider(LocalAppState provides state) { StatsScreen() }
                        Tab.Settings -> SettingsScreen(state, settings)
                    }
                }
            }
            TabBar(state)
        }
        // A book opens out of its cover over everything, and closes back into it.
        val quiet = LocalQuiet.current
        val book = remember(state.openBookId) { state.openBookId?.let { quiet.library.get(it) } }
        BookStage(state, book, reduced) { b ->
                if (b.format == app.quietreader.data.BookFormat.Other) {
                    LaunchedEffect(b.id) {
                        state.toast("Quiet can’t open ${b.fileName.substringAfterLast('.').uppercase()} files. It’s safe in your library.")
                        state.openBookId = null
                    }
                } else app.quietreader.reader.ReaderScreen(b, remember(b.id) { state.openNote.also { state.openNote = null } }, onInfo = { state.details = b.id },
                    onSessionEnd = { s -> if (s >= 60) state.toast("You read for ${app.quietreader.data.StatsMath.time(s)}.") },
                    onGoal = { state.toast("Today’s goal is done. Well read.") }) { state.openBookId = null }
        }
        // A book's page and the library's sheets, above everything (also from the reader's More).
        LibrarySheets(state)
        when (state.guide) {
            "whatsnew" -> WhatsNewSheet { state.guide = null }
            "report" -> ReportSheet { state.guide = null }
        }
        // The tour, over the app (not while a book is open).
        val step = state.tourStep
        if (step != null && state.openBookId == null) {
            val quietS = LocalQuiet.current.settings
            val tips = appTour.filter { it.key in state.targets }
            LaunchedEffect(step) { tips.getOrNull(step)?.tab?.let { state.tab = it } }
            CoachMarks(tips, state.targets, step.coerceAtMost(tips.size - 1),
                onNext = { if (step + 1 >= tips.size) { state.tourStep = null; quietS.tourDone = true } else state.tourStep = step + 1 },
                onSkip = { state.tourStep = null; quietS.tourDone = true })
        }
        // A long job (backup, restore): a quiet sheet over the app.
        state.busy?.let { Busy(it) }
        state.confirmRestore?.let { (summary, restore) ->
            ConfirmDialog(
                title = "Restore this backup?",
                text = "From ${shortDate(summary.createdAt)}: ${summary.books} ${if (summary.books == 1) "book" else "books"}" +
                    (if (summary.notes > 0) " and ${summary.notes} highlights" else "") +
                    ". Books you don’t have are added; nothing is deleted.",
                confirm = "Restore",
                onDismiss = { state.confirmRestore = null },
            ) { state.confirmRestore = null; restore() }
        }
        // Messages sit above the tab bar (and above a book's bottom bar).
        ToastHost(state, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 76.dp))
    }
}

@Composable
private fun Busy(text: String) {
    val c = LocalColors.current
    Box(
        Modifier.fillMaxSize().background(c.bg.copy(alpha = 0.92f)).clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KnobMark(Modifier.size(44.dp))
            Spacer(Modifier.height(14.dp))
            Text(text, style = Type.body.copy(color = c.text))
        }
    }
}

/** The top bar: the speaker grille behind the brand or the screen's name, and its keys. */
@Composable
private fun TopBar(state: AppState, settings: QuietSettings) {
    val c = LocalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .grille(c)
            .hairline(c.line)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.tab == Tab.Library) {
            Row(
                Modifier.background(c.bg, RoundedCornerShape(22.dp)).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                    .semantics { contentDescription = "Quiet" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KnobMark(Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("quiet", style = Type.brand.copy(color = c.text))
            }
        } else {
            Text(state.tab.label, style = Type.screenTitle.copy(color = c.text),
                modifier = Modifier.background(c.bg, RoundedCornerShape(6.dp)).padding(end = 8.dp))
        }
        Spacer(Modifier.weight(1f))
        if (state.tab == Tab.Library) {
            IconKey(Icons.More, "Library options", { state.librarySheet = "menu" }, Modifier.tourTarget(state.targets, "menu"), size = 42.dp)
            Spacer(Modifier.width(8.dp))
            IconKey(Icons.Plus, "Add books", { state.pickBooks() }, Modifier.tourTarget(state.targets, "add"), kind = KeyKind.Primary,
                size = 42.dp)
            Spacer(Modifier.width(8.dp))
        }
        val dark = c.isDark
        ThemeKey(dark, Modifier.tourTarget(state.targets, "theme")) { settings.theme = if (dark) ThemeMode.Day else ThemeMode.Night }
    }
}

/** The tab bar: a band selector. The chosen band is a raised key that glides to your tap. */
@Composable
private fun TabBar(state: AppState) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(c.panelDeep)
            .hairline(c.line, top = true)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(54.dp)) {
            val tabs = Tab.entries
            val width = maxWidth / tabs.size
            val x by animateDpAsState(width * state.tab.ordinal, glide(), label = "band")
            // The raised key under the chosen tab, with its orange light.
            Box(Modifier.offset(x = x).width(width).height(54.dp)) {
                Box(Modifier.fillMaxSize().offset(y = 1.dp).background(c.keyEdge, KeyShape))
                Box(Modifier.fillMaxSize().background(c.panel, KeyShape).border(1.dp, c.keyEdge, KeyShape))
                Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(6.dp).background(c.accent, RoundedCornerShape(3.dp)))
            }
            Row(Modifier.fillMaxSize()) {
                tabs.forEach { tab ->
                    val on = tab == state.tab
                    val ink by animateColorAsState(if (on) c.text else c.muted, glide(), label = "tab-ink")
                    // The chosen tab's icon dips and springs back, like a key answering your finger.
                    val bounce = remember { androidx.compose.animation.core.Animatable(1f) }
                    val reducedTab = LocalReducedMotion.current
                    var settled by remember { mutableStateOf(false) } // not on the first frame
                    LaunchedEffect(on) {
                        if (on && settled && !reducedTab) { bounce.animateTo(0.8f, tween(70)); bounce.animateTo(1f, pop()) }
                        settled = true
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .tourTarget(state.targets, "tab-" + tab.name.lowercase())
                            .semantics { selected = on }
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                                if (tab == state.tab) {
                                    if (tab == Tab.Settings) state.page = SettingsPage.Home
                                    haptics?.tick()
                                    state.reselect++
                                    return@clickable
                                }
                                haptics?.tick()
                                state.tab = tab
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Spacer(Modifier.height(4.dp))
                        Icon(tab.icon, null, Modifier.size(20.dp).graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }, tint = ink)
                        Spacer(Modifier.height(4.dp))
                        Text(tab.label, style = Type.tab.copy(color = ink, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Clip)
                    }
                }
            }
        }
    }
}

/** A short message above the tab bar, like the web app's toast. */
@Composable
private fun ToastHost(state: AppState, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val reduced = LocalReducedMotion.current
    var shown by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    LaunchedEffect(state.toastId) {
        val message = state.toast ?: return@LaunchedEffect
        shown = message
        action = state.toastAction
        delay(if (action != null) 5000 else 2800)
        state.hideToast()
    }
    AnimatedVisibility(
        visible = state.toast != null,
        modifier = modifier.padding(16.dp),
        // Messages spring up from the bottom and settle (a slight overshoot, like iOS banners).
        enter = if (reduced) fadeIn(snap()) else fadeIn(tween(160)) + slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 420f)) { it } +
            androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 420f), initialScale = 0.9f),
        exit = if (reduced) fadeOut(snap()) else fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 } + androidx.compose.animation.scaleOut(tween(200), targetScale = 0.95f),
    ) {
        Row(Modifier.background(c.text, KeyShape).padding(start = 16.dp, end = if (action != null) 6.dp else 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(shown.orEmpty(), style = Type.body.copy(color = c.panel, fontSize = Type.key.fontSize),
                modifier = Modifier.padding(vertical = 6.dp).weight(1f, fill = false))
            action?.let { (label, run) ->
                Spacer(Modifier.width(10.dp))
                Text(label, style = Type.rowTitle.copy(color = if (c.isDark) c.accent else c.accentSoft),
                    modifier = Modifier.click { run(); state.hideToast() }.padding(horizontal = 10.dp, vertical = 8.dp))
            }
        }
    }
}

/** Slides a Settings page in from the right, and back out to the right. */
internal fun pageTransition(reduced: Boolean, deeper: Boolean): androidx.compose.animation.ContentTransform {
    if (reduced) return fadeIn(snap()) togetherWith fadeOut(snap())
    // iOS's push: the new page slides in whole over the old one, which drifts a third of the way
    // left and dims; going back is the same in reverse, the leaving page on top.
    val move = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 1f, stiffness = 420f)
    return if (deeper) (slideInHorizontally(move) { it })
        .togetherWith(slideOutHorizontally(move) { -it * 3 / 10 } + fadeOut(tween(300), targetAlpha = 0.6f))
        .apply { targetContentZIndex = 1f }
    else (slideInHorizontally(move) { -it * 3 / 10 } + fadeIn(tween(300), initialAlpha = 0.6f))
        .togetherWith(slideOutHorizontally(move) { it })
        .apply { targetContentZIndex = -1f }
}

/**
 * The open book, staged like iOS Books: it grows out of the cover you tapped into the full page
 * (the cover fades into the first page as it grows), and shrinks back into that cover when you
 * close it. The back swipe drags the page off to the right with your finger, the library waiting
 * underneath; let go early and it springs back. Without a cover on screen (opened from Notes) it
 * rises from slightly smaller, fading in.
 */
@Composable
private fun BookStage(state: AppState, book: app.quietreader.data.Book?, reduced: Boolean, content: @Composable (app.quietreader.data.Book) -> Unit) {
    val c = LocalColors.current
    val open = remember { androidx.compose.animation.core.Animatable(0f) } // 0: at the cover, 1: the full page
    var shown by remember { mutableStateOf<app.quietreader.data.Book?>(null) }
    var from by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val swipe = rememberBackSwipe(enabled = book != null) { state.openBookId = null }
    val grow = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.92f, stiffness = 260f)
    LaunchedEffect(book?.id) {
        if (book != null) {
            from = state.coverBounds[book.id]
            shown = book
            if (reduced) open.snapTo(1f) else { open.snapTo(0f); open.animateTo(1f, grow) }
        } else if (shown != null) {
            val id = shown!!.id
            if (!swipe.finishedBySwipe && !reduced) {
                from = state.coverBounds[id] // where its cover is now (the library may have moved)
                open.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 380f))
            }
            shown = null
            swipe.reset()
        }
    }
    val b = shown ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val r = from
        val p = open.value
        // While the page is dragged away, the library underneath is dimmed less and less.
        if (swipe.active || p < 1f) {
            val dim = if (swipe.active) 1f - swipe.progress else p
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f * dim)))
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                if (r != null && r.width > 0f) {
                    scaleX = r.width / w + (1f - r.width / w) * p
                    scaleY = r.height / h + (1f - r.height / h) * p
                    translationX = r.left * (1f - p)
                    translationY = r.top * (1f - p)
                } else {
                    val s = 0.92f + 0.08f * p
                    scaleX = s; scaleY = s
                    translationX = w * (1f - s) / 2f; translationY = h * (1f - s) / 2f
                    alpha = p.coerceIn(0f, 1f)
                }
                translationX += swipe.progress * w
                shadowElevation = if (swipe.active) 20.dp.toPx() else 0f
                clip = p < 1f
                shape = RoundedCornerShape(((1f - p) * 24f).coerceAtLeast(0f).dp)
            },
        ) {
            content(b)
            // The cover, fading into the page during the first part of the growth.
            val coverAlpha = if (r == null) 0f else (1f - p * 2.2f).coerceIn(0f, 1f)
            if (coverAlpha > 0f) Cover(b, Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha }, fill = true)
        }
    }
}

/** The Day/Night key: the moon and sun swap with a turn, as if the knob were rotated. */
@Composable
private fun ThemeKey(dark: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val turn = remember { androidx.compose.animation.core.Animatable(0f) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(dark) {
        if (settled && !reduced) { turn.snapTo(-110f); turn.animateTo(0f, pop()) }
        settled = true
    }
    Key(onClick, modifier, padding = androidx.compose.foundation.layout.PaddingValues(0.dp), contentDescription = if (dark) "Switch to Day" else "Switch to Night") {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.Crossfade(dark, animationSpec = tween(if (reduced) 0 else 180), label = "theme-icon") { d ->
                Icon(if (d) Icons.Sun else Icons.Moon, null, Modifier.size(18.dp).graphicsLayer { rotationZ = turn.value; val s = 1f + turn.value / 600f; scaleX = s; scaleY = s })
            }
        }
    }
}

/** The app's state, for screens that only need a little of it (Stats glides to the top). */
val LocalAppState = androidx.compose.runtime.staticCompositionLocalOf<AppState?> { null }
