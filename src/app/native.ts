// Things that only exist when Quiet runs as an installed Android (later iOS) app.
// On the website every function here does nothing.
import { Capacitor, registerPlugin, SystemBars, SystemBarsStyle, type PluginListenerHandle } from '@capacitor/core'
import { App } from '@capacitor/app'
import type { Orientation } from '../settings'

export const isNativeApp = Capacitor.isNativePlatform()

const quietly = (promise: Promise<unknown>) => { promise.catch(() => {}) }

// Android's back button. `handle` returns true if it did something (closed a panel,
// left a book); otherwise Quiet goes to the background, like other apps do.
export function onBackButton(handle: () => boolean) {
  if (!isNativeApp) return
  quietly(App.addListener('backButton', () => {
    if (!handle()) quietly(App.minimizeApp())
  }))
}

// Status bar and navigation bar icons: light on dark themes, dark otherwise.
export function setSystemBarsTheme(dark: boolean) {
  if (!isNativeApp) return
  quietly(SystemBars.setStyle({ style: dark ? SystemBarsStyle.Dark : SystemBarsStyle.Light }))
}

// Hide the status and navigation bars while reading with the controls hidden.
export function setSystemBarsHidden(hidden: boolean) {
  if (!isNativeApp) return
  quietly(hidden ? SystemBars.hide() : SystemBars.show())
}

// ---------- Quiet's own Android plugin (android/app/src/main/java/app/quietreader/QuietNativePlugin.java) ----------

interface QuietNativePlugin {
  addListener(event: 'incomingFile', fn: (data: { path?: string; name: string; type?: string; error?: string }) => void): Promise<PluginListenerHandle>
  addListener(event: 'volumeKey', fn: (data: { direction: 'next' | 'prev' }) => void): Promise<PluginListenerHandle>
  release(options: { path: string }): Promise<void>
  shareImage(options: { data: string; name: string; text?: string }): Promise<void>
  saveImage(options: { data: string; name: string }): Promise<void>
  setVolumeKeys(options: { enabled: boolean }): Promise<void>
  keepAwake(options: { enabled: boolean }): Promise<void>
  setOrientation(options: { value: Orientation }): Promise<void>
  haptic(options: { kind: 'tick' | 'confirm' }): Promise<void>
  battery(): Promise<{ level: number; charging: boolean }>
  pickFolder(): Promise<{ uri?: string; name?: string; cancelled?: boolean }>
  scanFolder(options: { uri: string }): Promise<{ name: string; files: FolderFile[] }>
  copyFile(options: { uri: string; name: string }): Promise<{ path: string; name: string; type: string }>
  forgetFolder(options: { uri: string }): Promise<void>
  safetyWrite(options: { name: string; data: string; append: boolean }): Promise<void>
  safetyCommit(options: { name: string }): Promise<void>
  safetyInfo(options: { name: string }): Promise<{ exists: boolean; path: string; size: number }>
  safetyList(options: { dir: string }): Promise<{ names: string[] }>
  safetyDelete(options: { name: string }): Promise<void>
  createDocument(options: { name: string; mime: string }): Promise<{ uri?: string; cancelled?: boolean }>
  writeDocument(options: { uri: string; data: string; append: boolean }): Promise<void>
}

export interface FolderFile { uri: string; name: string; size: number; modified: number }

const QuietNative = registerPlugin<QuietNativePlugin>('QuietNative')

// "Open with Quiet" / "Share to Quiet": a book file handed over by another app.
export function onIncomingFile(handle: (file: File) => Promise<void>, failed: (name: string) => void) {
  if (!isNativeApp) return
  quietly(QuietNative.addListener('incomingFile', async ({ path, name, type, error }) => {
    if (error || !path) return failed(name)
    try {
      const response = await fetch(Capacitor.convertFileSrc(`file://${path}`))
      const blob = await response.blob()
      await handle(new File([blob], name, { type: type || blob.type }))
    } catch {
      failed(name)
    } finally {
      quietly(QuietNative.release({ path }))
    }
  }))
}

