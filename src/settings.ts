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
  hyphenate: boolean // split words at line ends with a hyphen (off: whole words move down)
  brightness: number // percent
  tapZones: boolean       // tap the left/right side to turn pages (page mode)
  scrollPastEnd: boolean  // keep scrolling at a chapter's end to go on (scroll mode)
  keepAwake: boolean      // keep the screen on while reading
  volumeKeys: boolean     // volume keys turn pages (Android app)
  uiScale: number         // percent: size of the app's own text and controls (not the book)
}

export const defaults: Settings = {
  theme: 'light',
  flow: 'scrolled',
  font: 'serif',
  fontSize: 100,
  lineHeight: 1.7,
  margins: 'normal',
  align: 'start',
  hyphenate: false,
  brightness: 100,
  tapZones: true,
  scrollPastEnd: true,
  keepAwake: true,
  volumeKeys: true,
  uiScale: 100,
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
