// Things drawn over the page: warm light, the reading ruler, and the clock and battery.
import type { Settings } from '../settings'
import { batteryLevel } from '../app/native'

export class ReadingOverlays {
  #warm: HTMLElement
  #ruler: HTMLElement
  #status: HTMLElement
  #settings: Settings
  #save: (rulerY: number) => void

  constructor(stage: HTMLElement, settings: Settings, save: (rulerY: number) => void) {
    this.#settings = settings
    this.#save = save
    this.#warm = div('warm-light')
    this.#ruler = div('reading-ruler')
    this.#status = div('reading-status')
    this.#warm.setAttribute('aria-hidden', 'true')
    this.#status.setAttribute('aria-hidden', 'true')
    const handle = div('ruler-handle')
    handle.title = 'Drag to move the ruler'
    this.#ruler.append(div('ruler-band'), handle)
    stage.append(this.#warm, this.#ruler, this.#status)
    this.#dragRuler(stage, handle)
    this.apply(settings)
    this.#tick()
  }

  apply(settings: Settings) {
    this.#settings = settings
    // Warm light: an amber layer that takes the blue out of the page, like Night Shift.
    this.#warm.style.opacity = String(settings.warmth / 100 * 0.45)
    this.#warm.hidden = settings.warmth === 0
    // The ruler is about two lines of text tall.
    const line = 16 * settings.fontSize / 100 * settings.lineHeight
    this.#ruler.hidden = settings.ruler === 'off'
    this.#ruler.dataset.kind = settings.ruler
    this.#ruler.style.setProperty('--band', `${Math.round(line * 2.1)}px`)
    this.#ruler.style.setProperty('--y', `${settings.rulerY * 100}%`)
    this.#status.hidden = !settings.showClock
  }

  // Drag the handle on the right of the ruler up or down.
  #dragRuler(stage: HTMLElement, handle: HTMLElement) {
    handle.addEventListener('pointerdown', e => {
      e.preventDefault()
      handle.setPointerCapture(e.pointerId)
      const move = (ev: PointerEvent) => {
        const rect = stage.getBoundingClientRect()
        const y = Math.min(0.92, Math.max(0.05, (ev.clientY - rect.top) / rect.height))
        this.#ruler.style.setProperty('--y', `${y * 100}%`)
        this.#settings = { ...this.#settings, rulerY: y }
      }
      const up = () => {
        handle.removeEventListener('pointermove', move)
        handle.removeEventListener('pointerup', up)
        handle.removeEventListener('pointercancel', up)
        this.#save(Math.round(this.#settings.rulerY * 1000) / 1000)
      }
      handle.addEventListener('pointermove', move)
      handle.addEventListener('pointerup', up)
      handle.addEventListener('pointercancel', up)
    })
  }

  // The time, and the battery when the phone tells us, updated every half minute.
  async #tick() {
    const time = new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
    const battery = await batteryLevel()
    this.#status.textContent = battery == null ? time : `${time} · ${Math.round(battery * 100)}%`
    window.setTimeout(() => this.#tick(), 30_000 - Date.now() % 30_000)
  }
}

function div(className: string) {
  const el = document.createElement('div')
  el.className = className
  return el
}
