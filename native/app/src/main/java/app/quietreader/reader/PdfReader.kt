package app.quietreader.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.quietreader.Flow
import app.quietreader.LocalQuiet
import app.quietreader.data.Book
import app.quietreader.ui.LocalColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * A PDF, drawn by Android's own PDF renderer: one page at a time, as you reach it, kept in a
 * small memory cache. Scroll mode is a column of pages; page mode turns them sideways.
 * Zoom comes with the other format work in L7.
 */
private class PdfPages(file: File) {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val lock = Mutex() // the renderer opens one page at a time
    private val cache = object : LruCache<Int, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }
    val count = renderer.pageCount
    val ratios: List<Float> = (0 until count).map { i -> renderer.openPage(i).use { it.width.toFloat() / it.height } }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)

    suspend fun render(index: Int, width: Int): Bitmap = cache.get(index)?.takeIf { it.width == width } ?: withContext(io) {
        lock.withLock {
            renderer.openPage(index).use { page ->
                val height = (width / ratios[index]).roundToInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(android.graphics.Color.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(index, it)
                }
            }
        }
    }

    fun close() { runCatching { renderer.close(); fd.close() } }
}

@Composable
fun PdfReader(book: Book, state: ReaderState) {
    val quiet = LocalQuiet.current
    var failed by remember(book.id) { mutableStateOf(false) }
    if (failed) return CantOpen("This PDF couldn’t be opened. It may be damaged or password-protected.")
    val pages = rememberOpened(book.id, open = {
        withContext(Dispatchers.IO) { PdfPages(quiet.library.fileOf(book.id)) }
    }, close = { it.close() }, onFail = { failed = true })
    val pdf = pages ?: return Opening()
    PdfView(book, pdf, state)
}

@OptIn(FlowPreview::class)
@Composable
private fun PdfView(book: Book, pdf: PdfPages, state: ReaderState) {
    val quiet = LocalQuiet.current
    val settings = quiet.settings
    val c = LocalColors.current
    val scope = rememberCoroutineScope()
    val startPage = remember { book.locator?.toIntOrNull() ?: (book.progress * pdf.count).toInt().coerceIn(0, pdf.count - 1) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    val pager = rememberPagerState(initialPage = startPage) { pdf.count }
    val scroll = settings.flow == Flow.Scrolled

    LaunchedEffect(pdf) {
        state.contents = (0 until pdf.count).map { ContentsEntry("Page ${it + 1}", 0, it) }
        state.pages = pdf.count
        fun jump(page: Int) = scope.launch {
            val p = page.coerceIn(0, pdf.count - 1)
            if (settings.flow == Flow.Scrolled) list.scrollToItem(p) else pager.scrollToPage(p)
        }
        fun here() = if (settings.flow == Flow.Scrolled) list.firstVisibleItemIndex else pager.currentPage
        state.goTo = { entry -> state.markBack(); jump(entry.key as Int) }
        state.goToPage = { n -> jump(n - 1) }
        state.goToProgress = { p -> jump((p * pdf.count).toInt()) }
        state.turn = { forward ->
            val target = (here() + if (forward) 1 else -1).coerceIn(0, pdf.count - 1)
            scope.launch {
                val none = settings.pageTurn == app.quietreader.PageTurn.None
                if (settings.flow == Flow.Scrolled) {
                    val step = list.layoutInfo.viewportSize.height * 0.88f * (if (forward) 1 else -1)
                    if (none) list.scrollBy(step) else list.animateScrollBy(step)
                }
                else { if (none) pager.scrollToPage(target) else pager.animateScrollToPage(target) }
            }
        }
        state.markBack = { val page = here(); state.backTo = "page ${page + 1}" to { jump(page) } }
        state.jump = { forward ->
            val target = here() + if (forward) 1 else -1
            if (target !in 0 until pdf.count) false else { jump(target); true }
        }
        quiet.library.get(book.id)?.let { quiet.library.put(it.with({ put("lastOpenedAt", System.currentTimeMillis()) })) }
        kotlinx.coroutines.delay(1800); state.barsShown = false
    }

    // Switching mode keeps the page.
    LaunchedEffect(scroll) {
        if (scroll) list.scrollToItem(pager.currentPage) else pager.scrollToPage(list.firstVisibleItemIndex)
    }

    // Your place, shown and saved.
    val current = if (scroll) list.firstVisibleItemIndex else pager.currentPage
    LaunchedEffect(current) {
        state.progress = (current + 1).toDouble() / pdf.count
        state.pageLabel = "${current + 1} / ${pdf.count}"
        state.page = current + 1
        state.chapter = ""
        state.currentContents = current
    }
    LaunchedEffect(Unit) {
        snapshotFlow { if (settings.flow == Flow.Scrolled) list.firstVisibleItemIndex else pager.currentPage }
            .distinctUntilChanged().debounce(600).collect { page ->
                if (page == startPage) return@collect
                quiet.library.get(book.id)?.let { b ->
                    val progress = if (page >= pdf.count - 1) 1.0 else page.toDouble() / pdf.count
                    quiet.library.put(b.with({ put("progress", progress); put("lastOpenedAt", System.currentTimeMillis()) }, locator = page.toString()))
                }
            }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(c.readerBg).pointerInput(scroll) {
            detectTapGestures { offset ->
                val third = size.width / 3
                when {
                    !scroll && offset.x < third -> scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }
                    !scroll && offset.x > size.width - third -> scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pdf.count - 1)) }
                    else -> state.toggleBars()
                }
            }
        },
    ) {
        val width = with(LocalDensity.current) { maxWidth.roundToPx() }
        if (scroll) {
            LazyColumn(state = list, contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pdf.count, key = { it }) { i -> PdfPage(pdf, i, width, Modifier.fillMaxWidth()) }
            }
        } else {
            HorizontalPager(state = pager, beyondViewportPageCount = 1) { i ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PdfPage(pdf, i, width, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PdfPage(pdf: PdfPages, index: Int, width: Int, modifier: Modifier) {
    var failed by remember(index) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, index, width) {
        value = runCatching { pdf.render(index, width) }.onFailure { failed = true }.getOrNull()
    }
    Box(modifier.aspectRatio(pdf.ratios[index]).background(Color.White), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        if (failed) androidx.compose.material3.Text("Page ${index + 1} couldn’t be drawn.", color = Color.Gray)
    }
}
