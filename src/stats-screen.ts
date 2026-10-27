// The Reading screen: time read today, this week and this year, the streak, goals, speed,
// finished books, and a card about the library.
import { BookCheck, Flame, Library } from 'lucide'
import { listBooks, listReading, type BookRecord } from './library/db'
import { generatedCover } from './library/library-view'
import type { Settings } from './settings'
import { computeStats, readingTime, type Stats } from './stats/stats'
import { h, icon } from './ui/dialog'
import { haptic } from './app/native'

export interface StatsScreenActions {
  get(): Settings
  update(patch: Partial<Settings>): void
  open(bookId: string): void
}

const DAILY_GOALS: [number, string][] = [[0, 'Off'], [10, '10 min'], [20, '20 min'], [30, '30 min'], [60, '1 hour']]
const YEAR_GOALS: [number, string][] = [[0, 'Off'], [6, '6'], [12, '12'], [24, '24'], [52, '52']]

export class StatsScreen {
  #root: HTMLElement
  #actions: StatsScreenActions
  #urls: string[] = []

  constructor(root: HTMLElement, actions: StatsScreenActions) {
    this.#root = root
    this.#actions = actions
  }

  async render() {
    const [days, books] = await Promise.all([listReading(), listBooks()])
    const settings = this.#actions.get()
    const stats = computeStats(days, books, settings.dailyGoal)
    this.#urls.forEach(URL.revokeObjectURL)
    this.#urls = []
    const inner = h('div', 'stats-inner')
    inner.append(this.#today(stats, settings), this.#week(stats), this.#year(stats, settings), this.#totals(stats), this.#library(stats))
    this.#root.replaceChildren(inner)
  }

  reset() { this.#root.scrollTop = 0 }

  #section(title: string, ...content: Node[]) {
    const section = h('section', 'settings-section')
    const card = h('div', 'settings-card stats-card')
    card.append(...content)
    section.append(h('h2', '', title), card)
    return section
  }

  // A row of choices that saves straight away (used for the two goals).
  #choices(label: string, key: 'dailyGoal' | 'yearGoal', options: [number, string][]) {
    const row = h('div', 'stats-goal')
    const group = h('div', 'choice-row')
    for (const [value, text] of options) {
      const b = h('button', `choice${this.#actions.get()[key] === value ? ' selected' : ''}`, text)
      b.addEventListener('click', () => { haptic(); this.#actions.update({ [key]: value }); this.render() })
      group.append(b)
    }
    row.append(h('div', 'settings-label', label), group)
    return row
  }

  #today(stats: Stats, settings: Settings) {
    const goal = settings.dailyGoal * 60
    const top = h('div', 'stats-today')
    top.append(ring(goal ? Math.min(1, stats.today / goal) : stats.today ? 1 : 0, readingTime(stats.today), goal ? `of ${readingTime(goal)}` : 'today'))
    const side = h('div', 'stats-today-side')
    const streak = h('div', 'stats-streak')
    streak.append(icon(Flame), h('span', '', stats.streak === 1 ? '1 day in a row' : `${stats.streak} days in a row`))
    streak.classList.toggle('on', stats.streak > 0)
    const message = !goal ? 'Set a daily goal below, if you’d like one.'
      : stats.today >= goal ? 'Today’s goal is done. Well read.'
      : `${readingTime(goal - stats.today)} to go today.`
    side.append(streak, h('p', 'settings-hint', message))
    top.append(side)
    return this.#section('Today', top, this.#choices('Daily goal', 'dailyGoal', DAILY_GOALS))
  }

  #week(stats: Stats) {
    const head = h('div', 'stats-head')
    head.append(h('span', 'stats-big', readingTime(stats.totals.week)), h('span', 'settings-hint', 'in the last 7 days'))
    return this.#section('This week', head, bars(stats.week.map(d => ({ label: d.label, seconds: d.seconds, now: d.label === 'Today' }))))
  }

  #year(stats: Stats, settings: Settings) {
    const year = new Date().getFullYear()
    const head = h('div', 'stats-head')
    head.append(h('span', 'stats-big', readingTime(stats.totals.year)), h('span', 'settings-hint', `in ${year}`))
    const content: Node[] = [head, bars(stats.months.map((m, i) => ({ label: m.label, seconds: m.seconds, now: i === stats.months.length - 1 })))]

