// Copies the data files PDF.js loads at runtime (character maps, standard fonts, image
// decoders, colour profiles) into public/pdfjs/, so they ship with the app and work offline.
// Runs automatically before `npm run dev` and `npm run build`.
import { cpSync, mkdirSync } from 'node:fs'

const from = 'node_modules/pdfjs-dist'
const to = 'public/pdfjs'
mkdirSync(to, { recursive: true })
for (const dir of ['cmaps', 'standard_fonts', 'wasm', 'iccs']) {
  cpSync(`${from}/${dir}`, `${to}/${dir}`, { recursive: true })
}
