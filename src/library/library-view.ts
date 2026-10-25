import { ArrowDownUp, Ellipsis, LayoutGrid, Library, Plus, Rows3, Search, X } from 'lucide'
import {
  deleteCollection, listBooks, listCollections, newId, saveCollection, statusOf,
  type BookRecord, type Collection, type Status,
} from './db'
import { confirmDialog, formDialog, h, menuDialog, icon } from '../ui/dialog'
import { percent, timeAgo } from '../ui/format'

type SortKey = 'recent' | 'added' | 'title' | 'author' | 'progress'
type View = 'grid' | 'list'
type Filter = 'all' | Status | `collection:${string}`

const SORTS: Record<SortKey, string> = {
  recent: 'Recently read', added: 'Recently added', title: 'Title', author: 'Author', progress: 'Progress',
}
const PREFS_KEY = 'quiet:library'

export interface LibraryActions {
  open(id: string): void
  info(record: BookRecord): void
  add(): void
}

interface Prefs { sort: SortKey; reversed: boolean; view: View }

// The library screen: "Continue reading", then every book, with search, sorting, filters and collections.
export class LibraryView {
  #actions: LibraryActions
  #prefs: Prefs
  #filter: Filter = 'all'
  #query = ''
  #books: BookRecord[] = []
  #collections: Collection[] = []
  #coverUrls: string[] = []

  // Parts of the screen that stay in place while the results change (so typing in search keeps focus).
  #welcome = h('div', 'library-empty')
  #inner = h('div', 'library-inner')
  #continue = h('section', 'shelf')
  #count = h('span', 'count')
  #search = h('input', 'search-input')
  #sortButton = h('button', 'btn small')
  #viewButton = h('button', 'btn icon small')
  #chips = h('div', 'chips')
  #results = h('div')

