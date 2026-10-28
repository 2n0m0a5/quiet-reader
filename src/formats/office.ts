// Word (.docx) and OpenDocument (.odt) files as books.
// Both are zip archives of XML. The text is turned into plain HTML: headings, paragraphs, bold,
// italic, underline, lists, tables and pictures. Then it's split into chapters at the headings
// like any web page (text-book.ts). Page layout, fonts and colours are left out on purpose:
// Quiet sets those itself.
import type { Book } from '../reader/foliate'
import { escape, htmlBook, namesFrom } from './text-book'

interface Zip { text(name: string): Promise<string | null>; dataUrl(name: string): Promise<string | null> }

async function openZip(file: Blob): Promise<Zip> {
  const { configure, ZipReader, BlobReader, TextWriter, BlobWriter } = await import('../../vendor/foliate-js/vendor/zip.js')
  configure({ useWebWorkers: false })
  const entries = await new ZipReader(new BlobReader(file)).getEntries()
  const find = (name: string) => entries.find((e: { filename: string }) => e.filename === name || e.filename.toLowerCase() === name.toLowerCase())
  return {
    text: async name => { const e = find(name); return e ? e.getData(new TextWriter()) : null },
    dataUrl: async name => {
      const e = find(name)
      if (!e) return null
      const type = /\.png$/i.test(name) ? 'image/png' : /\.gif$/i.test(name) ? 'image/gif' : /\.svg$/i.test(name) ? 'image/svg+xml' : 'image/jpeg'
      const blob: Blob = await e.getData(new BlobWriter(type))
      return new Promise(resolve => {
        const reader = new FileReader()
        reader.onload = () => resolve(reader.result as string)
        reader.onerror = () => resolve(null)
        reader.readAsDataURL(blob)
      })
    },
  }
}

const xml = (s: string) => new DOMParser().parseFromString(s, 'application/xml')
// Children by local name, whatever the namespace prefix.
const kids = (el: Element, name?: string) => [...el.children].filter(c => !name || c.localName === name)
const kid = (el: Element | null | undefined, name: string) => el ? kids(el, name)[0] ?? null : null
const attr = (el: Element | null | undefined, name: string) =>
  el ? [...el.attributes].find(a => a.localName === name)?.value ?? null : null
const metaText = (doc: Document | null, name: string) =>
  doc ? [...doc.getElementsByTagName('*')].find(e => e.localName === name)?.textContent?.trim() ?? '' : ''

// ---------- Word ----------

