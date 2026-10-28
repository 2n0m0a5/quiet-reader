// Dialogs: a sheet from the bottom on phones, a centred window on larger screens.
// Built on <dialog>, which handles focus and the Escape key.
import { createElement, type IconNode } from 'lucide'
import { draggableSheet, lastPointer, rubberBand } from './motion'

// An icon element, sized by the app's CSS (svg.lucide).
export const icon = (node: IconNode) => createElement(node, { class: 'lucide' })

export const h = <K extends keyof HTMLElementTagNameMap>(tag: K, className = '', text = '') => {
  const el = document.createElement(tag)
  if (className) el.className = className
  if (text) el.textContent = text
  return el
}

export interface DialogHandle {
  el: HTMLDialogElement
  body: HTMLElement
  close(): void
}

const open: DialogHandle[] = []
const phone = matchMedia('(max-width: 600px)')

// Closes the dialog on top, if any. Used by Android's back button.
export function closeTopDialog(): boolean {
  const top = open.at(-1)
  if (!top) return false
  top.close()
  return true
}

export function openDialog(options: {
  title?: string
  content: Node | Node[]
  className?: string
  onClose?: () => void
}): DialogHandle {
  const el = h('dialog', `dialog ${options.className ?? ''}`)
  const handle = h('div', 'sheet-handle')
  handle.setAttribute('aria-hidden', 'true')
  const body = h('div', 'dialog-body')
  if (options.title) {
    const title = h('h2', 'dialog-title', options.title)
    el.setAttribute('aria-label', options.title)
    body.append(title)
  }
  body.append(...([] as Node[]).concat(options.content))
  el.append(handle, body)
  document.body.append(el)

  let closed = false
  // `dragged`: it was already dragged off the screen, so it goes without the closing animation.
  const close = (dragged = false) => {
    if (closed) return
    closed = true
    open.splice(open.indexOf(dialog), 1)
    if (dragged) { el.close(); el.remove() }
    else {
      el.classList.add('closing')
      // Let the closing animation play, then remove it.
      window.setTimeout(() => { el.close(); el.remove() }, 200)
    }
    options.onClose?.()
  }
  const dialog: DialogHandle = { el, body, close: () => close() }
  // On phones it's a sheet: drag it down to close.
  draggableSheet(el, { close: () => close(true), enabled: () => phone.matches, handle: [handle], scroller: body })
  rubberBand(body, { edges: phone.matches ? 'bottom' : 'both' })

  el.addEventListener('cancel', e => { e.preventDefault(); close() })   // Escape
  el.addEventListener('click', e => { if (e.target === el) close() })    // backdrop
  open.push(dialog)
  el.showModal()
  // On wider screens a dialog grows out of where you tapped, like a popover on iOS.
  if (!phone.matches) {
    const r = el.getBoundingClientRect()
    el.style.transformOrigin = `${Math.max(0, Math.min(r.width, lastPointer.x - r.left))}px ${Math.max(0, Math.min(r.height, lastPointer.y - r.top))}px`
  }
  return dialog
}

export function button(label: string, className: string, onClick: () => void) {
  const b = h('button', `btn ${className}`, label)
  b.type = 'button'
  b.addEventListener('click', onClick)
  return b
}

// A yes/no question. Resolves to true if confirmed.
export function confirmDialog(options: {
  title: string
  message: string
  confirm: string
  danger?: boolean
}): Promise<boolean> {
  return new Promise(resolve => {
    let answer = false
    const actions = h('div', 'dialog-actions')
    const d = openDialog({
      title: options.title,
      className: 'dialog-small',
      content: [h('p', 'dialog-text', options.message), actions],
      onClose: () => resolve(answer),
    })
    actions.append(
      button('Cancel', '', () => d.close()),
      button(options.confirm, options.danger ? 'danger' : 'primary', () => { answer = true; d.close() }),
    )
  })
}

// Asks for one or more lines of text. Resolves to the values, or null if cancelled.
export function formDialog(options: {
  title: string
  fields: { label: string; value: string; placeholder?: string; multiline?: boolean }[]
  confirm: string
}): Promise<string[] | null> {
  return new Promise(resolve => {
    let result: string[] | null = null
    const form = h('form', 'dialog-form')
    const inputs = options.fields.map(field => {
      const label = h('label', 'field')
      const input = field.multiline ? h('textarea') : h('input')
      if (input instanceof HTMLTextAreaElement) input.rows = 5
      input.value = field.value
      input.placeholder = field.placeholder ?? ''
      label.append(h('span', '', field.label), input)
      form.append(label)
      return input
    })
    const actions = h('div', 'dialog-actions')
    form.append(actions)
    const d = openDialog({ title: options.title, className: 'dialog-small', content: form, onClose: () => resolve(result) })
    const submit = h('button', 'btn primary', options.confirm)
    submit.type = 'submit'
    actions.append(button('Cancel', '', () => d.close()), submit)
    form.addEventListener('submit', e => {
      e.preventDefault()
      result = inputs.map(i => i.value.trim())
      d.close()
    })
    inputs[0]?.focus()
    inputs[0]?.select()
  })
}

// A list of choices, like a menu. Each item runs its action and closes the menu.
export function menuDialog(options: {
  title?: string
  items: { label: string; hint?: string; icon?: SVGElement; danger?: boolean; selected?: boolean; run: () => void }[]
}) {
  const list = h('div', 'menu-list')
  const d = openDialog({ title: options.title, className: 'dialog-small', content: list })
  for (const item of options.items) {
    const b = h('button', `menu-item${item.danger ? ' danger' : ''}${item.selected ? ' selected' : ''}`)
    b.type = 'button'
    if (item.icon) b.append(item.icon)
    if (item.hint) {
      const text = h('span', 'menu-item-text')
      text.append(h('span', '', item.label), h('span', 'settings-hint', item.hint))
      b.append(text)
    } else {
      b.append(h('span', '', item.label))
    }
    b.addEventListener('click', () => { d.close(); item.run() })
    list.append(b)
  }
  return d
}
