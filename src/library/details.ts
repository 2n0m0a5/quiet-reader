import { BookCheck, BookOpen, Bookmark, FolderPlus, Image, Pencil, RotateCcw, Star, Tag, Trash2, X } from 'lucide'
import {
  listBooks, listCollections, newId, saveCollection, statusOf, updateBook, type BookRecord,
} from './db'
import { ensureDetails, originalCover, thumbnail } from './import'
import { generatedCover } from './library-view'
import { button, formDialog, h, menuDialog, openDialog, icon } from '../ui/dialog'
import { date, fileSize, percent, timeAgo } from '../ui/format'
import { toast } from '../ui/toast'
import { haptic } from '../app/native'

export interface DetailsActions {
  read(id: string): void
  remove(record: BookRecord): void
  changed(): void   // something about the book changed; refresh the library
}

export const STATUS_LABEL = { want: 'Want to read', new: 'Not started', reading: 'Reading', finished: 'Finished' }

// The details page for one book: cover, progress, rating, description, file information and actions.
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
    body.replaceChildren(header(), actionRow(), ...chipsRow(), ...review(), ...description(), facts())
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
      : book.lastOpenedAt ? `${percent(book.progress)} read · last opened ${timeAgo(book.lastOpenedAt)}`
      : `Not started · added ${timeAgo(book.addedAt)}`
    info.append(h('h2', 'details-title', book.title), h('div', 'details-author', book.author || 'Unknown author'))
    if (book.series) info.append(h('div', 'details-series', seriesText(book)))
    info.append(h('span', `status-pill ${status}`, STATUS_LABEL[status]), stars(), bar, h('div', 'details-progress', progressText))
    top.append(cover, info)
    return top
  }

  // Five stars. Tap one to rate; tap the same star again to clear the rating.
  const stars = () => {
    const row = h('div', 'stars')
    row.setAttribute('role', 'radiogroup')
    row.setAttribute('aria-label', 'Your rating')
    const rating = book.rating ?? 0
    for (let n = 1; n <= 5; n++) {
      const b = h('button', `star${n <= rating ? ' on' : ''}`)
      b.type = 'button'
      b.setAttribute('role', 'radio')
      b.setAttribute('aria-checked', String(n === rating))
      b.setAttribute('aria-label', `${n} ${n === 1 ? 'star' : 'stars'}`)
      b.append(icon(Star))
      b.addEventListener('click', () => { haptic(); change({ rating: n === rating ? 0 : n }) })
      row.append(b)
    }
    return row
  }

  const actionRow = () => {
    const row = h('div', 'details-actions')
    const status = statusOf(book)
    const read = button(book.lastOpenedAt && status !== 'finished' ? 'Continue reading' : status === 'finished' ? 'Read again' : 'Start reading',
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
        : small('Finished', BookCheck, () => change({ finished: true, wantToRead: false })),
      status === 'want'
        ? small('Not wanted', X, () => change({ wantToRead: false }))
        : small('Want to read', Bookmark, () => change({ wantToRead: true, finished: null })),
      small('Collections', FolderPlus, () => pickCollections(book, ids => change({ collections: ids }))),
      small('Tags', Tag, () => pickTags(book.tags ?? [], tags => change({ tags }))),
      small('Edit', Pencil, () => edit()),
      small('Cover', Image, () => chooseCover()),
      small('Remove', Trash2, () => { d.close(); actions.remove(book) }, true),
    )
    return row
  }

  const chipsRow = () => {
    if (!book.collections?.length && !book.tags?.length) return []
    const row = h('div', 'details-chips')
    for (const tag of book.tags ?? []) row.append(h('span', 'chip tag-chip', `#${tag}`))
    if (book.collections?.length) listCollections().then(all => {
      for (const c of all.filter(c => book.collections!.includes(c.id))) row.prepend(h('span', 'chip', c.name))
    })
    return [row]
  }

  const review = () => {
    const head = h('div', 'details-heading-row')
    head.append(h('h3', 'details-heading', 'Your review'), button(book.review ? 'Edit' : 'Write a review', 'link-button', () => editReview()))
    return book.review ? [head, h('p', 'details-description details-review', book.review)] : [head]
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
    add('Series', book.series ? seriesText(book) : '')
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
        { label: 'Series', value: book.series ?? '', placeholder: 'None' },
        { label: 'Number in the series', value: book.seriesIndex != null ? String(book.seriesIndex) : '', placeholder: '1' },
        { label: 'Language', value: book.language ?? '', placeholder: 'English' },
        { label: 'Description', value: book.description ?? '', multiline: true },
      ],
      confirm: 'Save',
    })
    if (!values) return
    const index = parseFloat(values[3].replace(',', '.'))
    change({
      title: values[0] || book.title, author: values[1], series: values[2],
      seriesIndex: values[2] && !Number.isNaN(index) ? index : null, language: values[4], description: values[5],
    })
  }

  const editReview = async () => {
    const values = await formDialog({
      title: 'Your review',
      fields: [{ label: 'What did you think?', value: book.review ?? '', multiline: true }],
      confirm: 'Save',
    })
    if (values) change({ review: values[0] })
  }

  const chooseCover = () => menuDialog({
    title: 'Cover',
    items: [
      { label: 'Choose a picture…', icon: icon(Image), run: () => pickImage(async file => {
        const cover = await thumbnail(file)
        if (cover) change({ cover })
        else toast('Couldn’t read that picture.')
      }) },
      { label: 'Use the book’s own cover', icon: icon(BookOpen), run: async () => {
        const cover = await originalCover(book)
        if (cover) change({ cover })
        else toast('This book has no cover of its own.')
      } },
      { label: 'Plain cover with the title', icon: icon(Pencil), run: () => change({ cover: null }) },
    ],
  })

  render()
  // Older books get their extra details read from the file the first time they're shown.
  ensureDetails(book).then(updated => { if (updated !== book) { book = { ...book, ...pick(updated) }; render() } })
}

