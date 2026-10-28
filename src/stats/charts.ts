// Small charts for the Reading screen, drawn with plain elements and styled in app.css.
// Bars and cells are read by their length and position (the most accurate for the eye);
// tapping, hovering or focusing one shows its value in the line above the chart, and every
// chart can show its numbers as a table, so nothing depends on colour or on hovering.
import { h } from '../ui/dialog'

export interface Column {
  label: string        // under the bar ('' to leave it out)
  value: number
  tip: string          // what the readout says for this bar
  now?: boolean        // the current day, week or month: drawn in the full colour
}

// A line above a chart that says what it shows, or the value of the bar you're touching.
export function readout(text: string) {
  const el = h('div', 'chart-readout', text)
  el.setAttribute('aria-live', 'polite')
  el.dataset.default = text
  return el
}

// Shows a mark's value in the readout while it's touched, hovered or focused.
function explain(mark: HTMLElement, out: HTMLElement, text: string) {
  mark.tabIndex = 0
  mark.setAttribute('aria-label', text)
  const show = () => {
    out.textContent = text
    out.parentElement?.querySelectorAll('.picked').forEach(m => m.classList.remove('picked'))
    mark.classList.add('picked')
  }
  const hide = () => {
    out.textContent = out.dataset.default ?? ''
    mark.classList.remove('picked')
  }
  mark.addEventListener('pointerenter', show)
  mark.addEventListener('pointerdown', show)
  mark.addEventListener('focus', show)
  mark.addEventListener('pointerleave', e => { if (e.pointerType === 'mouse') hide() })
  mark.addEventListener('blur', hide)
}

// Columns rising from one baseline. `max` keeps several charts on the same scale.
export function columns(items: Column[], out: HTMLElement, { max, className = '' }: { max?: number, className?: string } = {}) {
  const top = max ?? Math.max(...items.map(i => i.value), 1)
  const chart = h('div', `chart-columns ${className}`.trim())
  chart.setAttribute('role', 'group')
  items.forEach((item, i) => {
    const col = h('div', `chart-col${item.now ? ' now' : ''}`)
    col.style.setProperty('--i', String(i))
    const track = h('div', 'chart-track')
    const bar = h('div', 'chart-bar')
    bar.style.setProperty('--h', `${item.value ? Math.max(3, item.value / top * 100) : 0}%`)
    track.append(bar)
    col.append(track, h('div', 'chart-label', item.label))
    explain(col, out, item.tip)
    chart.append(col)
  })
  return chart
}

// One square per day, a column per week (Monday at the top), shaded in five steps of one colour.
export function heatmap(days: { day: string, seconds: number, future: boolean, tip: string }[], out: HTMLElement) {
  const max = Math.max(...days.map(d => d.seconds), 1)
  // Steps: none, then four equal parts of the busiest day (at least a minute counts as some).
  const step = (s: number) => s < 60 ? 0 : Math.min(4, Math.ceil(s / max * 4))
  const grid = h('div', 'chart-heatmap')
  grid.setAttribute('role', 'group')
  days.forEach((d, i) => {
    const cell = h('div', `heat-cell${d.future ? ' future' : ''}`)
    cell.dataset.step = String(step(d.seconds))
    cell.style.setProperty('--i', String(Math.floor(i / 7)))
    if (!d.future) explain(cell, out, d.tip)
    grid.append(cell)
  })
  const legend = h('div', 'heat-legend')
  legend.setAttribute('aria-hidden', 'true')
  legend.append(h('span', '', 'Less'))
  for (let s = 0; s <= 4; s++) {
    const swatch = h('span', 'heat-cell')
    swatch.dataset.step = String(s)
    legend.append(swatch)
  }
  legend.append(h('span', '', 'More'))
  const wrap = h('div', 'chart-heatmap-wrap')
  wrap.append(grid, legend)
  return wrap
}

// The same numbers as a table, folded away under the chart.
export function numbers(headers: string[], rows: (string | number)[][], summary = 'Show the numbers') {
  const details = h('details', 'chart-numbers')
  details.append(h('summary', '', summary), table(headers, rows))
  return details
}

export function table(headers: string[], rows: (string | number)[][], className = '') {
  const t = h('table', `chart-table ${className}`.trim())
  const head = h('tr')
  for (const text of headers) {
    const th = h('th', '', text)
    th.scope = 'col'
    head.append(th)
  }
  t.append(h('thead'), h('tbody'))
  t.tHead!.append(head)
  for (const row of rows) {
    const tr = h('tr')
    row.forEach((cell, i) => tr.append(h(i ? 'td' : 'th', typeof cell === 'number' ? 'num' : '', String(cell))))
    tr.firstElementChild?.setAttribute('scope', 'row')
    t.tBodies[0].append(tr)
  }
  return t
}

// Charts grow into place when they first scroll into view (once).
const watcher = typeof IntersectionObserver === 'undefined' ? null : new IntersectionObserver(entries => {
  for (const e of entries) if (e.isIntersecting) { e.target.classList.add('grown'); watcher?.unobserve(e.target) }
}, { threshold: 0.25 })
export function growWhenSeen(el: HTMLElement) {
  if (watcher) watcher.observe(el)
  else el.classList.add('grown')
}
