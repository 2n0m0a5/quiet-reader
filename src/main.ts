import '@fontsource-variable/literata'
import '@fontsource-variable/source-sans-3'
import './styles/tokens.css'
import './styles/app.css'
import {
  createIcons, AArrowDown, AArrowUp, AlignJustify, AlignLeft, ArrowLeft, BookOpen, ChevronDown,
  ChevronLeft, ChevronRight, ChevronUp, Download, Library, List, Moon, Plus, Settings2, Sun, Sunset,
  Bookmark, BookmarkCheck, Search, Settings as SettingsIcon, Undo2, X, ZoomIn, ZoomOut,
} from 'lucide'
import { Reader, ZOOM_STEPS, type TapZone } from './reader/reader'
import { effectiveTheme, onSystemThemeChange, paletteOf } from './reader/themes'
import { ReadingOverlays } from './reader/overlays'
import { showFootnote } from './reader/footnotes'
import { showImage } from './ui/image-viewer'
import type { Location } from './reader/foliate'
import { TocView } from './ui/toc'
import { toast } from './ui/toast'
import { clamp, limits, loadSettings, saveSettings, type Settings } from './settings'
import { getBook, getFile, moveToTrash, restoreBooks, updateBook, type BookRecord } from './library/db'
import { asFile, importBook, ImportError } from './library/import'
import { LibraryView } from './library/library-view'
import { showBookDetails } from './library/details'
import { closeTopDialog, formDialog } from './ui/dialog'
import { BookNotes, BookSearch } from './reader/notes'
import { hideSelectionMenu } from './reader/selection-menu'
import { duration, percent } from './ui/format'
import { setUpInstallButton, setUpOffline } from './app/install'
import { ACCEPTED } from './formats/load'
import {
  haptic, isNativeApp, keepScreenOn, onBackButton, onIncomingFile, setHaptics, setOrientation,
  setSystemBarsHidden, setSystemBarsTheme, setVolumeKeys,
} from './app/native'
import { SettingsScreen } from './settings-screen'

createIcons({
  icons: {
    AArrowDown, AArrowUp, AlignJustify, AlignLeft, ArrowLeft, BookOpen, ChevronDown,
    ChevronLeft, ChevronRight, ChevronUp, Download, Library, List, Moon, Plus, Settings2, Sun, Sunset,
    Bookmark, BookmarkCheck, Search, Settings: SettingsIcon, Undo2, X, ZoomIn, ZoomOut,
  },
})

const $ = <T extends HTMLElement = HTMLElement>(id: string) => document.getElementById(id) as T

const ui = {
  app: $('app'),
  fileInput: $<HTMLInputElement>('fileInput'),
  sidebar: $('sidebar'),
  scrim: $('scrim'),
  cover: $('cover'),
  bookTitle: $('bookTitle'),
  bookMeta: $('bookMeta'),
  bookmarkToggle: $('bookmarkToggle'),
  searchForm: $<HTMLFormElement>('searchForm'),
  searchInput: $<HTMLInputElement>('searchInput'),
  chapterLabel: $('chapterLabel'),
  viewer: $('viewer'),
  stage: $('readerStage'),
  readerError: $('readerError'),
  readerErrorText: $('readerErrorText'),
  topTitle: $('topTitle'),
  miniProgress: $('miniProgress'),
  sheetScrim: $('sheetScrim'),
  settings: $('settings'),
  settingsToggle: $('settingsToggle'),
  fontRange: $<HTMLInputElement>('fontRange'),
  fontValue: $('fontValue'),
  lineRange: $<HTMLInputElement>('lineRange'),
  lineValue: $('lineValue'),
  brightRange: $<HTMLInputElement>('brightRange'),
  brightValue: $('brightValue'),
  warmRange: $<HTMLInputElement>('warmRange'),
  warmValue: $('warmValue'),
  weightRange: $<HTMLInputElement>('weightRange'),
  weightValue: $('weightValue'),
  backChip: $('backChip'),
  backChipLabel: $('backChipLabel'),
  progress: $<HTMLInputElement>('progress'),
  progressText: $('progressText'),
  timeLeft: $('timeLeft'),
}

