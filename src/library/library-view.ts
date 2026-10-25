import { createElement, Ellipsis, Library } from 'lucide'
import { listBooks, type BookRecord } from './db'

type Sort = 'recent' | 'title' | 'author'

const SORT_KEY = 'quiet:library-sort'
const FINISHED = 0.995

export interface LibraryActions {
  open(id: string): void
  remove(record: BookRecord): void
  add(): void
}

const h = <K extends keyof HTMLElementTagNameMap>(tag: K, className = '', text = '') => {
  const el = document.createElement(tag)
  if (className) el.className = className
  if (text) el.textContent = text
  return el
}

// The library screen: "Continue reading" and every book, as covers.
export class LibraryView {
  #root: HTMLElement
  #actions: LibraryActions
  #sort: Sort
  #coverUrls: string[] = []

  constructor(root: HTMLElement, actions: LibraryActions) {
    this.#root = root
    this.#actions = actions
    this.#sort = (readPref(SORT_KEY) as Sort) || 'recent'
  }

  async refresh() {
    const books = await listBooks()
    this.#coverUrls.forEach(URL.revokeObjectURL)
    this.#coverUrls = []
    this.#root.replaceChildren(books.length ? this.#shelves(books) : this.#welcome())
  }

  #welcome() {
    const wrap = h('div', 'library-empty')
    const inner = h('div', 'empty-inner')
    const icon = h('div', 'empty-icon')
    icon.append(createElement(Library))
    const button = h('button', 'btn primary', 'Choose a book')
    button.addEventListener('click', () => this.#actions.add())
    inner.append(
      icon,
      h('h1', '', 'Make yourself at home.'),
      h('p', '', 'A quiet, distraction-free place for your books. Add an EPUB, MOBI, AZW3, FB2 or CBZ file and it will wait for you here, right where you left off.'),
      button,
    )
    wrap.append(inner)
    return wrap
  }

  #shelves(books: BookRecord[]) {
    const inner = h('div', 'library-inner')

    const reading = books
      .filter(b => b.lastOpenedAt && b.progress < FINISHED)
      .sort((a, b) => (b.lastOpenedAt ?? 0) - (a.lastOpenedAt ?? 0))
      .slice(0, 3)
    if (reading.length) {
      const shelf = h('section', 'shelf')
      const head = h('div', 'shelf-head')
      head.append(h('h2', '', 'Continue reading'))
      const row = h('div', 'continue-row')
      for (const book of reading) row.append(this.#continueCard(book))
      shelf.append(head, row)
      inner.append(shelf)
    }

    const shelf = h('section', 'shelf')
    const head = h('div', 'shelf-head')
    const title = h('h2', '', 'Library ')
    title.append(h('span', 'count', String(books.length)))
    head.append(title, this.#sortControl())
    const grid = h('div', 'book-grid')
    for (const book of this.#sorted(books)) grid.append(this.#tile(book))
    shelf.append(head, grid)
    inner.append(shelf)
    return inner
  }

  #sortControl() {
    const row = h('div', 'sort')
    row.setAttribute('role', 'group')
    row.setAttribute('aria-label', 'Sort books')
    const options: [Sort, string][] = [['recent', 'Recent'], ['title', 'Title'], ['author', 'Author']]
    for (const [value, label] of options) {
      const button = h('button', value === this.#sort ? 'selected' : '', label)
      button.setAttribute('aria-pressed', String(value === this.#sort))
      button.addEventListener('click', () => {
        this.#sort = value
        writePref(SORT_KEY, value)
        this.refresh()
      })
      row.append(button)
    }
    return row
  }

  #sorted(books: BookRecord[]) {
    const text = (s: string) => s.toLocaleLowerCase()
    const byTitle = (a: BookRecord, b: BookRecord) => text(a.title).localeCompare(text(b.title))
    switch (this.#sort) {
      case 'title': return books.sort(byTitle)
      case 'author': return books.sort((a, b) =>
        text(a.author || '￿').localeCompare(text(b.author || '￿')) || byTitle(a, b))
      default: return books.sort((a, b) =>
        Math.max(b.lastOpenedAt ?? 0, b.addedAt) - Math.max(a.lastOpenedAt ?? 0, a.addedAt))
    }
  }

  #continueCard(book: BookRecord) {
    const card = h('button', 'continue-card')
    card.addEventListener('click', () => this.#actions.open(book.id))
    const info = h('div', 'continue-info')
    const bar = h('div', 'bar')
    const fill = h('span')
    fill.style.width = `${Math.round(book.progress * 100)}%`
    bar.append(fill)
    info.append(
      h('div', 'continue-title', book.title),
      h('div', 'continue-author', book.author || 'Unknown author'),
      bar,
      h('div', 'continue-meta', `${Math.round(book.progress * 100)}% · Continue`),
    )
    card.append(this.#cover(book), info)
    return card
  }

  #tile(book: BookRecord) {
    const tile = h('div', 'book-tile')
    const open = h('button', 'tile-open')
    open.addEventListener('click', () => this.#actions.open(book.id))
    const cover = this.#cover(book)
    if (book.progress > 0 && book.progress < FINISHED) {
      const strip = h('div', 'cover-progress')
      const fill = h('span')
      fill.style.width = `${Math.round(book.progress * 100)}%`
      strip.append(fill)
      cover.append(strip)
    }
    const status = !book.lastOpenedAt ? h('div', 'tile-status new', 'New')
      : book.progress >= FINISHED ? h('div', 'tile-status', 'Finished')
      : h('div', 'tile-status', `${Math.round(book.progress * 100)}%`)
    open.append(cover, h('div', 'tile-title', book.title),
      h('div', 'tile-author', book.author || 'Unknown author'), status)

    const menu = h('button', 'tile-menu')
    menu.title = 'Remove from library'
    menu.setAttribute('aria-label', `Options for ${book.title}`)
    menu.append(createElement(Ellipsis))
    menu.addEventListener('click', () => this.#actions.remove(book))

    tile.append(open, menu)
    return tile
  }

  #cover(book: BookRecord) {
    const cover = h('div', 'book-cover')
    if (book.cover) {
      const img = h('img')
      img.alt = ''
      img.loading = 'lazy'
      img.src = URL.createObjectURL(book.cover)
      this.#coverUrls.push(img.src)
      cover.append(img)
    } else {
      cover.append(generatedCover(book))
    }
    return cover
  }
}

// Books without a cover get a simple typographic one, coloured by title.
const COVER_COLORS = ['#6e806c', '#8a6f5a', '#5f7486', '#866a80', '#7d7a55', '#5d7d78', '#8c6464']

export function generatedCover(book: Pick<BookRecord, 'title' | 'author'>) {
  let hash = 0
  for (const ch of book.title) hash = (hash * 31 + ch.charCodeAt(0)) | 0
  const color = COVER_COLORS[Math.abs(hash) % COVER_COLORS.length]
  const el = h('div', 'generated-cover')
  el.style.background = `linear-gradient(150deg, ${color}, color-mix(in srgb, ${color} 62%, #000))`
  el.append(h('div', 'gc-title', book.title), h('div', 'gc-author', book.author))
  return el
}

function readPref(key: string) {
  try { return localStorage.getItem(key) } catch { return null }
}

function writePref(key: string, value: string) {
  try { localStorage.setItem(key, value) } catch { /* storage unavailable */ }
}
