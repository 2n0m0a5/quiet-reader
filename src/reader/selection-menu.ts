import { Copy, Languages, NotebookPen, Search, Trash2 } from 'lucide'
import { h, icon } from '../ui/dialog'

// Highlight colours: soft enough to read through on every theme.
export const HIGHLIGHT_COLORS = [
  { name: 'Yellow', value: '#f2c94c' },
  { name: 'Green', value: '#7fbf7a' },
  { name: 'Blue', value: '#6fa8dc' },
  { name: 'Pink', value: '#e88ca5' },
]

export interface SelectionActions {
  color(value: string): void
  note(): void
  copy(): void
  lookUp(): void
  translate(): void
  remove?(): void   // only for an existing highlight
}

let menu: HTMLElement | null = null
let menuKind: 'selection' | 'highlight' | null = null

// Hides the menu; with a kind, only if the menu showing is of that kind.
export function hideSelectionMenu(kind?: 'selection' | 'highlight') {
  if (kind && kind !== menuKind) return
  menu?.remove()
  menu = null
  menuKind = null
}

// A small floating bar next to the selected text (or a tapped highlight).
export function showSelectionMenu(rect: DOMRect, actions: SelectionActions, currentColor?: string) {
  hideSelectionMenu()
  menuKind = actions.remove ? 'highlight' : 'selection'
  menu = h('div', 'selection-menu')
  menu.setAttribute('role', 'toolbar')
  menu.setAttribute('aria-label', 'Selected text')
  // Keep taps on the menu from reaching the page underneath.
  menu.addEventListener('pointerdown', e => e.stopPropagation())

  const colors = h('div', 'selection-colors')
  for (const c of HIGHLIGHT_COLORS) {
    const dot = h('button', `color-dot${c.value === currentColor ? ' selected' : ''}`)
    dot.style.background = c.value
    dot.title = `Highlight ${c.name.toLowerCase()}`
    dot.setAttribute('aria-label', dot.title)
    dot.addEventListener('click', () => { hideSelectionMenu(); actions.color(c.value) })
    colors.append(dot)
  }
  menu.append(colors)

  const item = (label: string, symbol: Parameters<typeof icon>[0], run: () => void, className = '') => {
    const b = h('button', `selection-action ${className}`)
    b.title = label
    b.setAttribute('aria-label', label)
    b.append(icon(symbol))
    b.addEventListener('click', () => { hideSelectionMenu(); run() })
    menu!.append(b)
  }
  item('Note', NotebookPen, actions.note)
  item('Copy', Copy, actions.copy)
  item('Look up', Search, actions.lookUp)
  item('Translate', Languages, actions.translate)
  if (actions.remove) item('Delete highlight', Trash2, actions.remove, 'danger-text')

  document.body.append(menu)

  // Below the selection (Android shows its own copy/paste bar above it), or above if there's no room.
  const { width, height } = menu.getBoundingClientRect()
  const margin = 10
  let top = rect.bottom + margin
  if (top + height > window.innerHeight - margin) top = Math.max(margin, rect.top - height - margin)
  const left = Math.min(Math.max(margin, rect.left + rect.width / 2 - width / 2), window.innerWidth - width - margin)
  menu.style.top = `${top}px`
  menu.style.left = `${left}px`
}