// Volume keys turn pages while a book is open (only when switched on in settings).
let volumeHandler: ((direction: 'next' | 'prev') => void) | null = null
if (isNativeApp) quietly(QuietNative.addListener('volumeKey', ({ direction }) => volumeHandler?.(direction)))

export function setVolumeKeys(handler: ((direction: 'next' | 'prev') => void) | null) {
  volumeHandler = handler
  if (isNativeApp) quietly(QuietNative.setVolumeKeys({ enabled: !!handler }))
}

// Keep the screen on while reading. Uses the plugin in the app, the Wake Lock API on the website.
let wakeLock: { release(): Promise<void> } | null = null
export function keepScreenOn(enabled: boolean) {
  if (isNativeApp) return quietly(QuietNative.keepAwake({ enabled }))
  const nav = navigator as Navigator & { wakeLock?: { request(type: 'screen'): Promise<{ release(): Promise<void> }> } }
  if (enabled && !wakeLock && nav.wakeLock) {
    nav.wakeLock.request('screen').then(lock => { wakeLock = lock }).catch(() => {})
  } else if (!enabled && wakeLock) {
    quietly(wakeLock.release())
    wakeLock = null
  }
}

// Screen rotation: follow the phone, or stay in portrait or landscape (Android app).
export function setOrientation(value: Orientation) {
  if (isNativeApp) quietly(QuietNative.setOrientation({ value }))
}

// A small vibration to confirm an action. 'tick' for small things, 'confirm' for saving something.
let hapticsOn = true
export function setHaptics(enabled: boolean) { hapticsOn = enabled }
export function haptic(kind: 'tick' | 'confirm' = 'tick') {
  if (isNativeApp && hapticsOn) quietly(QuietNative.haptic({ kind }))
}

// Battery charge from 0 to 1, or null when it can't be read (most desktop browsers).
type BatteryManager = { level: number }
export async function batteryLevel(): Promise<number | null> {
  try {
    if (isNativeApp) return (await QuietNative.battery()).level
    const nav = navigator as Navigator & { getBattery?: () => Promise<BatteryManager> }
    return nav.getBattery ? (await nav.getBattery()).level : null
  } catch {
    return null
  }
}

// ---------- Book folders (Android app) ----------

// Android's folder picker. Null if cancelled.
export async function pickFolder(): Promise<{ uri: string; name: string } | null> {
  const result = await QuietNative.pickFolder()
  return result.uri ? { uri: result.uri, name: result.name ?? 'Folder' } : null
}

export const scanFolder = (uri: string) => QuietNative.scanFolder({ uri })

// One book from a folder, as a File the library can add.
export async function readFolderFile(file: FolderFile): Promise<File> {
  const { path, name, type } = await QuietNative.copyFile({ uri: file.uri, name: file.name })
  try {
    const blob = await (await fetch(Capacitor.convertFileSrc(`file://${path}`))).blob()
    return new File([blob], name, { type: type || blob.type })
  } finally {
    quietly(QuietNative.release({ path }))
  }
}

export const forgetFolder = (uri: string) => quietly(QuietNative.forgetFolder({ uri }))

// Opens a web page outside Quiet: the system browser in the app, a new tab on the website.
// (In the app, Capacitor hands any navigation away from the app to the system browser.)
export function openExternal(url: string) {
  if (isNativeApp) window.location.href = url
  else window.open(url, '_blank', 'noopener')
}

// ---------- Safety copy: files in Quiet's private app storage (library/safety.ts) ----------
// In the app these are files in Android's private app storage. Browsers have no such place, so the
// website doesn't keep a safety copy; the tests turn one on (localStorage "quiet:test-safety") and
// it then uses the browser's private file system, to run the same code.

export const safetyCopyAvailable = isNativeApp || (() => { try { return !!localStorage.getItem('quiet:test-safety') } catch { return false } })()

const CHUNK = 3 * 1024 * 1024 // bytes per bridge call; base64 makes each call about 4 MB

