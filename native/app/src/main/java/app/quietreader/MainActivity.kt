package app.quietreader

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.quietreader.data.Backup
import app.quietreader.data.ImportResult
import app.quietreader.ui.AppState
import app.quietreader.ui.Haptics
import app.quietreader.ui.LocalColors
import app.quietreader.ui.QuietApp
import app.quietreader.ui.QuietTheme
import app.quietreader.ui.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val quiet by lazy { (application as QuietApplication).quiet }
    private val state = AppState()
    private val haptics by lazy { Haptics(this) { quiet.settings.haptics } }

    private val pickBooks = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importBooks(uris, openIfOne = false)
    }
    private val saveBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) writeBackup(uri)
    }
    private val pickBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readBackup(uri)
    }

    private val pickFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val family = withContext(Dispatchers.IO) { runCatching { quiet.fonts.add(uri) }.getOrNull() }
            state.fontsVersion++
            state.toast(if (family != null) "Added the font “$family”. Choose it under Text → Font." else "That isn’t a font file (TTF, OTF, WOFF or WOFF2).")
        }
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { quiet.folders.add(uri); state.foldersVersion++; scanFolders(always = true) }
    }
    private var coverFor: String? = null
    private val pickCover = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = coverFor
        if (uri != null && id != null) lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { quiet.importer.setCover(id, uri) }.getOrDefault(false) }
            state.toast(if (ok) "New cover set." else "That picture couldn’t be used.")
        }
    }

    /** Book folders: look for new books (on start, when back after 10 minutes, or by hand). */
    private fun scanFolders(always: Boolean = false) {
        if (quiet.folders.all().isEmpty()) { if (always) state.toast("Add a book folder first."); return }
        if (!always && System.currentTimeMillis() - quiet.folders.lastScan < 10 * 60_000) return
        lifecycleScope.launch {
            val r = quiet.folders.scan()
            when {
                r.added.isNotEmpty() -> state.toast("Added ${r.added.size} ${if (r.added.size == 1) "book" else "books"} from your folders.")
                always -> state.toast(buildString {
                    append("No new books in your folders.")
                    if (r.waiting > 0) append(" ${r.waiting} in other formats wait for L7.")
                    if (r.missing > 0) append(" ${r.missing} folder can’t be read; add it again.")
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        scanFolders()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // The book view is a fragment made with Readium's factory; Android can't rebuild it on its
        // own after the app was closed in the background, so start clean (the book reopens from the library).
        super.onCreate(null)
        val recovered = quiet.start()
        if (recovered > 0) state.toast("Quiet rebuilt your library from its safety copy: $recovered books.")
        // Any file: book types are many and phones label them loosely; the importer decides.
        state.pickBooks = { pickBooks.launch(arrayOf("*/*")) }
        state.saveBackup = { saveBackup.launch("Quiet backup ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.quietbackup") }
        state.pickBackup = { pickBackup.launch(arrayOf("*/*")) }
        state.pickFont = { pickFont.launch(arrayOf("*/*")) }
        state.pickFolder = { pickFolder.launch(null) }
        state.pickCover = { id -> coverFor = id; pickCover.launch(arrayOf("image/*")) }
        state.scanFolders = { scanFolders(always = true) }
        handle(intent)
        followLibraryForSafetyCopy()
        // First time: the tour. After an update: What's new, once.
        val version = "${BuildConfig.QUIET_VERSION} ${BuildConfig.QUIET_LAYER}"
        if (!quiet.settings.tourDone) state.startTour()
        else if (quiet.settings.seenVersion != version) state.guide = "whatsnew"
        quiet.settings.seenVersion = version

        setContent {
            CompositionLocalProvider(LocalQuiet provides quiet) {
                val settings = quiet.settings
                QuietTheme(settings, haptics) {
                    val dark = LocalColors.current.isDark
                    // Light or dark status-bar icons to match the theme, drawn edge to edge.
                    LaunchedEffect(dark) {
                        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    }
                    LaunchedEffect(settings.rotation) {
                        requestedOrientation = when (settings.rotation) {
                            Rotation.Auto -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            Rotation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                            Rotation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                        }
                    }
                    QuietApp(settings, state)
                }
            }
        }
    }

    // ---------- The reading ruler: hold, then drag ----------
    private val hold = android.os.Handler(android.os.Looper.getMainLooper())
    private var holdAt: android.graphics.PointF? = null
    private var holding = false
    private var lastY = 0f
    private val grab = Runnable {
        val at = holdAt ?: return@Runnable
        holding = true
        lastY = at.y
        quiet.ruler.grabbed = true
        haptics.confirm()
        // The book stops what it was doing with this touch (no scroll, no selection).
        val t = android.os.SystemClock.uptimeMillis()
        val cancel = android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_CANCEL, at.x, at.y, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }
    private fun letGo() { hold.removeCallbacks(grab); holdAt = null }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        quiet.lastTouch = System.currentTimeMillis()
        val grip = quiet.ruler
        if (holding) {
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_MOVE -> { grip.onMove(ev.y - lastY); lastY = ev.y }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    holding = false; grip.grabbed = false; grip.onDrop(); letGo()
                }
            }
            return true
        }
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                letGo()
                val band = grip.band
                if (band != null && ev.y in band) {
                    holdAt = android.graphics.PointF(ev.x, ev.y)
                    hold.postDelayed(grab, 350)
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> holdAt?.let { at ->
                val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
                if (kotlin.math.hypot(ev.x - at.x, ev.y - at.y) > slop) letGo() // a scroll: the book's
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL,
            android.view.MotionEvent.ACTION_POINTER_DOWN -> letGo()
        }
        return super.dispatchTouchEvent(ev)
    }

    /** Volume keys turn pages while a book is open (Settings → Reader). Down = next page. */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val turner = quiet.pageTurner
        val code = event.keyCode
        if (turner != null && quiet.settings.volumeKeys &&
            (code == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || code == android.view.KeyEvent.KEYCODE_VOLUME_UP)) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) turner(code == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStop() {
        super.onStop()
        if (quiet.safetyDirty) lifecycleScope.launch(Dispatchers.IO) { quiet.safetyDirty = false; quiet.safety.save() }
    }

    /** "Open with Quiet" and "Share to Quiet": add the book, then open it. */
    private fun handle(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (uri != null) {
            intent?.action = null // not again on rotation
            importBooks(listOf(uri), openIfOne = true)
        }
    }

    private fun importBooks(uris: List<Uri>, openIfOne: Boolean) = lifecycleScope.launch {
        state.busy = if (uris.size == 1) "Adding the book…" else "Adding ${uris.size} books…"
        val results = uris.map { quiet.importer.import(it) }
        state.busy = null
        state.tab = Tab.Library
        val added = results.filterIsInstance<ImportResult.Added>()
        val there = results.filterIsInstance<ImportResult.AlreadyThere>()
        val unsupported = results.filterIsInstance<ImportResult.NotSupported>()
        val failed = results.filterIsInstance<ImportResult.Failed>()
        state.toast(
            when {
                results.size == 1 && added.size == 1 -> "Added “${added[0].book.title}”."
                results.size == 1 && there.size == 1 -> "“${there[0].book.title}” is already in your library."
                results.size == 1 && unsupported.size == 1 -> "Quiet can’t open ${unsupported[0].format} files. It reads EPUB, MOBI, AZW3, FB2, PDF, Word, ODT, RTF, Markdown, TXT, HTML and comics."
                results.size == 1 -> "Couldn’t add “${failed[0].name}”: ${failed[0].reason}"
                else -> listOfNotNull(
                    added.size.takeIf { it > 0 }?.let { "Added $it ${if (it == 1) "book" else "books"}" },
                    there.size.takeIf { it > 0 }?.let { "$it already there" },
                    (unsupported.size + failed.size).takeIf { it > 0 }?.let { "$it couldn’t be added" },
                ).joinToString(" · ") + "."
            }
        )
        if (openIfOne && results.size == 1) {
            val book = added.firstOrNull()?.book ?: there.firstOrNull()?.book
            if (book != null) state.open(book.id)
        }
    }

    private fun writeBackup(uri: Uri) = lifecycleScope.launch {
        state.busy = "Preparing the backup…"
        val result = withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { out ->
                    quiet.backup.write(out, "${BuildConfig.QUIET_VERSION} ${BuildConfig.QUIET_LAYER} native") { f ->
                        state.busy = "Saving… ${(f * 100).toInt()}%"
                    }
                } ?: error("Couldn’t write there.")
            }
        }
        state.busy = null
        result.fold(
            { n -> quiet.library.onChange(); state.toast("Backed up $n ${if (n == 1) "book" else "books"}. Keep the file somewhere safe.") },
            { e -> state.toast("Backup failed: ${e.message ?: "couldn’t write the file"}") },
        )
    }

    private fun readBackup(uri: Uri) = lifecycleScope.launch {
        state.busy = "Reading the backup…"
        val file = File(cacheDir, "restore.quietbackup")
        val summary = withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: error("Couldn’t read the file.")
                quiet.backup.read(file)
            }
        }
        state.busy = null
        summary.fold(
            { s -> state.confirmRestore = s to { restore(file) } },
            { e -> file.delete(); state.toast((e as? Backup.NotABackup)?.message ?: "Couldn’t read that file.") },
        )
    }

    private fun restore(file: File) = lifecycleScope.launch {
        state.busy = "Restoring…"
        val result = withContext(Dispatchers.IO) { runCatching { quiet.backup.restore(file) { step -> state.busy = step } } }
        file.delete()
        state.busy = null
        result.fold(
            { r ->
                state.toast(buildString {
                    append(if (r.added == 0 && r.updated == 0) "Nothing new: everything in the backup is already here." else "Restored: ${r.added} new ${if (r.added == 1) "book" else "books"}")
                    if (r.updated > 0) append(", ${r.updated} with a newer place")
                    if (r.notes > 0) append(", ${r.notes} notes kept for L4")
                    if (r.settings) append(", and your settings")
                    if (r.added > 0 || r.updated > 0) append(".")
                })
                state.tab = Tab.Library
            },
            { e -> state.toast("Restore failed: ${e.message ?: "the file couldn’t be read"}") },
        )
    }

    /** The safety copy follows the library: a few seconds after a change, and when the app goes to the background. */
    private fun followLibraryForSafetyCopy() = lifecycleScope.launch {
        while (true) {
            delay(4000)
            if (quiet.safetyDirty) {
                quiet.safetyDirty = false
                withContext(Dispatchers.IO) { runCatching { quiet.safety.save() } }
            }
        }
    }
}
