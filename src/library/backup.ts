// Library backup and restore: everything in one file you keep, to bring back on this phone or
// another one (and on the Play Store version, which needs the test app uninstalled first).
//
// A backup is a zip (zip.ts) named "Quiet backup <date>.quietbackup":
//   quiet-backup.json   the records: books (without covers), collections, highlights, notes and
//                       quotes, reading time, your fonts' details, and Quiet's settings
//   books/<id>          each book file
//   covers/<id>         each cover
//   fonts/<id>          each font you added
// Restoring adds what's missing and never deletes anything: books you don't have come in, a book
// you have takes the backup's reading place if the backup's is newer, highlights and collections
// you don't have are added, and reading time keeps the larger of the two.
import { isNativeApp, saveFile } from '../app/native'
import { readStore, writeStore, putFile, getFile, countBooks, type BookRecord, type FontRecord, type Annotation, type ReadingDay, type Collection } from './db'
import { readZip, writeZip, type ZipEntry } from './zip'

const FORMAT = 'quiet-backup'
const VERSION = 1
export const LAST_BACKUP_KEY = 'quiet:last-backup'
// Settings that belong to this phone, not to the library.
const LOCAL_ONLY = ['quiet:seen-version', 'quiet:welcome-seen', 'quiet:test-safety', LAST_BACKUP_KEY, 'quiet:book-folders']

interface Manifest {
  format: string
  version: number
  createdAt: number
  app: string
  books: (Omit<BookRecord, 'cover'> & { hasCover?: boolean })[]
  collections: Collection[]
  annotations: Annotation[]
  reading: ReadingDay[]
  fonts: Omit<FontRecord, 'data'>[]
  settings: Record<string, string>
}

const fileName = (id: string) => id.replace(/[^A-Za-z0-9_-]/g, '_')

export function lastBackup(): number | null {
  try { const v = Number(localStorage.getItem(LAST_BACKUP_KEY)); return v > 0 ? v : null } catch { return null }
}

// Makes the backup file. `progress` gets 0 → 1.
export async function makeBackup(appVersion: string, progress?: (fraction: number) => void): Promise<{ blob: Blob; name: string; books: number }> {
  const [books, collections, annotations, reading, fonts] = await Promise.all([
    readStore<BookRecord>('books'), readStore<Collection>('collections'), readStore<Annotation>('annotations'),
    readStore<ReadingDay>('reading'), readStore<FontRecord>('fonts'),
  ])
  const entries: ZipEntry[] = []
  const saved: Manifest['books'] = []
  for (const { cover, ...book } of books) {
    const file = await getFile(book.id)
    if (!file) continue
    entries.push({ name: `books/${fileName(book.id)}`, data: file })
    if (cover) entries.push({ name: `covers/${fileName(book.id)}`, data: cover })
    saved.push({ ...book, hasCover: !!cover })
  }
  for (const font of fonts) entries.push({ name: `fonts/${fileName(font.id)}`, data: font.data })
  const settings: Record<string, string> = {}
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)!
    if (key.startsWith('quiet:') && !LOCAL_ONLY.includes(key)) settings[key] = localStorage.getItem(key) ?? ''
  }
  const manifest: Manifest = {
    format: FORMAT, version: VERSION, createdAt: Date.now(), app: appVersion,
    books: saved, collections, annotations, reading,
    fonts: fonts.map(({ data, ...f }) => f), settings,
  }
  entries.unshift({ name: 'quiet-backup.json', data: new Blob([JSON.stringify(manifest)], { type: 'application/json' }) })
  const blob = await writeZip(entries, progress)
  const date = new Date().toISOString().slice(0, 10)
  return { blob, name: `Quiet backup ${date}.quietbackup`, books: saved.length }
}

// Saves a backup where the reader chooses. False if they cancelled.
export async function saveBackup(appVersion: string, progress?: (step: string) => void): Promise<{ saved: boolean; books: number }> {
  const { blob, name, books } = await makeBackup(appVersion, f => progress?.(`Preparing… ${Math.round(f * 100)}%`))
  const saved = await saveFile(blob, name, 'application/zip', f => progress?.(`Saving… ${Math.round(f * 100)}%`))
  if (saved) try { localStorage.setItem(LAST_BACKUP_KEY, String(Date.now())) } catch { /* storage unavailable */ }
  return { saved, books }
}

