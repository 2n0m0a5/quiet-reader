package app.quietreader.data

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Plain-text books are turned into a small EPUB, so they read like every other book: pages or
 * scroll, fonts, sizes, your place. The original .txt stays the book file (and goes in backups).
 */
object TextBook {
    private val heading = Regex("""^\s*(chapter|part|book|prologue|epilogue|глава|часть|пролог|эпилог|bob|qism)\b.{0,60}$""", RegexOption.IGNORE_CASE)

    fun toEpub(txt: File, title: String, out: File) {
        val text = decode(txt.readBytes()).replace("\r\n", "\n").replace('\r', '\n')
        // Paragraphs: blank lines if the text has them, otherwise every line.
        val paragraphs = (if (Regex("\n\\s*\n").containsMatchIn(text)) text.split(Regex("\n\\s*\n")) else text.split('\n'))
            .map { it.trim().replace(Regex("\\s*\n\\s*"), " ") }
            .filter { it.isNotEmpty() }

        // Chapters: at headings, or every ~40,000 characters at a paragraph break.
        val chapters = mutableListOf<Pair<String, MutableList<String>>>()
        var size = 0
        for (p in paragraphs) {
            val isHeading = p.length < 80 && heading.matches(p)
            if (chapters.isEmpty() || isHeading || size > 40_000) {
                chapters += (if (isHeading) p else if (chapters.isEmpty()) title else "${title} · ${chapters.size + 1}") to mutableListOf()
                size = 0
                if (isHeading) continue
            }
            chapters.last().second += p
            size += p.length
        }
        if (chapters.isEmpty()) chapters += title to mutableListOf("")

        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            val mimetype = "application/epub+zip".toByteArray()
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; this.size = mimetype.size.toLong(); compressedSize = this.size
                crc = CRC32().apply { update(mimetype) }.value
            })
            zip.write(mimetype); zip.closeEntry()
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
            }
            put("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            val items = chapters.indices.joinToString("") { """<item id="c$it" href="c$it.xhtml" media-type="application/xhtml+xml"/>""" }
            val spine = chapters.indices.joinToString("") { """<itemref idref="c$it"/>""" }
            put("OEBPS/content.opf", """<?xml version="1.0" encoding="utf-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">quiet-txt</dc:identifier><dc:title>${esc(title)}</dc:title><dc:language>und</dc:language><meta property="dcterms:modified">2026-01-01T00:00:00Z</meta></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>$items</manifest><spine>$spine</spine></package>""")
            val nav = chapters.withIndex().joinToString("") { (i, c) -> """<li><a href="c$i.xhtml">${esc(c.first)}</a></li>""" }
            put("OEBPS/nav.xhtml", page(title, """<nav epub:type="toc"><ol>$nav</ol></nav>"""))
            chapters.forEachIndexed { i, (name, body) ->
                val head = if (i == 0 && name == title) "" else "<h2>${esc(name)}</h2>"
                put("OEBPS/c$i.xhtml", page(name, head + body.joinToString("") { "<p>${esc(it)}</p>" }))
            }
        }
    }

    /** UTF-8 if the bytes are valid UTF-8, otherwise Windows-1251 (common for Russian and Uzbek .txt files). */
    fun decode(bytes: ByteArray): String {
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, start, bytes.size - start)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1251"))
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun page(title: String, body: String) =
        """<?xml version="1.0" encoding="utf-8"?><!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>${esc(title)}</title></head><body>$body</body></html>"""
}
