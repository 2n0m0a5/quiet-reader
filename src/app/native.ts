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
  setVolumeKeys(options: { enabled: boolean }): Promise<void>
  keepAwake(options: { enabled: boolean }): Promise<void>
  setOrientation(options: { value: Orientation }): Promise<void>
  haptic(options: { kind: 'tick' | 'confirm' }): Promise<void>
  battery(): Promise<{ level: number; charging: boolean }>
}

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

// Opens a web page outside Quiet: the system browser in the app, a new tab on the website.
// (In the app, Capacitor hands any navigation away from the app to the system browser.)
export function openExternal(url: string) {
  if (isNativeApp) window.location.href = url
  else window.open(url, '_blank', 'noopener')
}
