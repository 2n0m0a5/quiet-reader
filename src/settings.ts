// Reading preferences, remembered between visits.

export type Theme = 'light' | 'sepia' | 'dark'
export type Flow = 'scrolled' | 'paginated'
export type FontChoice = 'original' | 'serif' | 'sans'
export type Margins = 'narrow' | 'normal' | 'wide'
export type Align = 'start' | 'justify'

export interface Settings {
  theme: Theme
  flow: Flow
  font: FontChoice   // 'original' keeps the book's own fonts
  fontSize: number   // percent
  lineHeight: number
  margins: Margins
  align: Align
  brightness: number // percent
}

export const defaults: Settings = {
  theme: 'light',
  flow: 'scrolled',
  font: 'serif',
  fontSize: 100,
  lineHeight: 1.7,
  margins: 'normal',
  align: 'start',
  brightness: 100,
}

export const limits = {
  fontSize: { min: 80, max: 180, step: 5 },
  lineHeight: { min: 1.2, max: 2.2, step: 0.1 },
  brightness: { min: 50, max: 100, step: 5 },
}

export const clamp = (value: number, { min, max }: { min: number; max: number }) =>
  Math.min(max, Math.max(min, value))

const KEY = 'quiet:settings'

export function loadSettings(): Settings {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? '{}')
    return { ...defaults, ...saved }
  } catch {
    return { ...defaults }
  }
}

export function saveSettings(settings: Settings) {
  try {
    localStorage.setItem(KEY, JSON.stringify(settings))
  } catch {
    // Storage can be unavailable (private mode). Settings then last for this visit only.
  }
}
