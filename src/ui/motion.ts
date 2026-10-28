// Motion in Quiet: springs, sheets you can drag, and screens that slide.
// The springs follow Apple's model (WWDC 2023, "Animate with springs"): a spring is set by a
// duration and a bounce, with stiffness (2π / duration)² and damping ratio 1 − bounce. Each one is
// worked out here into a CSS linear() curve, so CSS transitions and animations move like it.
// See docs/research-0.3.3.md.

export const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)')

// The position (0 → 1) of a spring at time t, starting at rest or with velocity v (in lengths a second).
function springAt(t: number, duration: number, bounce: number, v = 0) {
  const w = 2 * Math.PI / duration
  const zeta = 1 - bounce
  if (zeta >= 1) return 1 + (-1 + (v - w) * t) * Math.exp(-w * t)
  const wd = w * Math.sqrt(1 - zeta * zeta)
  return 1 + Math.exp(-zeta * w * t) * (-Math.cos(wd * t) + (v - zeta * w) / wd * Math.sin(wd * t))
}

// A spring as a CSS easing, with how long it takes to settle (to within 0.1%).
export function spring(duration: number, bounce = 0, velocity = 0) {
  let settle = duration
  for (let t = duration; t < duration * 6; t += 0.01) {
    if (Math.abs(springAt(t, duration, bounce, velocity) - 1) < 0.001 &&
        Math.abs(springAt(t + 0.1, duration, bounce, velocity) - 1) < 0.001) { settle = t; break }
    settle = t
  }
  const steps = 48
  const points = Array.from({ length: steps + 1 }, (_, i) =>
    (i === steps ? 1 : springAt(settle * i / steps, duration, bounce, velocity)).toFixed(4))
  return { easing: `linear(${points.join(', ')})`, ms: Math.round(settle * 1000) }
}

const supportsLinear = typeof CSS !== 'undefined' && CSS.supports('transition-timing-function', 'linear(0, 1)')

// The app's springs, as CSS variables (styles in app.css use them):
//  --spring         smooth, no bounce: screens, sheets, most things     (Apple's "smooth")
//  --spring-quick   short, no bounce: presses, toggles, small changes   (about 100–250 ms)
//  --spring-bounce  a little bounce, for the end of a gesture or a "done" moment (Apple's "snappy")
export const SPRINGS = {
  '': spring(0.42, 0),
  '-quick': spring(0.22, 0),
  '-bounce': spring(0.42, 0.15),
}
export function installSprings(root = document.documentElement) {
  if (!supportsLinear) return   // the cubic-bezier fallbacks in app.css stay
  for (const [name, s] of Object.entries(SPRINGS)) {
    root.style.setProperty(`--spring${name}`, s.easing)
    root.style.setProperty(`--spring${name}-ms`, `${s.ms}ms`)
  }
}

// ---------- Screens ----------
// Moving between screens uses the View Transitions API where there is one: a book slides in
// from the right and goes back to the right (the same way it came); tabs cross-fade.
// Opening a book from its cover zooms the cover into the page, and closing it zooms back into the
// same cover (like iOS 18's zoom transition); see main.ts.
export type Navigation = 'push' | 'pop' | 'fade' | 'zoom-in' | 'zoom-out'
export function transition(kind: Navigation, change: () => void | Promise<unknown>, done?: () => void) {
  const doc = document as Document & { startViewTransition?: (cb: () => Promise<void>) => unknown }
  if (!doc.startViewTransition || reducedMotion.matches || document.hidden) { Promise.resolve(change()).finally(done); return }
  document.documentElement.dataset.nav = kind
  const t = doc.startViewTransition(async () => {
    // Waits for the new screen, but not forever (a slow book still zooms in while it loads).
    await Promise.race([Promise.resolve(change()), new Promise(resolve => setTimeout(resolve, 600))])
  }) as { finished: Promise<void> }
  t.finished.catch(() => {}).finally(() => { delete document.documentElement.dataset.nav; done?.() })
}

