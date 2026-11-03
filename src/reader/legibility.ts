// Keeps the book's text readable on Quiet's page.
//
// Quiet sets the page colour and the text colour (book-css.ts), but a book can colour its own
// paragraphs: black text that disappears on the Night page, or white text made for a dark page that
// disappears on the Day page. Links stay visible because Quiet colours them, so the page looks empty
// while its links still work. After each chapter loads (and after the theme changes), every element
// whose own text colour is too faint against the page gets the theme's text colour instead.
// Colours that read well (a red heading, a grey epigraph) are left as the book made them.

const MIN_CONTRAST = 2.2 // below this, text is hard to see; WCAG's minimum for body text is 4.5

type RGBA = [number, number, number, number]

function parse(color: string): RGBA | null {
  const m = color.match(/rgba?\(\s*([\d.]+)[ ,]+([\d.]+)[ ,]+([\d.]+)(?:\s*[,/]\s*([\d.]+%?))?\s*\)/)
  if (!m) return null
  const a = m[4] === undefined ? 1 : m[4].endsWith('%') ? parseFloat(m[4]) / 100 : parseFloat(m[4])
  return [+m[1], +m[2], +m[3], a]
}

function luminance([r, g, b]: RGBA) {
  const lin = (v: number) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4 }
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
}

export function contrast(a: RGBA, b: RGBA) {
  const [x, y] = [luminance(a), luminance(b)].sort((p, q) => q - p)
  return (x + 0.05) / (y + 0.05)
}

// Does the element (or a parent inside the body) paint its own background? Then its text colour
// was chosen for that background, not for Quiet's page.
function hasOwnBackground(el: Element, view: Window, body: Element) {
  for (let node: Element | null = el; node && node !== body; node = node.parentElement) {
    const style = view.getComputedStyle(node)
    if (style.backgroundImage !== 'none') return true
    const bg = parse(style.backgroundColor)
    if (bg && bg[3] > 0.5) return true
  }
  return false
}

const MARK = 'data-quiet-ink'

export function keepTextReadable(doc: Document) {
  const view = doc.defaultView
  const body = doc.body
  if (!view || !body) return
  // Forget the last check (the theme may have changed), then look again.
  for (const el of doc.querySelectorAll(`[${MARK}]`)) {
    (el as HTMLElement).style.removeProperty('color')
    el.removeAttribute(MARK)
  }
  const page = parse(view.getComputedStyle(doc.documentElement).backgroundColor)
  const ink = view.getComputedStyle(doc.documentElement).color
  if (!page || page[3] < 0.5) return
  for (const el of body.querySelectorAll<HTMLElement>('*')) {
    if (el.closest('a, svg, math, img, video')) continue
    // Only elements that hold text themselves.
    if (![...el.childNodes].some(n => n.nodeType === 3 && n.textContent!.trim())) continue
    const style = view.getComputedStyle(el)
    if (style.color === ink) continue
    const color = parse(style.color)
    if (!color) continue
    if (color[3] >= 0.35 && contrast(color, page) >= MIN_CONTRAST) continue
    if (hasOwnBackground(el, view, body)) continue
    el.style.setProperty('color', 'inherit', 'important')
    el.setAttribute(MARK, '')
  }
}
