import type { TocItem } from '../reader/foliate'

// The contents list in the sidebar. Sub-chapters are indented under their chapter.
export class TocView {
  #root: HTMLElement
  #buttons = new Map<string, HTMLButtonElement>()
  #current: HTMLButtonElement | null = null

  constructor(root: HTMLElement) {
    this.#root = root
  }

  showMessage(text: string) {
    this.#buttons.clear()
    this.#current = null
    const note = document.createElement('div')
    note.className = 'toc-note'
    note.textContent = text
    this.#root.replaceChildren(note)
  }

  render(items: TocItem[], onSelect: (href: string) => void): number {
    this.#buttons.clear()
    this.#current = null
    this.#root.replaceChildren()
    let count = 0
    const add = (list: TocItem[], depth: number) => {
      for (const item of list) {
        const button = document.createElement('button')
        button.className = 'toc-item'
        button.style.paddingLeft = `${10 + depth * 14}px`
        button.textContent = item.label?.trim() || 'Untitled'
        if (item.href) {
          button.addEventListener('click', () => onSelect(item.href))
          this.#buttons.set(item.href, button)
        }
        this.#root.append(button)
        count++
        if (item.subitems?.length) add(item.subitems, depth + 1)
      }
    }
    add(items, 0)
    return count
  }

  setCurrent(href?: string) {
    const button = href ? this.#buttons.get(href) : undefined
    if (button === this.#current) return
    this.#current?.classList.remove('active')
    this.#current = button ?? null
    if (button) {
      button.classList.add('active')
      button.scrollIntoView({ block: 'nearest' })
    }
  }
}
