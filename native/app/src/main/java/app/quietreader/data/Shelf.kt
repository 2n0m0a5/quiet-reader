package app.quietreader.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A book's status, decided like the web app's statusOf (src/library/db.ts). */
enum class Status(val label: String) { Want("Want to read"), New("New"), Reading("Reading"), Finished("Finished") }

fun Book.status(): Status = when {
    json.has("finished") && !json.isNull("finished") && json.optBoolean("finished") -> Status.Finished
    !(json.has("finished") && !json.isNull("finished") && !json.optBoolean("finished")) && progress >= Book.FINISHED -> Status.Finished
    json.optBoolean("wantToRead") -> Status.Want
    lastOpenedAt != null -> Status.Reading
    else -> Status.New
}

/** Sets a status by hand (Finished stays finished whatever the progress; Want is a wish). */
fun Book.withStatus(status: Status): Book = (if (status == Status.New) Book(json, null) else this).with({
    when (status) {
        Status.Finished -> { put("finished", true); put("wantToRead", false); if (!has("finishedAt") || isNull("finishedAt")) put("finishedAt", System.currentTimeMillis()) }
        Status.Want -> { put("finished", JSONObject.NULL); put("wantToRead", true) }
        Status.Reading -> { put("finished", false); put("wantToRead", false); if (lastOpenedAt == null) put("lastOpenedAt", System.currentTimeMillis()) }
        // New means never opened: the place is cleared and the book starts from the beginning
        // (asked first in the app). Highlights and notes stay.
        Status.New -> {
            put("finished", false); put("wantToRead", false); put("lastOpenedAt", JSONObject.NULL)
            put("progress", 0); put("cfi", JSONObject.NULL); put("finishedAt", JSONObject.NULL)
        }
    }
})

fun Book.strings(key: String): List<String> = json.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
val Book.tags get() = strings("tags")
val Book.collectionIds get() = strings("collections")
val Book.series: String get() = json.optString("series").trim()
val Book.seriesIndex: Double? get() = json.optDouble("seriesIndex", Double.NaN).takeIf { !it.isNaN() }
val Book.rating: Int get() = json.optInt("rating", 0)
val Book.size: Long get() = json.optLong("size")

enum class Sort(val key: String, val label: String) {
    Recent("recent", "Recently read"), Added("added", "Recently added"), Title("title", "Title"), Author("author", "Author"),
    Series("series", "Series"), Rating("rating", "Rating"), Size("size", "Size"), Progress("progress", "Progress");
}

enum class Group(val key: String, val label: String) { None("none", "None"), Author("author", "Author"), Series("series", "Series"), Status("status", "Status"), Collection("collection", "Collection") }

object Shelf {
    private fun titleKey(b: Book) = b.title.lowercase().removePrefix("the ").removePrefix("a ").trim()

    fun sort(books: List<Book>, sort: Sort): List<Book> = when (sort) {
        Sort.Recent -> books.sortedWith(compareByDescending<Book> { it.lastOpenedAt ?: 0L }.thenByDescending { it.addedAt })
        Sort.Added -> books.sortedByDescending { it.addedAt }
        Sort.Title -> books.sortedBy { titleKey(it) }
        Sort.Author -> books.sortedWith(compareBy<Book> { it.author.ifBlank { "￿" }.lowercase() }.thenBy { titleKey(it) })
        Sort.Series -> books.sortedWith(compareBy<Book> { it.series.ifBlank { "￿" }.lowercase() }.thenBy { it.seriesIndex ?: 9999.0 }.thenBy { titleKey(it) })
        Sort.Rating -> books.sortedWith(compareByDescending<Book> { it.rating }.thenBy { titleKey(it) })
        Sort.Size -> books.sortedByDescending { it.size }
        Sort.Progress -> books.sortedByDescending { it.progress }
    }

