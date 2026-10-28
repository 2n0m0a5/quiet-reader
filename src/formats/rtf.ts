// RTF files as books. A small reader for the parts that carry text: paragraphs, bold, italic,
// underline, and characters in any code page (\'hh with \ansicpg, e.g. 1251 for Russian) or as
// Unicode (\uN). Fonts, colours, pictures and layout are skipped. The title and author come
// from the {\info} group when there is one.
import type { Book } from '../reader/foliate'
import { escape, htmlBook, namesFrom } from './text-book'

// Groups that hold no readable text.
const SKIP = new Set(['fonttbl', 'colortbl', 'stylesheet', 'listtable', 'listoverridetable', 'pict', 'object', 'header', 'footer',
  'headerl', 'headerr', 'footerl', 'footerr', 'footnote', 'xmlnstbl', 'rsidtbl', 'generator', 'themedata', 'colorschememapping',
  'datastore', 'latentstyles', 'pgdsctbl', 'revtbl', 'filetbl', 'mmathPr', 'pnseclvl', 'fldinst'])

export async function makeRtfBook(file: File): Promise<Book> {
  const bytes = new Uint8Array(await file.arrayBuffer())
  // RTF itself is ASCII; anything else is escaped, so read it byte for byte.
  let src = ''
  for (let i = 0; i < bytes.length; i += 0x8000) src += String.fromCharCode(...bytes.subarray(i, i + 0x8000))
  if (!src.startsWith('{\\rtf')) throw new Error('This isn’t an RTF file.')

  let decoder = new TextDecoder('windows-1252')
  const paragraphs: string[] = []
  const info: Record<string, string> = {}
  let line = ''
  let pendingBytes: number[] = []
  type State = { b: boolean; i: boolean; u: boolean; skip: boolean; uc: number; info: string | null }
  let state: State = { b: false, i: false, u: false, skip: false, uc: 1, info: null }
  const stack: State[] = []
  let skipChars = 0
  let open = { b: false, i: false, u: false }

  const flushBytes = () => {
    if (!pendingBytes.length) return
    emit(decoder.decode(new Uint8Array(pendingBytes)))
    pendingBytes = []
  }
  const style = () => {
    // Close and reopen tags when the formatting changes.
    let out = ''
    if (open.u && !state.u) { out += '</u>'; open.u = false }
    if (open.i && !state.i) { out += '</i>'; open.i = false }
    if (open.b && !state.b) { out += '</b>'; open.b = false }
    if (!open.b && state.b) { out += '<b>'; open.b = true }
    if (!open.i && state.i) { out += '<i>'; open.i = true }
    if (!open.u && state.u) { out += '<u>'; open.u = true }
    return out
  }
  const emit = (text: string) => {
    if (state.skip || !text) return
    if (state.info) { info[state.info] = (info[state.info] ?? '') + text; return }
    line += style() + escape(text)
  }
  const endParagraph = () => {
    flushBytes()
    const closing = (open.u ? '</u>' : '') + (open.i ? '</i>' : '') + (open.b ? '</b>' : '')
    open = { b: false, i: false, u: false }
    const p = (line + closing).trim()
    if (p.replace(/<[^>]+>/g, '').trim()) paragraphs.push(p)
    line = ''
  }

  for (let i = 0; i < src.length; i++) {
    const c = src[i]
    if (c === '{') { flushBytes(); stack.push({ ...state }); continue }
    if (c === '}') { flushBytes(); state = stack.pop() ?? state; continue }
    if (c === '\r' || c === '\n') continue
    if (c !== '\\') {
      if (skipChars) { skipChars--; continue }
      flushBytes()
      emit(c)
      continue
    }
    // A control word or symbol.
    const next = src[i + 1]
    if (next === "'") {
      const hex = src.substr(i + 2, 2)
      i += 3
      if (skipChars) { skipChars--; continue }
      if (!state.skip) pendingBytes.push(parseInt(hex, 16))
      continue
    }
    if (next === '*') { state.skip = true; i++; continue }
    if (!/[a-z]/i.test(next ?? '')) {
      flushBytes()
      i++
      if (next === '~') emit(' ')
      else if (next === '-' || next === '_') emit(next === '_' ? '‑' : '')
      else if (next === '\\' || next === '{' || next === '}') emit(next)
      else if (next === '\n' || next === '\r') endParagraph()
      continue
    }
    const m = /^([a-z]+)(-?\d+)? ?/i.exec(src.slice(i + 1, i + 40))!
    i += m[0].length
    const word = m[1]
    const arg = m[2] != null ? Number(m[2]) : null
    flushBytes()
    if (SKIP.has(word)) { state.skip = true; continue }
    switch (word) {
      case 'ansicpg': try { decoder = new TextDecoder(`windows-${arg}`) } catch { /* keep the default */ } break
      case 'par': case 'sect': case 'page': endParagraph(); break
      case 'line': line += '<br/>'; break
      case 'tab': emit(' '); break
      case 'emdash': emit('—'); break
      case 'endash': emit('–'); break
      case 'lquote': emit('‘'); break
      case 'rquote': emit('’'); break
      case 'ldblquote': emit('“'); break
      case 'rdblquote': emit('”'); break
      case 'bullet': emit('•'); break
      case 'b': state.b = arg !== 0; break
      case 'i': state.i = arg !== 0; break
      case 'ul': state.u = arg !== 0; break
      case 'ulnone': state.u = false; break
      case 'plain': state.b = state.i = state.u = false; break
      case 'uc': state.uc = arg ?? 1; break
      case 'u': emit(String.fromCharCode(arg! < 0 ? arg! + 65536 : arg!)); skipChars = state.uc; break
      case 'info': state.info = null; break
      case 'title': case 'author': case 'subject': state.info = word; break
      case 'cell': emit(' '); break
      case 'row': endParagraph(); break
    }
  }
  endParagraph()

  const names = namesFrom(file.name)
  const title = info.title?.trim() || names.title
  const author = info.author?.trim() || names.author
  // Short paragraphs that look like chapter headings become headings.
  const HEADING = /^((chapter|part|book|prologue|epilogue|глава|часть|книга|пролог|эпилог)(?=[\s.:]|$).{0,80}|[IVXLC]{1,7}\.?|\d{1,3}\.?)$/iu
  const body = paragraphs.map(p => {
    const plain = p.replace(/<[^>]+>/g, '').trim()
    return plain.length < 90 && HEADING.test(plain) ? `<h2>${p}</h2>` : `<p>${p}</p>`
  }).join('')
  return htmlBook(`<html><head><title>${escape(title)}</title><meta name="author" content="${escape(author)}"/></head><body>${body}</body></html>`, names)
}
