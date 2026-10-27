import { clearLibrary, listBooks } from './library/db'
import { limits, type Settings } from './settings'
import { confirmDialog, h } from './ui/dialog'
import { fileSize } from './ui/format'
import { toast } from './ui/toast'
import { haptic, isNativeApp, openExternal } from './app/native'
import { showWelcome, showWhatsNew } from './app/onboarding'

export interface SettingsScreenActions {
  get(): Settings
  update(patch: Partial<Settings>): void
  libraryCleared(): void
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

  render() {
    const inner = h('div', 'settings-inner')
    inner.append(
      this.#section('Appearance', 'System follows your device: Day while it’s light, Night while it’s in dark mode.', [
        this.#choices('Theme', 'theme', [['system', 'System'], ['light', 'Day'], ['sepia', 'Sepia'], ['dark', 'Night'], ['black', 'Black'], ['custom', 'Custom']]),
        this.#customColours(),
      ]),
      this.#section('Reading', 'These are also in the reading settings while a book is open.', [
        this.#choices('Reading mode', 'flow', [['scrolled', 'Scroll'], ['paginated', 'Pages']]),
        this.#choices('Font', 'font', [['serif', 'Serif'], ['sans', 'Sans'], ['original', 'Book’s own']]),
        this.#slider('Font size', 'fontSize', limits.fontSize, v => `${v}%`),
        this.#slider('Weight', 'fontWeight', limits.fontWeight, v => v === 400 ? 'Regular' : v < 400 ? 'Lighter' : 'Bolder'),
        this.#slider('Line spacing', 'lineHeight', limits.lineHeight, v => v.toFixed(1)),
        this.#choices('Margins', 'margins', [['narrow', 'Narrow'], ['normal', 'Normal'], ['wide', 'Wide']]),
        this.#choices('Text alignment', 'align', [['start', 'Left'], ['justify', 'Justified']]),
        this.#choices('Space between paragraphs', 'paragraphSpacing', [[-1, 'Book’s own'], [0, 'None'], [0.6, 'Some'], [1.2, 'More']]),
        this.#choices('First-line indent', 'indent', [[-1, 'Book’s own'], [0, 'None'], [1.5, 'Indent']]),
        this.#choices('Word breaks', 'hyphenate', [[false, 'Whole words'], [true, 'Hyphenate']]),
      ]),
      this.#section('Reading aids', '', [
        this.#slider('Warm light', 'warmth', limits.warmth, v => v ? `${v}%` : 'Off'),
        this.#choices('Reading ruler', 'ruler', [['off', 'Off'], ['line', 'Line'], ['focus', 'Focus']]),
        this.#toggle('Bold word starts', 'Bionic reading: the first letters of each word are bold, to help the eye along the line.', 'bionic'),
        this.#toggle('Clock and battery', 'Shown in the corner while reading with the controls hidden.', 'showClock'),
      ]),
      this.#section('App', '', [
        this.#choices('Text size', 'uiScale', [[90, 'Small'], [100, 'Default'], [115, 'Large'], [130, 'Larger']]),
        ...(isNativeApp ? [
          this.#choices('Screen rotation', 'orientation', [['auto', 'Automatic'], ['portrait', 'Portrait'], ['landscape', 'Landscape']]),
          this.#toggle('Vibration', 'A light tap when you bookmark, highlight or change a setting.', 'haptics'),
        ] : []),
      ]),
      this.#section('Behaviour', '', [
        this.#toggle('Tap the sides to turn pages', 'In page mode, tap the left or right edge of the screen.', 'tapZones'),
        this.#toggle('Scroll on to the next chapter', 'In scroll mode, keep scrolling at the end of a chapter to continue.', 'scrollPastEnd'),
        this.#toggle('Keep the screen on while reading', 'The screen won’t dim or lock while a book is open.', 'keepAwake'),
        ...(isNativeApp ? [this.#toggle('Turn pages with the volume keys', 'Volume down for the next page, volume up for the previous one.', 'volumeKeys')] : []),
      ]),
      this.#storage(),
      this.#about(),
    )
    this.#root.replaceChildren(inner)
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
    return this.#section('Storage', 'Books are stored on this device only. Nothing is uploaded.', [info, clear])
  }

  #about() {
    const section = h('section', 'settings-section about')
    section.append(h('h2', '', 'About'))
    const card = h('div', 'settings-card about-card')

    const head = h('div', 'about-head')
    const logo = h('div', 'logo about-logo', 'q')
    const name = h('div')
    name.append(h('div', 'about-name', 'Quiet'), h('div', 'settings-hint', `Version ${__APP_VERSION__}`))
    head.append(logo, name)

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
    credit('Literata', 'serif reading font', 'SIL Open Font License', 'https://github.com/googlefonts/literata')
    credit('Source Sans 3', 'sans reading font', 'SIL Open Font License', 'https://github.com/adobe-fonts/source-sans')
    credit('Lucide', 'icons', 'ISC License', 'https://lucide.dev')
    credit('Capacitor', 'the Android app', 'MIT License', 'https://capacitorjs.com')

    const links = h('div', 'about-links')
    const whatsNew = h('button', 'link-button', 'What’s new in this version')
    whatsNew.addEventListener('click', () => showWhatsNew())
    const welcome = h('button', 'link-button', 'Show the welcome again')
    welcome.addEventListener('click', () => showWelcome(() => document.getElementById('fileInput')?.click()))
    links.append(whatsNew, welcome)
    card.append(
      head,
      p('A calm, distraction-free place for your books.'),
      links,
      h('h3', 'details-heading', 'Privacy'),
      p('Quiet has no account, no ads and no tracking. Your books, reading positions, bookmarks and notes stay on this device and are never sent anywhere. The only time Quiet uses the internet is when you choose Look up or Translate on selected text. Translate sends that text to Google Translate and shows the answer inside Quiet; Look up opens a Google search in your browser.'),
      h('h3', 'details-heading', 'Formats'),
      p('EPUB, MOBI, AZW3 and AZW (without DRM), FB2, CBZ comics, PDF, TXT and HTML.'),
      h('h3', 'details-heading', 'Made with'),
      credits,
    )
    section.append(card)
    return section
  }
}