    /** Groups in order, each with its name; books without one go last ("No series"…). */
    fun group(books: List<Book>, group: Group, collections: Map<String, String>): List<Pair<String, List<Book>>> = when (group) {
        Group.None -> listOf("" to books)
        Group.Author -> books.groupBy { it.author.ifBlank { "Unknown author" } }.toList().sortedBy { it.first.lowercase() }
        Group.Series -> books.groupBy { it.series.ifBlank { "" } }.toList()
            .sortedWith(compareBy<Pair<String, List<Book>>> { it.first.isEmpty() }.thenBy { it.first.lowercase() })
            .map { (name, list) -> name.ifBlank { "No series" } to list.sortedBy { it.seriesIndex ?: 9999.0 } }
        Group.Status -> Status.entries.reversed().let { order ->
            listOf(Status.Reading, Status.Want, Status.New, Status.Finished).mapNotNull { s -> books.filter { it.status() == s }.takeIf { it.isNotEmpty() }?.let { s.label to it } }
        }
        Group.Collection -> {
            val named = collections.entries.sortedBy { it.value.lowercase() }.mapNotNull { (id, name) ->
                books.filter { id in it.collectionIds }.takeIf { it.isNotEmpty() }?.let { name to it }
            }
            val none = books.filter { b -> b.collectionIds.none { it in collections } }
            named + listOfNotNull(none.takeIf { it.isNotEmpty() }?.let { "Not in a collection" to it })
        }
    }

    /** The series, each with its books in order and the next one to read. */
    data class SeriesShelf(val name: String, val books: List<Book>) {
        val next: Book? get() = books.firstOrNull { it.status() != Status.Finished }
        val done get() = books.count { it.status() == Status.Finished }
    }
    fun series(books: List<Book>): List<SeriesShelf> = books.filter { it.series.isNotBlank() }.groupBy { it.series }
        .map { (name, list) -> SeriesShelf(name, list.sortedWith(compareBy<Book> { it.seriesIndex ?: 9999.0 }.thenBy { it.addedAt })) }
        .sortedBy { it.name.lowercase() }

    /** Books that look like the same book: the same title and author, or the same size and title start. */
    fun duplicates(books: List<Book>): List<List<Book>> {
        fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        val byTitle = books.groupBy { norm(it.title) + "|" + norm(it.author) }.values.filter { it.size > 1 }
        val seen = byTitle.flatten().map { it.id }.toSet()
        val bySize = books.filter { it.id !in seen && it.size > 0 }.groupBy { it.size to norm(it.title).take(12) }.values.filter { it.size > 1 }
        return byTitle + bySize
    }

    fun matches(b: Book, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return listOf(b.title, b.author, b.series, b.tags.joinToString(" "), b.json.optString("publisher")).any { it.lowercase().contains(q) }
    }
}

/** Collections: {id, name, createdAt} records, like the web app's. */
class Collections(private val library: Library) {
    fun all(): Map<String, String> = library.records(STORE).associate { it.optString("id") to it.optString("name") }
        .filterKeys { it.isNotBlank() }
    fun create(name: String): String {
        val id = "c-" + UUID.randomUUID().toString().take(8)
        library.putRecords(STORE, listOf(JSONObject().put("id", id).put("name", name.trim()).put("createdAt", System.currentTimeMillis())))
        return id
    }
    fun rename(id: String, name: String) =
        library.putRecords(STORE, listOf(JSONObject().put("id", id).put("name", name.trim()).put("createdAt", System.currentTimeMillis())))
    fun delete(id: String) {
        library.putAll(library.books.value.filter { id in it.collectionIds }.map { b -> b.with({ put("collections", JSONArray(b.collectionIds - id)) }) })
        library.removeRecords(STORE, listOf(id))
    }
    companion object { const val STORE = "collections" }
}

fun Book.withCollection(id: String, on: Boolean): Book =
    with({ put("collections", JSONArray(if (on) (collectionIds + id).distinct() else collectionIds - id)) })

fun Book.withTags(tags: List<String>): Book = with({ put("tags", JSONArray(tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct())) })
