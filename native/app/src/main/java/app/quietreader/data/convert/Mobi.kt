package app.quietreader.data.convert

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.Inflater

/*
 * MOBI, AZW, AZW3 (KF8) and PRC books, turned into an EPUB.
 *
 * A port of foliate-js's mobi.js (MIT, by John Factotum; vendor/foliate-js/mobi.js in this repo),
 * the parser Quiet 0.4.2 used: Palm database records, the MOBI and EXTH headers, PalmDOC and
 * HUFF/CDIC compression, the INDX tables, MOBI6 text with filepos links and recindex pictures,
 * and KF8 skeletons and fragments with kindle:pos/flow/embed links.
 */

private fun u8(b: ByteArray, o: Int) = b[o].toInt() and 0xFF
private fun u16(b: ByteArray, o: Int) = (u8(b, o) shl 8) or u8(b, o + 1)
private fun u32(b: ByteArray, o: Int): Long = (u8(b, o).toLong() shl 24) or (u8(b, o + 1).toLong() shl 16) or (u8(b, o + 2).toLong() shl 8) or u8(b, o + 3).toLong()
private fun str(b: ByteArray, o: Int, n: Int) = String(b, o, minOf(n, b.size - o).coerceAtLeast(0), Charsets.ISO_8859_1)
private const val NONE = 0xFFFFFFFFL

/** Reads a variable-length number (7 bits a byte, the last byte has its high bit set). */
private fun varLen(b: ByteArray, start: Int): Pair<Long, Int> {
    var value = 0L; var length = 0
    for (i in start until minOf(start + 4, b.size)) {
        val byte = u8(b, i)
        value = (value shl 7) or (byte and 0x7F).toLong()
        length++
        if (byte and 0x80 != 0) break
    }
    return value to length
}

private fun varLenFromEnd(b: ByteArray): Long {
    var value = 0L
    for (i in maxOf(0, b.size - 4) until b.size) {
        val byte = u8(b, i)
        if (byte and 0x80 != 0) value = 0
        value = (value shl 7) or (byte and 0x7F).toLong()
    }
    return value
}

private fun bitsSet(x: Long): Int { var c = 0; var v = x; while (v > 0) { if (v and 1L == 1L) c++; v = v shr 1 }; return c }
private fun unsetEnd(x: Int): Int { var c = 0; var v = x; while (v and 1 == 0 && c < 32) { v = v shr 1; c++ }; return c }

/** PalmDOC compression (a simple LZ77). */
fun decompressPalmDOC(a: ByteArray): ByteArray {
    var out = ByteArray(maxOf(64, a.size * 3))
    var n = 0
    fun put(v: Int) { if (n == out.size) out = out.copyOf(out.size * 2); out[n++] = v.toByte() }
    var i = 0
    while (i < a.size) {
        val byte = u8(a, i)
        when {
            byte == 0 -> put(0)
            byte <= 8 -> { for (k in 1..byte) if (i + k < a.size) put(u8(a, i + k)); i += byte }
            byte <= 0x7F -> put(byte)
            byte <= 0xBF -> {
                val bytes = (byte shl 8) or (if (i + 1 < a.size) u8(a, i + 1) else 0); i++
                val distance = (bytes and 0x3FFF) ushr 3
                val length = (bytes and 7) + 3
                repeat(length) { val at = n - distance; put(if (at >= 0) out[at].toInt() and 0xFF else 0) }
            }
            else -> { put(32); put(byte xor 0x80) }
        }
        i++
    }
    return out.copyOf(n)
}

private class Pdb(val data: ByteArray) {
    val name = str(data, 0, 32).trimEnd('\u0000')
    val type = str(data, 60, 8)
    val numRecords = u16(data, 76)
    private val offsets = (0 until numRecords).map { u32(data, 78 + it * 8).toInt() }
    fun record(i: Int): ByteArray {
        require(i in 0 until numRecords) { "Record index out of bounds" }
        val start = offsets[i]; val end = if (i + 1 < numRecords) offsets[i + 1] else data.size
        return data.copyOfRange(start, end.coerceAtLeast(start))
    }
    fun magic(i: Int) = record(i).let { str(it, 0, 4) }
}

