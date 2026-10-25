import '@fontsource-variable/literata'
import '@fontsource-variable/source-sans-3'
import './styles/tokens.css'
import './styles/app.css'
import {
  createIcons, AArrowDown, AArrowUp, AlignJustify, AlignLeft, ArrowLeft, BookOpen, ChevronDown,
  ChevronLeft, ChevronRight, ChevronUp, Download, Library, List, Moon, Plus, Settings2, Sun, Sunset,
} from 'lucide'
import { Reader, type TapZone } from './reader/reader'
import type { Location } from './reader/foliate'
import { TocView } from './ui/toc'
import { toast } from './ui/toast'
import { clamp, limits, loadSettings, saveSettings, type Settings } from './settings'
import { deleteBook, getBook, getFile, updateBook, type BookRecord } from './library/db'
import { asFile, importBook, ImportError } from './library/import'
import { LibraryView } from './library/library-view'
import { setUpInstallButton, setUpOffline } from './app/install'
import { isNativeApp, onBackButton, setSystemBarsHidden, setSystemBarsTheme } from './app/native'

createIcons({
  icons: {
    AArrowDown, AArrowUp, AlignJustify, AlignLeft, ArrowLeft, BookOpen, ChevronDown,
    ChevronLeft, ChevronRight, ChevronUp, Download, Library, List, Moon, Plus, Settings2, Sun, Sunset,
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
  tocCount: $('tocCount'),
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
}

const toc = new TocView($('toc'))
const coverPlaceholder = ui.cover.innerHTML
let settings = loadSettings()
let reader: Reader | null = null
let coverUrl = ''
let openToken = 0

// ---------- Settings ----------

function update(patch: Partial<Settings>) {
  settings = { ...settings, ...patch }
  saveSettings(settings)
  render()
  reader?.apply(settings)
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

  document.querySelectorAll<HTMLElement>('.choice-row').forEach(row => {
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

document.querySelectorAll<HTMLElement>('.choice-row').forEach(row => {
  row.addEventListener('click', e => {
    const button = (e.target as HTMLElement).closest<HTMLButtonElement>('.choice')
    if (button) update({ [row.dataset.setting!]: button.dataset.value } as Partial<Settings>)
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
  const paged = settings.flow === 'paginated' || reader?.view.isFixedLayout
  if (paged && zone !== 'center') {
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
$('tocToggle').addEventListener('click', () => setSidebar(true))
ui.scrim.addEventListener('click', () => setSidebar(false))


// ---------- Screens ----------
// "#/book/<id>" shows that book; anything else shows the library.
// Using the address bar means the browser's (and Android's) back button returns to the library.

const library = new LibraryView($('library'), {
  open: openBook,
  add: chooseFiles,
  remove: removeBook,
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
  else showLibrary()
}

window.addEventListener('hashchange', e => {
  // Remember that this book was opened from the library (see goToLibrary).
  if (/#\/book\//.test(location.hash) && !/#\/book\//.test(new URL(e.oldURL).hash))
    history.replaceState({ fromLibrary: true }, '')
  route()
})

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
  if (!confirm(`Remove “${record.title}” from your library?\n\nThe book file and your reading position will be deleted from Quiet.`)) return
  await deleteBook(record.id)
  library.refresh()
  toast(`Removed “${record.title}”.`)
}

// ---------- Reading ----------

function closeReader() {
  openToken++
  reader?.close()
  reader = null
  setSidebar(false)
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
  ui.tocCount.textContent = ''
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
    onUserMove: () => setChrome(false),
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
  updateBook(id, { lastOpenedAt: Date.now() })
  ui.progress.disabled = false

  const count = toc.render(next.book.toc ?? [], href => {
    reader?.goTo(href)
    setSidebar(false)
  })
  if (!count) toc.showMessage('This book has no table of contents.')
  ui.tocCount.textContent = count ? `(${count})` : ''
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
  const minutes = location.time?.section
  ui.progress.title = minutes != null
    ? `${Math.round(minutes)} min left in this chapter`
    : ''
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

onBackButton(() => {
  if (ui.settings.classList.contains('open')) { setSettingsOpen(false); return true }
  if (ui.sidebar.classList.contains('open')) { setSidebar(false); return true }
  if (ui.app.dataset.view === 'reader') { goToLibrary(); return true }
  return false
})

render()
route()
