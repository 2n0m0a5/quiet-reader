import {
  ArrowDownUp, Bookmark, BookCheck, ChartColumn, Check, CopyCheck, Ellipsis, FolderPlus, LayoutGrid, Library, ListChecks, Plus,
  RotateCcw, Rows3, Search, SquareLibrary, Tag, Trash2, Users, X,
} from 'lucide'
import {
  deleteCollection, listBooks, listCollections, listTrash, newId, purgeTrash, saveCollection, statusOf, updateBook,
  type BookRecord, type Collection, type Status,
} from './db'
import { confirmDialog, formDialog, h, menuDialog, icon } from '../ui/dialog'
import { fileSize, percent, timeAgo } from '../ui/format'
import { allTags, pickCollections, pickTags, seriesText } from './details'
import { findDuplicates, showDuplicates, showTrash } from './library-tools'
import { haptic } from '../app/native'

type SortKey = 'recent' | 'added' | 'title' | 'author' | 'series' | 'rating' | 'progress' | 'size'
type View = 'grid' | 'list' | 'shelf'
type Filter = 'all' | Status | `collection:${string}` | `tag:${string}`

const SORTS: Record<SortKey, string> = {
  recent: 'Recently read', added: 'Recently added', title: 'Title', author: 'Author', series: 'Series',
  rating: 'Rating', progress: 'Progress', size: 'File size',
}
const VIEWS: Record<View, string> = { grid: 'Covers', list: 'List', shelf: 'Shelf' }
const PREFS_KEY = 'quiet:library'

export interface LibraryActions {
  open(id: string): void
  info(record: BookRecord): void
  add(): void
  remove(books: BookRecord[]): Promise<void>   // to Recently deleted, with Undo
  summary?(): Promise<HTMLElement | null>       // today's reading, shown at the top
  stats?(): void
}

interface Prefs { sort: SortKey; reversed: boolean; view: View; groupByAuthor: boolean; shelfSize: number | null }

// The library screen: "Continue reading", then every book, with search, sorting, filters,
// collections and tags; several books can be selected at once.
export class LibraryView {
  #actions: LibraryActions
  #prefs: Prefs
  #filter: Filter = 'all'
  #query = ''
  #books: BookRecord[] = []
  #collections: Collection[] = []
  #tags: string[] = []
  #trashCount = 0
  // Picture addresses for covers, freed when that part of the screen is drawn again.
  #coverUrls: string[] = []
  #continueUrls: string[] = []
  #selection: Set<string> | null = null   // null when not selecting

  // Parts of the screen that stay in place while the results change (so typing in search keeps focus).
  #welcome = h('div', 'library-empty')
  #inner = h('div', 'library-inner')
  #continue = h('section', 'shelf')
  #summary = h('div', 'reading-summary')
  #count = h('span', 'count')
  #search = h('input', 'search-input')
  #sortButton = h('button', 'btn small')
  #menuButton = h('button', 'btn icon small')
  #chips = h('div', 'chips')
  #results = h('div')
  #selectBar = h('div', 'select-bar')
  #selectCount = h('span', 'select-count')

