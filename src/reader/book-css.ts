import type { Settings } from '../settings'

// Colours used inside the book itself. They mirror the reader tokens in tokens.css.
const palettes = {
  light: { bg: '#fffdf9', text: '#292923', link: '#55704f' },
  sepia: { bg: '#f6eddc', text: '#43382b', link: '#85623d' },
  dark: { bg: '#1d201d', text: '#e5e4dc', link: '#b4c8aa' },
}

// CSS injected into every chapter of the book.
export function bookCSS({ theme, fontSize, lineHeight }: Settings) {
  const c = palettes[theme]
  return `
    @namespace epub "http://www.idpf.org/2007/ops";
    html {
      color-scheme: ${theme === 'dark' ? 'dark' : 'light'};
      background: ${c.bg} !important;
      color: ${c.text} !important;
      font-size: ${fontSize}% !important;
    }
    body { background: transparent !important; color: inherit !important; }
    p, li, blockquote, dd {
      line-height: ${lineHeight} !important;
      hyphens: auto;
      -webkit-hyphens: auto;
      widows: 2;
      orphans: 2;
    }
    a:link, a:visited { color: ${c.link} !important; }
    pre { white-space: pre-wrap !important; }
    img, svg, video { max-width: 100%; height: auto; }
    aside[epub|type~="endnote"], aside[epub|type~="footnote"],
    aside[epub|type~="note"], aside[epub|type~="rearnote"] { display: none; }
  `
}
