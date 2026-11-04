package app.quietreader.data.convert

import app.quietreader.data.TextBook
import org.commonmark.parser.Parser as MarkdownParser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/*
 * Document formats turned into an EPUB: FB2/FBZ, Word (DOCX), OpenDocument (ODT), RTF,
 * Markdown, HTML/HTMLZ and Palm (PDB). Each keeps its chapters, headings, bold and italic,
 * pictures where it has them, and its title and author.
 */
object Documents {
    private fun esc(s: String) = EpubBuilder.esc(s)

    /** Long books without headings are split into chapters of about this many characters. */
    private const val CHUNK = 40_000

    // ---------- FB2 / FBZ ----------

    fun fb2(bytes: ByteArray, name: String, out: File) {
        val xml = if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            // FBZ / .fb2.zip: the .fb2 inside.
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                generateSequence { z.nextEntry }.firstOrNull { it.name.lowercase().endsWith(".fb2") } ?: error("No .fb2 inside")
                z.readBytes()
            }
        } else bytes
        val doc = Jsoup.parse(ByteArrayInputStream(xml), charsetOf(xml), "", Parser.xmlParser())
        val info = doc.selectFirst("description > title-info")
        val title = info?.selectFirst("book-title")?.text()?.trim().orEmpty().ifBlank { name.substringBeforeLast('.') }
        val authors = info?.select("author")?.map { a -> listOf("first-name", "middle-name", "last-name").mapNotNull { a.selectFirst(it)?.text()?.trim()?.ifBlank { null } }.joinToString(" ").ifBlank { a.selectFirst("nickname")?.text().orEmpty() } }.orEmpty()
        val book = EpubBuilder(title, authors.filter { it.isNotBlank() }.joinToString(", "), info?.selectFirst("lang")?.text()?.trim().orEmpty().ifBlank { "und" })
        info?.selectFirst("annotation")?.text()?.let { book.description = it }
        info?.select("genre")?.forEach { book.subjects += it.text() }
        info?.selectFirst("sequence")?.let { s -> book.series = s.attr("name"); s.attr("number").toDoubleOrNull()?.let { book.seriesIndex = it } }
        doc.selectFirst("publish-info > publisher")?.text()?.let { book.publisher = it }
        doc.selectFirst("publish-info > year")?.text()?.let { book.published = it }
        doc.selectFirst("publish-info > isbn")?.text()?.let { book.isbn = it }
        // Pictures (base64 "binary" elements).
        val images = mutableMapOf<String, String>()
        for (bin in doc.select("binary")) runCatching {
            val data = android.util.Base64.decode(bin.text().replace(Regex("\\s"), ""), android.util.Base64.DEFAULT)
            val (mime, ext) = EpubBuilder.sniffImage(data) ?: (bin.attr("content-type") to "jpg")
            images[bin.attr("id")] = book.resource("images/${bin.attr("id").replace(Regex("[^A-Za-z0-9_.-]"), "_")}.$ext", mime, data)
        }
        info?.selectFirst("coverpage image")?.let { img -> images[href(img).removePrefix("#")]?.let { book.coverPath = it } }

        val bodies = doc.select("FictionBook > body, body").distinct()
        val main = bodies.firstOrNull { it.attr("name").isBlank() } ?: bodies.firstOrNull()
        // Note ids, so links to notes point into the notes chapter.
        val noteFiles = mutableMapOf<String, String>()
        val sections = main?.children()?.filter { it.tagName() == "section" }.orEmpty()
        val chapterCount = sections.size.coerceAtLeast(1)
        bodies.filter { it != main }.forEach { b -> b.select("[id]").forEach { noteFiles[it.id()] = "c$chapterCount.xhtml" } }
        fun convert(node: Node): String = when (node) {
            is TextNode -> esc(node.text())
            is Element -> {
                val inner = node.childNodes().joinToString("") { convert(it) }
                val id = if (node.id().isNotBlank()) " id=\"${esc(node.id())}\"" else ""
                when (node.tagName()) {
                    "p" -> "<p$id>$inner</p>"
                    "title" -> "<h2$id>${node.children().joinToString("") { c -> c.childNodes().joinToString("") { convert(it) } + "<br/>" }.removeSuffix("<br/>")}</h2>"
                    "subtitle" -> "<h3$id>$inner</h3>"
                    "emphasis" -> "<em>$inner</em>"; "strong" -> "<strong>$inner</strong>"
                    "strikethrough" -> "<s>$inner</s>"; "sub" -> "<sub>$inner</sub>"; "sup" -> "<sup>$inner</sup>"; "code" -> "<code>$inner</code>"
                    "empty-line" -> "<p>&#160;</p>"
                    "epigraph" -> "<blockquote class=\"epigraph\"$id>$inner</blockquote>"
                    "cite" -> "<blockquote$id>$inner</blockquote>"
                    "poem" -> "<div class=\"poem\"$id>$inner</div>"; "stanza" -> "<div class=\"stanza\">$inner</div>"; "v" -> "<p class=\"v\">$inner</p>"
                    "text-author" -> "<p class=\"author\">$inner</p>"
                    "section" -> "<section$id>$inner</section>"
                    "image" -> images[href(node).removePrefix("#")]?.let { "<img src=\"$it\" alt=\"\"/>" } ?: ""
                    "a" -> {
                        val target = href(node)
                        val link = if (target.startsWith("#")) (noteFiles[target.removePrefix("#")] ?: "") + target else target
                        val note = node.attr("type") == "note"
                        "<a href=\"${esc(link)}\"${if (note) " epub:type=\"noteref\"" else ""}>$inner</a>"
                    }
                    "table" -> "<table>$inner</table>"; "tr" -> "<tr>$inner</tr>"; "td" -> "<td>$inner</td>"; "th" -> "<th>$inner</th>"
                    else -> inner
                }
            }
            else -> ""
        }
        val css = "<style>.epigraph{margin:1em 0 1em 30%;font-style:italic}.poem{margin:1em 2em}.v{margin:0;text-indent:0}.author{text-align:right;font-style:italic}</style>"
        if (sections.isEmpty() && main != null) book.chapter(title, css + convert(main))
        for (s in sections) {
            val head = s.selectFirst("> title")?.text()?.trim().orEmpty()
            book.chapter(head, css + convert(s))
        }
        bodies.filter { it != main }.forEach { b ->
            // Notes: each note an aside, so a tap shows it in a popup.
            val notes = b.select("section[id]").joinToString("") { n -> "<aside epub:type=\"footnote\" id=\"${esc(n.id())}\">${n.childNodes().joinToString("") { convert(it) }}</aside>" }
            book.chapter(b.selectFirst("> title")?.text()?.trim().orEmpty().ifBlank { "Notes" }, css + notes.ifBlank { convert(b) })
        }
        book.write(out)
    }

    private fun href(el: Element) = el.attributes().firstOrNull { it.key.endsWith("href") }?.value.orEmpty()

    private fun charsetOf(xml: ByteArray): String {
        val head = String(xml, 0, minOf(200, xml.size), Charsets.ISO_8859_1)
        return Regex("""encoding\s*=\s*["']([^"']+)["']""").find(head)?.groupValues?.get(1)?.takeIf { runCatching { Charset.forName(it) }.isSuccess } ?: "UTF-8"
    }

    // ---------- Word (DOCX) ----------

    fun docx(file: File, name: String, out: File) {
        ZipFile(file).use { zip ->
            fun read(path: String) = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { String(it.readBytes()) } }
            val core = read("docProps/core.xml")?.let { Jsoup.parse(it, "", Parser.xmlParser()) }
            val book = EpubBuilder(core?.selectFirst("dc|title")?.text().orEmpty().ifBlank { name.substringBeforeLast('.') }, core?.selectFirst("dc|creator")?.text().orEmpty())
            val rels = read("word/_rels/document.xml.rels")?.let { Jsoup.parse(it, "", Parser.xmlParser()) }
                ?.select("Relationship")?.associate { it.attr("Id") to it.attr("Target") }.orEmpty()
            val doc = Jsoup.parse(read("word/document.xml") ?: error("Not a Word document"), "", Parser.xmlParser())
            val parts = Splitter(book)
            for (p in doc.select("w|body > w|p, w|body > w|tbl")) {
                if (p.tagName() == "w:tbl") {
                    val rows = p.select("w|tr").joinToString("") { tr -> "<tr>" + tr.select("w|tc").joinToString("") { tc -> "<td>${esc(tc.text())}</td>" } + "</tr>" }
                    parts.add("<table>$rows</table>", 0); continue
                }
                val style = p.selectFirst("w|pPr w|pStyle")?.attr("w:val").orEmpty()
                val level = Regex("""(?i)heading\s?(\d)|^(\d)$|title""").find(style)?.let { m -> m.groupValues[1].toIntOrNull() ?: m.groupValues[2].toIntOrNull() ?: 1 } ?: 0
                val runs = StringBuilder()
                for (node in p.select("w|r, w|hyperlink > w|r")) {
                    val rpr = node.selectFirst("w|rPr")
                    val bold = rpr?.selectFirst("w|b")?.attr("w:val")?.let { it != "0" && it != "false" } ?: false
                    val italic = rpr?.selectFirst("w|i")?.attr("w:val")?.let { it != "0" && it != "false" } ?: false
                    val text = StringBuilder()
                    for (c in node.children()) when (c.tagName()) {
                        "w:t" -> text.append(esc(c.text()))
                        "w:tab" -> text.append(" ")
                        "w:br" -> text.append("<br/>")
                        "w:drawing", "w:pict" -> c.selectFirst("a|blip, v|imagedata")?.let { blip ->
                            val id = blip.attr("r:embed").ifBlank { blip.attr("r:id") }
                            rels[id]?.let { target ->
                                val path = "word/" + target.removePrefix("/word/").removePrefix("./")
                                zip.getEntry(path)?.let { e ->
                                    val data = zip.getInputStream(e).use { it.readBytes() }
                                    val src = book.resource("images/${target.substringAfterLast('/')}", EpubBuilder.mimeOf(target), data)
                                    text.append("<img src=\"$src\" alt=\"\"/>")
                                }
                            }
                        }
                    }
                    var t = text.toString()
                    if (italic) t = "<em>$t</em>"; if (bold) t = "<strong>$t</strong>"
                    runs.append(t)
                }
                val html = runs.toString()
                if (level > 0) parts.heading(level, html) else parts.add("<p>${html.ifBlank { "&#160;" }}</p>", html.length)
            }
            parts.finish()
            book.write(out)
        }
    }

    // ---------- OpenDocument (ODT) ----------

    fun odt(file: File, name: String, out: File) {
        ZipFile(file).use { zip ->
            fun read(path: String) = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { String(it.readBytes()) } }
            val meta = read("meta.xml")?.let { Jsoup.parse(it, "", Parser.xmlParser()) }
            val book = EpubBuilder(meta?.selectFirst("dc|title")?.text().orEmpty().ifBlank { name.substringBeforeLast('.') },
                meta?.selectFirst("meta|initial-creator, dc|creator")?.text().orEmpty())
            val doc = Jsoup.parse(read("content.xml") ?: error("Not an OpenDocument text"), "", Parser.xmlParser())
            // Bold and italic come from automatic styles.
            val bold = mutableSetOf<String>(); val italic = mutableSetOf<String>()
            for (st in doc.select("style|style")) {
                val props = st.selectFirst("style|text-properties") ?: continue
                if (props.attr("fo:font-weight") == "bold") bold += st.attr("style:name")
                if (props.attr("fo:font-style") == "italic") italic += st.attr("style:name")
            }
            fun inline(node: Node): String = when (node) {
                is TextNode -> esc(node.wholeText)
                is Element -> when (node.tagName()) {
                    "text:s" -> " ".repeat(node.attr("text:c").toIntOrNull() ?: 1)
                    "text:tab" -> " "
                    "text:line-break" -> "<br/>"
                    "text:span" -> {
                        var t = node.childNodes().joinToString("") { inline(it) }
                        val s = node.attr("text:style-name")
                        if (s in italic) t = "<em>$t</em>"; if (s in bold) t = "<strong>$t</strong>"; t
                    }
                    "draw:frame" -> node.selectFirst("draw|image")?.attr("xlink:href")?.let { p ->
                        zip.getEntry(p)?.let { e -> "<img src=\"${book.resource("images/" + p.substringAfterLast('/'), EpubBuilder.mimeOf(p), zip.getInputStream(e).use { it.readBytes() })}\" alt=\"\"/>" }
                    } ?: ""
                    "text:a" -> "<a href=\"${esc(node.attr("xlink:href"))}\">${node.childNodes().joinToString("") { inline(it) }}</a>"
                    "text:note" -> ""
                    else -> node.childNodes().joinToString("") { inline(it) }
                }
                else -> ""
            }
            val parts = Splitter(book)
            val text = doc.selectFirst("office|text") ?: error("Empty document")
            fun walk(el: Element) {
                for (c in el.children()) when (c.tagName()) {
                    "text:h" -> parts.heading(c.attr("text:outline-level").toIntOrNull() ?: 1, c.childNodes().joinToString("") { inline(it) })
                    "text:p" -> { val h = c.childNodes().joinToString("") { inline(it) }; parts.add("<p>${h.ifBlank { "&#160;" }}</p>", h.length) }
                    "text:list" -> c.select("text|list-item").forEach { li -> parts.add("<p>• ${li.select("text|p").joinToString(" ") { p -> p.childNodes().joinToString("") { inline(it) } }}</p>", 0) }
                    "table:table" -> parts.add("<table>" + c.select("table|table-row").joinToString("") { r -> "<tr>" + r.select("table|table-cell").joinToString("") { "<td>${esc(it.text())}</td>" } + "</tr>" } + "</table>", 0)
                    "text:section" -> walk(c)
                }
            }
            walk(text)
            parts.finish()
            book.write(out)
        }
    }

    // ---------- RTF ----------

    fun rtf(bytes: ByteArray, name: String, out: File) {
        val rtf = String(bytes, Charsets.ISO_8859_1)
        require(rtf.startsWith("{\\rtf")) { "Not an RTF document" }
        var codepage = Charset.forName("windows-1252")
        Regex("""\\ansicpg(\d+)""").find(rtf)?.groupValues?.get(1)?.let { cp -> runCatching { codepage = Charset.forName("windows-$cp") } }
        val skip = setOf("fonttbl", "colortbl", "stylesheet", "listtable", "listoverridetable", "pict", "header", "footer", "headerl", "headerr", "footerl", "footerr", "object", "themedata", "colorschememapping", "latentstyles", "datastore", "xmlnstbl", "rsidtbl", "generator", "fldinst")
        var title = ""; var author = ""
        val parts = Splitter(EpubBuilder(""))
        val para = StringBuilder()
        var bold = false; var italic = false; var ucSkip = 1
        data class State(val bold: Boolean, val italic: Boolean, val skipping: Boolean, val dest: String)
        val stack = ArrayDeque<State>()
        var skipping = false; var dest = ""
        val pendingBytes = java.io.ByteArrayOutputStream()
        fun flushBytes() { if (pendingBytes.size() > 0) { val t = String(pendingBytes.toByteArray(), codepage); pendingBytes.reset(); emit(t, para, bold, italic, skipping, dest).also { if (dest == "title") title += t; if (dest == "author") author += t } } }
        fun text(t: String) { flushBytes(); if (dest == "title") title += t else if (dest == "author") author += t; emit(t, para, bold, italic, skipping, dest) }
        fun endPara() { flushBytes(); val h = para.toString(); para.clear(); parts.add("<p>${h.ifBlank { "&#160;" }}</p>", h.length) }
        var i = 0
        var skipChars = 0
        while (i < rtf.length) {
            val ch = rtf[i]
            when {
                ch == '{' -> { flushBytes(); stack.addLast(State(bold, italic, skipping, dest)); i++ }
                ch == '}' -> { flushBytes(); stack.removeLastOrNull()?.let { bold = it.bold; italic = it.italic; skipping = it.skipping; dest = it.dest }; i++ }
                ch == '\\' -> {
                    val next = rtf.getOrNull(i + 1) ?: break
                    if (next == '\'') {
                        val hex = rtf.substring(i + 2, minOf(i + 4, rtf.length))
                        if (skipChars > 0) skipChars-- else hex.toIntOrNull(16)?.let { pendingBytes.write(it) }
                        i += 4; continue
                    }
                    if (!next.isLetter()) {
                        when (next) { '\\', '{', '}' -> text(next.toString()); '~' -> text("\u00A0"); '-' -> {}; '_' -> text("-"); '*' -> skipping = true }
                        i += 2; continue
                    }
                    var j = i + 1
                    while (j < rtf.length && rtf[j].isLetter()) j++
                    val word = rtf.substring(i + 1, j)
                    var k = j
                    if (k < rtf.length && (rtf[k] == '-' || rtf[k].isDigit())) { k++; while (k < rtf.length && rtf[k].isDigit()) k++ }
                    val param = rtf.substring(j, k).toIntOrNull()
                    if (k < rtf.length && rtf[k] == ' ') k++
                    i = k
                    when (word) {
                        in skip -> skipping = true
                        "info" -> dest = "info"
                        "title" -> if (dest == "info") dest = "title"
                        "author" -> if (dest == "info") dest = "author"
                        "par", "sect" -> if (!skipping && dest.isEmpty()) endPara()
                        "page" -> if (!skipping && dest.isEmpty()) { endPara(); parts.breakHere() }
                        "line" -> if (!skipping) { flushBytes(); para.append("<br/>") }
                        "tab" -> text(" ")
                        "b" -> { flushBytes(); bold = param != 0 }
                        "i" -> { flushBytes(); italic = param != 0 }
                        "plain" -> { flushBytes(); bold = false; italic = false }
                        "uc" -> ucSkip = param ?: 1
                        "u" -> { var cp = param ?: 0; if (cp < 0) cp += 65536; text(cp.toChar().toString()); skipChars = ucSkip }
                        "emdash" -> text("—"); "endash" -> text("–")
                        "lquote" -> text("‘"); "rquote" -> text("’"); "ldblquote" -> text("“"); "rdblquote" -> text("”"); "bullet" -> text("•")
                    }
                }
                ch == '\r' || ch == '\n' -> i++
                else -> {
                    if (skipChars > 0) skipChars-- else text(ch.toString())
                    i++
                }
            }
        }
        endPara()
        val book = parts.book
        book.title = title.trim().ifBlank { name.substringBeforeLast('.') }
        book.author = author.trim()
        parts.finish()
        book.write(out)
    }

    private fun emit(t: String, para: StringBuilder, bold: Boolean, italic: Boolean, skipping: Boolean, dest: String) {
        if (skipping || dest.isNotEmpty() || t.isEmpty()) return
        var s = esc(t)
        if (italic) s = "<em>$s</em>"; if (bold) s = "<strong>$s</strong>"
        para.append(s)
    }

    // ---------- Markdown, HTML, HTMLZ ----------

    fun markdown(bytes: ByteArray, name: String, out: File) {
        val text = TextBook.decode(bytes)
        val html = HtmlRenderer.builder().build().render(MarkdownParser.builder().build().parse(text))
        val title = Regex("""^#\s+(.+)$""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.trim() ?: name.substringBeforeLast('.')
        htmlBook(html, title, "", out)
    }

    fun html(bytes: ByteArray, name: String, out: File) {
        val doc = Jsoup.parse(ByteArrayInputStream(bytes), null, "")
        val title = doc.title().ifBlank { doc.selectFirst("h1")?.text().orEmpty() }.ifBlank { name.substringBeforeLast('.') }
        val author = doc.selectFirst("meta[name=author]")?.attr("content").orEmpty()
        doc.select("img").forEach { if (!it.attr("src").startsWith("data:")) it.remove() }
        htmlBook(doc.body().html(), title, author, out, doc.head().select("style").joinToString("") { it.outerHtml() })
    }

    fun htmlz(file: File, name: String, out: File) {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val index = entries.firstOrNull { it.name.lowercase().let { n -> n == "index.html" || n == "index.htm" } }
                ?: entries.firstOrNull { it.name.lowercase().endsWith(".html") || it.name.lowercase().endsWith(".htm") } ?: error("No HTML inside")
            val doc = Jsoup.parse(zip.getInputStream(index), null, "")
            val opf = entries.firstOrNull { it.name.endsWith(".opf") }?.let { e -> Jsoup.parse(zip.getInputStream(e), null, "", Parser.xmlParser()) }
            val book = EpubBuilder(opf?.selectFirst("dc|title")?.text().orEmpty().ifBlank { doc.title() }.ifBlank { name.substringBeforeLast('.') },
                opf?.select("dc|creator")?.joinToString(", ") { it.text() }.orEmpty())
            val base = index.name.substringBeforeLast('/', "")
            for (img in doc.select("img[src]")) {
                val src = img.attr("src"); val path = if (base.isEmpty()) src else "$base/$src"
                zip.getEntry(path)?.let { e -> img.attr("src", book.resource("images/" + src.substringAfterLast('/'), EpubBuilder.mimeOf(src), zip.getInputStream(e).use { it.readBytes() })) } ?: img.remove()
            }
            entries.firstOrNull { it.name.substringAfterLast('/').startsWith("cover.") && EpubBuilder.mimeOf(it.name).startsWith("image/") }?.let { e ->
                book.coverPath = book.resource("images/" + e.name.substringAfterLast('/'), EpubBuilder.mimeOf(e.name), zip.getInputStream(e).use { it.readBytes() })
            }
            splitHtml(book, doc.body().html(), doc.head().select("style").joinToString("") { it.outerHtml() })
            book.write(out)
        }
    }

    private fun htmlBook(html: String, title: String, author: String, out: File, css: String = "") {
        val book = EpubBuilder(title, author)
        splitHtml(book, html, css)
        book.write(out)
    }

    /** One chapter per h1 or h2 (or every ~40,000 characters if there are none). */
    private fun splitHtml(book: EpubBuilder, html: String, css: String) {
        val body = Jsoup.parseBodyFragment(html).body()
        val parts = Splitter(book, css)
        for (el in body.children()) {
            val tag = el.tagName()
            if (tag == "h1" || tag == "h2") parts.heading(if (tag == "h1") 1 else 2, el.html())
            else parts.add(el.outerHtml(), el.text().length)
        }
        parts.finish()
    }

    // ---------- Palm (PDB / PRC with PalmDOC text) ----------

    fun palm(bytes: ByteArray, name: String, out: File) {
        val type = String(bytes, 60, 8, Charsets.ISO_8859_1)
        if (type == "BOOKMOBI") { Mobi.toEpub(bytes, out, name.substringBeforeLast('.')); return }
        require(type == "TEXtREAd") { "This Palm book type isn’t supported (only PalmDOC and Mobipocket)." }
        val numRecords = ((bytes[76].toInt() and 0xFF) shl 8) or (bytes[77].toInt() and 0xFF)
        val offsets = (0 until numRecords).map { i -> java.nio.ByteBuffer.wrap(bytes, 78 + i * 8, 4).int }
        fun record(i: Int) = bytes.copyOfRange(offsets[i], if (i + 1 < numRecords) offsets[i + 1] else bytes.size)
        val rec0 = record(0)
        val compression = ((rec0[0].toInt() and 0xFF) shl 8) or (rec0[1].toInt() and 0xFF)
        val textRecords = ((rec0[8].toInt() and 0xFF) shl 8) or (rec0[9].toInt() and 0xFF)
        val text = java.io.ByteArrayOutputStream()
        for (i in 1..textRecords.coerceAtMost(numRecords - 1)) text.write(if (compression == 2) decompressPalmDOC(record(i)) else record(i))
        val title = String(bytes, 0, 32, Charsets.ISO_8859_1).trimEnd('\u0000').ifBlank { name.substringBeforeLast('.') }
        val tmp = File(out.parentFile, out.name + ".txt")
        tmp.writeBytes(text.toByteArray())
        try { TextBook.toEpub(tmp, title, out) } finally { tmp.delete() }
    }

    /** Collects paragraphs into chapters: at headings, or every ~40,000 characters. */
    private class Splitter(val book: EpubBuilder, val css: String = "") {
        private val buf = StringBuilder()
        private var title = ""
        private var size = 0
        fun heading(level: Int, html: String) {
            if (level <= 2 && (buf.isNotEmpty())) flush()
            if (level <= 2 && title.isEmpty()) title = Jsoup.parse(html).text()
            buf.append("<h${level.coerceIn(1, 6)}>$html</h${level.coerceIn(1, 6)}>")
        }
        fun add(html: String, length: Int) {
            buf.append(html); size += length
            if (size > CHUNK) flush()
        }
        fun breakHere() { if (buf.isNotEmpty()) flush() }
        private fun flush() {
            book.chapter(title, css + buf.toString())
            buf.clear(); size = 0; title = ""
        }
        fun finish() { if (buf.isNotEmpty() || book.chapters.isEmpty()) flush() }
    }
}
