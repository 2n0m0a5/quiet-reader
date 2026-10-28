import type { Settings } from '../settings'
import { fontFaceCSS, fontStacks } from './fonts'
import { paletteOf } from './themes'
import { customFontCSS, familyStack } from './custom-fonts'

// Replaces the book's fonts everywhere except code, which stays monospaced.
const fontRule = (stack: string) => `
    html body, html body *:not(code, pre, kbd, samp, tt, code *, pre *) {
      font-family: ${stack} !important;
    }`

// CSS injected into every chapter of the book.
export function bookCSS(settings: Settings) {
  const { font, fontSize, lineHeight, align, hyphenate, fontWeight, paragraphSpacing, indent, style } = settings
  const c = paletteOf(settings)
  const hyphens = hyphenate ? 'auto' : 'manual'
  return `
    @namespace epub "http://www.idpf.org/2007/ops";
    ${font === 'original' ? ''
      : font.startsWith('custom:') ? customFontCSS() + fontRule(familyStack(font.slice(7)))
      : fontFaceCSS(style) + fontRule(fontStacks(style)[font as 'serif' | 'sans'])}
    html {
      color-scheme: ${c.dark ? 'dark' : 'light'};
      background: ${c.bg} !important;
      color: ${c.text} !important;
      font-size: ${fontSize}% !important;
    }
    body { background: transparent !important; color: inherit !important; }
    ${fontWeight !== 400 ? `html body { font-weight: ${fontWeight} !important; }` : ''}
    p, li, blockquote, dd {
      line-height: ${lineHeight} !important;
      hyphens: ${hyphens} !important;
      -webkit-hyphens: ${hyphens} !important;
      widows: 2;
      orphans: 2;
    }
    ${paragraphSpacing >= 0 ? `html body p { margin-top: 0 !important; margin-bottom: ${paragraphSpacing}em !important; }` : ''}
    ${indent >= 0 ? `html body p { text-indent: ${indent}em !important; }` : ''}
    /* Not !important: paragraphs a book centres or right-aligns with a class (titles, epigraphs) keep it. */
    html body :is(p, li, dd) { text-align: ${align}; }
    a:link, a:visited { color: ${c.link} !important; }
    pre { white-space: pre-wrap !important; }
    img, svg, video { max-width: 100%; height: auto; }
    img { cursor: zoom-in; }
    /* Bionic reading: Quiet's own bold word starts (see bionic.ts). */
    b[data-quiet="bionic"] { font-weight: ${Math.min(900, fontWeight + 300)} !important; font-style: inherit; }
    aside[epub|type~="endnote"], aside[epub|type~="footnote"],
    aside[epub|type~="note"], aside[epub|type~="rearnote"] { display: none; }
  `
}
