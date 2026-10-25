// Things that only exist when Quiet runs as an installed Android (later iOS) app.
// On the website every function here does nothing.
import { Capacitor, SystemBars, SystemBarsStyle } from '@capacitor/core'
import { App } from '@capacitor/app'
import type { Theme } from '../settings'

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

// Status bar and navigation bar icons: light on the Night theme, dark otherwise.
export function setSystemBarsTheme(theme: Theme) {
  if (!isNativeApp) return
  quietly(SystemBars.setStyle({ style: theme === 'dark' ? SystemBarsStyle.Dark : SystemBarsStyle.Light }))
}

// Hide the status and navigation bars while reading with the controls hidden.
export function setSystemBarsHidden(hidden: boolean) {
  if (!isNativeApp) return
  quietly(hidden ? SystemBars.hide() : SystemBars.show())
}
