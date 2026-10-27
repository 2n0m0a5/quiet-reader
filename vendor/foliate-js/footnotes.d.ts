// Quiet: types for the parts of footnotes.js that Quiet uses.
export class FootnoteHandler extends EventTarget {
  detectFootnotes: boolean
  handle(book: unknown, e: CustomEvent<{ a: HTMLAnchorElement; href: string }>): Promise<void> | undefined
}
