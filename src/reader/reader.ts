import { reducedMotion } from '../ui/motion'
import '../../vendor/foliate-js/view.js'
import { Overlayer } from '../../vendor/foliate-js/overlayer.js'
import * as CFI from '../../vendor/foliate-js/epubcfi.js'
import type { Book, FoliateView, Location } from './foliate'
import type { Flow, Margins, PageTurn, Settings } from '../settings'
import type { MarkStyle } from '../library/db'
import { bookCSS } from './book-css'
import { addBionic, removeBionic } from './bionic'
import { inkOf } from './selection-menu'
import { loadBook } from '../formats/load'
import { ChapterPull } from './chapter-pull'
import { keepTextReadable } from './legibility'

// After the current work, without waiting long (the check reads every element's colour).
const whenIdle = (fn: () => void) => 'requestIdleCallback' in window ? requestIdleCallback(fn, { timeout: 500 }) : setTimeout(fn, 50)


// Side margin (percent of the page) and widest text column (px) for each margin setting.
const MARGINS: Record<Margins, { gap: string; maxWidth: string }> = {
  narrow: { gap: '3%', maxWidth: '820px' },
  normal: { gap: '6%', maxWidth: '720px' },
  wide: { gap: '10%', maxWidth: '620px' },
}

export type TapZone = 'left' | 'center' | 'right'

export interface ReaderEvents {
  onRelocate(location: Location): void
  // Called (at most a few times a second) with the place to remember.
  onSavePosition(cfi: string, fraction: number): void
  // A tap or click on the page (not on a link, not after selecting text).
  onTap(zone: TapZone): void
  // The reader started scrolling or turning pages by hand.
  onUserMove(): void
  // Text was selected (or the selection went away: null). The rectangle is in page coordinates.
  onSelection(selection: { text: string; cfi: string; rect: DOMRect } | null): void
  // A highlight was tapped.
  onHighlightTap(value: string, rect: DOMRect): void
  // A picture in the book was tapped.
  onImage(src: string, alt: string): void
  // A link inside the book was followed. Call preventDefault() on the event to handle it yourself.
  onLink(e: CustomEvent<{ a: HTMLAnchorElement; href: string }>): void
  // A PDF or comic was zoomed by pinching (times the screen width).
  onZoom?(factor: number): void
  // Pulling past the end of a chapter reached the point where letting go turns it.
  onPullArmed?(): void
}

// Zoom for PDFs and comics: the whole page, or a number of times the screen's width
// (1 is the page's width; pinching can choose anything in between, up to 4).
export type Zoom = 'fit-page' | number
export const ZOOM_STEPS = [1, 1.5, 2, 3]
const MAX_ZOOM = 4

// A highlight drawn on the page.
export interface Mark { value: string; color: string; style?: MarkStyle }

// The outline of a passage saved only as a quote.
const QUOTE_OUTLINE = '#b08d57'

// One open book on screen. Create a new Reader for each book.
const CURL_MS = 620

