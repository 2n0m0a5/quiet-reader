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
import androidx.compose.ui.graphics.vector.ImageVector
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

enum class SettingsPage { Home, Interface, Files, Trash, About }

/** Where the reader is in the app, plus the message at the bottom. */
class AppState(tab: Tab = Tab.Library, page: SettingsPage = SettingsPage.Home) {
    var tab by mutableStateOf(tab)
    var page by mutableStateOf(page)
    var toast by mutableStateOf<String?>(null)
        private set
    var toastId by mutableIntStateOf(0)
        private set

    fun toast(message: String) {
        toast = message
        toastId++
    }

    fun hideToast() { toast = null }

    /** The book being read, if any. */
    var openBookId by mutableStateOf<String?>(null)
    fun open(id: String) { openBookId = id }

    // Things only the activity can do (system pickers), set by MainActivity.
    var pickBooks: () -> Unit = {}
    var saveBackup: () -> Unit = {}
    var pickBackup: () -> Unit = {}
    var busy by mutableStateOf<String?>(null)
    /** A backup that was read and waits for "Restore" (its summary, and what restoring does). */
    var confirmRestore by mutableStateOf<Pair<app.quietreader.data.Backup.Summary, () -> Unit>?>(null)
}

/** The colours for the chosen theme; System follows the phone's dark mode. */
@Composable
fun resolveColors(theme: ThemeMode): QuietColors = when (theme) {
    ThemeMode.Day -> DayColors
    ThemeMode.Night -> NightColors
    ThemeMode.Black -> BlackColors
    ThemeMode.System -> if (isSystemInDarkTheme()) NightColors else DayColors
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
    val target = resolveColors(settings.theme)
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
    BackHandler(enabled = state.tab == Tab.Settings && state.page != SettingsPage.Home) {
        state.page = if (state.page == SettingsPage.Trash) SettingsPage.Files else SettingsPage.Home
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
                    transitionSpec = {
                        if (reduced) fadeIn(snap()) togetherWith fadeOut(snap())
                        else fadeIn(tween(180, delayMillis = 40)) togetherWith fadeOut(tween(120))
                    },
                    label = "tab",
                ) { tab ->
                    when (tab) {
                        Tab.Library -> LibraryScreen(state)
                        Tab.Notes -> NotesScreen()
                        Tab.Stats -> StatsScreen()
                        Tab.Settings -> SettingsScreen(state, settings)
                    }
                }
            }
            TabBar(state)
        }
        // A book opens over everything, and closes back to where you were.
        val quiet = LocalQuiet.current
        val book = remember(state.openBookId) { state.openBookId?.let { quiet.library.get(it) } }
        AnimatedVisibility(
            visible = book != null,
            enter = if (reduced) fadeIn(snap()) else fadeIn(tween(220)) + androidx.compose.animation.scaleIn(tween(260), initialScale = 0.96f),
            exit = if (reduced) fadeOut(snap()) else fadeOut(tween(180)) + androidx.compose.animation.scaleOut(tween(200), targetScale = 0.98f),
        ) {
            var shown by remember { mutableStateOf(book) }
            if (book != null) shown = book
            shown?.let { b ->
                if (b.format == app.quietreader.data.BookFormat.Other) {
                    LaunchedEffect(b.id) {
                        state.toast("${b.fileName.substringAfterLast('.').uppercase()} books open in a later layer (L7). It’s safe in your library.")
                        state.openBookId = null
                    }
                } else app.quietreader.reader.ReaderScreen(b) { state.openBookId = null }
            }
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
            IconKey(Icons.Plus, "Add books", { state.pickBooks() }, kind = KeyKind.Primary,
                size = 38.dp)
            Spacer(Modifier.width(8.dp))
        }
        val dark = c.isDark
        IconKey(if (dark) Icons.Sun else Icons.Moon, if (dark) "Switch to Day" else "Switch to Night", {
            settings.theme = if (dark) ThemeMode.Day else ThemeMode.Night
        }, size = 38.dp)
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
                    Column(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .semantics { selected = on }
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                                if (tab == state.tab) {
                                    if (tab == Tab.Settings) state.page = SettingsPage.Home
                                    return@clickable
                                }
                                haptics?.tick()
                                state.tab = tab
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Spacer(Modifier.height(4.dp))
                        Icon(tab.icon, null, Modifier.size(20.dp), tint = ink)
                        Spacer(Modifier.height(4.dp))
                        Text(tab.label, style = Type.tab.copy(color = ink, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal))
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
    LaunchedEffect(state.toastId) {
        val message = state.toast ?: return@LaunchedEffect
        shown = message
        delay(2800)
        state.hideToast()
    }
    AnimatedVisibility(
        visible = state.toast != null,
        modifier = modifier.padding(16.dp),
        enter = if (reduced) fadeIn(snap()) else fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 3 },
        exit = if (reduced) fadeOut(snap()) else fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 3 },
    ) {
        Text(
            shown.orEmpty(),
            style = Type.body.copy(color = c.panel, fontSize = Type.key.fontSize),
            modifier = Modifier.background(c.text, KeyShape).padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** Slides a Settings page in from the right, and back out to the right. */
internal fun pageTransition(reduced: Boolean, deeper: Boolean) =
    if (reduced) fadeIn(snap()) togetherWith fadeOut(snap())
    else if (deeper) (slideInHorizontally(androidx.compose.animation.core.spring(stiffness = Springs.MOVE)) { it } + fadeIn(tween(120)))
        .togetherWith(slideOutHorizontally(androidx.compose.animation.core.spring(stiffness = Springs.MOVE)) { -it / 4 } + fadeOut(tween(160)))
    else (slideInHorizontally(androidx.compose.animation.core.spring(stiffness = Springs.MOVE)) { -it / 4 } + fadeIn(tween(160)))
        .togetherWith(slideOutHorizontally(androidx.compose.animation.core.spring(stiffness = Springs.MOVE)) { it } + fadeOut(tween(120)))
