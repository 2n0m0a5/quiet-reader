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
    document.body.append(el)
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
