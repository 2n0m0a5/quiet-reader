// Reading preferences, remembered between visits.

// The design style: how the whole app looks (fonts, colours, shapes, controls).
export type DesignStyle = 'star' | 'paper' | 'tashkent'
export const DESIGN_STYLES: DesignStyle[] = ['star', 'paper', 'tashkent']

export type Theme = 'light' | 'sepia' | 'dark' | 'black' | 'custom' | 'system'
export type Flow = 'scrolled' | 'paginated'
export type FontChoice = 'original' | 'serif' | 'sans'
export type Margins = 'narrow' | 'normal' | 'wide'
export type Align = 'start' | 'justify'
export type Ruler = 'off' | 'line' | 'focus'
export type Orientation = 'auto' | 'portrait' | 'landscape'

export interface Settings {
  style: DesignStyle
  theme: Theme
  customBg: string        // colours for the Custom theme
  customText: string
  flow: Flow
  font: FontChoice   // 'original' keeps the book's own fonts
  fontSize: number   // percent
  lineHeight: number
  margins: Margins
  align: Align
  hyphenate: boolean // split words at line ends with a hyphen (off: whole words move down)
  fontWeight: number      // 300–700
  paragraphSpacing: number // em; -1 keeps the book's own
  indent: number          // em; -1 keeps the book's own
  bionic: boolean         // bold the start of each word
  warmth: number          // 0–100: warm amber light over the page
  ruler: Ruler            // reading ruler / focus band
  rulerY: number          // where it sits, as a fraction of the screen height
  showClock: boolean      // time and battery while reading
  brightness: number // percent
  tapZones: boolean       // tap the left/right side to turn pages (page mode)
  scrollPastEnd: boolean  // keep scrolling at a chapter's end to go on (scroll mode)
  keepAwake: boolean      // keep the screen on while reading
  volumeKeys: boolean     // volume keys turn pages (Android app)
  uiScale: number         // percent: size of the app's own text and controls (not the book)
  orientation: Orientation // Android app
  haptics: boolean        // Android app
  dailyGoal: number       // minutes of reading a day; 0 for no goal
  yearGoal: number        // books to finish this year; 0 for no goal
}

export const defaults: Settings = {
  style: 'star',
  theme: 'light',
  customBg: '#f3efe6',
  customText: '#2b2a26',
  flow: 'scrolled',
  font: 'serif',
  fontSize: 100,
  lineHeight: 1.7,
  margins: 'normal',
  align: 'start',
  hyphenate: false,
  fontWeight: 400,
  paragraphSpacing: -1,
  indent: -1,
  bionic: false,
  warmth: 0,
  ruler: 'off',
  rulerY: 0.4,
  showClock: true,
  brightness: 100,
  tapZones: true,
  scrollPastEnd: true,
  keepAwake: true,
  volumeKeys: true,
  uiScale: 100,
  orientation: 'auto',
  haptics: true,
  dailyGoal: 0,
  yearGoal: 0,
}

export const limits = {
  fontSize: { min: 80, max: 180, step: 5 },
  lineHeight: { min: 1.2, max: 2.2, step: 0.1 },
  brightness: { min: 50, max: 100, step: 5 },
  fontWeight: { min: 300, max: 700, step: 50 },
  warmth: { min: 0, max: 100, step: 5 },
}

export const clamp = (value: number, { min, max }: { min: number; max: number }) =>
  Math.min(max, Math.max(min, value))

const KEY = 'quiet:settings'

export function loadSettings(): Settings {
  try {
    const { autoNight, nightTheme, ...saved } = JSON.parse(localStorage.getItem(KEY) ?? '{}')
    // Before 0.3.0-dev.4 there was a "follow the system's dark mode" switch; it's now the System theme.
    if (autoNight) saved.theme = 'system'
    void nightTheme
    // A style that's no longer offered (Lamplight, in an early 0.3.3) falls back to the default.
    if (saved.style && !DESIGN_STYLES.includes(saved.style)) delete saved.style
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
