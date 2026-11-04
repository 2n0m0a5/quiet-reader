package app.quietreader.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

/**
 * A book in the library. [json] is the record in the web app's format (src/library/db.ts,
 * BookRecord, without the cover), so a backup carries it across both ways and fields a later
 * layer uses (tags, series, ratings…) are kept even before the native app shows them.
 * [locator] is Readium's exact place in the book (JSON); the web app's `cfi` is kept alongside.
 */
data class Book(val json: JSONObject, val locator: String?) {
    val id: String get() = json.getString("id")
    val title: String get() = json.optString("title").ifBlank { fileName.substringBeforeLast('.') }
    val author: String get() = json.optString("author")
    val fileName: String get() = json.optString("fileName")
    val format: BookFormat get() = BookFormat.of(fileName, json.optString("fileType"))
    val progress: Double get() = json.optDouble("progress", 0.0).takeIf { !it.isNaN() } ?: 0.0
    val addedAt: Long get() = json.optLong("addedAt")
    val lastOpenedAt: Long? get() = json.optLong("lastOpenedAt", 0L).takeIf { it > 0 }
    val deletedAt: Long? get() = json.optLong("deletedAt", 0L).takeIf { it > 0 }
    val cfi: String? get() = json.optString("cfi").takeIf { it.isNotBlank() && it != "null" }
    val finished: Boolean get() = when {
        json.has("finished") && !json.isNull("finished") -> json.optBoolean("finished")
        else -> progress >= FINISHED
    }

    fun with(change: JSONObject.() -> Unit, locator: String? = this.locator) =
        Book(JSONObject(json.toString()).apply(change), locator)

    companion object {
        const val FINISHED = 0.995
    }
}

enum class BookFormat(val label: String, val mime: String) {
    Epub("EPUB", "application/epub+zip"),
    Pdf("PDF", "application/pdf"),
    Txt("TXT", "text/plain"),
    Other("", "application/octet-stream");

    companion object {
        fun of(fileName: String, mime: String?): BookFormat {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when {
                ext == "epub" || mime == Epub.mime -> Epub
                ext == "pdf" || mime == Pdf.mime -> Pdf
                ext == "txt" || mime == Txt.mime -> Txt
                else -> Other
            }
        }
    }
}

/**
 * The library on this phone: an SQLite database plus the book files and covers in the app's
 * private storage. Other kinds of records (collections, highlights, reading time, fonts) are
 * stored as they come from a backup until their layer uses them, so nothing is lost.
 */
class Library(private val context: Context, dbName: String? = "library.db") {
    private val helper = object : SQLiteOpenHelper(context, dbName, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE books (id TEXT PRIMARY KEY, json TEXT NOT NULL, locator TEXT)")
            db.execSQL("CREATE TABLE records (store TEXT NOT NULL, id TEXT NOT NULL, json TEXT NOT NULL, PRIMARY KEY (store, id))")
        }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}
    }

    val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    val derivedDir = File(context.filesDir, "derived").apply { mkdirs() } // TXT turned into EPUB

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    /** Every book, newest reading first. Includes Recently deleted ones; see [shelf]. */
    val books: StateFlow<List<Book>> = _books

    /** Called after every change (the safety copy follows it, L2). */
    var onChange: () -> Unit = {}

    init { reload() }

    fun reload() {
        val list = mutableListOf<Book>()
        helper.readableDatabase.rawQuery("SELECT json, locator FROM books", null).use { c ->
            while (c.moveToNext()) runCatching { list += Book(JSONObject(c.getString(0)), c.getString(1)) }
        }
        _books.value = list.sortedWith(compareByDescending<Book> { it.lastOpenedAt ?: 0L }.thenByDescending { it.addedAt })
    }

    fun get(id: String): Book? = _books.value.firstOrNull { it.id == id }

    fun put(book: Book) = putAll(listOf(book))

    fun putAll(books: List<Book>) {
        if (books.isEmpty()) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (b in books) db.insertWithOnConflict("books", null, ContentValues().apply {
                put("id", b.id); put("json", b.json.toString()); put("locator", b.locator)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        reload()
        onChange()
    }

    /** Deletes books for good: the record, the file and the cover. */
    fun deleteForever(ids: Collection<String>) {
        val db = helper.writableDatabase
        for (id in ids) {
            db.delete("books", "id = ?", arrayOf(id))
            fileOf(id).delete(); coverOf(id).delete(); derivedOf(id).delete()
        }
        reload()
        onChange()
    }

    fun fileOf(id: String) = File(booksDir, safe(id))
    fun coverOf(id: String) = File(coversDir, safe(id))
    fun derivedOf(id: String) = File(derivedDir, safe(id) + ".epub")

    // ---------- Other records, kept as they come (web format) ----------

    fun records(store: String): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        helper.readableDatabase.rawQuery("SELECT json FROM records WHERE store = ?", arrayOf(store)).use { c ->
            while (c.moveToNext()) runCatching { list += JSONObject(c.getString(0)) }
        }
        return list
    }

    fun putRecords(store: String, list: List<JSONObject>) {
        if (list.isEmpty()) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (r in list) db.insertWithOnConflict("records", null, ContentValues().apply {
                put("store", store); put("id", r.getString("id")); put("json", r.toString())
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        onChange()
    }

    fun clearAll() {
        helper.writableDatabase.apply { delete("books", null, null); delete("records", null, null) }
        listOf(booksDir, coversDir, derivedDir).forEach { dir -> dir.listFiles()?.forEach { it.delete() } }
        reload()
        onChange()
    }

    companion object {
        /** The same file-name rule as the web app's backups. */
        fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val STORES = listOf("collections", "annotations", "reading", "fonts")
    }
}
