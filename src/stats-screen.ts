// The Reading screen: time read today, this week and this year, the streak, goals, speed,
// finished books, and a card about the library. From 0.3.3 also: the reading year, when you read,
// your pace, finished books over the months, and book by book.
import { BookCheck, Flame, Library } from 'lucide'
import { listBooks, listReading, statusOf, type BookRecord } from './library/db'
import { generatedCover } from './library/library-view'
import type { Settings } from './settings'
import { computeMore, computeStats, readingTime, type MoreStats, type Stats } from './stats/stats'
import { columns, growWhenSeen, heatmap, numbers, readout, table } from './stats/charts'
import { rollNumbers } from './ui/motion'
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
    const more = computeMore(days, books)
    this.#urls.forEach(URL.revokeObjectURL)
    this.#urls = []
    const inner = h('div', 'stats-inner')
    inner.append(
      this.#today(stats, settings), this.#week(stats),
      this.#calendar(more), this.#when(more), this.#pace(more),
      this.#year(stats, settings), this.#finished(more), this.#bookByBook(more),
      this.#totals(stats), this.#library(stats),
    )
    this.#root.replaceChildren(inner)
    inner.querySelectorAll<HTMLElement>('.chart-columns, .chart-heatmap').forEach(growWhenSeen)
    // The big numbers count up to their value.
    rollNumbers(inner.querySelectorAll<HTMLElement>('.stats-big, .stats-ring-value, .stats-value'))
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
    const out = readout('Minutes each day. Touch a day for its time.')
    const chart = columns(stats.week.map(d => ({
      label: d.label, value: d.seconds, now: d.label === 'Today', tip: `${d.label === 'Today' ? 'Today' : longDate(d.day)}: ${readingTime(d.seconds)}`,
    })), out, { max: Math.max(...stats.week.map(d => d.seconds), 60) })
    return this.#section('This week', head, out, chart)
  }

  #year(stats: Stats, settings: Settings) {
    const year = new Date().getFullYear()
    const head = h('div', 'stats-head')
    head.append(h('span', 'stats-big', readingTime(stats.totals.year)), h('span', 'settings-hint', `in ${year}`))
    const out = readout('Time each month. Touch a month for its time.')
    const chart = columns(stats.months.map((m, i) => ({
      label: m.label, value: m.seconds, now: i === stats.months.length - 1, tip: `${m.label}: ${readingTime(m.seconds)}`,
    })), out, { max: Math.max(...stats.months.map(m => m.seconds), 60) })
    const content: Node[] = [head, out, chart]

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

  // ---------- 1. Your reading year ----------
  // Every day of the last six months as a square, darker the longer you read. Days read only ever
  // count up: a missed day is just an empty square, never a broken record.
  #calendar(more: MoreStats) {
    const head = h('div', 'stats-head')
    head.append(h('span', 'stats-big', `${more.daysThisYear}`), h('span', 'settings-hint', `${more.daysThisYear === 1 ? 'day' : 'days'} with reading in ${new Date().getFullYear()}`))
    const out = readout('The last six months, a column for each week. Touch a day.')
    const map = heatmap(more.calendar.map(d => ({ ...d, tip: `${longDate(d.day)}: ${d.seconds >= 60 ? readingTime(d.seconds) : 'no reading'}` })), out)
    const grid = h('div', 'stats-grid')
    grid.append(
      tile(`${more.longestRun}`, more.longestRun === 1 ? 'day: your longest run' : 'days: your longest run'),
      tile(more.bestDay ? readingTime(more.bestDay.seconds) : '—', more.bestDay ? `your longest day, ${longDate(more.bestDay.day)}` : 'your longest day'),
    )
    return this.#section('Your reading year', head, out, map, grid)
  }

  // ---------- 2. When you read ----------
  #when(more: MoreStats) {
    const content: Node[] = []
    if (more.hasHours) {
      content.push(h('p', 'stats-sentence', whenSentence(more.hours)))
      const out = readout('Each hour of the day, midnight to midnight. Touch an hour.')
      const chart = columns(more.hours.map((s, hour) => ({
        label: hour % 6 === 0 ? hourName(hour) : '', value: s, tip: `${hourName(hour)} to ${hourName((hour + 1) % 24)}: ${readingTime(s)}`,
      })), out, { className: 'dense' })
      content.push(out, chart, numbers(['Hour', 'Time'], more.hours.map((s, hour) => [hourName(hour), readingTime(s)])))
    } else {
      content.push(h('p', 'settings-hint', 'Quiet starts noting the time of day from this version. Read a little and the hours will show here.'))
    }
    const names = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']
    const most = more.weekdays.indexOf(Math.max(...more.weekdays))
    const days = h('div', 'stats-subchart')
    const out = readout(more.weekdays.some(Boolean) ? `${names[most]} is your reading day.` : 'Each day of the week.')
    days.append(h('div', 'settings-label', 'Days of the week'), out, columns(more.weekdays.map((s, i) => ({
      label: names[i].slice(0, 3), value: s, now: s > 0 && i === most, tip: `${names[i]}s: ${readingTime(s)} in all`,
    })), out))
    content.push(days)
    return this.#section('When you read', ...content)
  }

  // ---------- 3. Your pace ----------
  #pace(more: MoreStats) {
    const total = more.weeks.reduce((t, w) => t + w.seconds, 0)
    const head = h('div', 'stats-head')
    head.append(h('span', 'stats-big', readingTime(total)), h('span', 'settings-hint', 'in the last 12 weeks'))
    const out = readout('Time each week. Touch a week.')
    const chart = columns(more.weeks.map((w, i) => ({
      label: i % 3 === 0 || i === 11 ? (i === 11 ? 'Now' : shortDate(w.start)) : '', value: w.seconds, now: i === 11,
      tip: `Week of ${longDate(w.start)}: ${readingTime(w.seconds)}${w.pages ? ` · ${w.pages} pages` : ''}`,
    })), out)
    const { now, before } = more.speed
    const change = now && before ? now - before : 0
    const speedNote = !now ? 'words a minute: read a little more'
      : !before ? 'words a minute, this month'
      : Math.abs(change) < 10 ? 'words a minute, about the same as last month'
      : `words a minute, ${change > 0 ? 'up' : 'down'} from ${before} last month`
    const grid = h('div', 'stats-grid')
    grid.append(
      tile(more.sitting ? readingTime(more.sitting) : '—', more.sitting ? 'a sitting, on average' : 'a sitting: read a little more'),
      tile(more.perDay ? readingTime(more.perDay) : '—', 'on a day you read, on average'),
      tile(now ? `${now}` : '—', speedNote),
    )
    return this.#section('Your pace', head, out, chart,
      numbers(['Week of', 'Time', 'Pages'], more.weeks.map(w => [longDate(w.start), readingTime(w.seconds), w.pages])), grid)
  }

  // ---------- 4. Finished books ----------
  #finished(more: MoreStats) {
    const year = more.finishedMonths.reduce((t, m) => t + m.count, 0)
    const head = h('div', 'stats-head')
    head.append(h('span', 'stats-big', `${year}`), h('span', 'settings-hint', `${year === 1 ? 'book' : 'books'} finished in the last 12 months`))
    const out = readout('Books finished each month. Touch a month.')
    const month = new Intl.DateTimeFormat('en', { month: 'narrow' })
    const full = new Intl.DateTimeFormat('en', { month: 'long', year: 'numeric' })
    const chart = columns(more.finishedMonths.map((m, i) => ({
      label: month.format(dateOf(m.start)), value: m.count, now: i === 11,
      tip: `${full.format(dateOf(m.start))}: ${m.count} ${m.count === 1 ? 'book' : 'books'}`,
    })), out, { max: Math.max(2, ...more.finishedMonths.map(m => m.count)) })
    const grid = h('div', 'stats-grid')
    grid.append(
      tile(more.daysToFinish ? `${Math.round(more.daysToFinish)}` : '—', more.daysToFinish ? `${Math.round(more.daysToFinish) === 1 ? 'day' : 'days'} to finish a book, usually` : 'days to finish a book'),
      tile(`${more.finishedAll}`, more.finishedAll === 1 ? 'book finished in all' : 'books finished in all'),
    )
    return this.#section('Finished books', head, out, chart,
      numbers(['Month', 'Books'], more.finishedMonths.map(m => [full.format(dateOf(m.start)), m.count])), grid)
  }

  // ---------- 5. Book by book ----------
  #bookByBook(more: MoreStats) {
    if (!more.books.length) return this.#section('Book by book', h('p', 'settings-hint', 'Each book you read shows here: time spent, days, speed, and about how long it has left.'))
    const t = table(['Book', 'Time', 'Days', 'Speed', 'To finish'], more.books.map(r => [
      r.book.title, readingTime(r.seconds), r.days, r.wordsPerMinute ? `${r.wordsPerMinute} wpm` : '—',
      statusOf(r.book) === 'finished' ? 'Finished' : r.left ? `about ${roughTime(r.left)}` : '—',
    ]), 'book-table')
    // Each title opens its book.
    t.querySelectorAll('tbody th').forEach((cell, i) => {
      const open = h('button', 'book-table-open', cell.textContent ?? '')
      open.addEventListener('click', () => this.#actions.open(more.books[i].book.id))
      cell.replaceChildren(open)
    })
    const scroll = h('div', 'table-scroll')
    scroll.append(t)
    return this.#section('Book by book', scroll,
      h('p', 'settings-hint stats-note', '“To finish” is a guess from your time so far and how far you are, so it gets better as you read.'))
  }

  #totals(stats: Stats) {
    const grid = h('div', 'stats-grid')
    grid.append(
      tile(readingTime(stats.totals.month), 'this month'),
      tile(readingTime(stats.totals.all), 'in all'),
      tile(stats.pages.toLocaleString('en'), stats.pages === 1 ? 'page read' : 'pages read'),
      tile(stats.wordsPerMinute ? `${stats.wordsPerMinute}` : '—', stats.wordsPerMinute ? 'words a minute' : 'speed: read a little more'),
    )
    const note = h('p', 'settings-hint stats-note', 'A page is about 250 words, the same in every book, so pages and speed compare fairly. Time counts only while a book is open on screen and you’re turning pages.')
    return this.#section('In numbers', grid, note)
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

