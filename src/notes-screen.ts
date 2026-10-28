// The Notes screen: highlights, notes and quotes from every book, in one place. Searchable,
// filtered by quotes, notes or tags, and grouped by book or newest first.
import { Copy, Ellipsis, NotebookPen, Pencil, Quote, Search, Tag, Trash2, UserRound, X } from 'lucide'
import {
  deleteAnnotation, listAllAnnotations, listBooks, saveAnnotation, type Annotation, type BookRecord,
} from './library/db'
import { pickTags } from './library/details'
import { generatedCover } from './library/library-view'
import { confirmDialog, formDialog, h, icon, menuDialog } from './ui/dialog'
import { allHighlightTags, noteCard, noteTool, passageText } from './ui/note-card'
import { toast } from './ui/toast'
import { askSpeaker, knownSpeakers } from './ui/quote-dialog'

type Filter = 'all' | 'quotes' | 'notes' | `tag:${string}`
type Order = 'book' | 'newest'

export interface NotesScreenActions {
  openAt(bookId: string, cfi: string): void
}

const ORDER_KEY = 'quiet:notes-order'

export class NotesScreen {
  #root: HTMLElement
  #actions: NotesScreenActions
  #items: Annotation[] = []
  #books = new Map<string, BookRecord>()
  #filter: Filter = 'all'
  #query = ''
  #order: Order = readOrder()
  #urls: string[] = []

  #inner = h('div', 'notes-inner')
  #count = h('span', 'count')
  #search = h('input', 'search-input')
  #chips = h('div', 'chips')
  #orderRow = h('div', 'sort notes-order')
  #results = h('div', 'notes-results')

