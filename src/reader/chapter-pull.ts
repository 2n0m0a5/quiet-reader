// Moving between chapters in scroll mode, the iOS way.
// At the end of a chapter the page keeps following the finger with growing resistance (a rubber
// band), and a small sign below it says what letting go will do. Past a set distance the sign
// changes to "Release for the next chapter"; only when the finger lets go does the next chapter
// come in. Let go before that and the page eases back to the end of the chapter, never jumping.
// The same works at the start of a chapter (for the previous one), and with a mouse wheel:
// the chapter turns when the wheel stops, if it was pulled far enough.
import { rubber, settle, spring, reducedMotion } from '../ui/motion'

const ARM = 88          // px the page has to move before letting go turns the chapter
const WHEEL_STOP = 160  // ms without wheel movement that counts as letting go

type Edges = () => { active: boolean; atStart: boolean; atEnd: boolean }

export class ChapterPull {
  #view: HTMLElement
  #sign: HTMLElement
  #label: HTMLElement
  #edges: Edges
  #turn: (dir: 1 | -1) => Promise<void>
  #hasMore: (dir: 1 | -1) => boolean
  #onArm: () => void
  #dir: 0 | 1 | -1 = 0      // 1: pulling past the end, −1: past the start
  #pull = 0                 // finger distance past the edge
  #shown = 0                // how far the page is moved
  #armed = false
  #busy = false
  #lastY = 0
  #lastT = 0
  #velocity = 0
  #wheelTimer = 0
  #running: Animation | null = null
  #touching = false

