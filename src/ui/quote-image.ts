// A quote (or any highlight) as a picture to share: the passage, who says it, the book and its
// cover, drawn in the current design style's own colours, type and ornament. Three shapes:
// a post (4:5), a square, and a story (9:16).
import type { Annotation, BookRecord } from '../library/db'
import { button, h, openDialog } from './dialog'
import { toast } from './toast'
import { saveImage, shareImage } from '../app/native'

type Shape = 'post' | 'square' | 'story'
const SIZES: Record<Shape, [number, number]> = { post: [1080, 1350], square: [1080, 1080], story: [1080, 1920] }
const KHATAM = 'M12 2.8 14.7 5.5H18.5V9.3L21.2 12 18.5 14.7V18.5H14.7L12 21.2 9.3 18.5H5.5V14.7L2.8 12 5.5 9.3V5.5H9.3Z'

export function showQuoteImage(a: Annotation, book?: BookRecord) {
  let shape: Shape = 'post'
  let blob: Blob | null = null
  const preview = h('img', 'quote-image-preview')
  preview.alt = 'The picture to share'
  const shapes = h('div', 'choice-row')
  for (const [value, label] of [['post', 'Post'], ['square', 'Square'], ['story', 'Story']] as [Shape, string][]) {
    const b = h('button', `choice${value === shape ? ' selected' : ''}`, label)
    b.addEventListener('click', () => {
      shape = value
      shapes.querySelectorAll('.choice').forEach(c => c.classList.toggle('selected', c === b))
      draw()
    })
    shapes.append(b)
  }
  const actions = h('div', 'dialog-actions')
  const fileName = () => `Quiet quote - ${(book?.title ?? "untitled").replace(/[^\p{L}\p{N} ._-]+/gu, "").slice(0, 60).trim()}.png`
  const share = button('Share', 'primary', async () => {
    if (!blob) return
    try { await shareImage(blob, fileName()) } catch (e) { if ((e as Error).name !== 'AbortError') toast('Couldn’t share the picture.') }
  })
  const save = button('Save picture', '', async () => {
    if (!blob) return
    try { await saveImage(blob, fileName()); toast('Saved to Pictures.') } catch { toast('Couldn’t save the picture.') }
  })
  actions.append(save, share)
  const frame = h('div', 'quote-image-frame')
  frame.append(preview)
  const d = openDialog({ title: 'Share as a picture', className: 'dialog-details dialog-quote-image', content: [frame, shapes, actions] })
  let url = ''
  const draw = async () => {
    frame.classList.add('drawing')
    blob = await renderQuote(a, book, shape)
    if (url) URL.revokeObjectURL(url)
    url = URL.createObjectURL(blob)
    preview.src = url
    preview.style.aspectRatio = `${SIZES[shape][0]} / ${SIZES[shape][1]}`
    preview.decode().catch(() => {}).finally(() => frame.classList.remove('drawing'))
  }
  draw()
  d.el.addEventListener('close', () => { if (url) URL.revokeObjectURL(url) })
}

// ---------- Drawing ----------

const css = (name: string) => getComputedStyle(document.documentElement).getPropertyValue(name).trim()