// ---------- Sheets you can drag ----------
// Drag a sheet down to close it, like iOS. The drag starts after 10 px (so taps and scrolling
// still work), follows the finger, and gives way softly when pulled the other way (rubber band).
// On release, where it would come to rest decides: past a third of its size, or flicked fast, it
// closes, carrying on at the finger's speed; otherwise it springs back.
// Touch events are used for fingers (so a drag at the top of a scrolled list can take over from
// scrolling), and pointer events for a mouse.
export function draggableSheet(sheet: HTMLElement, options: {
  close(): void
  enabled?: () => boolean            // e.g. only while it's a sheet (phones)
  handle?: HTMLElement[]             // always drags, even if the content is scrolled
  scroller?: HTMLElement             // drags only while this is scrolled to the top
  onDrag?: (fraction: number) => void   // 0 → 1 as it moves away, for the backdrop
  axis?: 'down' | 'left'             // the side panel closes to the left
}) {
  const axis = options.axis ?? 'down'
  let start: { x: number, y: number } | null = null
  let dragging = false
  let last = { pos: 0, t: 0 }, velocity = 0
  const size = () => axis === 'down' ? sheet.offsetHeight : sheet.offsetWidth
  const along = (x: number, y: number) => axis === 'down' ? y - start!.y : start!.x - x
  const across = (x: number, y: number) => axis === 'down' ? x - start!.x : y - start!.y
  const transform = (p: number) => axis === 'down' ? `translateY(${p}px)` : `translateX(${-p}px)`
  const backdrop = (f: number) => options.onDrag?.(Math.max(0, Math.min(1, f)))
  const place = (d: number) => {
    // Pulled the wrong way: rubber band, moving less and less the further the finger goes.
    sheet.style.transform = transform(d >= 0 ? d : -Math.pow(-d, 0.7))
    backdrop(d / size())
  }

  const begin = (x: number, y: number, t: number, target: EventTarget | null) => {
    if (options.enabled?.() === false) return
    const el = target as Element
    if (el.closest('input[type=range], textarea, select, .chart-columns, .chart-heatmap, .table-scroll, .ruler-handle')) return
    const onHandle = options.handle?.some(h => h.contains(el))
    if (axis === 'down' && !onHandle) {
      // Only from the top of the content: inside a scrolled list, a drag down scrolls it.
      for (let n: Element | null = el; n && n !== sheet.parentElement; n = n.parentElement)
        if (n.scrollTop > 0) return
    }
    start = { x, y }
    last = { pos: 0, t }
    velocity = 0
  }
  // Returns true while dragging (the caller then stops the page from scrolling).
  const move = (x: number, y: number, t: number) => {
    if (!start) return false
    const d = along(x, y)
    if (!dragging) {
      // Hysteresis: wait for 10 px, and only a drag the right way along.
      if (Math.abs(d) < 10 && Math.abs(across(x, y)) < 10) return false
      if (d <= 0 || Math.abs(across(x, y)) > Math.abs(d)) { start = null; return false }
      dragging = true
      sheet.classList.add('dragging')
      last = { pos: d, t }
    }
    const dt = Math.max(1, t - last.t)
    velocity = 0.7 * ((d - last.pos) / dt * 1000) + 0.3 * velocity   // px a second, smoothed
    last = { pos: d, t }
    place(d)
    return true
  }
  const end = (t: number) => {
    if (!start) return
    const wasDragging = dragging
    start = null
    dragging = false
    if (!wasDragging) return
    sheet.classList.remove('dragging')
    if (t - last.t > 80) velocity = 0      // the finger stopped before letting go
    const full = size()
    // Where it would come to rest if let go now (Apple's "projection", deceleration 0.998 a ms).
    const projected = last.pos + velocity / 1000 * 0.998 / (1 - 0.998)
    finish(projected > full / 3 || (velocity > 900 && last.pos > 24), last.pos, full)
  }
  const finish = (closing: boolean, from: number, full: number) => {
    const to = closing ? full : 0
    const distance = Math.abs(to - from) || 1
    // Carry on at the finger's speed, in lengths a second of the distance left.
    const v = Math.max(0, closing ? velocity : -velocity) / distance
    const s = spring(closing ? 0.3 : 0.42, closing ? 0 : 0.12, Math.min(v, 30))
    backdrop(closing ? 1 : 0)
    const animation = sheet.animate([{ transform: transform(from) }, { transform: transform(to) }],
      { duration: reducedMotion.matches ? 1 : s.ms, easing: supportsLinear ? s.easing : 'ease-out' })
    sheet.style.transform = transform(to)
    animation.finished.then(() => {
      if (closing) options.close()
      sheet.style.transform = ''
    }, () => {})
  }

  sheet.addEventListener('touchstart', e => {
    if (e.touches.length === 1) begin(e.touches[0].clientX, e.touches[0].clientY, e.timeStamp, e.target)
    else start = null
  }, { passive: true })
  sheet.addEventListener('touchmove', e => {
    if (e.touches.length !== 1) return
    if (move(e.touches[0].clientX, e.touches[0].clientY, e.timeStamp) && e.cancelable) e.preventDefault()
  }, { passive: false })
  sheet.addEventListener('touchend', e => end(e.timeStamp))
  sheet.addEventListener('touchcancel', () => { if (dragging) finish(false, last.pos, size()); start = null; dragging = false; sheet.classList.remove('dragging') })
  // A mouse drags too (by the handle or the top), for testing on a computer.
  sheet.addEventListener('pointerdown', e => {
    if (e.pointerType !== 'mouse' || e.button !== 0) return
    begin(e.clientX, e.clientY, e.timeStamp, e.target)
  })
  window.addEventListener('pointermove', e => { if (e.pointerType === 'mouse' && move(e.clientX, e.clientY, e.timeStamp)) e.preventDefault() })
  window.addEventListener('pointerup', e => { if (e.pointerType === 'mouse') end(e.timeStamp) })
}

