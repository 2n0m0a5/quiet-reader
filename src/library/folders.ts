// Book folders: the reader fills a folder with books and chooses it once. Quiet adds every book in
// it (sub-folders too). In the Android app it also checks the folders again when it opens, and adds
// new books by itself. A website can't watch a folder, so there the books are added once.
import { FolderOpen } from 'lucide'
import { addToLibrary } from './import'
import { button, h, icon, openDialog } from '../ui/dialog'
import { toast } from '../ui/toast'
import { forgetFolder, isNativeApp, pickFolder, readFolderFile, scanFolder, type FolderFile } from '../app/native'
import { isBookFile } from '../formats/formats'

export interface BookFolder {
  uri: string
  name: string
  seen: string[]       // files already looked at (so each one is copied in only once)
  count: number        // books found in it last time
  lastScan: number
  error?: string
}

const KEY = 'quiet:book-folders'

export function listFolders(): BookFolder[] {
  try { return JSON.parse(localStorage.getItem(KEY) ?? '[]') } catch { return [] }
}

function saveFolders(folders: BookFolder[]) {
  try { localStorage.setItem(KEY, JSON.stringify(folders)) } catch { /* storage unavailable */ }
}

function updateFolder(uri: string, patch: Partial<BookFolder>) {
  saveFolders(listFolders().map(f => f.uri === uri ? { ...f, ...patch } : f))
}

export function removeFolder(uri: string) {
  saveFolders(listFolders().filter(f => f.uri !== uri))
  forgetFolder(uri)
}

// Explains what to do first, then opens the folder picker. `changed` is called when books were added.
export function addFolder(changed: () => void) {
  const text = (s: string) => h('p', 'dialog-text', s)
  const badge = h('div', 'empty-icon folder-badge')
  badge.append(icon(FolderOpen))
  const actions = h('div', 'dialog-actions')
  const d = openDialog({
    title: 'Add a book folder', className: 'dialog-small dialog-folder',
    content: [
      badge,
      text('First, put your books in one folder on your phone, for example a “Books” folder in Downloads. Folders inside it are fine.'),
      text(isNativeApp
        ? 'Choose that folder, and Quiet adds every book in it. Each time you open Quiet it checks the folder again, so new books you put there show up by themselves.'
        : 'Choose that folder, and Quiet adds every book in it. (The Android app also checks the folder for new books each time it opens; a website can’t.)'),
      h('p', 'settings-hint', 'Other kinds of files are ignored. Removing a book from Quiet doesn’t delete its file.'),
      actions,
    ],
  })
  actions.append(
    button('Cancel', '', () => d.close()),
    button('Choose folder', 'primary', () => { d.close(); isNativeApp ? chooseNative(changed) : chooseInBrowser(changed) }),
  )
}

async function chooseNative(changed: () => void) {
  let picked
  try { picked = await pickFolder() } catch { return toast('Couldn’t open the folder picker.') }
  if (!picked) return
  const folders = listFolders()
  if (folders.some(f => f.uri === picked.uri)) {
    toast(`“${picked.name}” is already one of your book folders. Checking it now.`)
  } else {
    saveFolders([...folders, { uri: picked.uri, name: picked.name, seen: [], count: 0, lastScan: 0 }])
  }
  await scanFolders(changed, { only: picked.uri, announce: true })
}

// Browsers: a folder picker that hands over every file in the folder, once.
function chooseInBrowser(changed: () => void) {
  const input = h('input')
  input.type = 'file'
  input.multiple = true
  input.setAttribute('webkitdirectory', '')
  input.addEventListener('change', async () => {
    const files = Array.from(input.files ?? []).filter(f => isBookFile(f.name))
    const name = (input.files?.[0] as (File & { webkitRelativePath?: string }) | undefined)?.webkitRelativePath?.split('/')[0] || 'the folder'
    if (!files.length) return toast(`No books in “${name}”.`, 5000)
    const { added, failed } = await addFiles(files.map(f => async () => f), name, changed, true)
    report(name, added, failed, files.length)
  })
  input.click()
}

let scanning = false

// Checks the book folders (or one of them) and adds the books that are new there.
export async function scanFolders(changed: () => void, { only, announce = false }: { only?: string; announce?: boolean } = {}) {
  if (!isNativeApp || scanning) return
  scanning = true
  try {
    for (const folder of listFolders().filter(f => !only || f.uri === only)) {
      let files: FolderFile[]
      try {
        const result = await scanFolder(folder.uri)
        files = result.files
        updateFolder(folder.uri, { name: result.name || folder.name, count: files.length, lastScan: Date.now(), error: undefined })
      } catch {
        updateFolder(folder.uri, { lastScan: Date.now(), error: 'Quiet can’t open this folder any more. It may have been moved or deleted. Remove it and add it again.' })
        if (announce) toast(`Couldn’t open “${folder.name}”.`, 5000)
        continue
      }
      const key = (f: FolderFile) => `${f.uri}|${f.size}|${f.modified}`
      const seen = new Set(folder.seen)
      const fresh = files.filter(f => !seen.has(key(f)))
      if (!fresh.length) {
        if (announce) toast(files.length ? `No new books in “${folder.name}”.` : `No books in “${folder.name}” yet.`, 4000)
        continue
      }
      const { added, failed } = await addFiles(fresh.map(f => async () => {
        const file = await readFolderFile(f)
        seen.add(key(f))
        updateFolder(folder.uri, { seen: [...seen] })
        return file
      }), folder.name, changed, false)
      // Files that couldn't even be copied are tried again next time.
      if (added || failed || announce) report(folder.name, added, failed, fresh.length)
    }
  } finally {
    scanning = false
  }
}

// Adds books one by one, showing how far along it is. Books removed from Quiet stay removed
// when they're found in a folder again (unless the reader chose the files themselves).
async function addFiles(files: (() => Promise<File>)[], folder: string, changed: () => void, restoreRemoved: boolean) {
  let added = 0, failed = 0
  for (const [i, read] of files.entries()) {
    if (files.length > 2) toast(`Adding books from “${folder}”… ${i + 1} of ${files.length}`, 60_000)
    try {
      const { result } = await addToLibrary(await read(), { restoreRemoved })
      if (result === 'added') added++
    } catch (err) {
      console.warn('Could not add a book from the folder:', err)
      failed++
    }
    if (added && added % 10 === 0) changed()
  }
  changed()
  return { added, failed }
}

function report(folder: string, added: number, failed: number, total: number) {
  const books = (n: number) => `${n} ${n === 1 ? 'book' : 'books'}`
  if (!added && !failed) return toast(total ? `Every book in “${folder}” is already in your library.` : `No books in “${folder}”.`, 5000)
  const message = added ? `Added ${books(added)} from “${folder}”.` : `No new books from “${folder}”.`
  toast(failed ? `${message} ${books(failed)} couldn’t be read.` : message, 6000)
}
