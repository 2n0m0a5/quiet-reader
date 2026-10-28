// The first-run welcome (three short pages), and "What's new" after an update.
import { BookOpen, ChartColumn, FolderOpen, Highlighter, Languages, Library, NotebookText, Palette, Quote, Sparkles } from 'lucide'
import { button, h, icon, openDialog } from '../ui/dialog'
import { logo as quietLogo } from '../ui/brand'

const WELCOME_KEY = 'quiet:welcome-seen'
const VERSION_KEY = 'quiet:seen-version'

// The last version with something to tell: What's new shows once to anyone updating from before it.
const NEWS = '0.3.4'

// "0.3.0-dev.5" → [0, 3, 0], for comparing versions.
const parts = (version: string) => version.split('-')[0].split('.').map(Number)
const older = (a: string, b: string) => {
  const [x, y] = [parts(a), parts(b)]
  for (let i = 0; i < 3; i++) if ((x[i] ?? 0) !== (y[i] ?? 0)) return (x[i] ?? 0) < (y[i] ?? 0)
  return false
}

const read = (key: string) => { try { return localStorage.getItem(key) } catch { return null } }
const write = (key: string, value: string) => { try { localStorage.setItem(key, value) } catch { /* storage unavailable */ } }

// On start: a new reader sees the welcome; someone updating sees What's new (once).
export function greet(hasBooks: boolean, addBooks: () => void) {
  const seen = read(VERSION_KEY)
  write(VERSION_KEY, __APP_VERSION__)
  if (!read(WELCOME_KEY) && !hasBooks) {
    write(WELCOME_KEY, '1')
    return showWelcome(addBooks)
  }
  write(WELCOME_KEY, '1')
  // Versions before 0.3 didn't remember themselves; having books means it's an update.
  if (older(seen ?? '0.2.0', NEWS)) showWhatsNew(seen ?? '0.2.0')
}

interface Page { art: HTMLElement; title: string; text: string }

function art(className: string, ...children: (Node | string)[]) {
  const el = h('div', `welcome-art ${className}`)
  el.append(...children)
  el.setAttribute('aria-hidden', 'true')
  return el
}

export function showWelcome(addBooks: () => void) {
  // A tiny phone that shows where to tap.
  const phone = art('welcome-phone')
  for (const zone of ['left', 'center', 'right']) {
    const z = h('div', `welcome-zone ${zone}`)
    z.append(h('span', '', zone === 'center' ? 'Controls' : zone === 'left' ? 'Back' : 'Next'))
    phone.append(z)
  }
  const marks = art('welcome-marks')
  for (const [symbol, label] of [[Highlighter, 'Highlight'], [Quote, 'Quote'], [Languages, 'Translate']] as const) {
    const m = h('div', 'welcome-mark')
    m.append(icon(symbol), h('span', '', label))
    marks.append(m)
  }
  const logo = art('welcome-logo', quietLogo())

  const style = document.documentElement.dataset.style
  const pages: Page[] = [
    style === 'paper'
      ? { art: logo, title: 'A place to read. Nothing else.', text: 'EPUB, PDF, Word, comics, FB2, MOBI and more. No account, no ads, no feed. Your books, your place in them and your notes stay on this device.' }
      : style === 'frost'
      ? { art: logo, title: 'Clear as glass.', text: 'Your books, with everything else on a pane of frosted glass that floats out of the way. EPUB, PDF, Word, comics and more. Everything stays on this device.' }
      : style === 'tashkent'
      ? { art: logo, title: 'Come in, and read.', text: 'A quiet courtyard for your books: EPUB, PDF, Word, comics, FB2, MOBI and more. Everything stays on this device. No account, no ads.' }
      : { art: logo, title: 'Just you and the book.', text: 'A calm place for your books: EPUB, PDF, Word, comics, FB2, MOBI and more. Everything stays on this device. No account, no ads, no tracking.' },
    { art: phone, title: 'Tap to read', text: 'Tap the middle of the page to show or hide the controls. In page mode, tap the left or right edge to turn the page.' },
    { art: marks, title: 'Make it yours', text: 'Select text to highlight it, write a note, save a quote or translate it. Themes, fonts and spacing are under the sliders button while you read.' },
  ]

  let index = 0
  const stage = h('div', 'welcome-stage')
  const dots = h('div', 'welcome-dots')
  const actions = h('div', 'dialog-actions welcome-actions')
  const d = openDialog({ className: 'dialog-small dialog-welcome', content: [stage, dots, actions] })
  d.el.setAttribute('aria-label', 'Welcome to Quiet')

  const show = (next: number, direction = 1) => {
    index = Math.max(0, Math.min(pages.length - 1, next))
    const page = pages[index]
    const slide = h('div', 'welcome-slide')
    slide.style.setProperty('--from', `${direction * 24}px`)
    slide.append(page.art, h('h2', 'welcome-title', page.title), h('p', 'welcome-text', page.text))
    stage.replaceChildren(slide)
    dots.replaceChildren(...pages.map((_, i) => {
      const dot = h('button', `welcome-dot${i === index ? ' on' : ''}`)
      dot.setAttribute('aria-label', `Page ${i + 1} of ${pages.length}`)
      dot.addEventListener('click', () => show(i, i > index ? 1 : -1))
      return dot
    }))
    const last = index === pages.length - 1
    actions.replaceChildren(
      button(last ? 'Later' : 'Skip', '', () => d.close()),
      last
        ? button('Add a book', 'primary', () => { d.close(); addBooks() })
        : button('Next', 'primary', () => show(index + 1)),
    )
  }
  // Swipe between pages.
  let startX: number | null = null
  stage.addEventListener('touchstart', e => { startX = e.touches[0].clientX }, { passive: true })
  stage.addEventListener('touchend', e => {
    if (startX == null) return
    const dx = e.changedTouches[0].clientX - startX
    startX = null
    if (Math.abs(dx) > 50) show(index + (dx < 0 ? 1 : -1), dx < 0 ? 1 : -1)
  })
  d.el.addEventListener('keydown', e => {
    if (e.key === 'ArrowRight') show(index + 1, 1)
    if (e.key === 'ArrowLeft') show(index - 1, -1)
  })
  show(0)
}

