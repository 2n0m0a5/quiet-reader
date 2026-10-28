// Reading statistics, worked out from the reading time saved per day and book.
import type { BookRecord, ReadingDay } from '../library/db'
import { statusOf } from '../library/db'
import { dayKey } from './tracker'

const WORDS_PER_PAGE = 250
const DAY = 86_400_000

export interface Stats {
  today: number                    // seconds
  streak: number                   // days in a row with reading (or with the goal met)
  week: { day: string; label: string; seconds: number }[]   // the last 7 days
  months: { label: string; seconds: number }[]               // this year, January to now
  totals: { week: number; month: number; year: number; all: number }
  wordsPerMinute: number | null
  pages: number
  finishedThisYear: BookRecord[]
  library: { books: number; authors: number; reading: number; finished: number }
}

export function computeStats(days: ReadingDay[], books: BookRecord[], dailyGoalMinutes: number, now = Date.now()): Stats {
  const perDay = new Map<string, number>()
  for (const d of days) perDay.set(d.day, (perDay.get(d.day) ?? 0) + d.seconds)
  const today = dayKey(now)
  const date = new Date(now)
  const year = String(date.getFullYear())
  const month = today.slice(0, 7)

  // Streak: days in a row ending today (or yesterday, if you haven't read yet today).
  const enough = (day: string) => (perDay.get(day) ?? 0) >= Math.max(60, dailyGoalMinutes * 60)
  let streak = 0
  let cursor = enough(today) ? now : now - DAY
  while (enough(dayKey(cursor))) { streak++; cursor -= DAY }

  const weekday = new Intl.DateTimeFormat('en', { weekday: 'short' })
  const week = Array.from({ length: 7 }, (_, i) => {
    const t = now - (6 - i) * DAY
    return { day: dayKey(t), label: i === 6 ? 'Today' : weekday.format(t), seconds: perDay.get(dayKey(t)) ?? 0 }
  })
  const monthName = new Intl.DateTimeFormat('en', { month: 'short' })
  const months = Array.from({ length: date.getMonth() + 1 }, (_, m) => {
    const key = `${year}-${String(m + 1).padStart(2, '0')}`
    let seconds = 0
    for (const [day, s] of perDay) if (day.startsWith(key)) seconds += s
    return { label: monthName.format(new Date(date.getFullYear(), m, 1)), seconds }
  })

  const sum = (test: (day: string) => boolean) => [...perDay].reduce((total, [day, s]) => total + (test(day) ? s : 0), 0)
  const weekStart = dayKey(now - 6 * DAY)
  const totals = {
    week: sum(day => day >= weekStart), month: sum(day => day.startsWith(month)),
    year: sum(day => day.startsWith(year)), all: sum(() => true),
  }

  // Speed over the last 30 days, once there's enough reading to say.
  const since = dayKey(now - 29 * DAY)
  const recent = days.filter(d => d.day >= since)
  const recentSeconds = recent.reduce((t, d) => t + d.seconds, 0)
  const recentPages = recent.reduce((t, d) => t + d.pages, 0)
  const wordsPerMinute = recentSeconds >= 600 && recentPages >= 5
    ? Math.round(recentPages * WORDS_PER_PAGE / (recentSeconds / 60) / 10) * 10 : null

  const finishedThisYear = books
    .filter(b => statusOf(b) === 'finished' && new Date(b.finishedAt ?? b.lastOpenedAt ?? b.addedAt).getFullYear() === date.getFullYear())
    .sort((a, b) => (b.finishedAt ?? 0) - (a.finishedAt ?? 0))
  const authors = new Set(books.map(b => b.author.trim().toLowerCase()).filter(Boolean))
  return {
    today: perDay.get(today) ?? 0, streak, week, months, totals, wordsPerMinute,
    pages: days.reduce((t, d) => t + d.pages, 0),
    finishedThisYear,
    library: {
      books: books.length, authors: authors.size,
      reading: books.filter(b => statusOf(b) === 'reading').length,
      finished: books.filter(b => statusOf(b) === 'finished').length,
    },
  }
}