// ---------- Sliding selection ----------
// In a row of choices (segmented controls, tabs), the mark for the chosen one slides to it instead
// of jumping. The row draws the mark itself (.choice-row::before in styles.css) from --x/--y/--w/--h.
const watched = new WeakSet<Element>()
const sizes = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(entries => {
  for (const e of entries) placeMark(e.target as HTMLElement, false)
})
export function placeMark(row: HTMLElement, animate = true) {
  const chosen = row.querySelector<HTMLElement>(':scope > .selected')
  if (!watched.has(row)) { watched.add(row); sizes?.observe(row) }
  if (!chosen || !row.offsetWidth) { row.removeAttribute('data-marked'); return }
  if (!animate || !row.hasAttribute('data-marked')) row.classList.add('no-slide')
  // Moved to another option: data-moved replays the style's own animation of the mark
  // (Paper draws its underline again; Old Tashkent's niche fills from the bottom).
  else if (row.style.getPropertyValue('--x') !== `${chosen.offsetLeft}px` || row.style.getPropertyValue('--y') !== `${chosen.offsetTop}px`) {
    row.removeAttribute('data-moved')
    void row.offsetWidth
    row.setAttribute('data-moved', '')
  }
  row.style.setProperty('--x', `${chosen.offsetLeft}px`)
  row.style.setProperty('--y', `${chosen.offsetTop}px`)
  row.style.setProperty('--w', `${chosen.offsetWidth}px`)
  row.style.setProperty('--h', `${chosen.offsetHeight}px`)
  row.setAttribute('data-marked', '')
  if (row.classList.contains('no-slide')) requestAnimationFrame(() => requestAnimationFrame(() => row.classList.remove('no-slide')))
}
export function placeMarks(root: ParentNode = document) {
  root.querySelectorAll<HTMLElement>('.choice-row').forEach(row => placeMark(row))
}

// A small spring "pop" on something just chosen or done (a star, a bookmark, a tab).
export function pop(el: Element) {
  if (reducedMotion.matches) return
  // Paper doesn't bounce.
  const s = SPRINGS[document.documentElement.dataset.style === 'paper' ? '' : '-bounce']
  el.animate([{ transform: 'scale(.82)' }, { transform: 'scale(1)' }], { duration: s.ms, easing: supportsLinear ? s.easing : 'ease-out' })
}

// Keeps the sliding marks in place as choices are chosen anywhere in the app.
export function watchMarks(root: HTMLElement = document.body) {
  placeMarks(root)
  new MutationObserver(records => {
    const rows = new Set<HTMLElement>()
    for (const r of records) {
      const el = r.target as HTMLElement
      if (r.type === 'attributes' && el.classList?.contains('choice') && el.parentElement?.classList.contains('choice-row')) rows.add(el.parentElement)
      if (r.type === 'childList') for (const n of r.addedNodes) if (n instanceof HTMLElement) {
        if (n.classList.contains('choice-row')) rows.add(n)
        n.querySelectorAll?.<HTMLElement>('.choice-row').forEach(row => rows.add(row))
      }
    }
    rows.forEach(row => placeMark(row))
  }).observe(root, { subtree: true, childList: true, attributes: true, attributeFilter: ['class'] })
}

