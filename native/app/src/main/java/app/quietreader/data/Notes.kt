package app.quietreader.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A highlight, note, quote or bookmark, in the web app's form (src/library/db.ts, Annotation). */
data class Note(val json: JSONObject) {
    val id: String get() = json.getString("id")
    val bookId: String get() = json.optString("bookId")
    val isBookmark: Boolean get() = json.optString("type") == "bookmark"
    val text: String get() = json.optString("text")
    val note: String get() = json.optString("note")
    val color: String get() = json.optString("color").ifBlank { COLORS[0].value }
    val style: String get() = json.optString("style").ifBlank { "highlight" } // highlight | underline | strike | plain
    val quote: Boolean get() = json.optBoolean("quote")
    val speaker: String get() = json.optString("speaker")
    val label: String get() = json.optString("label")
    val progress: Double get() = json.optDouble("progress", 0.0).takeIf { !it.isNaN() } ?: 0.0
    val createdAt: Long get() = json.optLong("createdAt")
    val cfi: String get() = json.optString("cfi")
    /** Readium's exact place (native only); notes from 0.4.2 have only a CFI and the text. */
    val locator: String? get() = json.optString("locator").takeIf { it.isNotBlank() }
    val page: Int? get() = json.optInt("page", -1).takeIf { it >= 0 } // PDF bookmarks
    val tags: List<String> get() = json.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()

    fun with(change: JSONObject.() -> Unit) = Note(JSONObject(json.toString()).apply(change))

    data class Color(val name: String, val value: String, val ink: String)

    companion object {
        /** The web app's highlight colours (selection-menu.ts): a soft fill, and a stronger ink for lines. */
        val COLORS = listOf(
            Color("Yellow", "#f2c94c", "#d19a1c"), Color("Green", "#7fbf7a", "#4c9a47"),
            Color("Blue", "#6fa8dc", "#3b7fc4"), Color("Pink", "#e88ca5", "#d2557a"),
        )
        fun inkOf(color: String) = COLORS.firstOrNull { it.value == color }?.ink ?: color

        fun new(bookId: String, type: String, text: String, cfi: String) = Note(JSONObject().apply {
            put("id", UUID.randomUUID().toString()); put("bookId", bookId); put("type", type)
            put("text", text); put("cfi", cfi); put("createdAt", System.currentTimeMillis())
        })
    }
}

/** The notes on this phone: the "annotations" records. */
class Notes(private val library: Library) {
    fun all(): List<Note> = library.records(STORE).map(::Note).sortedByDescending { it.createdAt }
    fun forBook(bookId: String): List<Note> = all().filter { it.bookId == bookId }.sortedBy { it.progress }
    fun put(note: Note) = library.putRecords(STORE, listOf(note.json))
    fun delete(id: String) = library.removeRecords(STORE, listOf(id))
    fun deleteForBook(bookId: String) = library.removeRecords(STORE, all().filter { it.bookId == bookId }.map { it.id })

    companion object {
        const val STORE = "annotations"
        fun tagsJson(tags: List<String>) = JSONArray(tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct())
    }
}
