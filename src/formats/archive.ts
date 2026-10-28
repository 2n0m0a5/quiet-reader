// Archives other than zip (RAR, 7-Zip, TAR) through libarchive, compiled to WebAssembly.
// It runs in a worker that's loaded the first time it's needed (about 1 MB), then kept offline.
// The result looks like foliate-js's own zip loader, so comics and books inside open the same way.
import { Archive } from 'libarchive.js'

let ready = false

export interface Loader {
  entries: { filename: string }[]
  loadBlob(name: string): Promise<Blob>
  loadText(name: string): Promise<string>
  getSize(name: string): number
}

export async function archiveLoader(file: Blob): Promise<Loader> {
  if (!ready) {
    Archive.init({ workerUrl: new URL('vendor/libarchive/worker-bundle.js', document.baseURI).href })
    ready = true
  }
  const archive = await Archive.open(file as File)
  if (await archive.hasEncryptedData()) throw new Error('This archive is protected with a password.')
  const list = await archive.getFilesArray() as { file: { name: string; size: number; extract(): Promise<File> }; path: string }[]
  const byName = new Map(list.map(({ file, path }) => [`${path}${file.name}`, file]))
  const extracted = new Map<string, Promise<File>>()
  const loadBlob = (name: string) => {
    const entry = byName.get(name)
    if (!entry) return Promise.reject(new Error(`Not in the archive: ${name}`))
    if (!extracted.has(name)) extracted.set(name, entry.extract())
    return extracted.get(name)!
  }
  return {
    entries: [...byName.keys()].map(filename => ({ filename })),
    loadBlob,
    loadText: async name => (await loadBlob(name)).text(),
    getSize: name => byName.get(name)?.size ?? 0,
  }
}
