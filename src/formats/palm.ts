// Palm documents (.pdb, and .prc files that aren't MOBI): the old PalmDOC e-book format,
// still common in collections of public-domain books. The text is stored in records,
// either plain or with PalmDOC's own simple compression. MOBI files, which share the same
// container, are handled by foliate-js instead.
import type { Book } from '../reader/foliate'
import { namesFrom, textBook } from './text-book'

// PalmDOC (LZ77-style) decompression.
function decompress(data: Uint8Array) {
  const out: number[] = []
  for (let i = 0; i < data.length;) {
    const c = data[i++]
    if (c === 0 || (c >= 0x09 && c <= 0x7f)) out.push(c)
    else if (c >= 0x01 && c <= 0x08) for (let n = 0; n < c && i < data.length; n++) out.push(data[i++])
    else if (c >= 0xc0) out.push(0x20, c ^ 0x80)
    else {
      const pair = (c << 8) | data[i++]
      const distance = (pair >> 3) & 0x7ff
      const length = (pair & 7) + 3
      for (let n = 0; n < length; n++) out.push(out[out.length - distance])
    }
  }
  return out
}

export async function isPalmDoc(file: Blob) {
  const head = new Uint8Array(await file.slice(60, 68).arrayBuffer())
  return new TextDecoder('latin1').decode(head) === 'TEXtREAd'
}

export async function makePalmBook(file: File): Promise<Book> {
  const bytes = new Uint8Array(await file.arrayBuffer())
  const view = new DataView(bytes.buffer)
  const name = new TextDecoder('latin1').decode(bytes.subarray(0, 32)).replace(/\0.*$/s, '').trim()
  const count = view.getUint16(76)
  const offsets = Array.from({ length: count }, (_, i) => view.getUint32(78 + i * 8))
  const record = (i: number) => bytes.subarray(offsets[i], i + 1 < count ? offsets[i + 1] : bytes.length)
  const header = new DataView(record(0).slice().buffer)
  const compression = header.getUint16(0)
  const textRecords = header.getUint16(8)
  const out: number[] = []
  for (let i = 1; i <= textRecords && i < count; i++) {
    const data = record(i)
    if (compression === 2) out.push(...decompress(data))
    else for (const b of data) out.push(b)
  }
  const raw = new Uint8Array(out)
  let text: string
  try { text = new TextDecoder('utf-8', { fatal: true }).decode(raw) } catch {
    try { text = new TextDecoder('windows-1251', { fatal: true }).decode(raw) } catch { text = new TextDecoder('windows-1252').decode(raw) }
  }
  const names = namesFrom(file.name)
  return textBook(text, { title: name || names.title, author: names.author })
}
