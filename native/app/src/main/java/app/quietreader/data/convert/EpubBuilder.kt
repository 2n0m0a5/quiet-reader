package app.quietreader.data.convert

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a small EPUB 3 from converted parts, so every format reads in the same reader (fonts,
 * highlights, search, curl). Used for MOBI/AZW3, FB2, Word, ODT, RTF, Markdown, HTML, Palm and
 * comics. Chapters are XHTML; images, fonts and CSS are resources; the contents list is a nav.
 */
class EpubBuilder(var title: String, var author: String = "", var language: String = "und") {
    var publisher = ""
    var description = ""
    var published = ""
    var isbn = ""
    var series = ""
    var seriesIndex: Double? = null
    val subjects = mutableListOf<String>()
    /** Width × height for fixed-layout books (comics); null for reflowable. */
    var fixedLayout: Pair<Int, Int>? = null
    var rtl = false

    class Chapter(val file: String, val xhtml: String, val title: String)
    class Resource(val path: String, val mime: String, val bytes: ByteArray, val id: String)
    class Toc(val label: String, val href: String, val children: List<Toc> = emptyList())

    val chapters = mutableListOf<Chapter>()
    val resources = mutableListOf<Resource>()
    var toc: List<Toc> = emptyList()
    var coverPath: String? = null

    /** Adds a chapter from an HTML body (or a whole HTML document); it's cleaned into XHTML. */
    fun chapter(title: String, html: String, css: List<String> = emptyList()): String {
        val file = "c${chapters.size}.xhtml"
        chapters += Chapter(file, xhtml(title, html, css), title)
        return file
    }

    /** Adds a chapter that is already valid XHTML. */
    fun rawChapter(title: String, xhtml: String): String {
        val file = "c${chapters.size}.xhtml"
        chapters += Chapter(file, xhtml, title)
        return file
    }

    fun resource(path: String, mime: String, bytes: ByteArray): String {
        resources.firstOrNull { it.path == path }?.let { return it.path }
        resources += Resource(path, mime, bytes, "r${resources.size}")
        return path
    }

    fun write(out: File) {
        out.parentFile?.mkdirs()
        if (chapters.isEmpty()) chapter(title, "<p></p>")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            stored(zip, "mimetype", "application/epub+zip".toByteArray())
            text(zip, "META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            for (c in chapters) text(zip, "OEBPS/${c.file}", c.xhtml)
            for (r in resources) {
                // Pictures and fonts are already compressed: store them.
                if (r.mime.startsWith("image/") || r.mime.startsWith("font/")) stored(zip, "OEBPS/${r.path}", r.bytes)
                else { zip.putNextEntry(ZipEntry("OEBPS/${r.path}")); zip.write(r.bytes); zip.closeEntry() }
            }
            text(zip, "OEBPS/nav.xhtml", nav())
            text(zip, "OEBPS/content.opf", opf())
        }
    }