class MobiBook(
    val title: String, val authors: List<String>, val publisher: String, val language: String, val description: String,
    val subjects: List<String>, val isbn: String, val date: String, val cover: ByteArray?,
)

object Mobi {
    private val LANG = mapOf(
        1 to "ar", 2 to "bg", 3 to "ca", 4 to "zh", 5 to "cs", 6 to "da", 7 to "de", 8 to "el", 9 to "en", 10 to "es", 11 to "fi",
        12 to "fr", 13 to "he", 14 to "hu", 15 to "is", 16 to "it", 17 to "ja", 18 to "ko", 19 to "nl", 20 to "no", 21 to "pl", 22 to "pt",
        24 to "ro", 25 to "ru", 26 to "hr", 27 to "sk", 28 to "sq", 29 to "sv", 30 to "th", 31 to "tr", 32 to "ur", 33 to "id", 34 to "uk",
        35 to "be", 36 to "sl", 37 to "et", 38 to "lv", 39 to "lt", 41 to "fa", 42 to "vi", 43 to "hy", 44 to "az", 45 to "eu", 47 to "mk",
        55 to "ka", 57 to "hi", 63 to "kk", 67 to "uz", 68 to "tt",
    )

    private class Headers(rec0: ByteArray) {
        val compression = u16(rec0, 0)
        val numTextRecords = u16(rec0, 8)
        val encryption = u16(rec0, 12)
        val magic = str(rec0, 16, 4)
        val length = u32(rec0, 20).toInt()
        val encoding = u32(rec0, 28).toInt()
        val uid = u32(rec0, 32)
        val version = u32(rec0, 36).toInt()
        val titleOffset = u32(rec0, 84).toInt(); val titleLength = u32(rec0, 88).toInt()
        val localeRegion = u8(rec0, 94); val localeLanguage = u8(rec0, 95)
        val resourceStart = u32(rec0, 108).toInt()
        val huffcdic = u32(rec0, 112).toInt(); val numHuffcdic = u32(rec0, 116).toInt()
        val exthFlag = u32(rec0, 128)
        val trailingFlags = if (rec0.size > 244) u32(rec0, 240) else 0L
        val indx = if (rec0.size > 248) u32(rec0, 244) else NONE
        // KF8
        val fdst = if (rec0.size > 196) u32(rec0, 192) else NONE
        val frag = if (rec0.size > 252) u32(rec0, 248) else NONE
        val skel = if (rec0.size > 256) u32(rec0, 252) else NONE
        val guide = if (rec0.size > 264) u32(rec0, 260) else NONE
        val charset: Charset = if (encoding == 65001) Charsets.UTF_8 else Charset.forName("windows-1252")
        val title = if (titleOffset > 0 && titleOffset + titleLength <= rec0.size) String(rec0, titleOffset, titleLength, charset) else ""
        val language = LANG[localeLanguage] ?: ""
        val exth: Map<Int, List<ByteArray>> = if (exthFlag and 0x40L != 0L && length + 16 + 12 <= rec0.size) readExth(rec0, length + 16) else emptyMap()
        fun s(type: Int) = exth[type]?.firstOrNull()?.let { String(it, charset) }
        fun all(type: Int) = exth[type]?.map { String(it, charset) } ?: emptyList()
        fun n(type: Int): Long? = exth[type]?.firstOrNull()?.takeIf { it.size >= 4 }?.let { u32(it, 0) }

