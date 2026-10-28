import { Copy, Image as ImageIcon, Pencil, Tag, Trash2, UserRound, X } from 'lucide'
import { deleteAnnotation, getBook, listAnnotations, newId, saveAnnotation, type Annotation, type MarkStyle } from '../library/db'
import { pickTags } from '../library/details'
import { allHighlightTags, noteCard, noteTool, passageText } from '../ui/note-card'
import { showQuoteImage } from '../ui/quote-image'
import { showTranslation } from './translate'
import { askSpeaker, knownSpeakers } from '../ui/quote-dialog'
import type { Reader } from './reader'
import type { SearchResult } from './foliate'
import { HIGHLIGHT_COLORS, hideSelectionMenu, showSelectionMenu } from './selection-menu'
import { confirmDialog, formDialog, h, icon } from '../ui/dialog'
import { percent } from '../ui/format'
import { toast } from '../ui/toast'
import { haptic, openExternal } from '../app/native'

type NoteFilter = 'all' | 'quotes' | `tag:${string}`

interface NotesUI {
  bookmarkList: HTMLElement
  noteList: HTMLElement
  bookmarkButton: HTMLElement
}

// Bookmarks and highlights (with notes) for the open book: saving them, drawing them
// on the page, and listing them in the side panel.
export class BookNotes {
  #reader: Reader
  #bookId: string
  #ui: NotesUI
  #goTo: (cfi: string) => void
  #items: Annotation[] = []
  #lastColor = HIGHLIGHT_COLORS[0].value
  #book: { id: string; title: string; author: string }

  constructor(reader: Reader, book: { id: string; title: string; author: string }, ui: NotesUI, goTo: (cfi: string) => void) {
    this.#reader = reader
    this.#bookId = book.id
    this.#book = { id: book.id, title: book.title, author: book.author }
    this.#ui = ui
    this.#goTo = goTo
  }

