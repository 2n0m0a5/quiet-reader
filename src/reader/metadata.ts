import type { Contributor, LangMap } from './foliate'

export function langString(value?: LangMap): string {
  if (!value) return ''
  if (typeof value === 'string') return value
  return Object.values(value)[0] ?? ''
}

export function authorString(value?: Contributor): string {
  if (!value) return ''
  const one = (c: string | { name?: LangMap }) => (typeof c === 'string' ? c : langString(c.name))
  const list = Array.isArray(value) ? value.map(one) : [one(value)]
  return new Intl.ListFormat('en', { style: 'short', type: 'conjunction' }).format(list.filter(Boolean))
}

// "my_book-file.epub" → "my book-file"
export function titleFromFileName(name: string): string {
  return name.replace(/\.[^.]+$/, '').replace(/_/g, ' ').trim()
}

// Identifies a book for remembering its reading position.
export function bookKey(file: File): string {
  return `${file.name}:${file.size}`
}
