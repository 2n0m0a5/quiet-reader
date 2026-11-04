package app.quietreader.reader

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import app.quietreader.BookFont
import app.quietreader.Flow
import app.quietreader.LocalQuiet
import app.quietreader.QuietSettings
import app.quietreader.data.Book
import app.quietreader.data.BookFormat
import app.quietreader.data.Library
import app.quietreader.ui.LocalColors
import app.quietreader.ui.QuietColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positions

private const val TAG = "quiet-epub"
private val CRIMSON = FontFamily("Crimson Pro")
private val ARCHIVO = FontFamily("Archivo")

/** EPUB (and TXT, through its EPUB copy), drawn by Readium inside the page area. */
@Composable
fun EpubReader(book: Book, state: ReaderState) {
    val quiet = LocalQuiet.current
    val publication by produceState<Publication?>(null, book.id) {
        val file = if (book.format == BookFormat.Txt) quiet.library.derivedOf(book.id).also { derived ->
            if (!derived.exists()) withContext(Dispatchers.IO) {
                app.quietreader.data.TextBook.toEpub(quiet.library.fileOf(book.id), book.title, derived)
            }
        } else quiet.library.fileOf(book.id)
        value = quiet.readium.open(file)
    }
    DisposableEffect(publication) { onDispose { publication?.close() } }
    val pub = publication
    if (pub == null) Opening() else EpubView(book, pub, state)
}

@OptIn(ExperimentalReadiumApi::class, FlowPreview::class)
@Composable
private fun EpubView(book: Book, publication: Publication, state: ReaderState) {
    val quiet = LocalQuiet.current
    val settings = quiet.settings
    val colors = LocalColors.current
    val activity = LocalContext.current as FragmentActivity
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    // Where to start: the exact place saved here, or (from a web app backup) the reading
    // progress, in the chapter the web app's position points to.
    val start by produceState<Locator?>(null, book.id) {
        val saved = book.locator?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
        value = saved ?: startFromWeb(book, publication) ?: publication.readingOrder.firstOrNull()?.let { publication.locatorFromLink(it) }
    }
    val initial = start ?: return
    val totalPositions by produceState(0, publication) { value = runCatching { publication.positions().size }.getOrDefault(0) }

    // Contents, flattened.
    LaunchedEffect(publication) {
        fun flatten(links: List<Link>, depth: Int): List<ContentsEntry> =
            links.flatMap { listOf(ContentsEntry(it.title ?: "", depth, it)) + flatten(it.children, depth + 1) }
        state.contents = flatten(publication.tableOfContents, 0).filter { it.title.isNotBlank() }
        state.goTo = { entry -> (entry.key as? Link)?.let { navigator?.go(it, animated = false) } }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FragmentContainerView(context).apply {
                id = View.generateViewId()
                // After the view is in the window (posted, so no other fragment transaction is running).
                doOnAttach { post {
                    val fm = activity.supportFragmentManager
                    if (fm.isStateSaved) return@post
                    fm.fragmentFactory = EpubNavigatorFactory(publication).createFragmentFactory(
                        initialLocator = initial,
                        initialPreferences = preferences(settings, colors),
                        configuration = EpubNavigatorFragment.Configuration {
                            servedAssets = listOf("fonts/.*")
                            addFontFamilyDeclaration(CRIMSON) {
                                addFontFace { addSource("fonts/crimson_pro.ttf"); setFontWeight(200..900) }
                                addFontFace { addSource("fonts/crimson_pro_italic.ttf"); setFontStyle(FontStyle.ITALIC); setFontWeight(200..900) }
                            }
                            addFontFamilyDeclaration(ARCHIVO) {
                                addFontFace { addSource("fonts/archivo.ttf"); setFontWeight(100..900) }
                            }
                        },
                    )
                    fm.beginTransaction().replace(id, EpubNavigatorFragment::class.java, null, TAG).commitNow()
                    val nav = fm.findFragmentByTag(TAG) as EpubNavigatorFragment
                    // Taps: the edges turn pages (in page mode), the middle shows or hides the bars.
                    nav.addInputListener(DirectionalNavigationAdapter(nav, animatedTransition = true))
                    nav.addInputListener(object : InputListener {
                        override fun onTap(event: TapEvent): Boolean { state.toggleBars(); return true }
                    })
                    navigator = nav
                } }
            }
        },
    )
    DisposableEffect(Unit) {
        onDispose {
            val fm = activity.supportFragmentManager
            fm.findFragmentByTag(TAG)?.let { fm.beginTransaction().remove(it).commitNowAllowingStateLoss() }
        }
    }

    // Settings and theme follow along live.
    LaunchedEffect(navigator, settings.bookSize, settings.bookFont, settings.flow, colors.readerBg, colors.readerText) {
        navigator?.submitPreferences(preferences(settings, colors))
    }

    // Your place: shown in the bars and saved (a moment after you stop moving).
    LaunchedEffect(navigator) {
        val nav = navigator ?: return@LaunchedEffect
        quiet.library.get(book.id)?.let { quiet.library.put(it.with({ put("lastOpenedAt", System.currentTimeMillis()) })) }
        var first = true
        nav.currentLocator.collect { locator ->
            state.progress = locator.locations.totalProgression ?: state.progress
            state.chapter = chapterTitle(publication, locator, state) ?: ""
            val position = locator.locations.position
            state.pageLabel = if (position != null && totalPositions > 0) "$position / $totalPositions" else ""
            if (!first && state.barsShown && settings.flow == Flow.Scrolled) state.barsShown = false
            first = false
        }
    }
    LaunchedEffect(navigator) {
        val nav = navigator ?: return@LaunchedEffect
        var first = true
        nav.currentLocator.debounce(600).collect { locator ->
            if (first) { first = false; return@collect } // opening isn't moving
            save(quiet.library, book.id, publication, locator)
        }
    }
    // Bars start shown, then step aside for the page.
    LaunchedEffect(navigator) {
        if (navigator != null) { kotlinx.coroutines.delay(1800); state.barsShown = false }
    }
}