// The frames of a page curl on a page w × h px. The fold line goes from the outer edge (x = w) to
// the spine (x = 0); `back` plays it the other way (laying the previous page down).
//   page:  the turning page, cut off at the fold line
//   flap:  the back of the turned part, mirrored onto the page beside the fold, a little narrower
//          than the part it is (the paper bends over a curve), as a slanted strip
//   shade: the shadow on the page underneath, just beyond the fold
export function curlFrames(w: number, h: number, back = false, steps = 24, shift = 0) {
  // A gentle start (the corner is lifted at once), steady through the middle, settling at the end.
  const ease = (t: number) => 0.5 - 0.5 * Math.cos(Math.PI * t)
  const frames: Keyframe[] = []
  for (let i = 0; i <= steps; i++) {
    const t = back ? 1 - i / steps : i / steps
    const p = ease(t)
    const fold = w * (1 - p) * 1.03 - w * 0.03                // ends a little past the spine, so it finishes clean
    const tilt = w * 0.14 * Math.sin(Math.PI * Math.min(1, p ** 0.6)) // the lower corner lifts first and leads
    const top = fold + tilt / 2, bottom = fold - tilt / 2
    const width = Math.max(0, w - fold) * (0.86 + 0.1 * p)
    frames.push({
      offset: i / steps,
      // The turning page is cut off at the fold (in the screen's coordinates: `shift` is where the
      // page starts on screen, for books with fixed pages that don't fill it).
      '--curl-top': `${(top + shift).toFixed(1)}px`,
      '--curl-bottom': `${(bottom + shift).toFixed(1)}px`,
      // The back of the page: its picture has the crease at its right edge; squeezed to `width`,
      // slanted so the crease lies on the fold line.
      '--curl-sx': Math.max(0.0001, width / w).toFixed(4),
      '--curl-skew': ((bottom - top) / h).toFixed(4),
      '--curl-tx': `${(top - width).toFixed(1)}px`,
      '--curl-fold': `${top.toFixed(1)}px`,
      '--curl-flap': width < 2 ? '0' : '1',
      '--curl-shade': (Math.sin(Math.PI * p) * 0.9).toFixed(3),
    })
  }
  return frames
}

export class Reader {
  readonly view: FoliateView
  #events: ReaderEvents
  #saveTimer = 0
  #pull: ChapterPull
  #pageTurn: PageTurn = 'slide'
  #curling = false
  #marks = new Map<string, Mark & { index: number }>()
  #selectionTimer = 0
  #scrollPastEnd = true
  #bionic = false
  #zoom: Zoom = 'fit-page'

