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
