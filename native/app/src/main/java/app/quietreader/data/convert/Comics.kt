package app.quietreader.data.convert

import android.graphics.BitmapFactory
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.util.zip.ZipFile

/**
 * Comics (CBZ, CBR, CB7, CBT, and zip/rar/7z/tar archives of pictures), turned into a
 * fixed-layout EPUB: one picture a page, in natural order (2 before 10), skipping the junk
 * archives often carry (__MACOSX, thumbnails, hidden files). CBR is RAR 4; RAR 5 can't be read yet.
 */
object Comics {
    private val pictures = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    private fun junk(name: String): Boolean {
        val n = name.replace('\\', '/')
        val base = n.substringAfterLast('/')
        return n.contains("__MACOSX/") || base.startsWith(".") || base.equals("thumbs.db", true) ||
            n.split('/').any { it.startsWith(".") } || base.substringAfterLast('.', "").lowercase() !in pictures
    }

    /** "page 2" before "page 10". */
    private val natural = Comparator<String> { a, b ->
        val ra = Regex("\\d+|\\D+").findAll(a.lowercase()).map { it.value }.toList()
        val rb = Regex("\\d+|\\D+").findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ra.size, rb.size)) {
            val x = ra[i]; val y = rb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) x.trimStart('0').length.compareTo(y.trimStart('0').length).takeIf { it != 0 } ?: x.trimStart('0').compareTo(y.trimStart('0'))
            else x.compareTo(y)
            if (c != 0) return@Comparator c
        }
        ra.size.compareTo(rb.size)
    }

    /** The pictures inside, as (name, bytes), in reading order. */
    fun pages(file: File): List<Pair<String, ByteArray>> {
        val head = file.inputStream().use { s -> ByteArray(8).also { s.read(it) } }
        val list: List<Pair<String, ByteArray>> = when {
            head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> ZipFile(file).use { z ->
                z.entries().toList().filter { !it.isDirectory && !junk(it.name) }.map { e -> e.name to z.getInputStream(e).use { it.readBytes() } }
            }
            String(head, 0, 4, Charsets.ISO_8859_1) == "Rar!" -> {
                require(head[6].toInt() == 0) { "This CBR is RAR 5, which Quiet can't read yet. Re-save it as CBZ (or RAR 4)." }
                val out = mutableListOf<Pair<String, ByteArray>>()
                com.github.junrar.Archive(file).use { a ->
                    for (h in a.fileHeaders) {
                        if (h.isDirectory || junk(h.fileName)) continue
                        val bytes = java.io.ByteArrayOutputStream().also { a.extractFile(h, it) }.toByteArray()
                        out += h.fileName to bytes
                    }
                }
                out
            }
            head[0] == '7'.code.toByte() && head[1] == 'z'.code.toByte() -> {
                val out = mutableListOf<Pair<String, ByteArray>>()
                SevenZFile.builder().setFile(file).get().use { z ->
                    var e = z.nextEntry
                    while (e != null) {
                        if (!e.isDirectory && !junk(e.name)) out += e.name to z.getInputStream(e).readBytes()
                        e = z.nextEntry
                    }
                }
                out
            }
            else -> {
                // tar (CBT): "ustar" at byte 257
                val out = mutableListOf<Pair<String, ByteArray>>()
                TarArchiveInputStream(file.inputStream().buffered()).use { t ->
                    var e = t.nextEntry
                    while (e != null) {
                        if (!e.isDirectory && !junk(e.name)) out += e.name to t.readBytes()
                        e = t.nextEntry
                    }
                }
                out
            }
        }
        return list.sortedWith(compareBy(natural) { it.first })
    }

    fun toEpub(file: File, name: String, out: File) {
        val pages = pages(file)
        require(pages.isNotEmpty()) { "No pictures inside." }
        val book = EpubBuilder(name.substringBeforeLast('.').replace('_', ' '))
        // The page size comes from the first picture (most comics keep one size).
        val size = pages.firstNotNullOfOrNull { (_, b) ->
            BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeByteArray(b, 0, b.size, it) }
                .takeIf { it.outWidth > 0 }?.let { it.outWidth to it.outHeight }
        } ?: (1000 to 1500)
        book.fixedLayout = size
        pages.forEachIndexed { i, (n, bytes) ->
            val (mime, ext) = EpubBuilder.sniffImage(bytes) ?: (EpubBuilder.mimeOf(n) to n.substringAfterLast('.'))
            val path = book.resource("images/p%04d.%s".format(i, ext), mime, bytes)
            if (i == 0) book.coverPath = path
            book.rawChapter("", EpubBuilder.page("${i + 1}",
                """<img src="$path" alt="" style="display:block;width:100%;height:100%;object-fit:contain;margin:0;padding:0"/>""",
                viewport = size).replace("<head>", "<head><style>html,body{margin:0;padding:0;width:${size.first}px;height:${size.second}px;background:#000}</style>"))
        }
        book.toc = listOf(EpubBuilder.Toc("Page 1", book.chapters.first().file))
        book.write(out)
    }
}