const toc = new TocView($('toc'))
const coverPlaceholder = ui.cover.innerHTML
let settings = loadSettings()
let reader: Reader | null = null
let notes: BookNotes | null = null
let bookSearch: BookSearch | null = null
let coverUrl = ''
let openToken = 0
let appliedOrientation = ''
// Warm light, reading ruler, clock: drawn over the page (moving the ruler saves its place).
const overlays = new ReadingOverlays(ui.stage, settings, rulerY => {
  settings = { ...settings, rulerY }
  saveSettings(settings)
})

// ---------- Settings ----------

function update(patch: Partial<Settings>) {
  settings = { ...settings, ...patch }
  saveSettings(settings)
  render()
  reader?.apply(settings)
  applyReadingSession()
}

// While a book is open: keep the screen on, and let the volume keys turn pages (if switched on).
function applyReadingSession() {
  const reading = !!reader && ui.app.dataset.view === 'reader'
  keepScreenOn(reading && settings.keepAwake)
  setVolumeKeys(reading && settings.volumeKeys
    ? direction => { setChrome(false); direction === 'next' ? reader?.next() : reader?.prev() }
    : null)
}

function render() {
  const root = document.documentElement
  const palette = paletteOf(settings)
  root.dataset.theme = effectiveTheme(settings)
  root.dataset.flow = settings.flow
  root.toggleAttribute('data-dark', palette.dark)
  // The Custom theme: the app's colours are mixed from the two the reader picked.
  const custom = root.dataset.theme === 'custom'
  for (const [name, value] of [
    ['--reader-bg', palette.bg], ['--reader-text', palette.text], ['--reader-link', palette.link],
    ['--panel', palette.bg], ['--text', palette.text],
    ['--bg', `color-mix(in srgb, ${palette.bg} 94%, ${palette.text})`],
    ['--line', `color-mix(in srgb, ${palette.bg} 85%, ${palette.text})`],
    ['--muted', `color-mix(in srgb, ${palette.text} 60%, ${palette.bg})`],
  ]) custom ? root.style.setProperty(name, value) : root.style.removeProperty(name)
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content',
    getComputedStyle(root).getPropertyValue('--panel').trim())
  setSystemBarsTheme(palette.dark)
  setHaptics(settings.haptics)
  if (appliedOrientation !== settings.orientation) setOrientation(appliedOrientation = settings.orientation)

  ui.fontRange.value = String(settings.fontSize)
  ui.fontValue.textContent = `${settings.fontSize}%`
  ui.lineRange.value = String(settings.lineHeight)
  ui.lineValue.textContent = settings.lineHeight.toFixed(1)
  ui.brightRange.value = String(settings.brightness)
  ui.brightValue.textContent = `${settings.brightness}%`
  ui.stage.style.filter = settings.brightness < 100 ? `brightness(${settings.brightness / 100})` : ''
  ui.warmRange.value = String(settings.warmth)
  ui.warmValue.textContent = settings.warmth ? `${settings.warmth}%` : 'Off'
  ui.weightRange.value = String(settings.fontWeight)
  ui.weightValue.textContent = settings.fontWeight === 400 ? 'Regular' : settings.fontWeight < 400 ? 'Lighter' : 'Bolder'
  overlays.apply(settings)

  // App text size: scales Quiet's own text and controls, not the book (see app.css, --ui-zoom).
  root.style.setProperty('--ui-zoom', String(settings.uiScale / 100))

  ui.settings.querySelectorAll<HTMLElement>('.choice-row').forEach(row => {
    const current = String(settings[row.dataset.setting as keyof Settings])
    row.querySelectorAll<HTMLButtonElement>('.choice').forEach(b =>
      b.classList.toggle('selected', b.dataset.value === current))
  })
}

const setFont = (v: number) => update({ fontSize: clamp(v, limits.fontSize) })

