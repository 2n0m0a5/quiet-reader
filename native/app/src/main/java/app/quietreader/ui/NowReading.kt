package app.quietreader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quietreader.LocalQuiet
import app.quietreader.data.Book
import app.quietreader.data.Status
import app.quietreader.data.status
import app.quietreader.data.withStatus
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * The Now reading bar: the book you're in the middle of, floating just above the tabs, one tap
 * from the page (like the mini player in Apple Music, or Books' "Continue"). It answers the
 * question every reader has when they open the app: "where was I?"
 *
 *   Tap ............ open the book (it grows out of the bar's little cover)
 *   Swipe sideways . flip between your recent unfinished books
 *   Swipe up ....... open it too        Swipe down ... tuck the bar away until you next read
 *   Hold ........... Book page · Mark as finished · Tuck away · Bar settings
 *
 * It slides away while you scroll down a list and comes back as you scroll up (Safari's rule:
 * reading the list wins, a flick up brings controls back). Settings → Interface → Now reading:
 * on or off, every tab or Library only, how see-through it is, and the scroll behaviour.
 */

/** Your recent unfinished books, most recent first (the bar's pages). */
fun nowReading(books: List<Book>): List<Book> = books
    .filter { it.deletedAt == null && it.lastOpenedAt != null && it.status() != Status.Finished && it.format != app.quietreader.data.BookFormat.Other }
    .sortedByDescending { it.lastOpenedAt }
    .take(5)

/** Whether the bar is on screen now (lists leave room for it, toasts sit above it). */
@Composable
fun nowBarShown(state: AppState): Boolean {
    val quiet = LocalQuiet.current
    val s = quiet.settings
    val books by quiet.library.books.collectAsState()
    return s.nowBar && nowReading(books).isNotEmpty() && (s.nowBarEverywhere || state.tab == Tab.Library) &&
        !state.nowTucked && !state.selecting && state.openBookId == null && state.tourStep == null
}

/** Space lists leave at the bottom for the bar. */
val NowBarRoom = 78.dp
/** The screen behind the bar, for its frosted glass. */
val LocalGlass = androidx.compose.runtime.staticCompositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }

/** The room the bar takes right now (0 when it's off), for lists to leave at their end. */
val LocalNowRoom = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** Scrolling down a list sends the bar away; scrolling up brings it back. */
fun nowBarScroll(state: AppState, enabled: () -> Boolean) = object : NestedScrollConnection {
    private var travel = 0f
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (!enabled() || source != NestedScrollSource.UserInput) return Offset.Zero
        val dy = consumed.y
        travel = if ((dy < 0) == (travel < 0)) travel + dy else dy
        if (travel < -48f) state.nowScrolledAway = true
        if (travel > 24f) state.nowScrolledAway = false
        return Offset.Zero
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowReadingBar(state: AppState, modifier: Modifier = Modifier) {
    val quiet = LocalQuiet.current
    val settings = quiet.settings
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val books by quiet.library.books.collectAsState()
    val recents = nowReading(books)
    val visible = nowBarShown(state) && !(settings.nowBarAutoHide && state.nowScrolledAway)
    val glideUp = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.86f, stiffness = 420f)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) fadeIn(snap()) else slideInVertically(glideUp) { it * 2 } + fadeIn(),
        exit = if (reduced) fadeOut(snap()) else slideOutVertically(glideUp) { it * 2 } + fadeOut(),
    ) {
        if (recents.isEmpty()) return@AnimatedVisibility
        val pager = rememberPagerState { recents.size }
        LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.drop(1).collect { haptics?.tick() } }
        val book = recents[pager.currentPage.coerceIn(0, recents.lastIndex)]
        val scope = rememberCoroutineScope()
        val lift = remember { Animatable(0f) } // the bar follows a vertical swipe a little
        val opacity = settings.nowBarOpacity / 100f
        val glass = LocalGlass.current
        val shape = RoundedCornerShape(14.dp)
        fun open(b: Book) { haptics?.tick(); state.open(b.id, from = "now") }
        Column(
            Modifier
                .fillMaxWidth()
                .tourTarget(state.targets, "now")
                .graphicsLayer { translationY = lift.value; shadowElevation = 10.dp.toPx() * opacity; this.shape = shape; clip = true }
                // Frosted glass, like iOS: what's behind is blurred, then tinted with the panel's colour.
                // Opacity is how much tint: 100% is a solid key, lower lets the blurred page glow through.
                .then(glass?.let { g ->
                    Modifier.hazeEffect(g, dev.chrisbanes.haze.HazeStyle(
                        backgroundColor = c.bg,
                        tint = dev.chrisbanes.haze.HazeTint(c.panel.copy(alpha = opacity)),
                        blurRadius = 22.dp,
                        noiseFactor = 0.04f,
                    ))
                } ?: Modifier.background(c.panel.copy(alpha = opacity), shape))
                .border(1.dp, c.keyEdge.copy(alpha = 0.6f + 0.4f * opacity), shape)
                .pointerInput(recents.size) {
                    // Up opens the book, down tucks the bar away; short of that it springs back.
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onDragEnd = {
                            val b = recents.getOrNull(pager.currentPage)
                            when {
                                dragged < -60f && b != null -> { scope.launch { lift.snapTo(0f) }; open(b) }
                                dragged > 50f -> {
                                    haptics?.confirm()
                                    state.nowTucked = true
                                    scope.launch { lift.snapTo(0f) }
                                    state.toast("Tucked away until you next read.", "Undo" to { state.nowTucked = false })
                                }
                                else -> scope.launch { lift.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
                            }
                        },
                        onDragCancel = { scope.launch { lift.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f)) } },
                    ) { change, dy ->
                        change.consume()
                        dragged += dy
                        // Resistance: the bar moves less than your finger, more so upward.
                        scope.launch { lift.snapTo(if (dragged < 0) dragged * 0.35f else dragged * 0.6f) }
                    }
                },
        ) {
            Row(Modifier.padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                HorizontalPager(pager, Modifier.weight(1f), pageSpacing = 12.dp, key = { recents[it].id }) { page ->
                    val b = recents[page]
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClick = { open(b) },
                            onLongClick = { haptics?.confirm(); state.nowMenu = b.id },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(b, Modifier.width(34.dp).onGloballyPositioned { if (page == pager.currentPage) state.coverBounds["now"] = it.boundsInWindow() })
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.title, style = Type.rowTitle.copy(color = c.text, fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(subtitle(b, quiet, settings.dailyGoal), style = Type.hint.copy(color = c.muted, fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (recents.size > 1) {
                    // Which of your recent books this is: small dots, the current one orange.
                    Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        recents.indices.forEach { i ->
                            Box(Modifier.size(4.dp).background(if (i == pager.currentPage) c.accent else c.keyEdge, RoundedCornerShape(2.dp)))
                        }
                    }
                }
                IconKey(Icons.BookOpen, "Read ${book.title}", { open(book) }, size = 40.dp)
            }
            // How far through the book: a hairline along the bottom, orange like the needle.
            Box(Modifier.fillMaxWidth().height(2.dp).background(c.line)) {
                Box(Modifier.fillMaxWidth(book.progress.toFloat().coerceIn(0f, 1f)).height(2.dp).background(c.accent))
            }
        }
    }
    state.nowMenu?.let { id -> books.firstOrNull { it.id == id }?.let { NowMenu(state, it) } ?: run { state.nowMenu = null } }
}

private fun subtitle(b: Book, quiet: app.quietreader.Quiet, goal: Int): String {
    val parts = mutableListOf<String>()
    if (b.author.isNotBlank()) parts += b.author
    parts += "${(b.progress * 100).roundToInt()}%"
    if (goal > 0) {
        val today = (app.quietreader.reader.todaySeconds(quiet) / 60).roundToInt()
        parts += if (today >= goal) "goal done today" else "$today of $goal min today"
    }
    return parts.joinToString(" · ")
}

/** Hold the bar: quick things to do with that book, and the bar itself. */
@Composable
private fun NowMenu(state: AppState, book: Book) {
    val quiet = LocalQuiet.current
    val c = LocalColors.current
    val close = { state.nowMenu = null }
    app.quietreader.reader.Sheet(close) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(book.title, style = Type.section.copy(color = c.text), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${(book.progress * 100).roundToInt()}% · last read ${ago(book.lastOpenedAt ?: 0L)}", style = Type.hint.copy(color = c.muted))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextKey("Read", { close(); state.open(book.id, from = "now") }, Modifier.weight(1f), kind = KeyKind.Primary)
                TextKey("Book page", { close(); state.details = book.id }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextKey("Mark as finished", {
                    close()
                    val before = book
                    quiet.library.put(book.withStatus(Status.Finished))
                    state.toast("“${book.title}” is finished.", "Undo" to { quiet.library.put(before) })
                }, Modifier.weight(1f))
                TextKey("Tuck away", { close(); state.nowTucked = true; state.toast("Tucked away until you next read.", "Undo" to { state.nowTucked = false }) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            TextKey("Bar settings", { close(); state.tab = Tab.Settings; state.page = SettingsPage.Interface }, Modifier.fillMaxWidth())
        }
    }
}

private fun ago(at: Long): String {
    val minutes = ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        minutes < 48 * 60 -> "yesterday"
        else -> "${minutes / (24 * 60)} days ago"
    }
}
