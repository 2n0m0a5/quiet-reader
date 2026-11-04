// The reader bar: four keys you choose, then More. Everything else lives in More (the reading
// panel, with a grid of every control at its top), so removing a key never removes a feature.
import { ALargeSmall, Bookmark, Hash, Info, List, NotebookText, Search, Settings2, SlidersHorizontal, Sun, SunMoon } from 'lucide'
import { button, h, icon, openDialog } from './ui/dialog'

export interface KeyActions {
  contents(): void
  dayNight(): void
  light(): void            // the reading panel, at brightness and warmth
  text(): void             // the reading panel, at text size and font
  search(): void
  bookmark(): void
  notes(): void
  goTo(): void
  info(): void
  look(): void             // the reading panel, at the top
}

type KeyId = 'contents' | 'daynight' | 'light' | 'text' | 'search' | 'bookmark' | 'notes' | 'goto' | 'info' | 'look'

const KEYS: Record<KeyId, { label: string; hint: string; icon: Parameters<typeof icon>[0]; run: (a: KeyActions) => void }> = {
  contents: { label: 'Contents', hint: 'Chapters', icon: List, run: a => a.contents() },
  daynight: { label: 'Day/Night', hint: 'Switch the page between Day and Night', icon: SunMoon, run: a => a.dayNight() },
  light: { label: 'Light', hint: 'Brightness and warmth', icon: Sun, run: a => a.light() },
  text: { label: 'Text', hint: 'Size and font', icon: ALargeSmall, run: a => a.text() },
  search: { label: 'Search', hint: 'Find words in the book', icon: Search, run: a => a.search() },
  bookmark: { label: 'Bookmark', hint: 'Mark this page', icon: Bookmark, run: a => a.bookmark() },
  notes: { label: 'Notes', hint: 'Bookmarks, highlights and notes in this book', icon: NotebookText, run: a => a.notes() },
  goto: { label: 'Go to', hint: 'A page or a percentage', icon: Hash, run: a => a.goTo() },
  info: { label: 'Book info', hint: 'Details about the book and the file', icon: Info, run: a => a.info() },
  look: { label: 'Look', hint: 'Theme, layout, spacing, page turn', icon: Settings2, run: a => a.look() },
}
export const ALL_KEYS = Object.keys(KEYS) as KeyId[]
export const DEFAULT_KEYS: KeyId[] = ['contents', 'daynight', 'light', 'text']
export const BAR_SIZE = 4

const valid = (ids: string[]) => {
  const keys = ids.filter((id): id is KeyId => id in KEYS).slice(0, BAR_SIZE)
  return keys.length ? keys : DEFAULT_KEYS
}

function key(id: KeyId | 'more', actions: KeyActions, more: () => void) {
  const k = id === 'more' ? { label: 'More', icon: SlidersHorizontal } : KEYS[id]
  const b = h('button', `reader-key${id === 'more' ? ' more' : ''}`)
  b.dataset.key = id
  b.append(icon(k.icon), h('span', '', k.label))
  b.addEventListener('click', () => id === 'more' ? more() : KEYS[id].run(actions))
  return b
}

// Draws the bar: the chosen keys, then More.
export function renderReaderKeys(bar: HTMLElement, ids: string[], actions: KeyActions, more: () => void) {
  bar.replaceChildren(...valid(ids).map(id => key(id, actions, more)), key('more', actions, more))
}

// The grid at the top of More: every key that isn't in the bar, and "Customize the bar".
export function renderMoreGrid(grid: HTMLElement, ids: string[], actions: KeyActions, customize: () => void, close: () => void) {
  const inBar = new Set(valid(ids))
  const keys = ALL_KEYS.filter(id => !inBar.has(id) && id !== 'look')
  grid.replaceChildren(...keys.map(id => {
    const b = key(id, actions, () => {})
    b.addEventListener('click', close, { capture: true })
    return b
  }))
  const edit = h('button', 'reader-key customize')
  edit.append(icon(SlidersHorizontal), h('span', '', 'Customize bar'))
  edit.addEventListener('click', () => { close(); customize() })
  grid.append(edit)
}

// Choosing the four keys: tick up to four, move them up or down. More always stays last.
export function customizeBar(current: string[], save: (ids: string[]) => void) {
  let chosen = [...valid(current)]
  const list = h('div', 'key-list')
  const count = h('p', 'settings-hint')
  const draw = () => {
    count.textContent = `${chosen.length} of ${BAR_SIZE} keys chosen. More is always last, so every control stays one tap away.`
    const order = [...chosen, ...ALL_KEYS.filter(id => !chosen.includes(id))]
    list.replaceChildren(...order.map(id => {
      const on = chosen.includes(id)
      const row = h('div', `key-row${on ? ' on' : ''}`)
      const toggle = h('button', 'key-toggle')
      toggle.setAttribute('role', 'checkbox')
      toggle.setAttribute('aria-checked', String(on))
      toggle.append(icon(KEYS[id].icon), h('span', 'key-name', KEYS[id].label), h('span', 'key-hint', KEYS[id].hint))
      toggle.addEventListener('click', () => {
        if (on) chosen = chosen.filter(x => x !== id)
        else if (chosen.length < BAR_SIZE) chosen.push(id)
        else return
        draw()
      })
      row.append(toggle)
      if (on) {
        const i = chosen.indexOf(id)
        const up = button('↑', 'icon small', () => { if (i > 0) { [chosen[i - 1], chosen[i]] = [chosen[i], chosen[i - 1]]; draw() } })
        up.setAttribute('aria-label', `Move ${KEYS[id].label} left`)
        const down = button('↓', 'icon small', () => { if (i < chosen.length - 1) { [chosen[i + 1], chosen[i]] = [chosen[i], chosen[i + 1]]; draw() } })
        down.setAttribute('aria-label', `Move ${KEYS[id].label} right`)
        row.append(up, down)
      }
      return row
    }))
  }
  draw()
  const actions = h('div', 'dialog-actions')
  const d = openDialog({ title: 'Reader bar', className: 'dialog-small', content: [count, list, actions] })
  actions.append(
    button('Reset', '', () => { chosen = [...DEFAULT_KEYS]; draw() }),
    button('Done', 'primary', () => { save(chosen.length ? chosen : DEFAULT_KEYS); d.close() }),
  )
}
