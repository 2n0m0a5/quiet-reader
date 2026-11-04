import { clearLibrary, listBooks } from './library/db'
import { lastBackup, pickBackupFile, readBackup, restoreBackup, saveBackup } from './library/backup'
import { phoneWantsLessMotion } from './ui/motion'
import { limits, type Settings } from './settings'
import { confirmDialog, h, icon } from './ui/dialog'
import { fileSize, timeAgo } from './ui/format'
import { toast } from './ui/toast'
import { haptic, isNativeApp, openExternal } from './app/native'
import { showWhatsNew } from './app/onboarding'
import { showReport } from './app/report'
import { addFolder, listFolders, removeFolder, scanFolders } from './library/folders'
import { ChevronLeft, ChevronRight, X } from 'lucide'
import { logo as quietLogo } from './ui/brand'
import { fillRange } from './ui/range'
import { familyStack, loadedFamilies, loadedRecords, removeFamily } from './reader/custom-fonts'

export interface SettingsScreenActions {
  get(): Settings
  update(patch: Partial<Settings>): void
  addFonts(): void
  libraryCleared(): void
  customizeBar(): void
  tour(): void
}

type Page = 'interface' | 'reader' | 'library' | 'files' | 'privacy' | 'about'
const PAGES: Record<Page, { title: string; hint: string }> = {
  interface: { title: 'Interface', hint: 'Day and night, app text size, motion, vibration' },
  reader: { title: 'Reader', hint: 'Scroll or pages, page turn, fonts, spacing, the reader bar' },
  library: { title: 'Library', hint: 'Book folders Quiet watches for new books' },
  files: { title: 'Files & storage', hint: 'Back up and restore, space used, delete everything' },
  privacy: { title: 'Privacy & translation', hint: 'What leaves your phone: only text you translate' },
  about: { title: 'About Quiet', hint: '' },
}

type Choice<K extends keyof Settings> = [Settings[K], string][]


// The Settings screen: reading defaults, behaviour, storage, and About.
export class SettingsScreen {
  #root: HTMLElement
  #actions: SettingsScreenActions

  constructor(root: HTMLElement, actions: SettingsScreenActions) {
    this.#root = root
    this.#actions = actions
  }

  // Settings is a home list of sections; each opens on its own page. About has its own page too.
  #page: Page | null = null

  open(page: Page | null) {
    this.#page = page
    this.render()
    this.#root.scrollTop = 0
  }

