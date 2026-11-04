package app.quietreader

import android.app.Application
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import app.quietreader.data.Backup
import app.quietreader.data.Fonts
import app.quietreader.data.Importer
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

    /** On start: recover from the safety copy if needed, empty old trash, then follow every change. */
    fun start(): Int {
        val recovered = safety.recover()
        Safety.emptyOldTrash(library)
        library.onChange = { safetyDirty = true }
        return recovered
    }

    @Volatile var safetyDirty = false

    /** Turns a page in the open book (volume keys); null when no book is open. */
    var pageTurner: ((forward: Boolean) -> Unit)? = null
}

val LocalQuiet = staticCompositionLocalOf<Quiet> { error("Quiet isn’t set up") }

class QuietApplication : Application() {
    val quiet by lazy { Quiet(this) }
}