  constructor(root: HTMLElement, actions: NotesScreenActions) {
    this.#root = root
    this.#actions = actions
    const head = h('div', 'shelf-head library-head')
    const title = h('h2', '', 'Notes ')
    title.append(this.#count)
    const searchWrap = h('label', 'search')
    this.#search.type = 'search'
    this.#search.placeholder = 'Search your notes'
    this.#search.setAttribute('aria-label', 'Search your notes')
    const clear = h('button', 'search-clear')
    clear.type = 'button'
    clear.setAttribute('aria-label', 'Clear search')
    clear.append(icon(X))
    clear.addEventListener('click', () => { this.#search.value = ''; this.#query = ''; this.#renderResults() })
    this.#search.addEventListener('input', () => { this.#query = this.#search.value.trim(); this.#renderResults() })
    searchWrap.append(icon(Search), this.#search, clear)
    const tools = h('div', 'library-tools')
    tools.append(searchWrap)
    head.append(title, tools)
    this.#inner.append(head, this.#chips, this.#orderRow, this.#results)
    root.append(this.#inner)
  }

  async render() {
    const [items, books] = await Promise.all([listAllAnnotations(), listBooks()])
    this.#items = items.filter(a => a.type === 'highlight')
    this.#books = new Map(books.map(b => [b.id, b]))
    this.#count.textContent = String(this.#items.length)
    this.#renderChips()
    this.#renderOrder()
    this.#renderResults()
  }

  #renderChips() {
    const tags = [...new Set(this.#items.flatMap(a => a.tags ?? []))].sort()
    if (this.#filter.startsWith('tag:') && !tags.includes(this.#filter.slice(4))) this.#filter = 'all'
    const quotes = this.#items.filter(a => a.quote).length
    const notes = this.#items.filter(a => a.note).length
    const chips: [Filter, string][] = [
      ['all', 'All'],
      ['quotes', `Quotes ${quotes || ''}`],
      ['notes', `With notes ${notes || ''}`],
      ...tags.map(t => [`tag:${t}`, `#${t}`] as [Filter, string]),
    ]
    this.#chips.replaceChildren()
    this.#chips.hidden = !this.#items.length
    for (const [filter, label] of chips) {
      const chip = h('button', `chip${filter === this.#filter ? ' selected' : ''}`, label.trim())
      chip.setAttribute('aria-pressed', String(filter === this.#filter))
      chip.addEventListener('click', () => { this.#filter = filter; this.#renderChips(); this.#renderResults() })
      this.#chips.append(chip)
    }
  }

  #renderOrder() {
    this.#orderRow.replaceChildren()
    this.#orderRow.hidden = !this.#items.length
    for (const [order, label] of [['book', 'By book'], ['newest', 'Newest first']] as [Order, string][]) {
      const b = h('button', order === this.#order ? 'selected' : '', label)
      b.addEventListener('click', () => {
        this.#order = order
        try { localStorage.setItem(ORDER_KEY, order) } catch { /* storage unavailable */ }
        this.#renderOrder()
        this.#renderResults()
      })
      this.#orderRow.append(b)
    }
  }

  #visible() {
    const q = normalize(this.#query.replace(/^#/, ''))
    return this.#items.filter(a => {
      const book = this.#books.get(a.bookId)
      if (q && !normalize(`${a.text} ${a.note ?? ''} ${a.speaker ?? ''} ${(a.tags ?? []).join(' ')} ${book?.title ?? ''} ${book?.author ?? ''}`).includes(q)) return false
      if (this.#filter === 'quotes') return a.quote
      if (this.#filter === 'notes') return !!a.note
      if (this.#filter.startsWith('tag:')) return a.tags?.includes(this.#filter.slice(4))
      return true
    })
  }

  #renderResults() {
    this.#urls.forEach(URL.revokeObjectURL)
    this.#urls = []
    this.#results.replaceChildren()
    if (!this.#items.length) {
      const empty = h('div', 'notes-empty')
      const badge = h('div', 'empty-icon')
      badge.append(icon(NotebookPen))
      empty.append(badge, h('h1', '', 'Your notes will gather here.'),
        h('p', '', 'Select text while reading to highlight it, write a note, or save it as a quote. Everything from every book shows up on this page.'))
      this.#results.append(empty)
      return
    }
    const items = this.#visible()
    if (!items.length) {
      this.#results.append(h('p', 'no-results', this.#query ? `Nothing matches “${this.#query}”.`
        : this.#filter === 'quotes' ? 'No quotes yet. Select a passage you love and tap the quote button.'
        : this.#filter === 'notes' ? 'No notes yet. Select text and tap Note to write one.' : 'Nothing here.'))
      return
    }
    // Quotes read best as a column of passages; everything else as the usual list.
    const listClass = `note-list notes-list${this.#filter === 'quotes' ? ' quotes-list' : ''}`
    if (this.#order === 'newest') {
      const list = h('div', listClass)
      for (const a of items.sort((x, y) => y.createdAt - x.createdAt)) list.append(this.#card(a, true))
      this.#results.append(list)
      return
    }
    // By book: the book read most recently first, notes in reading order.
    const groups = new Map<string, Annotation[]>()
    for (const a of items) groups.set(a.bookId, [...groups.get(a.bookId) ?? [], a])
    const recent = (id: string) => { const b = this.#books.get(id); return Math.max(b?.lastOpenedAt ?? 0, b?.addedAt ?? 0) }
    for (const [bookId, notes] of [...groups].sort(([a], [b]) => recent(b) - recent(a))) {
      const book = this.#books.get(bookId)
      if (!book) continue
      this.#results.append(this.#bookHeading(book, notes.length))
      const list = h('div', listClass)
      for (const a of notes.sort((x, y) => (x.progress ?? 0) - (y.progress ?? 0))) list.append(this.#card(a, false))
      this.#results.append(list)
    }
  }

  #bookHeading(book: BookRecord, count: number) {
    const head = h('div', 'notes-book')
    const cover = h('div', 'book-cover notes-book-cover')
    if (book.cover) {
      const img = h('img')
      img.alt = ''
      img.src = URL.createObjectURL(book.cover)
      this.#urls.push(img.src)
      cover.append(img)
    } else {
      cover.append(generatedCover(book))
    }
    const text = h('div', 'row-text')
    text.append(h('div', 'tile-title', book.title), h('div', 'tile-author', `${book.author || 'Unknown author'} · ${count} ${count === 1 ? 'note' : 'notes'}`))
    head.append(cover, text)
    return head
  }

  #card(a: Annotation, showBook: boolean) {
    const book = this.#books.get(a.bookId)
    const name = book ? `${book.title}${book.author ? ` — ${book.author}` : ''}` : ''
    return noteCard(a, {
      open: () => this.#actions.openAt(a.bookId, a.cfi),
      book: showBook ? name : undefined,
      author: book?.author,
      tools: [
        noteTool('Copy', Copy, () => this.#copy(passageText(a, book))),
        noteTool('More', Ellipsis, () => this.#menu(a, book)),
      ],
    })
  }

  // Who says it, for a quote. Null if cancelled.
  #askSpeaker(a: Annotation, editing: boolean) {
    const sameBook = this.#items.filter(x => x.bookId === a.bookId)
    return askSpeaker({ text: a.text, current: a.speaker, known: knownSpeakers(sameBook), author: this.#books.get(a.bookId)?.author, editing })
  }

  #menu(a: Annotation, book?: BookRecord) {
    menuDialog({
      items: [
        ...(a.quote ? [{ label: a.speaker ? `Who says it: ${a.speaker}` : 'Who says it…', icon: icon(UserRound), run: async () => {
          const speaker = await this.#askSpeaker(a, true)
          if (speaker != null) await this.#save({ ...a, speaker })
        } }] : []),
        { label: a.note ? 'Edit note' : 'Add a note', icon: icon(Pencil), run: () => this.#editNote(a) },
        { label: 'Tags', icon: icon(Tag), run: () => this.#editTags(a) },
        { label: a.quote ? 'Remove from Quotes' : 'Save as a quote', icon: icon(Quote), run: () => this.#toggleQuote(a) },
        { label: 'Copy', icon: icon(Copy), run: () => this.#copy(passageText(a, book)) },
        { label: 'Delete', icon: icon(Trash2), danger: true, run: () => this.#delete(a) },
      ],
    })
  }

  async #save(a: Annotation) {
    await saveAnnotation(a)
    await this.render()
  }

  async #editNote(a: Annotation) {
    const values = await formDialog({
      title: a.note ? 'Edit note' : 'Add a note',
      fields: [{ label: `“${a.text.slice(0, 120)}${a.text.length > 120 ? '…' : ''}”`, value: a.note ?? '', placeholder: 'Your thoughts…', multiline: true }],
      confirm: 'Save',
    })
    if (values) await this.#save({ ...a, note: values[0] })
  }

  async #editTags(a: Annotation) {
    let current = a
    pickTags(a.tags ?? [], tags => { current = { ...current, tags }; this.#save(current) }, { known: await allHighlightTags() })
  }

  async #toggleQuote(a: Annotation) {
    if (!a.quote) {
      const speaker = await this.#askSpeaker(a, false)
      if (speaker == null) return
      await this.#save({ ...a, quote: true, speaker })
      return toast('Saved to Quotes.')
    }
    // A passage kept only as a quote (no colour, no note) goes away entirely.
    if (a.style === 'plain' && !a.note) {
      await deleteAnnotation(a.id)
      await this.render()
    } else {
      await this.#save({ ...a, quote: false })
    }
    toast('Removed from Quotes.')
  }

  async #delete(a: Annotation) {
    const ok = await confirmDialog({
      title: 'Delete this?', confirm: 'Delete', danger: true,
      message: a.note ? 'The highlight and its note will be deleted from the book.' : 'The highlight will be deleted from the book.',
    })
    if (!ok) return
    await deleteAnnotation(a.id)
    await this.render()
  }

  async #copy(text: string) {
    try {
      await navigator.clipboard.writeText(text)
      toast('Copied.')
    } catch {
      toast('Couldn’t copy on this device.')
    }
  }

  // Scroll back to the top when the screen is opened again.
  reset() {
    this.#root.scrollTop = 0
  }
}

function readOrder(): Order {
  try { return localStorage.getItem(ORDER_KEY) === 'newest' ? 'newest' : 'book' } catch { return 'book' }
}

const normalize = (s: string) => s.toLocaleLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '')
