package app.quietreader.data

import app.quietreader.BookFont
import app.quietreader.Flow
import app.quietreader.MotionMode
import app.quietreader.QuietSettings
import app.quietreader.Rotation
import app.quietreader.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Library backup and restore, in the same `.quietbackup` format as the web app
 * (src/library/backup.ts), so a backup moves both ways between 0.4.2 and the native app:
 *
 *   quiet-backup.json   the records: books (without covers), collections, highlights, notes and
 *                       quotes, reading time, your fonts' details, and Quiet's settings
 *   books/<id>          each book file
 *   covers/<id>         each cover
 *   fonts/<id>          each font you added
 *
 * Restoring adds what's missing and never deletes anything: books you don't have come in, a book
 * you have takes the backup's place if the backup's is newer, other records you don't have are
 * added, and reading time keeps the larger of the two. Settings come along only into an empty
 * library (a new phone).
 */
class Backup(private val library: Library, private val settings: QuietSettings, private val fontsDir: File) {

    class NotABackup(message: String) : Exception(message)

    data class Summary(val books: Int, val notes: Int, val createdAt: Long, val app: String)
    data class Restored(val added: Int, val updated: Int, val notes: Int, val settings: Boolean, val unreadable: Int)

    /** Writes a backup of everything to [out]. Returns the number of books in it. */
    fun write(out: OutputStream, appVersion: String, progress: (Float) -> Unit = {}): Int {
        val books = library.books.value
        val saved = JSONArray()
        val files = mutableListOf<Pair<String, File>>()
        for (book in books) {
            val file = library.fileOf(book.id)
            if (!file.exists()) continue
            files += "books/${Library.safe(book.id)}" to file
            val cover = library.coverOf(book.id)
            if (cover.exists()) files += "covers/${Library.safe(book.id)}" to cover
            saved.put(JSONObject(book.json.toString()).put("hasCover", cover.exists()).apply { remove("cover") })
        }
        val fonts = library.records("fonts")
        for (font in fonts) {
            val file = File(fontsDir, Library.safe(font.getString("id")))
            if (file.exists()) files += "fonts/${Library.safe(font.getString("id"))}" to file
        }
        val manifest = JSONObject().apply {
            put("format", FORMAT); put("version", VERSION); put("createdAt", System.currentTimeMillis()); put("app", appVersion)
            put("books", saved)
            for (store in listOf("collections", "annotations", "reading")) put(store, JSONArray(library.records(store)))
            put("fonts", JSONArray(fonts))
            put("settings", webSettings())
        }

        // Stored, not compressed (like the web app's): book files are already compressed.
        ZipOutputStream(out.buffered()).use { zip ->
            zip.store("quiet-backup.json", manifest.toString().toByteArray())
            files.forEachIndexed { i, (name, file) ->
                zip.store(name, file)
                progress((i + 1f) / files.size)
            }
        }
        settings.lastBackup = System.currentTimeMillis()
        return saved.length()
    }

    /** Says what's in a backup file without changing anything. */
    fun read(file: File): Summary {
        val manifest = manifest(file)
        val annotations = manifest.optJSONArray("annotations") ?: JSONArray()
        val notes = (0 until annotations.length()).count { annotations.getJSONObject(it).optString("type") == "highlight" }
        return Summary(manifest.optJSONArray("books")?.length() ?: 0, notes, manifest.optLong("createdAt"), manifest.optString("app"))
    }

    /** Puts a backup into the library (see the top of this file). */
    fun restore(file: File, progress: (String) -> Unit = {}): Restored {
        val manifest = manifest(file)
        val wasEmpty = library.books.value.isEmpty()
        var added = 0; var updated = 0; var unreadable = 0
        val books = mutableListOf<Book>()
        ZipFile(file).use { zip ->
            val list = manifest.optJSONArray("books") ?: JSONArray()
            for (i in 0 until list.length()) {
                progress("Restoring books… ${i + 1} of ${list.length()}")
                val record = list.getJSONObject(i)
                val id = record.optString("id").ifBlank { continue }
                val hasCover = record.optBoolean("hasCover")
                record.remove("hasCover"); record.remove("cover")
                val mine = library.get(id)
                if (mine == null) {
                    val entry = zip.getEntry("books/${Library.safe(id)}") ?: continue
                    zip.getInputStream(entry).use { input -> library.fileOf(id).outputStream().use { input.copyTo(it) } }
                    if (hasCover) zip.getEntry("covers/${Library.safe(id)}")?.let { e ->
                        zip.getInputStream(e).use { input -> library.coverOf(id).outputStream().use { input.copyTo(it) } }
                    }
                    if (BookFormat.of(record.optString("fileName"), record.optString("fileType")) == BookFormat.Other) unreadable++
                    books += Book(record, null)
                    added++
                } else if (record.optLong("lastOpenedAt", 0) > (mine.lastOpenedAt ?: 0)) {
                    // The backup was read further: take its place. Readium's own locator is
                    // dropped, so the reader starts from the backup's position.
                    books += mine.with({
                        for (key in listOf("progress", "cfi", "lastOpenedAt", "finished", "finishedAt")) put(key, record.opt(key) ?: JSONObject.NULL)
                        put("deletedAt", JSONObject.NULL)
                    }, locator = null)
                    updated++
                }
            }

            // Fonts: the details and the file.
            val fonts = mutableListOf<JSONObject>()
            val haveFonts = library.records("fonts").map { it.optString("id") }.toSet()
            manifest.optJSONArray("fonts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val font = arr.getJSONObject(i)
                    val id = font.optString("id")
                    if (id.isBlank() || id in haveFonts) continue
                    val entry = zip.getEntry("fonts/${Library.safe(id)}") ?: continue
                    fontsDir.mkdirs()
                    zip.getInputStream(entry).use { input -> File(fontsDir, Library.safe(id)).outputStream().use { input.copyTo(it) } }
                    fonts += font
                }
            }
            library.putRecords("fonts", fonts)
        }