export interface BackupSummary { books: number; notes: number; createdAt: number; manifest: Manifest; files: Map<string, () => Promise<Blob>> }

// Opens a backup file and says what's in it, without changing anything yet.
export async function readBackup(file: Blob): Promise<BackupSummary> {
  const files = await readZip(file)
  const json = files.get('quiet-backup.json')
  if (!json) throw new Error('This isn’t a Quiet backup.')
  const manifest = JSON.parse(await (await json()).text()) as Manifest
  if (manifest.format !== FORMAT) throw new Error('This isn’t a Quiet backup.')
  if (manifest.version > VERSION) throw new Error('This backup was made by a newer Quiet. Update Quiet first.')
  return { books: manifest.books.length, notes: manifest.annotations.filter(a => a.type === 'highlight').length, createdAt: manifest.createdAt, manifest, files }
}

// Puts a backup into the library (see the top of this file). Returns what changed.
export async function restoreBackup({ manifest, files }: BackupSummary, progress?: (step: string) => void) {
  const wasEmpty = await countBooks() === 0
  const byId = <T extends { id: string }>(list: T[]) => new Map(list.map(x => [x.id, x]))
  const [books, collections, annotations, reading, fonts] = await Promise.all([
    readStore<BookRecord>('books').then(byId), readStore<Collection>('collections').then(byId),
    readStore<Annotation>('annotations').then(byId), readStore<ReadingDay>('reading').then(byId), readStore<FontRecord>('fonts').then(byId),
  ])

  let added = 0, updated = 0
  const bookRecords: BookRecord[] = []
  for (const [i, { hasCover, ...book }] of manifest.books.entries()) {
    progress?.(`Restoring books… ${i + 1} of ${manifest.books.length}`)
    const mine = books.get(book.id)
    if (!mine) {
      const file = await files.get(`books/${fileName(book.id)}`)?.()
      if (!file) continue
      const cover = hasCover ? await files.get(`covers/${fileName(book.id)}`)?.() ?? null : null
      await putFile(book.id, file)
      bookRecords.push({ ...book, cover } as BookRecord)
      added++
    } else if ((book.lastOpenedAt ?? 0) > (mine.lastOpenedAt ?? 0)) {
      bookRecords.push({ ...mine, progress: book.progress, cfi: book.cfi, lastOpenedAt: book.lastOpenedAt, finished: book.finished, finishedAt: book.finishedAt, deletedAt: null })
      updated++
    }
  }

  const fontRecords: FontRecord[] = []
  for (const font of manifest.fonts) {
    if (fonts.has(font.id)) continue
    const data = await files.get(`fonts/${fileName(font.id)}`)?.()
    if (data) fontRecords.push({ ...font, data } as FontRecord)
  }
  const newNotes = manifest.annotations.filter(a => !annotations.has(a.id))
  await writeStore('collections', manifest.collections.filter(c => !collections.has(c.id)))
  await writeStore('annotations', newNotes)
  await writeStore('reading', manifest.reading.filter(r => (reading.get(r.id)?.seconds ?? -1) < r.seconds))
  await writeStore('fonts', fontRecords)
  await writeStore('books', bookRecords) // last: a book shows up only when the rest is in

  // Settings come along only into an empty library (a new phone); otherwise yours stay.
  let settings = false
  if (wasEmpty) {
    for (const [key, value] of Object.entries(manifest.settings ?? {}))
      if (!LOCAL_ONLY.includes(key)) try { localStorage.setItem(key, value); settings = true } catch { /* storage unavailable */ }
  }
  return { added, updated, notes: newNotes.length, fonts: fontRecords.length, settings }
}

// A file picker for backups. (The app's picker ignores file types it doesn't know, so it takes any.)
export function pickBackupFile(): Promise<File | null> {
  return new Promise(resolve => {
    const input = document.createElement('input')
    input.type = 'file'
    if (!isNativeApp) input.accept = '.quietbackup,.zip,application/zip'
    input.addEventListener('change', () => resolve(input.files?.[0] ?? null))
    input.addEventListener('cancel', () => resolve(null))
    input.click()
  })
}
