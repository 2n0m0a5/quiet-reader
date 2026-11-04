package app.quietreader.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File

/**
 * Your own fonts (TTF, OTF, WOFF, WOFF2), kept like the web app's: a record in the "fonts" store
 * ({id, family, weight, style, fileName, addedAt}) and the file in files/fonts/<id>.
 * Fonts from a 0.4.2 backup arrive the same way.
 */
class Fonts(private val context: Context, private val library: Library) {
    val dir = File(context.filesDir, "fonts").apply { mkdirs() }

    data class Font(val id: String, val family: String, val weight: Int, val italic: Boolean, val fileName: String) {
        val mime get() = when (fileName.substringAfterLast('.').lowercase()) {
            "otf" -> "font/otf"; "woff" -> "font/woff"; "woff2" -> "font/woff2"; else -> "font/ttf"
        }
    }

    fun all(): List<Font> = library.records("fonts").mapNotNull { r ->
        val id = r.optString("id").ifBlank { return@mapNotNull null }
        if (!fileOf(id).exists()) return@mapNotNull null
        Font(id, r.optString("family").ifBlank { r.optString("fileName").substringBeforeLast('.') }, r.optInt("weight", 400),
            r.optString("style") == "italic", r.optString("fileName"))
    }.sortedBy { it.family.lowercase() }

    fun families(): List<String> = all().map { it.family }.distinct()

    fun fileOf(id: String) = File(dir, Library.safe(id))

    /** Adds a font file. Returns its family name, or null if it isn't a font. */
    fun add(uri: Uri): String? {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "font.ttf"
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("ttf", "otf", "woff", "woff2")) return null
        val id = "font-" + System.currentTimeMillis()
        context.contentResolver.openInputStream(uri)?.use { input -> fileOf(id).outputStream().use { input.copyTo(it) } } ?: return null
        val base = name.substringBeforeLast('.')
        val italic = Regex("italic|oblique", RegexOption.IGNORE_CASE).containsMatchIn(base)
        val weight = when {
            Regex("thin|hairline", RegexOption.IGNORE_CASE).containsMatchIn(base) -> 100
            Regex("light", RegexOption.IGNORE_CASE).containsMatchIn(base) -> 300
            Regex("medium", RegexOption.IGNORE_CASE).containsMatchIn(base) -> 500
            Regex("semi.?bold|demi", RegexOption.IGNORE_CASE).containsMatchIn(base) -> 600
            Regex("extra.?bold|black|heavy", RegexOption.IGNORE_CASE).containsMatchIn(base) -> 800
            Regex("bold", RegexOption.IGNORE_CASE).containsMatchIn(base) -> 700
            else -> 400
        }
        val family = base.replace(Regex("[-_ ]?(regular|italic|oblique|thin|hairline|extra.?light|light|medium|semi.?bold|demi.?bold|extra.?bold|bold|black|heavy|variable|vf)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[-_]+"), " ").trim().ifBlank { base }
        library.putRecords("fonts", listOf(JSONObject().apply {
            put("id", id); put("family", family); put("weight", weight); put("style", if (italic) "italic" else "normal")
            put("fileName", name); put("addedAt", System.currentTimeMillis())
        }))
        return family
    }

    fun remove(family: String) {
        val gone = all().filter { it.family == family }
        gone.forEach { fileOf(it.id).delete() }
        library.removeRecords("fonts", gone.map { it.id })
    }
}
