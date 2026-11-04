// The tour: short tips shown on the real screens, one at a time. The thing a tip is about is
// lit up (the rest dims), with Back, Next and Skip. Starts for new readers, and from About.
import { button, h } from '../ui/dialog'

interface Step { target?: string; title: string; text: string; before?: () => void }

const DONE_KEY = 'quiet:tour-done'
export const tourDone = () => { try { return !!localStorage.getItem(DONE_KEY) } catch { return true } }

export function startTour(go: { library(): void; settings(): void }) {
  const steps: Step[] = [
    { title: 'Welcome to Quiet', text: 'A reader with nothing extra. Your books, your place in them and your notes stay on this phone. Seven short tips; skip any time.', before: go.library },
    { target: '#openFile', title: 'Add books', text: 'EPUB, PDF, Word, comics, FB2, MOBI and more. You can also choose a whole folder: Quiet adds every book in it.' },
    { target: '#library [data-book-id]', title: 'Open a book', text: 'Tap a cover to read. Press and hold it for everything about the book: length, file, your reading.' },
    { target: '#tabbar', title: 'Four places', text: 'Library, Notes (your highlights and quotes), Stats (your reading) and Settings.' },
    { title: 'While you read', text: 'The bar at the bottom: Contents, Day/Night, Light, Text and More. More has every other control, and lets you choose the four keys.' },
    { title: 'Mark what matters', text: 'Select words to highlight, add a note, save a quote, or translate. They all gather in Notes.' },
    { target: '#openSettings', title: 'Keep a backup', text: 'Settings → Files & storage → Back up: one file with everything. Keep it somewhere safe.', before: go.library },
  ]
  let i = 0
  const shade = h('div', 'tour-shade')
  const ring = h('div', 'tour-ring')
  const tip = h('div', 'tour-tip')
  tip.setAttribute('role', 'dialog')
  tip.setAttribute('aria-live', 'polite')
  document.body.append(shade, ring, tip)
  const end = () => {
    shade.remove(); ring.remove(); tip.remove()
    window.removeEventListener('resize', show)
    try { localStorage.setItem(DONE_KEY, '1') } catch { /* storage unavailable */ }
  }
  function show() {
    const step = steps[i]
    step.before?.()
    requestAnimationFrame(() => {
      const el = step.target ? document.querySelector<HTMLElement>(step.target) : null
      const r = el && el.getBoundingClientRect().width ? el.getBoundingClientRect() : null
      ring.hidden = !r
      shade.classList.toggle('full', !r)
      if (r) Object.assign(ring.style, { left: `${r.left - 6}px`, top: `${r.top - 6}px`, width: `${r.width + 12}px`, height: `${r.height + 12}px` })
      const count = h('div', 'tour-count', `${String(i + 1).padStart(2, '0')} / ${String(steps.length).padStart(2, '0')}`)
      const actions = h('div', 'tour-actions')
      const skip = button(i === steps.length - 1 ? 'Close' : 'Skip tour', 'link-button', end)
      const back = button('Back', '', () => { i--; show() })
      const next = button(i === steps.length - 1 ? 'Done' : 'Next', 'primary', () => { if (i === steps.length - 1) end(); else { i++; show() } })
      if (i === 0) back.setAttribute('disabled', '')
      actions.append(skip, h('span', 'tour-gap'), back, next)
      tip.replaceChildren(count, h('div', 'tour-title', step.title), h('p', 'tour-text', step.text), actions)
      // Above the lit thing if it's low on the screen, below it otherwise; centred without one.
      const below = r ? r.top < innerHeight / 2 : false
      tip.style.top = r ? (below ? `${Math.min(innerHeight - 220, r.bottom + 14)}px` : '') : ''
      tip.style.bottom = r && !below ? `${innerHeight - r.top + 14}px` : ''
      tip.classList.toggle('centred', !r)
      next.focus()
    })
  }
  window.addEventListener('resize', show)
  show()
}
