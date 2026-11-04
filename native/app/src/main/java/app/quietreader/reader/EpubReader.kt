package app.quietreader.reader

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
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
import app.quietreader.PageTurn
import androidx.compose.ui.graphics.asImageBitmap
import org.readium.r2.navigator.HyperlinkNavigator
import org.readium.r2.shared.util.AbsoluteUrl
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
import kotlinx.coroutines.launch
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
import org.readium.r2.shared.publication.services.search.search

private const val TAG = "quiet-epub"
private val CRIMSON = FontFamily("Crimson Pro")
private val ARCHIVO = FontFamily("Archivo")

/** EPUB (and TXT, through its EPUB copy), drawn by Readium inside the page area. */
@Composable
fun EpubReader(book: Book, state: ReaderState) {
    val quiet = LocalQuiet.current
    var failed by remember(book.id) { mutableStateOf(false) }
    if (failed) return CantOpen("This book couldn’t be opened. The file may be damaged or protected (DRM).")
    val publication = rememberOpened(book.id, open = {
        // Other kinds were turned into an EPUB when added (made again here if it's missing,
        // for example after restoring a backup from 0.4.2, which keeps only the original).
        val file = if (book.format.converted) quiet.library.derivedOf(book.id).also { derived ->
            if (!derived.exists()) withContext(Dispatchers.IO) {
                app.quietreader.data.convert.Converters.toEpub(quiet.library.fileOf(book.id), book.fileName, derived)
            }
        } else quiet.library.fileOf(book.id)
        quiet.readium.open(file)
    }, close = { it.close() }, onFail = { failed = true })
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
    val haptics = app.quietreader.ui.LocalHaptics.current
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    // Where to start: the exact place saved here, or (from a web app backup) the reading
    // progress, in the chapter the web app's position points to.
    val start by produceState<Locator?>(null, book.id) {
        val saved = book.locator?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
        value = saved ?: startFromWeb(book, publication) ?: publication.readingOrder.firstOrNull()?.let { publication.locatorFromLink(it) }
    }
    val initial = start ?: return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val positions by produceState<List<Locator>>(emptyList(), publication) { value = runCatching { publication.positions() }.getOrDefault(emptyList()) }
    val totalPositions = positions.size

    // Contents, flattened.
    LaunchedEffect(publication) {
        fun flatten(links: List<Link>, depth: Int): List<ContentsEntry> =
            links.flatMap { listOf(ContentsEntry(it.title ?: "", depth, it)) + flatten(it.children, depth + 1) }
        state.contents = flatten(publication.tableOfContents, 0).filter { it.title.isNotBlank() }
        state.goTo = { entry -> state.markBack(); (entry.key as? Link)?.let { navigator?.go(it, animated = false) } }
    }
    // The chapters as stations on the tuning scale; jumps by page or percentage; page turns.
    LaunchedEffect(positions, navigator) {
        val nav = navigator ?: return@LaunchedEffect
        state.pages = positions.size
        state.stations = publication.tableOfContents.mapNotNull { link ->
            val href = link.url().removeFragment()
            positions.firstOrNull { it.href.removeFragment() == href }?.locations?.totalProgression
        }.filter { it > 0.0 }.distinct()
        state.goToPage = { n -> positions.getOrNull(n - 1)?.let { nav.go(it) } }
        state.goToProgress = { p -> scope.launch { publication.locateProgression(p)?.let { nav.go(it) } } }
        state.turn = { forward ->
            val animated = settings.pageTurn != PageTurn.None
            if (settings.flow == Flow.Scrolled) scope.launch {
                // Scroll mode: one screen at a time; at the chapter's edge, on to the next one.
                val r = nav.evaluateJavascript(scrollStep(forward, animated))
                if (r?.contains("edge") == true) chapterStep(nav, publication, forward)
            } else if (forward) nav.goForward(animated) else nav.goBackward(animated)
        }
        state.jump = { forward -> if (forward) nav.goForward(false) else nav.goBackward(false) }
        state.markBack = {
            val here = nav.currentLocator.value
            val label = here.locations.position?.let { "page $it" } ?: "where you were"
            state.backTo = label to { nav.go(here) }
        }
    }

    // ---------- L4: highlights, notes, quotes, bookmarks, search ----------
    val notesVersion by quiet.library.recordsVersion.collectAsState()
    fun chapterCfi(locator: Locator): String {
        val i = publication.readingOrder.indexOfFirst { it.url().removeFragment() == locator.href.removeFragment() }
        return if (i >= 0) "epubcfi(/6/${2 * (i + 1)}!/4)" else ""
    }
    fun onSelected(action: Int, locator: Locator) {
        val text = locator.text.highlight?.trim().orEmpty()
        if (text.isBlank()) return
        if (action == 104) { state.translating = text; return }
        val note = app.quietreader.data.Note.new(book.id, "highlight", text, chapterCfi(locator)).with {
            put("color", settings.lastColor)
            put("style", if (action == 103) "plain" else "highlight")
            if (action == 103) put("quote", true)
            put("label", state.chapter)
            put("progress", locator.locations.totalProgression ?: state.progress)
            put("locator", locator.toJSON().toString())
        }
        quiet.notes.put(note)
        haptics?.confirm()
        if (action != 101) state.editing = note to (action == 102)
    }
    LaunchedEffect(navigator) {
        val nav = navigator ?: return@LaunchedEffect
        state.annotatable = true
        nav.addDecorationListener("highlights", object : org.readium.r2.navigator.DecorableNavigator.Listener {
            override fun onDecorationActivated(event: org.readium.r2.navigator.DecorableNavigator.OnActivatedEvent): Boolean {
                quiet.notes.all().firstOrNull { it.id == event.decoration.id }?.let { state.editing = it to false }
                return true
            }
        })
        state.goToNote = { note ->
            locatorOf(note, publication)?.let { l ->
                // From 0.4.2 notes the chapter is known; Readium finds the text in it.
                if (note.locator == null) scope.launch { publication.locateProgression(note.progress)?.let { nav.go(it) } ?: nav.go(l) } else nav.go(l)
            }
        }
        state.toggleBookmark = {
            val here = nav.currentLocator.value
            val marks = quiet.notes.forBook(book.id).filter { it.isBookmark && sameSpot(it, here) }
            if (marks.isNotEmpty()) marks.forEach { quiet.notes.delete(it.id) }
            else quiet.notes.put(app.quietreader.data.Note.new(book.id, "bookmark", here.text.highlight?.take(200) ?: "", chapterCfi(here)).with {
                put("label", listOfNotNull(state.chapter.ifBlank { null }, here.locations.position?.let { "page $it" }).joinToString(" · "))
                put("progress", here.locations.totalProgression ?: state.progress)
                put("locator", here.toJSON().toString())
            })
            haptics?.tick()
        }
        state.search = { query ->
            val hits = mutableListOf<SearchHit>()
            runCatching {
                val iterator = publication.search(query) ?: return@runCatching
                while (hits.size < 300) {
                    val page = iterator.next().getOrNull() ?: break
                    page.locators.forEach { l ->
                        hits += SearchHit(l.text.before.orEmpty(), l.text.highlight.orEmpty(), l.text.after.orEmpty(), l.title ?: "", l)
                    }
                }
                iterator.close()
            }
            hits
        }
        state.showHit = { hit ->
            val l = hit.key as Locator
            nav.go(l)
            scope.launch {
                nav.applyDecorations(listOf(org.readium.r2.navigator.Decoration("search", l,
                    org.readium.r2.navigator.Decoration.Style.Underline(colors.accent.toArgb()))), "search")
            }
        }
        state.pendingNote?.let { state.pendingNote = null; kotlinx.coroutines.delay(400); state.markBack(); state.goToNote(it) }
    }
    // Highlights drawn on the page, kept in step with your notes.
    LaunchedEffect(navigator, notesVersion) {
        val nav = navigator ?: return@LaunchedEffect
        nav.applyDecorations(quiet.notes.forBook(book.id).mapNotNull { decorationOf(it, publication) }, "highlights")
        state.bookmarkHere = quiet.notes.forBook(book.id).any { it.isBookmark && sameSpot(it, nav.currentLocator.value) }
    }

    // Pictures, bionic reading and your font, in each page (PageScripts.kt).
    suspend fun runPageScript() {
        navigator?.evaluateJavascript(PageScripts.script(settings.bionic, settings.customFont, settings.flow == Flow.Scrolled))
    }
    LaunchedEffect(navigator, settings.bionic, settings.customFont, settings.flow) { runPageScript() }
    LaunchedEffect(navigator) {
        var href: Any? = null
        navigator?.currentLocator?.collect { l -> if (l.href != href) { href = l.href; kotlinx.coroutines.delay(300); runPageScript() } }
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
                        listener = object : EpubNavigatorFragment.Listener {
                            // Footnotes open in a sheet; other links jump (with "Back to page N").
                            override fun shouldFollowInternalLink(link: Link, context: HyperlinkNavigator.LinkContext?): Boolean {
                                if (context is HyperlinkNavigator.FootnoteContext) { state.footnote = context.noteContent; return false }
                                state.markBack()
                                return true
                            }
                            override fun onExternalLinkActivated(url: AbsoluteUrl) {
                                runCatching { activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url.toString()))) }
                            }
                        },
                        paginationListener = object : EpubNavigatorFragment.PaginationListener {
                            override fun onPageLoaded() { scope.launch { runPageScript() } }
                        },
                        configuration = EpubNavigatorFragment.Configuration {
                            registerJavascriptInterface(PageScripts.BRIDGE) {
                                PageBridge(quiet.fonts,
                                    onImage = { url -> scope.launch { state.image = loadImage(publication, url) } },
                                    onChapter = { forward -> scope.launch { navigator?.let { chapterStep(it, publication, forward) } } })
                            }
                            servedAssets = listOf("fonts/.*")
                            decorationTemplates = quietTemplates()
                            // Select text: Highlight · Note · Quote · Translate, then the phone's own (Copy…).
                            selectionActionModeCallback = object : android.view.ActionMode.Callback {
                                override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                                    menu.add(0, 101, 0, "Highlight"); menu.add(0, 102, 1, "Note")
                                    menu.add(0, 103, 2, "Quote"); menu.add(0, 104, 3, "Translate")
                                    return true
                                }
                                override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu) = false
                                override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
                                    if (item.itemId !in 101..104) return false
                                    scope.launch {
                                        val nav = navigator ?: return@launch
                                        val selection = nav.currentSelection()
                                        mode.finish(); nav.clearSelection()
                                        if (selection != null) onSelected(item.itemId, selection.locator)
                                    }
                                    return true
                                }
                                override fun onDestroyActionMode(mode: android.view.ActionMode) {}
                            }
                            // Scroll mode reads by scrolling: a sideways swipe doesn't change chapters.
                            disablePageTurnsWhileScrolling = true
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
                    nav.addInputListener(DirectionalNavigationAdapter(nav, animatedTransition = settings.pageTurn != PageTurn.None))
                    nav.addInputListener(object : InputListener {
                        override fun onTap(event: TapEvent): Boolean { state.toggleBars(); return true }
                    })
                    navigator = nav
                    state.pageView = this
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
    LaunchedEffect(
        navigator, settings.bookSize, settings.bookFont, settings.customFont, settings.flow, colors.readerBg, colors.readerText,
        settings.lineHeight, settings.margins, settings.align, settings.hyphenate, settings.fontWeight, settings.paragraphSpacing, settings.indent,
    ) {
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
            state.page = position ?: 0
            state.bookmarkHere = quiet.notes.forBook(book.id).any { it.isBookmark && sameSpot(it, locator) }
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
    // Readium applies line spacing, alignment, hyphenation and paragraph settings only when the
    // publisher's styles are off: keep the book's own look until one of them is changed.
    val ownLayout = settings.customFont == null && settings.bookFont == BookFont.Original && settings.lineHeight == 17 && settings.align == app.quietreader.Align.Start &&
        !settings.hyphenate && settings.paragraphSpacing < 0 && settings.indent < 0
    return EpubPreferences(
        backgroundColor = ReadiumColor(bg.toArgb()),
        textColor = ReadiumColor(ink.toArgb()),
        fontFamily = settings.customFont?.let { FontFamily(it) } ?: when (settings.bookFont) {
            BookFont.Original -> null
            BookFont.Serif -> CRIMSON
            BookFont.Sans -> ARCHIVO
        },
        fontSize = settings.bookSize / 100.0,
        fontWeight = settings.fontWeight / 400.0,
        publisherStyles = ownLayout,
        lineHeight = if (ownLayout) null else settings.lineHeight / 10.0,
        pageMargins = settings.margins.readium,
        textAlign = if (ownLayout) null else if (settings.align == app.quietreader.Align.Justify) org.readium.r2.navigator.preferences.TextAlign.JUSTIFY else org.readium.r2.navigator.preferences.TextAlign.START,
        hyphens = if (ownLayout) null else settings.hyphenate,
        paragraphSpacing = if (ownLayout || settings.paragraphSpacing < 0) null else settings.paragraphSpacing / 10.0,
        paragraphIndent = if (ownLayout || settings.indent < 0) null else settings.indent / 10.0,
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

/** A picture from the book, by the address the page gave (https://readium/publication/…). */
private suspend fun loadImage(publication: Publication, url: String): androidx.compose.ui.graphics.ImageBitmap? =
    kotlinx.coroutines.withContext(Dispatchers.IO) {
        val href = url.substringAfter("https://readium/publication/", url).substringBefore('#')
        val resource = org.readium.r2.shared.util.Url(href)?.let { publication.get(it) } ?: return@withContext null
        val bytes = resource.read().getOrNull() ?: return@withContext null
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }

/** Scroll mode: the next chapter from its start, or the previous one from its end. */
private fun chapterStep(nav: EpubNavigatorFragment, publication: Publication, forward: Boolean) {
    val here = nav.currentLocator.value.href.removeFragment()
    val i = publication.readingOrder.indexOfFirst { it.url().removeFragment() == here }
    val link = publication.readingOrder.getOrNull(i + if (forward) 1 else -1) ?: return
    val start = publication.locatorFromLink(link) ?: return
    nav.go(if (forward) start else start.copyWithLocations(progression = 0.999), animated = false)
}

/** A bookmark is "here" when it's on the same page (Readium position), or very close for 0.4.2's. */
private fun sameSpot(note: app.quietreader.data.Note, here: Locator): Boolean {
    val at = note.locator?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
    val p = here.locations.position
    if (at != null && p != null && at.locations.position != null) return at.locations.position == p
    val t = here.locations.totalProgression ?: return false
    return kotlin.math.abs(note.progress - t) < 0.0015
}
