// Reading fonts bundled with Quiet (both SIL Open Font License, both cover Latin and Cyrillic).
// The book is shown in its own frame, so the page's fonts aren't visible there; instead the
// @font-face rules are injected into the book with absolute URLs. Thanks to unicode-range,
// the browser only downloads the alphabets a book actually uses.
import literata from '@fontsource-variable/literata/index.css?raw'
import literataItalic from '@fontsource-variable/literata/wght-italic.css?raw'
import sourceSans from '@fontsource-variable/source-sans-3/index.css?raw'
import sourceSansItalic from '@fontsource-variable/source-sans-3/wght-italic.css?raw'

const files = {
  ...import.meta.glob('/node_modules/@fontsource-variable/literata/files/*-wght-*.woff2',
    { eager: true, query: '?url', import: 'default' }),
  ...import.meta.glob('/node_modules/@fontsource-variable/source-sans-3/files/*-wght-*.woff2',
    { eager: true, query: '?url', import: 'default' }),
} as Record<string, string>

const urlFor = new Map(Object.entries(files).map(([path, url]) =>
  [path.split('/').pop()!, new URL(url, document.baseURI).href]))

const withAbsoluteUrls = (css: string) =>
  css.replace(/url\(\.\/files\/([^)]+)\)/g, (match, name: string) =>
    urlFor.has(name) ? `url(${urlFor.get(name)})` : match)

export const fontFaceCSS = withAbsoluteUrls([literata, literataItalic, sourceSans, sourceSansItalic].join('\n'))

export const fontStacks = {
  serif: `'Literata Variable', Georgia, serif`,
  sans: `'Source Sans 3 Variable', system-ui, sans-serif`,
}
