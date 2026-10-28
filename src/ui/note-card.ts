// One highlight, quote, note or bookmark in a list: in the reading side panel and on the Notes screen.
import { Quote } from 'lucide'
import { listAllAnnotations, type Annotation } from '../library/db'
import { inkOf } from '../reader/selection-menu'
import { h, icon } from './dialog'
import { date, percent } from './format'

export interface NoteCardOptions {
  open(): void                   // go to the place in the book
  tools?: HTMLElement[]          // small buttons on the right
  book?: string                  // the book's title and author, on the Notes screen
  author?: string                // the book's author: a quote nobody says in the book is theirs
}

// "— Elizabeth Bennet", or the author's name for the narrator's words.
export const attribution = (a: Annotation, author?: string) => a.speaker || author || ''

export function noteCard(a: Annotation, options: NoteCardOptions) {
  const style = a.type === 'bookmark' ? 'bookmark' : a.style ?? 'highlight'
  const item = h('div', `note-item ${a.type} style-${style}${a.quote ? ' is-quote' : ''}`)
  if (a.color) {
    item.style.setProperty('--note-color', a.color)
    item.style.setProperty('--note-ink', inkOf(a.color))
  }
  const open = h('button', 'note-open')
  open.addEventListener('click', options.open)
  if (a.quote) {
    const badge = h('div', 'quote-badge')
    badge.append(icon(Quote), h('span', '', 'Quote'))
    open.append(badge)
  }
  const text = h('div', 'note-text')
  text.append(h('span', 'note-mark', a.text || 'Bookmark'))
  open.append(text)
  if (a.quote) {
    const owner = attribution(a, options.author)
    if (owner) open.append(h('div', `quote-owner${a.speaker ? ' character' : ''}`, `— ${owner}`))
  }
  if (a.note) open.append(h('div', 'note-note', a.note))
  if (a.tags?.length) {
    const tags = h('div', 'note-tags')
    for (const t of a.tags) tags.append(h('span', 'note-tag', `#${t}`))
    open.append(tags)
  }
  if (options.book) open.append(h('div', 'note-book', options.book))
  const meta = [a.label, a.progress != null ? percent(a.progress) : '', date(a.createdAt)].filter(Boolean).join(' · ')
  open.append(h('div', 'note-meta', meta))
  item.append(open)
  if (options.tools?.length) {
    const tools = h('div', 'note-tools')
    tools.append(...options.tools)
    item.append(tools)
  }
  return item
}

export function noteTool(label: string, symbol: Parameters<typeof icon>[0], run: () => void) {
  const b = h('button', 'note-tool')
  b.title = label
  b.setAttribute('aria-label', label)
  b.append(icon(symbol))
  b.addEventListener('click', run)
  return b
}

// Every tag used on highlights, most used first.
export async function allHighlightTags() {
  const counts = new Map<string, number>()
  for (const a of await listAllAnnotations()) for (const t of a.tags ?? []) counts.set(t, (counts.get(t) ?? 0) + 1)
  return [...counts.keys()].sort((a, b) => counts.get(b)! - counts.get(a)! || a.localeCompare(b))
}

// A passage as text to copy or share. A quote is signed by whoever says it:
//   “…”
//   — Elizabeth Bennet, in Pride and Prejudice by Jane Austen
export function passageText(a: Annotation, book: { title?: string; author?: string } = {}) {
  const work = [book.title, book.author && `by ${book.author}`].filter(Boolean).join(' ')
  const source = a.quote
    ? `— ${a.speaker ? `${a.speaker}${work ? `, in ${work}` : ''}` : [book.author, book.title].filter(Boolean).join(', ')}`
    : [a.label, book.title].filter(Boolean).join(', ')
  return [`“${a.text}”`, source !== '— ' ? source : '', a.note].filter(Boolean).join('\n')
}