        private fun readExth(b: ByteArray, at: Int): Map<Int, List<ByteArray>> {
            if (str(b, at, 4) != "EXTH") return emptyMap()
            val count = u32(b, at + 8).toInt()
            val out = mutableMapOf<Int, MutableList<ByteArray>>()
            var o = at + 12
            repeat(count) {
                if (o + 8 > b.size) return out
                val type = u32(b, o).toInt(); val len = u32(b, o + 4).toInt()
                if (len < 8 || o + len > b.size) return out
                out.getOrPut(type) { mutableListOf() } += b.copyOfRange(o + 8, o + len)
                o += len
            }
            return out
        }
    }

    private class Reader(val pdb: Pdb, var start: Int, var h: Headers) {
        lateinit var decompress: (ByteArray) -> ByteArray
        private var trailing: (ByteArray) -> ByteArray = { it }
        val resourceStart get() = h.resourceStart

        fun setup() {
            decompress = when (h.compression) {
                1 -> { b -> b }
                2 -> ::decompressPalmDOC
                17480 -> huffcdic()
                else -> throw IllegalStateException("Unknown compression")
            }
            val multibyte = h.trailingFlags and 1L != 0L
            val entries = bitsSet(h.trailingFlags ushr 1)
            trailing = { arr0 ->
                var arr = arr0
                repeat(entries) { val len = varLenFromEnd(arr).toInt(); arr = arr.copyOfRange(0, (arr.size - len).coerceAtLeast(0)) }
                if (multibyte && arr.isNotEmpty()) { val len = (u8(arr, arr.size - 1) and 3) + 1; arr = arr.copyOfRange(0, (arr.size - len).coerceAtLeast(0)) }
                arr
            }
        }

        fun record(i: Int) = pdb.record(start + i)
        fun magic(i: Int) = pdb.magic(start + i)
        fun text(i: Int) = decompress(trailing(record(i + 1)))
        fun resource(i: Int): ByteArray {
            val buf = pdb.record(resourceStart + i)
            return when (str(buf, 0, 4)) {
                "FONT" -> font(buf)
                "VIDE", "AUDI" -> buf.copyOfRange(12, buf.size)
                else -> buf
            }
        }
        fun decode(b: ByteArray) = String(b, h.charset)

