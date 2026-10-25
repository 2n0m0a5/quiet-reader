// Human-friendly numbers and dates.

export function fileSize(bytes: number) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

// "just now", "5 minutes ago", "yesterday", "3 days ago", or a date for anything older than a month.
export function timeAgo(timestamp: number) {
  const seconds = (Date.now() - timestamp) / 1000
  if (seconds < 60) return 'just now'
  if (seconds < 3600) return relative.format(-Math.round(seconds / 60), 'minute')
  if (seconds < 86400) return relative.format(-Math.round(seconds / 3600), 'hour')
  if (seconds < 86400 * 30) return relative.format(-Math.round(seconds / 86400), 'day')
  return date(timestamp)
}

export function date(timestamp: number) {
  return new Date(timestamp).toLocaleDateString('en', { year: 'numeric', month: 'short', day: 'numeric' })
}

export const percent = (fraction: number) => `${Math.round(fraction * 100)}%`
