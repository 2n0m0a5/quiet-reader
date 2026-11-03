// A short message at the bottom of the screen, optionally with one action button.
let el: HTMLElement | null = null
let timer = 0

export interface ToastAction {
  label: string
  run(): void
}

export function toast(message: string, ms = 3500, action?: ToastAction) {
  if (!el) {
    el = document.createElement('div')
    el.className = 'toast'
    el.setAttribute('role', 'status')
    // A popover sits in the browser's top layer, so the message shows above open dialogs too
    // (it used to appear behind them: "Saved" after saving a quote picture was never seen).
    if ('showPopover' in el) el.setAttribute('popover', 'manual')
    document.body.append(el)
  }
  if (el.hasAttribute('popover')) {
    // Shown again each time, so it lands above a dialog opened since.
    try { if (el.matches(':popover-open')) el.hidePopover(); el.showPopover() } catch { /* not supported */ }
  }
  el.replaceChildren(message)
  if (action) {
    const button = document.createElement('button')
    button.className = 'toast-action'
    button.textContent = action.label
    button.addEventListener('click', () => {
      el?.classList.remove('show')
      action.run()
    })
    el.append(button)
  }
  el.classList.add('show')
  window.clearTimeout(timer)
  timer = window.setTimeout(() => el?.classList.remove('show'), ms)
}
