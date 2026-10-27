import { Copy, Highlighter, Languages, NotebookPen, Quote, Search, Strikethrough, Tag, Trash2, Underline } from 'lucide'
import { h, icon } from '../ui/dialog'
import type { MarkStyle } from '../library/db'

// Highlight colours: soft enough to read through on every theme. Underlines and strikethroughs
// are thin, so they use a stronger shade (ink) of the same colour.
export const HIGHLIGHT_COLORS = [
  { name: 'Yellow', value: '#f2c94c', ink: '#d19a1c' },
  { name: 'Green', value: '#7fbf7a', ink: '#4c9a47' },
  { name: 'Blue', value: '#6fa8dc', ink: '#3b7fc4' },
  { name: 'Pink', value: '#e88ca5', ink: '#d2557a' },
]
export const inkOf = (color: string) => HIGHLIGHT_COLORS.find(c => c.value === color)?.ink ?? color

export interface SelectionActions {
  color(value: string): void
  style(style: MarkStyle): void
  note(): void
  quote(): void
  tags(): void
  copy(): void
  lookUp(): void
  translate(): void
  remove?(): void   // only for an existing highlight
}

// What's chosen now, so the menu can show it (for an existing highlight).
export interface SelectionState { color?: string; style?: MarkStyle; quote?: boolean }

let menu: HTMLElement | null = null
let menuKind: 'selection' | 'highlight' | null = null

// Hides the menu; with a kind, only if the menu showing is of that kind.
export function hideSelectionMenu(kind?: 'selection' | 'highlight') {
  if (kind && kind !== menuKind) return
  menu?.remove()
  menu = null
  menuKind = null
}

const STYLES: [Exclude<MarkStyle, 'plain'>, string, Parameters<typeof icon>[0]][] = [
  ['highlight', 'Highlight', Highlighter], ['underline', 'Underline', Underline], ['strike', 'Strikethrough', Strikethrough],
]

// A small floating bar next to the selected text (or a tapped highlight): colours and styles on
// top, actions below.
export function showSelectionMenu(rect: DOMRect, actions: SelectionActions, state: SelectionState = {}) {
  hideSelectionMenu()
  menuKind = actions.remove ? 'highlight' : 'selection'
  menu = h('div', 'selection-menu')
  menu.setAttribute('role', 'toolbar')
  menu.setAttribute('aria-label', 'Selected text')
  // Keep taps on the menu from reaching the page underneath.
  menu.addEventListener('pointerdown', e => e.stopPropagation())

  const upper = h('div', 'selection-row')
  const colors = h('div', 'selection-colors')
  const marked = state.style && state.style !== 'plain'
  for (const c of HIGHLIGHT_COLORS) {
    const dot = h('button', `color-dot${marked && c.value === state.color ? ' selected' : ''}`)
    dot.style.background = c.value
    dot.title = `${c.name}`
    dot.setAttribute('aria-label', `Mark in ${c.name.toLowerCase()}`)
    dot.addEventListener('click', () => { hideSelectionMenu(); actions.color(c.value) })
    colors.append(dot)
  }
  const styles = h('div', 'selection-styles')
  for (const [style, label, symbol] of STYLES) {
    const b = h('button', `selection-action style-button${marked && state.style === style ? ' selected' : ''}`)
    b.title = label
    b.setAttribute('aria-label', label)
    b.setAttribute('aria-pressed', String(!!marked && state.style === style))
    b.append(icon(symbol))
    b.addEventListener('click', () => { hideSelectionMenu(); actions.style(style) })
    styles.append(b)
  }
  upper.append(colors, styles)

  const bottom = h('div', 'selection-row')
  const item = (label: string, symbol: Parameters<typeof icon>[0], run: () => void, className = '') => {
    const b = h('button', `selection-action ${className}`)
    b.title = label
    b.setAttribute('aria-label', label)
    b.append(icon(symbol))
    b.addEventListener('click', () => { hideSelectionMenu(); run() })
    bottom.append(b)
    return b
  }
  item('Note', NotebookPen, actions.note)
  const quote = item(state.quote ? 'Remove from Quotes' : 'Save as a quote', Quote, actions.quote, state.quote ? 'selected' : '')
  quote.setAttribute('aria-pressed', String(!!state.quote))
  item('Tags', Tag, actions.tags)
  item('Copy', Copy, actions.copy)
  item('Look up', Search, actions.lookUp)
  item('Translate', Languages, actions.translate)
  if (actions.remove) item('Delete', Trash2, actions.remove, 'danger-text')
  menu.append(upper, bottom)

  document.body.append(menu)

  // Below the selection (Android shows its own copy/paste bar above it), or above if there's no room.
  const { width, height } = menu.getBoundingClientRect()
  const margin = 10
  let top = rect.bottom + margin
  if (top + height > window.innerHeight - margin) top = Math.max(margin, rect.top - height - margin)
  top = Math.min(top, window.innerHeight - height - margin)   // always on screen
  const left = Math.min(Math.max(margin, rect.left + rect.width / 2 - width / 2), window.innerWidth - width - margin)
  // The menu is scaled by the app text size (CSS zoom), which also scales its top/left.
  const zoom = Number(getComputedStyle(document.documentElement).getPropertyValue('--ui-zoom')) || 1
  menu.style.top = `${top / zoom}px`
  menu.style.left = `${left / zoom}px`
}
