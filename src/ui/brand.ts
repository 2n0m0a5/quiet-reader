// Quiet's marks. Radio (0.4.0) uses a knob; the khatam star stays for ratings.

const NS = 'http://www.w3.org/2000/svg'
// The outline of a square and the same square turned 45°, in a 24 × 24 box.
const STAR = 'M12 2.8 14.7 5.5H18.5V9.3L21.2 12 18.5 14.7V18.5H14.7L12 21.2 9.3 18.5H5.5V14.7L2.8 12 5.5 9.3V5.5H9.3Z'

export function khatam(className = '') {
  const svg = document.createElementNS(NS, 'svg')
  svg.setAttribute('class', `khatam ${className}`.trim())
  svg.setAttribute('viewBox', '0 0 24 24')
  svg.setAttribute('aria-hidden', 'true')
  const path = document.createElementNS(NS, 'path')
  path.setAttribute('d', STAR)
  svg.append(path)
  return svg
}

// Radio's mark: a volume knob turned all the way down, with the orange "zero" dot.
export function knob(className = '') {
  const svg = document.createElementNS(NS, 'svg')
  svg.setAttribute('class', `knob ${className}`.trim())
  svg.setAttribute('viewBox', '0 0 64 64')
  svg.setAttribute('aria-hidden', 'true')
  svg.innerHTML = '<circle cx="32" cy="32" r="25" fill="var(--panel)" stroke="var(--key-edge)" stroke-width="3"/>'
    + '<path d="M32 32 19 45" stroke="currentColor" stroke-width="6" stroke-linecap="round"/><circle cx="11" cy="53" r="4" fill="var(--accent)"/>'
  return svg
}

// The app icon: the knob on the casing.
export function radioLogo(className = '') {
  const el = document.createElement('div')
  el.className = `logo logo-radio ${className}`.trim()
  el.setAttribute('aria-hidden', 'true')
  el.append(knob())
  return el
}

// (Before 0.4.0: a lapis tile with an ivory star, or "q." on white paper.)
export function logo(className = '') {
  if (document.documentElement.dataset.style === 'radio') return radioLogo(className)
  const el = document.createElement('div')
  el.className = `logo ${className}`.trim()
  el.setAttribute('aria-hidden', 'true')
  const q = document.createElement('span')
  q.className = 'logo-q'
  q.textContent = 'q'
  const dot = document.createElement('span')
  dot.className = 'logo-dot'
  dot.textContent = '.'
  q.append(dot)
  el.append(khatam(), q)
  return el
}