// "45 min", "2 h 5 min", "0 min".
export function readingTime(seconds: number) {
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  return minutes % 60 ? `${hours} h ${minutes % 60} min` : `${hours} h`
}

// ---------- The deeper stats (0.3.3) ----------

export interface BookRow {
  book: BookRecord
  seconds: number
  pages: number
  days: number                 // days it was read on
  wordsPerMinute: number | null
  left: number | null          // about how long to finish, in seconds, for books being read
}

export interface MoreStats {
  // Your reading year: the last 26 weeks, a column per week from Monday.
  calendar: { day: string; seconds: number; future: boolean }[]
  daysThisYear: number
  daysAll: number
  longestRun: number
  bestDay: { day: string; seconds: number } | null
  // When you read
  hours: number[]              // seconds in each hour of the day, 0–23
  hasHours: boolean
  weekdays: number[]           // seconds on each weekday, Monday first
  // Your pace
  weeks: { start: string; seconds: number; pages: number }[]   // the last 12 weeks, oldest first
  sitting: number | null       // average sitting, seconds
  perDay: number | null        // average on days you read, seconds
  speed: { now: number | null; before: number | null }        // words a minute, last 30 days and the 30 before
  // Finished books
  finishedMonths: { start: string; count: number }[]          // the last 12 months, oldest first
  daysToFinish: number | null  // median, from the first day read to finishing
  finishedAll: number
  // Book by book
  books: BookRow[]
}

const wpm = (seconds: number, pages: number) =>
  seconds >= 300 && pages >= 3 ? Math.round(pages * WORDS_PER_PAGE / (seconds / 60) / 10) * 10 : null

const median = (values: number[]) => {
  if (!values.length) return null
  const sorted = [...values].sort((a, b) => a - b)
  const mid = sorted.length >> 1
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}

// Midnight at the start of a day key, local time.
const dateOf = (day: string) => { const [y, m, d] = day.split('-').map(Number); return new Date(y, m - 1, d) }

