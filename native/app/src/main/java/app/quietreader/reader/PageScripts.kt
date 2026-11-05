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
  // Scroll mode: at a chapter's end (or start) the page stretches like iOS's rubber band as you
  // keep pulling. A quiet line says "Pull for the next chapter", then "Release…" with a click
  // when letting go will turn the chapter. Let go early and the page springs back.
  Q.scroll = $scroll;
  if (!Q.pull) {
    Q.pull = true;
    var startY = null, atEnd = false, atStart = false, armed = false, dir = 0, can = { next: false, prev: false }, tip = null;
    var se = function(){ return document.scrollingElement || document.documentElement; };
    var limit = function(){ return Math.min(window.innerHeight * 0.14, 110); };
    var band = function(d){ var h = window.innerHeight; return (1 - 1 / (Math.abs(d) * 0.55 / h + 1)) * h * (d < 0 ? -1 : 1); };
    var setY = function(y, glide){
      var b = document.body; if (!b) return;
      b.style.transition = glide ? 'transform .5s cubic-bezier(.2,.9,.25,1)' : 'none';
      b.style.transform = y ? 'translateY(' + y + 'px)' : '';
    };
    var label = function(){
      if (!tip) {
        tip = document.createElement('div');
        tip.id = 'quiet-pull';
        tip.setAttribute('style', 'position:fixed;left:0;right:0;text-align:center;font:500 13px/1.3 system-ui,sans-serif;letter-spacing:.02em;pointer-events:none;opacity:0;z-index:2147483647;transition:opacity .15s');
        tip.style.color = getComputedStyle(document.body || document.documentElement).color;
        document.documentElement.appendChild(tip);
      }
      return tip;
    };
    var hide = function(){ if (tip) tip.style.opacity = 0; };
    document.addEventListener('touchstart', function(e){
      if (!Q.scroll || e.touches.length !== 1) { startY = null; return; }
      var s = se();
      startY = e.touches[0].clientY;
      atEnd = s.scrollTop + window.innerHeight >= s.scrollHeight - 4;
      atStart = s.scrollTop <= 4;
      armed = false; dir = 0;
      if (atEnd || atStart) { can.next = !!$BRIDGE.can(true); can.prev = !!$BRIDGE.can(false); }
    }, { passive: true });
    document.addEventListener('touchmove', function(e){
      if (startY === null || !Q.scroll || e.touches.length !== 1) return;
      var dy = e.touches[0].clientY - startY;
      if (atEnd && dy < 0) dir = 1; else if (atStart && dy > 0) dir = -1;
      else { if (dir) { setY(0, true); hide(); } dir = 0; return; }
      var y = band(dy);
      setY(y, false);
      var has = dir > 0 ? can.next : can.prev;
      if (!has) return;
      var past = Math.abs(dy) > limit(), t = label();
      t.textContent = (past ? 'Release for the ' : 'Pull for the ') + (dir > 0 ? 'next chapter' : 'previous chapter');
      t.style.top = dir < 0 ? '18px' : 'auto';
      t.style.bottom = dir > 0 ? '24px' : 'auto';
      t.style.opacity = Math.min(1, Math.abs(y) / (limit() * 0.6)) * (past ? 1 : 0.55);
      if (past !== armed) { armed = past; if (past) $BRIDGE.tick(); }
    }, { passive: true });
    var end = function(go){
      if (startY === null) return;
      var has = dir > 0 ? can.next : can.prev;
      if (go && dir && armed && has) {
        hide();
        $BRIDGE.chapter(dir > 0);
        // The app glides the chapter away; put this page back once it's out of sight.
        setTimeout(function(){ setY(0, false); }, 700);
      } else { setY(0, true); hide(); }
      startY = null; dir = 0; armed = false;
    };
    document.addEventListener('touchend', function(){ end(true); }, { passive: true });
    document.addEventListener('touchcancel', function(){ end(false); }, { passive: true });
  }
  return 'ok';
})();
""".trimIndent()
}

/** What the page script calls. Runs on a web view thread: hand the work to the reader. */
class PageBridge(
    private val fonts: Fonts, private val onImage: (String) -> Unit, private val onChapter: (Boolean) -> Unit,
    private val canGo: (Boolean) -> Boolean = { true }, private val onTick: () -> Unit = {},
) {
    @JavascriptInterface fun openImage(url: String) = onImage(url)
    @JavascriptInterface fun chapter(forward: Boolean) = onChapter(forward)
    /** Whether there's a chapter after (or before) this one. */
    @JavascriptInterface fun can(forward: Boolean): Boolean = runCatching { canGo(forward) }.getOrDefault(false)
    /** The click as a pull passes the point of no return. */
    @JavascriptInterface fun tick() = onTick()

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
