// Reading fonts bundled with Quiet (all SIL Open Font License).
// The book is shown in its own frame, so the page's fonts aren't visible there; instead the
// @font-face rules are injected into the book with absolute URLs. Thanks to unicode-range,
// the browser only downloads the alphabets a book actually uses.
import literata from '@fontsource-variable/literata/index.css?raw'
import literataItalic from '@fontsource-variable/literata/wght-italic.css?raw'
import sourceSans from '@fontsource-variable/source-sans-3/index.css?raw'
import sourceSansItalic from '@fontsource-variable/source-sans-3/wght-italic.css?raw'
import crimson from '@fontsource-variable/crimson-pro/index.css?raw'
import crimsonItalic from '@fontsource-variable/crimson-pro/wght-italic.css?raw'
import archivo from '@fontsource-variable/archivo/index.css?raw'
import type { DesignStyle } from '../settings'

const files = import.meta.glob([
  '/node_modules/@fontsource-variable/literata/files/*-wght-*.woff2',
  '/node_modules/@fontsource-variable/source-sans-3/files/*-wght-*.woff2',
  '/node_modules/@fontsource-variable/crimson-pro/files/*-wght-*.woff2',
  '/node_modules/@fontsource-variable/archivo/files/*-wght-*.woff2',
], { eager: true, query: '?url', import: 'default' }) as Record<string, string>

const urlFor = new Map(Object.entries(files).map(([path, url]) =>
  [path.split('/').pop()!, new URL(url, document.baseURI).href]))

const withAbsoluteUrls = (css: string) =>
  css.replace(/url\(\.\/files\/([^)]+)\)/g, (match, name: string) =>
    urlFor.has(name) ? `url(${urlFor.get(name)})` : match)

const fallback = [literata, literataItalic, sourceSans, sourceSansItalic]
const faces: Record<DesignStyle, string> = {
  radio: withAbsoluteUrls([crimson, crimsonItalic, archivo, ...fallback].join('\n')),
}

export const fontFaceCSS = (style: DesignStyle) => faces[style] ?? faces.radio

// Radio: Crimson Pro for reading (a book face with large, open letters) and Archivo for the app
// (a plain grotesque, like the lettering on 1960s radios). Neither has Cyrillic, so Literata and
// Source Sans 3 take over for Russian books.
const stacks: Record<DesignStyle, { serif: string; sans: string }> = {
  radio: {
    serif: `'Crimson Pro Variable', 'Literata Variable', Georgia, serif`,
    sans: `'Archivo Variable', 'Source Sans 3 Variable', system-ui, sans-serif`,
  },
}

export const fontStacks = (style: DesignStyle) => stacks[style] ?? stacks.radio