  constructor(root: HTMLElement, actions: LibraryActions) {
    this.#actions = actions
    this.#prefs = { sort: 'recent', reversed: false, view: 'grid', groupByAuthor: false, shelfSize: null, ...readPrefs() }
    this.#buildWelcome()
    this.#buildSkeleton()
    this.#buildSelectBar()
    root.append(this.#welcome, this.#inner, this.#selectBar)
  }

  async refresh() {
    await purgeTrash()
    const [books, collections, tags, trash] = await Promise.all([listBooks(), listCollections(), allTags(), listTrash()])
    this.#books = books
    this.#collections = collections
    this.#tags = tags
    this.#trashCount = trash.length
    const empty = this.#books.length === 0
    this.#welcome.hidden = !empty
    this.#inner.hidden = empty
    if (empty) this.#endSelection()
    if (this.#selection) for (const id of [...this.#selection]) if (!books.some(b => b.id === id)) this.#selection.delete(id)
    this.#renderSelectBar()
    this.#welcome.querySelector('.trash-link')?.toggleAttribute('hidden', !this.#trashCount)
    if (empty) return
    if (this.#filter.startsWith('collection:') && !this.#currentCollection()) this.#filter = 'all'
    if (this.#filter.startsWith('tag:') && !this.#tags.includes(this.#filter.slice(4))) this.#filter = 'all'
    this.#count.textContent = String(this.#books.length)
    this.#actions.summary?.().then(el => this.#summary.replaceChildren(...(el ? [el] : [])))
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
    const trash = h('button', 'link-button trash-link', 'Recently deleted')
    trash.hidden = true
    trash.addEventListener('click', () => showTrash(() => this.refresh()))
    inner.append(
      badge,
      h('h1', '', 'Make yourself at home.'),
      h('p', '', 'A quiet, distraction-free place for your books. Add an EPUB, MOBI, AZW3, FB2, CBZ, TXT or PDF file and it will wait for you here, right where you left off.'),
      button, trash,
    )
    this.#welcome.append(inner)
  }

  #buildSkeleton() {
    const head = h('div', 'shelf-head library-head')
    const title = h('h2', '', 'Library ')
    title.append(this.#count)

    const searchWrap = h('label', 'search')
    this.#search.type = 'search'
    this.#search.placeholder = 'Search title, author, series, tag'
    this.#search.setAttribute('aria-label', 'Search books')
    const clear = h('button', 'search-clear')
    clear.type = 'button'
    clear.setAttribute('aria-label', 'Clear search')
    clear.append(icon(X))
    clear.addEventListener('click', () => { this.#search.value = ''; this.#setQuery('') })
    this.#search.addEventListener('input', () => this.#setQuery(this.#search.value))
    searchWrap.append(icon(Search), this.#search, clear)

    this.#sortButton.addEventListener('click', () => this.#chooseSort())
    this.#menuButton.id = 'libraryMenu'
    this.#menuButton.title = 'More'
    this.#menuButton.setAttribute('aria-label', 'More')
    this.#menuButton.append(icon(Ellipsis))
    this.#menuButton.addEventListener('click', () => this.#moreMenu())

    const tools = h('div', 'library-tools')
    tools.append(searchWrap, this.#sortButton, this.#menuButton)
    head.append(title, tools)

    const all = h('section', 'shelf')
    all.append(head, this.#chips, this.#results)
    this.#inner.append(this.#summary, this.#continue, all)
    this.#pinchShelves()
  }

  // Shelf view: pinch with two fingers (or Ctrl + scroll) to make the books bigger or smaller.
  #pinchShelves() {
    const MIN = 64, MAX = 260
    const shelves = () => this.#results.querySelectorAll<HTMLElement>('.book-shelf')
    const current = () => {
      const first = shelves()[0]
      return first ? parseFloat(getComputedStyle(first).getPropertyValue('--col')) || 110 : 110
    }
    const resize = (size: number) => {
      size = Math.round(Math.min(MAX, Math.max(MIN, size)))
      shelves().forEach(el => el.style.setProperty('--col', `${size}px`))
      return size
    }
    let start: { distance: number; size: number } | null = null
    const distance = (t: TouchList) => Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY)
    this.#results.addEventListener('touchstart', e => {
      if (this.#prefs.view === 'shelf' && e.touches.length === 2) start = { distance: distance(e.touches), size: current() }
    }, { passive: true })
    this.#results.addEventListener('touchmove', e => {
      if (!start || e.touches.length !== 2) return
      e.preventDefault()   // the page itself doesn't zoom
      resize(start.size * distance(e.touches) / start.distance)
    }, { passive: false })
    this.#results.addEventListener('touchend', e => {
      if (!start || e.touches.length) return
      start = null
      this.#savePrefs({ shelfSize: current() })
      haptic()
    })
    this.#results.addEventListener('wheel', e => {
      if (this.#prefs.view !== 'shelf' || !e.ctrlKey) return
      e.preventDefault()
      this.#savePrefs({ shelfSize: resize(current() * Math.exp(-e.deltaY / 200)) })
    }, { passive: false })
  }

  #renderControls() {
    const { sort, reversed } = this.#prefs
    this.#sortButton.replaceChildren(icon(ArrowDownUp), h('span', 'label', SORTS[sort] + (reversed ? ' ↑' : '')))
    this.#sortButton.title = 'Sort books'
  }