ui.fontRange.addEventListener('input', () => setFont(Number(ui.fontRange.value)))
ui.lineRange.addEventListener('input', () => update({ lineHeight: Number(ui.lineRange.value) }))
ui.brightRange.addEventListener('input', () => update({ brightness: Number(ui.brightRange.value) }))
ui.warmRange.addEventListener('input', () => update({ warmth: Number(ui.warmRange.value) }))
ui.weightRange.addEventListener('input', () => update({ fontWeight: Number(ui.weightRange.value) }))
$('smaller').addEventListener('click', () => setFont(settings.fontSize - limits.fontSize.step))
$('larger').addEventListener('click', () => setFont(settings.fontSize + limits.fontSize.step))
// The moon/sun button: switch between the day and night themes used last (Day and Night at first).
const LAST_KEY = (night: boolean) => night ? 'quiet:night-theme' : 'quiet:day-theme'
$('themeQuick').addEventListener('click', () => {
  const night = paletteOf(settings).dark
  try {
    if (settings.theme !== 'system') localStorage.setItem(LAST_KEY(night), settings.theme)
    const other = localStorage.getItem(LAST_KEY(!night)) as Settings['theme'] | null
    update({ theme: other ?? (night ? 'light' : 'dark') })
  } catch {
    update({ theme: night ? 'light' : 'dark' })   // storage unavailable
  }
})
onSystemThemeChange(() => { render(); reader?.apply(settings) })

ui.settings.querySelectorAll<HTMLElement>('.choice-row').forEach(row => {
  row.addEventListener('click', e => {
    const button = (e.target as HTMLElement).closest<HTMLButtonElement>('.choice')
    if (!button) return
    const raw = button.dataset.value!
    const value = raw === 'true' ? true : raw === 'false' ? false : /^-?[\d.]+$/.test(raw) ? Number(raw) : raw
    haptic()
    update({ [row.dataset.setting!]: value } as Partial<Settings>)
  })
})

// Reading settings: a popover on wide screens, a sheet from the bottom on phones.
// Closes on outside click, a tap on the backdrop, or Escape.
function setSettingsOpen(open: boolean) {
  ui.settings.classList.toggle('open', open)
  ui.sheetScrim.classList.toggle('open', open)
}
ui.settingsToggle.addEventListener('click', e => {
  e.stopPropagation()
  setSettingsOpen(!ui.settings.classList.contains('open'))
})
ui.settings.addEventListener('click', e => e.stopPropagation())
ui.sheetScrim.addEventListener('click', () => setSettingsOpen(false))
document.addEventListener('click', () => setSettingsOpen(false))

// ---------- Controls on a phone ----------
// On small screens the book fills the screen. A tap in the middle shows or hides the
// controls; scrolling or turning a page hides them again.

const compact = matchMedia('(max-width: 850px)')

// On phones the settings sheet sits at the top level, so the backdrop dims everything behind it.
// On wide screens it's a popover under the settings button.
const settingsHome = ui.settings.parentElement!
const placeSettings = () => (compact.matches ? ui.app : settingsHome).append(ui.settings)
compact.addEventListener('change', placeSettings)
placeSettings()

function setChrome(shown: boolean) {
  if (!shown && (ui.settings.classList.contains('open') || ui.sidebar.classList.contains('open'))) return
  ui.app.dataset.chrome = shown ? 'shown' : 'hidden'
  // In the Android app, hiding the controls also hides the status and navigation bars.
  setSystemBarsHidden(!shown && compact.matches && ui.app.dataset.view === 'reader')
}

function onTap(zone: TapZone) {
  // A tap while the selection menu is open just closes it.
  if (document.querySelector('.selection-menu')) { hideSelectionMenu(); return }
  const paged = settings.flow === 'paginated' || reader?.view.isFixedLayout
  if (paged && zone !== 'center' && settings.tapZones) {
    // Tap the left or right third to turn the page.
    zone === 'left' ? reader?.view.goLeft() : reader?.view.goRight()
    setChrome(false)
  } else if (compact.matches) {
    setChrome(ui.app.dataset.chrome === 'hidden')
  }
}

