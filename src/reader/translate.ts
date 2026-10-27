// Translate selected text in a window over the page, without leaving Quiet.
// Uses Google Translate's free service, so it needs the internet, and the selected text is sent
// to Google (the About page and the window itself say so).
import { Copy, ExternalLink } from 'lucide'
import { button, h, icon, openDialog } from '../ui/dialog'
import { toast } from '../ui/toast'
import { openExternal } from '../app/native'

const ENDPOINT = 'https://translate.googleapis.com/translate_a/single'
const TARGET_KEY = 'quiet:translate-to'
const MAX_LENGTH = 1500

// Major languages first; the device's language is added if it's not here.
const LANGUAGES = ['en', 'ru', 'uz', 'es', 'de', 'fr', 'pt', 'tr', 'it', 'uk', 'kk', 'pl', 'ar', 'hi', 'zh-CN', 'ja', 'ko']

const names = (() => {
  try {
    const display = new Intl.DisplayNames(['en'], { type: 'language' })
    return (code: string) => display.of(code) ?? code
  } catch {
    return (code: string) => code
  }
})()

interface Result {
  text: string
  source: string                                  // detected language
  dictionary: { pos: string; terms: string[] }[]  // for single words
}

export async function translateText(text: string, target: string): Promise<Result> {
  const params = new URLSearchParams({ client: 'gtx', sl: 'auto', tl: target, dj: '1', q: text.slice(0, MAX_LENGTH) })
  params.append('dt', 't')
  params.append('dt', 'bd')
  const response = await fetch(`${ENDPOINT}?${params}`)
  if (!response.ok) throw new Error(`Translate answered ${response.status}`)
  const data = await response.json() as {
    sentences?: { trans?: string }[]; src?: string; dict?: { pos: string; terms: string[] }[]
  }
  return {
    text: (data.sentences ?? []).map(s => s.trans ?? '').join('').trim(),
    source: data.src ?? '',
    dictionary: (data.dict ?? []).map(d => ({ pos: d.pos, terms: d.terms.slice(0, 8) })),
  }
}

function savedTarget() {
  try {
    const saved = localStorage.getItem(TARGET_KEY)
    if (saved) return saved
  } catch { /* storage unavailable */ }
  const device = (navigator.language || 'en').split('-')[0]
  // Reading in English on an English phone, translations into English would be useless.
  return device === 'en' ? 'ru' : device
}

export function showTranslation(original: string) {
  const text = original.trim().replace(/\s+/g, ' ')
  const source = h('p', 'translate-source', text.length > 300 ? `${text.slice(0, 300)}…` : text)
  const select = h('select', 'translate-select')
  select.setAttribute('aria-label', 'Translate into')
  const target = savedTarget()
  for (const code of LANGUAGES.includes(target) ? LANGUAGES : [target, ...LANGUAGES]) {
    const option = h('option', '', names(code))
    option.value = code
    option.selected = code === target
    select.append(option)
  }
  const from = h('span', 'translate-from', 'Detecting…')
  const langs = h('div', 'translate-langs')
  langs.append(from, h('span', 'translate-arrow', '→'), select)
  const result = h('div', 'translate-result')
  const note = h('p', 'settings-hint translate-note', 'Translated by Google. The selected text is sent to Google over the internet.')
  const actions = h('div', 'dialog-actions')
  const copy = button('', '', async () => {
    try { await navigator.clipboard.writeText(result.dataset.text ?? ''); toast('Copied.') } catch { toast('Couldn’t copy on this device.') }
  })
  copy.append(icon(Copy), h('span', '', 'Copy'))
  const google = button('', '', () => openExternal(`https://translate.google.com/?sl=auto&tl=${select.value}&op=translate&text=${encodeURIComponent(text)}`))
  google.append(icon(ExternalLink), h('span', '', 'Google Translate'))
  actions.append(copy, google)
  openDialog({ title: 'Translate', className: 'dialog-small dialog-translate', content: [source, langs, result, actions, note] })

  let run = 0
  const translate = async () => {
    const id = ++run
    result.replaceChildren(h('p', 'translate-loading', 'Translating…'))
    copy.disabled = true
    try {
      const r = await translateText(text, select.value)
      if (id !== run) return
      from.textContent = r.source ? names(r.source) : 'Unknown language'
      result.dataset.text = r.text
      result.replaceChildren(h('p', 'translate-text', r.text))
      if (r.source && r.source.split('-')[0] === select.value.split('-')[0])
        result.append(h('p', 'settings-hint', `This is already in ${names(select.value)}. Choose another language above.`))
      // A single word: its meanings, by part of speech.
      for (const d of r.dictionary) {
        const row = h('div', 'translate-dict')
        row.append(h('span', 'translate-pos', d.pos), h('span', '', d.terms.join(', ')))
        result.append(row)
      }
      copy.disabled = false
    } catch (err) {
      if (id !== run) return
      console.warn('Translate failed:', err)
      from.textContent = ''
      result.replaceChildren(h('p', 'translate-error', navigator.onLine === false
        ? 'You’re offline. Translation needs the internet.'
        : 'Couldn’t translate right now. Try again, or open Google Translate.'))
    }
  }
  select.addEventListener('change', () => {
    try { localStorage.setItem(TARGET_KEY, select.value) } catch { /* storage unavailable */ }
    translate()
  })
  translate()
}