export const seriesText = (b: BookRecord) => b.seriesIndex != null ? `${b.series} · Book ${b.seriesIndex}` : b.series ?? ''

const pick = (b: BookRecord) => ({
  format: b.format, publisher: b.publisher, language: b.language,
  published: b.published, description: b.description, subjects: b.subjects,
})

function pickImage(use: (file: File) => void) {
  const input = h('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.addEventListener('change', () => { if (input.files?.[0]) use(input.files[0]) })
  input.click()
}

// Tick the collections a book belongs to, or create a new one.
export async function pickCollections(book: Pick<BookRecord, 'collections'>, save: (ids: string[]) => void) {
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

// Every tag used in the library, most used first.
export async function allTags() {
  const counts = new Map<string, number>()
  for (const b of await listBooks()) for (const t of b.tags ?? []) counts.set(t, (counts.get(t) ?? 0) + 1)
  return [...counts.keys()].sort((a, b) => counts.get(b)! - counts.get(a)! || a.localeCompare(b))
}

// Tags: type a new one, or tap one you've used before. Lighter than collections, and a book can have many.
export async function pickTags(current: string[], save: (tags: string[]) => void) {
  const selected = [...current]
  const known = await allTags()
  const chips = h('div', 'tag-editor')
  const form = h('form', 'tag-form')
  const input = h('input', 'search-input')
  input.placeholder = 'New tag'
  input.setAttribute('aria-label', 'New tag')
  input.enterKeyHint = 'done'
  const add = h('button', 'btn', 'Add')
  form.append(input, add)
  const suggestions = h('div', 'tag-suggestions')
  const actions = h('div', 'dialog-actions')
  const d = openDialog({ title: 'Tags', className: 'dialog-small', content: [chips, form, suggestions, actions] })
  actions.append(button('Done', 'primary', () => d.close()))

  const render = () => {
    chips.replaceChildren()
    if (!selected.length) chips.append(h('p', 'dialog-text', 'Tags are quick labels, like “classic”, “to reread” or “gift from Mum”. A book can have as many as you like.'))
    for (const tag of selected) {
      const chip = h('button', 'chip selected tag-chip', `#${tag}`)
      chip.type = 'button'
      chip.title = 'Remove this tag'
      chip.append(icon(X))
      chip.addEventListener('click', () => { selected.splice(selected.indexOf(tag), 1); changed() })
      chips.append(chip)
    }
    suggestions.replaceChildren()
    const others = known.filter(t => !selected.some(s => same(s, t)))
    if (others.length) suggestions.append(h('div', 'settings-hint', 'Your tags'))
    for (const tag of others.slice(0, 24)) {
      const chip = h('button', 'chip tag-chip', `#${tag}`)
      chip.type = 'button'
      chip.addEventListener('click', () => { selected.push(tag); changed() })
      suggestions.append(chip)
    }
  }
  const changed = () => { haptic(); save([...selected]); render() }

  form.addEventListener('submit', e => {
    e.preventDefault()
    const tag = input.value.trim().replace(/^#+/, '').replace(/\s+/g, ' ')
    input.value = ''
    if (!tag) return
    if (!selected.some(s => same(s, tag))) {
      selected.push(known.find(k => same(k, tag)) ?? tag)
      changed()
    }
  })
  render()
  input.focus()
}

const same = (a: string, b: string) => a.toLocaleLowerCase() === b.toLocaleLowerCase()
