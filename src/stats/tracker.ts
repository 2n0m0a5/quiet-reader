// Counts reading time while a book is open: only while Quiet is on screen and you've touched,
// scrolled or turned a page in the last few minutes (a phone left on the table doesn't count).
import { addReading } from '../library/db'

const TICK = 5_000          // ms
const IDLE = 5 * 60_000     // no activity for this long: not reading
const SAVE_EVERY = 30_000

export const dayKey = (time = Date.now()) => {
  const d = new Date(time)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export class ReadingTracker {
  #bookId: string
  #lastActivity = Date.now()
  #lastPage: number | null = null
  #unsaved = { seconds: 0, pages: 0 }
  #session = { seconds: 0, pages: 0 }
  #timer: number
  #lastSave = Date.now()
  #saved: () => void

  // `saved` is called after reading time is written (to check the daily goal).
  constructor(bookId: string, saved: () => void) {
    this.#bookId = bookId
    this.#saved = saved
    this.#timer = window.setInterval(() => this.#tick(), TICK)
  }

  // Something the reader did: a touch, a scroll, a key.
  activity() {
    this.#lastActivity = Date.now()
  }

  // The reading position moved to this page (foliate's location number).
  page(current: number | undefined) {
    this.activity()
    if (current == null) return
    // Pages turned forward count. A big move (a jump through the contents, a link) isn't reading.
    if (this.#lastPage != null && current > this.#lastPage && current - this.#lastPage <= 3) {
      const pages = current - this.#lastPage
      this.#unsaved.pages += pages
      this.#session.pages += pages
    }
    this.#lastPage = current
  }

  get session() {
    return { ...this.#session }
  }

  #tick() {
    const reading = document.visibilityState === 'visible' && Date.now() - this.#lastActivity < IDLE
    if (!reading) return
    this.#unsaved.seconds += TICK / 1000
    this.#session.seconds += TICK / 1000
    if (Date.now() - this.#lastSave >= SAVE_EVERY) this.save()
  }

  async save() {
    this.#lastSave = Date.now()
    const { seconds, pages } = this.#unsaved
    if (!seconds && !pages) return
    this.#unsaved = { seconds: 0, pages: 0 }
    await addReading(dayKey(), this.#bookId, seconds, pages)
    this.#saved()
  }

  // The book was closed. Returns this sitting's reading time and pages.
  stop() {
    window.clearInterval(this.#timer)
    this.save()
    return this.session
  }
}
