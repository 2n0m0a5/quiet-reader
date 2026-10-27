import '@fontsource-variable/literata'
import '@fontsource-variable/source-sans-3'
import './styles/tokens.css'
import './styles/app.css'
import {
  createIcons, AArrowDown, AArrowUp, AlignJustify, AlignLeft, ArrowLeft, BookOpen, ChevronDown,
  ChevronLeft, ChevronRight, ChevronUp, Download, Library, List, Moon, Plus, Settings2, Sun, Sunset,
  Bookmark, BookmarkCheck, Search, Settings as SettingsIcon,
} from 'lucide'
import { Reader, type TapZone } from './reader/reader'
import type { Location } from './reader/foliate'
import { TocView } from './ui/toc'
import { toast } from './ui/toast'
import { clamp, limits, loadSettings, saveSettings, type Settings } from './settings'
import { deleteBook, getBook, getFile, updateBook, type BookRecord } from './library/db'
import { asFile, importBook, ImportError } from './library/import'
import { LibraryView } from './library/library-view'
import { showBookDetails } from './library/details'
import { closeTopDialog, confirmDialog } from './ui/dialog'
import { BookNotes, BookSearch } from './reader/notes'
import { hideSelectionMenu } from './reader/selection-menu'
import { duration } from './ui/format'
import { setUpInstallButton, setUpOffline } from './app/install'
import { ACCEPTED } from './formats/load'
import {
  isNativeApp, keepScreenOn, onBackButton, onIncomingFile, setSystemBarsHidden, setSystemBarsTheme, setVolumeKeys,
} from './app/native'
import { SettingsScreen } from './settings-screen'

createIcons({
  icons: {
    AArrowDown, AArrowUp, AlignJustify, AlignLeft, ArrowLeft, BookOpen, ChevronDown,
    ChevronLeft, ChevronRight, ChevronUp, Download, Library, List, Moon, Plus, Settings2, Sun, Sunset,
    Bookmark, BookmarkCheck, Search, Settings: SettingsIcon,
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
  root.dataset.theme = settings.theme
  root.dataset.flow = settings.flow
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content',
    getComputedStyle(root).getPropertyValue('--panel').trim())
  setSystemBarsTheme(settings.theme)

  ui.fontRange.value = String(settings.fontSize)
  ui.fontValue.textContent = `${settings.fontSize}%`
  ui.lineRange.value = String(settings.lineHeight)
  ui.lineValue.textContent = settings.lineHeight.toFixed(1)
  ui.brightRange.value = String(settings.brightness)
  ui.brightValue.textContent = `${settings.brightness}%`
  ui.stage.style.filter = settings.brightness < 100 ? `brightness(${settings.brightness / 100})` : ''

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
$('smaller').addEventListener('click', () => setFont(settings.fontSize - limits.fontSize.step))
$('larger').addEventListener('click', () => setFont(settings.fontSize + limits.fontSize.step))
$('themeQuick').addEventListener('click', () => update({ theme: settings.theme === 'dark' ? 'light' : 'dark' }))

ui.settings.querySelectorAll<HTMLElement>('.choice-row').forEach(row => {
  row.addEventListener('click', e => {
    const button = (e.target as HTMLElement).closest<HTMLButtonElement>('.choice')
    if (!button) return
    const raw = button.dataset.value!
    const value = raw === 'true' ? true : raw === 'false' ? false : raw
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
    remove: removeBook,
    changed: () => library.refresh(),
  }),
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

async function removeBook(record: BookRecord) {
  const ok = await confirmDialog({
    title: 'Remove this book?',
    message: `“${record.title}” will be removed from your library, with its reading position, bookmarks and highlights. The original file on your device isn’t touched.`,
    confirm: 'Remove',
    danger: true,
  })
  if (!ok) return
  await deleteBook(record.id)
  library.refresh()
  toast(`Removed “${record.title}”.`)
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
  if (!record || !blob) {
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
  notes = new BookNotes(next, id, { bookmarkList: $('bookmarkList'), noteList: $('noteList'), bookmarkButton: ui.bookmarkToggle }, goToFromPanel)
  notes.load()
  bookSearch = new BookSearch(next, { status: $('searchStatus'), results: $('searchResults') }, goToFromPanel)
  applyReadingSession()
  updateBook(id, { lastOpenedAt: Date.now() })
  ui.progress.disabled = false

  const count = toc.render(next.book.toc ?? [], href => {
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

// ---------- Navigation ----------

$('prev').addEventListener('click', () => reader?.prev())
$('next').addEventListener('click', () => reader?.next())
document.addEventListener('keydown', e => {
  if ((e.target as HTMLElement).matches('input, textarea')) return
  if (e.key === 'Escape') { setSettingsOpen(false); setSidebar(false); return }
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
  if (ui.app.dataset.view !== 'library') { goToLibrary(); return true }
  return false
})

render()
route()