export async function makeDocxBook(file: File): Promise<Book> {
  const zip = await openZip(file)
  const main = await zip.text('word/document.xml')
  if (!main) throw new Error('This Word file has no text in it.')
  const doc = xml(main)
  const rels = new Map<string, string>()
  const relsXml = await zip.text('word/_rels/document.xml.rels')
  if (relsXml) for (const r of xml(relsXml).getElementsByTagName('*'))
    if (r.localName === 'Relationship') rels.set(attr(r, 'Id') ?? '', attr(r, 'Target') ?? '')
  // Style names, to find headings ("Heading 1", "Title", "Заголовок 1"…).
  const styleNames = new Map<string, string>()
  const stylesXml = await zip.text('word/styles.xml')
  if (stylesXml) for (const s of xml(stylesXml).getElementsByTagName('*'))
    if (s.localName === 'style') styleNames.set(attr(s, 'styleId') ?? '', attr(kid(s, 'name'), 'val') ?? '')
  const headingLevel = (p: Element) => {
    const pPr = kid(p, 'pPr')
    const outline = attr(kid(pPr, 'outlineLvl'), 'val')
    if (outline != null && Number(outline) < 6) return Number(outline) + 1
    const id = attr(kid(pPr, 'pStyle'), 'val') ?? ''
    const name = `${id} ${styleNames.get(id) ?? ''}`.toLowerCase()
    if (/\btitle\b|название/.test(name)) return -1   // the document's title: shown big, but not a chapter of its own
    const m = name.match(/(heading|заголовок|überschrift|titre)\s*(\d)/)
    return m ? Math.min(6, Number(m[2])) : 0
  }

  const run = async (r: Element): Promise<string> => {
    let out = ''
    for (const c of kids(r)) {
      if (c.localName === 't') out += escape(c.textContent ?? '')
      else if (c.localName === 'tab') out += ' '
      else if (c.localName === 'br') out += attr(c, 'type') === 'page' ? '' : '<br/>'
      else if (c.localName === 'drawing' || c.localName === 'pict') {
        const blip = [...c.getElementsByTagName('*')].find(e => e.localName === 'blip' || e.localName === 'imagedata')
        const id = attr(blip, 'embed') ?? attr(blip, 'id')
        const target = id ? rels.get(id) : null
        const src = target ? await zip.dataUrl(`word/${target.replace(/^\/?word\//, '')}`) : null
        if (src) out += `<img src="${src}" alt=""/>`
      }
    }
    const rPr = kid(r, 'rPr')
    const on = (name: string) => { const e = kid(rPr, name); return !!e && attr(e, 'val') !== '0' && attr(e, 'val') !== 'false' && attr(e, 'val') !== 'none' }
    if (!out) return ''
    if (on('b')) out = `<b>${out}</b>`
    if (on('i')) out = `<i>${out}</i>`
    if (on('u')) out = `<u>${out}</u>`
    if (on('strike')) out = `<s>${out}</s>`
    const vert = attr(kid(rPr, 'vertAlign'), 'val')
    if (vert === 'superscript') out = `<sup>${out}</sup>`
    if (vert === 'subscript') out = `<sub>${out}</sub>`
    return out
  }
  const inline = async (p: Element): Promise<string> => {
    let out = ''
    for (const c of kids(p)) {
      if (c.localName === 'r') out += await run(c)
      else if (c.localName === 'hyperlink' || c.localName === 'ins' || c.localName === 'smartTag' || c.localName === 'sdt' || c.localName === 'sdtContent') out += await inline(c)
    }
    return out
  }
  const block = async (el: Element): Promise<string> => {
    if (el.localName === 'p') {
      const content = await inline(el)
      const level = headingLevel(el)
      if (level < 0) return content.trim() ? `<p style="font-size: 1.7em; text-align: center; margin: 1em 0">${content}</p>` : ''
      if (level) return content.trim() ? `<h${level}>${content}</h${level}>` : ''
      if (kid(kid(el, 'pPr'), 'numPr')) return `<li>${content}</li>`
      return content.trim() ? `<p>${content}</p>` : ''
    }
    if (el.localName === 'tbl') {
      let rows = ''
      for (const tr of kids(el, 'tr')) {
        let cells = ''
        for (const tc of kids(tr, 'tc')) cells += `<td>${(await Promise.all(kids(tc).map(block))).join('')}</td>`
        rows += `<tr>${cells}</tr>`
      }
      return `<table>${rows}</table>`
    }
    if (el.localName === 'sdt' || el.localName === 'sdtContent') return (await Promise.all(kids(el).map(block))).join('')
    return ''
  }
  const body = [...doc.getElementsByTagName('*')].find(e => e.localName === 'body')
  let html = body ? (await Promise.all(kids(body).map(block))).join('') : ''
  html = html.replace(/(?:<li>.*?<\/li>)+/gs, list => `<ul>${list}</ul>`)

  const core = await zip.text('docProps/core.xml')
  const coreDoc = core ? xml(core) : null
  const names = namesFrom(file.name)
  const title = metaText(coreDoc, 'title') || names.title
  const author = metaText(coreDoc, 'creator') || names.author
  return htmlBook(`<html><head><title>${escape(title)}</title><meta name="author" content="${escape(author)}"/></head><body>${html}</body></html>`,
    names, { language: metaText(coreDoc, 'language') || undefined })
}