  constructor(container: HTMLElement, events: ReaderEvents) {
    this.#events = events
    this.view = document.createElement('foliate-view') as FoliateView
    container.replaceChildren(this.view)
    this.#pull = new ChapterPull(this.view, container, {
      edges: () => this.#edges(),
      turn: dir => dir > 0 ? this.view.next() : this.view.prev(),
      hasMore: dir => {
        const section = this.view.lastLocation?.section
        return !section || (dir > 0 ? section.current < section.total - 1 : section.current > 0)
      },
      onArm: () => this.#events.onPullArmed?.(),
    })
    this.#pull.listen(this.view)
  }

  get book(): Book {
    return this.view.book
  }

  async open(file: File, settings: Settings, lastPosition?: string | null) {
    const { view } = this
    await view.open(await loadBook(file))

    // A broken image or font inside the book should not stop the whole chapter from loading.
    const target = (view.book as { transformTarget?: EventTarget }).transformTarget
    target?.addEventListener('data', (e: Event) => {
      const detail = (e as CustomEvent).detail
      detail.data = Promise.resolve(detail.data).catch(() => '')
    })

    view.addEventListener('relocate', e => this.#onRelocate((e as CustomEvent<Location>).detail))
    // Highlights: drawn when their chapter is shown, tappable afterwards.
    view.addEventListener('create-overlay', e => {
      const { index } = (e as CustomEvent<{ index: number }>).detail
      for (const mark of this.#marks.values()) if (mark.index === index) view.addAnnotation(mark)
    })
    view.addEventListener('draw-annotation', e => {
      const { draw, annotation } = (e as CustomEvent<{ draw: (f: unknown, o: object) => void; annotation: Mark }>).detail
      const { color, style } = annotation
      if (style === 'underline') draw(Overlayer.underline, { color: inkOf(color), width: 2.5 })
      else if (style === 'strike') draw(Overlayer.strikethrough, { color: inkOf(color), width: 2 })
      else if (style === 'plain') draw(Overlayer.outline, { color: QUOTE_OUTLINE, width: 1.5, radius: 3 })
      else draw(Overlayer.highlight, { color })
    })
    view.addEventListener('show-annotation', e => {
      const { value, range } = (e as CustomEvent<{ value: string; range: Range }>).detail
      this.#events.onHighlightTap(value, this.#screenRect(range))
    })
    view.addEventListener('load', e => {
      const { doc, index } = (e as CustomEvent<{ doc: Document; index: number }>).detail
      if (this.#bionic && !view.isFixedLayout) addBionic(doc)
      if (!view.isFixedLayout) whenIdle(() => keepTextReadable(doc))
      this.#listenTo(doc)
      this.#watchSelection(doc, () => index)
    })
    view.addEventListener('link', e => this.#events.onLink(e as CustomEvent<{ a: HTMLAnchorElement; href: string }>))
    this.#listenTo(view)

    this.#bionic = settings.bionic
    this.apply(settings)
    await view.init({ lastLocation: lastPosition ?? undefined })
    this.#watchScroll()
    view.renderer?.addEventListener('swipe-turn', e => {
      this.#events.onUserMove()
      ;(e as CustomEvent<{ side: 'left' | 'right' }>).detail.side === 'right' ? this.goRight() : this.goLeft()
    })
  }

  apply(settings: Settings) {
    const { renderer } = this.view
    if (!renderer) return
    renderer.setStyles?.(bookCSS(settings))
    this.#scrollPastEnd = settings.scrollPastEnd
    this.#pageTurn = settings.pageTurn ?? 'slide'
    // Curl: a swipe asks for a turn (drawn by #curl) instead of dragging the page along.
    renderer.toggleAttribute('swipe-turn', this.#pageTurn === 'curl')
    if (settings.bionic !== this.#bionic) this.#setBionic(settings.bionic)
    // The theme may have changed: check the book's own text colours against the new page.
    if (!this.view.isFixedLayout) whenIdle(() => { for (const { doc } of renderer.getContents?.() ?? []) if (doc) keepTextReadable(doc) })
    if (!this.view.isFixedLayout) {
      const { gap, maxWidth } = MARGINS[settings.margins] ?? MARGINS.normal
      renderer.setAttribute('flow', settings.flow)
      renderer.setAttribute('max-inline-size', maxWidth)
      renderer.setAttribute('margin', settings.flow === 'paginated' ? '44px' : '28px')
      renderer.setAttribute('gap', gap)
      // Pages slide when they turn (if chosen), unless the device asks for less motion.
      renderer.toggleAttribute('animated', this.#pageTurn === 'slide' && !reducedMotion.matches)
    }
  }

  // Turns bionic reading on or off in the chapter on screen, keeping the place and the highlights.
  #setBionic(on: boolean) {
    this.#bionic = on
    if (this.view.isFixedLayout) return
    const cfi = this.view.lastLocation?.cfi
    for (const { doc, index } of this.view.renderer.getContents?.() ?? []) {
      if (!doc) continue
      on ? addBionic(doc) : removeBionic(doc)
      // The text was re-split, so highlights are found again from their saved positions.
      for (const mark of this.#marks.values()) if (mark.index === index) {
        this.view.deleteAnnotation(mark)
        this.view.addAnnotation(mark)
      }
    }
    if (cfi) requestAnimationFrame(() => this.view.goTo(cfi))
  }

  // ---------- Zoom (PDFs and comics) ----------

  get zoom() { return this.#zoom }

  // The page's size (from its viewport tag), and how many pages are on screen side by side.
  #page() {
    const pages = (this.view.renderer?.getContents?.() ?? []).flatMap(c => {
      const meta = c.doc?.querySelector('meta[name="viewport"]')?.getAttribute('content') ?? ''
      const width = parseFloat(meta.match(/width=([\d.]+)/)?.[1] ?? '')
      const height = parseFloat(meta.match(/height=([\d.]+)/)?.[1] ?? '')
      const shown = (c.doc?.defaultView?.frameElement?.getBoundingClientRect().width ?? 0) > 0
      return width && height && shown ? [{ width, height }] : []
    })
    return pages.length ? { ...pages[0], count: pages.length } : null
  }

  // How wide one page is, in screen widths: 1 is the page's width. For "whole page" it's 1 or less.
  zoomFactor(zoom: Zoom = this.#zoom) {
    if (zoom !== 'fit-page') return zoom
    const page = this.#page()
    const box = this.view.renderer?.getBoundingClientRect()
    if (!page || !box?.width) return 1
    const scale = Math.min(box.width / (page.width * page.count), box.height / page.height)
    return scale * page.width / box.width
  }

  // Sets the zoom. With a point on screen, what's under it stays in place (like pinching).
  setZoom(zoom: Zoom, around?: { x: number; y: number }) {
    const { renderer } = this.view
    if (!this.view.isFixedLayout || !renderer) return
    const before = this.zoomFactor()
    const page = this.#page()
    const box = renderer.getBoundingClientRect()
    this.#zoom = zoom
    if (zoom === 'fit-page' || !page) renderer.setAttribute('zoom', 'fit-page')
    else renderer.setAttribute('zoom', String(zoom * box.width / page.width))
    if (around) {
      const ratio = this.zoomFactor() / before
      const x = around.x - box.left, y = around.y - box.top
      renderer.scrollLeft = (renderer.scrollLeft + x) * ratio - x
      renderer.scrollTop = (renderer.scrollTop + y) * ratio - y
    }
  }

  // Two fingers on a PDF or comic page zoom it. While pinching, the page is only stretched
  // (fast); when the fingers lift, it's drawn again sharp at the new size.
  #pinch: { distance: number; factor: number; ratio: number; x: number; y: number } | null = null

  #listenForPinch(target: Document | HTMLElement, doc: Document | null) {
    // Finger positions inside a page are in the page's own (possibly scaled) coordinates.
    const toScreen = (t: Touch) => {
      const frame = doc?.defaultView?.frameElement as HTMLElement | null | undefined
      if (!frame) return { x: t.clientX, y: t.clientY }
      const rect = frame.getBoundingClientRect()
      const scale = frame.offsetWidth ? rect.width / frame.offsetWidth : 1
      return { x: rect.left + t.clientX * scale, y: rect.top + t.clientY * scale }
    }
    const fingers = (list: TouchList) => {
      const a = toScreen(list[0]), b = toScreen(list[1])
      return { distance: Math.hypot(a.x - b.x, a.y - b.y), x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }
    }
    target.addEventListener('touchstart', e => {
      const touches = (e as TouchEvent).touches
      if (!this.view.isFixedLayout || touches.length !== 2) return
      const f = fingers(touches)
      this.#pinch = { distance: f.distance, factor: this.zoomFactor(), ratio: 1, x: f.x, y: f.y }
    }, { passive: true })
    target.addEventListener('touchmove', e => {
      const touches = (e as TouchEvent).touches
      const pinch = this.#pinch
      if (!pinch || touches.length !== 2) return
      e.preventDefault()
      const f = fingers(touches)
      const min = this.zoomFactor('fit-page')
      const factor = Math.min(MAX_ZOOM, Math.max(min, pinch.factor * f.distance / pinch.distance))
      pinch.ratio = factor / pinch.factor
      const renderer = this.view.renderer
      const box = renderer.getBoundingClientRect()
      renderer.style.transformOrigin = `${pinch.x - box.left}px ${pinch.y - box.top}px`
      renderer.style.transform = `scale(${pinch.ratio})`
    }, { passive: false })
    const end = (e: Event) => {
      const pinch = this.#pinch
      if (!pinch || (e as TouchEvent).touches.length >= 2) return
      this.#pinch = null
      const renderer = this.view.renderer
      renderer.style.transform = ''
      const factor = pinch.factor * pinch.ratio
      // Close to the whole page or the page's width: snap to it.
      const fit = this.zoomFactor('fit-page')
      const zoom: Zoom = factor <= fit * 1.04 ? 'fit-page' : Math.abs(factor - 1) < 0.04 ? 1 : factor
      this.setZoom(zoom, { x: pinch.x, y: pinch.y })
      this.#events.onZoom?.(this.zoomFactor())
    }
    target.addEventListener('touchend', end)
    target.addEventListener('touchcancel', end)
  }

  get flow(): Flow {
    return this.view.renderer?.scrolled ? 'scrolled' : 'paginated'
  }

  // ---------- Highlights, bookmarks, selection ----------

  setMarks(marks: Mark[]) {
    for (const mark of marks) this.addMark(mark)
  }

  addMark(mark: Mark) {
    const index = this.view.resolveNavigation(mark.value)?.index ?? -1
    this.#marks.set(mark.value, { ...mark, index })
    this.view.addAnnotation({ ...mark })
  }

  removeMark(value: string) {
    this.#marks.delete(value)
    this.view.deleteAnnotation({ value })
  }

  // Where the visible page starts, and the first words on it (for bookmarks).
  here() {
    const location = this.view.lastLocation
    if (!location?.cfi) return null
    const text = location.range?.toString().replace(/\s+/g, ' ').trim().slice(0, 160) ?? ''
    return { cfi: CFI.collapse(location.cfi), text, label: location.tocItem?.label?.trim() ?? '', progress: location.fraction ?? 0 }
  }

  // Is this position on the page that's showing now?
  isVisible(cfi: string) {
    const location = this.view.lastLocation?.cfi
    if (!location) return false
    try {
      return CFI.compare(CFI.collapse(location), cfi) <= 0 && CFI.compare(cfi, CFI.collapse(location, true)) <= 0
    } catch { return false }
  }

  clearSelection() {
    this.view.deselect()
  }

  // A rectangle inside a chapter, moved into the main page's coordinates.
  #screenRect(range: Range) {
    const rect = range.getBoundingClientRect()
    const frame = range.startContainer.ownerDocument?.defaultView?.frameElement?.getBoundingClientRect()
    return new DOMRect(rect.left + (frame?.left ?? 0), rect.top + (frame?.top ?? 0), rect.width, rect.height)
  }

  #watchSelection(doc: Document, index: () => number | undefined) {
    doc.addEventListener('selectionchange', () => {
      window.clearTimeout(this.#selectionTimer)
      this.#selectionTimer = window.setTimeout(() => {
        const selection = doc.getSelection()
        const text = selection?.toString().trim() ?? ''
        if (!selection || selection.isCollapsed || !text || selection.rangeCount === 0) return this.#events.onSelection(null)
        const range = selection.getRangeAt(0)
        const i = index()
        if (i == null) return
        this.#events.onSelection({ text, cfi: this.view.getCFI(i, range), rect: this.#screenRect(range) })
      }, 350)
    })
  }

  prev() { return this.#curl(-1, () => this.view.prev()) }
  next() { return this.#curl(1, () => this.view.next()) }
  // Turning by side of the screen (taps, arrow keys, swipes). In right-to-left books, left is forward.
  goLeft() { return this.#curl(this.#rtl ? 1 : -1, () => this.view.goLeft()) }
  goRight() { return this.#curl(this.#rtl ? -1 : 1, () => this.view.goRight()) }
  get #rtl() { return (this.view.book as { dir?: string })?.dir === 'rtl' }

  // The page curl, like a sheet of paper: the page on screen is captured, a fold line sweeps across
  // it from the outer edge (a little tilted, the lower corner leading), the part already turned
  // shows its back (lighter at the crease, shaded where it bends), and the next page lies
  // underneath in the curl's shadow. Going back, the previous page is laid down on top the same
  // way, in reverse. Drawn with the View Transitions API; the frames are worked out in curlFrames.
  async #curl(dir: 1 | -1, turn: () => Promise<unknown>) {
    const doc = document as Document & { startViewTransition?: (cb: () => Promise<void>) => { finished: Promise<void>; updateCallbackDone: Promise<void>; ready: Promise<void> } }
    const r = this.view.renderer
    if (this.#pageTurn !== 'curl' || !r || r.scrolled || !doc.startViewTransition || this.#curling || document.hidden
      || reducedMotion.matches) return turn()
    this.#curling = true
    const root = document.documentElement
    const stage = (this.view.closest('.reader-stage') ?? this.view) as HTMLElement
    const area = stage.getBoundingClientRect()
    // Books with fixed pages (PDFs, comics) show the page smaller than the screen: only the page curls.
    let box: DOMRect = area
    if (this.view.isFixedLayout) {
      const frames = (r.getContents?.() ?? []).map(({ doc }) => doc?.defaultView?.frameElement?.getBoundingClientRect())
        .filter((b): b is DOMRect => !!b && b.width > 0 && b.right > area.left && b.left < area.right)
      if (frames.length) {
        const left = Math.max(area.left, Math.min(...frames.map(b => b.left))), right = Math.min(area.right, Math.max(...frames.map(b => b.right)))
        const top = Math.max(area.top, Math.min(...frames.map(b => b.top))), bottom = Math.min(area.bottom, Math.max(...frames.map(b => b.bottom)))
        box = new DOMRect(left, top, right - left, bottom - top)
      }
    }
    // The back of the page has the colour of the paper: the page's own (white for a PDF).
    const doc0 = r.getContents?.()[0]?.doc
    const bg = doc0 ? getComputedStyle(doc0.documentElement).backgroundColor : ''
    const paper = !bg || bg === 'transparent' || /rgba\(.*,\s*0\)$/.test(bg) ? (this.view.isFixedLayout ? '#ffffff' : getComputedStyle(root).getPropertyValue('--reader-bg')) : bg
    root.dataset.curl = dir > 0 ? 'next' : 'prev'
    // The back of the turning page, and the shadow it casts: there only in the new picture.
    const flap = document.createElement('div')
    flap.className = 'curl-flap'
    flap.style.setProperty('--paper', paper)
    const shade = document.createElement('div')
    shade.className = 'curl-shade'
    for (const el of [flap, shade]) Object.assign(el.style, { left: `${box.left}px`, top: `${box.top}px`, width: `${box.width}px`, height: `${box.height}px` })
    // (The page is drawn again only after this returns, so it mustn't wait for a frame; foliate's
    // turn can, so it gets a moment and the curl starts anyway.)
    let turned: Promise<unknown> = Promise.resolve()
    const frames = curlFrames(box.width, box.height, dir < 0, 24, box.left - area.left)
    // The first frame at once, so nothing shows uncut before the animation starts.
    for (const [k, v] of Object.entries(frames[0])) if (k.startsWith('--')) root.style.setProperty(k, String(v))
    const t = doc.startViewTransition(async () => {
      document.body.append(flap, shade)
      turned = turn()
      await Promise.race([turned, new Promise(resolve => setTimeout(resolve, 350))])
    })
    // One animation of custom properties on the page root; the curl's pseudo-elements read them
    // (motion.css). Phones handle this more reliably than animating the pseudo-elements directly.
    const play = root.animate(frames, { duration: CURL_MS, easing: 'linear', fill: 'forwards' })
    t.ready.catch(() => {})
    t.finished.catch(() => {}).finally(() => {
      play.cancel()
      for (const k of Object.keys(frames[0])) if (k.startsWith('--')) root.style.removeProperty(k)
      flap.remove(); shade.remove(); delete root.dataset.curl; this.#curling = false
    })
    await t.updateCallbackDone.catch(() => {})
    return turned
  }
  goTo(href: string) { return this.view.goTo(href) }
  goToFraction(fraction: number) { return this.view.goToFraction(fraction) }

  // Arrow keys move a little in scroll mode and a whole page in page mode.
  handleKey(e: KeyboardEvent) {
    const scrolled = this.flow === 'scrolled' && !this.view.isFixedLayout
    const step = scrolled ? 70 : undefined
    switch (e.key) {
      case 'ArrowDown': this.view.next(step); break
      case 'ArrowUp': this.view.prev(step); break
      case 'ArrowRight': scrolled ? this.view.next(step) : this.goRight(); break
      case 'ArrowLeft': scrolled ? this.view.prev(step) : this.goLeft(); break
      case 'PageDown': case ' ': this.next(); break
      case 'PageUp': this.prev(); break
      default: return
    }
    e.preventDefault()
  }

  // Save the reading position now (e.g. when the app goes to the background).
  save() {
    window.clearTimeout(this.#saveTimer)
    this.#savePosition()
  }

  close() {
    this.save()
    this.view.close()
    this.view.remove()
  }

  #onRelocate(location: Location) {
    window.clearTimeout(this.#saveTimer)
    this.#saveTimer = window.setTimeout(() => this.#savePosition(), 400)
    this.#events.onRelocate(location)
  }

  #savePosition() {
    const location = this.view.lastLocation
    if (location?.cfi) this.#events.onSavePosition(location.cfi, location.fraction ?? 0)
  }

  // ---- Continuous scroll: keep scrolling at the end of a chapter to move on ----

  #edges() {
    const r = this.view.renderer
    const active = this.#scrollPastEnd && r && r.scrolled && !this.view.isFixedLayout
    return {
      active,
      atStart: active && r.start <= 1,
      atEnd: active && r.viewSize - r.end <= 2,
    }
  }

  // A flick into the end of a chapter bounces the page (chapter-pull.ts). Watches the scroll speed.
  #watchScroll() {
    const r = this.view.renderer
    if (!r) return
    let last = r.start, lastT = performance.now(), speed = 0
    r.addEventListener('scroll', () => {
      const now = performance.now()
      speed = 0.6 * ((r.start - last) / Math.max(1, now - lastT)) + 0.4 * speed
      last = r.start
      lastT = now
      const { atStart, atEnd } = this.#edges()
      if (atEnd && speed > 0.4) { this.#pull.bounce(1, speed); speed = 0 }
      else if (atStart && speed < -0.4) { this.#pull.bounce(-1, speed); speed = 0 }
    })
  }

  // Which third of the screen a click landed in. Clicks inside a chapter are measured
  // from the chapter's frame, so they are shifted by the frame's position on screen.
  #onClick(e: MouseEvent, doc: Document | null) {
    if (e.defaultPrevented || e.button !== 0) return                 // a link handled it
    if (doc?.getSelection()?.toString()) return                     // finished selecting text
    const target = e.target as Element | null
    if (target?.closest?.('a, button, input, audio, video')) return
    const img = target?.closest?.('img, image')
    if (img && doc && !this.view.isFixedLayout) {
      // (instanceof doesn't work here: the picture belongs to the chapter's own window.)
      const src = img.localName === 'img' ? (img as HTMLImageElement).currentSrc || (img as HTMLImageElement).src
        : img.getAttribute('href') ?? img.getAttribute('xlink:href') ?? ''
      if (src) return this.#events.onImage(src, img.getAttribute('alt') ?? '')
    }
    const frame = doc?.defaultView?.frameElement
    const x = (frame?.getBoundingClientRect().left ?? 0) + e.clientX
    const third = window.innerWidth / 3
    this.#events.onTap(x < third ? 'left' : x > third * 2 ? 'right' : 'center')
  }

  #listenTo(target: Document | HTMLElement) {
    const doc = target === this.view ? null : target as Document
    // Key presses inside a chapter don't reach the main page, so handle them here.
    if (doc) doc.addEventListener('keydown', e => this.handleKey(e as KeyboardEvent))
    target.addEventListener('click', e => this.#onClick(e as MouseEvent, doc))
    target.addEventListener('touchmove', () => this.#events.onUserMove(), { passive: true })
    this.#listenForPinch(target, doc)

    target.addEventListener('wheel', () => this.#events.onUserMove(), { passive: true })
    // Past the end of a chapter in scroll mode: pull on to the next one (chapter-pull.ts).
    if (doc) this.#pull.listen(doc)
  }
}
