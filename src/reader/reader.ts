import '../../vendor/foliate-js/view.js'
import { Overlayer } from '../../vendor/foliate-js/overlayer.js'
import * as CFI from '../../vendor/foliate-js/epubcfi.js'
import type { Book, FoliateView, Location } from './foliate'
import type { Flow, Margins, Settings } from '../settings'
import { bookCSS } from './book-css'
import { addBionic, removeBionic } from './bionic'
import { loadBook } from '../formats/load'

// How far past the end (or start) of a chapter you have to keep scrolling
// before Quiet moves to the next (or previous) chapter.
const WHEEL_PULL = 140
const TOUCH_PULL = 70

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
}

// Zoom for PDFs and comics: fit the page, fit the width, then larger (times the width).
export const ZOOMS = ['fit-page', 'fit-width', 1.5, 2, 3] as const
export type Zoom = typeof ZOOMS[number]

// A highlight drawn on the page.
export interface Mark { value: string; color: string }

// One open book on screen. Create a new Reader for each book.
export class Reader {
  readonly view: FoliateView
  #events: ReaderEvents
  #saveTimer = 0
  #wheelPull = 0
  #wheelReset = 0
  #turning = false
  #touch: { y: number; atStart: boolean; atEnd: boolean } | null = null
  #marks = new Map<string, Mark & { index: number }>()
  #selectionTimer = 0
  #scrollPastEnd = true
  #bionic = false
  #zoom: Zoom = 'fit-page'

  constructor(container: HTMLElement, events: ReaderEvents) {
    this.#events = events
    this.view = document.createElement('foliate-view') as FoliateView
    container.replaceChildren(this.view)
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
      const { draw, annotation } = (e as CustomEvent).detail
      draw(Overlayer.highlight, { color: annotation.color })
    })
    view.addEventListener('show-annotation', e => {
      const { value, range } = (e as CustomEvent<{ value: string; range: Range }>).detail
      this.#events.onHighlightTap(value, this.#screenRect(range))
    })
    view.addEventListener('load', e => {
      const { doc, index } = (e as CustomEvent<{ doc: Document; index: number }>).detail
      if (this.#bionic && !view.isFixedLayout) addBionic(doc)
      this.#listenTo(doc)
      this.#watchSelection(doc, () => index)
    })
    view.addEventListener('link', e => this.#events.onLink(e as CustomEvent<{ a: HTMLAnchorElement; href: string }>))
    this.#listenTo(view)

    this.#bionic = settings.bionic
    this.apply(settings)
    await view.init({ lastLocation: lastPosition ?? undefined })
  }

  apply(settings: Settings) {
    const { renderer } = this.view
    if (!renderer) return
    renderer.setStyles?.(bookCSS(settings))
    this.#scrollPastEnd = settings.scrollPastEnd
    if (settings.bionic !== this.#bionic) this.#setBionic(settings.bionic)
    if (!this.view.isFixedLayout) {
      const { gap, maxWidth } = MARGINS[settings.margins] ?? MARGINS.normal
      renderer.setAttribute('flow', settings.flow)
      renderer.setAttribute('max-inline-size', maxWidth)
      renderer.setAttribute('margin', settings.flow === 'paginated' ? '44px' : '28px')
      renderer.setAttribute('gap', gap)
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

  setZoom(zoom: Zoom) {
    const { renderer } = this.view
    if (!this.view.isFixedLayout || !renderer) return
    this.#zoom = zoom
    if (typeof zoom === 'string') return renderer.setAttribute('zoom', zoom)
    // Numbers are times the width of the screen, so 2 means a page twice as wide as the screen.
    // The page's own width is in its viewport tag (a blank frame beside a single page has none).
    // Two pages side by side (a blank one beside the first page counts) share the width.
    const contents = renderer.getContents?.() ?? []
    const meta = contents.map(c => c.doc?.querySelector('meta[name="viewport"]')).find(Boolean)
    const width = parseFloat(meta?.getAttribute('content')?.match(/width=([\d.]+)/)?.[1] ?? '') * Math.max(1, contents.length)
    const scale = width ? zoom * renderer.getBoundingClientRect().width / width : zoom
    renderer.setAttribute('zoom', String(scale))
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

  prev() { return this.view.prev() }
  next() { return this.view.next() }
  goTo(href: string) { return this.view.goTo(href) }
  goToFraction(fraction: number) { return this.view.goToFraction(fraction) }

  // Arrow keys move a little in scroll mode and a whole page in page mode.
  handleKey(e: KeyboardEvent) {
    const scrolled = this.flow === 'scrolled' && !this.view.isFixedLayout
    const step = scrolled ? 70 : undefined
    switch (e.key) {
      case 'ArrowDown': this.view.next(step); break
      case 'ArrowUp': this.view.prev(step); break
      case 'ArrowRight': scrolled ? this.view.next(step) : this.view.goRight(); break
      case 'ArrowLeft': scrolled ? this.view.prev(step) : this.view.goLeft(); break
      case 'PageDown': case ' ': this.view.next(); break
      case 'PageUp': this.view.prev(); break
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

  async #turn(dir: 1 | -1) {
    if (this.#turning) return
    this.#turning = true
    this.#wheelPull = 0
    try { await (dir > 0 ? this.view.next() : this.view.prev()) }
    finally { window.setTimeout(() => (this.#turning = false), 500) }
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

    target.addEventListener('wheel', e => {
      this.#events.onUserMove()
      const { deltaY } = e as WheelEvent
      const { active, atStart, atEnd } = this.#edges()
      if (!active) return
      const pushing = (deltaY > 0 && atEnd) || (deltaY < 0 && atStart)
      if (!pushing) { this.#wheelPull = 0; return }
      this.#wheelPull += Math.abs(deltaY)
      window.clearTimeout(this.#wheelReset)
      this.#wheelReset = window.setTimeout(() => (this.#wheelPull = 0), 300)
      if (this.#wheelPull > WHEEL_PULL) this.#turn(deltaY > 0 ? 1 : -1)
    }, { passive: true })

    target.addEventListener('touchstart', e => {
      const { active, atStart, atEnd } = this.#edges()
      const t = (e as TouchEvent).touches[0]
      this.#touch = active && t ? { y: t.clientY, atStart, atEnd } : null
    }, { passive: true })

    target.addEventListener('touchend', e => {
      const start = this.#touch
      this.#touch = null
      const t = (e as TouchEvent).changedTouches[0]
      if (!start || !t) return
      const { atStart, atEnd } = this.#edges()
      const dy = start.y - t.clientY
      if (dy > TOUCH_PULL && start.atEnd && atEnd) this.#turn(1)
      else if (dy < -TOUCH_PULL && start.atStart && atStart) this.#turn(-1)
    }, { passive: true })
  }
}
