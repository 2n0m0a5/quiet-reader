import { BookCheck, BookOpen, FolderPlus, Pencil, RotateCcw, Trash2 } from 'lucide'
import {
  listCollections, newId, saveCollection, statusOf, updateBook, type BookRecord,
} from './db'
import { ensureDetails } from './import'
import { generatedCover } from './library-view'
import { button, formDialog, h, openDialog, icon } from '../ui/dialog'
import { date, fileSize, percent, timeAgo } from '../ui/format'

export interface DetailsActions {
  read(id: string): void
  remove(record: BookRecord): void
  changed(): void   // something about the book changed; refresh the library
}

const STATUS_LABEL = { new: 'Not started', reading: 'Reading', finished: 'Finished' }

// The details page for one book: cover, progress, description, file information and actions.
export async function showBookDetails(record: BookRecord, actions: DetailsActions) {
  let book = record
  const body = h('div', 'details')
  const urls: string[] = []
  const d = openDialog({
    className: 'dialog-details',
    content: body,
    onClose: () => urls.forEach(URL.revokeObjectURL),
  })

  const render = () => {
    urls.forEach(URL.revokeObjectURL)
    urls.length = 0
    body.replaceChildren(header(), actionRow(), ...collectionsRow(), ...description(), facts())
  }

  const header = () => {
    const top = h('div', 'details-top')
    const cover = h('div', 'book-cover details-cover')
    if (book.cover) {
      const img = h('img')
      img.alt = ''
      img.src = URL.createObjectURL(book.cover)
      urls.push(img.src)
      cover.append(img)
    } else {
      cover.append(generatedCover(book))
    }
    const info = h('div', 'details-info')
    const status = statusOf(book)
    const bar = h('div', 'bar')
    const fill = h('span')
    fill.style.width = status === 'finished' ? '100%' : percent(book.progress)
    bar.append(fill)
    const progressText = status === 'finished' ? 'Finished'
      : status === 'reading' ? `${percent(book.progress)} read · last opened ${timeAgo(book.lastOpenedAt!)}`
      : `Not started · added ${timeAgo(book.addedAt)}`
    info.append(
      h('h2', 'details-title', book.title),
      h('div', 'details-author', book.author || 'Unknown author'),
      h('span', `status-pill ${status}`, STATUS_LABEL[status]),
      bar,
      h('div', 'details-progress', progressText),
    )
    top.append(cover, info)
    return top
  }

  const actionRow = () => {
    const row = h('div', 'details-actions')
    const status = statusOf(book)
    const read = button(status === 'reading' ? 'Continue reading' : status === 'finished' ? 'Read again' : 'Start reading',
      'primary details-read', () => { d.close(); actions.read(book.id) })
    read.prepend(icon(BookOpen))
    const small = (label: string, symbol: typeof Pencil, run: () => void, danger = false) => {
      const b = button('', `icon-label${danger ? ' danger-text' : ''}`, run)
      b.append(icon(symbol), h('span', '', label))
      return b
    }
    row.append(
      read,
      status === 'finished'
        ? small('Not finished', RotateCcw, () => change({ finished: false }))
        : small('Finished', BookCheck, () => change({ finished: true })),
      small('Collections', FolderPlus, () => chooseCollections()),
      small('Edit', Pencil, () => edit()),
      small('Remove', Trash2, () => { d.close(); actions.remove(book) }, true),
    )
    return row
  }

  const collectionsRow = () => {
    if (!book.collections?.length) return []
    const row = h('div', 'details-chips')
    listCollections().then(all => {
      for (const c of all.filter(c => book.collections!.includes(c.id))) row.append(h('span', 'chip', c.name))
    })
    return [row]
  }

  const description = () => {
    if (!book.description) return []
    const p = h('p', 'details-description clamped', book.description)
    const more = button('More', 'link-button', () => {
      p.classList.toggle('clamped')
      more.textContent = p.classList.contains('clamped') ? 'More' : 'Less'
    })
    requestAnimationFrame(() => { if (p.scrollHeight <= p.clientHeight + 2) more.remove() })
    return [h('h3', 'details-heading', 'About this book'), p, more]
  }

  const facts = () => {
    const wrap = h('div')
    wrap.append(h('h3', 'details-heading', 'Details'))
    const list = h('dl', 'details-facts')
    const add = (term: string, value?: string) => {
      if (!value) return
      list.append(h('dt', '', term), h('dd', '', value))
    }
    add('Format', book.format)
    add('File size', fileSize(book.size))
    add('Language', book.language)
    add('Publisher', book.publisher)
    add('Published', book.published)
    add('Subjects', book.subjects?.slice(0, 6).join(', '))
    add('Added', date(book.addedAt))
    if (book.lastOpenedAt) add('Last opened', date(book.lastOpenedAt))
    add('File', book.fileName)
    wrap.append(list)
    return wrap
  }

  const change = async (patch: Partial<BookRecord>) => {
    book = { ...book, ...patch }
    await updateBook(book.id, patch)
    render()
    actions.changed()
  }

  const edit = async () => {
    const values = await formDialog({
      title: 'Edit book',
      fields: [
        { label: 'Title', value: book.title },
        { label: 'Author', value: book.author, placeholder: 'Unknown author' },
      ],
      confirm: 'Save',
    })
    if (values) change({ title: values[0] || book.title, author: values[1] })
  }

  const chooseCollections = async () => {
    await pickCollections(book, ids => change({ collections: ids }))
  }

  render()
  // Older books get their extra details read from the file the first time they're shown.
  ensureDetails(book).then(updated => { if (updated !== book) { book = { ...book, ...pick(updated) }; render() } })
}

const pick = (b: BookRecord) => ({
  format: b.format, publisher: b.publisher, language: b.language,
  published: b.published, description: b.description, subjects: b.subjects,
})

// Tick the collections a book belongs to, or create a new one.
export async function pickCollections(book: BookRecord, save: (ids: string[]) => void) {
  const selected = new Set(book.collections ?? [])
  const list = h('div', 'check-list')
  const actions = h('div', 'dialog-actions')
  const d = openDialog({ title: 'Collections', className: 'dialog-small', content: [list, actions] })

  const render = async () => {
    const all = await listCollections()
    list.replaceChildren()
    if (!all.length) list.append(h('p', 'dialog-text', 'Collections are your own shelves: “Favourites”, “To read”, “Philosophy”… Create the first one.'))
    for (const c of all) {
      const row = h('label', 'check-row')
      const box = h('input')
      box.type = 'checkbox'
      box.checked = selected.has(c.id)
      box.addEventListener('change', () => {
        box.checked ? selected.add(c.id) : selected.delete(c.id)
        save([...selected])
      })
      row.append(box, h('span', '', c.name))
      list.append(row)
    }
  }

  actions.append(
    button('New collection', '', async () => {
      const values = await formDialog({ title: 'New collection', fields: [{ label: 'Name', value: '', placeholder: 'Favourites' }], confirm: 'Create' })
      if (!values?.[0]) return
      const collection = { id: newId(), name: values[0], createdAt: Date.now() }
      await saveCollection(collection)
      selected.add(collection.id)
      save([...selected])
      render()
    }),
    button('Done', 'primary', () => d.close()),
  )
  await render()
}
