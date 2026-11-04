package app.quietreader.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The safety copy: after every change, the whole list of records (not the book files, which are
 * already in private storage) is written to safety/library.json. If the database is ever lost or
 * damaged while the book files are still there, the library is rebuilt from it on start.
 * (The web app needed this because Android's web view lost its storage; the native database is
 * much safer, but a second copy is cheap.)
 */
class Safety(private val library: Library, dir: File) {
    private val file = File(dir, "safety/library.json")

    fun save() {
        val books = library.books.value
        // Never overwrite a copy that has books with an empty library, unless it was emptied on purpose.
        if (books.isEmpty() && !emptiedOnPurpose && count() > 0) return
        emptiedOnPurpose = false
        val json = JSONObject().apply {
            put("savedAt", System.currentTimeMillis())
            put("books", JSONArray(books.map { JSONObject(it.json.toString()).put("_locator", it.locator ?: JSONObject.NULL) }))
            for (store in Library.STORES) put(store, JSONArray(library.records(store)))
        }
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(json.toString())
        temp.renameTo(file)
    }

    /** Set before "Delete everything", so the empty library may replace the copy. */
    var emptiedOnPurpose = false

    fun count(): Int = runCatching { JSONObject(file.readText()).getJSONArray("books").length() }.getOrDefault(0)

    /** Rebuilds the library from the copy if the database is empty but the copy isn't. Returns books recovered. */
    fun recover(): Int {
        if (library.books.value.isNotEmpty() || !file.exists()) return 0
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return 0
        val books = json.optJSONArray("books") ?: return 0
        val list = (0 until books.length()).mapNotNull { i ->
            val record = books.getJSONObject(i)
            val locator = record.optString("_locator").takeIf { it.isNotBlank() && it != "null" }
            record.remove("_locator")
            if (library.fileOf(record.optString("id")).exists()) Book(record, locator) else null
        }
        for (store in Library.STORES) json.optJSONArray(store)?.let { arr ->
            library.putRecords(store, (0 until arr.length()).map { arr.getJSONObject(it) })
        }
        library.putAll(list)
        return list.size
    }

    companion object {
        const val TRASH_DAYS = 30
        /** Books in Recently deleted for more than 30 days are deleted for good. */
        fun emptyOldTrash(library: Library, now: Long = System.currentTimeMillis()) {
            val old = library.books.value.filter { b -> b.deletedAt?.let { now - it > TRASH_DAYS * 86_400_000L } == true }
            if (old.isNotEmpty()) library.deleteForever(old.map { it.id })
        }
    }
}
