// Quiet's marks, for both design styles. CSS shows the one that fits (see styles.css):
// Quiet Star uses the khatam, an eight-pointed star made of two squares; Paper uses an italic "q."

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

// The app icon: a lapis tile with an ivory star, or "q." on white paper.
export function logo(className = '') {
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