@OptIn(ExperimentalReadiumApi::class)
private fun preferences(settings: QuietSettings, colors: QuietColors): EpubPreferences {
    val (bg, ink) = pageColors(colors)
    return EpubPreferences(
        backgroundColor = ReadiumColor(bg.toArgb()),
        textColor = ReadiumColor(ink.toArgb()),
        fontFamily = when (settings.bookFont) {
            BookFont.Original -> null
            BookFont.Serif -> CRIMSON
            BookFont.Sans -> ARCHIVO
        },
        fontSize = settings.bookSize / 100.0,
        publisherStyles = settings.bookFont == BookFont.Original,
        lineHeight = if (settings.bookFont == BookFont.Original) null else 1.6,
        scroll = settings.flow == Flow.Scrolled,
    )
}

private fun chapterTitle(publication: Publication, locator: Locator, state: ReaderState): String? {
    val href = locator.href.removeFragment().toString()
    val entry = state.contents.lastOrNull { (it.key as? Link)?.url()?.removeFragment()?.toString() == href }
    state.currentContents = entry?.key
    return entry?.title ?: locator.title
}

/** The web app's position (a CFI) names the chapter: /6/N is spine item N/2 - 1. */
private suspend fun startFromWeb(book: Book, publication: Publication): Locator? {
    if (book.progress <= 0.0 && book.cfi == null) return null
    val byProgress = publication.locateProgression(book.progress.coerceIn(0.0, 1.0))
    val spine = book.cfi?.let { Regex("""epubcfi\(/6/(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() }?.let { it / 2 - 1 }
    val chapter = spine?.let { publication.readingOrder.getOrNull(it) } ?: return byProgress
    if (byProgress != null && byProgress.href.removeFragment() == chapter.url().removeFragment()) return byProgress
    return publication.locatorFromLink(chapter)
}

/** Saves the place: Readium's exact locator, plus the web app's fields so a backup works there too. */
private fun save(library: Library, id: String, publication: Publication, locator: Locator) {
    val book = library.get(id) ?: return
    val index = publication.readingOrder.indexOfFirst { it.url().removeFragment() == locator.href.removeFragment() }
    val progress = locator.locations.totalProgression ?: book.progress
    library.put(book.with({
        put("progress", progress)
        put("lastOpenedAt", System.currentTimeMillis())
        // The chapter, as a CFI the web app can open (it doesn't know Readium's locators).
        if (index >= 0) put("cfi", "epubcfi(/6/${2 * (index + 1)}!/4)")
        if (progress >= Book.FINISHED && !has("finishedAt")) put("finishedAt", System.currentTimeMillis())
    }, locator = locator.toJSON().toString()))
}