// ---------- Scrolling like iOS ----------
// Past the end of a list, content keeps following the finger, but less and less (a rubber band).
// Let go and it eases back to the end; it never jumps. A fast flick into the end bounces softly.

// How far content moves for a pull of x in a space of size d: UIScrollView's rubber band.
export const rubber = (x: number, d: number, c = 0.55) => (1 - 1 / (x * c / d + 1)) * d
const unrubber = (y: number, d: number, c = 0.55) => y >= d * 0.999 ? d * 50 : d * (1 / (1 - y / d) - 1) / c

// A critically damped spring from offset y0 (px) moving at v0 (px a second) back to rest at 0,
// as keyframe values. Like Apple's springs with bounce 0: smooth, no overshoot past the end.
export function settle(y0: number, v0: number, duration = 0.5) {
  const w = 2 * Math.PI / duration
  const at = (t: number) => (y0 + (v0 + w * y0) * t) * Math.exp(-w * t)
  let end = 0.1
  while (end < 3 && (Math.abs(at(end)) > 0.3 || Math.abs(at(end + 0.05)) > 0.3)) end += 0.02
  const steps = 40
  return { values: Array.from({ length: steps + 1 }, (_, i) => i === steps ? 0 : at(end * i / steps)), ms: Math.round(end * 1000) }
}

// Plays an offset animation on a CSS length property (registered with @property in motion.css).
// While it plays the element is marked data-pulling (styles move its content only then).
export function animateOffset(el: HTMLElement, property: string, values: number[], ms: number) {
  el.style.removeProperty(property)
  if (reducedMotion.matches) { delete el.dataset.pulling; return null }
  el.dataset.pulling = ''
  const animation = el.animate(values.map(v => ({ [property]: `${v}px` })), { duration: ms, easing: 'linear' })
  animation.finished.then(() => { if (!el.style.getPropertyValue(property)) delete el.dataset.pulling }, () => {})
  return animation
}

