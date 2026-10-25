// Types for the parts of the vendored foliate-js library that Quiet uses.
// The library is plain JavaScript; see vendor/foliate-js/README.md.

export type LangMap = string | Record<string, string>
export type Contributor = string | { name?: LangMap } | Array<string | { name?: LangMap }>

export interface TocItem {
  label: string
  href: string
  subitems?: TocItem[]
}

export interface Book {
  dir?: 'ltr' | 'rtl'
  toc?: TocItem[]
  sections: { linear?: string }[]
  metadata?: {
    title?: LangMap
    author?: Contributor
    language?: string | string[]
    identifier?: string
    publisher?: Contributor
    published?: string
    description?: string
    subject?: Contributor
  }
  rendition?: { layout?: string }
  getCover?: () => Promise<Blob | null> | Blob | null
}

export interface Location {
  fraction: number
  cfi: string
  tocItem?: TocItem & { id?: number }
  section?: { current: number; total: number }
  location?: { current: number; next: number; total: number }
  time?: { section: number; total: number }
}

export interface Paginator extends HTMLElement {
  scrolled: boolean
  start: number
  end: number
  size: number
  viewSize: number
  setStyles(css: string | [string, string]): void
  prev(distance?: number): Promise<void>
  next(distance?: number): Promise<void>
}

export interface FoliateView extends HTMLElement {
  book: Book
  renderer: Paginator
  isFixedLayout: boolean
  lastLocation?: Location
  open(book: File | Blob | string | Book): Promise<void>
  close(): void
  init(opts: { lastLocation?: string; showTextStart?: boolean }): Promise<void>
  goTo(target: string | number): Promise<unknown>
  goToFraction(fraction: number): Promise<void>
  prev(distance?: number): Promise<void>
  next(distance?: number): Promise<void>
  goLeft(): Promise<void>
  goRight(): Promise<void>
}
