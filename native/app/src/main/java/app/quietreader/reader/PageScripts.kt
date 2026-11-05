package app.quietreader.reader

import android.util.Base64
import android.webkit.JavascriptInterface
import app.quietreader.data.Fonts

/**
 * A small script run in each book page (Readium draws EPUB pages in a web view):
 *   - pictures: a tap on an image opens it full screen (through [PageBridge.openImage]);
 *   - bionic reading: the start of each word in bold, and back;
 *   - your own font: its @font-face, from the file on the phone (through [PageBridge.font]).
 * It's safe to run again and again: each part checks what's already done.
 */
object PageScripts {
    const val BRIDGE = "QuietPage"

    fun script(bionic: Boolean, fontFamily: String?, scroll: Boolean = false): String = """
(function(){
  var Q = window.__quiet || (window.__quiet = {});
  // One sheet of paper: Quiet's page colour shows through a book's own white page (some books
  // paint the body white with margins, a white card on the page, and white under Night text).
  if (!Q.paper) {
    Q.paper = true;
    var st = document.createElement('style');
    st.textContent = 'body, body > div, body > section, body > article { background-color: transparent !important; background-image: none !important; }';
    (document.head || document.documentElement).appendChild(st);
  }
  // Pictures
  if (!Q.images) {
    Q.images = true;
    document.addEventListener('click', function(e){
      var t = e.target;
      if (!t || !t.tagName) return;
      var tag = t.tagName.toLowerCase();
      if ((tag === 'img' || tag === 'image') && !(t.closest && t.closest('a'))) {
        var src = t.currentSrc || t.src || (t.href && t.href.baseVal) || t.getAttribute('xlink:href');
        if (!src) return;
        e.preventDefault(); e.stopPropagation();
        $BRIDGE.openImage(new URL(src, location.href).href);
      }
    }, true);
  }
  // Bionic reading
  var wantBionic = $bionic;
  if (wantBionic && !Q.bionic) {
    Q.bionic = true;
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode: function(n){
        var p = n.parentNode;
        if (!p || /^(script|style|b|strong|code|pre)$/i.test(p.nodeName) || (p.hasAttribute && p.hasAttribute('data-qb'))) return NodeFilter.FILTER_REJECT;
        return /\S/.test(n.nodeValue) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
      }
    });
    var nodes = []; while (walker.nextNode()) nodes.push(walker.currentNode);
    nodes.forEach(function(n){
      var span = document.createElement('span');
      span.setAttribute('data-qb', '1');
      span.__text = n.nodeValue;
      n.nodeValue.split(/(\s+)/).forEach(function(w){
        var m = w.match(/^([^\p{L}\p{N}]*)([\p{L}\p{N}][\p{L}\p{N}'’-]*)(.*)$/u);
        if (!m) { span.appendChild(document.createTextNode(w)); return; }
        var word = m[2], cut = Math.ceil(word.length * 0.45);
        span.appendChild(document.createTextNode(m[1]));
        var b = document.createElement('b'); b.textContent = word.slice(0, cut); span.appendChild(b);
        span.appendChild(document.createTextNode(word.slice(cut) + m[3]));
      });
      n.parentNode.replaceChild(span, n);
    });
  } else if (!wantBionic && Q.bionic) {
    Q.bionic = false;
    document.querySelectorAll('span[data-qb]').forEach(function(s){
      s.parentNode.replaceChild(document.createTextNode(s.__text != null ? s.__text : s.textContent), s);
    });
  }
  // Your font
  var family = ${fontFamily?.let { "'" + it.replace("'", "\\'") + "'" } ?: "null"};
  if (family && Q.font !== family) {
    Q.font = family;
    var old = document.getElementById('quiet-font'); if (old) old.remove();
    var css = $BRIDGE.font(family);
    if (css) { var st = document.createElement('style'); st.id = 'quiet-font'; st.textContent = css; document.head.appendChild(st); }
  }
  // Scroll mode: keep pulling past a chapter's end (or start) and let go to go on.
  Q.scroll = $scroll;
  if (!Q.pull) {
    Q.pull = true;
    var startY = null, atEnd = false, atStart = false;
    var se = function(){ return document.scrollingElement || document.documentElement; };
    document.addEventListener('touchstart', function(e){
      if (!Q.scroll || e.touches.length !== 1) { startY = null; return; }
      var s = se();
      startY = e.touches[0].clientY;
      atEnd = s.scrollTop + window.innerHeight >= s.scrollHeight - 4;
      atStart = s.scrollTop <= 4;
    }, { passive: true });
    document.addEventListener('touchend', function(e){
      if (startY === null || !Q.scroll) return;
      var dy = e.changedTouches[0].clientY - startY, pull = Math.min(window.innerHeight * 0.14, 110);
      if (atEnd && dy < -pull) $BRIDGE.chapter(true);
      else if (atStart && dy > pull) $BRIDGE.chapter(false);
      startY = null;
    }, { passive: true });
  }
  return 'ok';
})();
""".trimIndent()
}

/** What the page script calls. Runs on a web view thread: hand the work to the reader. */
class PageBridge(private val fonts: Fonts, private val onImage: (String) -> Unit, private val onChapter: (Boolean) -> Unit) {
    @JavascriptInterface fun openImage(url: String) = onImage(url)
    @JavascriptInterface fun chapter(forward: Boolean) = onChapter(forward)

    /** The @font-face rules for a family you added, with the files inlined. */
    @JavascriptInterface fun font(family: String): String = fonts.all().filter { it.family == family }.joinToString("\n") { f ->
        val data = Base64.encodeToString(fonts.fileOf(f.id).readBytes(), Base64.NO_WRAP)
        "@font-face{font-family:'${family.replace("'", "")}';src:url(data:${f.mime};base64,$data);" +
            "font-weight:${f.weight};font-style:${if (f.italic) "italic" else "normal"};}"
    }
}

/** One screen down or up in scroll mode; returns "edge" at the chapter's end or start. */
fun scrollStep(forward: Boolean, smooth: Boolean): String {
    val atEdge = if (forward) "top >= max - 4" else "top <= 4"
    val sign = if (forward) "" else "-"
    val behavior = if (smooth) "smooth" else "auto"
    return "(function(){var s=document.scrollingElement||document.documentElement,top=s.scrollTop,max=s.scrollHeight-window.innerHeight;" +
        "if($atEdge)return 'edge';s.scrollTo({top:top+${sign}window.innerHeight*0.88,behavior:'$behavior'});return 'ok';})();"
}
