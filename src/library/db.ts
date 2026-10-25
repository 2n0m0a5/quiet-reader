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
}

const DB_NAME = 'quiet'
const DB_VERSION = 1

let dbPromise: Promise<IDBDatabase> | null = null

function db(): Promise<IDBDatabase> {
  dbPromise ??= new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const d = request.result
      if (!d.objectStoreNames.contains('books')) d.createObjectStore('books', { keyPath: 'id' })
      if (!d.objectStoreNames.contains('files')) d.createObjectStore('files')
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

async function store(name: 'books' | 'files', mode: IDBTransactionMode = 'readonly') {
  return (await db()).transaction(name, mode).objectStore(name)
}

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
  const tx = (await db()).transaction(['books', 'files'], 'readwrite')
  tx.objectStore('books').delete(id)
  tx.objectStore('files').delete(id)
  await finished(tx)
}

// Ask the browser not to clear our storage when the device is low on space.
export function requestPersistentStorage() {
  navigator.storage?.persist?.().catch(() => {})
}