export function computeMore(days: ReadingDay[], books: BookRecord[], now = Date.now()): MoreStats {
  const perDay = new Map<string, number>()
  for (const d of days) perDay.set(d.day, (perDay.get(d.day) ?? 0) + d.seconds)
  const read = (day: string) => (perDay.get(day) ?? 0) >= 60
  const today = new Date(now)
  today.setHours(0, 0, 0, 0)
  const todayKey = dayKey(now)

  // Calendar: 26 whole weeks ending with this one.
  const monday = new Date(today)
  monday.setDate(monday.getDate() - (monday.getDay() + 6) % 7)
  const start = new Date(monday)
  start.setDate(start.getDate() - 25 * 7)
  const calendar: MoreStats['calendar'] = []
  for (let d = new Date(start); calendar.length < 26 * 7; d.setDate(d.getDate() + 1)) {
    const key = dayKey(d.getTime())
    calendar.push({ day: key, seconds: perDay.get(key) ?? 0, future: key > todayKey })
  }

  const readDays = [...perDay.keys()].filter(read).sort()
  const year = String(today.getFullYear())
  let longestRun = 0, run = 0, previous: Date | null = null
  for (const day of readDays) {
    const date = dateOf(day)
    run = previous && Math.round((date.getTime() - previous.getTime()) / DAY) === 1 ? run + 1 : 1
    longestRun = Math.max(longestRun, run)
    previous = date
  }
  let bestDay: MoreStats['bestDay'] = null
  for (const [day, seconds] of perDay) if (!bestDay || seconds > bestDay.seconds) bestDay = { day, seconds }

  // When: hours (recorded from 0.3.3) and weekdays (from all reading).
  const hours = Array(24).fill(0)
  let hasHours = false
  for (const d of days) d.hours?.forEach((s, h) => { if (s) { hours[h] += s; hasHours = true } })
  const weekdays = Array(7).fill(0)
  for (const [day, seconds] of perDay) weekdays[(dateOf(day).getDay() + 6) % 7] += seconds

  // Pace: 12 weeks from Monday.
  const weeks = Array.from({ length: 12 }, (_, i) => {
    const from = new Date(monday)
    from.setDate(from.getDate() - (11 - i) * 7)
    const to = new Date(from)
    to.setDate(to.getDate() + 7)
    const [a, b] = [dayKey(from.getTime()), dayKey(to.getTime())]
    const inWeek = days.filter(d => d.day >= a && d.day < b)
    return { start: a, seconds: inWeek.reduce((t, d) => t + d.seconds, 0), pages: inWeek.reduce((t, d) => t + d.pages, 0) }
  })
  const withSittings = days.filter(d => d.sittings)
  const sittingCount = withSittings.reduce((t, d) => t + (d.sittings ?? 0), 0)
  const sitting = sittingCount ? withSittings.reduce((t, d) => t + d.seconds, 0) / sittingCount : null
  const perDayValues = readDays.map(d => perDay.get(d)!)
  const perDayAverage = perDayValues.length ? perDayValues.reduce((a, b) => a + b, 0) / perDayValues.length : null
  const speedBetween = (from: number, to: number) => {
    const [a, b] = [dayKey(now - from * DAY), dayKey(now - to * DAY)]
    const inWindow = days.filter(d => d.day > a && d.day <= b)
    return wpm(inWindow.reduce((t, d) => t + d.seconds, 0), inWindow.reduce((t, d) => t + d.pages, 0))
  }

  // Finished books per month, the last 12 months.
  const finished = books.filter(b => statusOf(b) === 'finished')
  const finishedMonths = Array.from({ length: 12 }, (_, i) => {
    const from = new Date(today.getFullYear(), today.getMonth() - 11 + i, 1)
    const to = new Date(today.getFullYear(), today.getMonth() - 10 + i, 1)
    return {
      start: dayKey(from.getTime()),
      count: finished.filter(b => b.finishedAt && b.finishedAt >= from.getTime() && b.finishedAt < to.getTime()).length,
    }
  })
  const firstDay = new Map<string, string>()
  for (const d of days) if (!firstDay.has(d.bookId) || d.day < firstDay.get(d.bookId)!) firstDay.set(d.bookId, d.day)
  const daysToFinish = median(finished.flatMap(b => {
    const first = firstDay.get(b.id)
    return first && b.finishedAt ? [Math.max(1, Math.round((b.finishedAt - dateOf(first).getTime()) / DAY))] : []
  }))

  // Book by book: every book with reading time, most read first.
  const rows = new Map<string, { seconds: number, pages: number, days: Set<string> }>()
  for (const d of days) {
    const row = rows.get(d.bookId) ?? { seconds: 0, pages: 0, days: new Set<string>() }
    row.seconds += d.seconds
    row.pages += d.pages
    if (d.seconds >= 60) row.days.add(d.day)
    rows.set(d.bookId, row)
  }
  const byId = new Map(books.map(b => [b.id, b]))
  const bookRows: BookRow[] = [...rows].flatMap(([id, r]) => {
    const book = byId.get(id)
    if (!book || r.seconds < 60) return []
    // Time left: the time so far, stretched over the part still to read.
    const p = book.progress
    const left = statusOf(book) === 'reading' && p >= 0.03 && p < 1 && r.seconds >= 300 ? r.seconds * (1 - p) / p : null
    return [{ book, seconds: r.seconds, pages: r.pages, days: r.days.size, wordsPerMinute: wpm(r.seconds, r.pages), left }]
  }).sort((a, b) => b.seconds - a.seconds)

  return {
    calendar, daysThisYear: readDays.filter(d => d.startsWith(year)).length, daysAll: readDays.length, longestRun, bestDay,
    hours, hasHours, weekdays,
    weeks, sitting, perDay: perDayAverage, speed: { now: speedBetween(30, 0), before: speedBetween(60, 30) },
    finishedMonths, daysToFinish, finishedAll: finished.length,
    books: bookRows,
  }
}