        private fun font(buf: ByteArray): ByteArray {
            val flags = u32(buf, 8).toInt(); val dataStart = u32(buf, 12).toInt()
            val keyLength = u32(buf, 16).toInt(); val keyStart = u32(buf, 20).toInt()
            val array = buf.copyOfRange(dataStart, buf.size)
            if (flags and 2 != 0) {
                val bytes = if (keyLength == 16) 1024 else 1040
                val key = buf.copyOfRange(keyStart, keyStart + keyLength)
                for (i in 0 until minOf(bytes, array.size)) array[i] = (array[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            if (flags and 1 != 0) runCatching {
                val inf = Inflater(); inf.setInput(array)
                val out = ByteArrayOutputStream(); val tmp = ByteArray(65536)
                while (!inf.finished()) { val n = inf.inflate(tmp); if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break; out.write(tmp, 0, n) }
                return out.toByteArray()
            }
            return array
        }

        private fun huffcdic(): (ByteArray) -> ByteArray {
            val huff = record(h.huffcdic)
            require(str(huff, 0, 4) == "HUFF") { "Invalid HUFF record" }
            val off1 = u32(huff, 8).toInt(); val off2 = u32(huff, 12).toInt()
            val table1 = (0 until 256).map { val x = u32(huff, off1 + it * 4); Triple(x and 0x80L != 0L, (x and 0x1F).toInt(), x ushr 8) }
            val table2 = listOf(0L to 0L) + (0 until 32).map { u32(huff, off2 + it * 8) to u32(huff, off2 + it * 8 + 4) }
            val dict = mutableListOf<Pair<ByteArray, Boolean>>()
            for (i in 1 until h.numHuffcdic) {
                val rec = record(h.huffcdic + i)
                require(str(rec, 0, 4) == "CDIC") { "Invalid CDIC record" }
                val len = u32(rec, 4).toInt(); val numEntries = u32(rec, 8).toInt(); val codeLength = u32(rec, 12).toInt()
                val n = minOf(1 shl codeLength, numEntries - dict.size)
                val buffer = rec.copyOfRange(len, rec.size)
                for (k in 0 until n) {
                    val o = u16(buffer, k * 2); val x = u16(buffer, o)
                    dict += buffer.copyOfRange(o + 2, o + 2 + (x and 0x7FFF)) to (x and 0x8000 != 0)
                }
            }
            fun read32(b: ByteArray, from: Long): Long {
                val startByte = (from shr 3).toInt(); val end = from + 32; val endByte = (end shr 3).toInt()
                var bits = java.math.BigInteger.ZERO
                for (i in startByte..endByte) bits = bits.shiftLeft(8).or(java.math.BigInteger.valueOf(if (i < b.size) u8(b, i).toLong() else 0))
                return bits.shiftRight((8 - (end and 7)).toInt()).toLong() and 0xFFFFFFFFL
            }
            lateinit var decomp: (ByteArray) -> ByteArray
            decomp = { b ->
                val out = ByteArrayOutputStream()
                val bitLength = b.size.toLong() * 8
                var i = 0L
                while (i < bitLength) {
                    val bits = read32(b, i)
                    var (found, codeLength, value) = table1[(bits ushr 24).toInt()]
                    if (!found) {
                        while (codeLength < 32 && (bits ushr (32 - codeLength)) < table2[codeLength].first) codeLength++
                        value = table2[codeLength].second
                    }
                    i += codeLength
                    if (i > bitLength) break
                    val code = (value - (bits ushr (32 - codeLength))).toInt()
                    if (code !in dict.indices) break
                    var (result, done) = dict[code]
                    if (!done) { result = decomp(result); dict[code] = result to true }
                    out.write(result)
                }
                out.toByteArray()
            }
            return decomp
        }
    }

    private class Index(val table: List<Pair<String, Map<Int, List<Long>>>>, val cncx: Map<Int, String>)

    private fun index(r: Reader, at: Int): Index {
        val rec = r.record(at)
        require(str(rec, 0, 4) == "INDX") { "Invalid INDX record" }
        val length = u32(rec, 4).toInt(); val numRecords = u32(rec, 24).toInt(); val encoding = u32(rec, 28).toInt()
        val numCncx = u32(rec, 52).toInt()
        val cs: Charset = if (encoding == 65001) Charsets.UTF_8 else Charset.forName("windows-1252")
        val tagx = rec.copyOfRange(length, rec.size)
        require(str(tagx, 0, 4) == "TAGX") { "Invalid TAGX section" }
        val tagxLen = u32(tagx, 4).toInt(); val controlBytes = u32(tagx, 8).toInt()
        val tags = (0 until (tagxLen - 12) / 4).map { i -> IntArray(4) { u8(tagx, 12 + i * 4 + it) } }
        val cncx = mutableMapOf<Int, String>()
        var cncxOffset = 0
        for (i in 0 until numCncx) {
            val c = r.record(at + numRecords + i + 1)
            var pos = 0
            while (pos < c.size) {
                val idx = pos
                val (value, len) = varLen(c, pos); pos += len
                val end = minOf(c.size, pos + value.toInt())
                cncx[cncxOffset + idx] = String(c, pos, end - pos, cs)
                pos = end
            }
            cncxOffset += 0x10000
        }
        val table = mutableListOf<Pair<String, Map<Int, List<Long>>>>()
        for (i in 0 until numRecords) {
            val record = r.record(at + 1 + i)
            require(str(record, 0, 4) == "INDX") { "Invalid INDX record" }
            val idxt = u32(record, 20).toInt(); val n = u32(record, 24).toInt()
            for (j in 0 until n) {
                val offset = u16(record, idxt + 4 + 2 * j)
                val nameLen = u8(record, offset)
                val name = str(record, offset + 1, nameLen)
                val found = mutableListOf<IntArray>() // tag, valueCount(-1 null), valueBytes(-1 null), numValues
                val startPos = offset + 1 + nameLen
                var controlByteIndex = 0
                var pos = startPos + controlBytes
                for ((tag, numValues, mask, end) in tags.map { listOf(it[0], it[1], it[2], it[3]) }) {
                    if (end and 1 != 0) { controlByteIndex++; continue }
                    val value = u8(record, startPos + controlByteIndex) and mask
                    if (value == mask) {
                        if (bitsSet(mask.toLong()) > 1) {
                            val (v, l) = varLen(record, pos); found += intArrayOf(tag, -1, v.toInt(), numValues); pos += l
                        } else found += intArrayOf(tag, 1, -1, numValues)
                    } else found += intArrayOf(tag, value shr unsetEnd(mask), -1, numValues)
                }
                val map = mutableMapOf<Int, List<Long>>()
                for ((tag, valueCount, valueBytes, numValues) in found.map { listOf(it[0], it[1], it[2], it[3]) }) {
                    val values = mutableListOf<Long>()
                    if (valueCount != -1) {
                        repeat(valueCount * numValues) { val (v, l) = varLen(record, pos); values += v; pos += l }
                    } else {
                        var count = 0
                        while (count < valueBytes) { val (v, l) = varLen(record, pos); values += v; pos += l; count += l }
                    }
                    map[tag] = values
                }
                table += name to map
            }
        }
        return Index(table, cncx)
    }

    /** The book's details, without converting it (for a quick look). */
    fun isMobi(bytes: ByteArray) = bytes.size > 68 && str(bytes, 60, 8) == "BOOKMOBI"

    /** Converts a MOBI/AZW/AZW3/PRC file into an EPUB at [out]. Returns its details. */
    fun toEpub(bytes: ByteArray, out: java.io.File, fallbackTitle: String): MobiBook {
        val pdb = Pdb(bytes)
        require(str(bytes, 60, 8) == "BOOKMOBI") { "Not a MOBI book" }
        var h = Headers(pdb.record(0))
        require(h.magic == "MOBI") { "Missing MOBI header" }
        require(h.encryption == 0) { "This book is protected (DRM)." }
        val r = Reader(pdb, 0, h)
        var kf8 = h.version >= 8
        if (!kf8) {
            val boundary = h.n(121)
            if (boundary != null && boundary < NONE) runCatching {
                val k = Headers(pdb.record(boundary.toInt()))
                require(k.magic == "MOBI")
                r.start = boundary.toInt(); r.h = k; h = k; kf8 = true
            }
        }
        r.setup()

        val cs = h.charset
        val meta = MobiBook(
            title = unescape(h.s(503) ?: h.title).ifBlank { fallbackTitle },
            authors = h.all(100).map(::unescape),
            publisher = unescape(h.s(101) ?: ""), language = h.all(524).firstOrNull() ?: h.language,
            description = unescape(h.s(103) ?: ""), subjects = h.all(105).map(::unescape),
            isbn = h.s(104) ?: "", date = h.s(106)?.take(10) ?: "",
            cover = (h.n(201)?.takeIf { it < NONE } ?: h.n(202)?.takeIf { it < NONE })?.let { runCatching { r.resource(it.toInt()) }.getOrNull() },
        )
        val book = EpubBuilder(meta.title, meta.authors.joinToString(", "), meta.language.ifBlank { "und" }).apply {
            publisher = meta.publisher; description = meta.description; published = meta.date; isbn = meta.isbn
            subjects += meta.subjects
            if (h.s(527) == "rtl") rtl = true
        }
        meta.cover?.let { c -> EpubBuilder.sniffImage(c)?.let { (mime, ext) -> book.coverPath = book.resource("images/cover.$ext", mime, c) } }
        if (kf8) Kf8(r, book, cs).run() else Mobi6(r, book).run()
        book.write(out)
        return meta
    }

    private fun unescape(s: String) = if (s.contains('&')) Parser.unescapeEntities(s, false) else s

    // ---------- MOBI6 ----------

    private class Mobi6(val r: Reader, val book: EpubBuilder) {
        private val pagebreak = Regex("""<\s*(?:mbp:)?pagebreak[^>]*>""", RegexOption.IGNORE_CASE)
        private val filepos = Regex("""<[^<>]+filepos=['"]?(\d+)[^<>]*>""", RegexOption.IGNORE_CASE)

        fun run() {
            val all = ByteArrayOutputStream()
            for (i in 0 until r.h.numTextRecords) all.write(r.text(i))
            val array = all.toByteArray()
            val raw = String(array, Charsets.ISO_8859_1) // one char a byte: filepos are byte offsets
            val starts = listOf(0) + pagebreak.findAll(raw).map { it.range.first }.toList()
            val sections = starts.mapIndexed { i, s -> s to (starts.getOrNull(i + 1) ?: array.size) }
            val positions = filepos.findAll(raw).map { it.groupValues[1].toLong() }.toSortedSet()
            fun sectionOf(pos: Long) = sections.indexOfFirst { it.second > pos }.coerceAtLeast(0)
            val images = mutableMapOf<Int, String>()
            fun image(recindex: Int): String? = images.getOrPut(recindex) {
                val bytes = runCatching { r.resource(recindex - 1) }.getOrNull() ?: return null
                val (mime, ext) = EpubBuilder.sniffImage(bytes) ?: return null
                book.resource("images/img$recindex.$ext", mime, bytes)
            }
            sections.forEachIndexed { index, (start, end) ->
                // Anchors at each filepos inside this section.
                val out = ByteArrayOutputStream()
                var at = start
                for (p in positions.filter { it >= start && it < end }) {
                    out.write(array, at, (p - at).toInt()); out.write("<a id=\"filepos$p\"></a>".toByteArray()); at = p.toInt()
                }
                out.write(array, at, end - at)
                val html = r.decode(out.toByteArray()).replace(pagebreak, "")
                val doc = Jsoup.parse(html)
                for (img in doc.select("img[recindex]")) {
                    val src = img.attr("recindex").toIntOrNull()?.let(::image)
                    if (src != null) img.attr("src", src).removeAttr("recindex") else img.remove()
                }
                for (a in doc.select("[filepos]")) {
                    val p = a.attr("filepos").toLongOrNull() ?: continue
                    a.attr("href", "c${sectionOf(p)}.xhtml#filepos$p").removeAttr("filepos")
                }
                book.chapter("", "<style>blockquote{margin:0 0 0 1em}</style>" + doc.body().html())
            }
            // Contents: the guide's "toc" page lists filepos links.
            runCatching {
                val first = Jsoup.parse(r.decode(array.copyOfRange(sections[0].first, sections[0].second)))
                val tocPos = first.select("reference").firstOrNull { it.attr("type").contains("toc") }?.attr("filepos")?.toLongOrNull()
                if (tocPos != null) {
                    val s = sections[sectionOf(tocPos)]
                    val tocDoc = Jsoup.parse(r.decode(array.copyOfRange(s.first, s.second)))
                    book.toc = tocDoc.select("a[filepos]").mapNotNull { a ->
                        val p = a.attr("filepos").toLongOrNull() ?: return@mapNotNull null
                        EpubBuilder.Toc(a.text().trim(), "c${sectionOf(p)}.xhtml#filepos$p")
                    }.filter { it.label.isNotBlank() }
                }
            }
        }
    }

    // ---------- KF8 (AZW3) ----------

    private class Kf8(val r: Reader, val book: EpubBuilder, val cs: Charset) {
        private val resourceRegex = Regex("""kindle:(flow|embed):(\w+)(?:\?mime=(\w+/[-+.\w]+))?""")
        private val posRegex = Regex("""kindle:pos:fid:(\w+):off:(\w+)""")
        private lateinit var raw: ByteArray
        private var fdst: List<Pair<Int, Int>> = emptyList()
        private val resources = mutableMapOf<String, String>()

        private class Frag(val insertOffset: Int, val index: Int, val offset: Int, val length: Int)
        private class Skel(val numFrag: Int, val offset: Int, val length: Int)
        private class Section(val skel: Skel, val frags: List<Frag>)

        fun run() {
            val all = ByteArrayOutputStream()
            for (i in 0 until r.h.numTextRecords) all.write(r.text(i))
            raw = all.toByteArray()
            runCatching {
                val f = r.record(r.h.fdst.toInt())
                if (str(f, 0, 4) == "FDST") fdst = (0 until u32(f, 8).toInt()).map { u32(f, 12 + it * 8).toInt() to u32(f, 16 + it * 8).toInt() }
            }
            val skelTable = index(r, r.h.skel.toInt()).table.map { (_, t) -> Skel(t[1]!![0].toInt(), t[6]!![0].toInt(), t[6]!![1].toInt()) }
            val fragIndex = index(r, r.h.frag.toInt())
            val fragTable = fragIndex.table.map { (name, t) -> Frag(name.toIntOrNull() ?: 0, t[4]!![0].toInt(), t[6]!![0].toInt(), t[6]!![1].toInt()) }
            var fragStart = 0
            val sections = skelTable.map { skel ->
                val frags = fragTable.subList(fragStart.coerceAtMost(fragTable.size), (fragStart + skel.numFrag).coerceAtMost(fragTable.size))
                fragStart += skel.numFrag
                Section(skel, frags)
            }
            val fixed = r.h.s(122) == "true"
            val viewport = r.h.s(126)?.split('x')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size >= 2 }?.let { it[0] to it[1] }
            if (fixed) book.fixedLayout = viewport ?: (1072 to 1448)

            // Assemble each section: the skeleton with its fragments inserted.
            val texts = sections.map { s ->
                val end = minOf(raw.size, s.skel.offset + s.skel.length + s.frags.sumOf { it.length })
                val part = raw.copyOfRange(s.skel.offset.coerceAtMost(raw.size), end)
                var skeleton = part.copyOfRange(0, minOf(s.skel.length, part.size))
                for (f in s.frags) {
                    val insert = (f.insertOffset - s.skel.offset).coerceIn(0, skeleton.size)
                    val from = (s.skel.length + f.offset).coerceIn(0, part.size)
                    val fragRaw = part.copyOfRange(from, minOf(part.size, from + f.length))
                    skeleton = skeleton.copyOfRange(0, insert) + fragRaw + skeleton.copyOfRange(insert, skeleton.size)
                }
                String(skeleton, cs)
            }
            // Where each kindle:pos link points: the fragment, an offset in it, and the id/name/aid found there.
            fun target(fid: Int, off: Int): Pair<Int, String?> {
                val si = sections.indexOfFirst { s -> s.frags.any { it.index == fid } }
                if (si < 0) return -1 to null
                val s = sections[si]; val f = s.frags.first { it.index == fid }
                val start = s.skel.offset + s.skel.length + f.offset + off
                val str = String(raw, start.coerceIn(0, raw.size), (f.length - off).coerceIn(0, raw.size - start.coerceIn(0, raw.size)), cs)
                val m = Regex("""\s(id|name|aid)\s*=\s*['"]([^'"]*)['"]""", RegexOption.IGNORE_CASE).find(str)
                return si to m?.let { "${it.groupValues[1].lowercase()}=${it.groupValues[2]}" }
            }
            val docs = texts.map { t -> Jsoup.parse(replaceResources(t)) }
            fun href(fid: Int, off: Int): String {
                val (si, sel) = target(fid, off)
                if (si < 0) return "#"
                val doc = docs[si]
                val id = sel?.let { s ->
                    val (attr, value) = s.split('=', limit = 2)
                    val el = doc.selectFirst("[$attr=\"${value.replace("\"", "")}\"]")
                    el?.let { e -> e.id().ifBlank { "q-${attr}-$value".replace(Regex("[^A-Za-z0-9_-]"), "_").also { e.attr("id", it) } } }
                }
                return "c$si.xhtml" + (id?.let { "#$it" } ?: "")
            }
            for (doc in docs) for (a in doc.select("[href^=kindle:pos]")) {
                posRegex.find(a.attr("href"))?.let { m -> a.attr("href", href(m.groupValues[1].toInt(32), m.groupValues[2].toInt(32))) }
            }
            // Contents, from the NCX index (before writing, so the ids it needs are in the pages).
            runCatching {
                if (r.h.indx < NONE) {
                    val ncx = index(r, r.h.indx.toInt())
                    data class Item(val i: Int, val label: String, val pos: List<Long>?, val level: Int, val parent: Int?)
                    val items = ncx.table.mapIndexed { i, (_, t) ->
                        Item(i, unescape(ncx.cncx[t[3]?.firstOrNull()?.toInt() ?: -1] ?: ""), t[6], t[4]?.firstOrNull()?.toInt() ?: 0, t[21]?.firstOrNull()?.toInt())
                    }
                    fun node(item: Item): EpubBuilder.Toc = EpubBuilder.Toc(
                        item.label, item.pos?.let { href(it[0].toInt(), it.getOrElse(1) { 0L }.toInt()) } ?: "c0.xhtml",
                        items.filter { it.parent == item.i }.map(::node),
                    )
                    book.toc = items.filter { it.level == 0 }.map(::node)
                }
            }
            docs.forEach { doc ->
                val css = doc.head().select("link[rel=stylesheet]").map { it.attr("href") }
                val title = doc.title()
                if (fixed) book.rawChapter(title, EpubBuilder.page(title, cleanBody(doc), css, book.fixedLayout))
                else book.chapter(title, doc.outerHtml(), css)
            }
        }

