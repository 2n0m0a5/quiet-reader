package app.quietreader

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.quietreader.data.Backup
import app.quietreader.data.Fonts
import app.quietreader.data.Importer
import app.quietreader.data.Notes
import app.quietreader.data.ReadingLog
import app.quietreader.data.Collections
import app.quietreader.data.Folders
import app.quietreader.data.Safety
import java.io.File
import app.quietreader.data.Library
import app.quietreader.data.Readium

/** Everything the screens share: settings, the library, and the parts that open books. */
class Quiet(context: Context, dbName: String? = "library.db") {
    val app: Context = context.applicationContext
    val settings = QuietSettings(app)
    val library = Library(app, dbName)
    val readium = Readium(app)
    val importer = Importer(app, library, readium)
    val backup = Backup(library, settings, File(app.filesDir, "fonts"))
    val safety = Safety(library, app.filesDir)
    val fonts = Fonts(app, library)
    val notes = Notes(library)
    val collections = Collections(library)
    val readingLog = ReadingLog(library)
    /** When the screen was last touched (the reading tracker counts only active reading). */
    @Volatile var lastTouch = 0L
    val folders = Folders(app, library, importer)

    /** On start: recover from the safety copy if needed, empty old trash, then follow every change. */
    fun start(): Int {
        val recovered = safety.recover()
        Safety.emptyOldTrash(library)
        library.onChange = { safetyDirty = true }
        return recovered
    }

    @Volatile var safetyDirty = false

    /** The reading ruler's grip: where it is on screen, and what moving it does (MainActivity holds it). */
    val ruler = RulerGrip()

    /** Turns a page in the open book (volume keys); null when no book is open. */
    var pageTurner: ((forward: Boolean) -> Unit)? = null
}

val LocalQuiet = staticCompositionLocalOf<Quiet> { error("Quiet isn’t set up") }

class QuietApplication : Application() {
    val quiet by lazy { Quiet(this) }
    override fun onCreate() {
        super.onCreate()
        ErrorLog.install(this)
    }
}

class RulerGrip {
    /** The ruler's band in window pixels (with some margin); null when the ruler is off. */
    @Volatile var band: ClosedFloatingPointRange<Float>? = null
    var onMove: (dy: Float) -> Unit = {}
    var onDrop: () -> Unit = {}
    var grabbed by androidx.compose.runtime.mutableStateOf(false)
}