        var notes = 0
        for (store in listOf("collections", "annotations")) {
            val have = library.records(store).map { it.optString("id") }.toSet()
            val new = manifest.optJSONArray(store).objects().filter { it.optString("id").isNotBlank() && it.optString("id") !in have }
            library.putRecords(store, new)
            if (store == "annotations") notes = new.size
        }
        val reading = library.records("reading").associateBy { it.optString("id") }
        library.putRecords("reading", manifest.optJSONArray("reading").objects().filter {
            it.optString("id").isNotBlank() && (reading[it.optString("id")]?.optDouble("seconds") ?: -1.0) < it.optDouble("seconds", 0.0)
        })
        library.putAll(books) // last: a book shows up only when the rest is in

        var settingsRestored = false
        if (wasEmpty) manifest.optJSONObject("settings")?.let { applyWebSettings(it); settingsRestored = true }
        return Restored(added, updated, notes, settingsRestored, unreadable)
    }

    private fun manifest(file: File): JSONObject {
        val json = try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("quiet-backup.json") ?: throw NotABackup("This isn’t a Quiet backup.")
                zip.getInputStream(entry).use { String(it.readBytes()) }
            }
        } catch (e: NotABackup) { throw e } catch (e: Exception) { throw NotABackup("This isn’t a Quiet backup.") }
        val manifest = runCatching { JSONObject(json) }.getOrNull() ?: throw NotABackup("This backup is damaged.")
        if (manifest.optString("format") != FORMAT) throw NotABackup("This isn’t a Quiet backup.")
        if (manifest.optInt("version") > VERSION) throw NotABackup("This backup was made by a newer Quiet. Update Quiet first.")
        return manifest
    }

    // ---------- Settings, in the web app's form ----------

    /** The web app's settings (`quiet:*` keys), with this app's choices written into them. */
    fun webSettings(): JSONObject {
        val all = runCatching { JSONObject(settings.webSettings) }.getOrElse { JSONObject() }
        val s = runCatching { JSONObject(all.optString("quiet:settings", "{}")) }.getOrElse { JSONObject() }
        s.put("theme", settings.theme.key)
        s.put("uiScale", settings.textScale)
        s.put("motion", settings.motion.key)
        s.put("orientation", settings.rotation.key)
        s.put("haptics", settings.haptics)
        s.put("font", settings.bookFont.key)
        s.put("fontSize", settings.bookSize)
        s.put("flow", settings.flow.key)
        all.put("quiet:settings", s.toString())
        return all
    }

    /** Takes over a backup's settings: the ones this app has, and the rest kept for later layers. */
    fun applyWebSettings(all: JSONObject) {
        settings.webSettings = all.toString()
        val s = runCatching { JSONObject(all.optString("quiet:settings", "{}")) }.getOrNull() ?: return
        ThemeMode.entries.firstOrNull { it.key == s.optString("theme") }?.let { settings.theme = it }
        if (s.optString("theme") == "custom") settings.theme = ThemeMode.Day // Custom colours come later
        if (s.has("uiScale")) settings.textScale = s.optInt("uiScale", 100)
        MotionMode.entries.firstOrNull { it.key == s.optString("motion") }?.let { settings.motion = it }
        Rotation.entries.firstOrNull { it.key == s.optString("orientation") }?.let { settings.rotation = it }
        if (s.has("haptics")) settings.haptics = s.optBoolean("haptics", true)
        val font = s.optString("font")
        settings.bookFont = BookFont.entries.firstOrNull { it.key == font } ?: if (font.startsWith("custom:")) BookFont.Serif else settings.bookFont
        if (s.has("fontSize")) settings.bookSize = s.optInt("fontSize", 100)
        Flow.entries.firstOrNull { it.key == s.optString("flow") }?.let { settings.flow = it }
    }

    companion object {
        const val FORMAT = "quiet-backup"
        const val VERSION = 1

        private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

        private fun ZipOutputStream.store(name: String, data: ByteArray) {
            putNextEntry(ZipEntry(name).apply {
                method = ZipEntry.STORED; size = data.size.toLong(); compressedSize = size
                crc = CRC32().apply { update(data) }.value
            })
            write(data); closeEntry()
        }

        private fun ZipOutputStream.store(name: String, file: File) {
            val crc = CRC32()
            file.inputStream().buffered().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; crc.update(buf, 0, n) }
            }
            putNextEntry(ZipEntry(name).apply {
                method = ZipEntry.STORED; size = file.length(); compressedSize = size; this.crc = crc.value
            })
            file.inputStream().use { it.copyTo(this) }
            closeEntry()
        }
    }
}