        private fun cleanBody(doc: Document): String {
            doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml).escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml).prettyPrint(false)
            return doc.body().html()
        }

        private fun flow(i: Int): ByteArray? = fdst.getOrNull(i)?.let { (a, b) -> raw.copyOfRange(a.coerceIn(0, raw.size), b.coerceIn(0, raw.size)) }

        /** kindle:embed (pictures, fonts) and kindle:flow (stylesheets) become files in the EPUB. */
        fun replaceResources(text: String, prefix: String = ""): String = resourceRegex.replace(text) { m ->
            val key = m.value
            prefix + (resources[key] ?: run {
                val type = m.groupValues[1]; val id = m.groupValues[2].toInt(32); val mime = m.groupValues[3]
                val path = if (type == "flow") {
                    val data = flow(id) ?: return@run "#"
                    val css = replaceResources(String(data, cs), prefix = "../")
                    book.resource("styles/flow$id.css", "text/css", css.toByteArray())
                } else {
                    val data = runCatching { r.resource(id - 1) }.getOrNull() ?: return@run "#"
                    val sniff = EpubBuilder.sniffImage(data)
                    val ext = sniff?.second ?: when {
                        mime.contains("font") || String(data, 0, minOf(4, data.size), Charsets.ISO_8859_1).let { it == "OTTO" || it == "\u0000\u0001\u0000\u0000" } ->
                            if (String(data, 0, 4, Charsets.ISO_8859_1) == "OTTO") "otf" else "ttf"
                        else -> "bin"
                    }
                    book.resource("res/r$id.$ext", sniff?.first ?: EpubBuilder.mimeOf("x.$ext"), data)
                }
                // Paths are relative to the chapter (OEBPS/); a stylesheet (OEBPS/styles/) adds "../".
                path.also { resources[key] = it }
            })
        }
    }
}
