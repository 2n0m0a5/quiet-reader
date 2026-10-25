import { makeBook } from '../../vendor/foliate-js/view.js'
import type { Book } from '../reader/foliate'
import { authorString, langString, titleFromFileName } from '../reader/metadata'
import { addBook, getBook, getFile, requestPersistentStorage, updateBook, type BookRecord } from './db'

const COVER_WIDTH = 360

export class ImportError extends Error {}

// Adds a book file to the library. Importing the same file again returns the existing entry.
export async function importBook(file: File): Promise<BookRecord> {
  const id = await fingerprint(file)
  const existing = await getBook(id)
  if (existing) return existing

  let book: Book
  try {
    book = await makeBook(file)
  } catch (err) {
    throw new ImportError(err instanceof Error && err.message.startsWith('PDF')
      ? err.message
      : `“${file.name}” may be damaged, protected by DRM, or in a format Quiet doesn’t read yet.`)
  }

  const record: BookRecord = {
    id,
    title: langString(book.metadata?.title) || titleFromFileName(file.name),
    author: authorString(book.metadata?.author),
    fileName: file.name,
    fileType: file.type,
    size: file.size,
    cover: await coverThumbnail(book),
    addedAt: Date.now(),
    lastOpenedAt: null,
    progress: 0,
    cfi: null,
    collections: [],
    ...detailsOf(book, file.name),
  }
  await addBook(record, file)
  requestPersistentStorage()
  return record
}

// The extra information shown on a book's details page.
function detailsOf(book: Book, fileName: string): Partial<BookRecord> {
  const m = book.metadata ?? {}
  const language = Array.isArray(m.language) ? m.language[0] : m.language
  const description = m.description
    ? new DOMParser().parseFromString(m.description, 'text/html').body.textContent?.trim()
    : ''
  const subjects = m.subject
    ? (Array.isArray(m.subject) ? m.subject : [m.subject]).map(s => authorString(s)).filter(Boolean)
    : []
  return {
    format: formatOf(fileName),
    publisher: authorString(m.publisher),
    language: language ? languageName(language) : '',
    published: m.published ? String(m.published).slice(0, 10) : '',
    description: description ?? '',
    subjects,
  }
}

function formatOf(fileName: string) {
  const ext = fileName.toLowerCase().match(/\.([a-z0-9]+)$/)?.[1] ?? ''
  const names: Record<string, string> = { azw: 'AZW', azw3: 'AZW3', fbz: 'FB2', htm: 'HTML' }
  return names[ext] ?? (ext ? ext.toUpperCase() : 'Unknown')
}

function languageName(code: string) {
  try { return new Intl.DisplayNames(['en'], { type: 'language' }).of(code) ?? code } catch { return code }
}

// Books added before version 2 don't have the extra details yet; read them from the file once.
export async function ensureDetails(record: BookRecord): Promise<BookRecord> {
  if (record.format !== undefined) return record
  const blob = await getFile(record.id)
  if (!blob) return record
  try {
    const details = detailsOf(await makeBook(asFile(record, blob)), record.fileName)
    await updateBook(record.id, details)
    return { ...record, ...details }
  } catch {
    return record
  }
}

// Turns a stored book back into a File, which the reading engine needs (it uses the name to tell formats apart).
export function asFile(record: BookRecord, blob: Blob): File {
  return new File([blob], record.fileName, { type: record.fileType })
}

// A short, stable ID for a file: a hash of its size, start and end.
async function fingerprint(file: File): Promise<string> {
  const chunk = 256 * 1024
  try {
    const parts = await Promise.all([
      file.slice(0, chunk).arrayBuffer(),
      file.slice(Math.max(0, file.size - chunk)).arrayBuffer(),
    ])
    const data = new Uint8Array(parts[0].byteLength + parts[1].byteLength + 8)
    data.set(new Uint8Array(parts[0]), 0)
    data.set(new Uint8Array(parts[1]), parts[0].byteLength)
    new DataView(data.buffer).setFloat64(data.length - 8, file.size)
    const hash = new Uint8Array(await crypto.subtle.digest('SHA-256', data))
    return Array.from(hash.slice(0, 12), b => b.toString(16).padStart(2, '0')).join('')
  } catch {
    // crypto.subtle only exists on https or localhost.
    return `${file.name}:${file.size}`
  }
}

// Covers can be several megabytes. The library only needs a small one.
async function coverThumbnail(book: Book): Promise<Blob | null> {
  try {
    const blob = await book.getCover?.()
    if (!blob) return null
    const bitmap = await createImageBitmap(blob)
    const scale = Math.min(1, COVER_WIDTH / bitmap.width)
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    bitmap.close()
    return await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.85))
  } catch {
    return null
  }
}
