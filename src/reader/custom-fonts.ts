// Fonts you add yourself (TTF, OTF, WOFF, WOFF2). They're kept on this device with the books and
// offered next to Serif and Sans in the reading settings. A family's regular, bold and italic files
// are grouped into one choice. The font's own name, weight and style are read from inside the file
// (its "name" and "OS/2" tables) when possible, and guessed from the file name otherwise.
import { deleteFontRecords, listFontRecords, saveFontRecord, type FontRecord } from '../library/db'

export const FONT_ACCEPT = '.ttf,.otf,.woff,.woff2,font/ttf,font/otf,font/woff,font/woff2'

interface Loaded { records: FontRecord[]; urls: Map<string, string>; css: string }
let loaded: Loaded | null = null
const listeners = new Set<() => void>()

export const onFontsChange = (fn: () => void) => { listeners.add(fn) }

// The families you've added, in order.
export async function customFamilies(): Promise<string[]> {
  const { records } = await load()
  return [...new Set(records.sort((a, b) => a.addedAt - b.addedAt).map(r => r.family))]
}

export async function fontRecords() { return (await load()).records }

// The same as customFamilies(), from what's already loaded (for screens drawn straight away).
export function loadedFamilies(): string[] {
  return loaded ? [...new Set([...loaded.records].sort((a, b) => a.addedAt - b.addedAt).map(r => r.family))] : []
}
export function loadedRecords(): FontRecord[] { return loaded?.records ?? [] }

// @font-face rules for every added font, for the book's frame and for previews in the app.
export const customFontCSS = () => loaded?.css ?? ''

export async function load(): Promise<Loaded> {
  if (loaded) return loaded
  const records = await listFontRecords()
  const urls = new Map(records.map(r => [r.id, URL.createObjectURL(r.data)]))
  const css = records.map(r => `@font-face { font-family: '${cssName(r.family)}'; src: url(${urls.get(r.id)}); font-weight: ${r.weight}; font-style: ${r.style}; font-display: swap; }`).join('\n')
  loaded = { records, urls, css }
  let style = document.getElementById('custom-fonts')
  if (!style) { style = document.createElement('style'); style.id = 'custom-fonts'; document.head.append(style) }
  style.textContent = css
  return loaded
}

function reload() {
  loaded?.urls.forEach(url => URL.revokeObjectURL(url))
  loaded = null
  return load().then(() => listeners.forEach(fn => fn()))
}

const cssName = (family: string) => family.replace(/['\\]/g, '')
export const familyStack = (family: string) => `'${cssName(family)}', 'Literata Variable', Georgia, serif`

// Adds font files. Returns how many were added, and the names of files that weren't fonts.
export async function addFonts(files: File[]) {
  const failed: string[] = []
  let added = 0
  for (const file of files) {
    try {
      const data = new Uint8Array(await file.arrayBuffer())
      const info = describe(data, file.name)
      // Check that the browser can really use it before keeping it.
      const face = new FontFace(`check-${Date.now()}`, data.buffer.slice(0) as ArrayBuffer)
      await face.load()
      await saveFontRecord({
        id: `${info.family}|${info.weight}|${info.style}`.toLowerCase(),
        ...info, fileName: file.name, data: new Blob([data], { type: file.type || 'font/ttf' }), addedAt: Date.now(),
      })
      added++
    } catch {
      failed.push(file.name)
    }
  }
  await reload()
  return { added, failed }
}

export async function removeFamily(family: string) {
  const { records } = await load()
  await deleteFontRecords(records.filter(r => r.family === family).map(r => r.id))
  await reload()
}

// ---------- Reading a font's own name ----------

function describe(data: Uint8Array, fileName: string): { family: string; weight: number; style: 'normal' | 'italic' } {
  const guess = fromFileName(fileName)
  try {
    const tables = sfntTables(data)
    if (!tables) return guess
    const family = readName(data, tables.get('name'), [16, 1]) || guess.family
    const subfamily = readName(data, tables.get('name'), [17, 2]) || ''
    const os2 = tables.get('OS/2')
    const view = new DataView(data.buffer, data.byteOffset)
    const weight = os2 ? view.getUint16(os2 + 4) : guess.weight
    const italic = os2 ? (view.getUint16(os2 + 62) & 1) === 1 : guess.style === 'italic'
    return { family, weight: weight || 400, style: italic || /italic|oblique/i.test(subfamily) ? 'italic' : 'normal' }
  } catch {
    return guess
  }
}

// TrueType and OpenType files (not WOFF, which is compressed): table name → offset.
function sfntTables(data: Uint8Array) {
  const view = new DataView(data.buffer, data.byteOffset)
  const tag = view.getUint32(0)
  if (tag !== 0x00010000 && tag !== 0x4f54544f && tag !== 0x74727565) return null
  const count = view.getUint16(4)
  const tables = new Map<string, number>()
  for (let i = 0; i < count; i++) {
    const at = 12 + i * 16
    tables.set(String.fromCharCode(...data.subarray(at, at + 4)), view.getUint32(at + 8))
  }
  return tables
}

// The first of the given name IDs found, preferring Windows English, then any Unicode name.
function readName(data: Uint8Array, offset: number | undefined, ids: number[]) {
  if (offset == null) return ''
  const view = new DataView(data.buffer, data.byteOffset)
  const count = view.getUint16(offset + 2)
  const strings = offset + view.getUint16(offset + 4)
  const found: { id: number; english: boolean; value: string }[] = []
  for (let i = 0; i < count; i++) {
    const r = offset + 6 + i * 12
    const platform = view.getUint16(r), language = view.getUint16(r + 4), id = view.getUint16(r + 6)
    const length = view.getUint16(r + 8), start = strings + view.getUint16(r + 10)
    if (!ids.includes(id)) continue
    const bytes = data.subarray(start, start + length)
    let value = ''
    if (platform === 3 || platform === 0) for (let j = 0; j + 1 < bytes.length; j += 2) value += String.fromCharCode((bytes[j] << 8) | bytes[j + 1])
    else value = String.fromCharCode(...bytes)
    found.push({ id, english: platform === 3 && language === 0x409, value: value.trim() })
  }
  for (const id of ids) {
    const best = found.find(f => f.id === id && f.english) ?? found.find(f => f.id === id && f.value)
    if (best?.value) return best.value
  }
  return ''
}

// "Bookerly-BoldItalic.ttf" → Bookerly, 700, italic
function fromFileName(name: string) {
  const base = name.replace(/\.[^.]+$/, '')
  const style = /italic|oblique/i.test(base) ? 'italic' as const : 'normal' as const
  const weight = /black|heavy/i.test(base) ? 900 : /extra-?bold/i.test(base) ? 800 : /semi-?bold|demi/i.test(base) ? 600
    : /bold/i.test(base) ? 700 : /medium/i.test(base) ? 500 : /light/i.test(base) ? 300 : /thin/i.test(base) ? 100 : 400
  const family = base.replace(/[-_ ]?(regular|italic|oblique|bold|semi-?bold|extra-?bold|medium|light|thin|black|heavy|demi)+$/gi, '')
    .replace(/[-_]+/g, ' ').replace(/([a-z])([A-Z])/g, '$1 $2').trim() || base
  return { family, weight, style }
}