  #renderContinue() {
    const reading = this.#books
      .filter(b => statusOf(b) === 'reading')
      .sort((a, b) => (b.lastOpenedAt ?? 0) - (a.lastOpenedAt ?? 0))
      .slice(0, 3)
    this.#continue.hidden = !reading.length || this.#filter !== 'all' || !!this.#query || !!this.#selection
    this.#continueUrls.forEach(URL.revokeObjectURL)
    this.#continueUrls = []
    if (this.#continue.hidden) return
    const head = h('div', 'shelf-head')
    head.append(h('h2', '', 'Continue reading'))
    const row = h('div', 'continue-row')
    for (const book of reading) row.append(this.#continueCard(book))
    this.#continue.replaceChildren(head, row)
  }

  #renderChips() {
    const count = (status: Status) => this.#books.filter(b => statusOf(b) === status).length
    const want = count('want')
    const chips: [Filter, string][] = [
      ['all', 'All'],
      ...(want ? [['want', `Want to read ${want}`] as [Filter, string]] : []),
      ['reading', `Reading ${count('reading') || ''}`],
      ['new', `Not started ${count('new') || ''}`],
      ['finished', `Finished ${count('finished') || ''}`],
      ...this.#collections.map(c => [`collection:${c.id}`, c.name] as [Filter, string]),
      ...this.#tags.map(t => [`tag:${t}`, `#${t}`] as [Filter, string]),
    ]
    this.#chips.replaceChildren()
    for (const [filter, label] of chips) {
      const chip = h('button', `chip${filter === this.#filter ? ' selected' : ''}${filter.startsWith('tag:') ? ' tag-chip' : ''}`, label.trim())
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
    this.#coverUrls.forEach(URL.revokeObjectURL)
    this.#coverUrls = []
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
    // Grouped by author: one heading per author, in the chosen order inside each group.
    const groups = this.#prefs.groupByAuthor ? groupBy(books, b => b.author || 'Unknown author') : [['', books] as const]
    for (const [author, group] of groups) {
      if (author) {
        const heading = h('h3', 'group-heading', `${author} `)
        heading.append(h('span', 'count', String(group.length)))
        this.#results.append(heading)
      }
      this.#results.append(this.#bookList(group))
    }
  }

  #bookList(books: BookRecord[]) {
    const view = this.#prefs.view
    const list = h('div', view === 'grid' ? 'book-grid' : view === 'list' ? 'book-list' : 'book-shelf')
    if (view === 'shelf' && this.#prefs.shelfSize) list.style.setProperty('--col', `${this.#prefs.shelfSize}px`)
    list.classList.toggle('selecting', !!this.#selection)
    for (const book of books) list.append(view === 'grid' ? this.#tile(book) : view === 'list' ? this.#row(book) : this.#shelfBook(book))
    return list
  }

  #emptyMessage() {
    if (this.#query) return `No books match “${this.#query}”.`
    if (this.#currentCollection()) return 'This collection is empty. Open a book’s details (the ⋯ button) and choose Collections to add it here.'
    return {
      want: 'Nothing on your wish list. Open a book’s details and choose Want to read.',
      reading: 'Nothing in progress. Open a book to start reading.', new: 'You’ve started every book in your library.',
      finished: 'No finished books yet.', all: '',
    }[this.#filter as Status | 'all'] ?? ''
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
    const q = normalize(this.#query.replace(/^#/, ''))
    return this.#books.filter(b => {
      if (q && !normalize(`${b.title} ${b.author} ${b.series ?? ''} ${(b.tags ?? []).join(' ')}`).includes(q)) return false
      if (this.#filter === 'all') return true
      if (this.#filter.startsWith('collection:')) return b.collections?.includes(this.#filter.slice(11)) ?? false
      if (this.#filter.startsWith('tag:')) return b.tags?.includes(this.#filter.slice(4)) ?? false
      return statusOf(b) === this.#filter
    })
  }

  #sorted(books: BookRecord[]) {
    const text = (s: string) => s.toLocaleLowerCase()
    const byTitle = (a: BookRecord, b: BookRecord) => text(a.title).localeCompare(text(b.title))
    const last = '￿'   // sorts after every letter: books without an author or series go last
    const compare: Record<SortKey, (a: BookRecord, b: BookRecord) => number> = {
      recent: (a, b) => Math.max(b.lastOpenedAt ?? 0, b.addedAt) - Math.max(a.lastOpenedAt ?? 0, a.addedAt),
      added: (a, b) => b.addedAt - a.addedAt,
      title: byTitle,
      author: (a, b) => text(a.author || last).localeCompare(text(b.author || last)) || byTitle(a, b),
      series: (a, b) => text(a.series || last).localeCompare(text(b.series || last))
        || (a.seriesIndex ?? Infinity) - (b.seriesIndex ?? Infinity) || byTitle(a, b),
      rating: (a, b) => (b.rating ?? 0) - (a.rating ?? 0) || byTitle(a, b),
      progress: (a, b) => b.progress - a.progress || byTitle(a, b),
      size: (a, b) => b.size - a.size || byTitle(a, b),
    }
    const sorted = books.sort(compare[this.#prefs.sort])
    return this.#prefs.reversed ? sorted.reverse() : sorted
  }

  #rerender() {
    this.#renderControls()
    this.#renderContinue()
    this.#renderResults()
  }

  #chooseSort() {
    const { sort, reversed, groupByAuthor } = this.#prefs
    menuDialog({
      title: 'Sort by',
      items: [
        ...(Object.entries(SORTS) as [SortKey, string][]).map(([key, label]) => ({
          label, selected: key === sort,
          run: () => { this.#savePrefs({ sort: key, reversed: false }); this.#rerender() },
        })),
        { label: reversed ? 'Normal order' : 'Reverse order', icon: icon(ArrowDownUp), run: () => { this.#savePrefs({ reversed: !reversed }); this.#rerender() } },
        { label: groupByAuthor ? 'Don’t group by author' : 'Group by author', icon: icon(Users), run: () => { this.#savePrefs({ groupByAuthor: !groupByAuthor }); this.#rerender() } },
      ],
    })
  }

  // The ⋯ menu: select several books, how to show them, duplicates, Recently deleted.
  #moreMenu() {
    const duplicates = findDuplicates(this.#books).length
    menuDialog({
      items: [
        { label: 'Select books', icon: icon(ListChecks), run: () => this.#startSelection() },
        ...(Object.entries(VIEWS) as [View, string][]).map(([view, label]) => ({
          label: `Show as ${label.toLowerCase()}`, selected: view === this.#prefs.view,
          icon: icon(view === 'grid' ? LayoutGrid : view === 'list' ? Rows3 : SquareLibrary),
          run: () => { this.#savePrefs({ view }); this.#renderResults() },
        })),
        { label: duplicates ? `Find duplicates (${duplicates})` : 'Find duplicates', icon: icon(CopyCheck), run: () => showDuplicates(this.#books, async b => { await this.#actions.remove([b]) }) },
        { label: this.#trashCount ? `Recently deleted (${this.#trashCount})` : 'Recently deleted', icon: icon(Trash2), run: () => showTrash(() => this.refresh()) },
        ...(this.#actions.stats ? [{ label: 'Reading stats', icon: icon(ChartColumn), run: () => this.#actions.stats!() }] : []),
      ],
    })
  }

  // ---------- Selecting several books ----------

  #startSelection() {
    this.#selection = new Set()
    haptic()
    this.#renderSelectBar()
    this.#renderContinue()
    this.#renderResults()
  }

  #endSelection() {
    if (!this.#selection) return
    this.#selection = null
    this.#renderSelectBar()
    this.#renderContinue()
    this.#renderResults()
  }

  #toggleSelected(book: BookRecord, el: HTMLElement) {
    const s = this.#selection!
    s.has(book.id) ? s.delete(book.id) : s.add(book.id)
    el.classList.toggle('checked', s.has(book.id))
    el.setAttribute('aria-pressed', String(s.has(book.id)))
    haptic()
    this.#renderSelectBar()
  }

  #selected() {
    return this.#books.filter(b => this.#selection?.has(b.id))
  }

  #buildSelectBar() {
    this.#selectBar.setAttribute('role', 'toolbar')
    this.#selectBar.setAttribute('aria-label', 'Selected books')
    const top = h('div', 'select-top')
    const all = h('button', 'link-button', 'Select all')
    all.addEventListener('click', () => {
      const visible = this.#visible()
      const everything = visible.every(b => this.#selection?.has(b.id))
      this.#selection = new Set(everything ? [] : visible.map(b => b.id))
      this.#renderSelectBar()
      this.#renderResults()
    })
    const done = h('button', 'btn small primary', 'Done')
    done.addEventListener('click', () => this.#endSelection())
    top.append(this.#selectCount, all, done)

    const tool = (label: string, symbol: typeof Tag, run: () => void, danger = false) => {
      const b = h('button', `select-tool${danger ? ' danger-text' : ''}`)
      b.append(icon(symbol), h('span', '', label))
      b.addEventListener('click', () => { if (this.#selected().length) run() })
      return b
    }
    const tools = h('div', 'select-tools')
    tools.append(
      tool('Collections', FolderPlus, () => this.#bulkCollections()),
      tool('Tags', Tag, () => this.#bulkTags()),
      tool('Mark as', BookCheck, () => this.#bulkStatus()),
      tool('Remove', Trash2, async () => {
        await this.#actions.remove(this.#selected())
        this.#endSelection()
      }, true),
    )
    this.#selectBar.append(top, tools)
  }

  #renderSelectBar() {
    const on = !!this.#selection
    this.#selectBar.classList.toggle('open', on)
    document.getElementById('app')?.toggleAttribute('data-selecting', on)
    if (!on) return
    const n = this.#selection!.size
    this.#selectCount.textContent = n ? `${n} selected` : 'Tap books to select'
    this.#selectBar.querySelectorAll<HTMLButtonElement>('.select-tool').forEach(b => { b.disabled = !n })
  }

  async #bulkUpdate(patch: (b: BookRecord) => Partial<BookRecord>) {
    for (const b of this.#selected()) await updateBook(b.id, patch(b))
    await this.refresh()
  }

  // A collection is ticked when every selected book is in it; ticking adds all of them, unticking removes all.
  #bulkCollections() {
    const books = this.#selected()
    const common = this.#collections.filter(c => books.every(b => b.collections?.includes(c.id))).map(c => c.id)
    let before = new Set(common)
    pickCollections({ collections: common }, ids => {
      const now = new Set(ids)
      const added = ids.filter(id => !before.has(id))
      const removed = [...before].filter(id => !now.has(id))
      before = now
      this.#bulkUpdate(b => ({ collections: [...new Set([...(b.collections ?? []).filter(c => !removed.includes(c)), ...added])] }))
    })
  }

  #bulkTags() {
    const books = this.#selected()
    const common = (books[0]?.tags ?? []).filter(t => books.every(b => b.tags?.includes(t)))
    let before = common
    pickTags(common, tags => {
      const removed = before.filter(t => !tags.includes(t))
      before = tags
      this.#bulkUpdate(b => ({ tags: [...new Set([...(b.tags ?? []).filter(t => !removed.includes(t)), ...tags])] }))
    })
  }

  #bulkStatus() {
    menuDialog({
      title: `Mark ${this.#selected().length} as`,
      items: [
        { label: 'Want to read', icon: icon(Bookmark), run: () => this.#bulkUpdate(() => ({ wantToRead: true, finished: null })) },
        { label: 'Finished', icon: icon(BookCheck), run: () => this.#bulkUpdate(() => ({ finished: true, wantToRead: false })) },
        { label: 'Not finished', icon: icon(RotateCcw), run: () => this.#bulkUpdate(() => ({ finished: false, wantToRead: false })) },
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

  // Opens the book, or (while selecting) ticks it.
  #onActivate(el: HTMLElement, book: BookRecord) {
    el.addEventListener('click', () => this.#selection ? this.#toggleSelected(book, el) : this.#actions.open(book.id))
    if (this.#selection) {
      el.classList.toggle('checked', this.#selection.has(book.id))
      el.setAttribute('aria-pressed', String(this.#selection.has(book.id)))
      el.append(h('span', 'check-mark'))
      el.lastElementChild!.append(icon(Check))
    }
    this.#onLongPress(el, book)
  }

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
    card.append(this.#cover(book, this.#continueUrls), info)
    return card
  }

  #statusText(book: BookRecord) {
    const status = statusOf(book)
    // The list view has room for more: the series, the rating, or the size when sorting by size.
    const extra = this.#prefs.sort === 'size' ? fileSize(book.size)
      : book.rating ? '★'.repeat(book.rating) : ''
    const text = status === 'new' ? 'New' : status === 'want' ? 'Want to read' : status === 'finished' ? 'Finished' : percent(book.progress)
    return h('div', `tile-status${status === 'new' ? ' new' : ''}`, extra ? `${text} · ${extra}` : text)
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
    const cover = this.#cover(book)
    if (book.lastOpenedAt && statusOf(book) !== 'finished' && book.progress > 0) {
      const strip = h('div', 'cover-progress')
      const fill = h('span')
      fill.style.width = percent(book.progress)
      strip.append(fill)
      cover.append(strip)
    }
    open.append(cover, h('div', 'tile-title', book.title),
      h('div', 'tile-author', book.author || 'Unknown author'), this.#statusText(book))
    this.#onActivate(open, book)
    tile.append(open)
    if (!this.#selection) tile.append(this.#infoButton(book, 'tile-menu'))
    return tile
  }

  #row(book: BookRecord) {
    const row = h('div', 'book-row')
    const open = h('button', 'row-open')
    const text = h('div', 'row-text')
    text.append(h('div', 'tile-title', book.title), h('div', 'tile-author', [book.author || 'Unknown author', book.series ? seriesText(book) : ''].filter(Boolean).join(' · ')), this.#statusText(book))
    open.append(this.#cover(book), text)
    this.#onActivate(open, book)
    row.append(open)
    if (!this.#selection) row.append(this.#infoButton(book, 'row-menu'))
    return row
  }

  // Shelf view: covers standing on wooden shelves.
  #shelfBook(book: BookRecord) {
    const slot = h('div', 'shelf-slot')
    const open = h('button', 'shelf-book')
    open.title = `${book.title}${book.author ? ` — ${book.author}` : ''}`
    open.setAttribute('aria-label', open.title)
    open.append(this.#cover(book))
    this.#onActivate(open, book)
    slot.append(open)
    return slot
  }

  // Press and hold a book (or right-click it) to see its details. Android sends both a long touch
  // and a "contextmenu" for the same press, so whichever comes first opens the details, once.
  #onLongPress(el: HTMLElement, book: BookRecord) {
    let timer = 0
    let fired = false
    let shownAt = 0
    const show = (byTouch: boolean) => {
      // A held finger's release can count as a tap; that tap mustn't also open the book.
      if (byTouch) fired = true
      if (Date.now() - shownAt < 1000) return
      shownAt = Date.now()
      haptic()
      this.#actions.info(book)
    }
    const cancel = () => window.clearTimeout(timer)
    el.addEventListener('touchstart', () => {
      fired = false
      timer = window.setTimeout(() => show(true), 500)
    }, { passive: true })
    el.addEventListener('touchmove', cancel, { passive: true })
    el.addEventListener('touchend', cancel)
    el.addEventListener('click', e => { if (fired) { e.stopImmediatePropagation(); fired = false } }, true)
    el.addEventListener('contextmenu', e => { e.preventDefault(); cancel(); show(e.pointerType === 'touch' || fired) })
  }

  #cover(book: BookRecord, urls = this.#coverUrls) {
    const cover = h('div', 'book-cover')
    if (book.cover) {
      const img = h('img')
      img.alt = ''
      img.loading = 'lazy'
      img.src = URL.createObjectURL(book.cover)
      urls.push(img.src)
      cover.append(img)
    } else {
      cover.append(generatedCover(book))
    }
    return cover
  }

  // Android's back button and Escape leave selection first.
  closeSelection() {
    if (!this.#selection) return false
    this.#endSelection()
    return true
  }

  #savePrefs(patch: Partial<Prefs>) {
    this.#prefs = { ...this.#prefs, ...patch }
    try { localStorage.setItem(PREFS_KEY, JSON.stringify(this.#prefs)) } catch { /* storage unavailable */ }
  }
}

function readPrefs(): Partial<Prefs> {
  try { return JSON.parse(localStorage.getItem(PREFS_KEY) ?? '{}') } catch { return {} }
}

// Books in groups, keeping their order; groups ordered by name.
function groupBy(books: BookRecord[], key: (b: BookRecord) => string) {
  const groups = new Map<string, BookRecord[]>()
  for (const b of books) groups.set(key(b), [...groups.get(key(b)) ?? [], b])
  return [...groups.entries()].sort(([a], [b]) =>
    a === 'Unknown author' ? 1 : b === 'Unknown author' ? -1 : a.localeCompare(b))
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
