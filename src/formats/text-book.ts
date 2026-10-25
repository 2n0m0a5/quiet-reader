// Plain text (.txt) and web pages (.html) as books.
// The text is split into chapters and given to the reading engine in the same shape
// as its own EPUB and FB2 readers produce (see vendor/foliate-js/README.md, "The Main Interface for Books").
import type { Book } from '../reader/foliate'

interface Chapter { title: string; html: string }

const XHTML = 'application/xhtml+xml'
const MAX_CHAPTER = 60_000   // characters; longer stretches without headings are split up

const escape = (s: string) => s.replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]!))

const page = (title: string, body: string) => `<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>${escape(title)}</title>
<style>h1, h2 { text-align: center; margin: 1.5em 0 1em; } p { margin: 0 0 .9em; } .text-indent p { margin: 0; text-indent: 1.5em; }</style>
</head><body>${body}</body></html>`

// Decodes text as UTF-8, falling back to Windows-1251 (common for older Russian texts), then Latin-1.
async function decode(file: Blob) {
  const bytes = new Uint8Array(await file.arrayBuffer())
  for (const encoding of ['utf-8', 'windows-1251']) {
    try { return new TextDecoder(encoding, { fatal: true }).decode(bytes) } catch { /* try the next */ }
  }
  return new TextDecoder('latin1').decode(bytes)
}

// "Author - Title.txt" → { author, title }
function namesFrom(fileName: string) {
  const base = fileName.replace(/\.[^.]+$/, '').replace(/_/g, ' ').trim()
  const parts = base.split(/\s+[-–—]\s+/)
  return parts.length === 2 ? { author: parts[0], title: parts[1] } : { author: '', title: base }
}

// Lines that look like chapter headings, in English and Russian.
// (Not \b after the word: in JavaScript it only works for Latin letters.)
const HEADING = /^\s*((chapter|part|book|prologue|epilogue|interlude|глава|часть|книга|пролог|эпилог)(?=[\s.:]|$)[^\n]{0,80}|[IVXLC]{1,7}\.?|\d{1,3}\.?)\s*$/iu

export async function makeTextBook(file: File): Promise<Book> {
  const text = (await decode(file)).replace(/\r\n?/g, '\n').replace(/^﻿/, '')
  const { author, title } = namesFrom(file.name)

  // Paragraphs are separated by blank lines, or (in files without blank lines) by every line break.
  const lines = text.split('\n')
  const blankLines = lines.filter(l => !l.trim()).length
  const paragraphs = blankLines > lines.length / 20
    ? text.split(/\n\s*\n/).map(p => p.replace(/\s*\n\s*/g, ' ').trim()).filter(Boolean)
    : lines.map(l => l.trim()).filter(Boolean)

  const chapters: Chapter[] = []
  let current: Chapter = { title: '', html: '' }
  const flush = () => { if (current.html) chapters.push(current) }
  for (const p of paragraphs) {
    if (p.length < 90 && HEADING.test(p)) {
      flush()
      current = { title: p, html: `<h2>${escape(p)}</h2>` }
    } else {
      if (current.html.length > MAX_CHAPTER) {
        flush()
        current = { title: '', html: '' }
      }
      current.html += `<p>${escape(p)}</p>`
    }
  }
  flush()
  if (!chapters.length) chapters.push({ title, html: '<p></p>' })
  chapters.forEach((c, i) => { c.title ||= chapters.length > 1 ? `Part ${i + 1}` : title })

  return bookFrom(chapters, { title, author })
}

export async function makeHtmlBook(file: File): Promise<Book> {
  const doc = new DOMParser().parseFromString(await decode(file), 'text/html')
  const names = namesFrom(file.name)
  const title = doc.title.trim() || names.title
  const author = doc.querySelector('meta[name="author"]')?.getAttribute('content') ?? names.author
  // Remove anything that could run code or load from the internet.
  doc.querySelectorAll('script, iframe, object, embed, link, meta, base, form').forEach(el => el.remove())
  for (const el of doc.querySelectorAll('*'))
    for (const attr of [...el.attributes]) if (/^on/i.test(attr.name)) el.removeAttribute(attr.name)

  // One chapter per top-level heading (h1, or h2 if there are no h1s).
  const level = doc.body.querySelector('h1') ? 'H1' : 'H2'
  const serializer = new XMLSerializer()
  const chapters: Chapter[] = []
  let current: Chapter = { title, html: '' }
  for (const node of [...doc.body.childNodes]) {
    if (node instanceof HTMLElement && node.tagName === level && current.html.trim()) {
      chapters.push(current)
      current = { title: node.textContent?.trim() || 'Untitled', html: '' }
    } else if (node instanceof HTMLElement && node.tagName === level) {
      current.title = node.textContent?.trim() || current.title
    }
    current.html += serializer.serializeToString(node)
  }
  if (current.html.trim() || !chapters.length) chapters.push(current)
  return bookFrom(chapters, { title, author })
}

function bookFrom(chapters: Chapter[], meta: { title: string; author: string }): Book {
  const urls: string[] = []
  const sections = chapters.map((chapter, index) => {
    const str = page(chapter.title, chapter.html)
    const blob = new Blob([str], { type: XHTML })
    const url = URL.createObjectURL(blob)
    urls.push(url)
    return {
      id: index,
      load: () => url,
      createDocument: () => new DOMParser().parseFromString(str, XHTML),
      size: blob.size,
    }
  })
  const book = {
    metadata: { title: meta.title, author: meta.author || undefined },
    sections,
    toc: chapters.length > 1 ? chapters.map((c, i) => ({ label: c.title, href: String(i) })) : [],
    dir: 'ltr' as const,
    resolveHref: (href: string) => ({ index: Number(href.split('#')[0]) || 0, anchor: () => null }),
    splitTOCHref: (href: string) => [Number(href), null],
    getTOCFragment: (doc: Document) => doc.body,
    isExternal: (uri: string) => /^\w+:/i.test(uri),
    getCover: () => null,
    destroy: () => urls.forEach(URL.revokeObjectURL),
  }
  return book as unknown as Book
}