// Gives a scrolling element the rubber band (and the bounce). Its children move by --pull.
// `edges`: 'bottom' for sheets, whose top edge drags the sheet itself down to close.
export function rubberBand(scroller: HTMLElement, options: { edges?: 'both' | 'bottom', onPull?: (px: number) => void } = {}) {
  const both = (options.edges ?? 'both') === 'both'
  scroller.dataset.rubber = ''
  let lastY = 0, lastT = 0, velocity = 0
  let pull = 0          // finger distance past the end: + at the top, − at the bottom
  let shown = 0         // how far the content is moved
  let touching = false
  let running: Animation | null = null
  const height = () => scroller.clientHeight || window.innerHeight
  const show = (px: number) => {
    shown = px
    if (px) { scroller.style.setProperty('--pull', `${px}px`); scroller.dataset.pulling = '' }
    else { scroller.style.removeProperty('--pull'); delete scroller.dataset.pulling }
    options.onPull?.(px)
  }
  const release = () => {
    if (!shown) { pull = 0; return }
    const { values, ms } = settle(shown, velocity)
    running = animateOffset(scroller, '--pull', values, ms)
    if (options.onPull) {
      const start = performance.now()
      const step = () => {
        const i = Math.min(values.length - 1, Math.floor((performance.now() - start) / ms * (values.length - 1)))
        options.onPull!(values[i])
        if (i < values.length - 1 && running?.playState === 'running') requestAnimationFrame(step)
      }
      requestAnimationFrame(step)
    }
    shown = 0
    pull = 0
  }

  scroller.addEventListener('touchstart', e => {
    touching = true
    if (e.touches.length !== 1) { if (pull) release(); return }
    lastY = e.touches[0].clientY
    lastT = e.timeStamp
    velocity = 0
    // Caught while still easing back: hold it where it is, and carry on from there.
    if (running?.playState === 'running') {
      const now = parseFloat(getComputedStyle(scroller).getPropertyValue('--pull')) || 0
      running.cancel()
      running = null
      show(now)
      pull = Math.sign(now) * unrubber(Math.abs(now), height())
    }
  }, { passive: true })

  scroller.addEventListener('touchmove', e => {
    if (e.touches.length !== 1) return
    const y = e.touches[0].clientY
    const dy = y - lastY
    const dt = Math.max(1, e.timeStamp - lastT)
    lastY = y
    lastT = e.timeStamp
    const max = scroller.scrollHeight - scroller.clientHeight
    if (!pull) {
      const atTop = scroller.scrollTop <= 0
      const atBottom = scroller.scrollTop >= max - 1
      if (dy > 0 && atTop && both) pull = dy
      else if (dy < 0 && atBottom) pull = dy
      else return
    } else {
      const before = pull
      pull += dy
      // Back past where the pull began: the list scrolls normally again.
      if (Math.sign(pull) !== Math.sign(before)) { pull = 0; show(0); return }
    }
    if (e.cancelable) e.preventDefault()
    const next = Math.sign(pull) * rubber(Math.abs(pull), height())
    velocity = 0.6 * ((next - shown) / dt * 1000) + 0.4 * velocity
    show(next)
  }, { passive: false })

  const end = () => { touching = false; release() }
  scroller.addEventListener('touchend', e => { if (!e.touches.length) end() })
  scroller.addEventListener('touchcancel', end)

  // A flick that runs into the end: the content carries on a little and eases back.
  let lastTop = scroller.scrollTop, lastScrollT = performance.now(), speed = 0, bouncedAt = 0
  scroller.addEventListener('scroll', () => {
    const now = performance.now()
    const top = scroller.scrollTop
    const dt = Math.max(1, now - lastScrollT)
    speed = 0.6 * ((top - lastTop) / dt) + 0.4 * speed           // px a ms
    lastTop = top
    lastScrollT = now
    if (touching || shown || now - bouncedAt < 400) return
    const max = scroller.scrollHeight - scroller.clientHeight
    const hitTop = top <= 0 && speed < -0.4 && both
    const hitBottom = top >= max - 1 && speed > 0.4 && max > 0
    if (!hitTop && !hitBottom) return
    bouncedAt = now
    // Its speed carries the content past the end; the amount is kept small, as on iOS.
    const v0 = Math.max(-1800, Math.min(1800, -speed * 1000 * 0.9))
    const { values, ms } = settle(0, v0, 0.45)
    running = animateOffset(scroller, '--pull', values, ms)
    speed = 0
  }, { passive: true })
}

// ---------- Icons that answer (like SF Symbols' effects) ----------
// Tapping a button plays its icon's own small animation: a gear turns, arrows nudge the way they
// point, a plus turns into place, a bin wiggles, most things bounce. Swapped icons (moon and sun)
// replace each other with a blur and a scale (motion.css).
type Effect = Keyframe[]
const bounce: Effect = [{ transform: 'scale(1)' }, { transform: 'scale(.78)', offset: .3 }, { transform: 'scale(1.12)', offset: .65 }, { transform: 'scale(1)' }]
const nudge = (x: number, y: number): Effect => [{ transform: 'none' }, { transform: `translate(${x}px, ${y}px)`, offset: .35 }, { transform: 'none' }]
const EFFECTS: [RegExp, Effect][] = [
  [/lucide-settings/, [{ transform: 'rotate(0)' }, { transform: 'rotate(90deg)' }]],
  [/lucide-plus/, [{ transform: 'rotate(0) scale(1)' }, { transform: 'rotate(90deg) scale(.8)', offset: .5 }, { transform: 'rotate(180deg) scale(1)' }]],
  [/lucide-(arrow|chevron)-left|lucide-undo/, nudge(-4, 0)],
  [/lucide-(arrow|chevron)-right/, nudge(4, 0)],
  [/lucide-(arrow|chevron)-up|lucide-a-arrow-up|lucide-zoom-in/, nudge(0, -3)],
  [/lucide-(arrow|chevron)-down|lucide-a-arrow-down|lucide-zoom-out|lucide-download/, nudge(0, 3)],
  [/lucide-(trash|tag|bell|x)\b/, [{ transform: 'rotate(0)' }, { transform: 'rotate(-14deg)', offset: .2 }, { transform: 'rotate(11deg)', offset: .45 }, { transform: 'rotate(-6deg)', offset: .7 }, { transform: 'rotate(0)' }]],
  [/lucide-ellipsis/, [{ transform: 'scaleX(1)' }, { transform: 'scaleX(1.35)', offset: .4 }, { transform: 'scaleX(1)' }]],
  [/lucide-search/, [{ transform: 'none' }, { transform: 'rotate(-12deg) scale(1.12)', offset: .4 }, { transform: 'none' }]],
]
export function symbolEffect(svg: Element) {
  if (reducedMotion.matches) return
  const name = svg.getAttribute('class') ?? ''
  const frames = EFFECTS.find(([re]) => re.test(name))?.[1] ?? bounce
  const s = SPRINGS['-bounce']
  svg.animate(frames, { duration: Math.max(380, s.ms * 0.8), easing: frames === bounce ? 'ease-out' : supportsLinear ? s.easing : 'ease-out' })
}
export function watchSymbols(root: HTMLElement = document.body) {
  root.addEventListener('click', e => {
    const button = (e.target as Element).closest?.('button')
    if (!button || button.disabled || button.closest('.choice-row')) return
    const svg = [...button.querySelectorAll('svg.lucide')].find(s => (s as HTMLElement).offsetParent !== null || getComputedStyle(s).display !== 'none')
    if (svg) symbolEffect(svg)
  })
}

