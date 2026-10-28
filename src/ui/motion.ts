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
export type Navigation = 'push' | 'pop' | 'fade'
export function transition(kind: Navigation, change: () => void) {
  const doc = document as Document & { startViewTransition?: (cb: () => void) => unknown }
  if (!doc.startViewTransition || reducedMotion.matches || document.hidden) return change()
  document.documentElement.dataset.nav = kind
  const t = doc.startViewTransition(change) as { finished: Promise<void> }
  t.finished.finally(() => { delete document.documentElement.dataset.nav })
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
  const s = SPRINGS['-bounce']
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