// ---------- OpenDocument ----------

export async function makeOdtBook(file: File): Promise<Book> {
  const zip = await openZip(file)
  const content = await zip.text('content.xml')
  if (!content) throw new Error('This document has no text in it.')
  const doc = xml(content)
  // Which automatic styles mean bold, italic or underline.
  const styleOf = new Map<string, { b: boolean; i: boolean; u: boolean }>()
  for (const s of doc.getElementsByTagName('*')) {
    if (s.localName !== 'style') continue
    const props = kid(s, 'text-properties')
    styleOf.set(attr(s, 'name') ?? '', {
      b: attr(props, 'font-weight') === 'bold',
      i: attr(props, 'font-style') === 'italic',
      u: !!attr(props, 'text-underline-style') && attr(props, 'text-underline-style') !== 'none',
    })
  }
  const styled = (el: Element, html: string) => {
    const s = styleOf.get(attr(el, 'style-name') ?? '')
    if (!s || !html) return html
    if (s.b) html = `<b>${html}</b>`
    if (s.i) html = `<i>${html}</i>`
    if (s.u) html = `<u>${html}</u>`
    return html
  }
  const inline = async (el: Element): Promise<string> => {
    let out = ''
    for (const n of el.childNodes) {
      if (n.nodeType === Node.TEXT_NODE) { out += escape(n.textContent ?? ''); continue }
      if (!(n instanceof Element)) continue
      if (n.localName === 's') out += ' '.repeat(Number(attr(n, 'c') ?? 1))
      else if (n.localName === 'tab') out += ' '
      else if (n.localName === 'line-break') out += '<br/>'
      else if (n.localName === 'span') out += styled(n, await inline(n))
      else if (n.localName === 'a') out += await inline(n)
      else if (n.localName === 'frame') {
        const image = kid(n, 'image')
        const href = attr(image, 'href')
        const src = href ? await zip.dataUrl(href) : null
        if (src) out += `<img src="${src}" alt=""/>`
      } else if (n.localName !== 'note' && n.localName !== 'annotation') out += await inline(n)
    }
    return out
  }
  const block = async (el: Element): Promise<string> => {
    switch (el.localName) {
      case 'h': {
        const level = Math.min(6, Number(attr(el, 'outline-level') ?? 1))
        const text = await inline(el)
        return text.trim() ? `<h${level}>${text}</h${level}>` : ''
      }
      case 'p': { const text = styled(el, await inline(el)); return text.trim() ? `<p>${text}</p>` : '' }
      case 'list': return `<ul>${(await Promise.all(kids(el, 'list-item').map(async li => `<li>${(await Promise.all(kids(li).map(block))).join('')}</li>`))).join('')}</ul>`
      case 'table': return `<table>${(await Promise.all([...el.getElementsByTagName('*')].filter(e => e.localName === 'table-row').map(async tr =>
        `<tr>${(await Promise.all(kids(tr, 'table-cell').map(async td => `<td>${(await Promise.all(kids(td).map(block))).join('')}</td>`))).join('')}</tr>`))).join('')}</table>`
      case 'section': case 'soft-page-break': return (await Promise.all(kids(el).map(block))).join('')
      default: return ''
    }
  }
  const text = [...doc.getElementsByTagName('*')].find(e => e.localName === 'text' && e.parentElement?.localName === 'body')
  const html = text ? (await Promise.all(kids(text).map(block))).join('') : ''
  const meta = await zip.text('meta.xml')
  const metaDoc = meta ? xml(meta) : null
  const names = namesFrom(file.name)
  const title = metaText(metaDoc, 'title') || names.title
  const author = metaText(metaDoc, 'creator') || metaText(metaDoc, 'initial-creator') || names.author
  return htmlBook(`<html><head><title>${escape(title)}</title><meta name="author" content="${escape(author)}"/></head><body>${html}</body></html>`,
    names, { language: metaText(metaDoc, 'language') || undefined })
}
