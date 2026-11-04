// Report a problem (roadmap #66): a short technical report the reader sees in full and sends
// themselves, by email or Telegram. Nothing is sent by Quiet, and there are no servers: the
// privacy promise holds. The last few errors are kept in memory only (never stored).
import { button, h, openDialog } from '../ui/dialog'
import { toast } from '../ui/toast'
import { isNativeApp, openExternal } from './native'
import { phoneWantsLessMotion, reducedMotion } from '../ui/motion'

const errors: string[] = []
const keep = (text: string) => { errors.push(`${new Date().toISOString().slice(11, 19)} ${text}`.slice(0, 300)); if (errors.length > 15) errors.shift() }
window.addEventListener('error', e => keep(`${e.message} (${(e.filename || '').split('/').pop()}:${e.lineno})`))
window.addEventListener('unhandledrejection', e => keep(`Unhandled: ${String((e.reason as Error)?.message ?? e.reason)}`))

export function buildReport(extra: Record<string, string | number>) {
  const ua = navigator.userAgent
  const chrome = ua.match(/Chrome\/([\d.]+)/)?.[1]
  const lines = [
    `Quiet ${__APP_VERSION__} (${isNativeApp ? 'Android app' : 'website'})`,
    `Browser engine: ${chrome ? `Chrome ${chrome}` : ua}`,
    `Screen: ${screen.width}×${screen.height}, window ${innerWidth}×${innerHeight}, pixel ratio ${devicePixelRatio}`,
    `Motion: ${reducedMotion.matches ? 'reduced' : 'full'}${phoneWantsLessMotion() ? ' (the phone asks for less motion)' : ''}`,
    `View Transitions: ${'startViewTransition' in document ? 'yes' : 'no'}`,
    ...Object.entries(extra).map(([k, v]) => `${k}: ${v}`),
    '',
    errors.length ? `Recent errors:\n${errors.join('\n')}` : 'No errors since Quiet opened.',
  ]
  return lines.join('\n')
}

export function showReport(extra: Record<string, string | number>) {
  const what = h('textarea', 'report-what') as HTMLTextAreaElement
  what.placeholder = 'What happened? What did you do just before? (optional)'
  what.rows = 3
  const report = h('pre', 'report-text', buildReport(extra))
  const text = () => `${what.value.trim() ? `${what.value.trim()}\n\n` : ''}${report.textContent}`
  const actions = h('div', 'dialog-actions report-actions')
  const d = openDialog({
    title: 'Report a problem', className: 'dialog-details',
    content: [
      h('p', 'dialog-text', 'This is everything the report holds. Nothing is sent until you send it yourself. It has no books, notes or personal details.'),
      what, report, actions,
    ],
  })
  actions.append(
    button('Copy', '', async () => { try { await navigator.clipboard.writeText(text()); toast('Copied.') } catch { toast('Couldn’t copy.') } }),
    button('Telegram', '', () => openExternal(`https://t.me/share/url?url=${encodeURIComponent('Quiet problem report')}&text=${encodeURIComponent(text())}`)),
    button('Email', 'primary', () => { openExternal(`mailto:?subject=${encodeURIComponent(`Quiet ${__APP_VERSION__}: a problem`)}&body=${encodeURIComponent(text())}`); d.close() }),
  )
}
