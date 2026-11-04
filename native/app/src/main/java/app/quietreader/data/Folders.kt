package app.quietreader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Book folders: folders you choose; Quiet adds every book in them (and in folders inside them),
 * on start and when you come back after 10 minutes. Files it has already seen aren't added again,
 * so a book you deleted stays deleted, unless the library is empty (then everything comes back:
 * the web app's F2 lesson).
 */
class Folders(private val context: Context, private val library: Library, private val importer: Importer) {
    private val prefs = context.getSharedPreferences("quiet-folders", Context.MODE_PRIVATE)
    @Volatile var lastScan = 0L

    fun all(): List<Uri> = runCatching { JSONArray(prefs.getString("folders", "[]")) }.getOrDefault(JSONArray())
        .let { a -> (0 until a.length()).map { Uri.parse(a.getString(it)) } }

    private fun saveList(list: List<Uri>) = prefs.edit().putString("folders", JSONArray(list.map { it.toString() }).toString()).apply()
    private fun seen(): MutableSet<String> = prefs.getStringSet("seen", emptySet())!!.toMutableSet()

    fun name(uri: Uri): String = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast(':') ?: "Folder"

    fun add(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        saveList((all() + uri).distinct())
    }

    fun remove(uri: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        saveList(all() - uri)
    }

    data class Scan(val added: List<Book>, val waiting: Int, val missing: Int)

    /** Looks through every folder and adds the books it hasn't seen. [waiting]: files in formats that come in L7. */
    suspend fun scan(): Scan = withContext(Dispatchers.IO) {
        lastScan = System.currentTimeMillis()
        val everything = library.books.value.isEmpty()
        val seen = if (everything) mutableSetOf() else seen()
        val added = mutableListOf<Book>()
        var waiting = 0
        var missing = 0
        for (folder in all()) {
            val root = runCatching { DocumentFile.fromTreeUri(context, folder) }.getOrNull()
            if (root == null || !root.canRead()) { missing++; continue }
            for (file in walk(root, 0)) {
                val name = file.name ?: continue
                val key = file.uri.toString()
                val format = BookFormat.of(name, file.type)
                if (format == BookFormat.Other) { if (isBook(name)) waiting++; continue }
                if (key in seen) continue
                when (val r = importer.import(file.uri)) {
                    is ImportResult.Added -> { added += r.book; seen += key }
                    is ImportResult.AlreadyThere -> seen += key
                    else -> seen += key
                }
            }
        }
        prefs.edit().putStringSet("seen", seen).apply()
        Scan(added, waiting, missing)
    }

    private fun walk(dir: DocumentFile, depth: Int): List<DocumentFile> {
        if (depth > 5) return emptyList()
        return dir.listFiles().flatMap { f -> if (f.isDirectory) walk(f, depth + 1) else listOf(f) }
    }

    private fun isBook(name: String) = name.substringAfterLast('.', "").lowercase() in
        setOf("mobi", "azw", "azw3", "fb2", "fbz", "cbz", "cbr", "cb7", "cbt", "docx", "odt", "rtf", "md", "htmlz", "pdb", "prc")
}