// ---------- Where the finger is ----------
// The last place a finger or mouse went down: menus open from it, and Frosty Glass lights up there.
export const lastPointer = { x: innerWidth / 2, y: innerHeight / 2 }
document.addEventListener('pointerdown', e => {
  lastPointer.x = e.clientX
  lastPointer.y = e.clientY
  const target = (e.target as Element).closest?.<HTMLElement>('button, .chip, .choice, .tab')
  if (target) {
    const r = target.getBoundingClientRect()
    target.style.setProperty('--px', `${e.clientX - r.left}px`)
    target.style.setProperty('--py', `${e.clientY - r.top}px`)
  }
}, { capture: true, passive: true })

// ---------- Numbers that roll ----------
// Numbers count up to their value when they appear (the stats), like iOS's numeric text.
export function rollNumbers(els: Iterable<HTMLElement>) {
  if (reducedMotion.matches) return
  for (const el of els) {
    const final = el.textContent ?? ''
    const numbers = [...final.matchAll(/\d[\d,]*/g)].map(m => Number(m[0].replace(/,/g, '')))
    if (!numbers.some(n => n > 0)) continue
    const start = performance.now()
    const ms = 900
    el.classList.add('rolling')
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / ms)
      const k = 1 - Math.pow(1 - t, 3)
      let i = 0
      el.textContent = final.replace(/\d[\d,]*/g, m => {
        const n = numbers[i++]
        const v = Math.round(n * k)
        return m.includes(',') ? v.toLocaleString('en') : String(v)
      })
      if (t < 1) requestAnimationFrame(step)
      else { el.textContent = final; el.classList.remove('rolling') }
    }
    requestAnimationFrame(step)
  }
}

// ---------- Lists that rearrange ----------
// When books are sorted, filtered or removed, each one glides to its new place instead of the
// list redrawing at once (each item gets a view-transition-name for the change).
export function animateLayout(container: HTMLElement, selector: string, idOf: (el: HTMLElement) => string | undefined, change: () => void | Promise<void>) {
  const doc = document as Document & { startViewTransition?: (cb: () => Promise<void>) => { finished: Promise<void> } }
  if (!doc.startViewTransition || reducedMotion.matches || document.documentElement.dataset.nav) { change(); return }
  const name = (el: HTMLElement) => {
    const id = idOf(el)
    if (!id) return
    const r = el.getBoundingClientRect()
    // Only what's on screen (or near it) moves; the rest just appears.
    if (r.bottom < -200 || r.top > innerHeight + 200) return
    el.style.setProperty('view-transition-name', `item-${id.replace(/[^a-zA-Z0-9_-]/g, '')}`)
    el.style.setProperty('view-transition-class', 'list-item')
  }
  const clear = () => container.querySelectorAll<HTMLElement>(selector).forEach(el => { el.style.removeProperty('view-transition-name'); el.style.removeProperty('view-transition-class') })
  container.querySelectorAll<HTMLElement>(selector).forEach(name)
  document.documentElement.dataset.layout = ''
  const t = doc.startViewTransition(async () => {
    clear()
    await change()
    container.querySelectorAll<HTMLElement>(selector).forEach(name)
  })
  t.finished.catch(() => {}).finally(() => { clear(); delete document.documentElement.dataset.layout })
}
