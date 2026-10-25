# foliate-js (vendored)

Source: https://github.com/johnfactotum/foliate-js
Commit: 78914aef4466eb960965702401634c2cb348e9b1 (2026-05-01)
License: MIT (see LICENSE)

Copied as-is, except:
- `pdf.js` loads PDF.js from the `pdfjs-dist` package and its data files from
  `public/pdfjs/` (copied by `scripts/copy-pdfjs.mjs`); the rendering code is upstream's.
- `vendor/pdfjs/` contains only upstream's two text/annotation layer stylesheets.
- `view.d.ts`, `overlayer.d.ts`, `epubcfi.d.ts` are added (TypeScript types, written for Quiet).
- the rest of `vendor/pdfjs`, `reader.html`, `reader.js`, `ui/`, `opds.js`, `dict.js`,
  `quote-image.js`, `uri-template.js`, tests and build files are left out.

To update: copy the same files from a newer commit, keep the `pdf.js` stub,
and update the commit hash above.