  // Asks who says a quote. Null if cancelled.
  #askSpeaker(text: string, current?: string, editing = false) {
    return askSpeaker({ text, current, known: knownSpeakers(this.#items), author: this.#book.author, editing })
  }

  async load() {
    this.#items = await listAnnotations(this.#bookId)
    this.#reader.setMarks(this.#highlights().map(a => ({ value: a.cfi, color: a.color ?? this.#lastColor, style: a.style })))
    this.#render()
    this.refreshBookmarkButton()
  }

  #highlights() { return this.#items.filter(a => a.type === 'highlight') }
  #bookmarks() { return this.#items.filter(a => a.type === 'bookmark') }

  // ---------- Bookmarks ----------

  #bookmarkHere() {
    return this.#bookmarks().find(b => this.#reader.isVisible(b.cfi))
  }

  refreshBookmarkButton() {
    const marked = !!this.#bookmarkHere()
    this.#ui.bookmarkButton.classList.toggle('marked', marked)
    this.#ui.bookmarkButton.setAttribute('aria-pressed', String(marked))
    this.#ui.bookmarkButton.title = marked ? 'Remove bookmark' : 'Bookmark this page'
  }

  async toggleBookmark() {
    const existing = this.#bookmarkHere()
    if (existing) {
      await this.#delete(existing)
      toast('Bookmark removed.')
      return
    }
    const here = this.#reader.here()
    if (!here) return
    await this.#save({
      id: newId(), bookId: this.#bookId, type: 'bookmark', cfi: here.cfi,
      text: here.text, label: here.label, progress: here.progress, createdAt: Date.now(),
    })
    haptic('confirm')
    toast('Page bookmarked.')
  }

  // ---------- Highlights, underlines, quotes ----------

  #lastStyle: Exclude<MarkStyle, 'plain'> = 'highlight'
  #filter: NoteFilter = 'all'

  // Text was selected in the book.
  onSelection(selection: { text: string; cfi: string; rect: DOMRect } | null) {
    // The selection went away. (Tapping a highlight also clears the selection; keep its menu open.)
    if (!selection) return hideSelectionMenu('selection')
    showSelectionMenu(selection.rect, {
      color: color => this.#create(selection, { color }),
      style: style => this.#create(selection, { style }),
      note: async () => this.#editNote(await this.#create(selection, {}, false)),
      quote: async () => {
        const speaker = await this.#askSpeaker(selection.text)
        if (speaker == null) return this.#reader.clearSelection()
        await this.#create(selection, { style: 'plain', quote: true, speaker }, false)
        toast('Saved to Quotes.')
      },
      tags: async () => this.#editTags(await this.#create(selection, {}, false)),
      copy: () => this.#copy(selection.text),
      lookUp: () => lookUp(selection.text),
      translate: () => translate(selection.text),
    })
  }

  // An existing highlight (or quote) was tapped.
  onHighlightTap(value: string, rect: DOMRect) {
    const a = this.#items.find(x => x.cfi === value && x.type === 'highlight')
    if (!a) return
    const style = a.style ?? 'highlight'
    showSelectionMenu(rect, {
      color: color => this.#change(a, { color, style: style === 'plain' ? this.#lastStyle : style }),
      style: next => this.#change(a, { style: next, color: a.color ?? this.#lastColor }),
      note: () => this.#editNote(a),
      quote: async () => {
        if (!a.quote) {
          const speaker = await this.#askSpeaker(a.text, a.speaker)
          if (speaker == null) return
          await this.#change(a, { quote: true, speaker })
          return toast('Saved to Quotes.')
        }
        // A passage kept only as a quote (no colour, no note) goes away entirely.
        if (style === 'plain' && !a.note) await this.#delete(a)
        else await this.#change(a, { quote: false })
        toast('Removed from Quotes.')
      },
      tags: () => this.#editTags(a),
      copy: () => this.#copy(a.text),
      lookUp: () => lookUp(a.text),
      translate: () => translate(a.text),
      remove: () => this.#delete(a),
    }, { color: a.color, style, quote: a.quote })
  }

  async #create(selection: { text: string; cfi: string }, patch: Partial<Annotation>, announce = true) {
    this.#reader.clearSelection()
    const here = this.#reader.here()
    const a: Annotation = {
      id: newId(), bookId: this.#bookId, type: 'highlight', cfi: selection.cfi, text: selection.text,
      color: this.#lastColor, style: this.#lastStyle, label: here?.label, progress: here?.progress, createdAt: Date.now(),
      ...patch,
    }
    this.#remember(a)
    await this.#save(a)
    this.#reader.addMark({ value: a.cfi, color: a.color!, style: a.style })
    haptic('confirm')
    if (announce) toast(a.style === 'underline' ? 'Underlined.' : a.style === 'strike' ? 'Crossed out.' : 'Highlighted.')
    return a
  }

  async #change(a: Annotation, patch: Partial<Annotation>) {
    const next = { ...this.#latest(a), ...patch }
    this.#remember(next)
    await this.#save(next)
    this.#reader.addMark({ value: next.cfi, color: next.color ?? this.#lastColor, style: next.style })
    haptic()
    return next
  }

  // The colour and style used last are used for the next highlight.
  #remember(a: Annotation) {
    if (a.color) this.#lastColor = a.color
    if (a.style && a.style !== 'plain') this.#lastStyle = a.style
  }

  // The newest saved version of a highlight (it may have changed since a menu was opened).
  #latest(a: Annotation) {
    return this.#items.find(x => x.id === a.id) ?? a
  }

  async #editNote(a: Annotation) {
    a = this.#latest(a)
    const values = await formDialog({
      title: a.note ? 'Edit note' : 'Add a note',
      fields: [{ label: `“${a.text.slice(0, 120)}${a.text.length > 120 ? '…' : ''}”`, value: a.note ?? '', placeholder: 'Your thoughts…', multiline: true }],
      confirm: 'Save',
    })
    if (values) await this.#save({ ...this.#latest(a), note: values[0] })
  }

  async #editTags(a: Annotation) {
    pickTags(this.#latest(a).tags ?? [], tags => this.#save({ ...this.#latest(a), tags }), {
      known: await allHighlightTags(),
      hint: 'Tag highlights to find them again on the Notes screen: “idea”, “to look up”, “favourite line”…',
    })
  }

  async #copy(text: string) {
    try {
      await navigator.clipboard.writeText(text)
      toast('Copied.')
    } catch {
      toast('Couldn’t copy on this device.')
    }
  }

  // ---------- Saving ----------

  async #save(a: Annotation) {
    await saveAnnotation(a)
    const i = this.#items.findIndex(x => x.id === a.id)
    if (i >= 0) this.#items[i] = a
    else this.#items.push(a)
    this.#items.sort((x, y) => (x.progress ?? 0) - (y.progress ?? 0) || x.createdAt - y.createdAt)
    this.#render()
    this.refreshBookmarkButton()
  }

  async #delete(a: Annotation) {
    await deleteAnnotation(a.id)
    this.#items = this.#items.filter(x => x.id !== a.id)
    if (a.type === 'highlight') this.#reader.removeMark(a.cfi)
    this.#render()
    this.refreshBookmarkButton()
  }

  // ---------- Side panel lists ----------

  #render() {
    this.#renderList(this.#ui.bookmarkList, this.#bookmarks(),
      'No bookmarks yet. Tap the bookmark button at the top while reading to mark a page.')
    const all = this.#highlights()
    const tags = [...new Set(all.flatMap(a => a.tags ?? []))].sort()
    if (this.#filter.startsWith('tag:') && !tags.includes(this.#filter.slice(4))) this.#filter = 'all'
    const shown = all.filter(a => this.#filter === 'all' ? true
      : this.#filter === 'quotes' ? a.quote : a.tags?.includes(this.#filter.slice(4)))
    this.#renderList(this.#ui.noteList, shown, this.#filter === 'quotes'
      ? 'No quotes from this book yet. Select a passage you love and tap the quote button.'
      : 'No highlights yet. Select some text in the book, then pick a colour or add a note.')
    if (!all.length) return
    // Filters: all, quotes, and each tag used in this book.
    const chips = h('div', 'chips note-chips')
    const chip = (filter: NoteFilter, label: string) => {
      const c = h('button', `chip${filter === this.#filter ? ' selected' : ''}`, label)
      c.addEventListener('click', () => { this.#filter = filter; this.#render() })
      chips.append(c)
    }
    chip('all', `All ${all.length}`)
    const quotes = all.filter(a => a.quote).length
    if (quotes) chip('quotes', `Quotes ${quotes}`)
    for (const t of tags) chip(`tag:${t}`, `#${t}`)
    const copyAll = h('button', 'btn small note-list-action')
    copyAll.append(icon(Copy), h('span', '', shown.length === all.length ? 'Copy all' : `Copy these ${shown.length}`))
    copyAll.addEventListener('click', () => this.#copy(shown.map(a => passageText(a, this.#book)).join('\n\n')))
    this.#ui.noteList.prepend(chips, copyAll)
  }

  #renderList(root: HTMLElement, items: Annotation[], empty: string) {
    root.replaceChildren()
    if (!items.length) {
      root.append(h('p', 'panel-empty', empty))
      return
    }
    for (const a of items) {
      const tools: HTMLElement[] = []
      if (a.type === 'highlight') {
        if (a.quote) tools.push(noteTool('Who says it', UserRound, async () => {
          const speaker = await this.#askSpeaker(a.text, a.speaker, true)
          if (speaker != null) await this.#save({ ...this.#latest(a), speaker })
        }))
        tools.push(noteTool(a.note ? 'Edit note' : 'Add note', Pencil, () => this.#editNote(a)))
        tools.push(noteTool('Tags', Tag, () => this.#editTags(a)))
        tools.push(noteTool('Share as a picture', ImageIcon, async () => showQuoteImage(a, await getBook(this.#book.id))))
      }
      tools.push(noteTool(a.type === 'bookmark' ? 'Remove bookmark' : 'Delete', a.type === 'bookmark' ? X : Trash2, async () => {
        if (a.type === 'highlight' && (a.note || a.quote)) {
          const ok = await confirmDialog({ title: 'Delete this?', message: a.quote ? 'The passage will be deleted from Quotes, with its note.' : 'The highlight and its note will be deleted.', confirm: 'Delete', danger: true })
          if (!ok) return
        }
        this.#delete(a)
      }))
      root.append(noteCard(a, { open: () => this.#goTo(a.cfi), tools, author: this.#book.author }))
    }
  }
}

function lookUp(text: string) {
  const words = text.trim().split(/\s+/)
  openExternal(`https://www.google.com/search?q=${encodeURIComponent(words.length <= 3 ? `define ${text.trim()}` : text.trim())}`)
}

function translate(text: string) {
  showTranslation(text)
}

// Search inside the open book, shown in the side panel.
export class BookSearch {
  #reader: Reader
  #status: HTMLElement
  #results: HTMLElement
  #goTo: (cfi: string) => void
  #run = 0

  constructor(reader: Reader, ui: { status: HTMLElement; results: HTMLElement }, goTo: (cfi: string) => void) {
    this.#reader = reader
    this.#status = ui.status
    this.#results = ui.results
    this.#goTo = goTo
  }

  clear() {
    this.#run++
    this.#reader.view.clearSearch()
    this.#status.textContent = ''
    this.#results.replaceChildren()
  }

  async search(query: string) {
    this.clear()
    query = query.trim()
    if (query.length < 2) return
    const run = this.#run
    let count = 0
    this.#status.textContent = 'Searching…'
    try {
      // Matches are outlined on the page in Quiet's green.
      for await (const result of this.#reader.view.search({ query, drawOptions: { color: '#6e806c', width: 2, radius: 4 } })) {
        if (run !== this.#run) return
        if (result === 'done') break
        if ('progress' in result) {
          this.#status.textContent = `Searching… ${percent(result.progress)}`
          continue
        }
        count += this.#renderGroup(result)
      }
    } catch (err) {
      console.error('Search failed:', err)
    }
    if (run !== this.#run) return
    this.#status.textContent = count ? `${count} ${count === 1 ? 'result' : 'results'} for “${query}”` : `No results for “${query}”.`
  }

  #renderGroup(group: Extract<SearchResult, { label: string }>) {
    if (group.label) this.#results.append(h('div', 'search-group', group.label))
    for (const { cfi, excerpt } of group.subitems) {
      const item = h('button', 'search-hit')
      item.append(excerpt.pre, h('mark', '', excerpt.match), excerpt.post)
      item.addEventListener('click', () => this.#goTo(cfi))
      this.#results.append(item)
    }
    return group.subitems.length
  }
}