// ---------- Sidebar on small screens ----------

const setSidebar = (open: boolean) => {
  ui.sidebar.classList.toggle('open', open)
  ui.scrim.classList.toggle('open', open)
}
// The Contents button (the chapter name in the reading toolbar): opens the contents drawer on
// phones; on wider screens it shows or hides the side panel, for more room to read.
const SIDEBAR_KEY = 'quiet:sidebar-hidden'
const workspace = document.querySelector<HTMLElement>('.workspace')!
try { workspace.classList.toggle('sidebar-hidden', localStorage.getItem(SIDEBAR_KEY) === '1') } catch { /* storage unavailable */ }
$('tocToggle').addEventListener('click', () => {
  if (compact.matches) return setSidebar(true)
  const hidden = workspace.classList.toggle('sidebar-hidden')
  try { localStorage.setItem(SIDEBAR_KEY, hidden ? '1' : '0') } catch { /* storage unavailable */ }
})
ui.scrim.addEventListener('click', () => setSidebar(false))

// ---------- Side panel: contents, bookmarks, notes, search ----------

function showPanel(tab: string) {
  document.querySelectorAll<HTMLElement>('.panel-tab').forEach(t => {
    t.classList.toggle('selected', t.dataset.tab === tab)
    t.setAttribute('aria-selected', String(t.dataset.tab === tab))
  })
  document.querySelectorAll<HTMLElement>('.panel').forEach(p => { p.hidden = p.dataset.pane !== tab })
}
document.querySelectorAll<HTMLElement>('.panel-tab').forEach(t =>
  t.addEventListener('click', () => {
    showPanel(t.dataset.tab!)
    if (t.dataset.tab === 'search') ui.searchInput.focus()
  }))

// Jump to a place from the side panel; on phones the panel closes so the page is visible.
function goToFromPanel(cfi: string) {
  rememberJump()
  reader?.goTo(cfi)
  if (compact.matches) setSidebar(false)
}

ui.bookmarkToggle.addEventListener('click', () => notes?.toggleBookmark())
$('searchToggle').addEventListener('click', () => {
  setSidebar(true)
  showPanel('search')
  ui.searchInput.focus()
})
ui.searchForm.addEventListener('submit', e => {
  e.preventDefault()
  ui.searchInput.blur()
  bookSearch?.search(ui.searchInput.value)
})
ui.searchInput.addEventListener('input', () => { if (!ui.searchInput.value) bookSearch?.clear() })


// ---------- Screens ----------
// "#/book/<id>" shows that book, "#/settings" the settings; anything else shows the library.
// Using the address bar means the browser's (and Android's) back button returns to the library.

const library = new LibraryView($('library'), {
  open: openBook,
  add: chooseFiles,
  info: record => showBookDetails(record, {
    read: openBook,
    remove: book => removeBooks([book]),
    changed: () => library.refresh(),
  }),
  remove: removeBooks,
})

function openBook(id: string) {
  location.hash = `#/book/${id}`
}

function goToLibrary() {
  // Go back if we came from the library, so the back button doesn't bounce between the two.
  if (history.state?.fromLibrary) history.back()
  else location.hash = ''
}

