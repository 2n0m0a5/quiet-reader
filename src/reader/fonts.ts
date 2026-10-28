// Reading fonts bundled with Quiet (all SIL Open Font License). Each design style has its own
// serif and sans; the ones without Cyrillic (Newsreader, Figtree) fall back to Literata and
// Source Sans 3, which have it, so Russian books look right in every style.
// The book is shown in its own frame, so the page's fonts aren't visible there; instead the
// @font-face rules are injected into the book with absolute URLs. Thanks to unicode-range,
// the browser only downloads the alphabets a book actually uses.
import literata from '@fontsource-variable/literata/index.css?raw'
import literataItalic from '@fontsource-variable/literata/wght-italic.css?raw'
import sourceSans from '@fontsource-variable/source-sans-3/index.css?raw'
import sourceSansItalic from '@fontsource-variable/source-sans-3/wght-italic.css?raw'
import sourceSerif from '@fontsource-variable/source-serif-4/index.css?raw'
import sourceSerifItalic from '@fontsource-variable/source-serif-4/wght-italic.css?raw'
import figtree from '@fontsource-variable/figtree/index.css?raw'
import figtreeItalic from '@fontsource-variable/figtree/wght-italic.css?raw'
import newsreader from '@fontsource-variable/newsreader/index.css?raw'
import newsreaderItalic from '@fontsource-variable/newsreader/wght-italic.css?raw'
import franklin from '@fontsource-variable/libre-franklin/index.css?raw'
import franklinItalic from '@fontsource-variable/libre-franklin/wght-italic.css?raw'
import lora from '@fontsource-variable/lora/index.css?raw'
import loraItalic from '@fontsource-variable/lora/wght-italic.css?raw'
import type { DesignStyle } from '../settings'

const files = import.meta.glob([
  '/node_modules/@fontsource-variable/literata/files/*-wght-*.woff2',
  '/node_modules/@fontsource-variable/source-sans-3/files/*-wght-*.woff2',
  '/node_modules/@fontsource-variable/source-serif-4/files/*-wght-*.woff2',
  '/node_modules/@fontsource-variable/figtree/files/*-wght-*.woff2',
  '/node_modules/@fontsource-variable/newsreader/files/*-wght-*.woff2',
  '/node_modules/@fontsource-variable/libre-franklin/files/*-wght-*.woff2',
  '/node_modules/@fontsource-variable/lora/files/*-wght-*.woff2',
], { eager: true, query: '?url', import: 'default' }) as Record<string, string>

const urlFor = new Map(Object.entries(files).map(([path, url]) =>
  [path.split('/').pop()!, new URL(url, document.baseURI).href]))

const withAbsoluteUrls = (css: string) =>
  css.replace(/url\(\.\/files\/([^)]+)\)/g, (match, name: string) =>
    urlFor.has(name) ? `url(${urlFor.get(name)})` : match)

const fallback = [literata, literataItalic, sourceSans, sourceSansItalic]
const faces: Record<DesignStyle, string> = {
  star: withAbsoluteUrls([sourceSerif, sourceSerifItalic, figtree, figtreeItalic, ...fallback].join('\n')),
  paper: withAbsoluteUrls([newsreader, newsreaderItalic, franklin, franklinItalic, ...fallback].join('\n')),
  tashkent: withAbsoluteUrls([lora, loraItalic, ...fallback].join('\n')),
}

export const fontFaceCSS = (style: DesignStyle) => faces[style] ?? faces.star

const stacks: Record<DesignStyle, { serif: string; sans: string }> = {
  star: {
    serif: `'Source Serif 4 Variable', 'Literata Variable', Georgia, serif`,
    sans: `'Figtree Variable', 'Source Sans 3 Variable', system-ui, sans-serif`,
  },
  paper: {
    serif: `'Newsreader Variable', 'Literata Variable', Georgia, serif`,
    sans: `'Libre Franklin Variable', 'Source Sans 3 Variable', system-ui, sans-serif`,
  },
  // Old Tashkent: Lora, a calligraphic serif with Cyrillic (Marcellus is only for titles in the app).
  tashkent: {
    serif: `'Lora Variable', 'Literata Variable', Georgia, serif`,
    sans: `'Source Sans 3 Variable', system-ui, sans-serif`,
  },
}

export const fontStacks = (style: DesignStyle) => stacks[style] ?? stacks.star