// A guess, rounded the way you'd say it: "40 min", "1 h 30 min", "11 h".
function roughTime(seconds: number) {
  const minutes = seconds / 60
  if (minutes < 60) return `${Math.max(5, Math.round(minutes / 5) * 5)} min`
  if (minutes < 180) return readingTime(Math.round(minutes / 10) * 600)
  return `${Math.round(minutes / 60)} h`
}

// A number with a few words under it.
function tile(value: string, label: string) {
  const t = h('div', 'stats-tile')
  t.append(h('div', 'stats-value', value), h('div', 'settings-hint', label))
  return t
}

const dateOf = (day: string) => { const [y, m, d] = day.split('-').map(Number); return new Date(y, m - 1, d) }
const longFormat = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short' })
const shortFormat = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short' })
const longDate = (day: string) => longFormat.format(dateOf(day))
const shortDate = (day: string) => shortFormat.format(dateOf(day))
const hourName = (hour: number) => hour === 0 ? 'midnight' : hour === 12 ? 'noon' : hour < 12 ? `${hour} am` : `${hour - 12} pm`

// "You read most in the evening, between 8 pm and midnight." From the busiest four hours in a row.
function whenSentence(hours: number[]) {
  const sums = Array.from({ length: 24 }, (_, start) => [0, 1, 2, 3].reduce((t, i) => t + hours[(start + i) % 24], 0))
  const top = Math.max(...sums)
  // When several windows tie (two busy hours fit in more than one), take the middle one.
  const tied = sums.flatMap((sum, start) => sum >= top * 0.999 ? [start] : [])
  const best = tied[Math.floor((tied.length - 1) / 2)]
  const middle = (best + 2) % 24
  const part = middle >= 5 && middle < 12 ? 'in the morning' : middle >= 12 && middle < 17 ? 'in the afternoon'
    : middle >= 17 && middle < 22 ? 'in the evening' : 'at night'
  return `You read most ${part}, between ${hourName(best)} and ${hourName((best + 4) % 24)}.`
}