  constructor(view: HTMLElement, host: HTMLElement, options: {
    edges: Edges
    turn: (dir: 1 | -1) => Promise<void>
    hasMore: (dir: 1 | -1) => boolean
    onArm: () => void          // a light tap when it arms (haptics)
  }) {
    this.#view = view
    this.#edges = options.edges
    this.#turn = options.turn
    this.#hasMore = options.hasMore
    this.#onArm = options.onArm
    this.#sign = document.createElement('div')
    this.#sign.className = 'chapter-pull'
    this.#sign.setAttribute('aria-hidden', 'true')
    const mark = document.createElement('div')
    mark.className = 'chapter-pull-mark'
    mark.innerHTML = '<svg viewBox="0 0 24 24"><path class="pull-star" d="M12 2.8 14.7 5.5H18.5V9.3L21.2 12 18.5 14.7V18.5H14.7L12 21.2 9.3 18.5H5.5V14.7L2.8 12 5.5 9.3V5.5H9.3Z"/>'
      + '<g class="pull-rosette"><circle cx="12" cy="12" r="10"/><path d="M12 2.5c2.2 3.2 2.2 6.3 0 9.5-2.2-3.2-2.2-6.3 0-9.5zM12 21.5c2.2-3.2 2.2-6.3 0-9.5-2.2 3.2-2.2 6.3 0 9.5zM2.5 12c3.2-2.2 6.3-2.2 9.5 0-3.2 2.2-6.3 2.2-9.5 0zM21.5 12c-3.2-2.2-6.3-2.2-9.5 0 3.2 2.2 6.3 2.2 9.5 0z"/><circle cx="12" cy="12" r="1.6"/></g>'
      + '<path class="pull-arrow" d="M12 5v14M6 13l6 6 6-6"/></svg>'
      + '<span class="pull-line"></span><span class="pull-dot"></span>'
    this.#label = document.createElement('div')
    this.#label.className = 'chapter-pull-label'
    this.#sign.append(mark, this.#label)
    host.append(this.#sign)
  }

  // Touches and the wheel, in the main page and inside each chapter's frame.
  listen(target: Document | HTMLElement) {
    // Where a finger is on the screen. Inside a chapter's frame, its own coordinates are shifted
    // by where the frame is (it moves as the chapter scrolls).
    const frame = target instanceof Document ? target.defaultView?.frameElement as HTMLElement | null : null
    const screenY = (t: Touch) => {
      if (!frame) return t.clientY
      const rect = frame.getBoundingClientRect()
      return rect.top + t.clientY * (frame.offsetHeight ? rect.height / frame.offsetHeight : 1)
    }
    target.addEventListener('touchstart', e => this.#start(e as TouchEvent, screenY), { passive: true })
    target.addEventListener('touchmove', e => this.#move(e as TouchEvent, screenY), { passive: false })
    target.addEventListener('touchend', e => { if (!(e as TouchEvent).touches.length) { this.#touching = false; this.#release() } })
    target.addEventListener('touchcancel', () => { this.#touching = false; this.#release(false) })
    target.addEventListener('wheel', e => this.#wheel(e as WheelEvent), { passive: true })
  }

  // A flick that runs into the end of a chapter carries the page on a little, and it eases back.
  bounce(dir: 1 | -1, speed: number) {
    if (this.#dir || this.#busy || this.#touching || reducedMotion.matches) return
    const v0 = -dir * Math.min(1600, Math.abs(speed) * 1000 * 0.8)
    const { values, ms } = settle(0, v0, 0.45)
    this.#running?.cancel()
    this.#running = this.#view.animate(values.map(v => ({ transform: `translateY(${v}px)` })), { duration: ms, easing: 'linear' })
  }

  #start(e: TouchEvent, screenY: (t: Touch) => number) {
    this.#touching = true
    if (e.touches.length !== 1) { if (this.#dir) this.#release(false); return }
    this.#lastY = screenY(e.touches[0])
    this.#lastT = e.timeStamp
    this.#velocity = 0
  }

  #move(e: TouchEvent, screenY: (t: Touch) => number) {
    if (this.#busy || e.touches.length !== 1) return
    const y = screenY(e.touches[0])
    const dy = y - this.#lastY
    const dt = Math.max(1, e.timeStamp - this.#lastT)
    this.#lastY = y
    this.#lastT = e.timeStamp
    if (!this.#dir) {
      const { active, atStart, atEnd } = this.#edges()
      if (!active) return
      if (dy < 0 && atEnd) this.#dir = 1
      else if (dy > 0 && atStart) this.#dir = -1
      else return
      this.#pull = 0
      this.#running?.cancel()
      this.#sign.dataset.edge = this.#dir > 0 ? 'end' : 'start'
    }
    this.#pull += -dy * this.#dir
    if (this.#pull <= 0) { this.#reset(); return }     // back into the chapter: normal scrolling
    if (e.cancelable) e.preventDefault()
    const before = this.#shown
    this.#show(rubber(this.#pull, window.innerHeight))
    this.#velocity = 0.6 * ((this.#shown - before) / dt * 1000) + 0.4 * this.#velocity
  }

  #wheel(e: WheelEvent) {
    if (this.#busy) return
    const { active, atStart, atEnd } = this.#edges()
    const dir = e.deltaY > 0 ? 1 : -1
    const pushing = active && ((dir > 0 && atEnd) || (dir < 0 && atStart))
    if (!this.#dir) {
      if (!pushing) return
      this.#dir = dir
      this.#pull = 0
      this.#sign.dataset.edge = dir > 0 ? 'end' : 'start'
    }
    this.#pull += Math.abs(e.deltaY) * 0.5 * (dir === this.#dir ? 1 : -2.5)
    if (this.#pull <= 0) { this.#release(false); return }
    this.#show(rubber(this.#pull, window.innerHeight))
    window.clearTimeout(this.#wheelTimer)
    this.#wheelTimer = window.setTimeout(() => this.#release(), WHEEL_STOP)
  }

  #show(px: number) {
    this.#shown = px
    this.#view.style.transform = px ? `translateY(${-this.#dir * px}px)` : ''
    const more = this.#dir !== 0 && this.#hasMore(this.#dir as 1 | -1)
    const progress = Math.min(1, px / ARM)
    const armed = more && px >= ARM
    this.#sign.style.setProperty('--progress', progress.toFixed(3))
    this.#sign.style.setProperty('--shown', `${px}px`)
    this.#sign.classList.toggle('visible', px > 4)
    this.#sign.classList.toggle('armed', armed)
    this.#label.textContent = !more ? (this.#dir > 0 ? 'The end of the book' : 'The start of the book')
      : `${armed ? 'Release' : 'Pull'} for the ${this.#dir > 0 ? 'next' : 'previous'} chapter`
    if (armed && !this.#armed) this.#onArm()
    this.#armed = armed
  }

  // Let go: past the mark, on to the next chapter; before it, ease back to the end of this one.
  async #release(allowTurn = true) {
    const dir = this.#dir
    if (!dir) return
    if (allowTurn && this.#armed) return this.#go(dir)
    const { values, ms } = settle(-dir * this.#shown, -dir * this.#velocity)
    this.#reset(false)
    this.#running = this.#view.animate(values.map(v => ({ transform: `translateY(${v}px)` })),
      { duration: reducedMotion.matches ? 1 : ms, easing: 'linear' })
  }

  async #go(dir: 1 | -1) {
    this.#busy = true
    const from = -dir * this.#shown
    const h = window.innerHeight
    this.#reset(false)
    this.#sign.classList.add('turning')
    const out = this.#view.animate([
      { transform: `translateY(${from}px)`, opacity: 1 },
      { transform: `translateY(${-dir * h * 0.35}px)`, opacity: 0 },
    ], { duration: reducedMotion.matches ? 1 : 200, easing: 'cubic-bezier(.4, 0, 1, 1)', fill: 'forwards' })
    await out.finished.catch(() => {})
    try { await this.#turn(dir) } finally {
      const s = spring(0.42, 0)
      const enter = this.#view.animate([
        { transform: `translateY(${dir * h * 0.3}px)`, opacity: 0 },
        { transform: 'none', opacity: 1 },
      ], { duration: reducedMotion.matches ? 1 : s.ms, easing: s.easing })
      out.cancel()
      this.#sign.classList.remove('turning')
      await enter.finished.catch(() => {})
      this.#busy = false
    }
  }

  #reset(clear = true) {
    if (clear || this.#dir) this.#show(0)
    this.#view.style.transform = ''
    this.#dir = 0
    this.#pull = 0
    this.#armed = false
    this.#sign.classList.remove('visible', 'armed')
  }
}
