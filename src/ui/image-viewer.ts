// A picture from the book, full screen: pinch or scroll to zoom, drag to move, double-tap to zoom in and out.
import { X } from 'lucide'
import { h, icon, openDialog } from './dialog'

const MAX = 6

export function showImage(src: string, alt: string) {
  const stage = h('div', 'image-stage')
  const img = h('img', 'image-full')
  img.src = src
  img.alt = alt
  img.draggable = false
  const close = h('button', 'btn icon image-close')
  close.setAttribute('aria-label', 'Close')
  close.append(icon(X))
  stage.append(img)
  const d = openDialog({ className: 'dialog-image', content: [stage, close] })
  d.el.setAttribute('aria-label', alt || 'Picture')
  close.addEventListener('click', () => d.close())

  let scale = 1, x = 0, y = 0
  const draw = () => { img.style.transform = `translate(${x}px, ${y}px) scale(${scale})` }
  // Zoom around a point on screen, so what's under the fingers stays there.
  const zoomAt = (next: number, cx: number, cy: number) => {
    next = Math.min(MAX, Math.max(1, next))
    const rect = stage.getBoundingClientRect()
    const px = cx - rect.left - rect.width / 2, py = cy - rect.top - rect.height / 2
    x = px - (px - x) * next / scale
    y = py - (py - y) * next / scale
    scale = next
    if (scale === 1) x = y = 0
    draw()
  }

  stage.addEventListener('wheel', e => {
    e.preventDefault()
    zoomAt(scale * Math.exp(-e.deltaY / 300), e.clientX, e.clientY)
  }, { passive: false })

  const pointers = new Map<number, { x: number; y: number }>()
  let pinch = 0, lastTap = 0, moved = false
  const distance = () => {
    const [a, b] = [...pointers.values()]
    return Math.hypot(a.x - b.x, a.y - b.y)
  }
  stage.addEventListener('pointerdown', e => {
    stage.setPointerCapture(e.pointerId)
    pointers.set(e.pointerId, { x: e.clientX, y: e.clientY })
    moved = false
    if (pointers.size === 2) pinch = distance()
  })
  stage.addEventListener('pointermove', e => {
    const last = pointers.get(e.pointerId)
    if (!last) return
    const point = { x: e.clientX, y: e.clientY }
    if (Math.hypot(point.x - last.x, point.y - last.y) > 2) moved = true
    pointers.set(e.pointerId, point)
    if (pointers.size === 2) {
      const now = distance()
      const [a, b] = [...pointers.values()]
      zoomAt(scale * now / (pinch || now), (a.x + b.x) / 2, (a.y + b.y) / 2)
      pinch = now
    } else if (scale > 1) {
      x += point.x - last.x
      y += point.y - last.y
      draw()
    }
  })
  const up = (e: PointerEvent) => {
    pointers.delete(e.pointerId)
    pinch = 0
    if (moved || pointers.size) return
    // A tap: double-tap zooms; a single tap outside the picture closes the viewer.
    const now = Date.now()
    if (now - lastTap < 300) {
      lastTap = 0
      zoomAt(scale > 1 ? 1 : 2.5, e.clientX, e.clientY)
    } else {
      lastTap = now
      const r = img.getBoundingClientRect()
      const outside = scale === 1 && (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom)
      if (outside) window.setTimeout(() => { if (lastTap === now) d.close() }, 300)
    }
  }
  stage.addEventListener('pointerup', up)
  stage.addEventListener('pointercancel', e => { pointers.delete(e.pointerId); pinch = 0 })
}
