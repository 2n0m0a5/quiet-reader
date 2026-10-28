// Opens any supported file as a book for the reading engine (see formats.ts for the list).
// EPUB, MOBI/AZW3, FB2, CBZ and PDF are handled by foliate-js; the rest become one of those
// shapes here: text and web pages (text-book.ts), Word and OpenDocument (office.ts), RTF (rtf.ts),
// Palm documents (palm.ts), Markdown, and comics or books inside RAR, 7-Zip, TAR and zip archives.
import { makeBook } from '../../vendor/foliate-js/view.js'
import { makeComicBook } from '../../vendor/foliate-js/comic-book.js'
import type { Book } from '../reader/foliate'
import { decode, htmlBook, makeHtmlBook, makeTextBook, namesFrom } from './text-book'
import { extensionOf, isBookFile } from './formats'
import { archiveLoader, type Loader } from './archive'

export { ACCEPTED } from './formats'

const IMAGE = /\.(jpe?g|png|gif|bmp|webp|avif|jxl|svg)$/i
// Hidden files and macOS leftovers inside archives.
const JUNK = /(^|\/)(__MACOSX|\.[^/]*)(\/|$)|thumbs\.db$/i

export async function loadBook(file: File): Promise<Book> {
  const e = extensionOf(file.name)
  if (e === 'txt' || (file.type === 'text/plain' && !e)) return makeTextBook(file)
  if (e === 'html' || e === 'htm' || e === 'xhtml' || (file.type === 'text/html' && !e)) return makeHtmlBook(file)
  if (e === 'md' || e === 'markdown' || file.type === 'text/markdown') return markdownBook(await decode(file), namesFrom(file.name))
  if (e === 'docx') return (await import('./office')).makeDocxBook(file)
  if (e === 'odt') return (await import('./office')).makeOdtBook(file)
  if (e === 'rtf' || /rtf/.test(file.type)) return (await import('./rtf')).makeRtfBook(file)
  if (e === 'pdb' || e === 'prc') {
    const { isPalmDoc, makePalmBook } = await import('./palm')
    if (await isPalmDoc(file)) return makePalmBook(file)
  }
  if (['cbr', 'cb7', 'cbt', 'rar', '7z', 'tar'].includes(e)) return fromArchive(await archiveLoader(file), file)
  if (['zip', 'htmlz', 'txtz'].includes(e) || (e === 'cbz' && !(await isZip(file)))) {
    // A comic saved as .cbz is sometimes really a RAR file.
    const loader = await isZip(file) ? await zipLoader(file) : await archiveLoader(file)
    return fromArchive(loader, file)
  }
  return makeBook(file)
}

async function isZip(file: Blob) {
  const head = new Uint8Array(await file.slice(0, 4).arrayBuffer())
  return head[0] === 0x50 && head[1] === 0x4b && head[2] === 3 && head[3] === 4
}

async function zipLoader(file: Blob): Promise<Loader> {
  const { configure, ZipReader, BlobReader, TextWriter, BlobWriter } = await import('../../vendor/foliate-js/vendor/zip.js')
  configure({ useWebWorkers: false })
  const entries = (await new ZipReader(new BlobReader(file)).getEntries()).filter(e => !e.directory)
  const map = new Map(entries.map(e => [e.filename, e]))
  return {
    entries: entries.map(e => ({ filename: e.filename })),
    loadBlob: async name => map.get(name)!.getData(new BlobWriter()),
    loadText: async name => map.get(name)!.getData(new TextWriter()),
    getSize: name => map.get(name)?.uncompressedSize ?? 0,
  }
}

// What's inside an archive decides how it opens: an EPUB's own files, a web page with its
// pictures (HTMLZ), a text file (TXTZ), comic pages, or a single book file.
async function fromArchive(loader: Loader, file: File): Promise<Book> {
  const names = loader.entries.map(e => e.filename).filter(n => !JUNK.test(n))
  if (names.some(n => n.toLowerCase() === 'meta-inf/container.xml')) {
    const { EPUB } = await import('../../vendor/foliate-js/epub.js')
    return new EPUB(loader).init()
  }
  const images = names.filter(n => IMAGE.test(n))
  // A web page with a few pictures (HTMLZ): not a comic with an index page.
  const html = names.find(n => /\.x?html?$/i.test(n))
  if (html && (extensionOf(file.name) === 'htmlz' || images.length < 20)) {
    return htmlBook(await inlineImages(await loader.loadText(html), loader, html), namesFrom(file.name))
  }
  const inner = names.filter(n => isBookFile(n) && !IMAGE.test(n))
  if (images.length >= 2 || (images.length && !inner.length)) {
    const sorted = images.sort(new Intl.Collator([], { numeric: true, sensitivity: 'base' }).compare)
    const comic = makeComicBook({ entries: sorted.map(filename => ({ filename })), loadBlob: loader.loadBlob, getSize: loader.getSize }, file)
    comic.metadata = { title: namesFrom(file.name).title }
    return comic
  }
  if (inner.length) {
    // One book in the archive (the biggest, if there are several).
    const pick = inner.sort((a, b) => loader.getSize(b) - loader.getSize(a))[0]
    const blob = await loader.loadBlob(pick)
    return loadBook(new File([blob], pick.split('/').pop()!, { type: blob.type }))
  }
  const text = names.find(n => /\.(txt|md|markdown)$/i.test(n))
  if (text) {
    const content = await loader.loadText(text)
    return /\.txt$/i.test(text) ? makeTextBook(new File([content], file.name)) : markdownBook(content, namesFrom(file.name))
  }
  throw new Error('There’s no book or comic in this archive.')
}

// Pictures referenced by a web page inside an archive, put into the page itself.
async function inlineImages(html: string, loader: Loader, page: string) {
  const base = page.includes('/') ? page.slice(0, page.lastIndexOf('/') + 1) : ''
  const srcs = [...new Set([...html.matchAll(/\ssrc="([^"]+)"/g)].map(m => m[1]))]
  for (const src of srcs) {
    if (/^(data|https?):/i.test(src)) continue
    const name = decodeURIComponent(base + src).replace(/^\.\//, '')
    try {
      const ext = extensionOf(name)
      const type = ext === 'svg' ? 'image/svg+xml' : ext === 'jpg' ? 'image/jpeg' : `image/${ext}`
      const blob = new Blob([await loader.loadBlob(name)], { type })
      const url = await new Promise<string>(resolve => {
        const reader = new FileReader()
        reader.onload = () => resolve(reader.result as string)
        reader.readAsDataURL(blob)
      })
      html = html.split(`src="${src}"`).join(`src="${url}"`)
    } catch { /* leave it out */ }
  }
  return html
}

async function markdownBook(text: string, names: { title: string; author: string }) {
  const { marked } = await import('marked')
  const body = await marked.parse(text, { gfm: true })
  // The first heading names the book when the file name doesn't.
  return htmlBook(`<html><head></head><body>${body}</body></html>`, names)
}
