// A short message at the bottom of the screen.
let el: HTMLElement | null = null
let timer = 0

export function toast(message: string, ms = 3500) {
  if (!el) {
    el = document.createElement('div')
    el.className = 'toast'
    el.setAttribute('role', 'status')
    document.body.append(el)
  }
  el.textContent = message
  el.classList.add('show')
  window.clearTimeout(timer)
  timer = window.setTimeout(() => el?.classList.remove('show'), ms)
}