async function opfsFile(name: string, create: boolean): Promise<FileSystemFileHandle | null> {
  let dir = await (await navigator.storage.getDirectory()).getDirectoryHandle('safety', { create: true })
  const parts = name.split('/')
  try {
    for (const part of parts.slice(0, -1)) dir = await dir.getDirectoryHandle(part, { create })
    return await dir.getFileHandle(parts[parts.length - 1], { create })
  } catch {
    return null
  }
}

// Writes a whole blob, in chunks, then swaps it in, so a half-written file never replaces a good one.
export async function safetyWrite(name: string, blob: Blob) {
  if (!isNativeApp) {
    const writable = await (await opfsFile(name, true))!.createWritable()
    await writable.write(blob)
    await writable.close()
    return
  }
  if (!blob.size) await QuietNative.safetyWrite({ name, data: '', append: false })
  for (let start = 0; start < blob.size; start += CHUNK)
    await QuietNative.safetyWrite({ name, data: await base64(blob.slice(start, start + CHUNK)), append: start > 0 })
  await QuietNative.safetyCommit({ name })
}

export async function safetyRead(name: string): Promise<Blob | null> {
  if (!isNativeApp) {
    const handle = await opfsFile(name, false)
    return handle ? handle.getFile() : null
  }
  const info = await QuietNative.safetyInfo({ name })
  if (!info.exists) return null
  return (await fetch(Capacitor.convertFileSrc(`file://${info.path}`))).blob()
}

export async function safetyList(dir: string): Promise<string[]> {
  if (!isNativeApp) {
    try {
      const root = await (await navigator.storage.getDirectory()).getDirectoryHandle('safety', { create: true })
      const handle = await root.getDirectoryHandle(dir, { create: true })
      const names: string[] = []
      for await (const key of (handle as unknown as { keys(): AsyncIterable<string> }).keys()) names.push(key)
      return names
    } catch {
      return []
    }
  }
  return (await QuietNative.safetyList({ dir })).names
}

export async function safetyDelete(name: string) {
  if (!isNativeApp) {
    const parts = name.split('/')
    try {
      let dir = await (await navigator.storage.getDirectory()).getDirectoryHandle('safety')
      for (const part of parts.slice(0, -1)) dir = await dir.getDirectoryHandle(part)
      await dir.removeEntry(parts[parts.length - 1])
    } catch { /* already gone */ }
    return
  }
  await QuietNative.safetyDelete({ name })
}

// A picture (a quote card): Android's share sheet, or the browser's (or a download) on the website.
async function base64(blob: Blob) {
  const url = await new Promise<string>(resolve => { const r = new FileReader(); r.onload = () => resolve(r.result as string); r.readAsDataURL(blob) })
  return url.slice(url.indexOf(',') + 1)
}
export async function shareImage(blob: Blob, name: string, text?: string) {
  if (isNativeApp) return QuietNative.shareImage({ data: await base64(blob), name, text })
  const file = new File([blob], name, { type: 'image/png' })
  if (navigator.canShare?.({ files: [file] })) return navigator.share({ files: [file], text })
  return downloadBlob(blob, name)
}
export async function saveImage(blob: Blob, name: string) {
  if (isNativeApp) return QuietNative.saveImage({ data: await base64(blob), name })
  return downloadBlob(blob, name)
}
// A big file (a library backup) saved where the reader chooses: Android's "save as" picker, or a
// download on the website. False if the reader cancelled.
export async function saveFile(blob: Blob, name: string, mime: string, progress?: (fraction: number) => void): Promise<boolean> {
  if (!isNativeApp) { downloadBlob(blob, name); return true }
  const { uri } = await QuietNative.createDocument({ name, mime })
  if (!uri) return false
  for (let start = 0; start < blob.size || start === 0; start += CHUNK) {
    await QuietNative.writeDocument({ uri, data: await base64(blob.slice(start, start + CHUNK)), append: start > 0 })
    progress?.(Math.min(1, (start + CHUNK) / Math.max(1, blob.size)))
    if (!blob.size) break
  }
  return true
}

function downloadBlob(blob: Blob, name: string) {
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = name
  document.body.append(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(a.href), 10_000)
}
