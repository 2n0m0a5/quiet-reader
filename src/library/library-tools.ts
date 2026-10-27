// Recently deleted, and the duplicate finder.
import { RotateCcw, Trash2 } from 'lucide'
import { deleteBook, listTrash, restoreBooks, TRASH_DAYS, type BookRecord } from './db'
import { generatedCover } from './library-view'
import { button, confirmDialog, h, icon, openDialog } from '../ui/dialog'
import { date, fileSize, percent, timeAgo } from '../ui/format'
import { toast } from '../ui/toast'

// A small cover and a few lines of text, for the lists below.
function bookLine(book: BookRecord, meta: string, urls: string[]) {
  const row = h('div', 'tool-row')
  const cover = h('div', 'book-cover tool-cover')
  if (book.cover) {
    const img = h('img')
    img.alt = ''
    img.src = URL.createObjectURL(book.cover)
    urls.push(img.src)
    cover.append(img)
  } else {
    cover.append(generatedCover(book))
  }
  const text = h('div', 'row-text')
  text.append(h('div', 'tile-title', book.title), h('div', 'tile-author', book.author || 'Unknown author'), h('div', 'tile-status', meta))
  row.append(cover, text)
  return row
}

const daysLeft = (book: BookRecord) =>
  Math.max(1, Math.ceil((book.deletedAt! + TRASH_DAYS * 86_400_000 - Date.now()) / 86_400_000))

// Books removed in the last 30 days, with their bookmarks and highlights. Restore them or delete them for good.
export async function showTrash(changed: () => void) {
  const list = h('div', 'tool-list')
  const urls: string[] = []
  const actions = h('div', 'dialog-actions')
  const hint = h('p', 'dialog-text', `Removed books stay here for ${TRASH_DAYS} days, with their reading place, bookmarks and highlights. Then they’re deleted for good.`)
  openDialog({ title: 'Recently deleted', className: 'dialog-details', content: [hint, list, actions], onClose: () => urls.forEach(URL.revokeObjectURL) })

  const render = async () => {
    urls.forEach(URL.revokeObjectURL)
    urls.length = 0
    const books = await listTrash()
    list.replaceChildren()
    actions.replaceChildren()
    if (!books.length) { list.append(h('p', 'no-results', 'Nothing here.')); return }
    for (const book of books) {
      const days = daysLeft(book)
      const row = bookLine(book, `Removed ${timeAgo(book.deletedAt!)} · ${days} ${days === 1 ? 'day' : 'days'} left`, urls)
      const tools = h('div', 'tool-buttons')
      const restore = button('', 'icon small', async () => {
        await restoreBooks([book.id])
        toast(`Restored “${book.title}”.`)
        changed()
        render()
      })
      restore.title = 'Restore'
      restore.setAttribute('aria-label', `Restore ${book.title}`)
      restore.append(icon(RotateCcw))
      const remove = button('', 'icon small danger-text', async () => {
        const ok = await confirmDialog({
          title: 'Delete for good?', confirm: 'Delete', danger: true,
          message: `“${book.title}”, its reading place, bookmarks and highlights will be deleted. This can’t be undone.`,
        })
        if (!ok) return
        await deleteBook(book.id)
        render()
      })
      remove.title = 'Delete for good'
      remove.setAttribute('aria-label', `Delete ${book.title} for good`)
      remove.append(icon(Trash2))
      tools.append(restore, remove)
      row.append(tools)
      list.append(row)
    }
    actions.append(
      button('Restore all', '', async () => { await restoreBooks(books.map(b => b.id)); toast('Restored.'); changed(); render() }),
      button('Empty', 'danger', async () => {
        const ok = await confirmDialog({
          title: 'Empty Recently deleted?', confirm: 'Delete all', danger: true,
          message: `${books.length} ${books.length === 1 ? 'book' : 'books'} will be deleted for good, with their bookmarks and highlights.`,
        })
        if (!ok) return
        for (const b of books) await deleteBook(b.id)
        render()
      }),
    )
  }
  await render()
}

// The same book more than once: same title and author, often in two formats (EPUB and PDF).
export function findDuplicates(books: BookRecord[]) {
  const key = (b: BookRecord) => `${simplify(b.title)}|${simplify(b.author)}`
  const groups = new Map<string, BookRecord[]>()
  for (const b of books) groups.set(key(b), [...groups.get(key(b)) ?? [], b])
  return [...groups.values()].filter(g => g.length > 1)
}

const simplify = (s: string) => s.toLocaleLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '')
  .replace(/[^\p{L}\p{N}]+/gu, ' ').trim()

export function showDuplicates(books: BookRecord[], remove: (book: BookRecord) => Promise<void>) {
  const urls: string[] = []
  const list = h('div', 'tool-list')
  openDialog({ title: 'Duplicates', className: 'dialog-details', content: list, onClose: () => urls.forEach(URL.revokeObjectURL) })
  const render = (books: BookRecord[]) => {
    list.replaceChildren()
    const groups = findDuplicates(books)
    if (!groups.length) { list.append(h('p', 'no-results', 'No duplicates. Every book is in your library once.')); return }
    list.append(h('p', 'dialog-text', 'These books have the same title and author. Keep the copy you like; removed copies go to Recently deleted.'))
    for (const group of groups) {
      const section = h('div', 'dup-group')
      for (const book of group.sort((a, b) => b.progress - a.progress)) {
        const meta = [book.format, fileSize(book.size), book.lastOpenedAt ? `${percent(book.progress)} read` : `added ${date(book.addedAt)}`].filter(Boolean).join(' · ')
        const row = bookLine(book, meta, urls)
        const removeButton = button('Remove', 'small', async () => {
          await remove(book)
          render(books.filter(b => b.id !== book.id))
        })
        row.append(removeButton)
        section.append(row)
      }
      list.append(section)
    }
  }
  render(books)
}
