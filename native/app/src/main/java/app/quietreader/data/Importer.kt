package app.quietreader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Readium's parts for opening books, made once. */
class Readium(context: Context) {
    private val http = DefaultHttpClient()
    val assets = AssetRetriever(context.contentResolver, http)
    val opener = PublicationOpener(DefaultPublicationParser(context, http, assets, pdfFactory = null))

    /** Opens an EPUB file (a TXT book is opened through its EPUB copy). */
    suspend fun open(file: File): Publication? {
        val asset = assets.retrieve(file).getOrElse { return null }
        return opener.open(asset, allowUserInteraction = false).getOrElse { asset.close(); null }
    }
}

sealed interface ImportResult {
    data class Added(val book: Book) : ImportResult
    data class AlreadyThere(val book: Book) : ImportResult
    data class NotSupported(val name: String, val format: String) : ImportResult
    data class Failed(val name: String, val reason: String) : ImportResult
}

/** Adds book files to the library: copies them in, reads their details and cover. */
class Importer(private val context: Context, private val library: Library, private val readium: Readium) {

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val temp = File(context.cacheDir, "import-${System.nanoTime()}")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                ?: return@withContext ImportResult.Failed(name, "Couldn’t read the file.")
            importFile(temp, name, context.contentResolver.getType(uri))
        } catch (e: Exception) {
            ImportResult.Failed(name, e.message ?: "Couldn’t read the file.")
        } finally { temp.delete() }
    }

    /** Imports [file] (it is copied; the original stays where it is). */
    suspend fun importFile(file: File, name: String, mime: String? = null): ImportResult = withContext(Dispatchers.IO) {
        val format = BookFormat.of(name, mime)
        if (format == BookFormat.Other) {
            return@withContext ImportResult.NotSupported(name, name.substringAfterLast('.', "this kind of").uppercase())
        }
        val id = fingerprint(file)
        library.get(id)?.let { existing ->
            // Adding a book that's in Recently deleted brings it back.
            if (existing.deletedAt != null) library.put(existing.with({ put("deletedAt", JSONObject.NULL) }))
            return@withContext ImportResult.AlreadyThere(library.get(id)!!)
        }
        file.copyTo(library.fileOf(id), overwrite = true)
        val record = JSONObject().apply {
            put("id", id)
            put("title", name.substringBeforeLast('.'))
            put("author", "")
            put("fileName", name)
            put("fileType", format.mime)
            put("size", file.length())
            put("addedAt", System.currentTimeMillis())
            put("lastOpenedAt", JSONObject.NULL)
            put("progress", 0)
            put("cfi", JSONObject.NULL)
            put("format", BookFormat.labelOf(name))
        }
        try {
            when {
                format == BookFormat.Epub -> readEpub(library.fileOf(id), id, record)
                format == BookFormat.Pdf -> readPdf(library.fileOf(id), id)
                else -> {
                    // Every other kind becomes an EPUB; its title, author and cover come from there.
                    app.quietreader.data.convert.Converters.toEpub(library.fileOf(id), name, library.derivedOf(id))
                    readEpub(library.derivedOf(id), id, record, keepTitle = format == BookFormat.Txt)
                }
            }
        } catch (e: Exception) {
            app.quietreader.ErrorLog.keep("Couldn’t add $name", e)
            library.fileOf(id).delete(); library.derivedOf(id).delete()
            return@withContext ImportResult.Failed(name, e.message?.takeIf { it.endsWith(".") } ?: "This ${BookFormat.labelOf(name)} couldn’t be opened.")
        }
        val book = Book(record, null)
        library.put(book)
        ImportResult.Added(book)
    }

    private suspend fun readEpub(file: File, id: String, record: JSONObject, keepTitle: Boolean = false) {
        val publication = readium.open(file) ?: throw IllegalStateException("not an EPUB")
        try {
            val m = publication.metadata
            if (!keepTitle) m.title?.takeIf { it.isNotBlank() }?.let { record.put("title", it.trim()) }
            record.put("author", m.authors.joinToString(", ") { it.name })
            m.publishers.firstOrNull()?.let { record.put("publisher", it.name) }
            // The language's name, as the web app stores it ("English").
            m.languages.firstOrNull()?.let { record.put("language", java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.ENGLISH).ifBlank { it }) }
            m.published?.let { record.put("published", it.toString().take(10)) }
            m.description?.let { record.put("description", it) }
            if (m.subjects.isNotEmpty()) record.put("subjects", JSONArray(m.subjects.map { it.name }))
            // Series from the book itself (EPUB 3 belongs-to-collection, or Calibre's series).
            m.belongsToSeries.firstOrNull()?.let { s ->
                record.put("series", s.name)
                s.position?.let { record.put("seriesIndex", it) }
            }
            m.identifier?.let { id -> Regex("""(97[89])?\d{9}[\dXx]""").find(id.replace("-", ""))?.let { record.put("isbn", it.value) } }
            publication.cover()?.let { saveCover(it, id) }
        } finally { publication.close() }
    }

    private fun readPdf(file: File, id: String) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { pdf ->
                if (pdf.pageCount == 0) throw IllegalStateException("empty PDF")
                pdf.openPage(0).use { page ->
                    val w = 360
                    val h = (w * page.height.toFloat() / page.width).toInt().coerceIn(1, 1200)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    saveCover(bitmap, id)
                }
            }
        }
    }

    /** Covers are kept small (360 px wide): the library only needs a thumbnail. */
    private fun saveCover(source: Bitmap, id: String) {
        val w = 360
        val bitmap = if (source.width > w) Bitmap.createScaledBitmap(source, w, (w * source.height.toFloat() / source.width).toInt(), true) else source
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
        library.coverOf(id).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    /** A new cover from a picture you chose. */
    fun setCover(id: String, uri: Uri): Boolean {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return false
        saveCover(bitmap, id)
        library.get(id)?.let { library.put(it.with({ put("coverChangedAt", System.currentTimeMillis()) })) }
        return true
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Book"
    }

    companion object {
        /**
         * A short, stable ID for a file, the same as the web app's (src/library/import.ts):
         * SHA-256 of the first and last 256 KB plus the size as a big-endian double; 12 bytes in hex.
         * The same book gets the same ID in both apps, so restoring a backup never doubles it.
         */
        fun fingerprint(file: File): String {
            val chunk = 256 * 1024L
            val size = file.length()
            val digest = MessageDigest.getInstance("SHA-256")
            RandomAccessFile(file, "r").use { raf ->
                val head = ByteArray(minOf(chunk, size).toInt()).also { raf.readFully(it) }
                val start = maxOf(0L, size - chunk)
                val tail = ByteArray((size - start).toInt()).also { raf.seek(start); raf.readFully(it) }
                digest.update(head); digest.update(tail)
            }
            digest.update(ByteBuffer.allocate(8).putDouble(size.toDouble()).array())
            return digest.digest().take(12).joinToString("") { "%02x".format(it) }
        }
    }
}
