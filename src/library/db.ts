// The library lives in IndexedDB, the browser's built-in database.
// "books" holds what the library screen needs; "files" holds the book files themselves,
// so listing the library never has to load whole books.

export interface BookRecord {
  id: string
  title: string
  author: string
  fileName: string
  fileType: string
  size: number
  cover: Blob | null
  addedAt: number
  lastOpenedAt: number | null
  progress: number      // 0–1
  cfi: string | null    // exact reading position

  // Added in version 2. Older records may not have them yet.
  finished?: boolean | null   // true/false when set by hand; otherwise decided by progress
  collections?: string[]      // collection ids
  format?: string             // "EPUB", "MOBI", …
  publisher?: string
  language?: string
  published?: string
  description?: string
  subjects?: string[]

  // Added in version 3.
  tags?: string[]
  wantToRead?: boolean
  rating?: number             // 1–5 stars, 0 or missing for none
  review?: string
  series?: string
  seriesIndex?: number | null // the book's number in the series
  deletedAt?: number | null   // in Recently deleted since then
  finishedAt?: number | null  // when it was finished (for reading stats)
}

// Reading time, per day and book: what the reading stats are made from.
export interface ReadingDay {
  id: string        // `${day}|${bookId}`
  day: string       // "2026-10-27", in the device's time zone
  bookId: string
  seconds: number
  pages: number     // pages turned forward (a "page" is about 250 words, the same in every book)
  hours?: number[]  // seconds read in each hour of the day, 0–23 (from 0.3.3; older records have none)
  sittings?: number // how many times the book was opened and read that day (from 0.3.3)
}

export interface Collection {
  id: string
  name: string
  createdAt: number
}

export interface Annotation {
  id: string
  bookId: string
  type: 'bookmark' | 'highlight'
  cfi: string
  text: string          // the highlighted text, or the start of the page for a bookmark
  note?: string
  color?: string
  label?: string        // chapter title
  progress?: number
  createdAt: number

  // Added in version 3.
  style?: MarkStyle     // how a highlight is drawn; missing means 'highlight'
  quote?: boolean       // saved to Quotes
  speaker?: string      // who says it, for a quote: a character; empty means the narrator or author
  tags?: string[]
}

// 'plain' draws no colour, only a thin outline: a passage saved just as a quote.
export type MarkStyle = 'highlight' | 'underline' | 'strike' | 'plain'

export const FINISHED = 0.995

export type Status = 'want' | 'new' | 'reading' | 'finished'

export function statusOf(book: BookRecord): Status {
  if (book.finished === true) return 'finished'
  if (book.finished !== false && book.progress >= FINISHED) return 'finished'
  if (book.wantToRead) return 'want'
  return book.lastOpenedAt ? 'reading' : 'new'
}

// Books stay in Recently deleted for 30 days, then they're deleted for good.
export const TRASH_DAYS = 30

const DB_NAME = 'quiet'
const DB_VERSION = 3

let dbPromise: Promise<IDBDatabase> | null = null

