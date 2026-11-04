package app.quietreader.data.convert

import app.quietreader.data.BookFormat
import app.quietreader.data.TextBook
import java.io.File

/** Turns any book Quiet reads (but EPUB and PDF) into an EPUB, kept in files/derived. */
object Converters {
    fun toEpub(src: File, fileName: String, out: File) {
        val name = fileName.ifBlank { src.name }
        when (BookFormat.of(name, null)) {
            BookFormat.Txt -> TextBook.toEpub(src, name.substringBeforeLast('.'), out)
            BookFormat.Mobi -> Mobi.toEpub(src.readBytes(), out, name.substringBeforeLast('.'))
            BookFormat.Fb2 -> Documents.fb2(src.readBytes(), name, out)
            BookFormat.Docx -> Documents.docx(src, name, out)
            BookFormat.Odt -> Documents.odt(src, name, out)
            BookFormat.Rtf -> Documents.rtf(src.readBytes(), name, out)
            BookFormat.Markdown -> Documents.markdown(src.readBytes(), name, out)
            BookFormat.Html -> if (name.lowercase().endsWith(".htmlz")) Documents.htmlz(src, name, out) else Documents.html(src.readBytes(), name, out)
            BookFormat.Palm -> Documents.palm(src.readBytes(), name, out)
            BookFormat.Comic -> Comics.toEpub(src, name, out)
            else -> error("Not a converted format")
        }
    }
}
