// A small zip writer and reader for library backups (backup.ts).
// Writing stores files as they are (book files are compressed already); reading also takes
// deflated entries, in case a backup was re-zipped by another program. No zip64: up to 4 GB.

const table = (() => {
  const t = new Uint32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    t[n] = c >>> 0
  }
  return t
})()

async function crc32(blob: Blob) {
  let crc = 0xffffffff
  const STEP = 4 * 1024 * 1024
  for (let start = 0; start < blob.size; start += STEP) {
    const bytes = new Uint8Array(await blob.slice(start, start + STEP).arrayBuffer())
    for (let i = 0; i < bytes.length; i++) crc = table[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8)
  }
  return (crc ^ 0xffffffff) >>> 0
}

export interface ZipEntry { name: string; data: Blob }

// Builds a zip. `progress` gets 0 → 1 while the files are checked (the slow part).
export async function writeZip(entries: ZipEntry[], progress?: (fraction: number) => void): Promise<Blob> {
  const parts: BlobPart[] = []
  const central: BlobPart[] = []
  const enc = new TextEncoder()
  let offset = 0
  const total = entries.reduce((n, e) => n + e.data.size, 0) || 1
  let done = 0
  for (const { name, data } of entries) {
    const nameBytes = enc.encode(name)
    const crc = await crc32(data)
    done += data.size
    progress?.(done / total)
    const local = new DataView(new ArrayBuffer(30))
    local.setUint32(0, 0x04034b50, true)
    local.setUint16(4, 20, true)          // version needed
    local.setUint16(6, 0x0800, true)      // UTF-8 names
    local.setUint16(8, 0, true)           // stored
    local.setUint32(14, crc, true)
    local.setUint32(18, data.size, true)
    local.setUint32(22, data.size, true)
    local.setUint16(26, nameBytes.length, true)
    parts.push(local.buffer, nameBytes as Uint8Array<ArrayBuffer>, data)
    const dir = new DataView(new ArrayBuffer(46))
    dir.setUint32(0, 0x02014b50, true)
    dir.setUint16(4, 20, true)
    dir.setUint16(6, 20, true)
    dir.setUint16(8, 0x0800, true)
    dir.setUint32(16, crc, true)
    dir.setUint32(20, data.size, true)
    dir.setUint32(24, data.size, true)
    dir.setUint16(28, nameBytes.length, true)
    dir.setUint32(42, offset, true)
    central.push(dir.buffer, nameBytes as Uint8Array<ArrayBuffer>)
    offset += 30 + nameBytes.length + data.size
  }
  const size = central.reduce<number>((n, b) => n + (b as ArrayBuffer | Uint8Array).byteLength, 0)
  const end = new DataView(new ArrayBuffer(22))
  end.setUint32(0, 0x06054b50, true)
  end.setUint16(8, entries.length, true)
  end.setUint16(10, entries.length, true)
  end.setUint32(12, size, true)
  end.setUint32(16, offset, true)
  return new Blob([...parts, ...central, end.buffer], { type: 'application/zip' })
}

// Reads a zip's file list; each entry's data is read when asked for.
export async function readZip(blob: Blob): Promise<Map<string, () => Promise<Blob>>> {
  const tail = new DataView(await blob.slice(Math.max(0, blob.size - 65_557)).arrayBuffer())
  let endAt = -1
  for (let i = tail.byteLength - 22; i >= 0; i--) if (tail.getUint32(i, true) === 0x06054b50) { endAt = i; break }
  if (endAt < 0) throw new Error('Not a zip file')
  const count = tail.getUint16(endAt + 10, true)
  const dirSize = tail.getUint32(endAt + 12, true)
  const dirStart = tail.getUint32(endAt + 16, true)
  const dir = new DataView(await blob.slice(dirStart, dirStart + dirSize).arrayBuffer())
  const dec = new TextDecoder()
  const files = new Map<string, () => Promise<Blob>>()
  for (let i = 0, p = 0; i < count; i++) {
    if (dir.getUint32(p, true) !== 0x02014b50) throw new Error('Damaged zip file')
    const method = dir.getUint16(p + 10, true)
    const compressed = dir.getUint32(p + 20, true)
    const nameLength = dir.getUint16(p + 28, true)
    const extra = dir.getUint16(p + 30, true)
    const comment = dir.getUint16(p + 32, true)
    const localAt = dir.getUint32(p + 42, true)
    const name = dec.decode(new Uint8Array(dir.buffer, dir.byteOffset + p + 46, nameLength))
    p += 46 + nameLength + extra + comment
    files.set(name, async () => {
      const head = new DataView(await blob.slice(localAt, localAt + 30).arrayBuffer())
      const start = localAt + 30 + head.getUint16(26, true) + head.getUint16(28, true)
      const raw = blob.slice(start, start + compressed)
      if (method === 0) return raw
      if (method === 8) return new Response(raw.stream().pipeThrough(new DecompressionStream('deflate-raw'))).blob()
      throw new Error(`Unsupported zip method ${method}`)
    })
  }
  return files
}
