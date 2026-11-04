// More about a book, for its details sheet: read from the file (length, chapters, ISBN, what's
// inside) and from your reading (time, sittings, notes). Worked out when the sheet opens, then
// remembered until the app closes.
import { loadBook } from '../formats/load'
import { getFile, readStore, type Annotation, type BookRecord, type ReadingDay } from './db'

export interface BookFacts {
  words?: number
  pages?: number          // fixed-layout books (PDF, comics): real pages
  chapters?: number
  identifier?: string     // ISBN when the book has one
  pictures?: number
  fonts?: number
  readSeconds: number
  sittings: number
  started?: string        // first day read, "2026-10-24"
  highlights: number
  notes: number
  quotes: number
  bookmarks: number
  wordsPerMinute?: number // your pace across all books
}

const cache = new Map<string, Promise<BookFacts>>()
export const WORDS_PER_PAGE = 250 // the same "page" the reading stats use

export function bookFacts(record: BookRecord): Promise<BookFacts> {
  let facts = cache.get(record.id)
  if (!facts) {
    facts = work(record)
    cache.set(record.id, facts)
    facts.catch(() => cache.delete(record.id))
  }
  return facts
}

async function work(record: BookRecord): Promise<BookFacts> {
  const [reading, annotations] = await Promise.all([readStore<ReadingDay>('reading'), readStore<Annotation>('annotations')])
  const mine = reading.filter(r => r.bookId === record.id)
  const notes = annotations.filter(a => a.bookId === record.id)
  const allSeconds = reading.reduce((n, r) => n + r.seconds, 0)
  const allPages = reading.reduce((n, r) => n + r.pages, 0)
  const facts: BookFacts = {
    readSeconds: mine.reduce((n, r) => n + r.seconds, 0),
    sittings: mine.reduce((n, r) => n + (r.sittings ?? 1), 0),
    started: mine.map(r => r.day).sort()[0],
    highlights: notes.filter(a => a.type === 'highlight' && !a.quote).length,
    notes: notes.filter(a => a.note).length,
    quotes: notes.filter(a => a.quote).length,
    bookmarks: notes.filter(a => a.type === 'bookmark').length,
    // Only once there's enough reading to say (10 minutes).
    wordsPerMinute: allSeconds > 600 && allPages ? Math.round(allPages * WORDS_PER_PAGE / (allSeconds / 60)) : undefined,
  }

  const blob = await getFile(record.id)
  if (!blob) return facts
  try {
    const book = await loadBook(new File([blob], record.fileName, { type: record.fileType }))
    const meta = (book.metadata ?? {}) as { identifier?: unknown }
    const ids = ([] as unknown[]).concat(meta.identifier ?? []).map(String)
    facts.identifier = ids.find(id => /97[89][\d-]{10,}/.test(id))?.match(/97[89][\d-]{10,}/)?.[0] ?? ids.find(id => /^\d[\d-]{8,}[\dX]$/.test(id))
    const sections = (book.sections ?? []) as { linear?: string; createDocument?: () => Promise<Document> }[]
    if (book.rendition?.layout === 'pre-paginated' || !sections.some(s => s.createDocument)) {
      facts.pages = sections.length || undefined
    } else {
      facts.chapters = (book.toc?.length || sections.filter(s => s.linear !== 'no').length) || undefined
      let words = 0, pictures = 0
      for (const section of sections) {
        if (section.linear === 'no' || !section.createDocument) continue
        const doc = await section.createDocument()
        words += (doc.body?.textContent ?? '').split(/\s+/).filter(Boolean).length
        pictures += doc.querySelectorAll('img, image').length
      }
      facts.words = words || undefined
      facts.pictures = pictures
    }
    const manifest = (book as { resources?: { manifest?: { mediaType?: string }[] } }).resources?.manifest
    if (manifest) facts.fonts = manifest.filter(i => /font|opentype|truetype|woff/.test(i.mediaType ?? '')).length
  } catch (err) {
    console.warn('Could not read the book for its details:', err)
  }
  return facts
}
