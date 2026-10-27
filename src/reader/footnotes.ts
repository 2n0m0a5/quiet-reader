// Footnotes open in a small window over the page, instead of jumping to the end of the book.
import { FootnoteHandler } from '../../vendor/foliate-js/footnotes.js'
import type { FoliateView } from './foliate'
import type { Settings } from '../settings'
import { bookCSS } from './book-css'
import { button, h, openDialog, type DialogHandle } from '../ui/dialog'

const TITLES: Record<string, string> = {
  footnote: 'Footnote', endnote: 'Note', note: 'Note', biblioentry: 'Reference', definition: 'Definition',
}

// Call from the reader's link event. Returns true if the link was a footnote and is being shown.
export function showFootnote(
  book: unknown,
  e: CustomEvent<{ a: HTMLAnchorElement; href: string }>,
  settings: Settings,
  goTo: (href: string) => void,
): boolean {
  const handler = new FootnoteHandler()
  let dialog: DialogHandle | null = null
  const frame = h('div', 'footnote-frame')
  const title = h('h2', 'dialog-title', 'Footnote')
  const actions = h('div', 'dialog-actions')

  handler.addEventListener('before-render', ev => {
    const view = (ev as CustomEvent<{ view: FoliateView }>).detail.view
    // A link inside the footnote (often "back"): close it and follow the link in the book.
    view.addEventListener('link', le => {
      le.preventDefault()
      dialog?.close()
      goTo((le as CustomEvent<{ href: string }>).detail.href)
    })
    frame.append(view)
    dialog = openDialog({ className: 'dialog-small dialog-footnote', content: [title, frame, actions], onClose: () => view.close() })
    view.renderer.setAttribute('flow', 'scrolled')
    view.renderer.setAttribute('margin', '0px')
    view.renderer.setAttribute('gap', '0%')
    view.renderer.setStyles(bookCSS({ ...settings, bionic: false }) + 'html, body { background: transparent !important; } body { margin: 0 !important; }')
  })
  handler.addEventListener('render', ev => {
    const { view, type, href } = (ev as CustomEvent<{ view: FoliateView; type: string | null; href: string }>).detail
    title.textContent = TITLES[type ?? ''] ?? 'Note'
    actions.replaceChildren(button('Go to note', '', () => { dialog?.close(); goTo(href) }))
    // Fit the window to the note, up to half the screen, once the note has been laid out.
    let tries = 0
    const fit = () => {
      const doc = view.renderer.getContents?.()[0]?.doc
      const height = doc?.body?.getBoundingClientRect().height ?? 0
      if (height < 10 && tries++ < 30) return requestAnimationFrame(fit)
      frame.style.height = `${Math.min(Math.max(height, 40) + 12, window.innerHeight * 0.5)}px`
    }
    requestAnimationFrame(fit)
  })

  const shown = handler.handle(book, e)
  if (!shown) return false
  shown.catch(err => {
    // Not a note after all, or it couldn't be found: follow the link as usual.
    console.warn('Could not show footnote:', err)
    dialog?.close()
    goTo(e.detail.href)
  })
  return true
}
