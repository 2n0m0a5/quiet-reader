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

    fun script(bionic: Boolean, fontFamily: String?, scroll: Boolean = false, accent: String = "#D9622B"): String = """
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
    var startY = null, atEnd = false, atStart = false, armed = false, dir = 0, can = { next: false, prev: false }, titles = { next: '', prev: '' }, tip = null;
    var se = function(){ return document.scrollingElement || document.documentElement; };
    var limit = function(){ return Math.min(window.innerHeight * 0.14, 110); };
    var band = function(d){ var h = window.innerHeight; return (1 - 1 / (Math.abs(d) * 0.55 / h + 1)) * h * (d < 0 ? -1 : 1); };
    var setY = function(y, glide){
      var b = document.body; if (!b) return;
      b.style.transition = glide ? 'transform .5s cubic-bezier(.2,.9,.25,1)' : 'none';
      b.style.transform = y ? 'translateY(' + y + 'px)' : '';
    };
    // The sign in the gap the page leaves: a ring that fills as you pull, an arrow, and where you're
    // going ("Next: Chapter 10"). At the point of no return the ring turns orange and the arrow pops.
    var label = function(){
      if (!tip) {
        var ink = getComputedStyle(document.body || document.documentElement).color;
        tip = document.createElement('div');
        tip.id = 'quiet-pull';
        tip.setAttribute('style', 'position:fixed;left:0;right:0;display:flex;flex-direction:column;align-items:center;gap:6px;pointer-events:none;opacity:0;z-index:2147483647;color:' + ink + ';font:500 13px/1.3 system-ui,sans-serif;letter-spacing:.02em;transition:opacity .12s');
        tip.innerHTML = '<svg width="34" height="34" viewBox="0 0 34 34" style="overflow:visible">' +
          '<circle cx="17" cy="17" r="15" fill="none" stroke="currentColor" stroke-opacity=".18" stroke-width="2"/>' +
          '<circle id="quiet-ring" cx="17" cy="17" r="15" fill="none" stroke="$accent" stroke-width="2.5" stroke-linecap="round" stroke-dasharray="94.25" stroke-dashoffset="94.25" transform="rotate(-90 17 17)"/>' +
          '<g id="quiet-arrow" style="transform-origin:17px 17px;transition:transform .32s cubic-bezier(.3,1.7,.5,1)"><path d="M17 24V10M11 16l6-6 6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></g></svg>' +
          '<div id="quiet-pull-text"></div><div id="quiet-pull-title" style="font:italic 400 13px/1.3 Georgia,serif;opacity:.7;max-width:80vw;white-space:nowrap;overflow:hidden;text-overflow:ellipsis"></div>';
        document.documentElement.appendChild(tip);
      }
      return tip;
    };
    var show = function(y, past, title){
      var t = label(), h = Math.abs(y), lim = limit();
      document.getElementById('quiet-pull-text').textContent = (past ? 'Release for the ' : 'Pull for the ') + (dir > 0 ? 'next chapter' : 'previous chapter');
      document.getElementById('quiet-pull-title').textContent = title || '';
      // Sit in the middle of the gap the page leaves.
      var gap = Math.max(12, (h - t.offsetHeight) / 2);
      t.style.top = dir < 0 ? gap + 'px' : 'auto';
      t.style.bottom = dir > 0 ? gap + 'px' : 'auto';
      t.style.flexDirection = dir > 0 ? 'column' : 'column-reverse';
      t.style.opacity = Math.min(1, h / (lim * 0.5));
      var ring = document.getElementById('quiet-ring');
      ring.setAttribute('stroke-dashoffset', String(94.25 * (1 - Math.min(1, h / (lim * 0.62)))));
      ring.setAttribute('fill', past ? '$accent' : 'none');
      ring.setAttribute('fill-opacity', past ? '.16' : '0');
      var arrow = document.getElementById('quiet-arrow');
      arrow.style.transform = (dir < 0 ? 'rotate(180deg) ' : '') + (past ? 'scale(1.25)' : 'scale(1)');
      arrow.style.color = past ? '$accent' : '';
    };
    var hide = function(){ if (tip) tip.style.opacity = 0; };
    document.addEventListener('touchstart', function(e){
      if (!Q.scroll || e.touches.length !== 1) { startY = null; return; }
      var s = se();
      startY = e.touches[0].clientY;
      atEnd = s.scrollTop + window.innerHeight >= s.scrollHeight - 4;
      atStart = s.scrollTop <= 4;
      armed = false; dir = 0;
      if (atEnd || atStart) {
        can.next = !!$BRIDGE.can(true); can.prev = !!$BRIDGE.can(false);
        titles.next = can.next ? String($BRIDGE.title(true) || '') : ''; titles.prev = can.prev ? String($BRIDGE.title(false) || '') : '';
      }
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
      var past = Math.abs(dy) > limit();
      show(y, past, dir > 0 ? titles.next : titles.prev);
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
    private val titleOf: (Boolean) -> String = { "" },
) {
    @JavascriptInterface fun openImage(url: String) = onImage(url)
    @JavascriptInterface fun chapter(forward: Boolean) = onChapter(forward)
    /** Whether there's a chapter after (or before) this one. */
    @JavascriptInterface fun can(forward: Boolean): Boolean = runCatching { canGo(forward) }.getOrDefault(false)
    /** The click as a pull passes the point of no return. */
    @JavascriptInterface fun tick() = onTick()
    /** The name of the chapter after (or before) this one, for the pull's sign. */
    @JavascriptInterface fun title(forward: Boolean): String = runCatching { titleOf(forward) }.getOrDefault("")

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
