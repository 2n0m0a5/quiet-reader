// Opens any supported file as a book for the reading engine.
// EPUB, MOBI/AZW3, FB2, CBZ and PDF are handled by foliate-js; TXT and HTML by Quiet's own adapter.
import { makeBook } from '../../vendor/foliate-js/view.js'
import type { Book } from '../reader/foliate'
import { makeHtmlBook, makeTextBook } from './text-book'

const ext = (name: string) => name.toLowerCase().match(/\.([a-z0-9]+)$/)?.[1] ?? ''

export async function loadBook(file: File): Promise<Book> {
  const e = ext(file.name)
  if (e === 'txt' || file.type === 'text/plain') return makeTextBook(file)
  if (e === 'html' || e === 'htm' || e === 'xhtml' || file.type === 'text/html') return makeHtmlBook(file)
  return makeBook(file)
}

// For the file picker.
export const ACCEPTED = [
  '.epub', '.mobi', '.azw3', '.azw', '.fb2', '.fbz', '.cbz', '.pdf', '.txt', '.html', '.htm',
  'application/epub+zip', 'application/x-mobipocket-ebook', 'application/x-fictionbook+xml',
  'application/vnd.comicbook+zip', 'application/pdf', 'text/plain', 'text/html',
].join(',')