// What's new in this version. Also opened from Settings → About.
// `since`: the version the reader had before; what's newer than that comes first.
export function showWhatsNew(since = '0.3.3') {
  const latest: [typeof BookOpen, string, string][] = [
    [Palette, 'A fourth style: Frosty Glass', 'Controls of frosted glass float over a clear page, on soft, icy light. A liquid lens moves between tabs, and glass lights up where you press. Settings → Style.'],
    [Sparkles, 'Everything moves', 'A book zooms out of its cover and back into it. Icons answer when tapped, books glide into place when sorted, swipe from the left edge to leave a book, and pages can curl.'],
    [BookOpen, 'More kinds of books', 'Word (DOCX), OpenDocument (ODT), RTF, Markdown and Palm documents, and comics in CBR, CB7 and CBT, or as pictures in a zip.'],
    [Library, 'Series, fonts and quote pictures', 'A Series shelf with each series in order. Read in your own fonts (Settings → Your fonts). Share a quote as a picture from Notes.'],
  ]
  const v033: [typeof BookOpen, string, string][] = [
    [Palette, 'A third style: Old Tashkent', 'Ganch plaster, carved walnut and arched doorways. Books sit in arches, and screens open like a door into a courtyard. Settings → Style.'],
    [ChartColumn, 'Five new reading stats', 'Your reading year as a calendar, when you read, your pace, finished books by month, and book by book with time left to finish.'],
    [Sparkles, 'Motion like iOS', 'Lists stretch past their ends and ease back. In scroll mode, keep pulling at the end of a chapter and let go to turn it. Drag sheets down to close. And each style moves in its own way.'],
  ]
  const v032: [typeof BookOpen, string, string][] = [
    [Palette, 'Two design styles', 'Quiet Star: almost nothing, and one small star. Paper: black ink on white paper, nothing else. Pick one in Settings → Style.'],
    [Sparkles, 'More on the way', 'More styles are coming, and later you’ll be able to build your own.'],
  ]
  const before: [typeof BookOpen, string, string][] = [
    [FolderOpen, 'Book folders', 'Fill a folder with books and choose it once: Quiet adds them all, and in the app it checks the folder for new books each time it opens. Add books → Add a book folder.'],
    [Quote, 'Quotes have owners', 'When you save a quote, say who says it: a character, or leave it empty for the narrator. Copied quotes are signed with their name and the book.'],
  ]
  const items: [typeof BookOpen, string, string][] = [
    [Palette, 'Reading comfort', 'Black and custom themes, a System theme, warm light, a reading ruler, bold word starts, text weight and spacing.'],
    [BookOpen, 'Easier reading', 'Footnotes in a small window, pictures full screen, go to any percent, a Back chip after links, and pinch to zoom PDFs.'],
    [Library, 'A tidier library', 'Tags, ratings and reviews, Want to read, a shelf view, selecting several books, Recently deleted, and duplicates.'],
    [NotebookText, 'Notes and quotes', 'Underline and strikethrough, quotes, tags on highlights, and one Notes screen for every book.'],
    [Languages, 'Translate', 'Select text and translate it in a small window, with meanings for single words.'],
    [ChartColumn, 'Reading stats', 'Time read, a gentle streak, daily and yearly goals, and how many pages you read.'],
  ]
  const list = h('div', 'whats-new')
  const rows = [...latest, ...older(since, '0.3.3') ? v033 : [], ...older(since, '0.3.2') ? v032 : [], ...older(since, '0.3.1') ? before : [], ...older(since, '0.3.0') ? items : []]
  for (const [symbol, title, text] of rows) {
    const row = h('div', 'whats-new-item')
    const badge = h('div', 'whats-new-icon')
    badge.append(icon(symbol))
    const body = h('div')
    body.append(h('div', 'whats-new-title', title), h('div', 'settings-hint', text))
    row.append(badge, body)
    list.append(row)
  }
  const actions = h('div', 'dialog-actions')
  const d = openDialog({ title: `What’s new in Quiet ${NEWS}`, className: 'dialog-details', content: [list, actions] })
  actions.append(
    button('See the styles', '', () => { d.close(); location.hash = '#/settings' }),
    button('Continue', 'primary', () => d.close()),
  )
}