  // Android's back button and Escape: from a section back to the list.
  back() {
    if (!this.#page) return false
    this.open(null)
    return true
  }

  render() {
    const inner = h('div', 'settings-inner')
    if (!this.#page) inner.append(...this.#home())
    else {
      const bar = h('div', 'settings-pagebar')
      const back = h('button', 'btn icon', '')
      back.setAttribute('aria-label', 'All settings')
      back.append(icon(ChevronLeft))
      back.addEventListener('click', () => this.open(null))
      bar.append(back, h('h1', 'settings-pagetitle', PAGES[this.#page].title))
      inner.append(bar, ...this.#sections(this.#page))
    }
    this.#root.replaceChildren(inner)
  }

  #home() {
    const list = h('div', 'settings-card settings-home')
    ;(Object.keys(PAGES) as Page[]).filter(p => p !== 'about').forEach((page, i) => {
      const row = h('button', 'settings-home-row')
      row.dataset.page = page
      const text = h('div')
      text.append(h('div', 'settings-label', PAGES[page].title), h('div', 'settings-hint', PAGES[page].hint))
      row.append(h('span', 'home-num', String(i + 1).padStart(2, '0')), text, icon(ChevronRight))
      row.addEventListener('click', () => this.open(page))
      list.append(row)
    })
    const out: HTMLElement[] = [h('h1', 'settings-hometitle', 'Settings'), list]
    // A reminder when the library has never been backed up, or not for two weeks.
    const last = lastBackup()
    if (!last || Date.now() - last > 14 * 86_400_000) {
      const remind = h('div', 'settings-card backup-reminder')
      const text = h('div')
      text.append(h('div', 'settings-label', last ? `No backup in ${Math.floor((Date.now() - last) / 86_400_000)} days` : 'No backup yet'),
        h('div', 'settings-hint', 'Save your library to one file you keep.'))
      const go = h('button', 'btn small primary', 'Back up')
      go.addEventListener('click', () => this.open('files'))
      remind.append(h('span', 'led'), text, go)
      listBooks().then(books => { if (!books.length) remind.remove() })
      out.push(remind)
    }
    const more = h('div', 'settings-card settings-home')
    const row = (label: string, hint: string, run: () => void, mark?: HTMLElement) => {
      const b = h('button', 'settings-home-row')
      const text = h('div')
      text.append(h('div', 'settings-label', label), h('div', 'settings-hint', hint))
      b.append(mark ?? h('span', 'home-num', ''), text, icon(ChevronRight))
      b.addEventListener('click', run)
      more.append(b)
    }
    row('About Quiet', 'Version, what’s new, the tour, your data', () => this.open('about'), quietLogo('home-logo'))
    row('Report a problem', 'You see the report and send it yourself', () => this.#report())
    out.push(more)
    return out
  }

  #sections(page: Page): HTMLElement[] {
    switch (page) {
      case 'interface': return [
        this.#section('Theme', 'System follows your phone: Day while it’s light, Night while it’s in dark mode.', [
          this.#choices('Theme', 'theme', [['system', 'System'], ['light', 'Day'], ['dark', 'Night'], ['black', 'Black'], ['custom', 'Custom']]),
          this.#customColours(),
        ]),
        this.#section('The app', '', [
          this.#choices('Text size', 'uiScale', [[90, 'Small'], [100, 'Default'], [115, 'Large'], [130, 'Larger']]),
          this.#choices('Motion', 'motion', [['system', 'Follow phone'], ['full', 'Full'], ['reduced', 'Reduced']]),
          h('div', 'settings-hint motion-hint', phoneWantsLessMotion()
            ? 'Your phone is asking apps for less motion right now (Battery saver or “Remove animations”). Choose Full to keep Quiet’s animations anyway.'
            : 'Your phone isn’t asking for less motion, so Follow phone means full motion.'),
          ...(isNativeApp ? [
            this.#choices('Screen rotation', 'orientation', [['auto', 'Automatic'], ['portrait', 'Portrait'], ['landscape', 'Landscape']]),
            this.#toggle('Vibration', 'A light tap when you bookmark, highlight or change a setting.', 'haptics'),
          ] : []),
        ]),
      ]
      case 'reader': {
        const bar = h('div', 'settings-row')
        const text = h('div')
        text.append(h('div', 'settings-label', 'Reader bar'), h('div', 'settings-hint', 'Choose the four keys at the bottom of the page. More always has the rest.'))
        const edit = h('button', 'btn small', 'Choose keys')
        edit.addEventListener('click', () => this.#actions.customizeBar())
        bar.append(text, edit)
        return [
          this.#section('Page', 'These are also under More while a book is open.', [
            this.#choices('Reading mode', 'flow', [['scrolled', 'Scroll'], ['paginated', 'Pages']]),
            this.#choices('Page turn (in page mode)', 'pageTurn', [['slide', 'Slide'], ['curl', 'Curl'], ['none', 'None']]),
            this.#choices('Margins', 'margins', [['narrow', 'Narrow'], ['normal', 'Normal'], ['wide', 'Wide']]),
            bar,
          ]),
          this.#section('Text', '', [
            this.#choices('Font', 'font', [['serif', 'Serif'], ['sans', 'Sans'], ['original', 'Book’s own'],
              ...loadedFamilies().map(f => [`custom:${f}`, f] as [Settings['font'], string])]),
            this.#slider('Font size', 'fontSize', limits.fontSize, v => `${v}%`),
            this.#slider('Weight', 'fontWeight', limits.fontWeight, v => v === 400 ? 'Regular' : v < 400 ? 'Lighter' : 'Bolder'),
            this.#slider('Line spacing', 'lineHeight', limits.lineHeight, v => v.toFixed(1)),
            this.#choices('Text alignment', 'align', [['start', 'Left'], ['justify', 'Justified']]),
            this.#choices('Space between paragraphs', 'paragraphSpacing', [[-1, 'Book’s own'], [0, 'None'], [0.6, 'Some'], [1.2, 'More']]),
            this.#choices('First-line indent', 'indent', [[-1, 'Book’s own'], [0, 'None'], [1.5, 'Indent']]),
            this.#choices('Word breaks', 'hyphenate', [[false, 'Whole words'], [true, 'Hyphenate']]),
          ]),
          this.#fonts(),
          this.#section('Reading aids', '', [
            this.#slider('Warm light', 'warmth', limits.warmth, v => v ? `${v}%` : 'Off'),
            this.#choices('Reading ruler', 'ruler', [['off', 'Off'], ['line', 'Line'], ['focus', 'Focus']]),
            this.#toggle('Bold word starts', 'Bionic reading: the first letters of each word are bold, to help the eye along the line.', 'bionic'),
            this.#toggle('Clock and battery', 'Shown in the corner while reading with the controls hidden.', 'showClock'),
          ]),
          this.#section('Turning and taps', '', [
            this.#toggle('Tap the sides to turn pages', 'In page mode, tap the left or right edge of the screen.', 'tapZones'),
            this.#toggle('Scroll on to the next chapter', 'In scroll mode, keep scrolling at the end of a chapter to continue.', 'scrollPastEnd'),
            this.#toggle('Keep the screen on while reading', 'The screen won’t dim or lock while a book is open.', 'keepAwake'),
            ...(isNativeApp ? [this.#toggle('Turn pages with the volume keys', 'Volume down for the next page, volume up for the previous one.', 'volumeKeys')] : []),
          ]),
        ]
      }
      case 'library': return [this.#folders()]
      case 'files': return [this.#storage()]
      case 'privacy': {
        const p = (text: string) => h('p', 'about-text', text)
        const card = h('div', 'settings-card about-card')
        card.append(
          p('Quiet has no account, no ads and no tracking. Your books, reading places, bookmarks, notes and reading time stay on this phone and are never sent anywhere.'),
          p('The only time Quiet uses the internet is when you choose Translate or Look up on selected text. Translate sends that text to Google Translate (or to MyMemory, when Google is busy) and shows the answer inside Quiet; Look up opens a search in your browser.'),
          p('Report a problem never sends anything by itself: you see the report and send it yourself.'),
          p('Quiet has no AI features.'),
        )
        const section = h('section', 'settings-section')
        section.append(card)
        return [section]
      }
      case 'about': return [this.#about()]
    }
  }

  async #report() {
    const books = await listBooks()
    const formats = [...new Set(books.map(b => b.format ?? '?'))].join(', ')
    const s = this.#actions.get()
    showReport({
      Books: `${books.length}${formats ? ` (${formats})` : ''}`,
      Settings: `theme ${s.theme}, ${s.flow}, page turn ${s.pageTurn}, font ${s.font.startsWith('custom:') ? 'custom' : s.font} ${s.fontSize}%, motion ${s.motion}`,
    })
  }

  // ---------- Your fonts ----------

  #fonts() {
    const rows: HTMLElement[] = []
    const records = loadedRecords()
    for (const family of loadedFamilies()) {
      const row = h('div', 'settings-row font-row')
      const text = h('div', 'font-text')
      const sample = h('div', 'font-sample', 'It was a quiet morning.')
      sample.style.fontFamily = familyStack(family)
      const styles = records.filter(r => r.family === family).sort((a, b) => a.weight - b.weight || a.style.localeCompare(b.style)).map(r => `${r.weight >= 600 ? 'Bold' : r.weight <= 300 ? 'Light' : 'Regular'}${r.style === 'italic' ? ' italic' : ''}`)
      text.append(h('div', 'settings-label', family), sample, h('div', 'settings-hint', [...new Set(styles)].join(' · ')))
      const remove = h('button', 'btn small icon', '')
      remove.append(icon(X))
      remove.setAttribute('aria-label', `Remove ${family}`)
      remove.title = `Remove ${family}`
      remove.addEventListener('click', async () => {
        if (!await confirmDialog({ title: `Remove ${family}?`, message: 'Books shown in it go back to the style’s own font.', confirm: 'Remove', danger: true })) return
        await removeFamily(family)
        toast(`${family} removed.`)
      })
      row.append(text, remove)
      rows.push(row)
    }
    const add = h('div', 'settings-row')
    const button = h('button', 'btn small primary', 'Add fonts')
    button.addEventListener('click', () => this.#actions.addFonts())
    add.append(h('div', 'settings-label', rows.length ? 'Add more' : 'No fonts added yet'), button)
    rows.push(add)
    return this.#section('Your fonts', 'Read in any font you like: add TTF, OTF, WOFF or WOFF2 files. Add the bold and italic files too, and they’re used for bold and italic text. Fonts stay on this device.', rows)
  }

  // ---------- Building blocks ----------

  #section(title: string, hint: string, rows: HTMLElement[]) {
    const section = h('section', 'settings-section')
    section.append(h('h2', '', title))
    if (hint) section.append(h('p', 'settings-hint', hint))
    const card = h('div', 'settings-card')
    card.append(...rows)
    section.append(card)
    return section
  }

  #choices<K extends keyof Settings>(label: string, key: K, options: Choice<K>) {
    const row = h('div', 'settings-row stacked')
    const group = h('div', `choice-row${options.length > 4 ? ' many' : ''}`)
    for (const [value, text] of options) {
      const b = h('button', 'choice', text)
      b.dataset.key = key
      b.dataset.value = String(value)
      b.addEventListener('click', () => {
        this.#actions.update({ [key]: value } as Partial<Settings>)
        haptic()
        this.#refresh()
      })
      group.append(b)
    }
    row.append(h('div', 'settings-label', label), group)
    this.#refresh(row)
    return row
  }

  // Marks the chosen option in every row, after a change (one setting can change another).
  #refresh(root: HTMLElement = this.#root) {
    const settings = this.#actions.get()
    root.querySelectorAll<HTMLElement>('.choice[data-key]').forEach(c =>
      c.classList.toggle('selected', String(settings[c.dataset.key as keyof Settings]) === c.dataset.value))
  }

  // Two colour pickers for the Custom theme. Picking a colour switches to it.
  #customColours() {
    const row = h('div', 'settings-row colour-row')
    const settings = this.#actions.get()
    const picker = (label: string, key: 'customBg' | 'customText') => {
      const field = h('label', 'colour-field')
      const input = h('input')
      input.type = 'color'
      input.value = settings[key]
      input.addEventListener('input', () => {
        this.#actions.update({ [key]: input.value, theme: 'custom' })
        this.#refresh()
        sample.style.setProperty(key === 'customBg' ? '--swatch' : '--ink', input.value)
      })
      field.append(input, h('span', '', label))
      return field
    }
    const sample = h('div', 'colour-sample', 'Aa')
    sample.style.setProperty('--swatch', settings.customBg)
    sample.style.setProperty('--ink', settings.customText)
    const text = h('div')
    text.append(h('div', 'settings-label', 'Custom colours'), h('div', 'settings-hint', 'Your own page and text colours.'))
    const pickers = h('div', 'colour-pickers')
    pickers.append(sample, picker('Page', 'customBg'), picker('Text', 'customText'))
    row.append(text, pickers)
    return row
  }

  #slider(label: string, key: 'fontSize' | 'lineHeight' | 'fontWeight' | 'warmth', range: { min: number; max: number; step: number }, format: (v: number) => string) {
    const row = h('div', 'settings-row stacked')
    const head = h('div', 'setting-head')
    const value = h('span', '', format(this.#actions.get()[key]))
    head.append(h('span', 'settings-label', label), value)
    const input = h('input')
    input.type = 'range'
    input.setAttribute('aria-label', label)
    input.min = String(range.min)
    input.max = String(range.max)
    input.step = String(range.step)
    input.value = String(this.#actions.get()[key])
    fillRange(input)
    input.addEventListener('input', () => {
      this.#actions.update({ [key]: Number(input.value) })
      value.textContent = format(Number(input.value))
    })
    row.append(head, input)
    return row
  }

  #toggle(label: string, hint: string, key: 'tapZones' | 'scrollPastEnd' | 'keepAwake' | 'volumeKeys' | 'bionic' | 'showClock' | 'haptics') {
    const row = h('label', 'settings-row toggle-row')
    const text = h('div')
    text.append(h('div', 'settings-label', label), h('div', 'settings-hint', hint))
    const input = h('input', 'switch')
    input.type = 'checkbox'
    input.checked = this.#actions.get()[key]
    // (Turning Vibration on gives a small vibration, so you know it works.)
    input.addEventListener('change', () => { this.#actions.update({ [key]: input.checked }); haptic() })
    row.append(text, input)
    return row
  }

  // Book folders: the folders Quiet adds books from (and, in the app, checks for new ones).
  #folders() {
    const rows: HTMLElement[] = []
    for (const folder of isNativeApp ? listFolders() : []) {
      const row = h('div', 'settings-row folder-row')
      const text = h('div')
      text.append(h('div', 'settings-label', folder.name), h('div', `settings-hint${folder.error ? ' danger-text' : ''}`, folder.error
        ?? `${folder.count} ${folder.count === 1 ? 'book' : 'books'}${folder.lastScan ? ` · checked ${timeAgo(folder.lastScan)}` : ''}`))
      const tools = h('div', 'folder-tools')
      const check = h('button', 'btn small', 'Check now')
      check.addEventListener('click', async () => {
        check.disabled = true
        await scanFolders(() => this.#actions.libraryCleared(), { only: folder.uri, announce: true })
        this.render()
      })
      const remove = h('button', 'btn icon small')
      remove.title = `Stop using “${folder.name}”`
      remove.setAttribute('aria-label', remove.title)
      remove.append(icon(X))
      remove.addEventListener('click', async () => {
        const ok = await confirmDialog({
          title: `Stop using “${folder.name}”?`, confirm: 'Stop using it',
          message: 'Quiet won’t look in this folder for new books any more. Books already added stay in your library, and the files aren’t touched.',
        })
        if (!ok) return
        removeFolder(folder.uri)
        this.render()
      })
      tools.append(check, remove)
      row.append(text, tools)
      rows.push(row)
    }
    const add = h('div', 'settings-row')
    const addText = h('div')
    addText.append(h('div', 'settings-label', rows.length ? 'Add another folder' : 'Add a book folder'),
      h('div', 'settings-hint', 'Put your books in a folder first, for example “Books” in Downloads. Folders inside it count too.'))
    const addButton = h('button', 'btn small primary', 'Add')
    addButton.addEventListener('click', () => addFolder(() => { this.#actions.libraryCleared(); this.render() }))
    add.append(addText, addButton)
    rows.push(add)
    return this.#section('Book folders', isNativeApp
      ? 'Quiet adds every book in these folders, and checks them for new books each time it opens.'
      : 'Add every book in a folder at once. The Android app also keeps checking the folder for new books.', rows)
  }

  #storage() {
    const usage = h('div', 'settings-hint', 'Counting…')
    Promise.all([listBooks(), navigator.storage?.estimate?.().catch(() => null)]).then(([books, estimate]) => {
      const bytes = books.reduce((sum, b) => sum + b.size, 0)
      usage.textContent = `${books.length} ${books.length === 1 ? 'book' : 'books'} · ${fileSize(bytes)} of book files`
        + (estimate?.quota ? ` · ${fileSize(estimate.quota - (estimate.usage ?? 0))} free for Quiet` : '')
    })
    const info = h('div', 'settings-row')
    const text = h('div')
    text.append(h('div', 'settings-label', 'Your library'), usage)
    info.append(text)

    // Backup and restore (library/backup.ts).
    const last = lastBackup()
    const backupRow = h('div', 'settings-row')
    const backupText = h('div')
    const backupHint = h('div', 'settings-hint', last
      ? `Everything in one file you keep: books, places, notes, quotes, reading time, fonts and settings. Last backup: ${new Date(last).toLocaleDateString()}.`
      : 'Everything in one file you keep: books, places, notes, quotes, reading time, fonts and settings. No backup yet.')
    backupText.append(h('div', 'settings-label', 'Back up your library'), backupHint)
    const backupButton = h('button', 'btn small', 'Back up')
    backupButton.addEventListener('click', async () => {
      backupButton.setAttribute('disabled', '')
      try {
        const { saved, books } = await saveBackup(__APP_VERSION__, step => toast(step, 60_000))
        toast(saved ? `Backup saved: ${books} ${books === 1 ? 'book' : 'books'} and everything with them.` : 'Backup cancelled.', 5000)
        if (saved) this.render()
      } catch (err) {
        console.error('Backup failed:', err)
        toast('Couldn’t make the backup. Is there enough free space?', 6000)
      } finally {
        backupButton.removeAttribute('disabled')
      }
    })
    backupRow.append(backupText, backupButton)

    const restoreRow = h('div', 'settings-row')
    const restoreText = h('div')
    restoreText.append(h('div', 'settings-label', 'Restore from a backup'),
      h('div', 'settings-hint', 'Adds what the backup has and your library doesn’t. Nothing is deleted.'))
    const restoreButton = h('button', 'btn small', 'Restore')
    restoreButton.addEventListener('click', async () => {
      const file = await pickBackupFile()
      if (!file) return
      let summary
      try { summary = await readBackup(file) } catch (err) { return toast((err as Error).message || 'Couldn’t read that file.', 6000) }
      const ok = await confirmDialog({
        title: 'Restore this backup?',
        message: `From ${new Date(summary.createdAt).toLocaleDateString()}: ${summary.books} ${summary.books === 1 ? 'book' : 'books'} and ${summary.notes} highlights. Books and notes you don’t have yet are added; nothing is deleted.`,
        confirm: 'Restore',
      })
      if (!ok) return
      try {
        const r = await restoreBackup(summary, step => toast(step, 60_000))
        toast(`Restored: ${r.added} new ${r.added === 1 ? 'book' : 'books'}, ${r.updated} reading ${r.updated === 1 ? 'place' : 'places'} updated, ${r.notes} notes.`, 7000)
        if (r.settings) setTimeout(() => location.reload(), 1500)
        else { this.#actions.libraryCleared(); this.render() }
      } catch (err) {
        console.error('Restore failed:', err)
        toast('Couldn’t restore the backup.', 6000)
      }
    })
    restoreRow.append(restoreText, restoreButton)

    const clear = h('div', 'settings-row')
    const clearText = h('div')
    clearText.append(h('div', 'settings-label', 'Delete everything'),
      h('div', 'settings-hint', 'Removes all books, collections, bookmarks and highlights from Quiet. The original files on your device aren’t touched.'))
    const button = h('button', 'btn small danger', 'Delete')
    button.addEventListener('click', async () => {
      const ok = await confirmDialog({
        title: 'Delete your whole library?',
        message: 'Every book, collection, bookmark and highlight will be removed from Quiet. This can’t be undone.',
        confirm: 'Delete everything', danger: true,
      })
      if (!ok) return
      await clearLibrary()
      toast('Your library is empty.')
      this.#actions.libraryCleared()
      this.render()
    })
    clear.append(clearText, button)
    return this.#section('Storage', 'Books are stored on this device only. Nothing is uploaded.', [info, backupRow, restoreRow, clear])
  }

  #about() {
    const section = h('section', 'settings-section about')
    const card = h('div', 'settings-card about-card')
    const head = h('div', 'about-head')
    const name = h('div')
    name.append(h('div', 'about-name', 'quiet'), h('div', 'settings-hint about-version', `Version ${__APP_VERSION__}`), h('div', 'about-line', 'A reader with nothing extra.'))
    head.append(quietLogo('about-logo'), name)

    const links = h('div', 'about-buttons')
    const add = (label: string, run: () => void, primary = false) => {
      const b = h('button', `btn${primary ? ' primary' : ''}`, label)
      b.addEventListener('click', run)
      links.append(b)
    }
    add('Take the tour', () => this.#actions.tour(), true)
    add('What’s new', () => showWhatsNew('0.3.4'))
    add('Report a problem', () => this.#report())

    // Your data, all on this phone.
    const data = h('dl', 'about-data')
    const fact = (term: string, value: string) => { data.append(h('dt', '', term), h('dd', '', value)) }
    Promise.all([listBooks(), navigator.storage?.estimate?.().catch(() => null)]).then(([books, estimate]) => {
      fact('Books', String(books.length))
      fact('Space used', estimate?.usage ? fileSize(estimate.usage) : fileSize(books.reduce((n, b) => n + b.size, 0)))
      const last = lastBackup()
      fact('Last backup', last ? new Date(last).toLocaleDateString() : 'Never')
    })

    const promises = h('ul', 'about-promises')
    for (const line of ['No account, no ads, no tracking.', 'Books and notes never leave this phone. Only text you choose to translate is sent.', 'No AI features.'])
      promises.append(h('li', '', line))

    const p = (text: string) => h('p', 'about-text', text)
    const credits = h('ul', 'about-credits')
    const credit = (name: string, what: string, license: string, url: string) => {
      const li = h('li')
      const link = h('button', 'link-button', name)
      link.addEventListener('click', () => openExternal(url))
      li.append(link, ` — ${what}, ${license}`)
      credits.append(li)
    }
    credit('foliate-js', 'the e-book engine', 'MIT License', 'https://github.com/johnfactotum/foliate-js')
    credit('PDF.js', 'PDF support, by Mozilla', 'Apache License 2.0', 'https://mozilla.github.io/pdf.js/')
    credit('libarchive.js', 'CBR and 7z comics', 'MIT License', 'https://github.com/nika-begiashvili/libarchivejs')
    credit('Archivo, DM Mono, Crimson Pro, Literata, Source Sans 3', 'fonts', 'SIL Open Font License', 'https://fonts.google.com')
    credit('Lucide', 'icons', 'ISC License', 'https://lucide.dev')
    credit('Capacitor', 'the Android app', 'MIT License', 'https://capacitorjs.com')

    card.append(
      head, links,
      h('h3', 'details-heading', 'Your data · on this phone only'), data,
      h('h3', 'details-heading', 'Promises'), promises,
      h('h3', 'details-heading', 'Formats'),
      p('EPUB, MOBI, AZW3 and AZW (without DRM), FB2, PDF, Word (DOCX), OpenDocument (ODT), RTF, Markdown, TXT, HTML, Palm (PDB) and comics (CBZ, CBR, CB7, CBT).'),
      h('h3', 'details-heading', 'Made with'), credits,
      h('p', 'about-made', 'made by extrasolarions on howling dark'),
    )
    section.append(card)
    return section
  }
}
