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
}

export const FINISHED = 0.995

export type Status = 'new' | 'reading' | 'finished'

export function statusOf(book: BookRecord): Status {
  if (book.finished === true) return 'finished'
  if (book.finished !== false && book.progress >= FINISHED) return 'finished'
  return book.lastOpenedAt ? 'reading' : 'new'
}

const DB_NAME = 'quiet'
const DB_VERSION = 2

let dbPromise: Promise<IDBDatabase> | null = null

function db(): Promise<IDBDatabase> {
  dbPromise ??= new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const d = request.result
      if (!d.objectStoreNames.contains('books')) d.createObjectStore('books', { keyPath: 'id' })
      if (!d.objectStoreNames.contains('files')) d.createObjectStore('files')
      if (!d.objectStoreNames.contains('collections')) d.createObjectStore('collections', { keyPath: 'id' })
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

type StoreName = 'books' | 'files' | 'collections' | 'annotations'

async function store(name: StoreName, mode: IDBTransactionMode = 'readonly') {
  return (await db()).transaction(name, mode).objectStore(name)
}

export const newId = () => crypto.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`

// ---------- Books ----------

export async function listBooks(): Promise<BookRecord[]> {
  return done((await store('books')).getAll())
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
  if (record) books.put({ ...record, ...patch })
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

export async function saveAnnotation(annotation: Annotation) {
  await done((await store('annotations', 'readwrite')).put(annotation))
}

export async function deleteAnnotation(id: string) {
  await done((await store('annotations', 'readwrite')).delete(id))
}

// Deletes every book, file, collection, bookmark and highlight.
export async function clearLibrary() {
  const tx = (await db()).transaction(['books', 'files', 'collections', 'annotations'], 'readwrite')
  for (const name of ['books', 'files', 'collections', 'annotations'] as const) tx.objectStore(name).clear()
  await finished(tx)
}

// Ask the browser not to clear our storage when the device is low on space.
export function requestPersistentStorage() {
  navigator.storage?.persist?.().catch(() => {})
}