    private fun opf(): String {
        val fixed = fixedLayout != null
        val items = StringBuilder()
        items.append("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
        chapters.forEachIndexed { i, c -> items.append("""<item id="c$i" href="${c.file}" media-type="application/xhtml+xml"${if (fixed && false) "" else ""}/>""") }
        for (r in resources) items.append("""<item id="${r.id}" href="${esc(r.path)}" media-type="${r.mime}"${if (r.path == coverPath) " properties=\"cover-image\"" else ""}/>""")
        val spine = chapters.indices.joinToString("") { """<itemref idref="c$it"/>""" }
        val meta = StringBuilder()
        meta.append("<dc:identifier id=\"id\">quiet-${title.hashCode().toUInt()}-${chapters.size}</dc:identifier>")
        meta.append("<dc:title>${esc(title)}</dc:title><dc:language>${esc(language.ifBlank { "und" })}</dc:language>")
        author.split(Regex("\\s*[;&]\\s*|\\s+and\\s+")).filter { it.isNotBlank() }.forEach { meta.append("<dc:creator>${esc(it.trim())}</dc:creator>") }
        if (publisher.isNotBlank()) meta.append("<dc:publisher>${esc(publisher)}</dc:publisher>")
        if (description.isNotBlank()) meta.append("<dc:description>${esc(description)}</dc:description>")
        if (published.isNotBlank()) meta.append("<dc:date>${esc(published)}</dc:date>")
        if (isbn.isNotBlank()) meta.append("<dc:identifier>urn:isbn:${esc(isbn)}</dc:identifier>")
        subjects.forEach { meta.append("<dc:subject>${esc(it)}</dc:subject>") }
        if (series.isNotBlank()) {
            meta.append("""<meta property="belongs-to-collection" id="series">${esc(series)}</meta><meta refines="#series" property="collection-type">series</meta>""")
            seriesIndex?.let { meta.append("""<meta refines="#series" property="group-position">${fmt(it)}</meta>""") }
        }
        coverPath?.let { cp -> resources.firstOrNull { it.path == cp }?.let { meta.append("""<meta name="cover" content="${it.id}"/>""") } }
        if (fixed) meta.append("""<meta property="rendition:layout">pre-paginated</meta><meta property="rendition:spread">none</meta>""")
        meta.append("""<meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>""")
        return """<?xml version="1.0" encoding="utf-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"${if (fixed) " prefix=\"rendition: http://www.idpf.org/vocab/rendition/#\"" else ""}><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">$meta</metadata><manifest>$items</manifest><spine${if (rtl) " page-progression-direction=\"rtl\"" else ""}>$spine</spine></package>"""
    }

    private fun nav(): String {
        // Without a contents list: the chapters' titles, or "Part 1, 2…" (or the book's title, for one part).
        val entries = toc.ifEmpty {
            chapters.filter { it.title.isNotBlank() }.map { Toc(it.title, it.file) }.ifEmpty {
                if (chapters.size == 1) listOf(Toc(title, chapters[0].file)) else chapters.mapIndexed { i, c -> Toc("Part ${i + 1}", c.file) }
            }
        }
        fun list(items: List<Toc>): String = "<ol>" + items.joinToString("") { t ->
            "<li><a href=\"${esc(t.href)}\">${esc(t.label.ifBlank { "·" })}</a>${if (t.children.isNotEmpty()) list(t.children) else ""}</li>"
        } + "</ol>"
        return page(title, """<nav epub:type="toc" id="toc"><h1>${esc(title)}</h1>${list(entries)}</nav>""")
    }

    companion object {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

        fun page(title: String, body: String, css: List<String> = emptyList(), viewport: Pair<Int, Int>? = null) =
            """<?xml version="1.0" encoding="utf-8"?><!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><meta charset="utf-8"/><title>${esc(title)}</title>""" +
                (viewport?.let { (w, h) -> """<meta name="viewport" content="width=$w, height=$h"/>""" } ?: "") +
                css.joinToString("") { """<link rel="stylesheet" type="text/css" href="${esc(it)}"/>""" } +
                "</head><body>$body</body></html>"

        /** Any HTML (a body or a whole document) as well-formed XHTML. */
        fun xhtml(title: String, html: String, css: List<String> = emptyList()): String {
            val doc = Jsoup.parse(html)
            doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml).escapeMode(Entities.EscapeMode.xhtml).charset("UTF-8").prettyPrint(false)
            doc.select("script, iframe, object, embed").remove()
            val headCss = doc.head().select("style").joinToString("") { "<style>${it.data()}</style>" }
            return page(title, doc.body().html(), css).replace("</head>", "$headCss</head>")
        }

        private fun stored(zip: ZipOutputStream, name: String, data: ByteArray) {
            zip.putNextEntry(ZipEntry(name).apply {
                method = ZipEntry.STORED; size = data.size.toLong(); compressedSize = size
                crc = CRC32().apply { update(data) }.value
            })
            zip.write(data); zip.closeEntry()
        }

        private fun text(zip: ZipOutputStream, name: String, content: String) {
            zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
        }

        fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"
            "svg" -> "image/svg+xml"; "bmp" -> "image/bmp"; "css" -> "text/css"
            "ttf" -> "font/ttf"; "otf" -> "font/otf"; "woff" -> "font/woff"; "woff2" -> "font/woff2"
            "xhtml", "html", "htm" -> "application/xhtml+xml"
            else -> "application/octet-stream"
        }

        /** Image type from its first bytes. */
        fun sniffImage(bytes: ByteArray): Pair<String, String>? = when {
            bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg" to "jpg"
            bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png" to "png"
            bytes.size > 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "image/gif" to "gif"
            bytes.size > 12 && String(bytes, 8, 4) == "WEBP" -> "image/webp" to "webp"
            bytes.size > 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte() -> "image/bmp" to "bmp"
            else -> null
        }
    }
}