    // Books finished this year, with the yearly goal.
    const finished = stats.finishedThisYear.length
    const books = h('div', 'stats-books')
    const title = h('div', 'stats-books-title')
    title.append(icon(BookCheck), h('span', '', settings.yearGoal
      ? `${finished} of ${settings.yearGoal} books finished`
      : `${finished} ${finished === 1 ? 'book' : 'books'} finished`))
    books.append(title)
    if (settings.yearGoal) {
      const bar = h('div', 'bar')
      const fill = h('span')
      fill.style.width = `${Math.min(100, finished / settings.yearGoal * 100)}%`
      bar.append(fill)
      books.append(bar)
    }
    if (finished) {
      const covers = h('div', 'stats-covers')
      for (const b of stats.finishedThisYear.slice(0, 12)) covers.append(this.#cover(b))
      books.append(covers)
    }
    content.push(books, this.#choices('Books to finish this year', 'yearGoal', YEAR_GOALS))
    return this.#section(`${year}`, ...content)
  }

  #totals(stats: Stats) {
    const grid = h('div', 'stats-grid')
    const tile = (value: string, label: string) => {
      const t = h('div', 'stats-tile')
      t.append(h('div', 'stats-value', value), h('div', 'settings-hint', label))
      grid.append(t)
    }
    tile(readingTime(stats.totals.month), 'this month')
    tile(readingTime(stats.totals.all), 'in all')
    tile(stats.pages.toLocaleString('en'), stats.pages === 1 ? 'page read' : 'pages read')
    tile(stats.wordsPerMinute ? `${stats.wordsPerMinute}` : '—', stats.wordsPerMinute ? 'words a minute' : 'speed: read a little more')
    const note = h('p', 'settings-hint stats-note', 'A page is about 250 words, the same in every book, so pages and speed compare fairly. Time counts only while a book is open on screen and you’re turning pages.')
    return this.#section('Reading', grid, note)
  }

  #library(stats: Stats) {
    const { books, authors, reading, finished } = stats.library
    const row = h('div', 'stats-library')
    const badge = h('div', 'stats-library-icon')
    badge.append(icon(Library))
    const text = h('div')
    text.append(
      h('div', 'stats-library-line', `${books} ${books === 1 ? 'book' : 'books'} by ${authors} ${authors === 1 ? 'author' : 'authors'}`),
      h('div', 'settings-hint', `${reading} reading now · ${finished} finished`),
    )
    row.append(badge, text)
    return this.#section('Your library', row)
  }

  #cover(book: BookRecord) {
    const b = h('button', 'book-cover stats-cover')
    b.title = book.title
    b.setAttribute('aria-label', book.title)
    if (book.cover) {
      const img = h('img')
      img.alt = ''
      img.src = URL.createObjectURL(book.cover)
      this.#urls.push(img.src)
      b.append(img)
    } else {
      b.append(generatedCover(book))
    }
    b.addEventListener('click', () => this.#actions.open(book.id))
    return b
  }
}

// A progress ring with the time in the middle.
function ring(fraction: number, value: string, label: string) {
  const wrap = h('div', 'stats-ring')
  const size = 112, stroke = 10, r = (size - stroke) / 2, c = 2 * Math.PI * r
  wrap.innerHTML = `<svg viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" aria-hidden="true">
    <circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="var(--line)" stroke-width="${stroke}"/>
    <circle class="stats-ring-fill" cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="var(--accent)" stroke-width="${stroke}"
      stroke-linecap="round" stroke-dasharray="${c}" stroke-dashoffset="${c * (1 - fraction)}" transform="rotate(-90 ${size / 2} ${size / 2})"/>
  </svg>`
  const text = h('div', 'stats-ring-text')
  text.append(h('div', 'stats-ring-value', value), h('div', 'settings-hint', label))
  wrap.append(text)
  wrap.setAttribute('role', 'img')
  wrap.setAttribute('aria-label', `${value} ${label}`)
  return wrap
}

// A small bar chart: one bar per day or month.
function bars(items: { label: string; seconds: number; now: boolean }[]) {
  const max = Math.max(...items.map(i => i.seconds), 60)
  const chart = h('div', 'stats-bars')
  for (const item of items) {
    const col = h('div', `stats-bar${item.now ? ' now' : ''}`)
    col.setAttribute('aria-label', `${item.label}: ${readingTime(item.seconds)}`)
    col.title = `${item.label}: ${readingTime(item.seconds)}`
    const track = h('div', 'stats-bar-track')
    const fill = h('div', 'stats-bar-fill')
    fill.style.height = `${item.seconds ? Math.max(4, item.seconds / max * 100) : 0}%`
    track.append(fill)
    col.append(track, h('div', 'stats-bar-label', item.label))
    chart.append(col)
  }
  return chart
}
