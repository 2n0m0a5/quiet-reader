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

    fun script(bionic: Boolean, fontFamily: String?): String = """
(function(){
  var Q = window.__quiet || (window.__quiet = {});
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
  return 'ok';
})();
""".trimIndent()
}

/** What the page script calls. Runs on a web view thread: hand the work to the reader. */
class PageBridge(private val fonts: Fonts, private val onImage: (String) -> Unit) {
    @JavascriptInterface fun openImage(url: String) = onImage(url)

    /** The @font-face rules for a family you added, with the files inlined. */
    @JavascriptInterface fun font(family: String): String = fonts.all().filter { it.family == family }.joinToString("\n") { f ->
        val data = Base64.encodeToString(fonts.fileOf(f.id).readBytes(), Base64.NO_WRAP)
        "@font-face{font-family:'${family.replace("'", "")}';src:url(data:${f.mime};base64,$data);" +
            "font-weight:${f.weight};font-style:${if (f.italic) "italic" else "normal"};}"
    }
}
