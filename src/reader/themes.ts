// Reading themes: the colours of the page, and which theme is in effect right now.
import type { Settings, Theme } from '../settings'

export interface Palette { bg: string; text: string; link: string; dark: boolean }

// Colours used inside the book itself. They mirror the reader tokens in tokens.css.
const PALETTES: Record<Exclude<Theme, 'custom' | 'system'>, Palette> = {
  light: { bg: '#fffdf9', text: '#292923', link: '#55704f', dark: false },
  sepia: { bg: '#f6eddc', text: '#43382b', link: '#85623d', dark: false },
  dark: { bg: '#1d201d', text: '#e5e4dc', link: '#b4c8aa', dark: true },
  black: { bg: '#000000', text: '#cfcfc8', link: '#a9c39f', dark: true },
}

const systemDark = matchMedia('(prefers-color-scheme: dark)')

// The theme shown now. System is Day while the phone is light and Night while it's dark.
export function effectiveTheme(s: Settings): Exclude<Theme, 'system'> {
  if (s.theme === 'system') return systemDark.matches ? 'dark' : 'light'
  return s.theme
}

export function onSystemThemeChange(fn: () => void) {
  systemDark.addEventListener('change', fn)
}

export function paletteOf(s: Settings): Palette {
  const theme = effectiveTheme(s)
  if (theme !== 'custom') return PALETTES[theme]
  const dark = luminance(s.customBg) < 0.4
  return { bg: s.customBg, text: s.customText, link: dark ? '#b4c8aa' : '#55704f', dark }
}

// Relative luminance of a #rrggbb colour, 0 (black) to 1 (white).
function luminance(hex: string) {
  const n = parseInt(hex.replace('#', ''), 16)
  const [r, g, b] = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map(v => {
    const c = v / 255
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
