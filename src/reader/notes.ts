import { Copy, Pencil, Trash2, X } from 'lucide'
import { deleteAnnotation, listAnnotations, newId, saveAnnotation, type Annotation } from '../library/db'
import type { Reader } from './reader'
import type { SearchResult } from './foliate'
import { HIGHLIGHT_COLORS, hideSelectionMenu, showSelectionMenu } from './selection-menu'
import { confirmDialog, formDialog, h, icon } from '../ui/dialog'
import { date, percent } from '../ui/format'
import { toast } from '../ui/toast'
import { openExternal } from '../app/native'

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

  constructor(reader: Reader, bookId: string, ui: NotesUI, goTo: (cfi: string) => void) {
    this.#reader = reader
    this.#bookId = bookId
    this.#ui = ui
    this.#goTo = goTo
  }

  async load() {
    this.#items = await listAnnotations(this.#bookId)
    this.#reader.setMarks(this.#highlights().map(a => ({ value: a.cfi, color: a.color ?? this.#lastColor })))
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
    toast('Page bookmarked.')
  }

  // ---------- Highlights ----------

  // Text was selected in the book.
  onSelection(selection: { text: string; cfi: string; rect: DOMRect } | null) {
    // The selection went away. (Tapping a highlight also clears the selection; keep its menu open.)
    if (!selection) return hideSelectionMenu('selection')
    showSelectionMenu(selection.rect, {
      color: color => this.#highlight(selection, color),
      note: async () => {
        const a = await this.#highlight(selection, this.#lastColor, false)
        this.#editNote(a)
      },
      copy: () => this.#copy(selection.text),
      lookUp: () => lookUp(selection.text),
      translate: () => translate(selection.text),
    })
  }

  // An existing highlight was tapped.
  onHighlightTap(value: string, rect: DOMRect) {
    const a = this.#items.find(x => x.cfi === value && x.type === 'highlight')
    if (!a) return
    showSelectionMenu(rect, {
      color: async color => {
        this.#lastColor = color
        await this.#save({ ...a, color })
        this.#reader.addMark({ value: a.cfi, color })
      },
      note: () => this.#editNote(a),
      copy: () => this.#copy(a.text),
      lookUp: () => lookUp(a.text),
      translate: () => translate(a.text),
      remove: () => this.#delete(a),
    }, a.color)
  }

  async #highlight(selection: { text: string; cfi: string }, color: string, announce = true) {
    this.#lastColor = color
    this.#reader.clearSelection()
    const here = this.#reader.here()
    const a: Annotation = {
      id: newId(), bookId: this.#bookId, type: 'highlight', cfi: selection.cfi, text: selection.text,
      color, label: here?.label, progress: here?.progress, createdAt: Date.now(),
    }
    await this.#save(a)
    this.#reader.addMark({ value: a.cfi, color })
    if (announce) toast('Highlighted.')
    return a
  }

  async #editNote(a: Annotation) {
    const values = await formDialog({
      title: a.note ? 'Edit note' : 'Add a note',
      fields: [{ label: `“${a.text.slice(0, 120)}${a.text.length > 120 ? '…' : ''}”`, value: a.note ?? '', placeholder: 'Your thoughts…', multiline: true }],
      confirm: 'Save',
    })
    if (values) await this.#save({ ...a, note: values[0] })
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
    this.#renderList(this.#ui.noteList, this.#highlights(),
      'No highlights yet. Select some text in the book, then pick a colour or add a note.')
    if (this.#highlights().length) {
      const copyAll = h('button', 'btn small note-list-action')
      copyAll.append(icon(Copy), h('span', '', 'Copy all highlights'))
      copyAll.addEventListener('click', () => this.#copy(this.#highlights()
        .map(a => `> ${a.text}${a.note ? `\n\n${a.note}` : ''}${a.label ? `\n— ${a.label}` : ''}`).join('\n\n')))
      this.#ui.noteList.prepend(copyAll)
    }
  }

  #renderList(root: HTMLElement, items: Annotation[], empty: string) {
    root.replaceChildren()
    if (!items.length) {
      root.append(h('p', 'panel-empty', empty))
      return
    }
    for (const a of items) {
      const item = h('div', `note-item ${a.type}`)
      if (a.color) item.style.setProperty('--note-color', a.color)
      const open = h('button', 'note-open')
      open.addEventListener('click', () => this.#goTo(a.cfi))
      const meta = [a.label, a.progress != null ? percent(a.progress) : '', date(a.createdAt)].filter(Boolean).join(' · ')
      open.append(h('div', 'note-text', a.text || 'Bookmark'))
      if (a.note) open.append(h('div', 'note-note', a.note))
      open.append(h('div', 'note-meta', meta))
      const tools = h('div', 'note-tools')
      if (a.type === 'highlight') {
        const edit = h('button', 'note-tool')
        edit.title = a.note ? 'Edit note' : 'Add note'
        edit.append(icon(Pencil))
        edit.addEventListener('click', () => this.#editNote(a))
        tools.append(edit)
      }
      const del = h('button', 'note-tool')
      del.title = a.type === 'bookmark' ? 'Remove bookmark' : 'Delete highlight'
      del.append(icon(a.type === 'bookmark' ? X : Trash2))
      del.addEventListener('click', async () => {
        if (a.type === 'highlight' && a.note) {
          const ok = await confirmDialog({ title: 'Delete highlight?', message: 'The highlight and its note will be deleted.', confirm: 'Delete', danger: true })
          if (!ok) return
        }
        this.#delete(a)
      })
      tools.append(del)
      item.append(open, tools)
      root.append(item)
    }
  }
}

function lookUp(text: string) {
  const words = text.trim().split(/\s+/)
  openExternal(`https://www.google.com/search?q=${encodeURIComponent(words.length <= 3 ? `define ${text.trim()}` : text.trim())}`)
}

function translate(text: string) {
  const target = (navigator.language || 'en').split('-')[0]
  openExternal(`https://translate.google.com/?sl=auto&tl=${target}&op=translate&text=${encodeURIComponent(text.trim())}`)
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
