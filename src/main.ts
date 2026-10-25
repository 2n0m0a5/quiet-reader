import './styles/tokens.css'
import './styles/app.css'
import {
  createIcons, AArrowDown, AArrowUp, BookOpen, ChevronDown, ChevronLeft, ChevronRight,
  ChevronUp, Library, List, Moon, Plus, Settings2, Sun, Sunset,
} from 'lucide'
import { Reader } from './reader/reader'
import type { Location } from './reader/foliate'
import { authorString, bookKey, langString, titleFromFileName } from './reader/metadata'
import { TocView } from './ui/toc'
import { clamp, limits, loadSettings, saveSettings, type Settings } from './settings'

createIcons({
  icons: {
    AArrowDown, AArrowUp, BookOpen, ChevronDown, ChevronLeft, ChevronRight,
    ChevronUp, Library, List, Moon, Plus, Settings2, Sun, Sunset,
  },
})

const $ = <T extends HTMLElement = HTMLElement>(id: string) => document.getElementById(id) as T

const ui = {
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
  emptyState: $('emptyState'),
  emptyTitle: $('emptyTitle'),
  emptyText: $('emptyText'),
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

// Settings popover: toggle on the gear, close on outside click or Escape.
ui.settingsToggle.addEventListener('click', e => {
  e.stopPropagation()
  ui.settings.classList.toggle('open')
})
ui.settings.addEventListener('click', e => e.stopPropagation())
document.addEventListener('click', () => ui.settings.classList.remove('open'))

// ---------- Sidebar on small screens ----------

const setSidebar = (open: boolean) => {
  ui.sidebar.classList.toggle('open', open)
  ui.scrim.classList.toggle('open', open)
}
$('tocToggle').addEventListener('click', () => setSidebar(true))
ui.scrim.addEventListener('click', () => setSidebar(false))

// ---------- Opening a book ----------

const chooseFile = () => ui.fileInput.click()
$('openFile').addEventListener('click', chooseFile)
$('emptyOpen').addEventListener('click', chooseFile)
ui.fileInput.addEventListener('change', () => {
  const file = ui.fileInput.files?.[0]
  ui.fileInput.value = ''
  if (file) openBook(file)
})

// Drag and drop a book anywhere on the window (desktop).
document.addEventListener('dragover', e => e.preventDefault())
document.addEventListener('drop', e => {
  e.preventDefault()
  const file = e.dataTransfer?.files?.[0]
  if (file) openBook(file)
})

async function openBook(file: File) {
  const token = ++openToken
  reader?.close()
  reader = null

  const fallbackTitle = titleFromFileName(file.name)
  ui.emptyState.hidden = true
  ui.bookTitle.textContent = fallbackTitle
  ui.bookMeta.textContent = 'Opening book…'
  ui.chapterLabel.textContent = 'Preparing your reading space…'
  ui.cover.innerHTML = coverPlaceholder
  ui.tocCount.textContent = ''
  ui.progress.disabled = true
  setProgress(0)
  toc.showMessage('Loading contents…')

  const next = new Reader(ui.viewer, bookKey(file), { onRelocate })
  try {
    await next.open(file, settings)
  } catch (err) {
    console.error('Could not open book:', err)
    if (token !== openToken) return
    next.close()
    showError(err instanceof Error && err.message.startsWith('PDF')
      ? err.message
      : 'This file may be damaged, protected by DRM, or in a format Quiet doesn’t read yet.')
    return
  }
  if (token !== openToken) { next.close(); return }
  reader = next

  const { book } = next
  const title = langString(book.metadata?.title) || fallbackTitle
  const author = authorString(book.metadata?.author)
  document.title = `${title} · Quiet`
  ui.bookTitle.textContent = title
  ui.bookMeta.textContent = author || 'Unknown author'
  ui.progress.disabled = false

  const count = toc.render(book.toc ?? [], href => {
    reader?.goTo(href)
    setSidebar(false)
  })
  if (!count) toc.showMessage('This book has no table of contents.')
  ui.tocCount.textContent = count ? `(${count})` : ''

  Promise.resolve(book.getCover?.()).then(blob => {
    if (!blob || token !== openToken) return
    const img = document.createElement('img')
    img.alt = ''
    img.src = URL.createObjectURL(blob)
    ui.cover.replaceChildren(img)
  }).catch(() => {})
}

function showError(message: string) {
  ui.emptyState.hidden = false
  ui.emptyTitle.textContent = 'Couldn’t open this book'
  ui.emptyText.textContent = message
  ui.bookTitle.textContent = 'No book open'
  ui.bookMeta.textContent = 'Ready when you are'
  ui.chapterLabel.textContent = 'A little room for a good book.'
  toc.showMessage('Open a book to see its chapters here.')
}

// ---------- Reading progress ----------

function setProgress(fraction: number) {
  ui.progress.value = String(fraction)
  ui.progressText.textContent = `${Math.round(fraction * 100)}%`
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
  if (e.key === 'Escape') { ui.settings.classList.remove('open'); setSidebar(false); return }
  reader?.handleKey(e)
})
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden') reader?.save()
})

render()