export async function renderQuote(a: Annotation, book: BookRecord | undefined, shape: Shape): Promise<Blob> {
  const [W, H] = SIZES[shape]
  const canvas = document.createElement('canvas')
  canvas.width = W
  canvas.height = H
  const g = canvas.getContext('2d')!
  const style = document.documentElement.dataset.style ?? 'star'
  const colors = {
    bg: css('--reader-bg') || '#fcfbf8', text: css('--reader-text') || '#1b1a17', muted: css('--muted') || '#5e5a52',
    accent: css('--accent') || '#24407a', line: css('--line') || '#e4e0d8', mark: css('--mark') || css('--accent'),
  }
  const serif = css('--font-reading') || 'Georgia, serif'
  const title = css('--font-title') || serif
  const sans = css('--font-ui') || 'system-ui, sans-serif'
  await Promise.all([`400 60px ${serif}`, `italic 400 40px ${serif}`, `600 34px ${sans}`, `400 40px ${title}`].map(f => document.fonts.load(f).catch(() => null)))

  // Background, with each style's own touch.
  g.fillStyle = colors.bg
  g.fillRect(0, 0, W, H)
  const M = 96
  if (style === 'frost') {
    const blobs: [number, number, number, string][] = [[0.15, 0.1, 0.55, '#bcd6f5'], [0.9, 0.3, 0.5, '#dccbf2'], [0.3, 0.95, 0.6, '#c9ece4']]
    for (const [x, y, r, c] of blobs) {
      const grad = g.createRadialGradient(x * W, y * H, 0, x * W, y * H, r * W)
      grad.addColorStop(0, c + (document.documentElement.hasAttribute('data-dark') ? '55' : 'cc'))
      grad.addColorStop(1, c + '00')
      g.fillStyle = grad
      g.fillRect(0, 0, W, H)
    }
    // The frosted pane the quote sits on.
    roundRect(g, 56, 56, W - 112, H - 112, 64)
    g.fillStyle = document.documentElement.hasAttribute('data-dark') ? 'rgba(30,38,58,0.55)' : 'rgba(255,255,255,0.55)'
    g.fill()
    g.strokeStyle = document.documentElement.hasAttribute('data-dark') ? 'rgba(255,255,255,0.16)' : 'rgba(255,255,255,0.9)'
    g.lineWidth = 3
    g.stroke()
  }
  if (style === 'tashkent') {
    // Bands of ganch plaster, top and bottom.
    g.strokeStyle = colors.line
    g.lineWidth = 2
    for (const y of [40, H - 40]) for (let x = -14; x < W + 28; x += 28) { g.beginPath(); g.arc(x, y, 12, 0, Math.PI * 2); g.stroke() }
  }

  // The mark at the top.
  let y = M + 40
  g.fillStyle = colors.accent
  if (style === 'star') {
    g.save(); g.translate(M - 6, y - 30); g.scale(2.6, 2.6); g.fill(new Path2D(KHATAM)); g.restore()
  } else if (style === 'paper') {
    g.fillStyle = colors.mark
    g.beginPath(); g.arc(M + 8, y - 6, 9, 0, Math.PI * 2); g.fill()
  } else {
    g.font = `400 150px ${serif}`
    g.fillText('“', M - 10, y + 70)
  }
  y += style === 'paper' || style === 'star' ? 70 : 120

  // The passage, as large as fits.
  const passage = `“${a.text.trim().replace(/\s+/g, ' ')}”`
  const bottomSpace = book?.cover ? 330 : 250
  const maxH = H - y - bottomSpace
  let size = 68, lines: string[] = []
  for (; size >= 30; size -= 2) {
    g.font = `${style === 'paper' ? 'italic ' : ''}400 ${size}px ${serif}`
    lines = wrap(g, passage, W - M * 2)
    if (lines.length * size * 1.42 <= maxH) break
  }
  if (lines.length * size * 1.42 > maxH) {
    const keep = Math.max(1, Math.floor(maxH / (size * 1.42)))
    lines = lines.slice(0, keep)
    lines[keep - 1] = lines[keep - 1].replace(/\s*\S*$/, '') + '…”'
  }
  g.fillStyle = colors.text
  g.textBaseline = 'alphabetic'
  for (const line of lines) { y += size * 1.42; g.fillText(line, M, y - size * 0.32) }

  // Who says it.
  y += 50
  if (style === 'paper' || style === 'tashkent') { g.fillStyle = colors.line; g.fillRect(M, y - 26, 80, 2) }
  g.fillStyle = colors.text
  g.font = `600 34px ${style === 'tashkent' ? title : sans}`
  const who = a.speaker || (a.quote ? book?.author : '') || ''
  if (who) { g.fillText(`— ${who}`, M, y + 20); y += 56 }

  // The book, with its cover.
  const footY = H - M - (book?.cover ? 200 : 110)
  let textX = M
  if (book?.cover) {
    try {
      const img = await createImageBitmap(book.cover)
      const ch = 200, cw = Math.round(ch * Math.min(0.75, img.width / img.height))
      g.save()
      g.shadowColor = 'rgba(0,0,0,0.18)'; g.shadowBlur = 24; g.shadowOffsetY = 8
      if (style === 'tashkent') archPath(g, M, footY, cw, ch); else roundRect(g, M, footY, cw, ch, style === 'paper' ? 2 : 10)
      g.clip()
      g.drawImage(img, M, footY, cw, ch)
      g.restore()
      textX = M + cw + 34
    } catch { /* no cover */ }
  }
  const footText = book?.cover ? footY + 80 : footY + 40
  g.fillStyle = colors.text
  g.font = `italic 400 38px ${serif}`
  g.fillText(ellipsize(g, book?.title ?? '', W - textX - M), textX, footText)
  g.fillStyle = colors.muted
  g.font = `400 30px ${sans}`
  if (book?.author) g.fillText(ellipsize(g, book.author, W - textX - M), textX, footText + 48)

  // Quiet, small, in the corner.
  g.textAlign = 'right'
  g.fillStyle = colors.muted
  g.font = style === 'paper' ? `italic 400 34px ${serif}` : style === 'tashkent' ? `400 32px ${title}` : `600 28px ${sans}`
  const brand = style === 'paper' ? 'quiet' : 'Quiet'
  g.fillText(brand, W - M - (style === 'paper' ? 14 : 0), H - M + 30)
  if (style === 'paper') { g.fillStyle = colors.mark; g.beginPath(); g.arc(W - M - 4, H - M + 26, 5, 0, Math.PI * 2); g.fill() }

  return new Promise(resolve => canvas.toBlob(b => resolve(b!), 'image/png'))
}

function wrap(g: CanvasRenderingContext2D, text: string, width: number) {
  const words = text.split(' ')
  const lines: string[] = []
  let line = ''
  for (const word of words) {
    const test = line ? `${line} ${word}` : word
    if (g.measureText(test).width > width && line) { lines.push(line); line = word } else line = test
  }
  if (line) lines.push(line)
  return lines
}
function ellipsize(g: CanvasRenderingContext2D, text: string, width: number) {
  if (g.measureText(text).width <= width) return text
  while (text && g.measureText(`${text}…`).width > width) text = text.slice(0, -1)
  return `${text.trim()}…`
}
function roundRect(g: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  g.beginPath()
  g.roundRect(x, y, w, h, r)
}
function archPath(g: CanvasRenderingContext2D, x: number, y: number, w: number, h: number) {
  const r = w / 2
  g.beginPath()
  g.moveTo(x, y + h); g.lineTo(x, y + r * 0.8)
  g.ellipse(x + r, y + r * 0.8, r, r * 0.8, 0, Math.PI, 0)
  g.lineTo(x + w, y + h); g.closePath()
}