function db(): Promise<IDBDatabase> {
  dbPromise ??= new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const d = request.result
      if (!d.objectStoreNames.contains('books')) d.createObjectStore('books', { keyPath: 'id' })
      if (!d.objectStoreNames.contains('files')) d.createObjectStore('files')
      if (!d.objectStoreNames.contains('collections')) d.createObjectStore('collections', { keyPath: 'id' })
      if (!d.objectStoreNames.contains('reading')) {
        const reading = d.createObjectStore('reading', { keyPath: 'id' })
        reading.createIndex('day', 'day')
      }
      if (!d.objectStoreNames.contains('annotations')) {
        const annotations = d.createObjectStore('annotations', { keyPath: 'id' })
        annotations.createIndex('bookId', 'bookId')
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
  return dbPromise
}

const done = <T>(request: IDBRequest<T>) => new Promise<T>((resolve, reject) => {
  request.onsuccess = () => resolve(request.result)
  request.onerror = () => reject(request.error)
})

const finished = (tx: IDBTransaction) => new Promise<void>((resolve, reject) => {
  tx.oncomplete = () => resolve()
  tx.onerror = tx.onabort = () => reject(tx.error)
})

type StoreName = 'books' | 'files' | 'collections' | 'annotations' | 'reading'

async function store(name: StoreName, mode: IDBTransactionMode = 'readonly') {
  return (await db()).transaction(name, mode).objectStore(name)
}

export const newId = () => crypto.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`

// ---------- Books ----------

// Books in the library (not the ones in Recently deleted).
export async function listBooks(): Promise<BookRecord[]> {
  return (await listAllBooks()).filter(b => !b.deletedAt)
}

export async function listAllBooks(): Promise<BookRecord[]> {
  return done((await store('books')).getAll())
}

// ---------- Recently deleted ----------

export async function listTrash(): Promise<BookRecord[]> {
  const all = await listAllBooks()
  return all.filter(b => b.deletedAt).sort((a, b) => b.deletedAt! - a.deletedAt!)
}

export async function moveToTrash(ids: string[]) {
  const now = Date.now()
  for (const id of ids) await updateBook(id, { deletedAt: now })
}

export async function restoreBooks(ids: string[]) {
  for (const id of ids) await updateBook(id, { deletedAt: null })
}

// Deletes books that have been in Recently deleted for longer than 30 days.
export async function purgeTrash() {
  const limit = Date.now() - TRASH_DAYS * 86_400_000
  for (const book of await listTrash()) if (book.deletedAt! < limit) await deleteBook(book.id)
}

export async function getBook(id: string): Promise<BookRecord | undefined> {
  return done((await store('books')).get(id))
}

export async function getFile(id: string): Promise<Blob | undefined> {
  return done((await store('files')).get(id))
}

export async function addBook(record: BookRecord, file: Blob) {
  const tx = (await db()).transaction(['books', 'files'], 'readwrite')
  tx.objectStore('books').put(record)
  tx.objectStore('files').put(file, record.id)
  await finished(tx)
}

export async function updateBook(id: string, patch: Partial<BookRecord>) {
  const tx = (await db()).transaction('books', 'readwrite')
  const books = tx.objectStore('books')
  const record = await done<BookRecord | undefined>(books.get(id))
  if (record) {
    const next = { ...record, ...patch }
    // Remember when a book was finished (by reading to the end, or by marking it), for the stats.
    if (statusOf(next) === 'finished' && !next.finishedAt) next.finishedAt = Date.now()
    if (statusOf(next) !== 'finished' && next.finishedAt) next.finishedAt = null
    books.put(next)
  }
  await finished(tx)
}

export async function deleteBook(id: string) {
  const tx = (await db()).transaction(['books', 'files', 'annotations'], 'readwrite')
  tx.objectStore('books').delete(id)
  tx.objectStore('files').delete(id)
  const annotations = tx.objectStore('annotations')
  const keys = await done(annotations.index('bookId').getAllKeys(id))
  for (const key of keys) annotations.delete(key)
  await finished(tx)
}

// ---------- Collections ----------

export async function listCollections(): Promise<Collection[]> {
  const all = await done((await store('collections')).getAll()) as Collection[]
  return all.sort((a, b) => a.name.localeCompare(b.name))
}

export async function saveCollection(collection: Collection) {
  await done((await store('collections', 'readwrite')).put(collection))
}

// Deletes the collection and takes it off every book. The books stay in the library.
export async function deleteCollection(id: string) {
  const tx = (await db()).transaction(['collections', 'books'], 'readwrite')
  tx.objectStore('collections').delete(id)
  const books = tx.objectStore('books')
  for (const book of await done(books.getAll()) as BookRecord[]) {
    if (book.collections?.includes(id))
      books.put({ ...book, collections: book.collections.filter(c => c !== id) })
  }
  await finished(tx)
}

// ---------- Bookmarks and highlights ----------

export async function listAnnotations(bookId: string): Promise<Annotation[]> {
  const all = await done((await store('annotations')).index('bookId').getAll(bookId)) as Annotation[]
  return all.sort((a, b) => (a.progress ?? 0) - (b.progress ?? 0) || a.createdAt - b.createdAt)
}

// Highlights, notes and quotes from every book in the library (not ones in Recently deleted).
export async function listAllAnnotations(): Promise<Annotation[]> {
  const [all, books] = await Promise.all([done((await store('annotations')).getAll()) as Promise<Annotation[]>, listBooks()])
  const ids = new Set(books.map(b => b.id))
  return all.filter(a => ids.has(a.bookId))
}

export async function saveAnnotation(annotation: Annotation) {
  await done((await store('annotations', 'readwrite')).put(annotation))
}

export async function deleteAnnotation(id: string) {
  await done((await store('annotations', 'readwrite')).delete(id))
}

// Deletes every book, file, collection, bookmark, highlight and reading record.
export async function clearLibrary() {
  const names = ['books', 'files', 'collections', 'annotations', 'reading'] as const
  const tx = (await db()).transaction(names, 'readwrite')
  for (const name of names) tx.objectStore(name).clear()
  await finished(tx)
}

// ---------- Reading time ----------

// `hours`: the seconds read in each hour of the day; `newSitting`: the first save since the book was opened.
export async function addReading(day: string, bookId: string, seconds: number, pages: number, hours: number[] = [], newSitting = false) {
  const tx = (await db()).transaction('reading', 'readwrite')
  const reading = tx.objectStore('reading')
  const id = `${day}|${bookId}`
  const record = await done<ReadingDay | undefined>(reading.get(id))
  const total = Array.from({ length: 24 }, (_, h) => (record?.hours?.[h] ?? 0) + (hours[h] ?? 0))
  reading.put({
    id, day, bookId, seconds: (record?.seconds ?? 0) + seconds, pages: (record?.pages ?? 0) + pages,
    hours: total, sittings: (record?.sittings ?? 0) + (newSitting ? 1 : 0),
  } satisfies ReadingDay)
  await finished(tx)
}

export async function listReading(): Promise<ReadingDay[]> {
  return done((await store('reading')).getAll())
}

// Ask the browser not to clear our storage when the device is low on space.
export function requestPersistentStorage() {
  navigator.storage?.persist?.().catch(() => {})
}