  constructor(root: HTMLElement, actions: LibraryActions) {
    this.#actions = actions
    this.#prefs = { sort: 'recent', reversed: false, view: 'grid', ...readPrefs() }
    this.#buildWelcome()
    this.#buildSkeleton()
    root.append(this.#welcome, this.#inner)
  }

  async refresh() {
    ;[this.#books, this.#collections] = await Promise.all([listBooks(), listCollections()])
    const empty = this.#books.length === 0
    this.#welcome.hidden = !empty
    this.#inner.hidden = empty
    if (empty) return
    if (this.#filter.startsWith('collection:') && !this.#collections.some(c => `collection:${c.id}` === this.#filter))
      this.#filter = 'all'
    this.#coverUrls.forEach(URL.revokeObjectURL)
    this.#coverUrls = []
    this.#count.textContent = String(this.#books.length)
    this.#renderControls()
    this.#renderContinue()
    this.#renderChips()
    this.#renderResults()
  }

  // ---------- Building the screen ----------

  #buildWelcome() {
    const inner = h('div', 'empty-inner')
    const badge = h('div', 'empty-icon')
    badge.append(icon(Library))
    const button = h('button', 'btn primary', 'Choose a book')
    button.addEventListener('click', () => this.#actions.add())
    inner.append(
      badge,
      h('h1', '', 'Make yourself at home.'),
      h('p', '', 'A quiet, distraction-free place for your books. Add an EPUB, MOBI, AZW3, FB2, CBZ, TXT or PDF file and it will wait for you here, right where you left off.'),
      button,
    )
    this.#welcome.append(inner)
  }

  #buildSkeleton() {
    const head = h('div', 'shelf-head library-head')
    const title = h('h2', '', 'Library ')
    title.append(this.#count)

    const searchWrap = h('label', 'search')
    this.#search.type = 'search'
    this.#search.placeholder = 'Search title or author'
    this.#search.setAttribute('aria-label', 'Search books')
    const clear = h('button', 'search-clear')
    clear.type = 'button'
    clear.setAttribute('aria-label', 'Clear search')
    clear.append(icon(X))
    clear.addEventListener('click', () => { this.#search.value = ''; this.#setQuery('') })
    this.#search.addEventListener('input', () => this.#setQuery(this.#search.value))
    searchWrap.append(icon(Search), this.#search, clear)

    this.#sortButton.addEventListener('click', () => this.#chooseSort())
    this.#viewButton.addEventListener('click', () => {
      this.#savePrefs({ view: this.#prefs.view === 'grid' ? 'list' : 'grid' })
      this.#renderControls()
      this.#renderResults()
    })

    const tools = h('div', 'library-tools')
    tools.append(searchWrap, this.#sortButton, this.#viewButton)
    head.append(title, tools)

    const all = h('section', 'shelf')
    all.append(head, this.#chips, this.#results)
    this.#inner.append(this.#continue, all)
  }

  #renderControls() {
    const { sort, reversed, view } = this.#prefs
    this.#sortButton.replaceChildren(icon(ArrowDownUp), h('span', 'label', SORTS[sort] + (reversed ? ' ↑' : '')))
    this.#sortButton.title = 'Sort books'
    this.#viewButton.replaceChildren(icon(view === 'grid' ? Rows3 : LayoutGrid))
    this.#viewButton.title = view === 'grid' ? 'Show as list' : 'Show as covers'
    this.#viewButton.setAttribute('aria-label', this.#viewButton.title)
  }

  #renderContinue() {
    const reading = this.#books
      .filter(b => statusOf(b) === 'reading')
      .sort((a, b) => (b.lastOpenedAt ?? 0) - (a.lastOpenedAt ?? 0))
      .slice(0, 3)
    this.#continue.hidden = !reading.length || this.#filter !== 'all' || !!this.#query
    if (this.#continue.hidden) return
    const head = h('div', 'shelf-head')
    head.append(h('h2', '', 'Continue reading'))
    const row = h('div', 'continue-row')
    for (const book of reading) row.append(this.#continueCard(book))
    this.#continue.replaceChildren(head, row)
  }

  #renderChips() {
    const count = (status: Status) => this.#books.filter(b => statusOf(b) === status).length
    const chips: [Filter, string][] = [
      ['all', 'All'],
      ['reading', `Reading ${count('reading') || ''}`],
      ['new', `Not started ${count('new') || ''}`],
      ['finished', `Finished ${count('finished') || ''}`],
      ...this.#collections.map(c => [`collection:${c.id}`, c.name] as [Filter, string]),
    ]
    this.#chips.replaceChildren()
    for (const [filter, label] of chips) {
      const chip = h('button', `chip${filter === this.#filter ? ' selected' : ''}`, label.trim())
      chip.setAttribute('aria-pressed', String(filter === this.#filter))
      chip.addEventListener('click', () => {
        // Tapping the selected collection again opens its options.
        if (filter === this.#filter && filter.startsWith('collection:')) return this.#collectionMenu(filter)
        this.#filter = filter
        this.#renderContinue()
        this.#renderChips()
        this.#renderResults()
      })
      if (filter.startsWith('collection:'))
        chip.addEventListener('contextmenu', e => { e.preventDefault(); this.#collectionMenu(filter) })
      this.#chips.append(chip)
    }
    const add = h('button', 'chip chip-add')
    add.append(icon(Plus), h('span', '', 'Collection'))
    add.addEventListener('click', () => this.#newCollection())
    this.#chips.append(add)
  }

  #renderResults() {
    const books = this.#sorted(this.#visible())
    this.#results.replaceChildren()
    const collection = this.#currentCollection()
    if (collection) {
      const bar = h('div', 'collection-bar')
      const edit = h('button', 'link-button', 'Rename or delete')
      edit.addEventListener('click', () => this.#collectionMenu(this.#filter))
      bar.append(h('span', '', `${books.length} ${books.length === 1 ? 'book' : 'books'} in “${collection.name}”`), edit)
      this.#results.append(bar)
    }
    if (!books.length) {
      this.#results.append(h('p', 'no-results', this.#emptyMessage()))
      return
    }
    const list = h('div', this.#prefs.view === 'grid' ? 'book-grid' : 'book-list')
    for (const book of books) list.append(this.#prefs.view === 'grid' ? this.#tile(book) : this.#row(book))
    this.#results.append(list)
  }

  #emptyMessage() {
    if (this.#query) return `No books match “${this.#query}”.`
    if (this.#currentCollection()) return 'This collection is empty. Open a book’s details (the ⋯ button) and choose Collections to add it here.'
    return { reading: 'Nothing in progress. Open a book to start reading.', new: 'You’ve started every book in your library.', finished: 'No finished books yet.', all: '' }[this.#filter as Status | 'all'] ?? ''
  }

  // ---------- Filtering and sorting ----------

  #setQuery(query: string) {
    this.#query = query.trim()
    this.#renderContinue()
    this.#renderResults()
  }

  #currentCollection() {
    return this.#collections.find(c => `collection:${c.id}` === this.#filter)
  }

  #visible() {
    const q = normalize(this.#query)
    return this.#books.filter(b => {
      if (q && !normalize(`${b.title} ${b.author}`).includes(q)) return false
      if (this.#filter === 'all') return true
      if (this.#filter.startsWith('collection:')) return b.collections?.includes(this.#filter.slice(11)) ?? false
      return statusOf(b) === this.#filter
    })
  }

  #sorted(books: BookRecord[]) {
    const text = (s: string) => s.toLocaleLowerCase()
    const byTitle = (a: BookRecord, b: BookRecord) => text(a.title).localeCompare(text(b.title))
    const compare: Record<SortKey, (a: BookRecord, b: BookRecord) => number> = {
      recent: (a, b) => Math.max(b.lastOpenedAt ?? 0, b.addedAt) - Math.max(a.lastOpenedAt ?? 0, a.addedAt),
      added: (a, b) => b.addedAt - a.addedAt,
      title: byTitle,
      author: (a, b) => text(a.author || '￿').localeCompare(text(b.author || '￿')) || byTitle(a, b),
      progress: (a, b) => b.progress - a.progress || byTitle(a, b),
    }
    const sorted = books.sort(compare[this.#prefs.sort])
    return this.#prefs.reversed ? sorted.reverse() : sorted
  }

  #chooseSort() {
    const { sort, reversed } = this.#prefs
    menuDialog({
      title: 'Sort by',
      items: [
        ...(Object.entries(SORTS) as [SortKey, string][]).map(([key, label]) => ({
          label, selected: key === sort,
          run: () => { this.#savePrefs({ sort: key, reversed: false }); this.#renderControls(); this.#renderResults() },
        })),
        {
          label: reversed ? 'Normal order' : 'Reverse order',
          run: () => { this.#savePrefs({ reversed: !reversed }); this.#renderControls(); this.#renderResults() },
        },
      ],
    })
  }

  // ---------- Collections ----------

  async #newCollection() {
    const values = await formDialog({ title: 'New collection', fields: [{ label: 'Name', value: '', placeholder: 'Favourites' }], confirm: 'Create' })
    if (!values?.[0]) return
    const collection = { id: newId(), name: values[0], createdAt: Date.now() }
    await saveCollection(collection)
    this.#filter = `collection:${collection.id}`
    this.refresh()
  }

  #collectionMenu(filter: Filter) {
    const collection = this.#collections.find(c => `collection:${c.id}` === filter)
    if (!collection) return
    menuDialog({
      title: collection.name,
      items: [
        {
          label: 'Rename', run: async () => {
            const values = await formDialog({ title: 'Rename collection', fields: [{ label: 'Name', value: collection.name }], confirm: 'Save' })
            if (!values?.[0]) return
            await saveCollection({ ...collection, name: values[0] })
            this.refresh()
          },
        },
        {
          label: 'Delete collection', danger: true, run: async () => {
            const ok = await confirmDialog({
              title: 'Delete collection?',
              message: `“${collection.name}” will be deleted. The books in it stay in your library.`,
              confirm: 'Delete', danger: true,
            })
            if (!ok) return
            await deleteCollection(collection.id)
            this.#filter = 'all'
            this.refresh()
          },
        },
      ],
    })
  }

  // ---------- Books ----------

  #continueCard(book: BookRecord) {
    const card = h('button', 'continue-card')
    card.addEventListener('click', () => this.#actions.open(book.id))
    this.#onLongPress(card, book)
    const info = h('div', 'continue-info')
    const bar = h('div', 'bar')
    const fill = h('span')
    fill.style.width = percent(book.progress)
    bar.append(fill)
    info.append(
      h('div', 'continue-title', book.title),
      h('div', 'continue-author', book.author || 'Unknown author'),
      bar,
      h('div', 'continue-meta', `${percent(book.progress)} · ${timeAgo(book.lastOpenedAt!)}`),
    )
    card.append(this.#cover(book), info)
    return card
  }

  #statusText(book: BookRecord) {
    const status = statusOf(book)
    return status === 'new' ? h('div', 'tile-status new', 'New')
      : status === 'finished' ? h('div', 'tile-status', 'Finished')
      : h('div', 'tile-status', percent(book.progress))
  }

  #infoButton(book: BookRecord, className: string) {
    const menu = h('button', className)
    menu.title = 'Book details'
    menu.setAttribute('aria-label', `Details for ${book.title}`)
    menu.append(icon(Ellipsis))
    menu.addEventListener('click', () => this.#actions.info(book))
    return menu
  }

  #tile(book: BookRecord) {
    const tile = h('div', 'book-tile')
    const open = h('button', 'tile-open')
    open.addEventListener('click', () => this.#actions.open(book.id))
    this.#onLongPress(open, book)
    const cover = this.#cover(book)
    if (statusOf(book) === 'reading' && book.progress > 0) {
      const strip = h('div', 'cover-progress')
      const fill = h('span')
      fill.style.width = percent(book.progress)
      strip.append(fill)
      cover.append(strip)
    }
    open.append(cover, h('div', 'tile-title', book.title),
      h('div', 'tile-author', book.author || 'Unknown author'), this.#statusText(book))
    tile.append(open, this.#infoButton(book, 'tile-menu'))
    return tile
  }

  #row(book: BookRecord) {
    const row = h('div', 'book-row')
    const open = h('button', 'row-open')
    open.addEventListener('click', () => this.#actions.open(book.id))
    this.#onLongPress(open, book)
    const text = h('div', 'row-text')
    text.append(h('div', 'tile-title', book.title), h('div', 'tile-author', book.author || 'Unknown author'), this.#statusText(book))
    open.append(this.#cover(book), text)
    row.append(open, this.#infoButton(book, 'row-menu'))
    return row
  }

  // Press and hold a book (or right-click it) to see its details.
  #onLongPress(el: HTMLElement, book: BookRecord) {
    let timer = 0
    let fired = false
    const cancel = () => window.clearTimeout(timer)
    el.addEventListener('touchstart', () => {
      fired = false
      timer = window.setTimeout(() => { fired = true; this.#actions.info(book) }, 500)
    }, { passive: true })
    el.addEventListener('touchmove', cancel, { passive: true })
    el.addEventListener('touchend', cancel)
    el.addEventListener('click', e => { if (fired) { e.stopImmediatePropagation(); fired = false } }, true)
    el.addEventListener('contextmenu', e => { e.preventDefault(); this.#actions.info(book) })
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

  #savePrefs(patch: Partial<Prefs>) {
    this.#prefs = { ...this.#prefs, ...patch }
    try { localStorage.setItem(PREFS_KEY, JSON.stringify(this.#prefs)) } catch { /* storage unavailable */ }
  }
}

function readPrefs(): Partial<Prefs> {
  try { return JSON.parse(localStorage.getItem(PREFS_KEY) ?? '{}') } catch { return {} }
}

// Case- and accent-insensitive text for searching ("Émile" matches "emile").
const normalize = (s: string) => s.toLocaleLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '')

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
