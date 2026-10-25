import '../../vendor/foliate-js/view.js'
import type { Book, FoliateView, Location } from './foliate'
import type { Flow, Margins, Settings } from '../settings'
import { bookCSS } from './book-css'
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
}

// One open book on screen. Create a new Reader for each book.
export class Reader {
  readonly view: FoliateView
  #events: ReaderEvents
  #saveTimer = 0
  #wheelPull = 0
  #wheelReset = 0
  #turning = false
  #touch: { y: number; atStart: boolean; atEnd: boolean } | null = null

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
    view.addEventListener('load', e => this.#listenTo((e as CustomEvent<{ doc: Document }>).detail.doc))
    this.#listenTo(view)

    this.apply(settings)
    await view.init({ lastLocation: lastPosition ?? undefined })
  }

  apply(settings: Settings) {
    const { renderer } = this.view
    if (!renderer) return
    renderer.setStyles?.(bookCSS(settings))
    if (!this.view.isFixedLayout) {
      const { gap, maxWidth } = MARGINS[settings.margins] ?? MARGINS.normal
      renderer.setAttribute('flow', settings.flow)
      renderer.setAttribute('max-inline-size', maxWidth)
      renderer.setAttribute('margin', settings.flow === 'paginated' ? '44px' : '28px')
      renderer.setAttribute('gap', gap)
    }
  }

  get flow(): Flow {
    return this.view.renderer?.scrolled ? 'scrolled' : 'paginated'
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
    const active = r && r.scrolled && !this.view.isFixedLayout
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
    if ((e.target as Element | null)?.closest?.('a, button, input, audio, video')) return
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