function route() {
  const match = location.hash.match(/^#\/book\/(.+)$/)
  if (match) showReader(decodeURIComponent(match[1]))
  else if (location.hash === '#/settings') showSettings()
  else showLibrary()
}

window.addEventListener('hashchange', e => {
  // Remember that this screen was opened from the library (see goToLibrary).
  const oldHash = new URL(e.oldURL).hash
  if (location.hash.length > 1 && (oldHash === '' || oldHash === '#'))
    history.replaceState({ fromLibrary: true }, '')
  route()
})

const settingsScreen = new SettingsScreen($('settingsScreen'), {
  get: () => settings,
  update,
  libraryCleared: () => library.refresh(),
})

function showSettings() {
  closeReader()
  library.closeSelection()
  setSystemBarsHidden(false)
  ui.app.dataset.view = 'settings'
  document.title = 'Settings · Quiet'
  settingsScreen.render()
  $('settingsScreen').scrollTop = 0
}

$('openSettings').addEventListener('click', () => { location.hash = '#/settings' })
$('moreSettings').addEventListener('click', () => { setSettingsOpen(false); location.hash = '#/settings' })

function showLibrary() {
  closeReader()
  setSystemBarsHidden(false)
  ui.app.dataset.view = 'library'
  document.title = 'Quiet'
  library.refresh()
  offline.offerUpdate()
}

$('backToLibrary').addEventListener('click', goToLibrary)
$('errorBack').addEventListener('click', goToLibrary)

// ---------- Adding books ----------

function chooseFiles() {
  ui.fileInput.click()
}
$('openFile').addEventListener('click', chooseFiles)

ui.fileInput.addEventListener('change', () => {
  const files = Array.from(ui.fileInput.files ?? [])
  ui.fileInput.value = ''
  addFiles(files)
})

// Drag and drop books anywhere on the window (desktop).
document.addEventListener('dragover', e => e.preventDefault())
document.addEventListener('drop', e => {
  e.preventDefault()
  addFiles(Array.from(e.dataTransfer?.files ?? []))
})

async function addFiles(files: File[]) {
  if (!files.length) return
  if (files.length > 1) toast(`Adding ${files.length} books…`, 60_000)
  const added: BookRecord[] = []
  const failed: string[] = []
  for (const file of files) {
    try {
      added.push(await importBook(file))
    } catch (err) {
      console.error('Could not add book:', err)
      failed.push(err instanceof ImportError ? err.message : `Couldn’t add “${file.name}”.`)
    }
  }

  // One book: open it straight away. Several: show them in the library.
  if (files.length === 1 && added.length === 1) {
    openBook(added[0].id)
    return
  }
  if (ui.app.dataset.view === 'library') library.refresh()
  if (failed.length === 1 && !added.length) toast(failed[0], 6000)
  else if (failed.length) toast(`Added ${added.length} of ${files.length} books. ${failed.length} couldn’t be read.`, 6000)
  else toast(`Added ${added.length} books.`)
}

// Removed books go to Recently deleted for 30 days, so there's no need to ask first; Undo brings them back.
async function removeBooks(books: BookRecord[]) {
  if (!books.length) return
  await moveToTrash(books.map(b => b.id))
  haptic()
  library.refresh()
  toast(books.length === 1 ? `Removed “${books[0].title}”.` : `Removed ${books.length} books.`, 6000, {
    label: 'Undo',
    run: async () => { await restoreBooks(books.map(b => b.id)); library.refresh() },
  })
}

// ---------- Reading ----------

function closeReader() {
  openToken++
  bookSearch?.clear()
  bookSearch = null
  notes = null
  hideSelectionMenu()
  reader?.close()
  reader = null
  clearJump()
  delete ui.app.dataset.fxl
  applyReadingSession()
  setSidebar(false)
  showPanel('contents')
  ui.searchInput.value = ''
  setSettingsOpen(false)
  if (coverUrl) URL.revokeObjectURL(coverUrl)
  coverUrl = ''
}

async function showReader(id: string) {
  closeReader()
  library.closeSelection()
  const token = openToken
  ui.app.dataset.view = 'reader'
  ui.app.dataset.chrome = 'shown'
  ui.readerError.hidden = true
  ui.topTitle.textContent = ''
  ui.chapterLabel.textContent = 'Preparing your reading space…'
  ui.progress.disabled = true
  setProgress(0)
  toc.showMessage('Loading contents…')

  const record = await getBook(id)
  const blob = record && await getFile(id)
  if (token !== openToken) return
  if (!record || !blob || record.deletedAt) {
    showReaderError('This book is no longer in your library.')
    return
  }

  document.title = `${record.title} · Quiet`
  ui.bookTitle.textContent = record.title
  ui.topTitle.textContent = record.title
  ui.bookMeta.textContent = record.author || 'Unknown author'
  showSidebarCover(record)
  setProgress(record.progress)

  const next = new Reader(ui.viewer, {
    onRelocate,
    onSavePosition: (cfi, progress) => { updateBook(id, { cfi, progress }) },
    onTap,
    onUserMove: () => { setChrome(false); hideSelectionMenu() },
    onSelection: selection => notes?.onSelection(selection),
    onHighlightTap: (value, rect) => notes?.onHighlightTap(value, rect),
    onImage: (src, alt) => showImage(src, alt),
    onZoom: factor => { haptic(); toast(factor <= next.zoomFactor('fit-page') + 0.001 ? 'Whole page' : zoomLabel(factor), 1000) },
    onLink: e => {
      if (showFootnote(next.book, e, settings, href => { rememberJump(); reader?.goTo(href) })) return
      rememberJump()
    },
  })
  try {
    await next.open(asFile(record, blob), settings, record.cfi)
  } catch (err) {
    console.error('Could not open book:', err)
    next.close()
    if (token === openToken) showReaderError('This file may be damaged, or in a format Quiet doesn’t read yet.')
    return
  }
  if (token !== openToken) { next.close(); return }
  reader = next
  ui.app.toggleAttribute('data-fxl', next.view.isFixedLayout)
  notes = new BookNotes(next, id, { bookmarkList: $('bookmarkList'), noteList: $('noteList'), bookmarkButton: ui.bookmarkToggle }, goToFromPanel)
  notes.load()
  bookSearch = new BookSearch(next, { status: $('searchStatus'), results: $('searchResults') }, goToFromPanel)
  applyReadingSession()
  // Opening a book takes it off "Want to read".
  updateBook(id, { lastOpenedAt: Date.now(), wantToRead: false })
  ui.progress.disabled = false

  const count = toc.render(next.book.toc ?? [], href => {
    rememberJump()
    reader?.goTo(href)
    setSidebar(false)
  })
  if (!count) toc.showMessage('This book has no table of contents.')
}

function showSidebarCover(record: BookRecord) {
  if (record.cover) {
    const img = document.createElement('img')
    img.alt = ''
    img.src = coverUrl = URL.createObjectURL(record.cover)
    ui.cover.replaceChildren(img)
  } else {
    ui.cover.innerHTML = coverPlaceholder
  }
}

function showReaderError(message: string) {
  ui.readerError.hidden = false
  ui.readerErrorText.textContent = message
  ui.chapterLabel.textContent = ''
  toc.showMessage('')
}

function setProgress(fraction: number) {
  ui.progress.value = String(fraction)
  ui.progressText.textContent = ui.miniProgress.textContent = `${Math.round(fraction * 100)}%`
}

function onRelocate(location: Location) {
  setProgress(location.fraction ?? 0)
  if (location.tocItem?.label) ui.chapterLabel.textContent = location.tocItem.label.trim()
  toc.setCurrent(location.tocItem?.href)
  notes?.refreshBookmarkButton()
  // Time left, from the book's length and an average reading speed.
  const { section, total } = location.time ?? {}
  if (section != null && total != null) {
    const chapter = `${duration(section)} left in chapter`
    ui.progress.title = `${chapter} · ${duration(total)} left in book`
    ui.miniProgress.textContent = `${chapter} · ${Math.round((location.fraction ?? 0) * 100)}%`
    ui.timeLeft.textContent = chapter
  }
}

ui.progress.addEventListener('input', () => reader?.goToFraction(Number(ui.progress.value)))
ui.progress.addEventListener('pointerdown', () => rememberJump())
ui.progress.addEventListener('keydown', () => rememberJump())

// ---------- Jumps: a "Back" chip to return after following a link or jumping elsewhere ----------

let jumpFrom: { cfi: string; fraction: number } | null = null

function rememberJump() {
  const here = reader?.view.lastLocation
  if (!here?.cfi || jumpFrom) return
  jumpFrom = { cfi: here.cfi, fraction: here.fraction ?? 0 }
  const chapter = here.tocItem?.label?.trim()
  ui.backChipLabel.textContent = chapter ? `Back to ${chapter.length > 28 ? chapter.slice(0, 27) + '…' : chapter}` : `Back to ${percent(jumpFrom.fraction)}`
  ui.backChip.hidden = false
}

function clearJump() {
  jumpFrom = null
  ui.backChip.hidden = true
}

$('backChipGo').addEventListener('click', () => {
  if (jumpFrom) reader?.goTo(jumpFrom.cfi)
  haptic()
  clearJump()
})
$('backChipClose').addEventListener('click', clearJump)

// Tap the percentage to go to any place in the book.
ui.progressText.addEventListener('click', async () => {
  if (!reader) return
  const values = await formDialog({
    title: 'Go to',
    fields: [{ label: 'Percent of the book (0–100)', value: String(Math.round(Number(ui.progress.value) * 100)) }],
    confirm: 'Go',
  })
  const value = values && parseFloat(values[0].replace(',', '.'))
  if (value == null || Number.isNaN(value)) return
  rememberJump()
  reader?.goToFraction(Math.min(100, Math.max(0, value)) / 100)
})

// Zoom for PDFs and comics: the buttons step through whole page, page width, 1.5×, 2×, 3×
// (from wherever pinching left it).
const zoomLabel = (factor: number) => Math.abs(factor - 1) < 0.01 ? 'Page width' : `${Math.round(factor * 10) / 10}× width`
function zoomBy(step: 1 | -1) {
  if (!reader) return
  const now = reader.zoomFactor()
  const fit = reader.zoomFactor('fit-page')
  const levels = [fit, ...ZOOM_STEPS.filter(z => z > fit + 0.01)]
  const next = step > 0 ? levels.find(z => z > now + 0.01) : levels.findLast(z => z < now - 0.01)
  if (next == null) return
  reader.setZoom(next === fit ? 'fit-page' : next)
  haptic()
  toast(next === fit ? 'Whole page' : zoomLabel(next), 1200)
}
$('zoomIn').addEventListener('click', () => zoomBy(1))
$('zoomOut').addEventListener('click', () => zoomBy(-1))

// ---------- Navigation ----------

$('prev').addEventListener('click', () => reader?.prev())
$('next').addEventListener('click', () => reader?.next())
document.addEventListener('keydown', e => {
  if ((e.target as HTMLElement).matches('input, textarea')) return
  if (e.key === 'Escape') { setSettingsOpen(false); setSidebar(false); library.closeSelection(); return }
  reader?.handleKey(e)
})
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden') reader?.save()
})

// The website keeps itself offline with a service worker. The Android app doesn't need one:
// its files are already on the phone, and a worker could keep showing an old version after an update.
const offline = isNativeApp ? { offerUpdate() {} } : setUpOffline(() => ui.app.dataset.view === 'reader')
if (!isNativeApp) setUpInstallButton($('installApp'))

// Android's file picker often greys out e-books when filtered by type, so the app shows every file.
// Quiet still checks each file and explains if one can't be read.
if (isNativeApp) ui.fileInput.removeAttribute('accept')
else ui.fileInput.accept = ACCEPTED

// "Open with Quiet" and "Share to Quiet" in the Android app.
onIncomingFile(file => addFiles([file]), name => toast(`Couldn’t open “${name}”.`, 6000))

onBackButton(() => {
  if (closeTopDialog()) return true
  if (ui.settings.classList.contains('open')) { setSettingsOpen(false); return true }
  if (ui.sidebar.classList.contains('open')) { setSidebar(false); return true }
  if (ui.app.dataset.view === 'library' && library.closeSelection()) return true
  if (ui.app.dataset.view !== 'library') { goToLibrary(); return true }
  return false
})

render()
route()
