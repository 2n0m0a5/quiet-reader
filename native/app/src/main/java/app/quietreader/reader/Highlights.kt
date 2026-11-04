package app.quietreader.reader

import android.graphics.Color
import androidx.annotation.ColorInt
import app.quietreader.data.Note
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.html.HtmlDecorationTemplate
import org.readium.r2.navigator.html.HtmlDecorationTemplates
import org.readium.r2.navigator.html.toCss
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.mediatype.MediaType

/** Strikethrough: a line through the middle of the text. */
@Parcelize
data class StrikeStyle(@ColorInt override val tint: Int, override val isActive: Boolean = false) :
    Decoration.Style, Decoration.Style.Tinted, Decoration.Style.Activable

/** A passage saved only as a quote: a thin dashed line under it, no colour. */
@Parcelize
data class PlainStyle(@ColorInt override val tint: Int, override val isActive: Boolean = false) :
    Decoration.Style, Decoration.Style.Tinted, Decoration.Style.Activable

/** Readium's highlight and underline, plus Quiet's strikethrough and quote styles. */
fun quietTemplates(): HtmlDecorationTemplates = HtmlDecorationTemplates.defaultTemplates(alpha = 0.35).apply {
    set(StrikeStyle::class, HtmlDecorationTemplate(layout = HtmlDecorationTemplate.Layout.BOXES, element = { d ->
        val c = ((d.style as? Decoration.Style.Tinted)?.tint ?: Color.RED).toCss(1.0)
        """<div style="background: linear-gradient(to bottom, transparent 47%, $c 47%, $c 57%, transparent 57%);"></div>"""
    }))
    set(PlainStyle::class, HtmlDecorationTemplate(layout = HtmlDecorationTemplate.Layout.BOXES, element = { d ->
        val c = ((d.style as? Decoration.Style.Tinted)?.tint ?: Color.GRAY).toCss(0.7)
        """<div style="border-bottom: 1.5px dashed $c; box-sizing: border-box;"></div>"""
    }))
}

private fun parse(hex: String) = runCatching { Color.parseColor(hex) }.getOrDefault(Color.YELLOW)

/**
 * Where a note is. Native notes keep Readium's locator; notes from 0.4.2 have the web app's
 * CFI and the text: the CFI names the chapter, and Readium finds the text in it.
 */
fun locatorOf(note: Note, publication: Publication): Locator? {
    note.locator?.let { json -> runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()?.let { return it } }
    val spine = Regex("""epubcfi\(/6/(\d+)""").find(note.cfi)?.groupValues?.get(1)?.toIntOrNull()?.let { it / 2 - 1 } ?: return null
    val link = publication.readingOrder.getOrNull(spine) ?: return null
    val base = publication.locatorFromLink(link) ?: Locator(link.url(), link.mediaType ?: MediaType.XHTML)
    return base.copy(
        title = note.label.ifBlank { base.title },
        locations = base.locations.copy(totalProgression = note.progress),
        text = Locator.Text(highlight = note.text.take(400)),
    )
}

fun decorationOf(note: Note, publication: Publication): Decoration? {
    if (note.isBookmark) return null
    val locator = locatorOf(note, publication) ?: return null
    val style: Decoration.Style = when (note.style) {
        "underline" -> Decoration.Style.Underline(parse(Note.inkOf(note.color)))
        "strike" -> StrikeStyle(parse(Note.inkOf(note.color)))
        "plain" -> PlainStyle(Color.GRAY)
        else -> Decoration.Style.Highlight(parse(note.color))
    }
    return Decoration(note.id, locator, style)
}
