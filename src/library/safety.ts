// The safety copy (Android app only).
//
// The library lives in the WebView's database (db.ts). Android's WebView can lose that database,
// for example after a crash, while the rest of the app's data survives; that is how a reader's
// whole library once vanished. So Quiet keeps a second copy in its own private app storage:
//   safety/library.json         every record: books, notes, quotes, reading time, collections, fonts, settings
//   safety/books/<id>           each book file (copied once)
//   safety/books/<id>.cover-<n> each cover (n = its size, so a changed cover is copied again)
//   safety/fonts/<id>           each font you added
// It follows every change a few seconds later. When Quiet starts with an empty library but the
// safety copy has books, it puts them back (recoverLibrary).
import { safetyCopyAvailable, safetyDelete, safetyList, safetyRead, safetyWrite } from '../app/native'
import { countBooks, getFile, onLibraryChange, putFile, readStore, writeStore, type BookRecord, type FontRecord } from './db'

const FORMAT = 1

type SavedBook = Omit<BookRecord, 'cover'> & { coverFile?: string | null }
type SavedFont = Omit<FontRecord, 'data'>

interface Snapshot {
  format: number
  savedAt: number
  books: SavedBook[]
  collections: unknown[]
  annotations: unknown[]
  reading: unknown[]
  fonts: SavedFont[]
  settings: Record<string, string>
}

// Ids become file names: keep them to safe characters.
const fileName = (id: string) => id.replace(/[^A-Za-z0-9_-]/g, '_')

let timer = 0
let running: Promise<void> | null = null
let again = false
let started = false
let emptyOnPurpose = false

// Starts following the library. Call it after recoverLibrary, never before: otherwise an empty
// library could overwrite a good safety copy.
export function startSafetyCopy() {
  if (!safetyCopyAvailable || started) return
  started = true
  onLibraryChange(() => schedule())
  // Leaving the app (or switching away) saves at once.
  document.addEventListener('visibilitychange', () => { if (document.hidden) void saveSafetyCopy() })
  // The first copy soon after start also brings libraries from before 0.4.0 into the safety copy.
  schedule(4000)
}

// The reader chose "Delete everything": the empty library is meant, so the copy may be emptied too.
export function libraryEmptiedOnPurpose() {
  emptyOnPurpose = true
  void saveSafetyCopy()
}

// Waits for a quiet moment, but no longer than MAX_WAIT: while reading, the place is saved every
// few seconds, which would otherwise put the copy off for as long as the reading goes on.
const MAX_WAIT = 20_000
let pendingSince = 0

function schedule(ms = 2500) {
  window.clearTimeout(timer)
  pendingSince ||= Date.now()
  const wait = Math.min(ms, Math.max(0, pendingSince + MAX_WAIT - Date.now()))
  timer = window.setTimeout(() => void saveSafetyCopy(), wait)
}

export function saveSafetyCopy(): Promise<void> {
  if (!started) return Promise.resolve()
  window.clearTimeout(timer)
  pendingSince = 0
  if (running) { again = true; return running }
  running = save()
    .catch(err => console.warn('Safety copy failed:', err))
    .finally(() => {
      running = null
      if (again) { again = false; schedule(500) }
    })
  return running
}

async function readSnapshot(): Promise<Snapshot | null> {
  try {
    const blob = await safetyRead('library.json')
    if (!blob) return null
    const snapshot = JSON.parse(await blob.text()) as Snapshot
    return snapshot.format === FORMAT && Array.isArray(snapshot.books) ? snapshot : null
  } catch {
    return null
  }
}

async function save() {
  const [books, collections, annotations, reading, fonts] = await Promise.all([
    readStore<BookRecord>('books'), readStore('collections'), readStore('annotations'), readStore('reading'), readStore<FontRecord>('fonts'),
  ])

  // An empty library replaces a copy with books only when that's what the reader wanted:
  // "Delete everything", or the last books leaving Recently deleted after 30 days.
  if (!books.length && !emptyOnPurpose) {
    const previous = await readSnapshot()
    if (previous?.books.some(b => !b.deletedAt)) return
  }
  emptyOnPurpose = false

  // Files first, then the record list, then cleaning up: library.json never names a missing file.
  const haveBooks = new Set(await safetyList('books'))
  const keepBooks = new Set<string>()
  const saved: SavedBook[] = []
  for (const { cover, ...book } of books) {
    const name = fileName(book.id)
    if (!haveBooks.has(name)) {
      const file = await getFile(book.id)
      if (!file) continue // a record without its file can't be read anyway
      await safetyWrite(`books/${name}`, file)
    }
    keepBooks.add(name)
    let coverFile: string | null = null
    if (cover) {
      coverFile = `${name}.cover-${cover.size}`
      if (!haveBooks.has(coverFile)) await safetyWrite(`books/${coverFile}`, cover)
      keepBooks.add(coverFile)
    }
    saved.push({ ...book, coverFile })
  }

  const haveFonts = new Set(await safetyList('fonts'))
  const keepFonts = new Set<string>()
  const savedFonts: SavedFont[] = []
  for (const { data, ...font } of fonts) {
    const name = fileName(font.id)
    if (!haveFonts.has(name)) await safetyWrite(`fonts/${name}`, data)
    keepFonts.add(name)
    savedFonts.push(font)
  }

  const settings: Record<string, string> = {}
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)!
    if (key.startsWith('quiet:')) settings[key] = localStorage.getItem(key) ?? ''
  }

  const snapshot: Snapshot = { format: FORMAT, savedAt: Date.now(), books: saved, collections, annotations, reading, fonts: savedFonts, settings }
  await safetyWrite('library.json', new Blob([JSON.stringify(snapshot)], { type: 'application/json' }))

  for (const name of haveBooks) if (!keepBooks.has(name)) await safetyDelete(`books/${name}`)
  for (const name of haveFonts) if (!keepFonts.has(name)) await safetyDelete(`fonts/${name}`)
}

// If the library is empty but the safety copy has books, puts everything back.
// Returns how many books came back (0 when nothing was needed or nothing could be done).
export async function recoverLibrary(): Promise<number> {
  if (!safetyCopyAvailable) return 0
  try {
    if (await countBooks() > 0) return 0
  } catch (err) {
    console.error('The library database could not be opened:', err)
    return 0
  }
  const snapshot = await readSnapshot()
  if (!snapshot?.books.length) return 0

  const books: BookRecord[] = []
  for (const { coverFile, ...book } of snapshot.books) {
    const file = await safetyRead(`books/${fileName(book.id)}`)
    if (!file) continue
    await putFile(book.id, file)
    const cover = coverFile ? await safetyRead(`books/${coverFile}`) : null
    books.push({ ...book, cover } as BookRecord)
  }
  if (!books.length) return 0

  const fonts: FontRecord[] = []
  for (const font of snapshot.fonts ?? []) {
    const data = await safetyRead(`fonts/${fileName(font.id)}`)
    if (data) fonts.push({ ...font, data } as FontRecord)
  }

  await writeStore('collections', snapshot.collections ?? [])
  await writeStore('annotations', snapshot.annotations ?? [])
  await writeStore('reading', snapshot.reading ?? [])
  await writeStore('fonts', fonts)
  await writeStore('books', books) // last: a library with books means the rest is there too

  // Settings normally survive (they're stored apart); only fill in what's missing.
  for (const [key, value] of Object.entries(snapshot.settings ?? {}))
    try { if (localStorage.getItem(key) === null) localStorage.setItem(key, value) } catch { /* storage unavailable */ }

  return books.length
}
